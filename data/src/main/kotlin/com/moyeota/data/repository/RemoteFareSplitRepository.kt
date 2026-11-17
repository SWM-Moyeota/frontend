package com.moyeota.data.repository

import com.moyeota.data.remote.FareSplitApi
import com.moyeota.data.remote.fareSplitCall
import com.moyeota.data.remote.toFareSplitDetail
import com.moyeota.data.remote.toFareSplitException
import com.moyeota.data.remote.toMyFareSplits
import com.moyeota.data.remote.toRequestDto
import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitDraft
import com.moyeota.domain.model.FareSplitException
import com.moyeota.domain.model.MyFareSplit
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.repository.FareSplitRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException

/**
 * 택시비 정산 저장소 — 서버가 **보는 사람에 맞춰** 응답을 만들므로 세션을 받지 않는다.
 *
 * `RemoteRideRepository`·`RemoteChatRepository` 는 「멤버 중 누가 나인가」를 앱이 판정해야 해서
 * 세션을 받지만, 여기서는 서버가 `myRole`·`myAmount` 를 **토큰 기준으로 계산해** 내려 준다
 * ([com.moyeota.domain.model.FareSplitRole]). 앱이 같은 판정을 다시 세우면 서버와 어긋날 수 있는
 * 두 번째 진실이 생긴다.
 *
 * 예외는 하나다 — 보내는 사람이 **자기 송금 상태**를 찾을 때는 자기 publicId 가 필요하다
 * (응답에 `myStatus` 가 없다). 그건 화면이
 * [com.moyeota.domain.model.FareSplitDetail.transferOf] 로 하고, 저장소가 끼어들지 않는다:
 * 세션을 여기로 끌어들이면 「내 행」을 **매퍼가 찍어 둔 상세**와 화면이 세션으로 찾는 결과가
 * 둘로 갈린다.
 *
 * 실패는 전부 [FareSplitException] 으로 좁힌다(`fareSplitCall`).
 */
