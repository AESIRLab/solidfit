package com.example.solidfit.data

import android.content.Context
import com.example.solidfit.model.WorkoutItem
import com.hp.hpl.jena.query.QueryExecutionFactory
import com.hp.hpl.jena.query.QueryFactory
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.UUID
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.example.solidfit.data.Utilities.Companion.ABSOLUTE_URI

public class SolidUtilities(
  context: Context,
) {
  private val tokenStore: AuthTokenStore = AuthTokenStore(context)

  public suspend fun updateSolidDataset(items: List<WorkoutItem>): Int {
    val accessToken = runBlocking { tokenStore.getAccessToken().first() }
    val storageUri = runBlocking { val webId = tokenStore.getWebId().first(); getStorage(webId) }
    val signingJwk = runBlocking { tokenStore.getSigner().first() }
    if (storageUri != "" && accessToken != "") {
      val client = OkHttpClient.Builder().build()
      val resourceUri = "${storageUri}${ABSOLUTE_URI}"
      val model = ModelFactory.createDefaultModel()
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
      items.forEach { ci -> 
      val id = ci.id
      val mThingUri = model.createResource("$resourceUri#$id")
      mThingUri.addLiteral(ciName, ci.name)
      mThingUri.addLiteral(ciDateCreated, ci.dateCreated)
      mThingUri.addLiteral(ciDatePerformed, ci.datePerformed)
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
      val putRequest = generatePutRequest(resourceUri, rBody, accessToken, signingJwk)
      val putResponse = client.newCall(putRequest).execute()
      return putResponse.code
    } else {
      return 600
    }
  }

  public suspend fun regenerateRefreshToken() {
    val tokenUri = runBlocking { tokenStore.getTokenUri().first() }
    val refreshToken = runBlocking { tokenStore.getRefreshToken().first() }
    val clientId = runBlocking { tokenStore.getClientId().first() }
    val accessToken = runBlocking { tokenStore.getAccessToken().first() }
    val clientSecret = runBlocking { tokenStore.getClientSecret().first() }
    val signingJwk = runBlocking { tokenStore.getSigner().first() }
    val formBody = FormBody.Builder()
    .addEncoded("grant_type", "refresh_token")
    .addEncoded("refresh_token", refreshToken)
    .addEncoded("client_id", clientId)
    .addEncoded("client_secret", clientSecret)
    .addEncoded("scope", "openid+offline_access+webid")
    .build()
    val request = generatePostRequest(tokenUri, formBody, accessToken, signingJwk)
    val client = OkHttpClient()
    val response = client.newCall(request).execute()
    val body = response.body!!.string()
    if (response.code in 400..499) {
      throw Error("could not refresh token sad")
    } else {
      val jsonResponse = JSONObject(body)
      val newAccessToken = jsonResponse.getString("access_token")
      val newIdToken = jsonResponse.getString("id_token")
      val newRefreshToken = jsonResponse.getString("refresh_token")
      tokenStore.setIdToken(newIdToken)
      tokenStore.setRefreshToken(newRefreshToken)
      tokenStore.setAccessToken(newAccessToken)
    }
  }

  public suspend fun checkStorage(
    storageUri: String,
    accessToken: String,
    signingJwk: String,
  ): String {
    val client = OkHttpClient()
    val request = generateGetRequest("$storageUri$ABSOLUTE_URI", accessToken, signingJwk)
    val response = client.newCall(request).execute()
    if (response.code in 400..499) {
      return ""
    }
    val body = response.body!!.string()
    return body
  }
}

public suspend fun getStorage(webId: String): String {
  val client = OkHttpClient()
  val webIdRequest = Request.Builder().url(webId).build()
  val webIdResponse = client.newCall(webIdRequest).execute()
  val responseString = webIdResponse.body!!.string()
  val byteArray = responseString.toByteArray()
  val inStream = String(byteArray).byteInputStream()
  val m = ModelFactory.createDefaultModel().read(inStream, null, "TURTLE")
  val queryString = "SELECT ?o\n" +
                  "WHERE\n" +
                  "{ ?s <http://www.w3.org/ns/pim/space#storage> ?o }"
  val q = QueryFactory.create(queryString)
  var storage = ""
  try {
  val qexec = QueryExecutionFactory.create(q, m)
  val results = qexec.execSelect()
  while (results.hasNext()) {
  val soln = results.nextSolution()
  storage = soln.getResource("o").toString()
  break
  }
  } catch (e: Exception) {
  }
  return storage
}

public fun generateGetRequest(
  resourceUri: String,
  accessToken: String,
  signingJwk: String,
): Request = Request.Builder().url(resourceUri).addHeader("DPoP", generateCustomToken("GET",
    resourceUri, signingJwk)
).addHeader("Authorization", "DPoP " +
    "$accessToken").addHeader("Content-Type", "text/turtle").addHeader("Link",
    "<http://www.w3.org/ns/ldp#Resource>;rel=\"type\"").method("GET", null).build()

private fun generateCustomToken(
  method: String,
  uri: String,
  signingJwk: String,
): String {
  val parsedSigningJwk = JWK.parse(signingJwk)
  val jsonObj = parsedSigningJwk.toJSONObject()
  val parsedKey = ECKey.parse(jsonObj)
  val ecPublicJWK = parsedKey.toPublicJWK()
  val signer = ECDSASigner(parsedKey)
  val body = JWTClaimsSet.Builder().claim("htu", uri).claim("htm",
      method).issueTime(Calendar.getInstance().time).jwtID(UUID.randomUUID().toString()).build()
  val header =
      JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType("dpop+jwt")).jwk(ecPublicJWK).build()
  val signedJWT = SignedJWT(header, body)
  signedJWT.sign(signer)
  return signedJWT.serialize()
}

public fun generatePutRequest(
  resourceUri: String,
  rBody: RequestBody,
  accessToken: String,
  signingJwk: String,
): Request = Request.Builder().url(resourceUri).addHeader("DPoP", generateCustomToken("PUT",
    resourceUri, signingJwk)
).addHeader("Authorization", "DPoP " +
    "$accessToken").addHeader("content-type", "text/turtle").addHeader("Link",
    "<http://www.w3.org/ns/ldp#Resource>;rel=\"type\"").method("PUT", rBody).build()

public fun generatePostRequest(
  tokenUri: String,
  formBody: RequestBody,
  accessToken: String,
  signingJwk: String,
): Request = Request.Builder().url(tokenUri).addHeader("DPoP", generateCustomToken("POST", tokenUri,
    signingJwk)
).addHeader("Authorization", "DPoP " + "$accessToken").addHeader("content-type",
    "text/turtle").addHeader("Link",
    "<http://www.w3.org/ns/ldp#Resource>;rel=\"type\"").method("POST", formBody).build()
