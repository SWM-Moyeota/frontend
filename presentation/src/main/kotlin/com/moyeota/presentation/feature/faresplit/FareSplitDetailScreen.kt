package com.moyeota.presentation.feature.faresplit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.moyeota.domain.model.FareSplitBank
import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitParticipant
import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.FareSplitTransfer
import com.moyeota.domain.model.TransferStatus

/**
 * 39 · 택시비 정산 상세 [신규]
 *
 * ### 한 라우트인데 **역할마다 다른 화면**이다 ★
 *
 * | | PAYER(받는 사람 = 방장) | SENDER(보내는 사람) |
 * |---|---|---|
 * | 보는 것 | 보낼 사람별 금액·상태·**가린 실명** | 내가 보낼 금액·받을 계좌 |
 * | 누르는 것 | 「받았어요」/「아직 안 들어왔어요」 | **토스로 보내기** + 「보냈어요」 |
 * | 수정 | 가능([FareSplitDetail.canEdit]) | 없다 |
 *
 * 화면을 둘로 쪼개지 않는 이유: 서버가 **같은 엔드포인트**로 두 모양을 주고(`myRole` 로 갈린다)
 * 둘 다 같은 `partyId` 로만 열린다. 쪼개면 들어오기 전에 역할을 알아야 하는데, 그건 GET 을 해 봐야
 * 알 수 있다 — 라우트가 둘이면 **먼저 조회한 뒤 어느 라우트로 갈지 정하는 중간 화면**이 필요해진다.
 *
 * ### 이 화면이 조심하는 것 넷
 * 1. **「받았어요」와 「아직 안 들어왔어요」를 뚜렷하게 가른다.** 둘 다 204 로 성공해 보이는데
 *    한쪽은 되돌릴 수 없다. 그래서 **생김새를 전부 다르게** 둔다 — 모양(알약 vs 글자), 색
 *    (Primary vs Danger), 크기, **줄이 다르다**(같은 Row 에 나란히 두지 않는다). 글자도
 *    「못 받았어요」가 아니라 「아직 안 들어왔어요」다: 「받았어요/못 받았어요」는 두 글자 차이라
 *    같은 자리에 있으면 손이 기억한 위치로 누른다.
 * 2. **본문 없는 204 뒤에는 반드시 재조회한다.** 그 일은 Route 가 한다([FareSplitDetailViewModel]) —
 *    이 화면은 콜백만 부르고 낙관적으로 한 행도 고치지 않는다. 한 행만 고치면 **마지막 확인이
 *    정산 전체를 닫는 것**을 놓쳐, 닫힌 정산에 계좌번호가 남아 보인다.
 * 3. **토스 딥링크가 null 이면 버튼이 없다.** 앱이 조립하지 않는다([showsTossButton]).
 *    열지 못했을 때는 복사 버튼을 가리킨다 — 복사는 선택이 아니라 그 실패의 짝이다.
 * 4. **계좌번호를 로그에 흘리지 않는다.** 이 파일에 로깅이 한 줄도 없다([FareSplitActions] KDoc).
 * 5. **내 송금 상태를 세션 uuid 로 찾지 않는다.** 서버가 [FareSplitDetail.myTransferStatus] 로 직접
 *    주고(Backend PR #328) 「보냈어요」의 권한은 [FareSplitDetail.canMarkSent] 한 곳에 있다 —
 *    그래서 이 화면은 내 publicId 를 **받지 않는다.** [FareSplitDetail.transferOf] 는 받는 사람이
 *    「받았어요」를 누를 대상을 고르는 자리에만 남아 있다.
 *
 * 이동:
 * - 뒤로가기 → 들어온 자리(끝난 방 상세 · 탑승 기록 · 홈 배너)
 * - 「금액·계좌 고치기」 → 40 정산 고치기([onEdit]) — [FareSplitDetail.canEdit] 일 때만 그린다
 * - 「채팅으로 물어보기」 → 그 방의 채팅([onOpenChat]). **null 이면 그리지 않는다** — 정산은 방이
 *   지워진 뒤에도 남으므로 열 채팅방이 없을 수 있고, 눌러도 열 것이 없는 버튼을 두지 않는다
 */
