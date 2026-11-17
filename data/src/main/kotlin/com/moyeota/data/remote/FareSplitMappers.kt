package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.FareSplitDetailResponse
import com.moyeota.data.remote.dto.FareSplitPayerDto
import com.moyeota.data.remote.dto.FareSplitRequestDto
import com.moyeota.data.remote.dto.MyFareSplitListResponse
import com.moyeota.domain.model.Bank
import com.moyeota.domain.model.FareSplitBank
import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitDraft
import com.moyeota.domain.model.FareSplitException
import com.moyeota.domain.model.FareSplitParticipant
import com.moyeota.domain.model.FareSplitPolicy
import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.FareSplitTransfer
import com.moyeota.domain.model.MyFareSplit
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.model.WithdrawnMemberNames
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * 도메인 초안 → 요청 본문.
 *
 * 하는 일이 둘 있고 **둘 다 「잊어도 되게 만드는」 배치**다:
 * 1. **계좌번호 정규화** — [FareSplitPolicy.normalizeAccountNumber] 를 여기서 부른다. 화면이 잊어도
 *    하이픈이 섞인 값이 서버로 나가지 않는다(나가면 400 `INVALID_ACCOUNT_NUMBER` 인데, 그 실패는
 *    금액을 다 채운 뒤에 온다). 규칙 자체는 도메인에 하나만 있다 — 매퍼가 베껴 쓰지 않는다.
 * 2. **키 이름 고정** — `userPublicId`·`amount`·`bankCode`. 서버는 모르는 키를 무시하므로 이름이
 *    틀리면 「필드 누락」 400 으로만 드러나고 원인이 앱 쪽 오타임이 가려진다
 *    (`toFavoriteDeleteRequestDto` 와 같은 이유).
 *
 * 금액·탑승자 검증은 **하지 않는다** — 그건 보내기 전에 화면이
 * [FareSplitPolicy.problemOf] 로 묻는다. 매퍼가 조용히 고치면 사용자가 입력한 것과 다른 값이 나간다.
 */
fun FareSplitDraft.toRequestDto(): FareSplitRequestDto = FareSplitRequestDto(
    totalFare = totalFare,
    bankCode = bankCode,
    accountNumber = FareSplitPolicy.normalizeAccountNumber(accountNumber),
    payments = shares.map {
        FareSplitRequestDto.PaymentDto(userPublicId = it.riderPublicId, amount = it.amount)
    },
)

/**
 * 정산 상세 응답 → 도메인.
 *
 * @param requestedPartyId **호출에 쓴 partyId.** 서버가 `partyId` 를 말해 주지 않았을 때 이 값으로
 *   메운다 — 0 으로 접으면 이후 「받았어요」·「보냈어요」가 **존재하지 않는 방으로** 나가고, 돌아온
 *   404 는 「정산이 없다」로 읽혀 화면이 「정산 시작하기」를 띄운다(원인이 완전히 가려진다).
 *   앱은 어느 방을 물었는지 **이미 알고 있으므로** 추측이 아니다.
 */
