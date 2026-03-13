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
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.tryRefreshTokens
import kotlinx.coroutines.flow.firstOrNull
import com.example.solidfit.data.RecentWebIdStore
import com.google.firebase.perf.FirebasePerformance
import com.hp.hpl.jena.query.QueryExecutionFactory
import com.hp.hpl.jena.query.QueryFactory
import com.hp.hpl.jena.rdf.model.ModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

suspend fun getOidcProviderFromWebId(webId: String): String = withContext(Dispatchers.IO) {
    val client = getUnsafeOkHttpClient()
    val req = Request.Builder()
        .url(webId)
        .addHeader("Accept", "text/turtle, application/ld+json;q=0.9, */*;q=0.1")
        .build()
    val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
    val stringAsByteArray = body.toByteArray()
    val utf8String = String(stringAsByteArray, Charsets.UTF_8)
    val inStream = utf8String.byteInputStream()
    val m = ModelFactory.createDefaultModel().read(inStream, null, "TURTLE")
    val queryString = "SELECT ?o\n" +
            "WHERE\n" +
            "{ ?s <http://www.w3.org/ns/pim/space#storage> ?o }"
    val q = QueryFactory.create(queryString)
    var result = ""
    try {
        val qexec = QueryExecutionFactory.create(q, m)
        val results = qexec.execSelect()
        while (results.hasNext()) {
            val soln = results.nextSolution()
            result = soln.getResource("o").toString()
            break
        }
    } catch (e: Exception) {
        throw Error("could not perform fetch with exception ${e.message}")
    }
    return@withContext result.ifBlank {
        val uri = Uri.parse(webId)
        val host = uri.host ?: ""

        val firstPath = uri.pathSegments[0] ?: ""
        if (firstPath.isBlank()) {
            host
        } else {
            "$host/$firstPath/"
        }
    }
}


class WorkoutItemViewModel(
    private val repository: WorkoutItemRepository,
    private val remoteDataSource: WorkoutItemRemoteDataSource,
    private val tokenStore: AuthTokenStore,
    private val application: Application
): ViewModel() {

    private var _allItems: MutableStateFlow<List<WorkoutItem>> = MutableStateFlow(listOf())
    val allItems: StateFlow<List<WorkoutItem>> get() = _allItems

    private val _workoutItem = MutableStateFlow<WorkoutItem?>(null)
    val workoutItem: StateFlow<WorkoutItem?> = _workoutItem

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private fun WorkoutItem.sortTime(): Long =
        if (datePerformed != 0L) datePerformed else dateCreated

    private val workoutComparator =
        compareByDescending<WorkoutItem> { it.sortTime() }
            .thenByDescending { it.dateCreated }
            .thenByDescending { it.dateModified }
            .thenBy { it.id } // final tie-breaker so order can’t drift

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.allWorkoutItemsAsFlow.collect { list ->
                val stable = list
                    .groupBy { it.id }
                    .map { (_, items) ->
                        items.maxWithOrNull(
                            compareBy<WorkoutItem> { it.dateModified }
                                .thenBy { if (it.datePerformed != 0L) it.datePerformed else it.dateCreated }
                        ) ?: items.first()
                    }
                    .sortedWith(
                        compareByDescending<WorkoutItem> { if (it.datePerformed != 0L) it.datePerformed else it.dateCreated }
                            .thenByDescending { it.dateCreated }
                            .thenByDescending { it.dateModified }
                            .thenBy { it.id }
                    )

                withContext(Dispatchers.Main) {
                    _allItems.value = stable
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = application as WorkoutItemSolidApplication
                val recentStore = RecentWebIdStore(app.applicationContext)

                val webIds = app.credentialClient.fetchWebIds()
                webIds.forEach { recentStore.add(it) }

                Log.d("CredentialManager", "Fetched ${webIds.size} webIds from service")
            } catch (t: Throwable) {
                Log.w("CredentialManager", "Failed to fetch webIds from service", t)
            }
        }
    }