@Composable
fun FareSplitDetailScreen(
    /** 그릴 정산. **기본값이 없다** — 예시 값이 프로덕션에 새는 길을 막는다(21 의 `waitingRideDummy` 교훈). */
    detail: FareSplitDetail,
    actionInProgress: Boolean = false,
    /** 마지막 동작이 실패한 사유([fareSplitFailureMessage]). CTA 위 띠로 보여 준다. */
    actionErrorMessage: String? = null,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onMarkSent: () -> Unit = {},
    /** 「받았어요」 — 확인 다이얼로그 **뒤에만** 불린다. */
    onConfirmReceived: (senderPublicId: String) -> Unit = {},
    /** 「아직 안 들어왔어요」 — 확인 다이얼로그 **뒤에만** 불린다. */
    onMarkNotReceived: (senderPublicId: String) -> Unit = {},
    /** 토스를 연다. **열지 못하면 false** — 그때 화면이 복사 안내로 바뀐다([TOSS_OPEN_FAILED_NOTICE]). */
    onOpenToss: (deeplink: String) -> Boolean = { false },
    /** 클립보드 복사. 성공 여부를 돌려준다([CopyButton]). */
    onCopy: (label: String, value: String) -> Boolean = { _, _ -> false },
    /** 그 방의 채팅으로. 열 방이 없으면 null — 버튼을 그리지 않는다. */
    onOpenChat: (() -> Unit)? = null,
) {
    // 확인 다이얼로그 대상. 세 개를 **따로** 들고 있는 이유: 하나로 합치면 「받았어요」와
    // 「아직 안 들어왔어요」가 같은 상태 변수를 쓰게 되고, 그게 바로 두 동작이 섞이는 길이다.
    var confirming by remember { mutableStateOf<FareSplitTransfer?>(null) }
    var reverting by remember { mutableStateOf<FareSplitTransfer?>(null) }
    var sending by remember { mutableStateOf(false) }
    // 토스를 열지 못했다. 한 번 실패하면 복사 안내를 띄운 채 둔다 — 다시 눌러 봐야 같은 결과다.
    var tossFailed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            MoyeotaTopBar(title = "택시비 정산", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Sm),
        ) {
            StatusCard(detail = detail)

            if (detail.isPayer) {
                PayerBlock(
                    detail = detail,
                    actionInProgress = actionInProgress,
                    onCopy = onCopy,
                    onConfirmClick = { confirming = it },
                    onRevertClick = { reverting = it },
                )
            } else {
                SenderBlock(
                    detail = detail,
                    stage = senderStageOf(detail),
                    tossFailed = tossFailed,
                    onCopy = onCopy,
                    onTossClick = { deeplink -> tossFailed = !onOpenToss(deeplink) },
                )
            }

            // 보낼 계좌가 사라졌거나(탈퇴) 정산이 기간으로 닫힌 자리에서는 **앱 안에 길이 없다.**
            // 그때만 채팅을 가리킨다 — 멀쩡한 정산에 「채팅으로 물어보기」를 두면 할 일이 흐려진다.
            if (onOpenChat != null && showsChatFallback(detail)) {
                SecondaryButton(text = "채팅으로 물어보기", onClick = onOpenChat)
            }

            FareSplitHint(text = APP_DOES_NOT_MOVE_MONEY)
            if (detail.isOpen) FareSplitHint(text = EXPIRY_NOTICE)
            Spacer(Modifier.height(MoyeotaSpacing.Xxs))
        }

        // 하단 고정 영역 — 역할마다 **딱 하나의 채워진 버튼**만 둔다.
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            HorizontalDivider(color = MoyeotaColor.Hairline)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Xs),
            ) {
                if (actionErrorMessage != null) {
                    NoticeBanner(kind = NoticeKind.ERROR, text = actionErrorMessage)
                }

                if (detail.isPayer) {
                    PayerFooter(detail = detail, actionInProgress = actionInProgress, onEdit = onEdit)
                } else {
                    SenderFooter(
                        detail = detail,
                        tossFailed = tossFailed,
                        actionInProgress = actionInProgress,
                        onTossClick = { deeplink -> tossFailed = !onOpenToss(deeplink) },
                        onMarkSentClick = { sending = true },
                    )
                }
            }
            NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))
        }
    }

    // ─── 확인 다이얼로그 셋. 세 개가 **서로 다른 글자와 다른 버튼 색**을 쓴다 ───

    confirming?.let { target ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("받았다고 확인할까요?", style = MoyeotaType.HeadingMd) },
            text = { Text(confirmReceivedDialogMessage(detail, target), style = MoyeotaType.BodySm) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onConfirmReceived(target.senderPublicId)
                }) {
                    Text("받았어요", color = MoyeotaColor.Primary600, style = MoyeotaType.ButtonMd)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text("아니요", color = MoyeotaColor.TextMute, style = MoyeotaType.ButtonMd)
                }
            },
        )
    }

    reverting?.let { target ->
        AlertDialog(
            onDismissRequest = { reverting = null },
            title = { Text("「보냈어요」를 되돌릴까요?", style = MoyeotaType.HeadingMd) },
            text = { Text(markNotReceivedDialogMessage(target), style = MoyeotaType.BodySm) },
            confirmButton = {
                TextButton(onClick = {
                    reverting = null
                    onMarkNotReceived(target.senderPublicId)
                }) {
                    // 되돌리기는 Danger 색이다 — 확인(Primary)과 **색까지** 갈라 둔다.
                    Text("되돌리기", color = MoyeotaColor.Danger600, style = MoyeotaType.ButtonMd)
                }
            },
            dismissButton = {
                TextButton(onClick = { reverting = null }) {
                    Text("그대로 두기", color = MoyeotaColor.TextMute, style = MoyeotaType.ButtonMd)
                }
            },
        )
    }

    if (sending) {
        AlertDialog(
            onDismissRequest = { sending = false },
            title = { Text("보냈다고 알릴까요?", style = MoyeotaType.HeadingMd) },
            text = { Text(markSentDialogMessage(detail), style = MoyeotaType.BodySm) },
            confirmButton = {
                TextButton(onClick = {
                    sending = false
                    onMarkSent()
                }) {
                    Text("보냈어요", color = MoyeotaColor.Primary600, style = MoyeotaType.ButtonMd)
                }
            },
            dismissButton = {
                TextButton(onClick = { sending = false }) {
                    Text("아직이에요", color = MoyeotaColor.TextMute, style = MoyeotaType.ButtonMd)
                }
            },
        )
    }
}

