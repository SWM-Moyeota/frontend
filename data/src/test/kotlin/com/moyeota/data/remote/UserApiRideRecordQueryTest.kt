package com.moyeota.data.remote

import com.moyeota.data.remote.dto.RIDE_RECORD_PAGE_SIZE
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 탑승 기록 조회가 **실제로 어떤 URL 로 나가는지** 확인한다.
 *
 * `AuthenticatedPathContractTest` 는 애노테이션 문자열만 읽으므로 「null 커서가 쿼리에 실리는가」를
 * 말해 주지 못한다. 그 답은 Retrofit 의 동작(`@Query` 는 null 을 생략한다)에 달려 있고, 바뀌면
 * 서버에 `cursor=` 가 나가기 시작한다 — 서버는 그 빈 값도 200 으로 받지만(실측) 계약과 어긋나고,
 * 반대로 **커서를 실어야 할 때 빠지면 2페이지가 영영 1페이지가 되어 목록이 무한히 중복된다.**
 *
 * MockWebServer 의존성 없이 확인한다: OkHttp 애플리케이션 인터셉터가 `chain.proceed()` 를
 * **부르지 않고** 응답을 직접 만들면 소켓이 열리지 않는다(`UserApiDeleteBodyTest` 와 같은 수법).
 */
class UserApiRideRecordQueryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val emptyPage = """{"rides":[],"nextCursor":null,"hasNext":false}"""

    private fun api(captured: MutableList<Request>): UserApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    captured += chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(emptyPage.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            // UserApi 의 모든 메서드를 create() 시점에 파싱한다 — 하나라도 Retrofit 이 거부하면
            // 호출 전에 실패한다.
            .validateEagerly(true)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UserApi::class.java)
    }

    /**
     * 첫 페이지. 커서가 null 이면 **쿼리에 실리지 않아야** 한다 — `cursor=` 를 보내는 쪽이
     * 서버에서 200 이긴 하지만(실측) 「값이 없다」와 「빈 값이다」를 섞는 계약이 된다.
     * 기본 `size` 는 서버 기본값과 같은 20 이다(1~50 밖이면 400 `INVALID_PAGE_SIZE`).
     */
    @Test
    fun `cursor 가 null 이면 쿼리에 실리지 않는다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured).getMyRideRecords() }

        val url = captured.single().url
        assertEquals("/api/v1/users/me/parties", url.encodedPath)
        assertNull("커서가 없는데 cursor 쿼리가 나갔다", url.queryParameter("cursor"))
        assertEquals(RIDE_RECORD_PAGE_SIZE.toString(), url.queryParameter("size"))
        assertEquals(20, RIDE_RECORD_PAGE_SIZE)
    }

    /**
     * 2페이지. 이전 응답의 `nextCursor` 를 그대로 싣는다. 빠지면 서버가 첫 페이지를 다시 주고
     * 목록이 무한히 중복된다(이 화면에서 가장 비싼 버그다).
     */
    @Test
    fun `cursor 가 있으면 그 값이 쿼리로 나간다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured).getMyRideRecords(cursor = 69L, size = 1) }

        val url = captured.single().url
        assertEquals("69", url.queryParameter("cursor"))
        assertEquals("1", url.queryParameter("size"))
    }

    /**
     * 서버에 없는 `status` 파라미터가 **나가지 않는** 것을 못 박는다.
     *
     * `docs/BACKEND-GAP-CLEANUP.md` §4 가 적어 둔 `?status=FINISHED` 는 구현되지 않은 추측이었고,
     * 붙여도 400 이 아니라 **200 + 조용히 무시**라서(실측 2026-11-01) 「필터가 동작한다」고
     * 착각하고 지나간다. 필터는 필요 없다 — 서버가 이미 완료분만 준다.
     */
    @Test
    fun `서버에 없는 status 파라미터를 보내지 않는다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured).getMyRideRecords(cursor = null, size = 50) }

        assertNull("서버에 없는 status 파라미터가 나갔다", captured.single().url.queryParameter("status"))
    }
}
