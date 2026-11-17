package com.moyeota.presentation.feature.faresplit

import com.moyeota.domain.model.Bank
import com.moyeota.domain.model.FareShare
import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitDraft
import com.moyeota.domain.model.FareSplitDraftProblem
import com.moyeota.domain.model.FareSplitPolicy
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.WithdrawnMemberNames
import com.moyeota.domain.model.fareSplitRiders
import com.moyeota.domain.model.isHostedByMe

/**
 * 40 택시비 정산 시작·수정 화면이 들고 있는 **입력 상태와 그 판정** — 전부 순수 함수다.
 *
 * ### 왜 떼어 내는가 ★
 * 이 화면의 진짜 어려움은 그리기가 아니라 **언제 보낼 수 있는가**다. 서버는 틀린 요청을 400 하나로
 * 답하고(`PAYMENTS_NOT_EQUAL_FARE`·`PAYMENT_TARGET_MISMATCH`), 그 실패는 **사용자가 금액을 다 채운
 * 뒤에** 돌아온다. 즉 잠금 규칙이 한 칸이라도 틀리면 화면은 멀쩡해 보이는데 **제출만 영구히
 * 실패하거나**, 반대로 **잠겨 있어야 할 버튼이 열려** 사용자가 400 을 스스로 만든다. 둘 다
 * 스크린샷으로 구분되지 않는다. 그래서 규칙을 값으로 못 박는다(`FareSplitDraftStateTest`).
 *
 * ### 서버가 앱에 맡긴 일이 여기 있다
 * Swagger 원문: 「1/N 로 미리 채우는 것은 앱이 한다」. 즉 **나누는 규칙의 소유자가 앱**이고, 서버는
 * 검사만 한다. 그 규칙 자체는 [FareSplitPolicy.splitEvenly] 한 곳에 있으므로 이 파일은 **언제
 * 그것을 다시 부르는가**만 정한다 — 나눗셈을 여기서 다시 쓰면 1원 처리가 두 곳으로 갈린다.
 */

/** 택시비 입력의 자릿수 상한 — 9,999,999원. */
internal const val MAX_FARE_DIGITS = 7

/**
 * 한 사람 부담 금액 입력의 자릿수 상한.
 *
 * 상한이 **없으면 `Int` 가 넘친다.** 10자리를 받으면 `toIntOrNull()` 이 null 이 되고(그건 0 으로
 * 떨어진다), 9자리 넷을 더하면 합이 음수로 감긴다 — 그러면 「합계가 택시비와 같다」가 **거짓으로
 * 참이 되는** 조합이 생긴다([FareSplitPolicy.problemOf] 는 합을 `Long` 으로 세지만 **각 항목은
 * 이미 `Int`** 다). 택시비 상한과 같은 자릿수로 맞춰 둔다.
 */
internal const val MAX_AMOUNT_DIGITS = 7

/**
 * 계좌번호 입력의 길이 상한. 서버 상한은 **숫자** 16자리인데([FareSplitPolicy.ACCOUNT_MAX_DIGITS])
 * 입력에는 하이픈·공백이 섞일 수 있어 그만큼 여유를 둔다(`3333-01-2345678` 식 표기).
 */
internal const val MAX_ACCOUNT_INPUT_LENGTH = 24

/**
 * 시작인가 수정인가 — **문구와 1/N 자동 채우기 규칙이 갈린다**([FareSplitDraftState.autoSplits]).
 *
 * 이 파일의 다른 것들과 달리 `public` 이다. 이유는 가시성 규칙 하나뿐이다: 네비게이션이 라우트
 * 인자에서 이 값을 만들어 공개 Route([FareSplitEditRoute])에 넘기므로, `internal` 로 두면
 * 「public 함수가 internal 타입을 노출한다」로 컴파일이 깨진다. 모듈 밖(`app`)에서 쓰이지는 않는다.
 */
enum class FareSplitEditMode { START, EDIT }

/**
 * 부담 금액을 적을 사람 한 명.
 *
 * @property publicId 서버로 가는 식별자. **빈 값은 여기 올라오지 않는다** — 빌더가 미리 막는다
 *   ([startDraftGate]·[editDraftGate]). 빈 값을 섞으면 [amountInputs] 의 키가 겹쳐 두 사람이 한 칸을
 *   나눠 쓰고, 그 요청은 400 `PAYMENT_TARGET_MISMATCH` 다.
 * @property isPayer 받는 사람(= 방장)인가. 1/N 의 **나머지 1원을 흡수하는 사람**이고
 *   ([FareSplitPolicy.splitEvenly]) 화면에서 「받는 분」 배지가 붙는다.
 */