// ─────────────────────────── 공통 ───────────────────────────

/**
 * 상태 요약. 총 택시비와 상태 배지를 함께 둔다 — **상태를 모르면 왜 버튼이 없는지 알 수 없다.**
 *
 * 경로(출발→도착)를 적지 않는다: 정산 상세 응답에 그 값이 **없다**(홈 배너 쪽에만 있다).
 * 방 상세를 따로 불러 채우면 요청이 하나 늘고, 방이 지워진 정산에서는 그마저 실패한다.
 */
@Composable
private fun StatusCard(detail: FareSplitDetail) {
    FareSplitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "총 택시비",
                style = MoyeotaType.BodySm,
                color = MoyeotaColor.TextMute,
                modifier = Modifier.weight(1f),
            )
            FareSplitBadgeChip(fareSplitBadge(detail.status))
        }
        Text(text = fareWonLabel(detail.totalFare), style = MoyeotaType.DisplayMd, color = MoyeotaColor.InkPrimary)
        fareSplitStatusNotice(detail.status)?.let { notice ->
            FareSplitNotice(FareSplitBadge(fareSplitBadge(detail.status).tone, notice))
        }
    }
}

// ─────────────────────────── 받는 사람 ───────────────────────────

@Composable
private fun PayerBlock(
    detail: FareSplitDetail,
    actionInProgress: Boolean,
    onCopy: (String, String) -> Boolean,
    onConfirmClick: (FareSplitTransfer) -> Unit,
    onRevertClick: (FareSplitTransfer) -> Unit,
) {
    val summary = payerSummaryOf(detail)

    FareSplitCard {
        FareSplitSectionTitle("내가 받을 돈")
        FareSplitKeyValueRow("아직 못 받은 돈", fareWonLabel(summary.outstanding), strong = true)
        FareSplitKeyValueRow("받을 돈 전체", fareWonLabel(summary.toReceive))
        // 「내 부담」을 함께 보여 주는 이유: 받을 돈(12,466원)이 총 택시비(18,700원)보다 적은 것이
        // 버그처럼 보인다. 빠진 금액이 내 몫임을 그 자리에서 말한다.
        FareSplitKeyValueRow("내가 부담한 몫", fareWonLabel(summary.myAmount))
        FareSplitHint(confirmedProgressLabel(summary))
    }

    // 내가 적어 둔 계좌 — **고치기 전에 맞는지 확인하는 용도**다. 닫힌 정산에서는 서버가 지워
    // null 로 오므로 블록 자체가 사라진다(「불러오지 못했어요」가 아니다).
    detail.manualTransfer?.let { manual ->
        FareSplitCard {
            FareSplitSectionTitle("받을 계좌")
            CopyableValueRow(
                label = manual.bankDisplayName,
                display = manual.accountNumber,
                copyValue = manual.accountNumber,
                onCopy = onCopy,
            )
            FareSplitHint("동승자에게 보이는 계좌예요. 틀렸다면 아래 「금액·계좌 고치기」로 바꿔 주세요")
        }
    }

    FareSplitCard {
        FareSplitSectionTitle("보낼 분들")
        // 가린 실명이 하나라도 오면 그 칸의 **쓸모**를 한 번만 적는다.
        if (detail.transfers.any { depositorHintOrNull(it) != null }) {
            FareSplitHint(DEPOSITOR_HINT_PURPOSE)
        }
        detail.transfers.forEachIndexed { index, transfer ->
            if (index > 0) HorizontalDivider(color = MoyeotaColor.Hairline)
            TransferRow(
                detail = detail,
                transfer = transfer,
                actionInProgress = actionInProgress,
                onConfirmClick = onConfirmClick,
                onRevertClick = onRevertClick,
            )
        }
    }
}

