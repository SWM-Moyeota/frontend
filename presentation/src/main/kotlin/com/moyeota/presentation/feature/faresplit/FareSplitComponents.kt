package com.moyeota.presentation.feature.faresplit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import kotlinx.coroutines.delay

/**
 * 39·40 정산 화면의 공용 조각들 — **두 화면에서만** 쓰이므로 `core:designsystem` 으로 올리지 않는다
 * (스킬 규칙: 2개 이상 화면에서 쓰이는 새 UI 요소만 승격한다. 여기 있는 것들은 정산 두 화면 전용이고
 * 「복사」 버튼처럼 이 도메인에만 있는 개념을 담는다).
 *
 * 색은 [MoyeotaColor] 에서만 가져온다 — 이 파일에 hex 리터럴은 한 줄도 없다.
 */

/** 정산 화면의 뜻([FareSplitTone]) → 디자인 시스템의 색 종류. **여기가 둘을 잇는 유일한 자리다.** */
internal fun noticeKindOf(tone: FareSplitTone): NoticeKind = when (tone) {
    FareSplitTone.INFO -> NoticeKind.INFO
    FareSplitTone.WARN -> NoticeKind.WAITING
    FareSplitTone.DONE -> NoticeKind.SUCCESS
    FareSplitTone.DANGER -> NoticeKind.ERROR
}

/** 배지 하나. 글자와 색을 함께 든 [FareSplitBadge] 를 그대로 받는다 — 호출부가 색을 고르지 않는다. */
@Composable
internal fun FareSplitBadgeChip(badge: FareSplitBadge, modifier: Modifier = Modifier) {
    StatusBadge(kind = noticeKindOf(badge.tone), text = badge.label, modifier = modifier)
}

/** 안내 한 줄. [FareSplitBadge] 를 띠로 그린다. */
@Composable
internal fun FareSplitNotice(badge: FareSplitBadge, modifier: Modifier = Modifier) {
    NoticeBanner(kind = noticeKindOf(badge.tone), text = badge.label, modifier = modifier)
}

/** 흰 카드 한 장. 이 화면들의 모든 블록이 같은 모양이라 한 곳에 둔다. */
@Composable
internal fun FareSplitCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MoyeotaColor.SurfaceCard)
            .border(1.dp, MoyeotaColor.Hairline, RoundedCornerShape(16.dp))
            .padding(MoyeotaSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Sm),
        content = content,
    )
}

/** 카드 안의 소제목. */
@Composable
internal fun FareSplitSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MoyeotaType.HeadingMd, color = MoyeotaColor.InkPrimary, modifier = modifier)
}

/**
 * `라벨 ────── 값` 한 줄. 값을 **강조할지**([strong]) 고를 수 있다 — 같은 카드 안에서 「총 택시비」와
 * 「내가 보낼 금액」의 무게가 달라야 한다.
 */
@Composable
internal fun FareSplitKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MoyeotaType.BodySm,
            color = MoyeotaColor.TextMute,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (strong) MoyeotaType.HeadingLg else MoyeotaType.BodyMd,
            color = if (strong) MoyeotaColor.Primary600 else MoyeotaColor.InkPrimary,
        )
    }
}

/**
 * 이름 자리의 작은 이니셜 원.
 *
 * `MemberDisplay.MemberAvatar` 를 쓰지 않는 이유: 그쪽은 `User` 를 받아 **프로필 사진**을 그리고
 * 실패하면 이니셜로 떨어지는데, 정산 응답에는 `User` 도 `imageUrl` 도 없다(서버가 정산에
 * 프로필 사진을 싣지 않는다). 즉 사진을 그릴 거리가 **애초에 없어서** 그 폴백 장치 전체가
 * 쓸모가 없다 — 억지로 맞추면 늘 실패하는 이미지 요청 경로만 하나 생긴다.
 */
@Composable
internal fun FareSplitInitialCircle(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(MoyeotaColor.SurfaceSoft, CircleShape)
            .border(1.dp, MoyeotaColor.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).ifBlank { "?" },
            style = MoyeotaType.ButtonMd,
            color = MoyeotaColor.TextMute,
        )
    }
}

/** 복사 버튼의 글자가 「복사했어요」로 머무는 시간(ms). 눌렀다는 것을 알아볼 만큼만 둔다. */
private const val COPY_FEEDBACK_MS = 1_800L

/**
 * **복사 버튼** — 토스가 열리지 않는 기기에서 유일하게 남는 길이다.
 *
 * ### 이 버튼은 선택이 아니라 요구사항이다 ★
 * 서버 주석 원문: 「비공식 규격이라 열리지 않을 수 있어 계좌·금액 복사 버튼을 함께 둔다」.
 * 토스 버튼 **옆**이 아니라 계좌·금액 **바로 옆**에 두는 이유: 토스가 열리지 않는 것을 확인한
 * 사용자가 다음으로 보는 곳이 숫자 그 자체다.
 *
 * 피드백을 토스트가 아니라 **버튼 글자**로 주는 근거는 [copyToClipboard] KDoc 에 있다(API 33+ 는
 * OS 가 이미 확인 UI 를 띄운다). 실패하면 「복사하지 못했어요」로 갈린다 — 「복사했어요」를 띄우고
 * 붙여넣기가 비어 있는 것이 최악이다.
 *
 * @param onCopy 복사를 실제로 수행하고 **성공 여부**를 돌려준다. 화면이 [Context] 를 들고 있지
 *   않아도 되게 하기 위한 배치다(스테이트리스 Screen 의 `@Preview` 가 공짜로 안전해진다).
 */
@Composable
internal fun CopyButton(
    label: String,
    onCopy: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    // null = 아직 안 눌렀다, true = 복사됨, false = 못 했다. 세 값을 가르지 않으면
    // 「복사하지 못했어요」와 「아직 안 눌렀다」가 같은 모습이 된다.
    var copied by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(copied) {
        if (copied != null) {
            delay(COPY_FEEDBACK_MS)
            copied = null
        }
    }

    val text = when (copied) {
        true -> "복사했어요"
        false -> "복사하지 못했어요"
        null -> label
    }
    val tint = when (copied) {
        true -> MoyeotaColor.Success600
        false -> MoyeotaColor.Danger600
        null -> MoyeotaColor.Primary600
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(MoyeotaColor.Primary50)
            .clickable { copied = onCopy() }
            .padding(horizontal = MoyeotaSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MoyeotaType.ButtonSm, color = tint, maxLines = 1)
    }
}

/**
 * 계좌·금액 한 줄 + 복사 버튼.
 *
 * **복사되는 값과 보이는 값을 따로 받는다**([display] · [copyValue]). 계좌번호는 사람이 읽기 쉽게
 * 끊어 보여 줄 수 있지만 **복사되는 값은 숫자만**이어야 한다 — 은행 앱 붙여넣기 칸이 하이픈을
 * 거절하는 경우가 있다([com.moyeota.domain.model.ManualTransfer] KDoc).
 */
@Composable
internal fun CopyableValueRow(
    label: String,
    display: String,
    copyValue: String,
    onCopy: (String, String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute)
            Spacer(Modifier.height(2.dp))
            Text(
                text = display,
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(MoyeotaSpacing.Xs))
        CopyButton(label = "복사", onCopy = { onCopy(label, copyValue) })
    }
}

/** 보조 설명 한 줄(작고 회색). 「왜 이 칸이 있는가」를 적는 자리다. */
@Composable
internal fun FareSplitHint(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute, modifier = modifier)
}