data class FareSplitRider(
    val publicId: String,
    val nickname: String,
    val isPayer: Boolean,
    val isMe: Boolean,
) {
    /** 표시 이름. 비어 있으면 자리값을 쓴다(`MemberDisplay.displayNickname` 과 같은 규칙). */
    val displayName: String get() = nickname.ifBlank { WithdrawnMemberNames.NO_SUMMARY }
}

/**
 * 입력 상태 하나. **원시 문자열을 들고 있고 숫자로 바꾸는 일은 묻는 자리에서 한다** —
 * `DigitGroupVisualTransformation` 의 교훈과 같은 이유다: 표시용으로 가공한 값을 상태로 들고 있으면
 * 입력 한 글자가 길이 2를 움직여 커서가 어긋난다.
 *
 * @property amountInputs publicId → 숫자만. **없는 키는 「아직 안 적었다」**이고 `"0"` 과 다르다
 *   (앞은 버튼만 잠그고, 뒤는 「1원 이상이어야 해요」를 말한다).
 * @property bankDisplayName 서버가 알려 준 은행 이름. **모르는 은행 코드의 정산을 고칠 때** 쓰인다 —
 *   [Bank] 에 없는 코드면 [Bank.ofCodeOrNull] 이 null 이라 이름을 만들 수 없는데, 그 코드를 그대로
 *   되돌려 보내야 금액만 고칠 수 있다([FareSplitDraft.bankCode] KDoc).
 * @property autoSplits 택시비가 바뀔 때 금액을 **1/N 로 다시 채울 것인가.**
 *
 * [FareSplitEditMode] 와 같은 이유로 `public` 이다 — 스테이트리스 화면([FareSplitEditScreen])의
 * 파라미터 타입이라 화면보다 좁은 가시성을 가질 수 없다. 만들고 고치는 길은 전부 `internal` 이다
 * (빌더 둘과 `with*` 확장) — 모듈 밖에서 이 값을 조립할 수는 없다.
 */
data class FareSplitDraftState(
    val mode: FareSplitEditMode,
    val riders: List<FareSplitRider>,
    val fareInput: String = "",
    val bankCode: String? = null,
    val bankDisplayName: String? = null,
    val accountInput: String = "",
    val amountInputs: Map<String, String> = emptyMap(),
    val autoSplits: Boolean = true,
) {
    /** 받는 사람의 publicId. 빌더가 반드시 한 명을 세워 주므로 비어 있지 않다. */
    val payerPublicId: String get() = riders.first { it.isPayer }.publicId

    /** 택시비(원). 비었거나 넘치면 0 이다 — 0 은 [FareSplitPolicy.MIN_TOTAL_FARE] 미달이라 버튼이 잠긴다. */
    val totalFare: Int get() = fareInput.toIntOrNull() ?: 0

    /** 적어 둔 부담 금액의 합(원). **아직 안 적은 칸은 0** 으로 센다. */
    val amountSum: Int get() = riders.sumOf { amountInputs[it.publicId]?.toIntOrNull() ?: 0 }

    /**
     * `택시비 − 합계`. **양수면 모자라고 음수면 넘친다.**
     *
     * 절댓값만 들고 다니지 않는 이유: 사용자가 할 일이 정반대다(더 적거나 덜 적는다).
     */
    val difference: Int get() = totalFare - amountSum

    /** 선택한 은행의 표시 이름. 아직 안 골랐으면 null. */
    val bankLabel: String?
        get() = bankCode?.let { code ->
            bankDisplayName?.takeIf { it.isNotBlank() } ?: Bank.ofCodeOrNull(code)?.displayName ?: code
        }
}

// ─────────────────────────── 빌더 ───────────────────────────

/**
 * 시작·수정 화면을 **열 수 있는가**의 답.
 *
 * 빈 폼을 띄우고 제출할 때 막는 방식을 쓰지 않는다 — 방장이 금액을 다 채운 뒤에 「이 방은 정산할 수
 * 없어요」를 받는 것이 가장 나쁘다. 그래서 열기 전에 묻고, 막혔으면 **왜 막혔는지와 할 수 있는
 * 일**을 그 자리에 적는다.
 */
