package com.moyeota.presentation.feature.faresplit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SecondaryButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.domain.model.Bank

/**
 * 40 · 택시비 정산 시작·수정 [신규] — **받는 사람(방장) 전용**
 *
 * 세 칸과 한 목록으로 끝난다: 택시비 · 은행 · 계좌번호 · 사람마다 부담 금액.
 * 시작(POST)과 수정(PATCH)이 **같은 모양**을 쓰므로([com.moyeota.domain.model.FareSplitDraft])
 * 화면도 하나다 — 글자만 [FareSplitEditMode] 로 갈린다.
 *
 * ### 이 화면이 조심하는 것 넷
 * 1. **1/N 은 앱의 책임이다**(Swagger 원문: 「1/N 로 미리 채우는 것은 앱이 한다」). 택시비를 적는
 *    순간 채워지고, 사용자가 한 칸이라도 손대면 **덮지 않는다**([FareSplitDraftState.autoSplits]).
 * 2. **합계를 실시간으로 보여 주고 어긋나면 잠근다.** 서버는 400 `PAYMENTS_NOT_EQUAL_FARE` 하나로만
 *    답하고 그 답은 **입력을 다 끝낸 뒤에** 온다([differenceNotice]).
 * 3. **수정은 전원 NOT_SENT 일 때만**. 못 고치는 상태에서는 폼을 열지 않고 왜 못 고치는지 적는다
 *    ([editDraftGate]) — 「정산 취소는 없다」(서버 원문)까지 함께 말한다.
 * 4. **계좌번호를 로그에 흘리지 않는다.** 이 파일에 로깅이 한 줄도 없다.
 *
 * ### 409 `FARE_SPLIT_ALREADY_STARTED` 가 와도 **화면을 닫지 않는다** ★
 * 그 실패는 사용자가 금액을 다 채운 뒤에 온다(그사이 누군가 「보냈어요」를 눌렀다). 닫으면 입력이
 * 통째로 사라진다. 그래서 [readOnlyReason] 으로 **읽기 전용**으로 바꾸고 적은 값은 그대로 남긴다 —
 * 사용자가 숫자를 보며 채팅으로 이야기할 수 있다.
 */
