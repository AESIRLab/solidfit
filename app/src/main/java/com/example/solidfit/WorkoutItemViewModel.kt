package com.example.solidfit

import android.app.Application
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.solidfit.data.WorkoutItemRemoteDataSource
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import coil.memory.MemoryCache
import coil.imageLoader


class WorkoutItemViewModel(
    private val repository: WorkoutItemRepository,
    private val remoteDataSource: WorkoutItemRemoteDataSource,
    private val application: Application
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
                    _allItems.value = list.distinctBy { it.id }.sortedByDescending { it.dateCreated }
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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                getOrFetchStorageRoot()
                Log.d("SolidImage", "Storage root primed in setRemoteRepositoryData.")

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
            try {
                repository.insertWebId(webId)
            } catch (e: Exception) {
                repository.resetModel()
            }

            val remote = if (remoteDataSource.remoteAccessible())
                remoteDataSource.fetchRemoteItemList()
            else
                emptyList()

            val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

            val merged = (remote + local)
                .distinctBy { it.id }

            repository.overwriteModelWithList(merged)

            syncRemoteImages(merged)

            _allItems.value = merged.sortedByDescending { it.dateCreated }

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
                    Log.e("WorkoutViewModel", "Image upload failed for new item, will sync later.", e)
                    target
                }

                if (prepared.mediaUri != target.mediaUri) {
                    repository.update(prepared)
                }

                if (!prepared.mediaUri.startsWith("content", true) && prepared.mediaUri.isNotBlank()) {
                    val fullUrl = resolveMediaUrl(prepared.mediaUri) // Get the full https://... URL
                    if (fullUrl != null) {
                        val loader = application.imageLoader
                        loader.memoryCache?.remove(MemoryCache.Key(fullUrl))
                        loader.diskCache?.remove(fullUrl)
                        Log.d("SolidImage", "Cache cleared for: $fullUrl")
                    }
                }

                if (remoteDataSource.remoteAccessible()) {
                    val latest = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()

                    val sanitized = mutableListOf<WorkoutItem>()
                    for (workout in latest) {
                        try {
                            sanitized.add(ensureRemoteMedia(workout))
                        } catch (e: Exception) {
                            Log.w("WorkoutViewModel", "Failed to sanitize item ${workout.id}, skipping its media upload.", e)
                            sanitized.add(workout)
                        }
                    }

                    remoteDataSource.updateRemoteItemList(sanitized)
                }
            } catch (t: Throwable) {
                Log.e("WorkoutViewModel", "Failed to insert and sync workout.", t)
            }
        }
    }

    fun delete(item: WorkoutItem) {
        viewModelScope.launch {
            repository.deleteByUri(item.id)

            val remaining: List<WorkoutItem> =
                repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

            _allItems.value = remaining.sortedByDescending { it.dateCreated }

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
                repository.update(item)

                if (!item.mediaUri.startsWith("content", true) && item.mediaUri.isNotBlank()) {
                    val fullUrl = resolveMediaUrl(item.mediaUri)
                    if (fullUrl != null) {
                        val loader = application.imageLoader
                        loader.memoryCache?.remove(MemoryCache.Key(fullUrl))
                        loader.diskCache?.remove(fullUrl)
                        Log.d("SolidImage", "Cache cleared for: $fullUrl")
                    }
                }

                val prepared = try {
                    ensureRemoteMedia(item)
                } catch (e: Exception) {
                    Log.e("WorkoutViewModel", "Image upload failed during background update.", e)
                    item
                }

                if (!prepared.mediaUri.startsWith("content", true) && prepared.mediaUri.isNotBlank()) {
                    val fullUrl = resolveMediaUrl(prepared.mediaUri)
                    if (fullUrl != null) {
                        val loader = application.imageLoader
                        loader.memoryCache?.remove(MemoryCache.Key(fullUrl))
                        loader.diskCache?.remove(fullUrl)
                        Log.d("SolidImage", "Cache cleared post-upload for: $fullUrl")
                    }
                }

                if (prepared.mediaUri != item.mediaUri) {
                    repository.update(prepared)
                }

                if (remoteDataSource.remoteAccessible()) {
                    val latest = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()

                    val sanitized = mutableListOf<WorkoutItem>()
                    for (workout in latest) {
                        try {
                            sanitized.add(ensureRemoteMedia(workout))
                        } catch (e: Exception) {
                            Log.w("WorkoutViewModel", "Failed to sanitize item ${workout.id}, skipping its media upload.", e)
                            sanitized.add(workout)
                        }
                    }
                    remoteDataSource.updateRemoteItemList(sanitized)
                }

            } catch (t: Throwable) {
                Log.e("WorkoutViewModel", "Failed to sync remote update.", t)
            }
        }
    }

    private fun resolveMediaUrl(mediaUri: String): String? {
        if (mediaUri.isBlank()) return null
        return if (mediaUri.startsWith("http", ignoreCase = true)) {
            mediaUri
        } else {
            val root = storageRootCache ?: return null
            val sep = if (root.endsWith("/")) "" else "/"
            root + sep + mediaUri.trimStart('/')
        }
    }

    private fun merge(remote: List<WorkoutItem>, local: List<WorkoutItem>): List<WorkoutItem> =
        (remote + local).distinctBy { it.id }

    private suspend fun ensureRemoteMedia(item: WorkoutItem): WorkoutItem {
        val uri = item.mediaUri
        if (uri.isBlank()) return item
        if (uri.startsWith("http", ignoreCase = true)) return item
        if (!uri.startsWith("content", ignoreCase = true)) return item

        val idPart = item.id.substringAfterLast('#', item.id).ifBlank { UUID.randomUUID().toString() }
        val relative = uploadImageIfLocal(uri, preferredBaseName = idPart)
        return item.copy(mediaUri = relative)
    }

    private suspend fun sanitizeForPod(items: List<WorkoutItem>): List<WorkoutItem> =
        items.map {
            try {
                ensureRemoteMedia(it)
            } catch (e: Exception) {
                Log.w("WorkoutViewModel", "Failed to sanitize item ${it.id} for pod, skipping media upload.", e)
                it
            }
        }

    private var storageRootCache: String? = null

    private suspend fun getOrFetchStorageRoot(): String? {
        storageRootCache?.let { return it }
        val webId = remoteDataSource.webId ?: return null
        val root = getStorageRootFromWebId(webId)
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

        val ext = mime.substringAfter('/', "bin")
        val baseName = preferredBaseName ?: UUID.randomUUID().toString()
        val fileName = "$baseName.$ext"

        // storage root + container
        val storageRoot = getOrFetchStorageRoot()
            ?: error("Storage root unavailable (not signed in yet?)")
        val container = storageRoot + IMAGES_DIR
        ensureContainer(container)

        val targetUrl = container + fileName

        val client = getUnsafeOkHttpClient()
        val at = remoteDataSource.accessToken ?: error("No access token")
        val jwk = remoteDataSource.signingJwk ?: error("No signing JWK")
        val dpop = buildResourceDPoP("PUT", targetUrl, at, jwk)

        val req = Request.Builder()
            .url(targetUrl)
            .header("Authorization", "DPoP $at")
            .header("DPoP", dpop)
            .header("Content-Type", mime)
            .put(bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Image upload failed: ${resp.code}")
        }

        return@withContext IMAGES_DIR + fileName
    }

    private fun syncRemoteImages(items: List<WorkoutItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("SolidImage", "Starting background image sync for ${items.size} items.")
            val loader = application.imageLoader

            items.forEach { item ->
                val uri = item.mediaUri
                if (uri.isNotBlank() && !uri.startsWith("content", ignoreCase = true)) {

                    val request = buildAuthorizedImageRequest(application, uri)
                    if (request != null) {
                        Log.d("SolidImage", "Enqueuing sync for: ${request.data}")
                        loader.enqueue(request)
                    } else {
                        Log.w("SolidImage", "Failed to build authorized request for $uri")
                    }
                }
            }
            Log.d("SolidImage", "Background image sync complete.")
        }
    }

    fun buildAuthorizedImageRequest(context: Context, mediaUri: String): ImageRequest? {
        if (mediaUri.isBlank()) return null

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
            .memoryCacheKey(MemoryCache.Key(fullUrl))
            .diskCacheKey(fullUrl)
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

                val msg = p.body?.string().orEmpty()

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
                        .header("Slug", containerUrl.removeSuffix("/").substringAfterLast('/'))
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
            repository.getWorkoutItemLiveData(id).firstOrNull()?.let {
                _workoutItem.value = it
                return@launch
            }

            val fromMerged = _allItems.value.find { it.id == id }
            if (fromMerged != null) {
                _workoutItem.value = fromMerged
            } else if (remoteDataSource.remoteAccessible()) {
                val remote = remoteDataSource.fetchRemoteItemList()
                val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()
                val merged = merge(remote, local)
                _allItems.value = merged.sortedByDescending { it.dateCreated }
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
                WorkoutItemViewModel(itemRepository, itemRemoteDataSource, application)
            }
        }
    }
}