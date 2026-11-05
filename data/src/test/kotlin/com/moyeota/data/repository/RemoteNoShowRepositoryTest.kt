package com.moyeota.data.repository

import com.moyeota.data.remote.MatchingApi
import com.moyeota.data.remote.dto.ArriveRequestDto
import com.moyeota.data.remote.dto.ConfirmMeetingRequestDto
import com.moyeota.data.remote.dto.MatchCandidateResponse
import com.moyeota.data.remote.dto.MyNoShowListResponse
import com.moyeota.data.remote.dto.NoShowItemDto
import com.moyeota.data.remote.dto.NoShowObjectionRequestDto
import com.moyeota.data.remote.dto.OpenPartyRequestDto
import com.moyeota.data.remote.dto.OpenPartyResponse
import com.moyeota.data.remote.dto.OpenReservationRequestDto
import com.moyeota.data.remote.dto.PartyDetailResponse
import com.moyeota.data.remote.dto.PartyListResponse
import com.moyeota.data.remote.dto.PartyStatusResponse
import com.moyeota.data.remote.dto.RealTimeMatchingRequestDto
import com.moyeota.data.remote.dto.ReservationListResponse
import com.moyeota.data.remote.dto.RouteEstimateResponse
import com.moyeota.data.remote.dto.RouteRequestDto
import com.moyeota.domain.model.NoShowObjectionException
import com.moyeota.domain.model.ObjectionStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RemoteNoShowRepositoryTest {

    /**
     * 조회가 **서버 순서 그대로** 올라오고, 무효가 된 건도 **걸러지지 않는다**.
     *
     * 걸러내고 싶어지는 줄이 바로 그 무효 건인데, 그 줄이 「이의가 받아들여졌다」는 **유일한 증거**다
     * — 빼면 사용자가 결과를 확인할 길이 없다(프로필의 `restrictedUntil` 은 「지금 막혀 있나」만 말한다).
     */
    @Test
    fun `노쇼 기록을 서버 순서 그대로 올리고 무효 건도 남긴다`() = runBlocking {
        val api = FakeNoShowApi(
            noShows = listOf(
                NoShowItemDto(
                    partyId = 7,
                    departure = "서울시청",
                    destination = "강남역",
                    meetingAt = "2026-11-03T08:30:00Z",
                    noShowAt = "2026-11-03T08:40:00Z",
                    voided = false,
                    objectionStatus = null,
                ),
                NoShowItemDto(partyId = 5, voided = true, objectionStatus = "ACCEPTED"),
            ),
        )

        val records = RemoteNoShowRepository(api).getMyNoShows()

        assertEquals(listOf(7L, 5L), records.map { it.partyId })
        assertEquals(listOf(false, true), records.map { it.isVoided })
        assertEquals(listOf(null, ObjectionStatus.ACCEPTED), records.map { it.objectionStatus })
        assertEquals("2026-11-03T08:30:00Z", records[0].meetingAt)
    }

    /** 기록이 없으면 **빈 목록**이다 — 서버가 404 가 아니라 `{"noShows":[]}` 를 준다. */
    @Test
    fun `기록이 없으면 빈 목록이다`() = runBlocking {
        assertTrue(RemoteNoShowRepository(FakeNoShowApi()).getMyNoShows().isEmpty())
    }

    /**
     * **조회 실패는 감싸지 않는다**(맨몸으로 던진다). 이 조회의 서버 실패는 401 하나뿐이고 화면의
     * 대응도 하나다 — `RemoteRideRecordRepository` 와 같은 결이고, 감싸면 쓰지 않을 구분이 생긴다.
     * (그 `try`/`catch` 가 없는 것 자체가 취소 보존 장치이기도 하다.)
     */
    @Test
    fun `조회 실패는 전용 예외로 감싸지 않는다`() = runBlocking {
        val api = FakeNoShowApi(noShowsFailure = unauthorized())

        val error = runCatching { RemoteNoShowRepository(api).getMyNoShows() }.exceptionOrNull()

        assertTrue("조회 실패를 NoShowObjectionException 으로 감쌌다", error is HttpException)
        assertEquals(401, (error as HttpException).code())
    }

    /** 이의 신청은 방 id 와 사유만 보낸다 — 주체는 Bearer 토큰이 정한다. */
    @Test
    fun `이의 신청은 방 id 와 사유만 보낸다`() = runBlocking {
        val api = FakeNoShowApi()

        RemoteNoShowRepository(api).submitObjection(partyId = 7, reason = "병원에 있었어요")

        assertEquals(7L, api.objectionCall)
        assertEquals("병원에 있었어요", api.objectionRequest?.reason)
    }

    /**
     * **앞뒤 공백을 떼고 보낸다.** 서버 검증(`@NotBlank` + `@Size(max = 500)`)이 **다듬기 전**
     * 문자열에 걸리기 때문이다 — 공백으로 패딩된 501자는 다듬으면 500자 이하여도 400 이다.
     * 여기서 먼저 다듬으면 사용자 입장에서 「같은 글인데 결과가 다른」 상황이 사라진다.
     */
    @Test
    fun `사유의 앞뒤 공백을 떼고 보낸다`() = runBlocking {
        val api = FakeNoShowApi()

        RemoteNoShowRepository(api).submitObjection(partyId = 7, reason = "  병원에 있었어요\n ")

        assertEquals("병원에 있었어요", api.objectionRequest?.reason)
    }

    /**
     * 반대로 **빈 값·길이 초과를 앱이 끊지는 않는다.** 그 판정을 들면 서버가 상한을 바꿀 때 앱만
     * 뒤처지고, 무엇보다 「앱이 막은 실패」와 「서버가 막은 실패」가 같은 예외로 섞여 원인을 가릴 수
     * 없게 된다(`confirmMeeting` 이 약속 시간을 검사하지 않는 것과 같은 정책).
     */
    @Test
    fun `빈 사유도 서버로 보낸다 — 판정은 서버가 한다`() = runBlocking {
        val api = FakeNoShowApi()

        RemoteNoShowRepository(api).submitObjection(partyId = 7, reason = "   ")

        assertEquals("앱이 빈 사유를 먼저 끊었다 — 판정은 서버 몫이다", 7L, api.objectionCall)
        assertEquals("", api.objectionRequest?.reason)
    }

    /**
     * 길이 초과도 **자르지 않는다** — 서버가 400 `INVALID_REQUEST` 로 판정한다. 앱이 몰래 자르면
     * 사용자가 쓴 글의 뒷부분이 조용히 사라진 채 접수된다(실패보다 나쁘다).
     * 500자 상한을 아는 곳은 입력란(화면)이고, 그 값은 이 도메인 상수를 함께 쓴다.
     */
    @Test
    fun `길이 초과 사유도 자르지 않고 보낸다`() = runBlocking {
        val api = FakeNoShowApi()
        val tooLong = "가".repeat(NoShowObjectionException.MAX_REASON_LENGTH + 1)

        RemoteNoShowRepository(api).submitObjection(partyId = 7, reason = tooLong)

        assertEquals(tooLong, api.objectionRequest?.reason)
        assertEquals(500, NoShowObjectionException.MAX_REASON_LENGTH)
    }

    /**
     * ### 다섯 사유가 하나씩 갈린다 ★
     * **409 가 두 가지 뜻**이라(`NOT_NO_SHOW`·`ALREADY_OBJECTED`) 상태 코드로는 안내 문구를 가를 수
     * 없다. 그리고 두 사유의 다음 행동이 정반대다: 앞은 「낼 이유가 없다(좋은 소식)」, 뒤는
     * 「심사를 기다린다」다.
     */
    @Test
    fun `이의 신청 실패는 사유마다 다른 프로퍼티로 갈린다`() = runBlocking {
        val invalid = objectionError(400, "INVALID_REQUEST")
        assertTrue(invalid.isInvalidReason)
        assertFalse(invalid.isAlreadyObjected)

        val notMember = objectionError(403, "NOT_PARTY_MEMBER")
        assertTrue(notMember.isNotMember)
        assertFalse(notMember.isNotNoShow)

        val notFound = objectionError(404, "PARTY_NOT_FOUND")
        assertTrue(notFound.isPartyNotFound)

        // 같은 409 두 개 — 뜻이 다르고, 사용자가 할 일도 다르다.
        val notNoShow = objectionError(409, "NOT_NO_SHOW")
        assertTrue(notNoShow.isNotNoShow)
        assertFalse(notNoShow.isAlreadyObjected)

        val already = objectionError(409, "ALREADY_OBJECTED")
        assertTrue(already.isAlreadyObjected)
        assertFalse(already.isNotNoShow)
        assertEquals(409, already.httpStatus)
        // 서버 원문을 그대로 들고 온다 — 화면 문구는 화면이 정한다.
        assertEquals("message-ALREADY_OBJECTED", already.serverMessage)
    }

    /**
     * **모르는 코드와 본문 없는 실패는 사유를 추측하지 않는다** — `code` 가 null 이거나 모르는 값이면
     * 어느 프로퍼티도 참이 아니라 화면이 일반 문구로 떨어진다.
     *
     * 특히 **상태 코드로 폴백하지 않는 것**이 중요하다: 409 를 「이미 이의를 냈어요」로 뭉개면
     * 서버가 409 코드를 하나 더 늘리는 날 **낼 수 있는 사람에게 이미 냈다고** 말한다.
     */
    @Test
    fun `모르는 코드와 빈 본문은 사유를 추측하지 않는다`() = runBlocking {
        val unknown = objectionError(409, "SOMETHING_NEW")
        assertEquals("SOMETHING_NEW", unknown.code)
        assertFalse(unknown.isAlreadyObjected)
        assertFalse(unknown.isNotNoShow)

        val bodyless = failedObjection(FakeNoShowApi(objectionFailure = bodylessError(409)))
        assertNull(bodyless.code)
        assertNull(bodyless.serverMessage)
        assertFalse(bodyless.isAlreadyObjected)
        assertEquals(409, bodyless.httpStatus)
    }

    /** 네트워크 실패도 같은 예외로 좁힌다 — code 는 null 이라 화면은 일반 문구를 쓴다. */
    @Test
    fun `네트워크 실패도 같은 예외로 좁힌다`() = runBlocking {
        val error = failedObjection(FakeNoShowApi(objectionFailure = IOException("연결 거부")))

        assertNull(error.code)
        assertNull(error.httpStatus)
    }

    /**
     * **취소는 에러가 아니다.** `runCatching` 이 `CancellationException` 까지 삼키므로 저장소가
     * 되던진다 — 안 그러면 화면을 떠나는 동작이 「이의를 신청하지 못했습니다」로 보인다
     * (이 저장소 전반의 규칙이고, `RemoteRideRecordRepositoryTest` 가 같은 것을 확인한다).
     */
    @Test
    fun `취소는 그대로 전파된다`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val repository = RemoteNoShowRepository(FakeNoShowApi(objectionGate = entered))

        val job = launch(start = CoroutineStart.UNDISPATCHED) { repository.submitObjection(7, "사유") }
        entered.await()
        job.cancel()
        job.join()

        assertTrue("취소가 예외로 바뀌었다", job.isCancelled)
    }

    private suspend fun objectionError(status: Int, code: String): NoShowObjectionException =
        failedObjection(FakeNoShowApi(objectionFailure = httpError(status, code)))

    private suspend fun failedObjection(api: FakeNoShowApi): NoShowObjectionException =
        runCatching { RemoteNoShowRepository(api).submitObjection(partyId = 7, reason = "사유") }
            .exceptionOrNull() as NoShowObjectionException

    private fun httpError(status: Int, code: String) = HttpException(
        Response.error<Unit>(
            status,
            """{"code":"$code","message":"message-$code"}"""
                .toResponseBody("application/json".toMediaType()),
        ),
    )

    /** 본문 없는 실패 — 게이트웨이·프록시가 가로챈 응답이 이렇게 온다(서버 코드가 없다). */
    private fun bodylessError(status: Int) = HttpException(
        Response.error<Unit>(status, "".toResponseBody("application/json".toMediaType())),
    )

    private fun unauthorized() = HttpException(
        Response.error<Unit>(
            401,
            """{"code":"USER005","message":"로그인이 필요합니다."}"""
                .toResponseBody("application/json".toMediaType()),
        ),
    )

    /**
     * 노쇼 두 호출만 응답하는 [MatchingApi]. **나머지는 끊어 둔다** — 조용히 기본값을 돌려주면
     * 「저장소가 다른 호출을 섞지 않는다」가 검증되지 않는다(이 저장소는 세션도, 방 조회도 쓰지 않는다).
     */
    private class FakeNoShowApi(
        private val noShows: List<NoShowItemDto> = emptyList(),
        private val noShowsFailure: Throwable? = null,
        private val objectionFailure: Throwable? = null,
        /** 호출이 **들어왔음**을 알리고 그대로 멈춰 선다 — 취소 전파를 보기 위한 장치다. */
        private val objectionGate: CompletableDeferred<Unit>? = null,
    ) : MatchingApi {
        var objectionCall: Long? = null
        var objectionRequest: NoShowObjectionRequestDto? = null

        override suspend fun getMyNoShows(): MyNoShowListResponse {
            noShowsFailure?.let { throw it }
            return MyNoShowListResponse(noShows = noShows)
        }

        override suspend fun submitNoShowObjection(partyId: Long, request: NoShowObjectionRequestDto) {
            objectionCall = partyId
            objectionRequest = request
            objectionGate?.let { gate ->
                gate.complete(Unit)
                // 영원히 기다린다 — 깨우는 것은 취소뿐이다.
                CompletableDeferred<Unit>().await()
            }
            objectionFailure?.let { throw it }
        }

        override suspend fun getParties(): PartyListResponse = unused()

        override suspend fun getPartiesWithin(
            swLat: Double,
            swLng: Double,
            neLat: Double,
            neLng: Double,
        ): PartyListResponse = unused()

        override suspend fun getPartyDetail(partyId: Long): PartyDetailResponse = unused()

        override suspend fun openParty(request: OpenPartyRequestDto): OpenPartyResponse = unused()

        override suspend fun joinParty(partyId: Long): PartyStatusResponse = unused()

        override suspend fun finishParty(partyId: Long) {
            unused()
        }

        override suspend fun leaveParty(partyId: Long) {
            unused()
        }

        override suspend fun confirmMeeting(partyId: Long, request: ConfirmMeetingRequestDto) {
            unused()
        }

        override suspend fun arriveAtDeparture(partyId: Long, request: ArriveRequestDto) {
            unused()
        }

        override suspend fun departParty(partyId: Long) {
            unused()
        }

        override suspend fun previewRoute(request: RouteRequestDto): RouteEstimateResponse = unused()

        override suspend fun getRealtimeCandidates(
            request: RealTimeMatchingRequestDto,
        ): List<MatchCandidateResponse> = unused()

        override suspend fun createReservation(request: OpenReservationRequestDto): OpenPartyResponse = unused()

        override suspend fun getReservations(from: String, to: String): ReservationListResponse = unused()

        override suspend fun getReservationsSoon(
            swLat: Double,
            swLng: Double,
            neLat: Double,
            neLng: Double,
        ): ReservationListResponse = unused()

        override suspend fun searchReservations(
            depLat: Double,
            depLng: Double,
            date: String,
            destLat: Double?,
            destLng: Double?,
        ): ReservationListResponse = unused()

        private fun unused(): Nothing = error("이 테스트에서 부를 일이 없다")
    }
}
