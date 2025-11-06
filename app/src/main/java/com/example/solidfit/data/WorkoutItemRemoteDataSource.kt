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

  public suspend fun updateRemoteItemList(items: List<WorkoutItem>) {
    setLatestList(items)
    if (webId != null && accessToken != null && accessTokenIsValid()) {
      val client = OkHttpClient()
      externalScope.launch { 
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
      mThingUri.addLiteral(ciQuantity, ci.quantity)
      mThingUri.addLiteral(ciDuration, ci.duration)
      mThingUri.addLiteral(ciHeartRate, ci.heartRate)
      mThingUri.addLiteral(ciWorkoutType, ci.workoutType)
      mThingUri.addLiteral(ciNotes, ci.notes)
      mThingUri.addLiteral(ciMediaUri, ci.mediaUri)
      }
      val bOutputStream = ByteArrayOutputStream()
      model.write(bOutputStream, "TURTLE", null)
      val rBody = bOutputStream.toByteArray().toRequestBody(null, 0, bOutputStream.size())
      val putRequest = generatePutRequest( resourceUri, rBody, accessToken!!, signingJwk!!)
      val putResponse = client.newCall(putRequest).execute()
      if (putResponse.code !in 200..299) {
        throw Error("remote update failed: ${putResponse.message}")
      }
    }
    }
  }

  private fun accessTokenIsValid(): Boolean = !(expirationTime == null || expirationTime!! <
      System.currentTimeMillis())

  public suspend fun fetchRemoteItemList(): List<WorkoutItem> {
    if (webId != null && accessToken != null && accessTokenIsValid()) {
    val itemList = externalScope.async {
    val storageUri = getStorage(webId!!)
    val getRequest = generateGetRequest("${storageUri}$ABSOLUTE_URI", accessToken!!, signingJwk!!)
    val client = OkHttpClient.Builder().build()
    val response = client.newCall(getRequest).execute()
    if (response.code in 200..299) {
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
    val ciQuantity = model.createProperty(Utilities.NS_WorkoutItem + "quantity")
    val ciDuration = model.createProperty(Utilities.NS_WorkoutItem + "duration")
    val ciHeartRate = model.createProperty(Utilities.NS_WorkoutItem + "heartRate")
    val ciWorkoutType = model.createProperty(Utilities.NS_WorkoutItem + "workoutType")
    val ciNotes = model.createProperty(Utilities.NS_WorkoutItem + "notes")
    val ciMediaUri = model.createProperty(Utilities.NS_WorkoutItem + "mediaUri")
    val body = response.body!!.string().byteInputStream()
    model.read(body, null, "TURTLE")
    val res = model.listResourcesWithProperty(ciName)
    val itemList = mutableListOf<WorkoutItem>()
    while (res.hasNext()) {
    val nextResource = res.nextResource()
    itemList.add(resourceToWorkoutItem(nextResource))
    }
    latestListMutex.withLock { latestList = itemList }
    } else { Log.d("GENERATED_DATA_SOURCE_FILE", "oops") }
    return@async this@WorkoutItemRemoteDataSource.latestList
    }.await()
    return itemList
    } else {
    return latestListMutex.withLock { this.latestList }
    }
  }

  public fun remoteAccessible(): Boolean = (accessToken != null &&
                  webId != null &&
                  expirationTime != null &&
                  expirationTime!! > System.currentTimeMillis() &&
                  signingJwk != null)
}