/**
 * 송금 한 행.
 *
 * ### 두 버튼의 생김새를 전부 갈라 둔다 ★
 *
 * | | 받았어요 | 아직 안 들어왔어요 |
 * |---|---|---|
 * | 모양 | 채워진 알약 버튼(전체 너비) | **글자만**(테두리·배경 없음) |
 * | 색 | Primary | Danger |
 * | 자리 | 행 아래 **첫째 줄** | 그 **아래 줄**, 오른쪽 끝 |
 * | 글자 | 「받았어요」 | 「아직 안 들어왔어요」 |
 *
 * 같은 Row 에 나란히 두지 않는 것이 핵심이다. 나란히 두면 손이 **위치로** 기억하고, 행마다 한쪽만
 * 있을 때(NOT_SENT) 그 위치가 밀려 **되돌리려던 손가락이 확인을 누른다.** 그 오탭은 되돌릴 수 없다.
 */
@Composable
private fun TransferRow(
    detail: FareSplitDetail,
    transfer: FareSplitTransfer,
    actionInProgress: Boolean,
    onConfirmClick: (FareSplitTransfer) -> Unit,
    onRevertClick: (FareSplitTransfer) -> Unit,
) {
    val actions = transferActionsOf(detail, transfer)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MoyeotaSpacing.Xxs),
        verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FareSplitInitialCircle(transfer.displayName)
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.displayName,
                    style = MoyeotaType.HeadingMd,
                    color = MoyeotaColor.InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                depositorHintOrNull(transfer)?.let { hint ->
                    Text(text = hint, style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute)
                }
                Text(text = fareWonLabel(transfer.amount), style = MoyeotaType.BodySm, color = MoyeotaColor.TextBody)
            }
            Spacer(Modifier.size(MoyeotaSpacing.Xs))
            FareSplitBadgeChip(transferStatusLabel(transfer.status))
        }

        actions.lockReason?.let { reason -> FareSplitHint(reason) }

        if (actions.canConfirm) {
            PrimaryCtaButton(
                text = "받았어요",
                onClick = { onConfirmClick(transfer) },
                enabled = !actionInProgress,
            )
        }
        if (actions.canMarkNotReceived) {
            // 글자만. 전체 너비를 차지하지 않고 오른쪽 끝에 둔다 — 위의 알약과 **무게가 다르다**.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { onRevertClick(transfer) }, enabled = !actionInProgress) {
                    Text("아직 안 들어왔어요", color = MoyeotaColor.Danger600, style = MoyeotaType.ButtonMd)
                }
            }
        }
    }
}