internal sealed interface FareSplitDraftGate {
    data class Ready(val state: FareSplitDraftState) : FareSplitDraftGate

    /**
     * 열 수 없다. [recoverable] 이 false 면 **이 방은 영구히 정산할 수 없다** — 화면이
     * 「다시 시도」를 권하지 않아야 하는 경우다(도착은 끝난 방에서 되돌릴 수 없다).
     */
    data class Blocked(val message: String, val recoverable: Boolean) : FareSplitDraftGate
}

/**
 * 끝난 방 + 방장 + 탑승자 2명 이상일 때만 시작 폼을 연다 — **서버 `create` 의 세 조건 그대로**다.
 *
 * ### 세 조건을 미리 보는 이유와 **보지 못하는 것** ★
 * 서버가 최종 판정이다. 앱이 미리 보는 이유는 **열 수 없는 화면을 열지 않기** 위한 것이고,
 * 세 실패의 처방이 서로 다르기 때문이다([fareSplitFailureMessage] 의 표). 다만 앱이 모르는
 * 조건이 하나 있다 — **이미 정산이 있는가**(409 `FARE_SPLIT_ALREADY_EXISTS`). 그건 GET 으로만
 * 알 수 있고, 그 경로는 상세 화면이 먼저 밟는다([FareSplitDetailRoute]).
 *
 * ### 금액을 미리 채우지 않는다
 * [Ride.estimatedFare] 가 있지만 그건 **앱의 예상 요금**이고, 이 칸이 묻는 것은 **실제로 낸
 * 택시비**다. 예상값을 넣어 두면 그대로 제출될 수 있고, 그러면 사람마다 부담 금액이 실제와 다른
 * 장부가 만들어진다 — 앱이 지어낸 숫자로 돈이 움직이는 것이 이 화면에서 가장 나쁜 실패다.
 * 예상 요금은 **힌트 문구로만** 보여 준다([estimatedFareHint]).
 */
internal fun startDraftGate(ride: Ride): FareSplitDraftGate {
    if (ride.status != RideStatus.COMPLETED) {
        return FareSplitDraftGate.Blocked(
            message = "아직 끝나지 않은 방이에요. 「출발」을 눌러 동승을 끝낸 뒤 정산할 수 있어요",
            recoverable = true,
        )
    }
    if (!ride.isHostedByMe) {
        return FareSplitDraftGate.Blocked(message = "방장만 정산을 시작할 수 있어요", recoverable = false)
    }

    val riders = ride.fareSplitRiders
    val me = riders.firstOrNull { it.isMe }
        ?: return FareSplitDraftGate.Blocked(
            message = "출발지 도착을 누르지 않아 이 방의 탑승자가 아니에요. 이 방은 정산할 수 없어요",
            recoverable = false,
        )

    if (riders.size < FareSplitPolicy.MIN_RIDERS) {
        return FareSplitDraftGate.Blocked(
            message = "함께 탄 사람이 혼자예요. 나눌 상대가 없어 이 방은 정산할 수 없어요",
            recoverable = false,
        )
    }
    // 서버 riderIds 에는 들어 있는데 앱이 지목할 수 없는 멤버다 — 빼고 보내도 400,
    // 넣고 보내도 400 이라(둘 다 PAYMENT_TARGET_MISMATCH) 열어 봐야 제출할 수 없는 폼이 된다.
    if (riders.any { it.id.isEmpty() }) {
        return FareSplitDraftGate.Blocked(
            message = "서버가 설명하지 못한 탑승자가 있어 정산을 시작할 수 없어요. 잠시 뒤 다시 시도해 주세요",
            recoverable = true,
        )
    }

    return FareSplitDraftGate.Ready(
        FareSplitDraftState(
            mode = FareSplitEditMode.START,
            riders = riders.map { member ->
                FareSplitRider(
                    publicId = member.id,
                    nickname = member.nickname,
                    isPayer = member.id == me.id,
                    isMe = member.isMe,
                )
            },
            // 택시비를 적는 순간 1/N 로 채워진다 — 그게 Swagger 가 앱에 맡긴 「미리 채우기」다.
            autoSplits = true,
        ),
    )
}