@Composable
fun FareSplitEditScreen(
    /** 입력 상태. **기본값이 없다** — 예시 값이 프로덕션에 새는 길을 막는다. */
    state: FareSplitDraftState,
    /** 「앱이 예상한 요금은 …」. **칸에 넣지 않는다**([estimatedFareHint] KDoc). */
    fareHint: String? = null,
    submitting: Boolean = false,
    /** 서버 실패 사유([fareSplitFailureMessage]). CTA 위 띠. */
    errorMessage: String? = null,
    /**
     * null 이 아니면 **읽기 전용**이다. 409 `FARE_SPLIT_ALREADY_STARTED` 뒤에 채워진다 —
     * 입력은 그대로 남기고 보내는 길만 닫는다.
     */
    readOnlyReason: String? = null,
    onBack: () -> Unit = {},
    onFareChange: (String) -> Unit = {},
    onBankSelect: (Bank) -> Unit = {},
    onAccountChange: (String) -> Unit = {},
    onAmountChange: (publicId: String, raw: String) -> Unit = { _, _ -> },
    onSplitEvenly: () -> Unit = {},
    onSubmit: () -> Unit = {},
    /**
     * **이미 그 방에 정산이 있을 때**만 null 이 아니다(409 `FARE_SPLIT_ALREADY_EXISTS`).
     *
     * 그 실패를 「오류」로만 보여 주고 끝내면 사용자는 **이미 존재하는 자기 정산을 못 보게** 된다 —
     * 서버 Swagger 가 그 복구 경로를 「GET 으로 본다」라고 적어 둔 실패다. 화면을 저절로 바꾸지
     * 않는 이유는 입력 때문이다: 사용자가 방금 적은 금액·계좌는 **저장되지 않았고**, 말없이 다른
     * 화면으로 옮기면 그 값이 사라진 것도, 저장되지 않은 것도 알 수 없다. 그래서 적은 값을 남긴 채
     * **다음으로 갈 길을 버튼으로** 준다.
     */
    onSeeExisting: (() -> Unit)? = null,
) {
    var pickingBank by remember { mutableStateOf(false) }
    val editable = readOnlyReason == null && !submitting
    val block = submitBlockOf(state)

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            MoyeotaTopBar(title = editTitle(state.mode), onBack = onBack)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Sm),
        ) {
            if (readOnlyReason != null) {
                NoticeBanner(kind = NoticeKind.ERROR, text = readOnlyReason)
            }

            FareSplitCard {
                FareSplitSectionTitle("실제로 낸 택시비")
                MoyeotaTextField(
                    value = state.fareInput,
                    onValueChange = onFareChange,
                    placeholder = "0",
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    helperText = fareHint,
                    trailing = { Text("원", style = MoyeotaType.BodyMd, color = MoyeotaColor.TextMute) },
                )
            }

            FareSplitCard {
                FareSplitSectionTitle("받을 계좌")
                BankSelectRow(
                    label = state.bankLabel,
                    enabled = editable,
                    onClick = { pickingBank = true },
                )
                MoyeotaTextField(
                    value = state.accountInput,
                    onValueChange = onAccountChange,
                    label = "계좌번호",
                    placeholder = "숫자 10~16자리",
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    errorText = accountErrorText(state),
                    // 하이픈을 떼는 일은 보내기 직전에 매퍼가 한다 — 사용자가 은행 앱에서 복사한
                    // 모양을 그대로 붙여넣을 수 있다는 사실을 알려 준다.
                    helperText = "은행 앱에서 복사한 하이픈(-)이 있어도 괜찮아요",
                )
            }

            FareSplitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FareSplitSectionTitle("사람마다 부담 금액", modifier = Modifier.weight(1f))
                    if (editable && state.canSplitEvenly()) {
                        TextButton(onClick = onSplitEvenly) {
                            Text("똑같이 나누기", color = MoyeotaColor.Primary600, style = MoyeotaType.ButtonMd)
                        }
                    }
                }
                evenSplitRemainderNotice(state)?.let { notice -> FareSplitHint(notice) }

                state.riders.forEachIndexed { index, rider ->
                    if (index > 0) HorizontalDivider(color = MoyeotaColor.Hairline)
                    AmountRow(
                        rider = rider,
                        value = state.amountInputs[rider.publicId].orEmpty(),
                        enabled = editable,
                        onValueChange = { raw -> onAmountChange(rider.publicId, raw) },
                    )
                }

                HorizontalDivider(color = MoyeotaColor.Hairline)
                Text(text = sumLabel(state), style = MoyeotaType.BodyMd, color = MoyeotaColor.InkPrimary)
                // 서버 400 을 **입력하는 동안** 대신 말하는 줄이다. 아래 경고 띠와 중복으로 적지 않는다
                // (submitBlockMessage 가 SUM_NOT_EQUAL_FARE 에 null 을 주는 근거).
                differenceNotice(state)?.let { badge -> FareSplitNotice(badge) }
            }

            if (state.mode == FareSplitEditMode.START) {
                FareSplitHint(START_IRREVERSIBLE_NOTICE)
            }
            FareSplitHint(APP_DOES_NOT_MOVE_MONEY)
            Spacer(Modifier.height(MoyeotaSpacing.Xxs))
        }

        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            HorizontalDivider(color = MoyeotaColor.Hairline)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Xs),
            ) {
                // 서버가 보낸 실패가 1순위다 — 앱이 미리 보지 못한 사유이므로 더 새 정보다.
                val notice = errorMessage ?: submitBlockMessage(block)
                if (notice != null) {
                    NoticeBanner(kind = NoticeKind.ERROR, text = notice)
                }
                PrimaryCtaButton(
                    text = editCtaLabel(state.mode),
                    onClick = onSubmit,
                    enabled = editable && block == null,
                    loading = submitting,
                )
                if (onSeeExisting != null) {
                    SecondaryButton(text = "지금 있는 정산 보기", onClick = onSeeExisting)
                }
            }
            NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))
        }
    }

    if (pickingBank) {
        BankPickerDialog(
            selectedCode = state.bankCode,
            onPick = { bank ->
                pickingBank = false
                onBankSelect(bank)
            },
            onDismiss = { pickingBank = false },
        )
    }
}

/**
 * 은행 선택 줄. 고르기 전에는 「은행 선택」이고 고른 뒤에는 그 이름이다.
 *
 * 22개를 칩으로 펼치지 않는 이유: 여덟 줄을 차지해 **아래의 금액 목록이 화면에서 밀려난다** —
 * 합계를 보면서 금액을 고치는 것이 이 화면의 중심 동작이다. 목록은 다이얼로그로 뺀다.
 */
@Composable
private fun BankSelectRow(label: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, MoyeotaColor.Hairline, RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = MoyeotaSpacing.ScreenGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label ?: "은행 선택",
            style = MoyeotaType.BodyMd,
            color = if (label == null) MoyeotaColor.TextAsh else MoyeotaColor.InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = "바꾸기", style = MoyeotaType.ButtonSm, color = MoyeotaColor.Primary600)
    }
}

