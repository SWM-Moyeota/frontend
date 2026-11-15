package com.moyeota.app

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.moyeota.domain.model.GoogleCredentialProvider

/** Activity 수명에서 호출한다. 공급자 토큰은 서버에 전달한 뒤 보관하지 않는다. */
class GoogleSignIn(private val activity: Activity) : GoogleCredentialProvider {
    override suspend fun clearCredentialState() {
        CredentialManager.create(activity).clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
    }
    override suspend fun getIdToken(): String? {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(
                activity, GetCredentialRequest.Builder().addCredentialOption(option).build(),
            ).credential
            check(credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: GetCredentialCancellationException) {
            null
        }
    }
}