/**
 * 기존 정산을 **고치는** 폼. 방 상세가 필요 없다 — 탑승자 전원이 이미 상세 안에 있다
 * (받는 사람 + `transfers`).
 *
 * ### 1/N 자동 채우기를 **켜지 않는다** ★
 * 여기 들어 있는 금액은 방장이 **손으로 적은 값**일 수 있다(서버는 1/N 을 강제하지 않는다).
 * 자동 채우기를 켜 두면 택시비 한 글자를 고치는 순간 그 조정이 **통째로 사라진다** — 사용자는
 * 자기가 지운 줄 모르고 저장하고, 그러면 다른 사람이 보는 금액이 바뀐다.
 * 「똑같이 나누기」 버튼은 그대로 있으므로 원하면 **명시적으로** 다시 나눌 수 있다([splitEvenly]).
 *
 * ### 계좌번호는 되살릴 수 없다
 * 서버는 상세에 계좌번호를 **OPEN 일 때만** 싣고, 그 값을 프리필한다. 닫힌 정산은 애초에 고칠 수
 * 없으므로([FareSplitDetail.canEdit]) 이 빌더가 열리지 않는다.
 */
internal fun editDraftGate(detail: FareSplitDetail): FareSplitDraftGate {
    editBlockReason(detail)?.let { reason ->
        return FareSplitDraftGate.Blocked(message = reason, recoverable = false)
    }
    if (!detail.isPayer) {
        return FareSplitDraftGate.Blocked(message = "받는 분만 정산을 고칠 수 있어요", recoverable = false)
    }
    if (!detail.payer.isIdentified || detail.transfers.any { !it.isConfirmable }) {
        return FareSplitDraftGate.Blocked(
            message = "서버가 설명하지 못한 탑승자가 있어 금액을 고칠 수 없어요",
            recoverable = true,
        )
    }

    val riders = buildList {
        add(
            FareSplitRider(
                publicId = detail.payer.publicId,
                nickname = detail.payer.nickname,
                isPayer = true,
                isMe = true,
            ),
        )
        detail.transfers.forEach { transfer ->
            add(
                FareSplitRider(
                    publicId = transfer.senderPublicId,
                    nickname = transfer.nickname,
                    isPayer = false,
                    isMe = false,
                ),
            )
        }
    }

    return FareSplitDraftGate.Ready(
        FareSplitDraftState(
            mode = FareSplitEditMode.EDIT,
            riders = riders,
            fareInput = detail.totalFare.toString(),
            // 서버가 준 코드를 **그대로** 되돌려 보낸다 — 앱이 모르는 은행이어도 고칠 수 있게.
            bankCode = detail.bank.code,
            bankDisplayName = detail.bank.displayName,
            accountInput = detail.accountNumber.orEmpty(),
            amountInputs = buildMap {
                put(detail.payer.publicId, detail.myAmount.toString())
                detail.transfers.forEach { put(it.senderPublicId, it.amount.toString()) }
            },
            autoSplits = false,
        ),
    )
}

/** 「앱이 예상했던 요금」 힌트. 없으면 null — 모르는 숫자를 지어내지 않는다. */
internal fun estimatedFareHint(ride: Ride): String? =
    ride.estimatedFare?.takeIf { it > 0 }?.let { "앱이 예상한 요금은 ${fareWonLabel(it)}이었어요" }

// ─────────────────────────── 입력 반영 ───────────────────────────

/** 숫자만 남기고 자릿수를 자른다 — 상한의 근거는 [MAX_FARE_DIGITS]·[MAX_AMOUNT_DIGITS] 에 있다. */
private fun digitsOnly(raw: String, maxLength: Int): String =
    raw.filter { it in '0'..'9' }.trimStart('0').take(maxLength)

/**
 * 택시비를 고친다. [FareSplitDraftState.autoSplits] 가 켜져 있으면 **금액을 1/N 로 다시 채운다.**
 *
 * | 상황 | 택시비를 고치면 |
 * |---|---|
 * | 시작 폼, 아직 금액을 손대지 않았다 | 전원 금액이 **다시 1/N** 로 채워진다 |
 * | 금액을 한 칸이라도 손댔다 | 금액은 **그대로**. 합계가 어긋난 것을 화면이 알려 준다 |
 * | 수정 폼 | 금액은 **그대로**([editDraftGate] — 서버에 있던 조정을 지우지 않는다) |
 *
 * 나눌 수 없는 택시비(인원보다 적은 금액)에서는 다시 채우지 **않는다** — [FareSplitPolicy.splitEvenly]
 * 가 던지는 자리이고, 그건 화면 버그를 잡는 장치이지 사용자 입력을 거르는 수단이 아니다.
 */
