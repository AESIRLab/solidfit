package com.example.solidfit.data

import android.util.Log
import com.example.solidfit.model.WorkoutItem
import com.hp.hpl.jena.rdf.model.ModelFactory
import java.io.ByteArrayOutputStream
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.collections.List
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.solidfit.data.Utilities.Companion.ABSOLUTE_URI
import com.example.solidfit.data.Utilities.Companion.resourceToWorkoutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private const val TAG = "WorkoutItemRemoteDataSource"

public class WorkoutItemRemoteDataSource(
  public var webId: String? = null,
  public var accessToken: String? = null,
  public var expirationTime: Long? = null,
  public var signingJwk: String? = null,
  private val externalScope: CoroutineScope,
) {
  private var latestList: List<WorkoutItem> = emptyList()
  private var cachedStorageUri: String? = null

  private val latestListMutex: Mutex = Mutex()

  public fun clearStorageCache() { cachedStorageUri = null }

  private suspend fun resolveStorageUri(): String {
    cachedStorageUri?.let { return it }
    val uri = getStorage(webId!!)
    cachedStorageUri = uri
    return uri
  }

  private fun setLatestList(items: List<WorkoutItem>) {
    latestList = items
  }

  public suspend fun updateRemoteItemList(items: List<WorkoutItem>) = withContext(Dispatchers.IO) {
    setLatestList(items)

    if (webId == null || accessToken == null || signingJwk == null || expirationTime == null) return@withContext
    if (!accessTokenIsValid()) return@withContext

    val client = OkHttpClient()
    val storageUri = resolveStorageUri()
    val model = ModelFactory.createDefaultModel()
    val resourceUri = "${storageUri}$ABSOLUTE_URI"

    model.setNsPrefix("acp", Utilities.NS_ACP)
    model.setNsPrefix("acl", Utilities.NS_ACL)
    model.setNsPrefix("ldp", Utilities.NS_LDP)
    model.setNsPrefix("skos", Utilities.NS_SKOS)
    model.setNsPrefix("xsd", Utilities.NS_XSD)
    model.setNsPrefix("ci", Utilities.NS_WorkoutItem)

    val ciName = model.createProperty(Utilities.NS_WorkoutItem + "name")
    val ciDateCreated = model.createProperty(Utilities.NS_WorkoutItem + "dateCreated")
    val ciDateModified = model.createProperty(Utilities.NS_WorkoutItem + "dateModified")
    val ciDatePerformed = model.createProperty(Utilities.NS_WorkoutItem + "datePerformed")
    val ciQuantity = model.createProperty(Utilities.NS_WorkoutItem + "quantity")
    val ciDuration = model.createProperty(Utilities.NS_WorkoutItem + "duration")
    val ciHeartRate = model.createProperty(Utilities.NS_WorkoutItem + "heartRate")
    val ciWorkoutType = model.createProperty(Utilities.NS_WorkoutItem + "workoutType")
    val ciNotes = model.createProperty(Utilities.NS_WorkoutItem + "notes")
    val ciMediaUri = model.createProperty(Utilities.NS_WorkoutItem + "mediaUri")
    val ciDetailsJson = model.createProperty(Utilities.NS_WorkoutItem + "detailsJson")

    latestList.forEach { ci ->
      val id = ci.id
      val mThingUri = model.createResource("$resourceUri#$id")
      mThingUri.addLiteral(ciName, ci.name)
      mThingUri.addLiteral(ciDateCreated, ci.dateCreated)
      mThingUri.addLiteral(ciDateModified, ci.dateModified)
      mThingUri.addLiteral(ciDatePerformed, ci.datePerformed)
      mThingUri.addLiteral(ciQuantity, ci.quantity)
      mThingUri.addLiteral(ciDuration, ci.duration)
      mThingUri.addLiteral(ciHeartRate, ci.heartRate)
      mThingUri.addLiteral(ciWorkoutType, ci.workoutType)
      mThingUri.addLiteral(ciNotes, ci.notes)
      mThingUri.addLiteral(ciMediaUri, ci.mediaUri)
      mThingUri.addLiteral(ciDetailsJson, ci.detailsJson)
    }

    val bOutputStream = ByteArrayOutputStream()
    model.write(bOutputStream, "TURTLE", null)
    val rBody = bOutputStream.toByteArray()
      .toRequestBody(null, 0, bOutputStream.size())
    Log.d(TAG, resourceUri)
    val putRequest = generatePutRequest(resourceUri, rBody, accessToken!!, signingJwk!!)
    client.newCall(putRequest).execute().use { putResponse ->
      if (putResponse.code !in 200..299) {
        throw Error("remote update failed: ${putResponse.code} ${putResponse.message}")
      }
    }
  }


  private fun accessTokenIsValid(): Boolean {
    val skew = 60_000L
    return expirationTime != null &&
            expirationTime!! > System.currentTimeMillis() + skew
  }



  public suspend fun fetchRemoteLastModified(): String? = withContext(Dispatchers.IO) {
    if (webId == null || accessToken == null || signingJwk == null || !accessTokenIsValid()) {
      return@withContext null
    }
    val storageUri = resolveStorageUri()
    val resourceUri = "${storageUri}$ABSOLUTE_URI"
    val headRequest = generateHeadRequest(resourceUri, accessToken!!, signingJwk!!)
    val client = OkHttpClient()
    client.newCall(headRequest).execute().use { response ->
      Log.d(TAG, "HEAD $resourceUri → ${response.code}")
      if (response.code !in 200..299) {
        Log.d(TAG, "fetchRemoteLastModified failed: ${response.code} ${response.message}")
        return@withContext null
      }
      val lm = response.header("Last-Modified")
      Log.d(TAG, "Remote Last-Modified: $lm")
      return@withContext lm
    }
  }

  public suspend fun fetchRemoteItemList(): List<WorkoutItem> = withContext(Dispatchers.IO) {
    if (webId == null || accessToken == null || signingJwk == null || !accessTokenIsValid()) {
      return@withContext latestListMutex.withLock { this@WorkoutItemRemoteDataSource.latestList }
    }

    val storageUri = resolveStorageUri()
    val getRequest = generateGetRequest("${storageUri}$ABSOLUTE_URI", accessToken!!, signingJwk!!)
    val client = OkHttpClient()

    client.newCall(getRequest).execute().use { response ->
      if (response.code !in 200..299) {
        Log.d(
          "GENERATED_DATA_SOURCE_FILE",
          "fetchRemoteItemList failed: ${response.code} ${response.message}"
        )
        return@withContext latestListMutex.withLock { this@WorkoutItemRemoteDataSource.latestList }
      }

      val model = ModelFactory.createDefaultModel()
      model.setNsPrefix("acp", Utilities.NS_ACP)
      model.setNsPrefix("acl", Utilities.NS_ACL)
      model.setNsPrefix("ldp", Utilities.NS_LDP)
      model.setNsPrefix("skos", Utilities.NS_SKOS)
      model.setNsPrefix("xsd", Utilities.NS_XSD)
      model.setNsPrefix("ci", Utilities.NS_WorkoutItem)

      val ciName = model.createProperty(Utilities.NS_WorkoutItem + "name")

      response.body?.byteStream()?.let { bodyStream ->
        model.read(bodyStream, null, "TURTLE")
      }

      val res = model.listResourcesWithProperty(ciName)
      val list = mutableListOf<WorkoutItem>()
      while (res.hasNext()) {
        val nextResource = res.nextResource()
        list.add(resourceToWorkoutItem(nextResource))
      }

      latestListMutex.withLock { latestList = list }
      return@withContext list
    }
  }




  public fun remoteAccessible(): Boolean = (accessToken != null &&
                  webId != null &&
                  expirationTime != null &&
                  expirationTime!! > System.currentTimeMillis() &&
                  signingJwk != null)
}
