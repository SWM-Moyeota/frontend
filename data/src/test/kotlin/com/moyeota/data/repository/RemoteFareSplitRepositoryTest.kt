package com.moyeota.data.repository

import com.moyeota.data.remote.FareSplitApi
import com.moyeota.data.remote.dto.FareSplitDetailResponse
import com.moyeota.data.remote.dto.FareSplitPayerDto
import com.moyeota.data.remote.dto.FareSplitRequestDto
import com.moyeota.data.remote.dto.MyFareSplitListResponse
import com.moyeota.domain.model.FareShare
import com.moyeota.domain.model.FareSplitDraft
import com.moyeota.domain.model.FareSplitException
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.TransferStatus
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * 정산 저장소의 **실패 처리**를 못 박는다. 서버가 상태 코드 하나에 뜻을 여러 개 담으므로
 * (403 에 5종, 404 에 3종, 409 에 6종) 「상태 코드만으로 다루지 않는다」가 이 저장소의 판단이고,
 * 경로마다 갈린다:
 *
 * - 조회의 404 `FARE_SPLIT_NOT_FOUND` → **null 로 접는다.** 「아직 아무도 시작하지 않았다」는 끝난
 *   방의 정상 상태다.
 * - 조회의 404 `PARTY_NOT_FOUND`·403 → **올린다.** 「정산이 없다」와 뜻이 달라서, 접으면 사라진 방이나
 *   당사자 아닌 사람에게 「정산 시작하기」가 뜨고 눌렀을 때 또 막힌다.
 * - 시작의 409 `FARE_SPLIT_ALREADY_EXISTS` → **실패로 올리되 기존 정산을 담아** 올린다. 서버가
 *   「GET 으로 본다」라고 복구 경로를 명시한 실패다.
 * - 멱등 204 둘 → **성공으로만.** 「이미 그 상태였다」와 「지금 바뀌었다」를 알 길이 없다.
 */
class RemoteFareSplitRepositoryTest {

    private val partyId = 42L
    private val senderPublicId = "22222222-2222-2222-2222-222222222222"

    private val draft = FareSplitDraft(
        totalFare = 18_700,
        bankCode = "KAKAO",
        accountNumber = "3333-01-2345678",
        shares = listOf(FareShare("11111111-1111-1111-1111-111111111111", 18_700)),
    )

    // ─── 조회 ───

    /**
     * 못 박는 것: **정산이 없는 방이 에러로 보이지 않는다.** 예외로 올리면 모든 호출자가 「정상인
     * 404」와 「진짜 실패」를 가리려고 try/catch 를 쓰게 되고, 한 곳만 빠뜨리면 멀쩡한 방에 빨간
     * 에러가 뜬다(방장이 정산을 시작할 자리가 그 화면이다).
     */
    @Test
    fun `정산이 없다는 404 는 null 로 돌려준다`() = runBlocking {
        val api = FakeFareSplitApi(detailError = httpError(404, "FARE_SPLIT_NOT_FOUND", "이 방의 택시비 정산이 없습니다."))

        assertNull(RemoteFareSplitRepository(api).getFareSplit(partyId))
    }