internal fun FareSplitDraftState.withFare(raw: String): FareSplitDraftState {
    val next = copy(fareInput = digitsOnly(raw, MAX_FARE_DIGITS))

    return if (next.autoSplits) next.splitEvenlyOrSame() else next
}

/**
 * 한 사람의 금액을 고친다 — **이 순간부터 1/N 자동 채우기가 멈춘다.**
 *
 * 멈추지 않으면 택시비를 한 글자 고칠 때마다 사용자가 방금 조정한 금액이 사라진다.
 * 「똑같이 나누기」를 다시 누르면 자동 채우기도 함께 되살아난다([splitEvenly]).
 */
internal fun FareSplitDraftState.withAmount(publicId: String, raw: String): FareSplitDraftState {
    val digits = digitsOnly(raw, MAX_AMOUNT_DIGITS)

    return copy(
        amountInputs = amountInputs.toMutableMap().apply {
            if (digits.isEmpty()) remove(publicId) else put(publicId, digits)
        },
        autoSplits = false,
    )
}

/**
 * 은행을 고른다. 목록에서 고른 경우이므로 **표시 이름도 함께** 바꾼다 — 안 바꾸면 수정 폼에서
 * 서버가 준 옛 이름이 새 코드 옆에 남는다.
 */
internal fun FareSplitDraftState.withBank(bank: Bank): FareSplitDraftState =
    copy(bankCode = bank.code, bankDisplayName = bank.displayName)

/**
 * 계좌번호를 고친다. **하이픈·공백을 지우지 않는다** — 은행 앱이 복사해 주는 모양(`3333-01-2345678`)을
 * 그대로 붙여넣게 하고, 떼어 내는 일은 매퍼가 보내기 직전에 한다
 * ([FareSplitPolicy.normalizeAccountNumber]).
 *
 * 숫자·하이픈·공백이 **아닌** 문자는 받지 않는다. 지우는 것과 다르다: 조용히 지우면
 * 「3333.01.2345678」이 통과해 **사용자가 확인하지 않은 계좌번호**가 만들어지고, 송금은 앱이 아니라
 * 사용자가 하므로 그 틀림은 되돌릴 수 없다(그 근거는 `normalizeAccountNumber` KDoc 에 있다).
 * 그래서 받지 않고, 틀린 값이 남아 있으면 버튼을 잠근 채 이유를 말한다([accountErrorText]).
 */
internal fun FareSplitDraftState.withAccount(raw: String): FareSplitDraftState =
    copy(
        accountInput = raw
            .filter { it in '0'..'9' || it == '-' || it == ' ' }
            .take(MAX_ACCOUNT_INPUT_LENGTH),
    )

/**
 * 「똑같이 나누기」 — [FareSplitPolicy.splitEvenly] 를 그대로 부르고 자동 채우기를 **되살린다.**
 *
 * 나머지 1원은 **받는 사람이 흡수한다**(근거는 그 함수의 표에 있다) — 보내는 금액이 전원 같아져
 * 「왜 나만 1원 더 내나」가 생기지 않는다. 그 사실은 화면이 한 줄로 알려 준다
 * ([evenSplitRemainderNotice]).
 */
internal fun FareSplitDraftState.splitEvenly(): FareSplitDraftState =
    splitEvenlyOrSame().copy(autoSplits = true)

/** 나눌 수 없으면 **그대로 돌려준다** — 던지지 않는다(사용자가 「2」를 넣을 수 있다). */
private fun FareSplitDraftState.splitEvenlyOrSame(): FareSplitDraftState {
    if (!FareSplitPolicy.canSplitEvenly(totalFare, riders.size)) return this

    val shares = FareSplitPolicy.splitEvenly(
        totalFare = totalFare,
        payerPublicId = payerPublicId,
        riderPublicIds = riders.map { it.publicId },
    )

    return copy(amountInputs = shares.associate { it.riderPublicId to it.amount.toString() })
}

/** 「똑같이 나누기」를 **누를 수 있는가** — 택시비가 인원보다 적으면 누구를 0원으로 만들 수밖에 없다. */
internal fun FareSplitDraftState.canSplitEvenly(): Boolean =
    FareSplitPolicy.canSplitEvenly(totalFare, riders.size)

