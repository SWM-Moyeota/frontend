package com.moyeota.presentation.feature.faresplit

import com.moyeota.domain.model.Bank
import com.moyeota.domain.model.FareSplitBank
import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitException
import com.moyeota.domain.model.FareSplitParticipant
import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.FareSplitTransfer
import com.moyeota.domain.model.MyFareSplit
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.model.User

/**
 * 정산 테스트가 함께 쓰는 값 만들기.
 *
 * 한 곳에 모으는 이유는 `AnalyticsTestSupport.kt` 와 같다 — [FareSplitDetail] 은 필드가 열두 개라
 * 테스트마다 다시 적으면 **무엇을 보려고 만든 값인지가 잡음에 묻힌다.** 기본값은 「가장 흔한 정상
 * 상태」(OPEN · 받는 사람 화면 · 보내는 사람 둘)로 두고, 각 테스트는 보려는 한 가지만 바꾼다.
 */

internal const val PAYER_ID = "payer-uuid"
internal const val SENDER_A = "sender-a-uuid"
internal const val SENDER_B = "sender-b-uuid"

internal fun transfer(
    publicId: String = SENDER_A,
    nickname: String = "동승자A",
    amount: Int = 6_233,
    status: TransferStatus = TransferStatus.NOT_SENT,
    maskedRealName: String? = null,
) = FareSplitTransfer(
    senderPublicId = publicId,
    nickname = nickname,
    amount = amount,
    status = status,
    sentAt = if (status != TransferStatus.NOT_SENT) "2026-11-12T01:00:00Z" else null,
    confirmedAt = if (status == TransferStatus.CONFIRMED) "2026-11-12T02:00:00Z" else null,
    maskedRealName = maskedRealName,
)

internal fun detail(
    status: FareSplitStatus = FareSplitStatus.OPEN,
    role: FareSplitRole = FareSplitRole.PAYER,
    /**
     * 내 송금 상태. **기본값이 역할을 따라간다** — 받는 사람은 서버가 **일부러 null** 을 주므로
     * (`FareSplit.transferStatusOf` 가 빈 값) 그 기본값을 NOT_SENT 로 두면 테스트가 실제로 올 수
     * 없는 응답을 만들어 「방장에게 「보냈어요」가 뜨는」 결함을 통과시킨다.
     */
    myTransferStatus: TransferStatus? = if (role == FareSplitRole.SENDER) TransferStatus.NOT_SENT else null,
    totalFare: Int = 18_700,
    myAmount: Int = 6_234,
    accountNumber: String? = "3333012345678",
    tossDeeplink: String? = null,
    transfers: List<FareSplitTransfer> = listOf(
        transfer(SENDER_A, "동승자A", 6_233),
        transfer(SENDER_B, "동승자B", 6_233),
    ),
    payer: FareSplitParticipant = FareSplitParticipant(PAYER_ID, "방장"),
    bank: FareSplitBank = FareSplitBank(Bank.KAKAO.code, Bank.KAKAO.displayName, Bank.KAKAO),
) = FareSplitDetail(
    partyId = 42L,
    status = status,
    totalFare = totalFare,
    payer = payer,
    myRole = role,
    myAmount = myAmount,
    myTransferStatus = myTransferStatus,
    bank = bank,
    accountNumber = accountNumber,
    tossDeeplink = tossDeeplink,
    transfers = transfers,
    createdAt = "2026-11-12T00:00:00Z",
    closedAt = if (status == FareSplitStatus.OPEN) null else "2026-11-12T03:00:00Z",
)

internal fun member(
    id: String,
    nickname: String = id,
    isMe: Boolean = false,
    isHost: Boolean = false,
    arrived: Boolean = true,
) = User(
    id = id,
    nickname = nickname,
    verifiedLabel = "",
    rating = 0.0,
    rideCount = 0,
    isMe = isMe,
    isHost = isHost,
    hasArrived = arrived,
)

internal fun ride(
    status: RideStatus = RideStatus.COMPLETED,
    members: List<User> = listOf(
        member(PAYER_ID, "방장", isMe = true, isHost = true),
        member(SENDER_A, "동승자A"),
        member(SENDER_B, "동승자B"),
    ),
    estimatedFare: Int? = null,
) = Ride(
    id = "42",
    origin = "센텀시티역 3번 출구",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 3,
    members = members,
    farePerPerson = 0,
    totalFare = 0,
    status = status,
    estimatedFare = estimatedFare,
)

internal fun myFareSplit(
    partyId: Long = 42L,
    departure: String? = "센텀시티역",
    destination: String? = "서면역",
    myAmount: Int = 6_233,
    status: TransferStatus = TransferStatus.NOT_SENT,
) = MyFareSplit(
    partyId = partyId,
    departure = departure,
    destination = destination,
    payer = FareSplitParticipant(PAYER_ID, "방장"),
    myAmount = myAmount,
    status = status,
    createdAt = "2026-11-12T00:00:00Z",
)

/** 서버 코드 하나로 만든 실패. `serverMessage` 는 **일부러 사람이 읽을 만한 한국어**로 둔다 —
 *  그래도 화면이 그걸 쓰지 않는지 보는 테스트가 있다([FareSplitFailureMessageTest]). */
internal fun failure(
    code: String?,
    httpStatus: Int? = 400,
    serverMessage: String? = "서버가 보낸 원문입니다",
    isNetwork: Boolean = false,
) = FareSplitException(
    code = code,
    serverMessage = serverMessage,
    httpStatus = httpStatus,
    isNetwork = isNetwork,
)