    /**
     * **같은 404 를 상태 코드로만 접으면 안 된다.** `PARTY_NOT_FOUND` 를 「정산이 없다」로 읽으면
     * 사라진 방에 「정산 시작하기」가 뜨고, 눌렀을 때 또 404 를 받는다 — 사용자는 앱이 고장났다고 읽는다.
     */
    @Test
    fun `방이 없다는 404 는 정산이 없는 것으로 접지 않는다`() {
        val api = FakeFareSplitApi(detailError = httpError(404, "PARTY_NOT_FOUND", "존재하지 않는 방입니다."))

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).getFareSplit(partyId) }
        }

        assertTrue(e.isPartyNotFound)
        assertFalse("PARTY_NOT_FOUND 가 「정산 없음」으로 접혔다", e.isFareSplitNotFound)
    }

    /**
     * 403 `NOT_FARE_SPLIT_PARTICIPANT` 를 null 로 접으면 **도착을 누르지 않은 사람에게
     * 「정산 시작하기」가 뜬다**(눌러도 403 `NOT_A_RIDER` 다). 두 실패는 화면이 할 말이 다르다.
     */
    @Test
    fun `당사자가 아니라는 403 은 그대로 올린다`() {
        val api = FakeFareSplitApi(detailError = httpError(403, "NOT_FARE_SPLIT_PARTICIPANT", "이 택시비 정산에 참여하지 않았습니다."))

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).getFareSplit(partyId) }
        }

        assertTrue(e.isNotParticipant)
        assertEquals(403, e.httpStatus)
    }

    @Test
    fun `조회 성공은 요청한 방 번호로 상세를 돌려준다`() = runBlocking {
        val api = FakeFareSplitApi(detail = detailResponse(partyId = null))

        val detail = RemoteFareSplitRepository(api).getFareSplit(partyId)

        assertEquals(partyId, detail!!.partyId)
        assertEquals(FareSplitStatus.OPEN, detail.status)
    }

    // ─── 시작 ───

    /**
     * 못 박는 것: **409 가 막다른 길이 되지 않는다.** 서버 Swagger 원문이 「이미 정산을 시작한
     * 방(GET 으로 본다)」이다 — 그 GET 을 화면마다 시키면 한 곳만 빠뜨려도 사용자는 이미 존재하는
     * 자기 정산을 볼 수 없다(시작도 조회도 막힌 화면이 된다).
     *
     * 성공으로 **바꿔서** 돌려주지는 않는다 — 방금 입력한 금액·계좌는 저장되지 않았고, 성공으로
     * 보여 주면 화면은 반영된 줄 안다.
     */
    @Test
    fun `이미 정산이 있으면 그 정산을 예외에 담아 올린다`() {
        val api = FakeFareSplitApi(
            createError = httpError(409, "FARE_SPLIT_ALREADY_EXISTS", "이미 택시비 정산을 시작한 방입니다."),
            detail = detailResponse(totalFare = 20_000),
        )

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).startFareSplit(partyId, draft) }
        }

        assertTrue(e.isAlreadyExists)
        assertEquals("원래 실패의 코드·상태가 보존되지 않았다", 409, e.httpStatus)
        assertNotNull("이어 읽은 기존 정산이 담기지 않았다", e.existing)
        assertEquals(20_000, e.existing!!.totalFare)
        assertEquals("이어 읽는 조회는 한 번뿐이어야 한다", 1, api.detailCalls)
    }

    /**
     * 이어 읽는 GET 이 실패하면 **그 실패로 갈아 끼우지 않는다.** 네트워크 오류 같은 것으로 바꾸면
     * 화면이 「다시 시작해 보세요」로 안내하게 되고, 그건 영원히 409 다.
     */
    @Test
    fun `이어 읽는 조회가 실패해도 원래 409 를 그대로 올린다`() {
        val api = FakeFareSplitApi(
            createError = httpError(409, "FARE_SPLIT_ALREADY_EXISTS", "이미 택시비 정산을 시작한 방입니다."),
            detailError = IOException("연결 거부"),
        )

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).startFareSplit(partyId, draft) }
        }

        assertTrue(e.isAlreadyExists)
        assertNull(e.existing)
        assertFalse("이어 읽기 실패가 원래 실패를 네트워크 오류로 갈아 끼웠다", e.isNetwork)
    }

    /**
     * **409 가 아닌 실패에는 이어 읽지 않는다.** 「출발을 누르지 않은 방」에 조회를 덧붙이면 쓸모
     * 없는 왕복이 하나 늘고, 그 조회의 404 가 로그에 섞여 원인을 가린다.
     */
    @Test
    fun `정산을 시작할 수 없는 실패에는 이어 읽지 않는다`() {
        val api = FakeFareSplitApi(createError = httpError(409, "PARTY_NOT_FINISHED", "동승이 끝난 방에서만 택시비를 정산할 수 있습니다."))

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).startFareSplit(partyId, draft) }
        }

        assertTrue(e.isPartyNotFinished)
        assertEquals("불필요한 상세 조회가 나갔다", 0, api.detailCalls)
    }

    /**
     * 셋이 같은 403/409 인데 **사용자가 할 수 있는 일이 서로 다르다** — 문구를 뭉치면 기다려야 할 때
     * 다시 누르고, 다시 누르면 될 때 포기한다. 플래그가 서로 **배타적**인지까지 못 박는다.
     */
    @Test
    fun `정산을 시작할 수 없는 세 이유를 서로 다르게 가른다`() {
        val cases = mapOf(
            httpError(409, "PARTY_NOT_FINISHED", "x") to listOf(true, false, false),
            httpError(403, "NOT_A_RIDER", "x") to listOf(false, true, false),
            httpError(409, "NOT_ENOUGH_RIDERS", "x") to listOf(false, false, true),
        )

        for ((error, expected) in cases) {
            val e = assertThrows(FareSplitException::class.java) {
                runBlocking { RemoteFareSplitRepository(FakeFareSplitApi(createError = error)).startFareSplit(partyId, draft) }
            }

            assertEquals(
                "${e.code} 의 분기가 섞였다",
                expected,
                listOf(e.isPartyNotFinished, e.isNotARider, e.isNotEnoughRiders),
            )
        }
    }

    /** 계좌번호 정규화가 **저장소를 거쳐도** 적용되는지 — 화면이 원문을 그대로 넘겨도 된다는 계약이다. */
    @Test
    fun `보내는 계좌번호에서 하이픈이 떨어진다`() = runBlocking {
        val api = FakeFareSplitApi(detail = detailResponse())

        RemoteFareSplitRepository(api).startFareSplit(partyId, draft)

        assertEquals("3333012345678", api.createdRequests.single().accountNumber)
        assertEquals("KAKAO", api.createdRequests.single().bankCode)
    }

    // ─── 멱등 204 ───

    /**
     * 못 박는 것: **멱등 204 를 성공으로만 다룬다.** 정산이 **열려 있는 동안** 서버는 이미
     * SENT·CONFIRMED 여도 같은 204 를 주므로(실측), 「이미 그 상태였다」와 「지금 바뀌었다」를
     * 응답으로 구분할 수 없다 — 알아낸 척하는 반환값을 만들면 화면이 그 값으로 **틀린 문구**를
     * 띄운다. 확인할 것은 「안 터진다」가 전부다.
     *
     * 정산이 **닫힌 뒤**로는 둘의 사정이 갈린다 — 「받았어요」는 409 를 접고 「보냈어요」는 올린다
     * (바로 아래 두 테스트).
     */
    @Test
    fun `보냈어요와 받았어요는 다시 불러도 성공으로 끝난다`() = runBlocking {
        val api = FakeFareSplitApi()
        val repository = RemoteFareSplitRepository(api)

        repository.markSent(partyId)
        repository.markSent(partyId)
        repository.confirmReceived(partyId, senderPublicId)
        repository.confirmReceived(partyId, senderPublicId)

        assertEquals(2, api.sentCalls)
        assertEquals(listOf(senderPublicId, senderPublicId), api.receivedCalls)
    }

    /**
     * 못 박는 것: **「받았어요」의 409 `FARE_SPLIT_CLOSED` 를 성공으로 접는다** — 실측으로 알아낸 동작이다.
     *
     * 서버 Swagger 는 「이미 CONFIRMED 면 아무것도 바꾸지 않고 204」라고 적어 두었다. **사실이 아니다.**
     * 마지막 송금을 확인하면 정산이 COMPLETED 로 닫히고, 그다음부터는 닫힘 검사가 먼저 걸려 409 다
     * (로컬 실측 2026-11-12, partyId 4143):
     *
     * | 호출 | 서버 |
     * |---|---|
     * | 1회 | 204 — 확인됐고 정산이 닫혔다 |
     * | 2회 | **409 `FARE_SPLIT_CLOSED`** |
     *
     * 접지 않으면 204 가 터널에서 유실된 뒤 다시 누른 받는 사람에게 「이미 끝난 정산입니다」가 뜬다 —
     * **자기가 방금 닫은 정산인데** 실패로 보인다. 「확인했다」와 「이미 확인돼 닫혔다」는 사용자가
     * 할 일이 같다(아무것도 없다).
     *
     * 접어도 안전한 근거: 정산이 닫히는 조건이 **모든 송금이 CONFIRMED** 인 것이므로,
     * 닫힌 정산에 남은 미확인 송금은 **존재할 수 없다**.
     */
    @Test
    fun `받았어요의 닫힘 409 는 성공으로 접는다`() = runBlocking {
        val api = FakeFareSplitApi(
            receivedError = httpError(409, "FARE_SPLIT_CLOSED", "이미 끝난 택시비 정산입니다."),
        )

        RemoteFareSplitRepository(api).confirmReceived(partyId, senderPublicId)

        assertEquals(listOf(senderPublicId), api.receivedCalls)
    }

    /**
     * 못 박는 것: **그 외 실패는 그대로 올린다** — 닫힘만 접는다.
     *
     * `FARE_SPLIT_TRANSFER_NOT_FOUND`(그 publicId 가 이 정산의 보내는 사람이 아니다)를 함께 접으면
     * **받는 사람이 엉뚱한 사람의 송금을 확인한 것이 성공으로 보인다** — 화면은 확인됐다고 그리고,
     * 서버에는 그 송금이 그대로 남아 정산이 영구히 닫히지 않는다.
     */
    @Test
    fun `받았어요의 다른 실패는 접지 않는다`() {
        val api = FakeFareSplitApi(
            receivedError = httpError(404, "FARE_SPLIT_TRANSFER_NOT_FOUND", "이 정산에서 택시비를 보내는 사람이 아닙니다."),
        )

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).confirmReceived(partyId, senderPublicId) }
        }

        assertTrue(e.isTransferNotFound)
        assertFalse("닫힘으로 잘못 읽었다", e.isClosed)
    }

    /**
     * **「못 받았어요」만 멱등이 아니다.** 409 `TRANSFER_NOT_SENT` 를 삼키면 안 된다 —
     * 「되돌릴 것이 없었다」는 받는 사람이 보고 있는 목록이 서버와 어긋났다는 뜻이라 **재조회가
     * 필요한 신호**다.
     */
    @Test
    fun `못 받았어요의 409 는 삼키지 않는다`() {
        val api = FakeFareSplitApi(notReceivedError = httpError(409, "TRANSFER_NOT_SENT", "보냈다고 표시된 송금만 못 받았다고 되돌릴 수 있습니다."))

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).markNotReceived(partyId, senderPublicId) }
        }

        assertTrue(e.isTransferNotSent)
    }

    /** 응답을 받지 못한 실패는 재시도를 안내할 수 있게 [FareSplitException.isNetwork] 로 표시된다. */
    @Test
    fun `네트워크 실패는 재시도 안내로 좁혀진다`() {
        val api = FakeFareSplitApi(sentError = IOException("타임아웃"))

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).markSent(partyId) }
        }

        assertTrue(e.isNetwork)
        assertNull(e.httpStatus)
        assertNull(e.code)
    }

    /** `{code,message}` 가 아닌 본문(스프링 기본 404·405)에서도 터지지 않는다 — 상태 코드만 남는다. */
    @Test
    fun `에러 본문이 code message 모양이 아니어도 터지지 않는다`() {
        val body = """{"timestamp":"2026-11-12T00:00:00Z","status":405,"error":"Method Not Allowed","path":"/api/v1/..."}"""
        val api = FakeFareSplitApi(sentError = HttpException(Response.error<Any>(405, body.toResponseBody("application/json".toMediaType()))))

        val e = assertThrows(FareSplitException::class.java) {
            runBlocking { RemoteFareSplitRepository(api).markSent(partyId) }
        }

        assertEquals(405, e.httpStatus)
        assertNull(e.code)
    }

    // ─── 내 정산 목록 ───

    /**
     * 상태를 **늘 명시해 보낸다** — 서버 `defaultValue = "NOT_SENT"` 에 맡기면 서버가 기본값을 바꿀 때
     * 홈 배너가 조용히 다른 목록을 그린다(에러로 드러나지 않는다).
     */
    @Test
    fun `내 정산 목록은 상태 이름을 쿼리 값으로 넘긴다`() = runBlocking {
        val api = FakeFareSplitApi()
        val repository = RemoteFareSplitRepository(api)

        repository.getMyFareSplits()
        repository.getMyFareSplits(TransferStatus.SENT)

        assertEquals(listOf("NOT_SENT", "SENT"), api.myListQueries)
    }

    @Test
    fun `내 정산 목록이 비어 있어도 실패가 아니다`() = runBlocking {
        val api = FakeFareSplitApi(myList = MyFareSplitListResponse())

        assertEquals(emptyList<Long>(), RemoteFareSplitRepository(api).getMyFareSplits().map { it.partyId })
    }

    // ─── 하네스 ───

    private fun httpError(code: Int, errorCode: String, message: String): HttpException = HttpException(
        Response.error<Any>(
            code,
            """{"code":"$errorCode","message":"$message"}""".toResponseBody("application/json".toMediaType()),
        ),
    )

    private fun detailResponse(partyId: Long? = this.partyId, totalFare: Int = 18_700) = FareSplitDetailResponse(
        partyId = partyId,
        status = "OPEN",
        totalFare = totalFare,
        payer = FareSplitPayerDto("11111111-1111-1111-1111-111111111111", "방장"),
        myRole = "PAYER",
        myAmount = totalFare,
        bank = FareSplitDetailResponse.BankDto("KAKAO", "카카오뱅크"),
        accountNumber = "3333012345678",
    )

    private class FakeFareSplitApi(
        private val detail: FareSplitDetailResponse = FareSplitDetailResponse(),
        private val myList: MyFareSplitListResponse = MyFareSplitListResponse(),
        private val createError: Throwable? = null,
        private val detailError: Throwable? = null,
        private val editError: Throwable? = null,
        private val sentError: Throwable? = null,
        private val receivedError: Throwable? = null,
        private val notReceivedError: Throwable? = null,
    ) : FareSplitApi {
        val createdRequests = mutableListOf<FareSplitRequestDto>()
        val editedRequests = mutableListOf<FareSplitRequestDto>()
        val myListQueries = mutableListOf<String>()
        val receivedCalls = mutableListOf<String>()
        val notReceivedCalls = mutableListOf<String>()
        var detailCalls = 0
        var sentCalls = 0

        override suspend fun createFareSplit(partyId: Long, request: FareSplitRequestDto): FareSplitDetailResponse {
            createdRequests += request
            createError?.let { throw it }
            return detail
        }

        override suspend fun getFareSplit(partyId: Long): FareSplitDetailResponse {
            detailCalls++
            detailError?.let { throw it }
            return detail
        }

        override suspend fun editFareSplit(partyId: Long, request: FareSplitRequestDto): FareSplitDetailResponse {
            editedRequests += request
            editError?.let { throw it }
            return detail
        }

        override suspend fun markSent(partyId: Long) {
            sentCalls++
            sentError?.let { throw it }
        }

        override suspend fun confirmReceived(partyId: Long, senderPublicId: String) {
            receivedCalls += senderPublicId
            receivedError?.let { throw it }
        }

        override suspend fun markNotReceived(partyId: Long, senderPublicId: String) {
            notReceivedCalls += senderPublicId
            notReceivedError?.let { throw it }
        }

        override suspend fun getMyFareSplits(status: String): MyFareSplitListResponse {
            myListQueries += status
            return myList
        }
    }
}