/**
 * 1/N 의 나머지를 누가 내는지 한 줄. 나누어떨어지면 null 이다 — 할 말이 없다.
 *
 * 이 줄이 있어야 하는 이유: 1/N 을 눌렀는데 받는 사람의 금액만 1~2원 다른 것이 **버그로 보인다.**
 * 「1인당 N원」이라고 단정하지 않는 이 화면에서, 다른 이유로 다른 금액이 섞이는 것을 미리 설명한다.
 */
internal fun evenSplitRemainderNotice(state: FareSplitDraftState): String? {
    if (!state.canSplitEvenly()) return null
    val remainder = state.totalFare % state.riders.size
    if (remainder == 0) return null

    return "나누어떨어지지 않는 ${remainder}원은 받는 분이 부담해요"
}

// ─────────────────────────── 보낼 값 ───────────────────────────

/** 지금 적힌 값으로 만든 부담 금액 목록. **아직 안 적은 칸은 0** 이다(그 상태로는 버튼이 잠긴다). */
internal fun FareSplitDraftState.sharesOf(): List<FareShare> = riders.map { rider ->
    FareShare(riderPublicId = rider.publicId, amount = amountInputs[rider.publicId]?.toIntOrNull() ?: 0)
}

/**
 * 서버로 보낼 값. **[submitBlockOf] 가 null 일 때만 부른다.**
 *
 * 계좌번호를 여기서 정규화하지 않는다 — 매퍼가 보내기 직전에 한다
 * ([FareSplitDraft.accountNumber] KDoc). 두 곳에서 하면 한쪽이 바뀔 때 조용히 갈린다.
 */
internal fun FareSplitDraftState.toDraft(): FareSplitDraft = FareSplitDraft(
    totalFare = totalFare,
    bankCode = requireNotNull(bankCode) { "은행을 고르지 않은 상태로 보낼 수 없다 — submitBlockOf 가 먼저 막는다" },
    accountNumber = accountInput,
    shares = sharesOf(),
)

// ─────────────────────────── 제출 잠금 ───────────────────────────

/**
 * CTA 를 잠근 **하나의** 이유. 여럿을 한꺼번에 띄우지 않는다 — 사용자가 **다음에 할 일 하나**만
 * 알면 되고, 다섯 줄짜리 오류 목록은 읽히지 않는다.
 *
 * 값을 뭉치지 않는 이유는 [FareSplitDraftProblem] 과 같다: 할 일이 서로 다르다. 합이 안 맞는 것은
 * 금액 칸을 고치면 되고, 대상이 어긋난 것은 금액을 아무리 고쳐도 풀리지 않는다.
 */
internal enum class FareSplitSubmitBlock {
    /** 택시비를 아직 적지 않았다. **문구 없음** — 적기 전에 꾸짖지 않는다. */
    FARE_EMPTY,
    FARE_BELOW_MINIMUM,

    /** 은행을 아직 고르지 않았다. 문구 없음. */
    BANK_NOT_SELECTED,

    /** 계좌번호를 아직 적지 않았다. 문구 없음. */
    ACCOUNT_EMPTY,
    ACCOUNT_INVALID,

    /** 아직 비어 있는 금액 칸이 있다. 문구 없음. */
    AMOUNT_EMPTY,
    SHARE_BELOW_MINIMUM,
    SUM_NOT_EQUAL_FARE,
    NOT_ENOUGH_RIDERS,
    RIDER_NOT_IDENTIFIED,
    TARGET_MISMATCH,
}

/**
 * **보낼 수 있는가** — null 이면 보낼 수 있다.
 *
 * 폼의 위에서 아래 순서로 본다(택시비 → 은행 → 계좌 → 금액). 그 순서가 사용자가 칸을 채우는
 * 순서이고, 「다음에 할 일」을 가리키려면 **아직 안 지난 칸**을 먼저 말해야 한다.
 *
 * 금액 쪽 판정은 [FareSplitPolicy.problemOf] 에 그대로 넘긴다 — 서버가 400 으로 거절할 이유를
 * 아는 곳은 거기 하나다. 여기서 다시 세우면 두 판정이 갈려 **버튼은 열리는데 서버가 막는** 조합이
 * 생긴다.
 */