//    fun remoteIsAvailable(): Boolean {
//        return remoteDataSource.remoteAccessible()
//    }

    private fun isLocalContentUri(uri: String): Boolean =
        uri.startsWith("content", ignoreCase = true)


    fun setRemoteRepositoryData(
        accessToken: String,
        signingJwk: String,
        webId: String,
        expirationTime: Long,
    ) {
        remoteDataSource.signingJwk = signingJwk
        remoteDataSource.webId = webId
        remoteDataSource.clearStorageCache()
        remoteDataSource.expirationTime = expirationTime
        remoteDataSource.accessToken = accessToken

        viewModelScope.launch(Dispatchers.IO) {
            try {
                getOidcProviderFromWebId(webId)
                Log.d("SolidImage", "Storage root primed in setRemoteRepositoryData.")

                withContext(Dispatchers.Main) {
                    _isReady.value = true
                }

            } catch (e: Exception) {
                Log.e("SolidImage", "Failed to prime storage root", e)
            }
        }
    }

    fun fetchAllOnceForBenchmark() {
        val trace = FirebasePerformance.getInstance().newTrace("solid_warm_fetch_trace")
        trace.start()
        viewModelScope.launch {
            try {
                // Ensure we have a valid token and storage URI
                if (!remoteDataSource.remoteAccessible()) {
                    Log.e("SolidPerf", "Solid remote is not accessible (missing token or WebID).")
                    trace.stop()
                    return@launch
                }

                // HEAD request to compare Last-Modified before doing a full fetch
                val remoteLastModified = remoteDataSource.fetchRemoteLastModified()
                val localLastModified = repository.getLastModified()
                Log.d("SolidPerf", "warm HEAD check — remote=$remoteLastModified local=$localLastModified")
                if (remoteLastModified != null && remoteLastModified == localLastModified) {
                    Log.d("SolidPerf", "Pod unchanged (Last-Modified matches). Skipping full fetch.")
                    trace.stop()
                    return@launch
                }

                // Perform the full fetch from the Pod container
                val remoteList = remoteDataSource.fetchRemoteItemList()
                if (remoteLastModified != null) {
                    repository.setLastModified(remoteLastModified)
                }

                Log.d("SolidPerf", "Successfully fetched ${remoteList.size} workouts from Solid Pod.")
                trace.stop()
            } catch (e: Exception) {
                Log.e("SolidPerf", "Error fetching from Solid Pod", e)
                trace.stop()
            }
        }
    }
    
//    suspend fun storeCredentialsFromServerJson(
//        tokenStore: AuthTokenStore,
//        raw: String
//    ): Boolean {
//        val trimmed = raw.trim()
//
//        if (trimmed.equals("access denied", ignoreCase = true)) return false
//
//        // If server still returns "access granted" without JSON, you can't store anything.
//        if (!trimmed.startsWith("{")) {
//            throw IllegalStateException("Expected JSON credentials, got: $trimmed")
//        }
//
//        val obj = JSONObject(trimmed)
//
//        // Your server payload keys (from the changes you’re making):
//        // status, webId, accessToken, refreshToken, expiresAt, signingKey
//        val status = obj.optString("status")
//        if (status.isNotBlank() && status != "granted") return false
//
//        val webId = obj.getString("webId")
//        val accessToken = obj.getString("accessToken")
//        val expiresAtSeconds = obj.optLong("expiresAt")
//        val expiresAtMs = expiresAtSeconds * 1000L
//        val signingKey = obj.optString("signingKey", "")
//        val refreshToken = obj.optString("refreshToken", "")
//
//        tokenStore.setWebId(webId)
//        tokenStore.setAccessToken(accessToken)
//        tokenStore.setTokenExpiresAt(expiresAtMs)
//
//        if (signingKey.isNotBlank() && signingKey != "null") {
//            tokenStore.setSigner(signingKey)
//        }
//        if (refreshToken.isNotBlank() && refreshToken != "null") {
//            tokenStore.setRefreshToken(refreshToken)
//        }
//
//        return true
//    }

    private fun normalizeWebIdForInrupt(webId: String): String {
        val w = webId.trim()
        // If it’s an Inrupt profile without a fragment, use the canonical WebID
        return if (w.startsWith("https://id.inrupt.com/") && !w.contains("#")) {
            "$w#me"
        } else w
    }


