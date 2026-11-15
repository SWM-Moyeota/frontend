package com.moyeota.domain.model

enum class SocialProvider { GOOGLE, APPLE }
enum class LoginMethodStatus { ACTIVE, REVOKED }
data class LoginMethod(val provider: SocialProvider, val status: LoginMethodStatus)
data class LoginMethods(val hasPassword: Boolean, val methods: List<LoginMethod>)
data class LinkVerification(val token: String, val expiresInSeconds: Int)

/** SDK는 앱 계층에만 있으며 취소 시 null을 반환한다. 토큰은 저장하지 않는다. */
fun interface GoogleCredentialProvider {
    suspend fun getIdToken(): String?
    suspend fun clearCredentialState() {}
}