/**
 * 은행 목록 다이얼로그 — [Bank] 22종을 **서버 enum 순서 그대로** 그린다.
 *
 * 순서를 바꾸지 않는 이유: 서버 `Bank` 가 많이 쓰는 은행부터 늘어놓은 순서이고, 앱이 가나다순으로
 * 다시 정렬하면 **자주 쓰는 은행이 아래로 내려간다.** 검색 칸을 두지 않는 것도 같은 판단이다
 * (22개는 한 번 훑어 찾을 수 있는 길이다).
 *
 * 지금 고른 은행이 **목록에 없을 수 있다** — 서버가 추가한 23번째 은행의 정산을 고치는 경우다.
 * 그때 아무 줄도 선택 표시가 되지 않는 것이 정상이고, 사용자가 아무것도 고르지 않으면
 * [FareSplitDraftState.bankCode] 가 그대로 남아 **모르는 은행의 정산도 금액만 고쳐 저장된다.**
 */
@Composable
private fun BankPickerDialog(selectedCode: String?, onPick: (Bank) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("은행 선택", style = MoyeotaType.HeadingMd) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Bank.entries.forEach { bank ->
                    val selected = bank.code == selectedCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(bank) }
                            .padding(vertical = MoyeotaSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bank.displayName,
                            style = MoyeotaType.BodyMd,
                            color = if (selected) MoyeotaColor.Primary600 else MoyeotaColor.InkPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(text = "선택됨", style = MoyeotaType.ButtonSm, color = MoyeotaColor.Primary600)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = MoyeotaColor.TextMute, style = MoyeotaType.ButtonMd)
            }
        },
    )
}

/**
 * 한 사람의 금액 줄.
 *
 * 「받는 분」 배지를 붙이는 이유: 1/N 의 나머지 1원을 그 사람이 흡수해 **금액이 혼자 다르다**
 * ([evenSplitRemainderNotice] 가 그 이유를 설명한다). 배지가 없으면 버그로 보인다.
 */
@Composable
private fun AmountRow(
    rider: FareSplitRider,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MoyeotaSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rider.displayName,
                style = MoyeotaType.BodyMd,
                color = MoyeotaColor.InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rider.isPayer) {
                Text(text = "받는 분", style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute)
            }
        }
        Spacer(Modifier.size(MoyeotaSpacing.Xs))
        Box(modifier = Modifier.width(140.dp)) {
            MoyeotaTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "0",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailing = { Text("원", style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute) },
            )
        }
    }
}

// ─────────────────────────── 프리뷰 ───────────────────────────

private fun previewState(mode: FareSplitEditMode = FareSplitEditMode.START) = FareSplitDraftState(
    mode = mode,
    riders = listOf(
        FareSplitRider("p-1", "모여타방장", isPayer = true, isMe = true),
        FareSplitRider("s-a", "서면가자", isPayer = false, isMe = false),
        FareSplitRider("s-b", "부산대", isPayer = false, isMe = false),
    ),
)

@Preview(showBackground = true)
@Composable
private fun FareSplitEditEmptyPreview() {
    FareSplitEditScreen(
        state = previewState(),
        fareHint = "앱이 예상한 요금은 10,800원이었어요",
    )
}

/** 택시비를 적은 직후 — 1/N 이 채워지고 나머지 1원이 받는 분에게 간 상태. */
@Preview(showBackground = true)
@Composable
private fun FareSplitEditFilledPreview() {
    FareSplitEditScreen(
        state = previewState()
            .withFare("18700")
            .withBank(Bank.KAKAO)
            .withAccount("3333-01-2345678"),
    )
}

/** 합계가 어긋난 상태 — 차액을 금액으로 말하고 CTA 가 잠긴다. */
@Preview(showBackground = true)
@Composable
private fun FareSplitEditMismatchedPreview() {
    FareSplitEditScreen(
        state = previewState()
            .withFare("18700")
            .withBank(Bank.KAKAO)
            .withAccount("3333012345678")
            .withAmount("s-a", "1000"),
    )
}

/** 409 뒤의 읽기 전용 — **적은 값을 버리지 않는다.** */
@Preview(showBackground = true)
@Composable
private fun FareSplitEditReadOnlyPreview() {
    FareSplitEditScreen(
        state = previewState(FareSplitEditMode.EDIT)
            .withFare("18700")
            .withBank(Bank.SHINHAN)
            .withAccount("110123456789"),
        readOnlyReason = "누군가 「보냈어요」를 눌러 더 이상 고칠 수 없어요",
    )
}
