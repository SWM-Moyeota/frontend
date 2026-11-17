package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FareSplitRequestDto
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 정산 7경로가 **실제로 어떤 요청으로 나가는지**를 진짜 Retrofit 으로 확인한다.
 * 하네스는 [PlaceApiDeleteTest] 의 `service()` 를 그대로 베꼈다 — `chain.proceed()` 를 부르지 않는
 * 인터셉터라 소켓이 열리지 않고, `validateEagerly(true)` 로 인터페이스 전체를 `create()` 시점에 파싱한다.
 *
 * ### 왜 이 계층을 테스트하나
 * Retrofit 애노테이션은 문자열이라 **컴파일러도 매퍼 테스트도 못 잡는다.** 틀리면 실행 시점의 4xx 로만
 * 드러나고, 이 모듈에서는 그 4xx 가 특히 **원인을 가린다**:
 *
 * | 틀리는 모양 | 오는 응답 | 왜 원인이 가려지나 |
 * |---|---|---|
 * | 수정을 PATCH 대신 POST | **409 `FARE_SPLIT_ALREADY_EXISTS`** | `POST …/fare-split` 매핑이 **이미 있다**(= 정산 시작). 405 가 아니라 「이미 있다」로 보인다 |
 * | `not-received` 를 `received` 로 | **204 성공** | 되돌리려던 송금이 **확인으로 닫힌다.** 마지막 한 건이면 정산이 COMPLETED 가 되고 계좌번호가 지워진다 — 되돌릴 수 없다 |
 * | `status` 쿼리 누락 | 200 + **다른 목록** | 서버 `defaultValue = "NOT_SENT"` 가 메워 줘서 지금은 우연히 맞는다. 서버가 기본값을 바꾸면 홈 배너가 조용히 다른 목록을 그린다 |
 *
 * 경로는 컨트롤러 소스(`faresplit/FareSplitController.java`, 2026-11-12) 대조다 — **실서버 미검증**.
 */
class FareSplitApiContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val partyId = 42L
    private val senderPublicId = "22222222-2222-2222-2222-222222222222"

    private fun api(
        status: Int = 200,
        body: String = "{}",
        captured: MutableList<Request> = mutableListOf(),
    ): FareSplitApi {
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
            .create(FareSplitApi::class.java)
    }

    private fun Request.bodyAsString(): String? = body?.let { requestBody ->
        Buffer().also { requestBody.writeTo(it) }.readUtf8()
    }

    private val draftDto = FareSplitRequestDto(
        totalFare = 18_700,
        bankCode = "KAKAO",
        accountNumber = "3333012345678",
        payments = listOf(FareSplitRequestDto.PaymentDto("11111111-1111-1111-1111-111111111111", 18_700)),
    )

    @Test
    fun `정산 시작은 rooms partyId fare-split 의 POST 이고 본문을 싣는다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured = captured).createFareSplit(partyId, draftDto) }

        val request = captured.single()
        assertEquals("POST", request.method)
        assertEquals("http://localhost/api/v1/matching/rooms/42/fare-split", request.url.toString())
        assertEquals(
            """{"totalFare":18700,"bankCode":"KAKAO","accountNumber":"3333012345678",""" +
                """"payments":[{"userPublicId":"11111111-1111-1111-1111-111111111111","amount":18700}]}""",
            request.bodyAsString(),
        )
    }

    @Test
    fun `정산 조회는 같은 경로의 GET 이고 본문이 없다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured = captured).getFareSplit(partyId) }

        val request = captured.single()
        assertEquals("GET", request.method)
        assertEquals("http://localhost/api/v1/matching/rooms/42/fare-split", request.url.toString())
        assertNull("조회에 본문이 실렸다", request.body)
        assertNull(request.url.query)
    }

    /**
     * **동사가 PATCH 여야 한다.** POST 로 나가면 405 가 아니라 **409 `FARE_SPLIT_ALREADY_EXISTS`** 를
     * 받는다 — 같은 경로에 POST 매핑(= 정산 시작)이 이미 있기 때문이다. 「수정이 안 된다」가 아니라
     * 「이미 있다」로 보여 디버깅이 엉뚱한 곳으로 간다.
     */
    @Test
    fun `정산 수정은 PATCH 이고 시작과 같은 본문을 전부 싣는다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured = captured).editFareSplit(partyId, draftDto) }

        val request = captured.single()
        assertEquals("PATCH", request.method)
        assertEquals("http://localhost/api/v1/matching/rooms/42/fare-split", request.url.toString())
        assertTrue(
            "수정 본문에 네 필드가 전부 실려야 한다 — 부분 수정 PATCH 가 아니다",
            request.bodyAsString()!!.let {
                it.contains("\"totalFare\"") && it.contains("\"bankCode\"") &&
                    it.contains("\"accountNumber\"") && it.contains("\"payments\"")
            },
        )
    }

    /** 「보냈어요」는 **본문 없는 POST** 다([MatchingApi.departParty] 와 같은 모양). */
    @Test
    fun `보냈어요는 fare-split sent 의 POST 이고 본문이 없다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(status = 204, body = "", captured = captured).markSent(partyId) }

        val request = captured.single()
        assertEquals("POST", request.method)
        assertEquals("http://localhost/api/v1/matching/rooms/42/fare-split/sent", request.url.toString())
        assertEquals("본문 없는 POST 는 Content-Length 0 으로 나간다", 0L, request.body?.contentLength() ?: 0L)
    }

    /**
     * **두 경로를 함께 못 박는다.** 마지막 세그먼트 하나(`received` ↔ `not-received`)만 다르고,
     * 바꿔 쓰면 **되돌리려던 송금이 확인으로 닫힌다** — 204 성공으로 보이고, 마지막 한 건이면
     * 정산이 COMPLETED 가 되어 계좌번호까지 지워진다. 사용자가 누른 것은 그 반대다.
     */
    @Test
    fun `받았어요와 못 받았어요는 마지막 세그먼트만 다른 두 경로다`() {
        val received = mutableListOf<Request>()
        val notReceived = mutableListOf<Request>()

        runBlocking {
            api(status = 204, body = "", captured = received).confirmReceived(partyId, senderPublicId)
            api(status = 204, body = "", captured = notReceived).markNotReceived(partyId, senderPublicId)
        }

        assertEquals("POST", received.single().method)
        assertEquals(
            "http://localhost/api/v1/matching/rooms/42/fare-split/transfers/$senderPublicId/received",
            received.single().url.toString(),
        )
        assertEquals("POST", notReceived.single().method)
        assertEquals(
            "http://localhost/api/v1/matching/rooms/42/fare-split/transfers/$senderPublicId/not-received",
            notReceived.single().url.toString(),
        )
        // publicId 는 **경로**에 싣는다 — 쿼리로 돌리면 서버 {publicId} 매핑에 닿지 못한다
        assertNull(received.single().url.query)
        assertNull(notReceived.single().url.query)
    }

    /**
     * 홈 배너 목록은 `users/me` 아래다(방 경로가 아니다) — 서버가 같은 컨트롤러에 두었지만
     * 프리픽스가 다르다. `status` 는 **늘 명시해 보낸다**(위 표의 셋째 줄).
     */
    @Test
    fun `내 정산 목록은 users me fare-splits 의 GET 이고 status 를 쿼리로 싣는다`() {
        val captured = mutableListOf<Request>()

        runBlocking { api(captured = captured).getMyFareSplits("NOT_SENT") }

        val request = captured.single()
        assertEquals("GET", request.method)
        assertEquals("http://localhost/api/v1/users/me/fare-splits?status=NOT_SENT", request.url.toString())
        assertEquals("NOT_SENT", request.url.queryParameter("status"))
    }

    /** 세 동작 모두 **204 + 빈 본문**으로 끝난다 — 돌려받을 값이 없으니 「안 터진다」가 확인할 전부다. */
    @Test
    fun `204 는 본문 없이 성공으로 끝난다`() {
        val api = api(status = 204, body = "")

        runBlocking {
            api.markSent(partyId)
            api.confirmReceived(partyId, senderPublicId)
            api.markNotReceived(partyId, senderPublicId)
        }
    }

    /**
     * 일곱 경로 전부가 `api/v1` 프리픽스를 쓴다 — 하나라도 빠지면 Security 가 보호 경로로 잡지 않아
     * **401 이 아니라 404** 가 오고, 그 404 는 「정산이 없다」로 읽혀 화면이 「정산 시작하기」를 띄운다.
     */
    @Test
    fun `일곱 경로 전부가 api v1 프리픽스를 쓴다`() {
        val paths = FareSplitApi::class.java.declaredMethods.mapNotNull { method ->
            method.getAnnotation(retrofit2.http.GET::class.java)?.value
                ?: method.getAnnotation(retrofit2.http.POST::class.java)?.value
                ?: method.getAnnotation(retrofit2.http.PATCH::class.java)?.value
        }

        assertEquals("경로가 일곱이 아니다 — 엔드포인트가 늘거나 줄었다", 7, paths.size)
        assertTrue("api/v1 프리픽스가 빠진 경로가 있다: $paths", paths.all { it.startsWith("api/v1/") })
    }
}
