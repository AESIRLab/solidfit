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
import com.example.solidfit.tryRefreshTokens

public class SolidUtilities(
  context: Context,
) {
  private val tokenStore: AuthTokenStore = AuthTokenStore(context)

  public suspend fun updateSolidDataset(items: List<WorkoutItem>): Int {
    // IMPORTANT: don't use runBlocking inside a suspend function
    var accessToken = tokenStore.getAccessToken().first()
    val storageUri = runBlocking { val webId = tokenStore.getWebId().first(); getStorage(webId) }
    val signingJwk = tokenStore.getSigner().first()

    if (storageUri.isBlank() || accessToken.isBlank()) return 600

    val client = OkHttpClient.Builder().build()
    val resourceUri = "${storageUri}${ABSOLUTE_URI}"

    // build model → rBody (same as you already do)
    val model = ModelFactory.createDefaultModel()
    // ... your model setup exactly as-is ...
    val bOutputStream = ByteArrayOutputStream()
    model.write(bOutputStream, "TURTLE", null)
    val rBody = bOutputStream.toByteArray().toRequestBody(null, 0, bOutputStream.size())

    fun makePut(token: String) =
      generatePutRequest(resourceUri, rBody, token, signingJwk)

    // 1) try once
    var response = client.newCall(makePut(accessToken)).execute()
    var code = response.code
    response.close()

    // 2) if unauthorized, refresh + retry once
    if (code == 401 || code == 403) {
      val refreshed = tryRefreshTokens(tokenStore) // <-- your new refresh function
      if (!refreshed) return code

      accessToken = tokenStore.getAccessToken().first()
      response = client.newCall(makePut(accessToken)).execute()
      code = response.code
      response.close()
    }

    return code
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