/**
 * 내 송금 상태 — 서버가 준 `myTransferStatus` 를 쓰되, **없으면 `transfers` 에서 내 행을 찾아 메운다.**
 *
 * ### 폴백이 필요한 이유: 서버가 아직 그 필드를 안 보낼 수 있다 ★
 * `myTransferStatus` 는 Backend PR #328 이 더한 값이다. 그 배포가 닿기 전의 서버는 **키 자체를
 * 보내지 않고**, `ignoreUnknownKeys` 정책상 그건 조용한 null 이 된다. 폴백이 없으면 보내는 사람
 * 화면이 「내 송금 상태를 확인하지 못했어요」로 떨어지고 **「보냈어요」가 영영 안 뜬다** — 즉
 * 앱이 **지금 떠 있는 서버에서 쓸 수 없다**(로컬 실측 2026-11-12, partyId 4146 에서 실제로 그랬다).
 *
 * 옛 길을 되살리는 것이지 새로 만드는 게 아니다: 그 필드가 생기기 전에는 보내는 사람이 `transfers`
 * 에서 자기 publicId 로 자기 행을 찾는 것이 **유일한 길**이었다.
 *
 * ### 받는 사람에게는 폴백하지 않는다 ★
 * PAYER 의 null 은 「모른다」가 아니라 **「보낼 것이 없다」**는 뜻이고(서버가 일부러 비운다),
 * 받는 사람의 행은 애초에 `transfers` 에 없다. 그래서 [FareSplitRole.SENDER] 일 때만 찾는다 —
 * 가리지 않으면 방장에게 「보냈어요」가 떠서 403 `NOT_FARE_SPLIT_SENDER` 가 된다.
 *
 * 세 경우 모두 null 로 남는다: 내가 받는 사람이거나 · 세션 uuid 를 모르거나 · 서버가 내 행의
 * publicId 를 못 채웠거나. 그때 화면은 버튼을 닫고 다시 불러오기를 권한다 — 모르는 상태에
 * 버튼을 열어 두는 것보다 낫다.
 */
private fun FareSplitDetailResponse.myTransferStatusOf(currentUserUuid: String?): TransferStatus? {
    toTransferStatusOrNull(myTransferStatus)?.let { return it }

    if (toFareSplitRole(myRole) != FareSplitRole.SENDER) return null
    val uuid = currentUserUuid?.takeIf { it.isNotBlank() } ?: return null
    val mine = transfers.firstOrNull { !it.publicId.isNullOrBlank() && it.publicId == uuid } ?: return null

    return toTransferStatus(mine.status)
}

fun FareSplitDetailResponse.toFareSplitDetail(
    requestedPartyId: Long,
    currentUserUuid: String? = null,
): FareSplitDetail = FareSplitDetail(
    partyId = partyId ?: requestedPartyId,
    status = toFareSplitStatus(status),
    totalFare = totalFare ?: 0,
    payer = payer.toFareSplitParticipant(),
    myRole = toFareSplitRole(myRole),
    myAmount = myAmount ?: 0,
    // PAYER 는 서버가 null 을 준다 — 모르는 문자열도 같은 null 로 떨어뜨린다. NOT_SENT 로 접지 않는
    // 근거는 [FareSplitDetail.myTransferStatus] KDoc(방장에게 「보냈어요」가 뜨는 실패).
    // 서버가 그 필드를 아직 안 보내면 `transfers` 에서 내 행을 찾아 메운다([myTransferStatusOf]).
    myTransferStatus = myTransferStatusOf(currentUserUuid),
    bank = bank.toFareSplitBank(),
    accountNumber = accountNumber,
    tossDeeplink = tossDeeplink,
    transfers = transfers.map { it.toFareSplitTransfer() },
    createdAt = createdAt,
    closedAt = closedAt,
)

/**
 * 내가 보낼 정산 목록 → 도메인.
 *
 * **`partyId` 가 없는 줄은 버린다.** partyId 는 그 줄을 눌러 상세로 가는 유일한 열쇠라, 없으면
 * 화면에 보여 봐야 **누를 수 없는 줄**이다(또는 눌러서 엉뚱한 방으로 간다). 한 줄 때문에 배너
 * 전체를 날리지 않고 그 줄만 버리는 것은 즐겨찾기 목록과 같은 관례다(`toFavoritePlaces`).
 *
 * 정렬하지 않는다 — 서버가 **정산 시작 최신순**으로 준다. 앱이 다시 정렬하면 `createdAt` 이 null 인
 * 줄의 자리가 앱마다 달라진다.
 */
fun MyFareSplitListResponse.toMyFareSplits(): List<MyFareSplit> = fareSplits.mapNotNull { item ->
    val partyId = item.partyId ?: return@mapNotNull null

    MyFareSplit(
        partyId = partyId,
        departure = item.departure,
        destination = item.destination,
        payer = item.payer.toFareSplitParticipant(),
        myAmount = item.myAmount ?: 0,
        status = toTransferStatus(item.status),
        createdAt = item.createdAt,
    )
}

