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

public class WorkoutItemRemoteDataSource(
  public var webId: String? = null,
  public var accessToken: String? = null,
  public var expirationTime: Long? = null,
  public var signingJwk: String? = null,
  private val externalScope: CoroutineScope,
) {
  private var latestList: List<WorkoutItem> = emptyList()

  private val latestListMutex: Mutex = Mutex()

  private fun setLatestList(items: List<WorkoutItem>) {
    latestList = items
  }

  public suspend fun updateRemoteItemList(items: List<WorkoutItem>) = withContext(Dispatchers.IO) {
    setLatestList(items)

    if (webId == null || accessToken == null || signingJwk == null || expirationTime == null) return@withContext
    if (!accessTokenIsValid()) return@withContext

    val client = OkHttpClient()
    val storageUri = getStorage(webId!!)
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
    }

    val bOutputStream = ByteArrayOutputStream()
    model.write(bOutputStream, "TURTLE", null)
    val rBody = bOutputStream.toByteArray()
      .toRequestBody(null, 0, bOutputStream.size())

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



  public suspend fun fetchRemoteItemList(): List<WorkoutItem> = withContext(Dispatchers.IO) {
    if (webId == null || accessToken == null || signingJwk == null || !accessTokenIsValid()) {
      return@withContext latestListMutex.withLock { this@WorkoutItemRemoteDataSource.latestList }
    }

    val storageUri = getStorage(webId!!)
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