/**
 * 받는 사람의 하단. 고칠 수 있으면 버튼, 못 고치면 **왜 못 고치는지**([editBlockReason]).
 *
 * 버튼을 그냥 숨기지 않는 이유: 방장은 금액을 잘못 넣었다는 것을 알고 이 화면에 왔다. 고치는 길이
 * 아무 말 없이 사라져 있으면 **정산을 취소하려 하고**, 그 길은 서버에 없다.
 */
@Composable
private fun PayerFooter(detail: FareSplitDetail, actionInProgress: Boolean, onEdit: () -> Unit) {
    if (detail.canEdit) {
        SecondaryButton(text = "금액·계좌 고치기", onClick = onEdit, enabled = !actionInProgress)
    } else {
        editBlockReason(detail)?.let { reason -> FareSplitHint(reason) }
    }
}

// ─────────────────────────── 보내는 사람 ───────────────────────────

@Composable
private fun SenderBlock(
    detail: FareSplitDetail,
    stage: SenderStage,
    tossFailed: Boolean,
    onCopy: (String, String) -> Boolean,
    onTossClick: (String) -> Unit,
) {
    FareSplitCard {
        FareSplitSectionTitle("내가 보낼 금액")
        Text(
            text = fareWonLabel(detail.myAmount),
            style = MoyeotaType.DisplayLg,
            color = MoyeotaColor.Primary600,
        )
        FareSplitKeyValueRow("받는 분", detail.payer.displayName)
        senderStageNotice(stage)?.let { notice ->
            FareSplitNotice(FareSplitBadge(senderStageTone(stage), notice))
        }
    }

    // 계좌·금액 복사 — **토스가 열리지 않는 기기에서 유일하게 남는 길**이다(서버 주석의 요구사항).
    // 닫힌 정산·탈퇴한 받는 사람에게는 계좌가 null 이라 이 블록이 통째로 사라진다.
    detail.manualTransfer?.let { manual ->
        FareSplitCard {
            FareSplitSectionTitle("보낼 곳")
            CopyableValueRow(
                label = manual.bankDisplayName,
                display = manual.accountNumber,
                copyValue = manual.accountNumber,
                onCopy = onCopy,
            )
            HorizontalDivider(color = MoyeotaColor.Hairline)
            CopyableValueRow(
                label = "금액",
                display = fareWonLabel(manual.amount),
                // 금액은 **숫자만** 복사한다 — 은행 앱 금액 칸에 「6,233원」을 붙이면 거절된다.
                copyValue = manual.amount.toString(),
                onCopy = onCopy,
            )
            if (tossFailed) {
                FareSplitNotice(FareSplitBadge(FareSplitTone.WARN, TOSS_OPEN_FAILED_NOTICE))
            }
        }
    }

    // 토스 버튼을 카드 안에도 둔다 — 계좌 바로 아래가 「보내러 간다」의 자연스러운 자리다.
    // 하단 고정 CTA 와 중복이 아니다: 저쪽은 스크롤과 무관하게 늘 보이는 자리다.
    if (showsTossButton(detail) && !tossFailed) {
        detail.tossDeeplink?.let { deeplink ->
            SecondaryButton(text = "토스로 보내기", onClick = { onTossClick(deeplink) })
        }
    }
}

/**
 * 보내는 사람의 하단 — **채워진 버튼은 늘 하나**다.
 *
 * 토스 딥링크가 있으면 「토스로 보내기」가 그 하나이고(먼저 할 일이 송금이다) 「보냈어요」는
 * 테두리 버튼으로 그 아래 선다. 딥링크가 없거나 **열리지 않았으면**([tossFailed]) 할 일이
 * 「보냈어요」 하나라 그것이 채워진 버튼이 된다. 채워진 버튼이 둘이면 **무엇을 먼저 할지** 알 수 없다.
 */