private fun FareSplitDetailResponse.TransferItemDto.toFareSplitTransfer(): FareSplitTransfer = FareSplitTransfer(
    senderPublicId = publicId.orEmpty(),
    nickname = nickname ?: WithdrawnMemberNames.NO_SUMMARY,
    amount = amount ?: 0,
    status = toTransferStatus(status),
    sentAt = sentAt,
    confirmedAt = confirmedAt,
    maskedRealName = maskedRealName,
)

/**
 * 받는 사람·보내는 사람 한 명 → 도메인.
 *
 * 서버가 유저 요약을 못 찾으면 publicId·nickname 을 **함께** null 로 내린다. 그때 닉네임 자리에
 * [WithdrawnMemberNames.NO_SUMMARY] 를 넣는 것은 `PartyMappers.toUser` 와 **같은 관례**다 —
 * 두 곳이 다른 문자열을 쓰면 「사람 이름이 아닌 것」을 거르는 화면 쪽 검사
 * ([WithdrawnMemberNames.ALL])가 한쪽을 못 걸러 안내문이 「탈퇴한 회원님께 보내세요」가 된다.
 *
 * publicId 는 **빈 문자열**로 남긴다([com.moyeota.domain.model.FareSplitParticipant] 와 같은 관례) —
 * 자리값을 지어 넣으면 「받았어요」가 그 id 로 나가 404 가 된다.
 */
private fun FareSplitPayerDto?.toFareSplitParticipant(): FareSplitParticipant = FareSplitParticipant(
    publicId = this?.publicId.orEmpty(),
    nickname = this?.nickname ?: WithdrawnMemberNames.NO_SUMMARY,
)

/**
 * 은행 코드·이름 → 도메인.
 *
 * **모르는 코드를 버리지도, 아는 은행으로 떨어뜨리지도 않는다.** 서버가 이름을 함께 주므로 모르는
 * 코드도 화면에 정확히 그려진다([Bank] KDoc 의 대가 비대칭 표).
 *
 * [FareSplitBank.displayName] 의 우선순위는 **서버 이름 → 아는 은행의 이름 → 코드**다.
 * 서버 이름을 1순위에 두는 이유: 은행이 이름을 바꿨을 때(「대구은행」→「iM뱅크」) 앱의 상수는
 * 낡지만 서버 값은 배포로 갱신된다 — 둘이 어긋나면 **서버를 믿는다**. 마지막이 코드인 것은
 * 「KAKAO」라도 보여 주는 쪽이 빈칸보다 낫기 때문이다(사용자가 어느 은행인지 알아볼 수 있다).
 */
private fun FareSplitDetailResponse.BankDto?.toFareSplitBank(): FareSplitBank {
    val code = this?.code.orEmpty()
    val known = Bank.ofCodeOrNull(this?.code)

    return FareSplitBank(
        code = code,
        displayName = this?.name?.takeIf { it.isNotBlank() } ?: known?.displayName ?: code,
        known = known,
    )
}

/**
 * 서버 `FareSplitStatus` → 도메인. **모르는 값과 null 은 [FareSplitStatus.OPEN]** 이다.
 *
 * | 모르는 값을 … | 진행 중인 정산을 틀리게 읽으면 | 닫힌 정산을 틀리게 읽으면 |
 * |---|---|---|
 * | **[FareSplitStatus.OPEN]**(지금) | — | 보낼 버튼이 떠서 눌렀을 때 409 `FARE_SPLIT_CLOSED` 를 받는다. 서버가 막아 주고 화면은 상세를 다시 받아 고친다 — **복구된다** |
 * | [FareSplitStatus.COMPLETED] | 「정산이 끝났어요」가 떠서 보내는 사람이 **자기 몫을 영영 보내지 않는다.** 서버는 그 송금을 NOT_SENT 로 세고 4시간마다 재요청 알림만 보낸다 — **복구되지 않는다** | — |
 *
 * 또 하나: 닫힌 정산은 서버가 계좌번호를 **지워서** 보내므로, OPEN 으로 잘못 읽어도 화면에 띄울
 * 계좌가 없다([FareSplitDetail.hasLostAccount] 로 떨어진다). 즉 한쪽 오류는 서버 데이터가
 * 한 번 더 막아 준다.
 */