//    fun requestAccessAndSelectWebId(webId: String) {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                val app = application as WorkoutItemSolidApplication
//                val tokenStore = AuthTokenStore(app.applicationContext)
//                val recentStore = RecentWebIdStore(app.applicationContext)
//
//                val normalized = normalizeWebIdForInrupt(webId)
//
//                val json = app.credentialClient.requestCredentialsJson(normalized)
//                val obj = JSONObject(json)
//
//                when (obj.optString("status")) {
//                    "granted" -> {
//                        val accessToken = obj.optString("accessToken", "")
//                        if (accessToken.isBlank()) {
//                            Log.e("CredentialManager", "Granted but missing accessToken. Payload=$json")
//                            return@launch
//                        }
//
//                        tokenStore.setAccessToken(accessToken)
//                        val storedWebId = normalizeWebIdForInrupt(obj.optString("webId", normalized))
//                        tokenStore.setWebId(storedWebId)
//
//                        val refresh = obj.optString("refreshToken", "")
//                        if (refresh.isNotBlank() && refresh != "null") {
//                            tokenStore.setRefreshToken(refresh)
//                        }
//
//                        val expiresAtSeconds = obj.optLong("expiresAt", 0L)
//                        if (expiresAtSeconds > 0L) {
//                            val expiresAtMs = expiresAtSeconds * 1000L
//                            tokenStore.setTokenExpiresAt(expiresAtMs)
//                        }
//
//                        val signingKey = obj.optString("signingKey", "")
//                        if (signingKey.isNotBlank() && signingKey != "null") {
//                            tokenStore.setSigner(signingKey)
//                        }
//
//                        if (signingKey.isBlank() || signingKey == "null" || !signingKey.trim().startsWith("{")) {
//                            Log.e("CredentialManager", "Bad signingKey format (must be JSON JWK). signingKey=$signingKey")
//                            return@launch
//                        }
//
//                        // store as "recent"
//                        recentStore.add(normalized)
//
//                        // IMPORTANT: prime remoteDataSource so remote fetch/insert works immediately
//                        setRemoteRepositoryData(
//                            accessToken = accessToken,
//                            signingJwk = signingKey,
//                            webId = storedWebId,
//                            expirationTime = if (expiresAtSeconds > 0L) expiresAtSeconds * 1000L else 0L
//                        )
//
//                        // proceed with your existing workflow
//                        updateWebId(storedWebId)
//                    }
//
//                    "denied" -> {
//                        Log.d("CredentialManager", "Access denied for $normalized")
//                    }
//
//                    else -> {
//                        Log.e("CredentialManager", "Credential manager error: ${obj.optString("message")} payload=$json")
//                    }
//                }
//            } catch (t: Throwable) {
//                Log.e("CredentialManager", "Access request failed", t)
//            }
//        }
//    }


    fun updateWebId(webId: String) {
        runBlocking {
            delay(20000)
        }

        viewModelScope.launch {
            try {
                repository.insertWebId(webId)
            } catch (e: Exception) {
                Log.d("WorkoutItemViewModel", "WebID already exists. Proceeding.")
            }



            try {
                if (remoteDataSource.remoteAccessible()) {
                    // HEAD request to compare Last-Modified before doing a full fetch
                    val remoteLastModified = remoteDataSource.fetchRemoteLastModified()
                    val localLastModified = repository.getLastModified()
                    if (remoteLastModified != null && remoteLastModified == localLastModified) {
                        Log.d("WorkoutItemViewModel", "Pod unchanged (Last-Modified matches). Skipping full fetch.")
                        loadLocalData()
                        return@launch
                    }

                    val fetchTrace = FirebasePerformance.getInstance().newTrace("solid_cold_fetch_trace_real")
                    fetchTrace.start()
                    // Fetch the absolute truth from the Pod
                    val remote = remoteDataSource.fetchRemoteItemList()

                    fetchTrace.stop()

                    if (remoteLastModified != null) {
                        repository.setLastModified(remoteLastModified)
                    }

                    // Overwrite the local database entirely with the Pod's data
                    repository.overwriteModelWithList(remote)

                    // Sync media and update the UI
                    syncRemoteImages(remote)

                    withContext(Dispatchers.Main) {
                        _allItems.value = remote.sortedWith(workoutComparator)
                    }

                } else {
                    // Offline fallback: load local data
                    loadLocalData()
                }
            } catch (e: Exception) {
                Log.w("WorkoutItemViewModel", "Fetch from Pod failed. Falling back to local data.", e)
                loadLocalData()
            }
        }
    }

    private suspend fun loadLocalData() {
        val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()
        withContext(Dispatchers.Main) {
            _allItems.value = local.sortedWith(workoutComparator)
        }
    }

    fun insert(item: WorkoutItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // Create and start the trace exactly like the Firebase app
            val trace = com.google.firebase.perf.FirebasePerformance.getInstance().newTrace("solid_workout_insert_trace")
            trace.start()

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
                    val fullUrl = resolveMediaUrl(prepared.mediaUri)
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

                    pushRemoteWithRefreshRetry(sanitized)
                }
            } catch (t: Throwable) {
                Log.e("WorkoutViewModel", "Failed to insert and sync workout.", t)
            } finally {
                trace.stop()
            }
        }
    }

    fun delete(item: WorkoutItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaToDelete = item.mediaUri

            repository.deleteByUri(item.id)

            val remaining: List<WorkoutItem> =
                repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()

            withContext(Dispatchers.Main) {
                _allItems.value = remaining
            }

            pushRemoteWithRefreshRetry(remaining)

            try {
                deleteRemoteImageIfUnused(mediaToDelete, remaining)
            } catch (e: Exception) {
                Log.w("SolidImage", "Error while deleting remote image for workout ${item.id}", e)
            }
        }
    }

    private fun deleteRemoteImageIfUnused(
        deletedMediaUri: String,
        remainingItems: List<WorkoutItem>
    ) {
        if (deletedMediaUri.isBlank()) return
        if (isLocalContentUri(deletedMediaUri)) return

        val stillReferenced = remainingItems.any { it.mediaUri == deletedMediaUri }
        if (stillReferenced) return

        if (!remoteDataSource.remoteAccessible()) return

        val fullUrl = resolveMediaUrl(deletedMediaUri) ?: return
        val at = remoteDataSource.accessToken ?: return
        val jwk = remoteDataSource.signingJwk ?: return

        val dpop = buildResourceDPoP("DELETE", fullUrl, at, jwk)

        val req = Request.Builder()
            .url(fullUrl)
            .header("Authorization", "DPoP $at")
            .header("DPoP", dpop)
            .delete()
            .build()

        val client = getUnsafeOkHttpClient()
        client.newCall(req).execute().use { resp ->
            if (!(resp.isSuccessful || resp.code == 404)) {
                val msg = resp.body?.string().orEmpty()
                Log.w("SolidImage", "Failed to delete remote image (${resp.code}): $fullUrl $msg")
            } else {
                Log.d("SolidImage", "Deleted remote image: $fullUrl")
            }
        }

        val loader = application.imageLoader
        loader.memoryCache?.remove(MemoryCache.Key(fullUrl))
        loader.diskCache?.remove(fullUrl)
    }

