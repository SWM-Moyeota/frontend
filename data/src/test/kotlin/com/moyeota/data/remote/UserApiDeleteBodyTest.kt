package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AccountDeletionRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE

/**
 * **회원 탈퇴 요청이 본문을 싣고 DELETE 로 나가는지**를 실제 Retrofit 으로 확인한다.
 *
 * ### 왜 리플렉션 검사(AuthenticatedPathContractTest)로는 부족한가
 * Retrofit 의 `@DELETE` 는 `hasBody = false` 라 [Body] 와 함께 쓸 수 없는데, 그 조합을 **컴파일러가
 * 막지 않는다.** 터지는 건 Retrofit 이 서비스 메서드를 파싱하는 **실행 시점**이고
 * (`IllegalArgumentException: Non-body HTTP method cannot contain @Body or @Field parameters.`),
 * 그 시점은 사용자가 「탈퇴하기」를 누른 순간이다. 애노테이션 값만 읽는 검사는
 * "`@HTTP` 가 붙어 있다"까지만 말해 주고 Retrofit 이 그것을 **받아들이는지**는 말해 주지 않는다.
 *
 * ### 서버 없이 확인하는 방법
 * OkHttp 애플리케이션 인터셉터가 `chain.proceed()` 를 **부르지 않고** 응답을 직접 만들어 돌려주면
 * 네트워크가 전혀 일어나지 않는다. 그래서 MockWebServer 의존성 없이 (a) Retrofit 이 메서드를
 * 받아들이는지, (b) 실제 요청의 메서드·경로, (c) **본문 JSON**, (d) 204 처리까지 전부 볼 수 있다.
 */
class UserApiDeleteBodyTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 나간 요청을 붙잡아 두고 정해진 응답을 돌려주는 Retrofit. `chain.proceed()` 를 부르지 않으므로
     * 소켓이 열리지 않는다 — [baseUrl] 은 형식만 맞으면 된다.
     *
     * `validateEagerly(true)`: [UserApi] 의 **모든** 메서드를 `create()` 시점에 파싱한다.
     * 어느 메서드든 Retrofit 이 거부하면 테스트가 호출 전에 실패한다.
     */
    private fun <T> service(
        service: Class<T>,
        status: Int = 204,
        body: String = "",
        captured: MutableList<Request> = mutableListOf(),
    ): T {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    captured += chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message(if (status == 204) "No Content" else "Response")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .validateEagerly(true)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(service)
    }

    private fun Request.bodyAsString(): String? = body?.let { requestBody ->
        Buffer().also { requestBody.writeTo(it) }.readUtf8()
    }

    /**
     * 핵심 검사. 서버가 비밀번호를 `@RequestBody` 로 받으므로 본문이 **반드시** 실려야 하고,
     * 동사는 서버 `@DeleteMapping` 에 맞춰 DELETE 여야 한다.
     *
     * 이 테스트가 깨지는 가장 흔한 경우는 누군가 `@HTTP` 를 `@DELETE` 로 "정리"하는 것이다 —
     * 그러면 `create()` 가 이미 던져 호출까지 가지 못한다.
     */
    @Test
    fun `탈퇴 요청은 DELETE 인데 본문에 비밀번호를 싣는다`() {
        val captured = mutableListOf<Request>()
        val api = service(UserApi::class.java, captured = captured)

        runBlocking { api.deleteAccount(AccountDeletionRequest("Passw0rd!")) }

        val request = captured.single()
        assertEquals("DELETE", request.method)
        assertEquals("http://localhost/api/v1/users/me", request.url.toString())
        assertNotNull("DELETE 에 본문이 실리지 않았다 — @HTTP(hasBody = true) 가 빠졌다", request.body)
        assertEquals("""{"password":"Passw0rd!"}""", request.bodyAsString())
    }

    /**
     * 비밀번호가 **URL 에 새지 않는** 것을 못 박는다. `@Query` 로 돌리면 서버 액세스 로그와
     * 프록시·브라우저 히스토리에 평문이 남는다. 쿼리 문자열이 비어 있어야 한다.
     */
    @Test
    fun `비밀번호가 URL 에 실리지 않는다`() {
        val captured = mutableListOf<Request>()
        val api = service(UserApi::class.java, captured = captured)

        runBlocking { api.deleteAccount(AccountDeletionRequest("Passw0rd!")) }

        val url = captured.single().url
        assertNull("비밀번호가 쿼리 문자열로 나갔다", url.query)
        assertTrue("URL 에 비밀번호가 보인다: $url", !url.toString().contains("Passw0rd"))
    }

    /** 204 는 본문이 없다 — 반환형 Unit 이 그 응답을 예외 없이 끝내는지 본다. */
    @Test
    fun `204 는 본문 없이 성공으로 끝난다`() {
        val api = service(UserApi::class.java, status = 204, body = "")

        // 던지면 테스트가 실패한다. 돌려받을 값이 없는 성공이라 확인할 것은 "안 터진다"가 전부다.
        runBlocking { api.deleteAccount(AccountDeletionRequest("Passw0rd!")) }
    }

    /**
     * **함정 자체를 기록해 두는 음성 대조군.** 같은 서명을 `@DELETE` 로 쓰면 Retrofit 이 거부한다.
     *
     * 이 테스트가 깨진다면(= 더 이상 던지지 않는다면) Retrofit 이 `@DELETE` + 본문을 허용하도록
     * 바뀐 것이고, 그때는 [UserApi.deleteAccount] 의 `@HTTP` 를 단순화할 수 있다.
     */
    @Test
    fun `DELETE 에 Body 를 붙이면 Retrofit 이 거부한다 - 그래서 HTTP 를 쓴다`() {
        val thrown = runCatching { service(BodylessDeleteApi::class.java) }.exceptionOrNull()

        assertTrue(
            "Retrofit 이 @DELETE + @Body 를 받아들였다 (실제: $thrown)",
            thrown is IllegalArgumentException,
        )
        assertTrue(
            "거부 이유가 본문 문제가 아니다: ${thrown?.message}",
            thrown?.message?.contains("Non-body HTTP method cannot contain @Body") == true,
        )
    }

    /** 음성 대조군 전용. 운영 코드에 이 모양이 들어가면 안 된다는 것을 보여주기 위한 선언이다. */
    private interface BodylessDeleteApi {
        @DELETE("api/v1/users/me")
        suspend fun deleteAccount(@Body request: AccountDeletionRequest)
    }
}
