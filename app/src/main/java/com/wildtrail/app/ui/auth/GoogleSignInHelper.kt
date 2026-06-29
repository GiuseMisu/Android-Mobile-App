package com.wildtrail.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInHelper(private val context: Context) {

    suspend fun requestIdToken(serverClientId: String): Result<String> = runCatching {
        require(serverClientId.isNotBlank()) {
            "Google Sign-In isn't configured. Add GOOGLE_WEB_CLIENT_ID to local.properties."
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = CredentialManager.create(context).getCredential(
            context = context,
            request = request,
        )
        val credential = response.credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type ${credential.javaClass.simpleName}"
        }
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