//    suspend fun updateRemote() {
//        if (!remoteDataSource.remoteAccessible()) return
//        val list = repository.allWorkoutItemsAsFlow.firstOrNull().orEmpty()
//        val sanitized = sanitizeForPod(list)
//        pushRemoteWithRefreshRetry(sanitized)
//    }

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
                    pushRemoteWithRefreshRetry(sanitized)
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

//    private suspend fun sanitizeForPod(items: List<WorkoutItem>): List<WorkoutItem> =
//        items.map {
//            try {
//                ensureRemoteMedia(it)
//            } catch (e: Exception) {
//                Log.w("WorkoutViewModel", "Failed to sanitize item ${it.id} for pod, skipping media upload.", e)
//                it
//            }
//        }

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

    private suspend fun pushRemoteWithRefreshRetry(items: List<WorkoutItem>) {
        if (!remoteDataSource.remoteAccessible()) return


        try {
            remoteDataSource.updateRemoteItemList(items)
            return
        } catch (e: Exception) {
            // First failure: attempt refresh then retry once
            Log.w("WorkoutViewModel", "Remote update failed, attempting refresh+retry", e)
        }

        val newAccessToken = tryRefreshTokens(application.applicationContext, tokenStore)
        if (newAccessToken == null) {
            Log.w("WorkoutViewModel", "Refresh failed; remote update will be retried later")
            return
        }

        remoteDataSource.accessToken = newAccessToken

        val newExp = tokenStore.getTokenExpiresAt().firstOrNull()

//        if (!newAccessToken.isNullOrBlank()) remoteDataSource.accessToken = newAccessToken
        if (newExp != null && newExp > 0L) remoteDataSource.expirationTime = newExp

        try {
            remoteDataSource.updateRemoteItemList(items)
            Log.d("WorkoutViewModel", "Remote update succeeded on retry!")
        } catch (e2: Exception) {
            Log.e("WorkoutViewModel", "Remote update still failed after refresh", e2)
            // Don't crash; keep local data. You can queue a retry if you want.
        }
    }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WorkoutItemSolidApplication)
                val itemRepository = application.repository
                val itemRemoteDataSource = WorkoutItemRemoteDataSource(
                    externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                )
                val tokenStore = com.example.solidfit.data.AuthTokenStore(application.applicationContext)
                WorkoutItemViewModel(itemRepository, itemRemoteDataSource, tokenStore, application)
            }
        }
    }
}