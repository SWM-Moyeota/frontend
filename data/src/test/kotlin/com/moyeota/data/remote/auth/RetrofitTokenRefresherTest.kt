package com.moyeota.data.remote.auth

import com.moyeota.data.remote.AuthApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * [RetrofitTokenRefresher] 가 **「서버가 거절했다」와 「답을 못 받았다」를 가르는지**를 확인한다.
 *
 * ### 왜 이 테스트가 있어야 하는가
 * 이 판정이 틀리면 세션이 조용히 날아간다. 운영 액세스 토큰 수명이 30분이라 활성 사용자는 30분마다
 * 반드시 이 코드를 한 번 타고, 그 순간 지하철·엘리베이터·셀↔와이파이 전환이나 서버 배포 중
 * 503(ALB 가 **HTML 본문**을 준다)·429 를 맞을 수 있다. 전부 [RefreshResult.Unavailable] 이어야
 * 하는데 [RefreshResult.Rejected] 로 떨어지면 **서버에서는 아무 일도 없었는데** 사용자가
 * 로그인 화면으로 쫓겨난다. 반대로 진짜 401 을 Unavailable 로 보면 401 루프가 된다.
 *
 * ### 서버 없이 확인하는 방법
 * 애플리케이션 인터셉터가 `chain.proceed()` 를 **부르지 않고** 응답을 직접 만들어 돌려주면
 * 네트워크가 전혀 일어나지 않는다([com.moyeota.data.remote.UserApiDeleteBodyTest] 와 같은 수법이다).
 * 포트를 열지 않으므로 CI 에서 흔들릴 여지도 없고, 인터셉터가 [IOException] 을 **던지게** 하면
 * 연결 실패·타임아웃까지 그대로 재현된다.
 */
class RetrofitTokenRefresherTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 정해진 응답 하나를 돌려주는 [AuthApi]. [contentType] 은 503 의 HTML 본문을 흉내 내는 데 쓴다. */
    private fun refresher(
        status: Int,
        body: String,
        contentType: String = "application/json",
    ): RetrofitTokenRefresher = refresher(
        Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Response")
                .body(body.toResponseBody(contentType.toMediaType()))
                .build()
        },
    )

    /** 응답 대신 예외를 던지는 변종 — 소켓이 열리지 않은 채 연결 실패·타임아웃을 재현한다. */
    private fun failingRefresher(failure: IOException): RetrofitTokenRefresher =
        refresher(Interceptor { _ -> throw failure })

    private fun refresher(interceptor: Interceptor): RetrofitTokenRefresher {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val api = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        return RetrofitTokenRefresher(api)
    }

    @Test
    fun `200 이면 회전된 토큰 쌍을 돌려준다`() {
        val result = refresher(
            status = 200,
            body = """{"accessToken":"new.access","refreshToken":"new.refresh"}""",
        ).refresh("old.refresh")

        assertEquals(RefreshResult.Rotated(TokenPair("new.access", "new.refresh")), result)
    }

    /** 401 은 만료·회전됨·재사용·로그아웃·탈퇴가 전부 섞여 있지만 대응은 하나다 — 재로그인. */
    @Test
    fun `401 은 서버의 거절로 본다`() {
        val result = refresher(
            status = 401,
            body = """{"code":"USER002","message":"만료된 토큰입니다."}""",
        ).refresh("expired.refresh")

        assertEquals(RefreshResult.Rejected, result)
    }

    /** 403 도 "이 리프레시로는 안 된다"는 서버의 답이다. */
    @Test
    fun `403 도 서버의 거절로 본다`() {
        val result = refresher(status = 403, body = "").refresh("forbidden.refresh")

        assertEquals(RefreshResult.Rejected, result)
    }

    /**
     * 상태 코드가 401·403 이 아니어도 본문의 토큰 계열 코드(`USER001`~`USER005`)는 거절로 읽는다
     * — 서버가 같은 뜻을 다른 상태로 감싸 보내는 경우까지 덮어 두려는 것이다.
     */
    @Test
    fun `토큰 계열 에러 코드는 상태 코드와 무관하게 거절로 본다`() {
        val result = refresher(
            status = 400,
            body = """{"code":"USER004","message":"이미 사용된 리프레시 토큰입니다."}""",
        ).refresh("reused.refresh")

        assertEquals(RefreshResult.Rejected, result)
    }

    /**
     * **이 테스트가 가장 중요하다.** ALB 가 배포 중에 내보내는 503 은 본문이 JSON 이 아니라 HTML 이다.
     * 파싱을 시도하다 예외가 나거나 이걸 거절로 오해하면, 서버 배포 한 번에 쓰고 있던 사용자 전원이
     * 로그아웃된다.
     */
    @Test
    fun `503 과 HTML 본문은 연결 불가로 본다`() {
        val result = refresher(
            status = 503,
            body = "<html><head><title>503 Service Temporarily Unavailable</title></head></html>",
            contentType = "text/html",
        ).refresh("live.refresh")

        assertEquals(RefreshResult.Unavailable, result)
    }

    /** 429 는 "잠시 뒤 다시"다. 세션을 비울 근거가 전혀 없다. */
    @Test
    fun `429 는 연결 불가로 본다`() {
        val result = refresher(status = 429, body = "").refresh("live.refresh")

        assertEquals(RefreshResult.Unavailable, result)
    }

    /** 스프링이 직접 만든 404 는 본문에 code 가 없다 — 상태 코드만으로 판정해 연결 불가로 떨어진다. */
    @Test
    fun `code 가 없는 비정상 응답은 연결 불가로 본다`() {
        val result = refresher(status = 404, body = """{"error":"Not Found","path":"/api/v1/auth/reissue"}""")
            .refresh("live.refresh")

        assertEquals(RefreshResult.Unavailable, result)
    }

    /** 지하철·엘리베이터. 응답 자체를 못 받았으니 리프레시는 여전히 멀쩡하다. */
    @Test
    fun `연결 실패는 연결 불가로 본다`() {
        val result = failingRefresher(IOException("Failed to connect to api.moyeota.co.kr")).refresh("live.refresh")

        assertEquals(RefreshResult.Unavailable, result)
    }

    @Test
    fun `읽기 타임아웃도 연결 불가로 본다`() {
        val result = failingRefresher(SocketTimeoutException("timeout")).refresh("live.refresh")

        assertEquals(RefreshResult.Unavailable, result)
    }

    /**
     * 200 인데 토큰이 비어 있으면 서버 계약 위반이다. 다시 물어도 같은 답이 오므로 재시도는 의미가
     * 없고, 그 토큰을 저장하면 Bearer 가 빈 문자열인 요청이 나가 401 루프가 된다 — 재로그인으로 보낸다.
     */
    @Test
    fun `200 인데 액세스 토큰이 비어 있으면 거절로 본다`() {
        val result = refresher(
            status = 200,
            body = """{"accessToken":"","refreshToken":"new.refresh"}""",
        ).refresh("old.refresh")

        assertEquals(RefreshResult.Rejected, result)
    }

    /** 회전 방식이라 리프레시가 비어 있어도 다음 재발급을 할 수 없다 — 같은 이유로 거절이다. */
    @Test
    fun `200 인데 리프레시 토큰이 비어 있으면 거절로 본다`() {
        val result = refresher(
            status = 200,
            body = """{"accessToken":"new.access","refreshToken":""}""",
        ).refresh("old.refresh")

        assertEquals(RefreshResult.Rejected, result)
    }

    /**
     * 재발급 요청이 **리프레시 토큰을 본문에 싣는지**까지 본다. 여기가 비면 서버는 400 을 주고
     * 모든 재발급이 영구히 실패한다 — 위의 분기 판정이 다 맞아도 소용이 없어진다.
     */
    @Test
    fun `재발급 요청은 리프레시 토큰을 본문에 싣는다`() {
        val bodies = mutableListOf<String>()
        val refresher = refresher(
            Interceptor { chain ->
                bodies += chain.request().body?.let { requestBody ->
                    Buffer().also { requestBody.writeTo(it) }.readUtf8()
                }.orEmpty()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"accessToken":"new.access","refreshToken":"new.refresh"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            },
        )

        refresher.refresh("old.refresh")

        assertTrue("본문에 리프레시가 실리지 않았다: ${bodies.single()}", bodies.single().contains("old.refresh"))
    }
}
