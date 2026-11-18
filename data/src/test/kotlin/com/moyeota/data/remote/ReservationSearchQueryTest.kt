package com.moyeota.data.remote

import com.moyeota.data.repository.RemoteRideRepository
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.ReservationDestination
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * 예약 조회가 **실제로 어떤 URL 로 나가는지** 확인한다.
 *
 * `AuthenticatedPathContractTest` 는 애노테이션 문자열만 읽으므로 「도착지를 안 보낼 때 그 두 키가
 * 빠지는가」를 말해 주지 못한다. 그 답은 Retrofit 의 동작(`@Query` 는 null 을 생략한다)에 달려 있고,
 * **하나만 실리면 서버가 400 `INCOMPLETE_DESTINATION`** 이다. 반대로 둘 다 `destLat=` 처럼 빈 값으로
 * 나가면 서버가 숫자 파싱에 실패해 400 `INVALID_REQUEST` 다 — 둘 다 「검색이 안 된다」로 보이는데
 * 원인이 URL 조립이라 추적이 어렵다.
 *
 * `UserApiRideRecordQueryTest` 와 같은 수법이다: OkHttp 애플리케이션 인터셉터가 `chain.proceed()` 를
 * **부르지 않고** 응답을 직접 만들면 소켓이 열리지 않아 MockWebServer 가 필요 없다.
 *
 * 저장소를 통해 부르는 것이 중요하다 — 「둘 다 또는 둘 다 아님」을 보장하는 자리가
 * [RemoteRideRepository.searchReservations] 의 파라미터 타입([ReservationDestination])이기 때문이다.
 */
class ReservationSearchQueryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val emptyListBody = """{"list":[]}"""

    private fun api(captured: MutableList<Request>): MatchingApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    captured += chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(emptyListBody.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            // MatchingApi 의 모든 메서드를 create() 시점에 파싱한다 — 하나라도 Retrofit 이 거부하면
            // 호출 전에 실패한다(새 메서드의 애노테이션 조합이 유효한지 함께 확인된다).
            .validateEagerly(true)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MatchingApi::class.java)
    }

    private fun repository(captured: MutableList<Request>) = RemoteRideRepository(
        api(captured),
        object : UserSession {
            override val authState: StateFlow<AuthState> =
                MutableStateFlow(AuthState.Authenticated("01a06145-3caf-7614-a3bd-cee6e25316b1"))
        },
    )

    /**
     * 도착지를 안 넘기면 **두 키가 함께 빠진다.** 하나만 실리면 400 `INCOMPLETE_DESTINATION` 이므로
     * 「빠짐」은 반드시 쌍이어야 한다 — 저장소가 한 값(null)에서 두 쿼리를 만들기 때문에 쌍이 깨질
     * 길이 없다는 것을 URL 로 확인한다.
     */
    @Test
    fun `도착지가 없으면 destLat destLng 가 함께 빠진다`() {
        val captured = mutableListOf<Request>()

        runBlocking {
            repository(captured).searchReservations(
                departureLat = 37.4979,
                departureLng = 127.0276,
                date = "2026-11-13",
            )
        }

        val url = captured.single().url
        assertEquals("/api/v1/matching/reservations/search", url.encodedPath)
        // 출발지 이름은 **줄어 있다** — departureLat 으로 나가면 서버 필수 파라미터가 누락된다.
        assertEquals("37.4979", url.queryParameter("depLat"))
        assertEquals("127.0276", url.queryParameter("depLng"))
        assertNull("검색 쿼리에 departureLat 이 나갔다 — 서버 파라미터는 depLat 이다", url.queryParameter("departureLat"))
        // 날짜는 instant 가 아니라 한국 달력 날짜다.
        assertEquals("2026-11-13", url.queryParameter("date"))
        assertNull("도착지를 안 보냈는데 destLat 이 나갔다", url.queryParameter("destLat"))
        assertNull("도착지를 안 보냈는데 destLng 이 나갔다", url.queryParameter("destLng"))
    }

    /** 도착지를 넘기면 **둘 다** 실린다. 이름은 `destinationLat` 이 아니라 `destLat` 이다. */
    @Test
    fun `도착지를 넘기면 destLat destLng 가 함께 실린다`() {
        val captured = mutableListOf<Request>()

        runBlocking {
            repository(captured).searchReservations(
                departureLat = 37.4979,
                departureLng = 127.0276,
                date = "2026-11-13",
                destination = ReservationDestination(latitude = 37.3948, longitude = 127.1112),
            )
        }

        val url = captured.single().url
        assertEquals("37.3948", url.queryParameter("destLat"))
        assertEquals("127.1112", url.queryParameter("destLng"))
        assertNull("도착지가 destinationLat 으로 나갔다 — 그건 생성 본문 쪽 이름이다", url.queryParameter("destinationLat"))
    }

    // 「기간 목록은 from·to 를 그대로 싣는다」 테스트는 **지웠다** — 서버가
    // `GET /matching/reservations?from&to` 와 `INVALID_TIME_RANGE` 를 함께 없앴다(`2732cc75`:
    // 「앱이 부르지 않는 … 삭제」). 앱도 부른 적이 없다. 남은 예약 조회 두 경로
    // (「곧 출발」·검색)는 아래에서 그대로 못 박는다.

    /**
     * 「곧 출발」은 **방 목록의 뷰포트 조회와 같은 이름·같은 순서**로 나간다. 섞이면 서버가
     * 400 `INVALID_MAP_BOUNDS` 를 주는데, 방 목록 쪽은 그 검사가 없어 **같은 실수가 한쪽에만**
     * 증상을 낸다 — 그래서 값으로 못 박는다.
     */
    @Test
    fun `곧 출발은 남서 북동 순서 그대로 네 쿼리를 싣는다`() {
        val captured = mutableListOf<Request>()

        runBlocking { repository(captured).getReservationsSoon(35.20, 129.05, 35.26, 129.12) }

        val url = captured.single().url
        assertEquals("/api/v1/matching/reservations/soon", url.encodedPath)
        assertEquals("35.2", url.queryParameter("swLat"))
        assertEquals("129.05", url.queryParameter("swLng"))
        assertEquals("35.26", url.queryParameter("neLat"))
        assertEquals("129.12", url.queryParameter("neLng"))
    }

    // 「노쇼 기록 조회는 쿼리 없이 나간다」 테스트는 **지웠다** — 서버가 노쇼 기록·제재·이의
    // 신청을 전부 없앴다(`b16551b7`, 마이그레이션 V37 이 테이블까지 DROP). 그 경로는 404 다.
}
