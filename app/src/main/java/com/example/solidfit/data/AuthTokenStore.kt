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

  suspend fun clearAuth() {
    dataStore.edit {
      it[ACCESS_TOKEN] = ""
      it[ID_TOKEN] = ""
      it[REFRESH_TOKEN] = ""
      it[WEB_ID] = ""
      it[SIGNER] = ""
      it[TOKEN_EXPIRES_AT] = 0L
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
  }
}