internal fun toFareSplitStatus(raw: String?): FareSplitStatus = when (raw) {
    "COMPLETED" -> FareSplitStatus.COMPLETED
    "EXPIRED" -> FareSplitStatus.EXPIRED
    else -> FareSplitStatus.OPEN
}

/**
 * 서버 `FareSplitRole` → 도메인. **모르는 값과 null 은 [FareSplitRole.SENDER]** 다.
 *
 * 두 값뿐이라 「모르는 값」이 실제로 올 일은 서버가 역할을 추가할 때뿐이지만, 어느 쪽으로
 * 떨어뜨리느냐는 대가가 다르다:
 *
 * | 모르는 값을 … | 되돌릴 수 있나 |
 * |---|---|
 * | **[FareSplitRole.SENDER]**(지금) | **된다.** 화면이 「보냈어요」를 띄우고, 그 동작은 **멱등**이다 — 실제로 받는 사람이었다면 403 `NOT_FARE_SPLIT_SENDER` 로 막히고 아무것도 바뀌지 않는다 |
 * | [FareSplitRole.PAYER] | **안 된다.** 「받았어요」가 떠서, 실제로 받는 사람이었다면 **성공한다** — 받지도 않은 돈을 확인으로 닫고, 마지막 한 건이면 정산이 COMPLETED 로 **닫히며 계좌번호가 지워진다.** 서버에 되돌리는 길이 없다 |
 *
 * 즉 모르는 값은 **되돌릴 수 있는 쪽**에 둔다. 한 정산에 PAYER 는 한 명이고 SENDER 는 N-1 명이라
 * 확률로도 같은 방향이다.
 *
 * (보강 근거: [FareSplitDetailResponse.tossDeeplink] 가 non-null 이면 서버가 그 사람을 SENDER 로
 * 본 것이 확실하다 — 서버가 `role == SENDER` 일 때만 싣는다. 역할 문자열이 깨진 응답에서도 그 쪽
 * 증거는 남아 있다.)
 */
internal fun toFareSplitRole(raw: String?): FareSplitRole = when (raw) {
    "PAYER" -> FareSplitRole.PAYER
    else -> FareSplitRole.SENDER
}

/**
 * 서버 `TransferStatus` → 도메인. **모르는 값과 null 은 [TransferStatus.NOT_SENT]** 다.
 *
 * | 모르는 값을 … | 아직 안 보낸 송금을 틀리게 읽으면 | 이미 확인된 송금을 틀리게 읽으면 |
 * |---|---|---|
 * | **[TransferStatus.NOT_SENT]**(지금) | — | 받는 사람 화면에 「받았어요」가 한 번 더 떠서 누르면 **멱등 204** 다(아무것도 바뀌지 않는다) |
 * | [TransferStatus.CONFIRMED] | 받지 않은 돈이 **받은 것으로** 표시돼, 받는 사람이 그 사람에게 다시 요청하지 않는다. 서버는 그 송금을 NOT_SENT 로 세고 있으므로 정산은 영구히 닫히지 않는다 | — |
 *
 * 「할 일이 남아 있다」가 「끝났다」보다 안전하다 — 남은 일은 눌러서 확인하면 되고, 끝났다고 말한
 * 일은 아무도 다시 보지 않는다.
 */
internal fun toTransferStatus(raw: String?): TransferStatus = when (raw) {
    "SENT" -> TransferStatus.SENT
    "CONFIRMED" -> TransferStatus.CONFIRMED
    else -> TransferStatus.NOT_SENT
}

