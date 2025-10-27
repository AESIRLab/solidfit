package com.example.solidfit

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.request.ImageRequest
import com.example.solidfit.WorkoutItemSolidApplication.Companion.IMAGES_DIR
import com.example.solidfit.data.WorkoutItemRepository
import com.example.solidfit.model.WorkoutItem
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.skCompiler.generatedModel.WorkoutItemRemoteDataSource
import java.security.MessageDigest
import java.util.Date
import java.util.UUID


class WorkoutItemViewModel(
    private val repository: WorkoutItemRepository,
    private val remoteDataSource: WorkoutItemRemoteDataSource
): ViewModel() {

    private var _allItems: MutableStateFlow<List<WorkoutItem>> = MutableStateFlow(listOf())
    val allItems: StateFlow<List<WorkoutItem>> get() = _allItems

    private val _workoutItem = MutableStateFlow<WorkoutItem?>(null)
    val workoutItem: StateFlow<WorkoutItem?> = _workoutItem

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady


    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.allWorkoutItemsAsFlow.collect { list ->
                withContext(Dispatchers.Main) {
                    _allItems.value = list.distinctBy { it.id }
                }
            }
        }
    }

    fun remoteIsAvailable(): Boolean {
        return remoteDataSource.remoteAccessible()
    }

    fun setRemoteRepositoryData(
        accessToken: String,
        signingJwk: String,
        webId: String,
        expirationTime: Long,
    ) {
        remoteDataSource.signingJwk = signingJwk
        remoteDataSource.webId = webId
        remoteDataSource.expirationTime = expirationTime
        remoteDataSource.accessToken = accessToken

        // Prime the cache *after* setting the WebID
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getOrFetchStorageRoot()
                Log.d("SolidImage", "Storage root primed in setRemoteRepositoryData.")

                // Tell the UI we are ready to display images
                withContext(Dispatchers.Main) {
                    _isReady.value = true
                }

            } catch (e: Exception) {
                Log.e("SolidImage", "Failed to prime storage root", e)
            }
        }
    }

    fun updateWebId(webId: String) {
        viewModelScope.launch {
            // Keep the new webId (or reset if bad)
            try {
                repository.insertWebId(webId)
            } catch (e: Exception) {
                repository.resetModel()
            }

            // Fetch remote snapshot
            val remote = if (remoteDataSource.remoteAccessible())
                remoteDataSource.fetchRemoteItemList()
            else
                emptyList()

            // Fetch one snapshot of local items
            val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

            // Merge & de-duplicate
            val merged = (remote + local)
                .distinctBy { it.id }

            // Overwrite local cache so you never “see” dupes again
            repository.overwriteModelWithList(merged)

            // Update UI
            _allItems.value = merged

            // Push merged back to remote
            if (remoteDataSource.remoteAccessible()) {
                remoteDataSource.updateRemoteItemList(merged)
            }
        }
    }

    fun insert(item: WorkoutItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insert(item)

                val after = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()
                val target = after.firstOrNull { it.dateCreated == item.dateCreated && it.name == item.name }
                    ?: item

                val prepared = try {
                    ensureRemoteMedia(target)
                } catch (e: Exception) {
                    Log.e("WorkoutViewModel", "Image upload failed, will sync later.", e)
                    target
                }

                if (prepared.mediaUri != target.mediaUri) {
                    repository.update(prepared)
                }

                if (remoteDataSource.remoteAccessible()) {
                    val latest = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()
                    val sanitized = mutableListOf<WorkoutItem>()
                    for (workout in latest) {
                        sanitized.add(ensureRemoteMedia(workout))
                    }
                }
            } catch (e: Exception) {
                Log.e("WorkoutViewModel", "Failed to insert and sync workout.", e)
            }
        }
    }

    fun delete(item: WorkoutItem) {
        viewModelScope.launch {
            // 1) Delete it from your local RDF store
            repository.deleteByUri(item.id)

            // 2) Pull one snapshot of your now-current local list
            val remaining: List<WorkoutItem> =
                repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

            // 3) Update your in-memory UI state
            _allItems.value = remaining

            // 4) Sync that exact list back to your Pod
            remoteDataSource.updateRemoteItemList(remaining)
        }
    }

    suspend fun updateRemote() {
        if (!remoteDataSource.remoteAccessible()) return
        val list = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()
        val sanitized = sanitizeForPod(list)
        remoteDataSource.updateRemoteItemList(sanitized)
    }

    fun update(item: WorkoutItem) {
        require(item.id.isNotBlank()) { "update() called with blank id" }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prepared = try {
                    ensureRemoteMedia(item)
                } catch (e: Exception) {
                    Log.e("WorkoutViewModel", "Image upload failed during update.", e)
                    item
                }

                repository.update(prepared)

                if (remoteDataSource.remoteAccessible()) {
                    val latest = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()

                    val sanitized = mutableListOf<WorkoutItem>()
                    for (workout in latest) {
                        sanitized.add(ensureRemoteMedia(workout))
                    }

                    remoteDataSource.updateRemoteItemList(sanitized)
                }

            } catch (t: Throwable) {
                Log.e("WorkoutViewModel", "Failed to sync remote update.", t)
            }
        }
    }

    private fun merge(remote: List<WorkoutItem>, local: List<WorkoutItem>): List<WorkoutItem> =
        (remote + local).distinctBy { it.id }

    private suspend fun ensureRemoteMedia(item: WorkoutItem): WorkoutItem {
        val uri = item.mediaUri
        if (uri.isBlank()) return item
        if (uri.startsWith("http", ignoreCase = true)) return item
        if (!uri.startsWith("content", ignoreCase = true)) return item

        // deterministic base name from the workout's id fragment
        val idPart = item.id.substringAfterLast('#', item.id).ifBlank { UUID.randomUUID().toString() }
        val relative = uploadImageIfLocal(uri, preferredBaseName = idPart)
        return item.copy(mediaUri = relative)  // store RELATIVE path in DB/Pod
    }

    private suspend fun sanitizeForPod(items: List<WorkoutItem>): List<WorkoutItem> =
        items.map { ensureRemoteMedia(it) }

    // cache the user's Pod storage root once per session
    private var storageRootCache: String? = null

    private suspend fun getOrFetchStorageRoot(): String? {
        storageRootCache?.let { return it }
        val webId = remoteDataSource.webId ?: return null
        val root = getStorageRootFromWebId(webId) // <- you already have this helper
        storageRootCache = if (root.endsWith("/")) root else "$root/"
        return storageRootCache
    }

    private suspend fun uploadImageIfLocal(
        localUri: String,
        preferredBaseName: String? = null
    ): String = withContext(Dispatchers.IO) {
        val ctx = WorkoutItemSolidApplication.appInstance
        val cr = ctx.contentResolver
        val u = Uri.parse(localUri)

        val mime = cr.getType(u) ?: "application/octet-stream"
        val bytes = cr.openInputStream(u)?.use { it.readBytes() }
            ?: error("Failed to read image stream")

        // derive extension from MIME
        val ext = mime.substringAfter('/', "bin")
        val baseName = preferredBaseName ?: UUID.randomUUID().toString()
        val fileName = "$baseName.$ext"

        // storage root + container
        val storageRoot = getOrFetchStorageRoot()
            ?: error("Storage root unavailable (not signed in yet?)")
        val container = storageRoot + IMAGES_DIR
        ensureContainer(container) // will no-op if exists

        val targetUrl = container + fileName

        // DPoP-authenticated PUT
        val client = getUnsafeOkHttpClient()
        val at = remoteDataSource.accessToken ?: error("No access token")
        val jwk = remoteDataSource.signingJwk ?: error("No signing JWK")
        val dpop = buildResourceDPoP("PUT", targetUrl, at, jwk)

        val req = Request.Builder()
            .url(targetUrl)
            .header("Authorization", "DPoP $at")
            .header("DPoP", dpop)
            .header("Content-Type", mime)
            .header("If-None-Match", "*")
            .put(bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Image upload failed: ${resp.code}")
        }

        // return RELATIVE path for storage in the item
        return@withContext IMAGES_DIR + fileName     // e.g. "AndroidApplication/Images/SolidFit/<id>.jpeg"
    }

    // Replace the existing function with this new version
    fun buildAuthorizedImageRequest(context: Context, mediaUri: String): ImageRequest? {
        if (mediaUri.isBlank()) return null

        // If it's content://, we don't try to fetch from the Pod
        if (mediaUri.startsWith("content", ignoreCase = true)) return null

        val root = storageRootCache
        if (root == null) {
            Log.w("SolidImage", "buildAuthReq returning null: storageRootCache is null")
            return null
        }

        val at = remoteDataSource.accessToken
        if (at == null) {
            Log.w("SolidImage", "buildAuthReq returning null: accessToken is null")
            return null
        }

        val jwk = remoteDataSource.signingJwk
        if (jwk == null) {
            Log.w("SolidImage", "buildAuthReq returning null: signingJwk is null")
            return null
        }

        // Resolve relative path -> absolute URL using cached storage root.
        val fullUrl = if (mediaUri.startsWith("http", ignoreCase = true)) {
            mediaUri
        } else {
            val sep = if (root.endsWith("/")) "" else "/"
            root + sep + mediaUri.trimStart('/')
        }

        val dpop = buildResourceDPoP("GET", fullUrl, at, jwk)

        return ImageRequest.Builder(context)
            .data(fullUrl)
            .addHeader("Authorization", "DPoP $at")
            .addHeader("DPoP", dpop)
            .addHeader("Accept", "image/*, */*;q=0.1")
            .listener(
                onStart = { Log.d("SolidImage", "Coil start: $fullUrl") },
                onSuccess = { _, r ->
                    Log.d("SolidImage", "Coil success: $fullUrl (${r.drawable.intrinsicWidth}x${r.drawable.intrinsicHeight})")
                },
                onError = { req, result ->
                    Log.e("SolidImage", "Coil error for ${req.data}", result.throwable)
                }
            )
            .build()
    }

    private fun buildResourceDPoP(
        method: String,
        url: String,
        accessToken: String,
        signerJwk: String
    ): String {
        val ec = ECKey.parse(signerJwk)
        val signer = ECDSASigner(ec.toECPrivateKey())

        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("dpop+jwt"))
            .jwk(ec.toPublicJWK())
            .build()

        val ath = Base64URL.encode(
            MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray())
        ).toString()

        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString()) // jti
            .issueTime(Date())                   // iat (seconds precision in lib)
            .claim("htu", url)
            .claim("htm", method.uppercase())
            .claim("ath", ath)
            .build()

        val jwt = SignedJWT(header, claims)
        jwt.sign(signer)
        return jwt.serialize()
    }

    private suspend fun ensureContainer(containerUrlRaw: String) = withContext(Dispatchers.IO) {
        val containerUrl = if (containerUrlRaw.endsWith("/")) containerUrlRaw else "$containerUrlRaw/"
        val client = getUnsafeOkHttpClient()

        // HEAD the container
        val headDpop = buildResourceDPoP(
            "HEAD", containerUrl,
            remoteDataSource.accessToken!!,
            remoteDataSource.signingJwk!!
        )
        val head = Request.Builder()
            .url(containerUrl)
            .header("Authorization", "DPoP ${remoteDataSource.accessToken}")
            .header("DPoP", headDpop)
            .head()
            .build()

        client.newCall(head).execute().use { h ->
            if (h.isSuccessful) return@withContext // already exists
            if (h.code != 404) error("HEAD ${h.code} for $containerUrl")

            // A tiny RDF body declaring BasicContainer + Container helps avoid 400/415
            val turtleBody = """
            @prefix ldp: <http://www.w3.org/ns/ldp#> .
            <> a ldp:BasicContainer, ldp:Container .
        """.trimIndent()

            val putDpop = buildResourceDPoP(
                "PUT", containerUrl,
                remoteDataSource.accessToken!!,
                remoteDataSource.signingJwk!!
            )
            val putReq = Request.Builder()
                .url(containerUrl)
                .header("Authorization", "DPoP ${remoteDataSource.accessToken}")
                .header("DPoP", putDpop)
                .header("If-None-Match", "*")
                .header("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"")
                .header("Content-Type", "text/turtle")
                .put(turtleBody.toRequestBody("text/turtle".toMediaTypeOrNull()))
                .build()

            client.newCall(putReq).execute().use { p ->
                if (p.isSuccessful || p.code == 409) return@withContext

                // LOG the body once so we can see what ESS didn't like
                val msg = p.body?.string().orEmpty()

                // Some ESS setups prefer POST-to-parent; try that as a fallback
                if (p.code == 400 || p.code == 415) {
                    val parent = containerUrl.trimEnd('/').substringBeforeLast('/') + "/"
                    val postDpop = buildResourceDPoP(
                        "POST", parent,
                        remoteDataSource.accessToken!!,
                        remoteDataSource.signingJwk!!
                    )
                    val postReq = Request.Builder()
                        .url(parent)
                        .header("Authorization", "DPoP ${remoteDataSource.accessToken}")
                        .header("DPoP", postDpop)
                        .header("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"")
                        .header("Slug", containerUrl.removeSuffix("/").substringAfterLast('/')) // e.g., "Images"
                        .header("Content-Type", "text/turtle")
                        .post(turtleBody.toRequestBody("text/turtle".toMediaTypeOrNull()))
                        .build()

                    client.newCall(postReq).execute().use { pp ->
                        if (pp.isSuccessful || pp.code == 201 || pp.code == 409) return@withContext
                        val msg2 = pp.body?.string().orEmpty()
                        error("Failed to create Images container: ${pp.code} ${msg2.ifBlank { "" }}")
                    }
                } else {
                    error("Failed to create Images container: ${p.code} ${msg.ifBlank { "" }}")
                }
            }
        }
    }

    private suspend fun getStorageRootFromWebId(webId: String): String = withContext(Dispatchers.IO) {
        // Fetch the WebID doc and extract pim:storage
        val client = getUnsafeOkHttpClient()
        val req = Request.Builder()
            .url(webId)
            .addHeader("Accept", "text/turtle, application/ld+json;q=0.9, */*;q=0.1")
            .build()
        val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
        val m = Regex("""pim:storage\s*<([^>]+)>""").find(body)
        val root = m?.groupValues?.get(1)
            ?: Regex("""https://storage\.inrupt\.com/[a-f0-9-]+/""")
                .find(body)?.value
            ?: error("Could not locate Pod storage root from WebID doc")
        if (root.endsWith('/')) root else "$root/"
    }

    fun loadWorkoutById(id: String) {
        viewModelScope.launch {
            // Try local-first
            repository.getWorkoutItemLiveData(id).firstOrNull()?.let {
                _workoutItem.value = it
                return@launch
            }

            // Fallback to in-memory merged list
            val fromMerged = _allItems.value.find { it.id == id }
            if (fromMerged != null) {
                _workoutItem.value = fromMerged
            } else if (remoteDataSource.remoteAccessible()) {
                // as a last resort, pull fresh remote list into merged
                val remote = remoteDataSource.fetchRemoteItemList()
                val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()
                val merged = merge(remote, local)
                _allItems.value = merged
                _workoutItem.value = merged.find { it.id == id }
            } else {
                _workoutItem.value = null
            }
        }

    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WorkoutItemSolidApplication)
                val itemRepository = application.repository
                val itemRemoteDataSource = WorkoutItemRemoteDataSource(externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
                WorkoutItemViewModel(itemRepository, itemRemoteDataSource)
            }
        }
    }
}