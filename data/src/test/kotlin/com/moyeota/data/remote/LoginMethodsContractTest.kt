package com.moyeota.data.remote

import com.moyeota.data.remote.dto.*
import com.moyeota.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class LoginMethodsContractTest {
    @Test fun `연결 API는 가입 SMS와 분리하고 등록번호를 본문에 보내지 않는다`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val body = when {
                request.url.encodedPath.endsWith("/sms/verify") -> """{"verificationToken":"proof","expiresInSeconds":300}"""
                request.method == "GET" -> """{"hasPassword":false,"methods":[{"provider":"APPLE","status":"ACTIVE"},{"provider":"GOOGLE","status":"REVOKED"}]}"""
                else -> ""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("OK")
                .code(if (body.isEmpty()) 204 else 200).body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(UserApi::class.java)
        val methods = api.getLoginMethods().toDomain()
        api.sendLoginMethodCode()
        val verified = api.verifyLoginMethodCode(LinkCodeDto("123456"))
        api.linkGoogle(GoogleLinkDto("google-token", verified.verificationToken))
        assertFalse(methods.hasPassword)
        assertEquals(LoginMethodStatus.REVOKED, methods.methods[1].status)
        assertEquals(listOf("/api/v1/users/me/login-methods", "/api/v1/users/me/login-methods/sms/send",
            "/api/v1/users/me/login-methods/sms/verify", "/api/v1/users/me/login-methods/google"), requests.map { it.url.encodedPath })
        assertEquals(0L, requests[1].body?.contentLength())
        fun body(index: Int) = Buffer().also { requests[index].body!!.writeTo(it) }.readUtf8()
        assertEquals("""{"code":"123456"}""", body(2))
        assertEquals("""{"idToken":"google-token","verificationToken":"proof"}""", body(3))
        assertFalse(requests.any { it.url.toString().contains("token") || it.url.query != null })
    }
}