class RemoteFareSplitRepository(
    private val api: FareSplitApi,
    /**
     * 세션 — **내 송금 상태를 메우는 데만** 쓴다([FareSplitMappers] 의 `myTransferStatusOf`).
     *
     * 서버가 `myTransferStatus` 를 보내 주면(Backend PR #328) 쓸 일이 없다. 그 배포가 닿기 전까지는
     * 보내는 사람이 `transfers` 에서 **자기 publicId 로 자기 행을 찾는 것이 유일한 길**이고, 그
     * 판정의 열쇠가 세션 uuid 다. 없어도 앱은 돌아간다 — 그때는 상태를 모른다고 말하고 버튼을 닫는다.
     *
     * 기본값을 null 로 두는 이유: 이 저장소의 나머지 여섯 동작은 세션을 **전혀** 쓰지 않는다
     * (서버가 토큰으로 `myRole`·`myAmount` 를 계산해 준다). 필수로 만들면 쓰지도 않는 의존을
     * 테스트마다 들려 보내게 된다.
     */
    private val session: UserSession? = null,
) : FareSplitRepository {

    /**
     * ### 409 `FARE_SPLIT_ALREADY_EXISTS` 를 **그냥 올리지 않는다**
     * 서버 Swagger 가 「이미 정산을 시작한 방(GET 으로 본다)」라고 적어 둔, **복구 경로가 명시된**
     * 실패다. 그 GET 을 화면마다 시키면 한 곳만 빠뜨려도 사용자는 **이미 존재하는 자기 정산을
     * 볼 수 없게** 된다(시작도 조회도 막힌 막다른 화면이 된다). 그래서 데이터 계층이 이어서 읽고
     * [FareSplitException.existing] 에 담아 올린다 — `PartyJoinException.joinedStatus` 와 같은 배치다.
     *
     * 성공으로 **바꿔서** 돌려주지는 않는다. 사용자가 방금 입력한 금액·계좌는 **저장되지 않았고**
     * (서버의 기존 정산이 그대로다), 그것을 성공으로 보여 주면 화면은 입력한 값이 반영된 줄 안다.
     * 「실패지만 볼 것은 있다」가 사실에 가깝다.
     *
     * 이어 읽는 GET 이 실패하면 **그 실패는 삼킨다** — 원래 실패(409)를 네트워크 오류 같은 것으로
     * 갈아 끼우면 화면이 「다시 시작해 보세요」로 안내하게 되고, 그건 영원히 409 다.
     */
    override suspend fun startFareSplit(partyId: Long, draft: FareSplitDraft): FareSplitDetail =
        try {
            api.createFareSplit(partyId, draft.toRequestDto()).toFareSplitDetail(partyId, session?.currentUserUuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val failure = e.toFareSplitException()

            throw if (failure.isAlreadyExists) failure.withExisting(partyId) else failure
        }

    /**
     * **404 `FARE_SPLIT_NOT_FOUND` 만 null 로 접는다** — 「아직 아무도 시작하지 않았다」는 끝난 방의
     * 정상 상태이고, 그걸 예외로 올리면 모든 호출자가 「정상인 404」와 「진짜 실패」를 가리려고
     * try/catch 를 쓰게 된다(한 곳만 빠뜨리면 멀쩡한 방에 빨간 에러가 뜬다).
     *
     * **상태 코드만으로 판별하지 않는다.** 같은 404 에 `PARTY_NOT_FOUND`(방이 없다)와
     * `FARE_SPLIT_TRANSFER_NOT_FOUND` 도 있다 — 앞쪽을 「정산이 없다」로 접으면 사라진 방에
     * 「정산 시작하기」가 떠서, 눌렀을 때 또 404 를 받는다. `code` 를 보고 가른다.
     * (전체 삭제의 404 를 `code` 없이 상태로만 삼키는 `RemotePlaceRepository` 와 다른 이유가 이것이다 —
     * 그쪽 경로의 404 는 한 종류뿐이었다.)
     *
     * 403 `NOT_FARE_SPLIT_PARTICIPANT` 는 **그대로 올린다.** 「정산이 없다」와 「정산은 있는데 내가
     * 당사자가 아니다」는 화면이 할 말이 다르고, null 로 접으면 도착을 누르지 않은 사람에게
     * 「정산 시작하기」가 떠서 눌렀을 때 403 `NOT_A_RIDER` 를 받는다.
     */
    override suspend fun getFareSplit(partyId: Long): FareSplitDetail? =
        try {
            api.getFareSplit(partyId).toFareSplitDetail(partyId, session?.currentUserUuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val failure = e.toFareSplitException()

            if (failure.isFareSplitNotFound) null else throw failure
        }

    /**
     * 수정은 **전부 다시 보낸다**(부분 수정이 아니다 — [FareSplitDraft] 네 필드가 모두 필수다).
     * 응답이 고친 상세이므로 화면은 **그것으로 갈아 끼우고** 따로 재조회하지 않는다.
     */
    override suspend fun editFareSplit(partyId: Long, draft: FareSplitDraft): FareSplitDetail = fareSplitCall {
        api.editFareSplit(partyId, draft.toRequestDto()).toFareSplitDetail(partyId, session?.currentUserUuid)
    }

    /**
     * **멱등 204 는 성공으로만 다룬다.** 이미 SENT·CONFIRMED 였는지 지금 바뀌었는지 서버가
     * 구분해 주지 않으므로(응답이 같다) 앱이 그 차이를 알 길이 없다 — 알아낸 척하는 반환값
     * (`Boolean` 같은 것)을 만들지 않는다. 화면이 그 값으로 문구를 갈라 쓰면 **틀린 문구**가 뜬다.
     *
     * 돌려줄 것이 없으므로 성공 뒤 상세·배너 재조회는 **호출자 몫**이다
     * ([com.moyeota.domain.repository.FareSplitRepository] KDoc 의 표).
     */
    override suspend fun markSent(partyId: Long) = fareSplitCall {
        api.markSent(partyId)
    }

    /**
     * 「받았어요」. 마지막 확인이 정산을 닫고 계좌번호를 지우므로 성공 뒤 재조회가 필수다.
     *
     * ### 🔴 멱등이 **끝까지** 가지 않는다 — 실측으로 알아냈다 ★
     * 서버 Swagger 는 「이미 CONFIRMED 면 아무것도 바꾸지 않고 204」라고 적어 두었다. 사실이 아니다.
     * 마지막 송금을 확인하면 정산이 `COMPLETED` 로 닫히고, 그다음부터는 **닫힘 검사가 먼저 걸려**
     * 409 `FARE_SPLIT_CLOSED` 가 온다(로컬 실측 2026-11-12, partyId 4143):
     *
     * | 호출 | 서버 | 뜻 |
     * |---|---|---|
     * | 1회 | 204 | 확인됐다 → 정산이 닫혔다 |
     * | 2회 | **409 `FARE_SPLIT_CLOSED`** | 「이미 CONFIRMED」가 아니라 「닫힌 정산」으로 답한다 |
     *
     * 그래서 **그 409 를 성공으로 접는다.** 접지 않으면 204 응답이 터널에서 유실된 뒤 다시 누른
     * 받는 사람에게 「이미 끝난 정산입니다」가 뜬다 — 자기가 방금 닫은 정산인데 실패로 보이고,
     * 화면을 새로 읽을 때까지 무엇이 참인지 알 수 없다. 「확인했다」와 「이미 확인돼 닫혔다」는
     * 사용자가 할 일이 **같다**(아무것도 없다).
     *
     * 그 대가는 하나다: **한 번도 확인하지 않은 송금을 닫힌 정산에서 누르면** 그것도 성공으로 보인다.
     * 그럴 수가 없다 — 정산이 닫히는 조건이 **모든 송금이 CONFIRMED** 인 것이기 때문이다. 즉
     * 닫힌 정산에 남은 미확인 송금은 존재하지 않는다.
     *
     * [markSent] 쪽은 접지 않는다. 보내는 사람에게 닫힌 정산의 409 는 「내가 보낼 필요가 없어졌다」는
     * **새 정보**이고(받는 사람이 현금으로 받고 닫았을 수 있다), 그때 화면은 상세를 다시 읽어
     * 「정산이 끝났어요」로 바꿔야 한다 — 성공으로 접으면 보냈다는 표시만 남고 끝난 사실을 놓친다.
     */
    override suspend fun confirmReceived(partyId: Long, senderPublicId: String) {
        try {
            fareSplitCall { api.confirmReceived(partyId, senderPublicId) }
        } catch (e: FareSplitException) {
            if (!e.isClosed) throw e
        }
    }

    /**
     * **멱등이 아니다** — SENT 인 송금에서만 되고, 아니면 409 `TRANSFER_NOT_SENT` 다
     * ([FareSplitException.isTransferNotSent]). 그 실패를 삼키지 않는 이유: 「되돌렸다」와
     * 「되돌릴 것이 없었다」는 받는 사람이 보고 있는 목록이 서버와 어긋났다는 뜻이라
     * **재조회가 필요한 신호**다.
     */
    override suspend fun markNotReceived(partyId: Long, senderPublicId: String) = fareSplitCall {
        api.markNotReceived(partyId, senderPublicId)
    }

    /**
     * 상태는 **늘 명시해 보낸다** — 서버에 `defaultValue = "NOT_SENT"` 가 있지만, 기본값을 서버에
     * 맡기면 서버가 그 값을 바꿀 때 홈 배너가 조용히 다른 목록을 그린다(에러로 드러나지 않는다).
     *
     * 빈 목록은 **정상**이고 에러가 아니다. 다만 「내가 보낼 돈이 없다」는 뜻일 뿐이라
     * 「정산이 없다」로 읽으면 안 된다 — 받는 사람인 정산은 이 목록에 **절대** 나타나지 않는다.
     */
    override suspend fun getMyFareSplits(status: TransferStatus): List<MyFareSplit> = fareSplitCall {
        api.getMyFareSplits(status.name).toMyFareSplits()
    }

    /**
     * 409 `FARE_SPLIT_ALREADY_EXISTS` 에 **이미 있는 정산**을 덧붙인다. 이어 읽는 GET 이 실패하면
     * 원래 예외를 그대로 돌려준다(그 실패로 갈아 끼우지 않는다 — [startFareSplit] KDoc).
     */
    private suspend fun FareSplitException.withExisting(partyId: Long): FareSplitException {
        // runCatching 을 쓰지 않는다 — 그러면 CancellationException 까지 삼켜, 화면을 떠나는 순간의
        // 취소가 「409 였다」로 바뀌어 올라간다(이탈이 에러 문구로 보이는 그 버그다).
        val existing = try {
            getFareSplit(partyId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

        return if (existing == null) this else toFareSplitException(existing)
    }
}