internal fun submitBlockOf(state: FareSplitDraftState): FareSplitSubmitBlock? {
    if (state.fareInput.isEmpty()) return FareSplitSubmitBlock.FARE_EMPTY
    if (state.totalFare < FareSplitPolicy.MIN_TOTAL_FARE) return FareSplitSubmitBlock.FARE_BELOW_MINIMUM
    if (state.bankCode.isNullOrBlank()) return FareSplitSubmitBlock.BANK_NOT_SELECTED
    if (state.accountInput.isBlank()) return FareSplitSubmitBlock.ACCOUNT_EMPTY
    if (!FareSplitPolicy.isAccountNumberValid(state.accountInput)) return FareSplitSubmitBlock.ACCOUNT_INVALID
    if (state.riders.any { state.amountInputs[it.publicId].isNullOrEmpty() }) return FareSplitSubmitBlock.AMOUNT_EMPTY

    return when (
        FareSplitPolicy.problemOf(
            totalFare = state.totalFare,
            riderPublicIds = state.riders.map { it.publicId },
            shares = state.sharesOf(),
        )
    ) {
        null -> null
        FareSplitDraftProblem.FARE_BELOW_MINIMUM -> FareSplitSubmitBlock.FARE_BELOW_MINIMUM
        FareSplitDraftProblem.NOT_ENOUGH_RIDERS -> FareSplitSubmitBlock.NOT_ENOUGH_RIDERS
        // 빌더가 먼저 막는 경우다([startDraftGate]). 그래도 남겨 두는 것은 방어다 —
        // 빌더를 지나쳐 들어오는 길이 생기면 버튼이 **열린 채** 400 으로만 끝난다.
        FareSplitDraftProblem.RIDER_NOT_IDENTIFIED -> FareSplitSubmitBlock.RIDER_NOT_IDENTIFIED
        FareSplitDraftProblem.TARGET_MISMATCH -> FareSplitSubmitBlock.TARGET_MISMATCH
        FareSplitDraftProblem.SHARE_BELOW_MINIMUM -> FareSplitSubmitBlock.SHARE_BELOW_MINIMUM
        FareSplitDraftProblem.SUM_NOT_EQUAL_FARE -> FareSplitSubmitBlock.SUM_NOT_EQUAL_FARE
    }
}

/**
 * 잠금 사유를 **화면 아래 경고로 띄울 때**의 문구. null 이면 아무것도 띄우지 않는다.
 *
 * ### 「아직 안 적었다」에는 문구가 없다 ★
 * 화면을 열자마자 「택시비를 적어 주세요」·「은행을 골라 주세요」가 빨간 띠로 떠 있으면, 사용자는
 * 아무 잘못도 하지 않았는데 오류 화면을 본다. 빈 칸은 **버튼이 잠겨 있는 것으로 충분히** 말한다.
 * 문구가 필요한 것은 **적었는데 틀린** 경우다.
 *
 * [FareSplitSubmitBlock.SUM_NOT_EQUAL_FARE] 가 null 인 것도 비슷하지만 이유가 다르다 — 그 차이는
 * 금액 칸 바로 아래 **합계 줄**이 실시간으로 말하고 있어([sumLabel]·[differenceNotice]) 같은 말을
 * 두 군데서 하면 어느 쪽을 고쳐야 하는지 흐려진다.
 */
internal fun submitBlockMessage(block: FareSplitSubmitBlock?): String? = when (block) {
    null,
    FareSplitSubmitBlock.FARE_EMPTY,
    FareSplitSubmitBlock.BANK_NOT_SELECTED,
    FareSplitSubmitBlock.ACCOUNT_EMPTY,
    FareSplitSubmitBlock.AMOUNT_EMPTY,
    FareSplitSubmitBlock.SUM_NOT_EQUAL_FARE,
    -> null

    FareSplitSubmitBlock.FARE_BELOW_MINIMUM -> "택시비는 1원 이상이어야 해요"
    FareSplitSubmitBlock.ACCOUNT_INVALID -> "계좌번호는 숫자 10~16자리여야 해요"
    FareSplitSubmitBlock.SHARE_BELOW_MINIMUM -> "한 사람당 1원 이상이어야 해요"
    FareSplitSubmitBlock.NOT_ENOUGH_RIDERS -> "함께 탄 사람이 혼자예요. 나눌 상대가 없어 정산할 수 없어요"
    FareSplitSubmitBlock.RIDER_NOT_IDENTIFIED -> "서버가 설명하지 못한 탑승자가 있어 보낼 수 없어요"
    FareSplitSubmitBlock.TARGET_MISMATCH -> "정산 대상이 어긋났어요. 탑승 정보를 다시 불러와 주세요"
}