/**
 * `myTransferStatus` → 도메인. **[toTransferStatus] 와 달리 모르는 값·null 을 null 로 둔다.**
 *
 * 같은 enum 인데 떨어지는 방향이 반대인 이유는 **null 의 뜻이 다르기** 때문이다:
 *
 * | 자리 | null 의 뜻 | 그래서 |
 * |---|---|---|
 * | `transfers[].status` | 서버가 말해 주지 않았다(올 수 없는 값) | 「할 일이 남았다」(NOT_SENT)로 떨어뜨린다 |
 * | `myTransferStatus` | **내가 받는 사람이다**(서버가 일부러 비운다) | null 을 그대로 지킨다 |
 *
 * 여기서 NOT_SENT 로 접으면 **방장 화면에 「보냈어요」가 뜬다** — 누르면 403
 * `NOT_FARE_SPLIT_SENDER` 이고, 사용자는 왜 자기 정산에서 거절당하는지 알 수 없다.
 *
 * 모르는 문자열(서버가 상태를 늘렸을 때)도 null 로 둔다. 그때 보내는 사람 화면은 버튼을 닫고
 * ([FareSplitDetail.canMarkSent] 가 false) 상세를 다시 읽게 된다 — 모르는 상태에 버튼을 열어
 * 두는 것보다 낫다.
 */
internal fun toTransferStatusOrNull(raw: String?): TransferStatus? = when (raw) {
    "NOT_SENT" -> TransferStatus.NOT_SENT
    "SENT" -> TransferStatus.SENT
    "CONFIRMED" -> TransferStatus.CONFIRMED
    else -> null
}

/**
 * 정산 요청 실패를 [FareSplitException] 으로 좁힌다 — presentation 이 HTTP 를 모르게 하는 경계다
 * ([toFavoritePlaceException]·`toChatException` 과 같은 방식).
 *
 * **상태 코드로는 아무것도 갈리지 않는다** — 400 에 4종, 403 에 5종, 404 에 3종, 409 에 6종이 몰려
 * 있고 같은 상태 안에서 사용자가 할 일이 정반대인 것들이 섞인다(`FareSplitErrorCode` 18종).
 * 그래서 본문의 `code` 까지 반드시 읽는다. 자세한 표는 [FareSplitException] KDoc 에 있다.
 *
 * 본문 파싱을 runCatching 으로 감싸는 이유: `{code,message}` 가 아닌 응답이 실제로 온다 —
 * 스프링이 직접 만드는 404·405 는 `{timestamp,status,error,path}` 다(즐겨찾기에서 실측).
 */
internal fun Throwable.toFareSplitException(existing: FareSplitDetail? = null): FareSplitException = when (this) {
    is FareSplitException -> if (existing == null) this else copyWith(existing)

    is HttpException -> {
        val body = runCatching {
            response()?.errorBody()?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { errorJson.decodeFromString<ApiErrorDto>(it) }
        }.getOrNull()
        FareSplitException(
            code = body?.code,
            serverMessage = body?.message,
            httpStatus = code(),
            existing = existing,
            cause = this,
        )
    }

    // 응답 자체를 못 받은 경우(연결 거부·타임아웃·DNS). 사용자에게는 재시도를 안내한다.
    is IOException -> FareSplitException(code = null, serverMessage = null, isNetwork = true, cause = this)

    else -> FareSplitException(code = null, serverMessage = null, cause = this)
}

/** 이미 좁혀 둔 예외에 [FareSplitException.existing] 만 덧붙인다(원래 code·message·status 를 보존한다). */
private fun FareSplitException.copyWith(existing: FareSplitDetail): FareSplitException = FareSplitException(
    code = code,
    serverMessage = serverMessage,
    httpStatus = httpStatus,
    isNetwork = isNetwork,
    existing = existing,
    cause = cause,
)

/**
 * 정산 호출을 감싸 실패를 [FareSplitException] 으로 통일한다.
 * [CancellationException] 은 그대로 통과시킨다 — 취소를 에러로 바꾸면 화면 이탈이 에러 문구로 보인다.
 */
internal suspend fun <T> fareSplitCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toFareSplitException()
    }
