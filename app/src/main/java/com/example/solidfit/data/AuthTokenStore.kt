package com.example.solidfit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.Preferences.Key
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlin.String
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class AuthTokenStore(
  public val context: Context,
) {
  private val dataStore: DataStore<Preferences> = context.dataStore

  public suspend fun setWebId(webId: String) {
    dataStore.edit { it[WEB_ID] = webId }
  }

  public fun getWebId(): Flow<String> = dataStore.data.map { it[WEB_ID] ?: "" }

  public suspend fun setAccessToken(accessToken: String) {
    dataStore.edit { it[ACCESS_TOKEN] = accessToken }
  }

  public fun getAccessToken(): Flow<String> = dataStore.data.map { it[ACCESS_TOKEN] ?: "" }

  public suspend fun setRefreshToken(refreshToken: String) {
    dataStore.edit { it[REFRESH_TOKEN] = refreshToken }
  }

  public fun getRefreshToken(): Flow<String> = dataStore.data.map { it[REFRESH_TOKEN] ?: "" }

  public suspend fun setIdToken(idToken: String) {
    dataStore.edit { it[ID_TOKEN] = idToken }
  }

  public fun getIdToken(): Flow<String> = dataStore.data.map { it[ID_TOKEN] ?: "" }

  public suspend fun setClientId(clientId: String) {
    dataStore.edit { it[CLIENT_ID] = clientId }
  }

  public fun getClientId(): Flow<String> = dataStore.data.map { it[CLIENT_ID] ?: "" }

  public suspend fun setClientSecret(clientSecret: String) {
    dataStore.edit { it[CLIENT_SECRET] = clientSecret }
  }

  public fun getClientSecret(): Flow<String> = dataStore.data.map { it[CLIENT_SECRET] ?: "" }

  public suspend fun setTokenUri(tokenUri: String) {
    dataStore.edit { it[TOKEN_URI] = tokenUri }
  }

  public fun getTokenUri(): Flow<String> = dataStore.data.map { it[TOKEN_URI] ?: "" }

  public suspend fun setCodeVerifier(codeVerifier: String) {
    dataStore.edit { it[CODE_VERIFIER] = codeVerifier }
  }

  public fun getTokenExpiresAt(): Flow<Long> =
    dataStore.data.map { it[TOKEN_EXPIRES_AT] ?: 0L }

  public suspend fun setTokenExpiresAt(expiresAtMillis: Long) {
    dataStore.edit { it[TOKEN_EXPIRES_AT] = expiresAtMillis }
  }

  public fun getCodeVerifier(): Flow<String> = dataStore.data.map { it[CODE_VERIFIER] ?: "" }

  public suspend fun setOidcProvider(oidcProvider: String) {
    dataStore.edit { it[OIDC_PROVIDER] = oidcProvider }
  }

  public fun getOidcProvider(): Flow<String> = dataStore.data.map { it[OIDC_PROVIDER] ?: "" }

  public suspend fun setRedirectUri(redirectUri: String) {
    dataStore.edit { it[REDIRECT_URI] = redirectUri }
  }

  public fun getRedirectUri(): Flow<String> = dataStore.data.map { it[REDIRECT_URI] ?: "" }

  public suspend fun setSigner(Signer: String) {
    dataStore.edit { it[SIGNER] = Signer }
  }

  public fun getSigner(): Flow<String> = dataStore.data.map { it[SIGNER] ?: "" }

  public suspend fun setSummaryText(summaryText: String) {
    dataStore.edit { it[SUMMARY_TEXT] = summaryText }
  }

  public fun getSummaryText(): Flow<String> = dataStore.data.map { it[SUMMARY_TEXT] ?: "" }

  /** Where the current session came from: [AUTH_SOURCE_SCM], [AUTH_SOURCE_BROWSER], or "" when signed out. */
  public fun getAuthSource(): Flow<String> = dataStore.data.map { it[AUTH_SOURCE] ?: "" }

  public suspend fun setAuthSource(source: String) {
    dataStore.edit { it[AUTH_SOURCE] = source }
  }

  /** When the current access token was received, to size the SCM re-request threshold. */
  public fun getTokenReceivedAt(): Flow<Long> = dataStore.data.map { it[TOKEN_RECEIVED_AT] ?: 0L }

  /** Saves an SCM grant: token, its DPoP key and expiry change together. */
  suspend fun saveScmSession(webId: String, accessToken: String, dpopKey: String, expiresAt: Long, receivedAt: Long) {
    dataStore.edit {
      it[WEB_ID] = webId
      it[ACCESS_TOKEN] = accessToken
      it[SIGNER] = dpopKey
      it[TOKEN_EXPIRES_AT] = expiresAt
      it[TOKEN_RECEIVED_AT] = receivedAt
      it[REFRESH_TOKEN] = ""
      it[AUTH_SOURCE] = AUTH_SOURCE_SCM
      it[SIGN_IN_PROBLEM] = ""
      it[SIGN_IN_PROBLEM_WEBID] = ""
    }
  }

  /** Why the user was signed out of an SCM session, shown on the sign-in problem screen. */
  public fun getSignInProblem(): Flow<Pair<String, String>> =
    dataStore.data.map { (it[SIGN_IN_PROBLEM] ?: "") to (it[SIGN_IN_PROBLEM_WEBID] ?: "") }

  suspend fun setSignInProblem(reason: String, webId: String) {
    dataStore.edit {
      it[SIGN_IN_PROBLEM] = reason
      it[SIGN_IN_PROBLEM_WEBID] = webId
    }
  }

  /** The WebID whose SCM approval or login is in progress, so a restart can retry the request. */
  public fun getWaitingForScm(): Flow<String> = dataStore.data.map { it[WAITING_FOR_SCM_WEBID] ?: "" }

  suspend fun setWaitingForScm(webId: String) {
    dataStore.edit { it[WAITING_FOR_SCM_WEBID] = webId }
  }

  suspend fun clearAuth() {
    dataStore.edit {
      it[ACCESS_TOKEN] = ""
      it[ID_TOKEN] = ""
      it[REFRESH_TOKEN] = ""
      it[WEB_ID] = ""
      it[SIGNER] = ""
      it[TOKEN_EXPIRES_AT] = 0L
      it[TOKEN_RECEIVED_AT] = 0L
      it[AUTH_SOURCE] = ""
    }
  }

  public companion object {
    public val WEB_ID: Key<String> = stringPreferencesKey("web_id")

    public val ACCESS_TOKEN: Key<String> = stringPreferencesKey("access_token")

    public val REFRESH_TOKEN: Key<String> = stringPreferencesKey("refresh_token")

    public val ID_TOKEN: Key<String> = stringPreferencesKey("id_token")

    public val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")

    public val CLIENT_ID: Key<String> = stringPreferencesKey("client_id")

    public val CLIENT_SECRET: Key<String> = stringPreferencesKey("client_secret")

    public val TOKEN_URI: Key<String> = stringPreferencesKey("token_uri")

    public val CODE_VERIFIER: Key<String> = stringPreferencesKey("code_verifier")

    public val OIDC_PROVIDER: Key<String> = stringPreferencesKey("oidc_provider")

    public val REDIRECT_URI: Key<String> = stringPreferencesKey("redirect_uri")

    public val SIGNER: Key<String> = stringPreferencesKey("signer")

    public val SUMMARY_TEXT: Key<String> = stringPreferencesKey("summary_text")

    public val AUTH_SOURCE: Key<String> = stringPreferencesKey("auth_source")

    public val TOKEN_RECEIVED_AT: Key<Long> = longPreferencesKey("token_received_at")

    public val SIGN_IN_PROBLEM: Key<String> = stringPreferencesKey("sign_in_problem")

    public val SIGN_IN_PROBLEM_WEBID: Key<String> = stringPreferencesKey("sign_in_problem_webid")

    public val WAITING_FOR_SCM_WEBID: Key<String> = stringPreferencesKey("waiting_for_scm_webid")

    public const val AUTH_SOURCE_SCM: String = "scm"

    public const val AUTH_SOURCE_BROWSER: String = "browser"
  }
}