@Composable
private fun SenderFooter(
    detail: FareSplitDetail,
    /**
     * 토스를 한 번 열지 못했다 — 그러면 **채워진 버튼이 「보냈어요」로 옮겨 간다.**
     *
     * 실패한 버튼을 그 자리에 남겨 두지 않는 이유: 다시 눌러도 같은 결과이고(받아 줄 앱이 없다),
     * 그 사이 위쪽 카드는 이미 「복사해서 은행 앱에서 보내 주세요」로 바뀌어 있다
     * ([TOSS_OPEN_FAILED_NOTICE]). 두 영역이 서로 다른 다음 단계를 가리키면 안 된다.
     */
    tossFailed: Boolean,
    actionInProgress: Boolean,
    onTossClick: (String) -> Unit,
    onMarkSentClick: () -> Unit,
) {
    val deeplink = detail.tossDeeplink?.takeIf { showsTossButton(detail) && !tossFailed }

    if (deeplink != null) {
        PrimaryCtaButton(text = "토스로 보내기", onClick = { onTossClick(deeplink) }, enabled = !actionInProgress)
        if (showsMarkSentButton(detail)) {
            SecondaryButton(text = "보냈어요", onClick = onMarkSentClick, enabled = !actionInProgress)
        }
    } else if (showsMarkSentButton(detail)) {
        PrimaryCtaButton(
            text = "보냈어요",
            onClick = onMarkSentClick,
            enabled = !actionInProgress,
            loading = actionInProgress,
        )
    }
}

// ─────────────────────────── 정산이 아직 없을 때 ───────────────────────────

/**
 * 39 의 **정상 상태 하나** — 이 방에 아직 정산이 없다.
 *
 * ### 「불러오지 못했어요」가 아니다 ★
 * 서버 404 `FARE_SPLIT_NOT_FOUND` 는 「아직 아무도 시작하지 않았다」는 뜻이고, 그건 끝난 방의
 * 정상 상태다(저장소가 그 404 를 null 로 접어 준다). 빨간 오류 화면으로 그리면 방장은 **자기가
 * 시작해야 한다는 것을 모르고**, 동승자는 돈을 못 보낸 것을 앱 탓으로 읽는다.
 *
 * @param canStart 내가 시작할 수 있는가. false 면 「정산 시작하기」를 **그리지 않는다** —
 *   누르면 403 으로만 끝나는 버튼을 두지 않는다.
 * @param blockedReason 왜 시작할 수 없는지. **null 이면 「알 수 없다」**다(방 상세 조회 실패) —
 *   그때는 단정하지 않고 다시 불러오기만 둔다.
 */
@Composable
fun FareSplitAbsentScreen(
    canStart: Boolean,
    blockedReason: String?,
    onBack: () -> Unit = {},
    onStart: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            MoyeotaTopBar(title = "택시비 정산", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Sm),
        ) {
            FareSplitCard {
                FareSplitSectionTitle("아직 택시비 정산이 없어요")
                FareSplitHint(
                    if (canStart) {
                        "택시비를 낸 분이 정산을 시작하면 함께 탄 분들에게 송금 요청이 가요"
                    } else {
                        "방장이 정산을 시작하면 보낼 금액과 계좌가 여기에 보여요"
                    },
                )
                blockedReason?.let { reason -> FareSplitNotice(FareSplitBadge(FareSplitTone.WARN, reason)) }
            }
            FareSplitHint(APP_DOES_NOT_MOVE_MONEY)
        }

        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            HorizontalDivider(color = MoyeotaColor.Hairline)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Xs),
            ) {
                if (canStart) {
                    PrimaryCtaButton(text = "택시비 정산 시작하기", onClick = onStart)
                } else {
                    // 「시작할 수 있는가」를 알 수 없었던 경우까지 여기로 온다 — 다시 읽는 길은 남긴다.
                    SecondaryButton(text = "다시 불러오기", onClick = onRetry)
                }
            }
            NavigationBarSpacer(Modifier.background(MoyeotaColor.SurfaceCanvas))
        }
    }
}

// ─────────────────────────── 프리뷰 ───────────────────────────

private val previewBank = FareSplitBank(Bank.KAKAO.code, Bank.KAKAO.displayName, Bank.KAKAO)