/**
 * 계좌번호 칸 **아래**에 붙는 오류. 아직 적지 않았으면 null 이다(빈 칸을 틀렸다고 말하지 않는다).
 *
 * 서버가 400 `INVALID_ACCOUNT_NUMBER` 로 답하는 것과 **같은 판정**이라
 * ([FareSplitPolicy.isAccountNumberValid] 가 서버 `AccountNumber` 와 같은 규칙이다) 이 문구가
 * 떠 있는 동안 제출은 반드시 거절된다 — 즉 이 칸은 「서버가 받아 줄 것인가」의 정확한 예측이다.
 */
internal fun accountErrorText(state: FareSplitDraftState): String? = when {
    state.accountInput.isBlank() -> null
    FareSplitPolicy.isAccountNumberValid(state.accountInput) -> null
    else -> "숫자 10~16자리로 적어 주세요"
}

// ─────────────────────────── 합계 표시 ───────────────────────────

/** 「합계 18,700원 / 택시비 18,700원」 — 두 수를 **나란히** 보여 준다(한쪽만 보여 주면 비교할 수 없다). */
internal fun sumLabel(state: FareSplitDraftState): String =
    "합계 ${fareWonLabel(state.amountSum)} / 택시비 ${fareWonLabel(state.totalFare)}"

/**
 * 합계가 택시비와 **얼마나 어긋났는가**를 실시간으로 말하는 줄. 톤까지 함께 정한다.
 *
 * ### 이 줄이 서버 400 을 대신한다 ★
 * 서버는 합이 다르면 400 `PAYMENTS_NOT_EQUAL_FARE` **하나로만** 답한다 — 얼마나 어긋났는지,
 * 어느 칸을 고쳐야 하는지 알려 주지 않고, 그 답은 **입력을 다 끝낸 뒤에** 온다. 그래서 차액을
 * 금액으로, 입력하는 동안 말한다. 「모자라다」와 「많다」를 가르는 이유는 할 일이 정반대라서다.
 *
 * 택시비를 아직 적지 않았으면 null 이다 — 0원과의 차이를 말할 자리가 아니다.
 */
internal fun differenceNotice(state: FareSplitDraftState): FareSplitBadge? {
    if (state.fareInput.isEmpty()) return null
    val diff = state.difference

    return when {
        diff == 0 -> FareSplitBadge(FareSplitTone.DONE, "합계가 택시비와 같아요")
        diff > 0 -> FareSplitBadge(FareSplitTone.WARN, "택시비보다 ${fareWonLabel(diff)} 모자라요")
        else -> FareSplitBadge(FareSplitTone.WARN, "택시비보다 ${fareWonLabel(-diff)} 많아요")
    }
}

// ─────────────────────────── 문구 ───────────────────────────

/** 화면 제목. 시작과 수정이 **다른 일**이라 글자도 다르다. */
internal fun editTitle(mode: FareSplitEditMode): String = when (mode) {
    FareSplitEditMode.START -> "택시비 정산"
    FareSplitEditMode.EDIT -> "정산 고치기"
}

/** CTA 글자. 「저장」이 아니라 「시작」인 쪽은 **보내는 사람 전원에게 알림이 간다**(서버 Swagger). */
internal fun editCtaLabel(mode: FareSplitEditMode): String = when (mode) {
    FareSplitEditMode.START -> "정산 시작하고 알리기"
    FareSplitEditMode.EDIT -> "수정 저장하기"
}

/**
 * 시작 CTA 를 누르기 전에 한 번 말해 주는 사실 — **되돌릴 수 없는 쪽**이다.
 *
 * 「정산 취소는 없다」(Swagger 원문)와 「보내는 사람 전원에게 알림이 간다」는 둘 다 누른 뒤에 알면
 * 늦다. 수정 모드에서는 띄우지 않는다(이미 있는 정산을 고치는 것이고, 알림은 다시 가지 않는다).
 */
internal const val START_IRREVERSIBLE_NOTICE =
    "시작하면 함께 탄 분들에게 송금 요청이 가요. 정산을 취소하는 길은 없고, 금액은 아무도 보내기 전까지만 고칠 수 있어요"