private fun previewDetail(
    role: FareSplitRole,
    status: FareSplitStatus = FareSplitStatus.OPEN,
    /** 받는 사람은 **서버가 일부러 null 을 준다** — NOT_SENT 로 접으면 방장에게 「보냈어요」가 뜬다. */
    myTransferStatus: TransferStatus? = if (role == FareSplitRole.SENDER) TransferStatus.NOT_SENT else null,
    accountNumber: String? = "3333012345678",
    tossDeeplink: String? = null,
    transfers: List<FareSplitTransfer> = listOf(
        FareSplitTransfer("s-a", "서면가자", 6_233, TransferStatus.SENT, "2026-11-12T01:00:00Z", null, "홍*동"),
        FareSplitTransfer("s-b", "부산대", 6_233, TransferStatus.NOT_SENT, null, null, "김*수"),
    ),
) = FareSplitDetail(
    partyId = 42L,
    status = status,
    totalFare = 18_700,
    payer = FareSplitParticipant("p-1", "모여타방장"),
    myRole = role,
    myAmount = if (role == FareSplitRole.PAYER) 6_234 else 6_233,
    myTransferStatus = myTransferStatus,
    bank = previewBank,
    accountNumber = accountNumber,
    tossDeeplink = tossDeeplink,
    transfers = transfers,
    createdAt = "2026-11-12T00:00:00Z",
    closedAt = null,
)

@Preview(showBackground = true)
@Composable
private fun FareSplitDetailPayerPreview() {
    FareSplitDetailScreen(detail = previewDetail(FareSplitRole.PAYER))
}

@Preview(showBackground = true)
@Composable
private fun FareSplitDetailSenderPreview() {
    FareSplitDetailScreen(
        detail = previewDetail(
            role = FareSplitRole.SENDER,
            tossDeeplink = "supertoss://send?amount=6233&bank=카카오뱅크&accountNo=3333012345678",
            transfers = listOf(
                FareSplitTransfer("s-a", "서면가자", 6_233, TransferStatus.NOT_SENT, null, null, null),
            ),
        ),
    )
}

/** 받는 사람이 탈퇴해 보낼 계좌가 사라진 진행 중 정산 — 「불러오기 실패」가 아니다. */
@Preview(showBackground = true)
@Composable
private fun FareSplitDetailAccountLostPreview() {
    FareSplitDetailScreen(
        detail = previewDetail(
            role = FareSplitRole.SENDER,
            accountNumber = null,
            transfers = listOf(
                FareSplitTransfer("s-a", "서면가자", 6_233, TransferStatus.NOT_SENT, null, null, null),
            ),
        ),
        onOpenChat = {},
    )
}

/** 전원 확인으로 닫힌 정산 — 계좌번호가 서버에서 지워져 계좌 블록이 아예 없다. */
@Preview(showBackground = true)
@Composable
private fun FareSplitDetailCompletedPreview() {
    FareSplitDetailScreen(
        detail = previewDetail(
            role = FareSplitRole.PAYER,
            status = FareSplitStatus.COMPLETED,
            accountNumber = null,
            transfers = listOf(
                FareSplitTransfer("s-a", "서면가자", 6_233, TransferStatus.CONFIRMED, null, "2026-11-12T02:00:00Z", "홍*동"),
                FareSplitTransfer("s-b", "부산대", 6_233, TransferStatus.CONFIRMED, null, "2026-11-12T02:10:00Z", "김*수"),
            ),
        ),
    )
}

/** 끝난 방인데 아직 정산이 없다 — **정상 상태**다. 방장에게는 시작 버튼이 보인다. */
@Preview(showBackground = true)
@Composable
private fun FareSplitAbsentStartablePreview() {
    FareSplitAbsentScreen(canStart = true, blockedReason = null)
}

/** 혼자 탄 방 — 되돌릴 길이 없는 막힘이라 시작 버튼을 그리지 않는다. */
@Preview(showBackground = true)
@Composable
private fun FareSplitAbsentBlockedPreview() {
    FareSplitAbsentScreen(
        canStart = false,
        blockedReason = "함께 탄 사람이 혼자예요. 나눌 상대가 없어 이 방은 정산할 수 없어요",
    )
}
