package com.moyeota.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

// 온보딩 페이지 인디케이터 — 활성은 20x8 pill, 비활성은 8x8 dot
@Composable
fun PageDots(count: Int, activeIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            if (i == activeIndex) {
                Box(Modifier.size(20.dp, 8.dp).background(MoyeotaColor.Primary500, CircleShape))
            } else {
                Box(Modifier.size(8.dp).background(MoyeotaColor.TextAsh, CircleShape))
            }
        }
    }
}

/**
 * 안내·상태의 종류. 색이 **뜻을 싣는** 셋(ERROR 빨강 · SUCCESS 초록 = 매칭/확정 · WAITING 호박 = 대기)과
 * 뜻이 없는 둘(INFO · NEUTRAL)로 나뉜다.
 */
enum class NoticeKind { INFO, ERROR, SUCCESS, WAITING, NEUTRAL }

/**
 * 공통 규칙: 화면 단위 오류는 CTA 위 NoticeBanner Kind=error.
 *
 * **INFO 는 회색이다**(디자인 QA G8, 2026-11). 예전엔 옅은 파랑이었는데, 안내 한 줄마다 파란 판이 깔려
 * 화면 곳곳이 "강조"가 되고 정작 파란 CTA 가 묻혔다. 색이 뜻을 싣는 ERROR·SUCCESS·WAITING 만 물들인다.
 */
@Composable
fun NoticeBanner(kind: NoticeKind, text: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        NoticeKind.INFO, NoticeKind.NEUTRAL -> MoyeotaColor.SurfaceSoft to MoyeotaColor.TextBody
        NoticeKind.ERROR -> MoyeotaColor.Danger50 to MoyeotaColor.Danger600
        NoticeKind.SUCCESS -> MoyeotaColor.Success50 to MoyeotaColor.Success600
        NoticeKind.WAITING -> MoyeotaColor.Waiting50 to MoyeotaColor.Waiting600
    }
    Box(
        modifier = modifier.fillMaxWidth().background(bg, MoyeotaShape.Card).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MoyeotaType.BodySm, color = fg)
    }
}

/**
 * 상태 배지 (모집 중 · 매칭 완료 · 대기 등).
 *
 * [NoticeKind.NEUTRAL] 은 상태가 아니라 **사실**(「2/3명」·「도착지」)을 붙일 때 쓴다 — 초록(=매칭됨)이나
 * 호박(=기다리는 중)으로 칠하면 없는 상태를 말하게 된다(디자인 QA S16-5·E2).
 */
@Composable
fun StatusBadge(kind: NoticeKind, text: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        NoticeKind.INFO -> MoyeotaColor.Primary50 to MoyeotaColor.Primary600
        NoticeKind.NEUTRAL -> MoyeotaColor.SurfaceSoft to MoyeotaColor.TextBody
        NoticeKind.ERROR -> MoyeotaColor.Danger50 to MoyeotaColor.Danger600
        NoticeKind.SUCCESS -> MoyeotaColor.Success50 to MoyeotaColor.Success600
        NoticeKind.WAITING -> MoyeotaColor.Waiting50 to MoyeotaColor.Waiting600
    }
    Box(modifier = modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text = text, style = MoyeotaType.CaptionMd, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 묶음 머리말 — 「즐겨찾기」·「매칭 조건」·「이미 참여한 사람」 (디자인 QA G9).
 *
 * 예전엔 같은 역할의 머리말이 13 Bold · 13 Medium · 12 Bold 로 여섯 갈래였다. 13sp SemiBold [MoyeotaColor.TextMute]
 * 한 가지로 모으고, 아래 8dp 를 띄운다. **좌우 여백은 놓이는 자리가 준다** — 대부분 이미 16dp 화면 여백 안에
 * 놓이므로 여기서 또 주면 두 번 들어간다. 스크린리더에는 제목(heading)으로 알린다.
 *
 * @param trailing 오른쪽 끝의 작은 행동(「편집」) — [TextLinkButton] 을 넘긴다
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MoyeotaType.BodySm,
            fontWeight = FontWeight.SemiBold,
            color = MoyeotaColor.TextMute,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) trailing()
    }
}

/**
 * 글자만 있는 작은 행동(「편집」·「조정」·「로그인」·「지도 접기」) — **보이는 건 글자지만 터치 영역은 48dp** 이고
 * 누르면 물결이 번진다(디자인 QA G12). 예전엔 글자 크기 그대로(24dp 안팎) 눌리고 눌림 표시도 없었다.
 *
 * 좌우 8dp 안쪽 여백이 있어 글자는 터치 상자보다 8dp 안에 그려진다 — 화면 여백에 글자 끝을 맞추려면
 * 호출부가 그만큼 바깥으로 둔다.
 *
 * @param trailingIcon 글자 뒤의 16dp 아이콘(「지도 접기」의 꺾쇠)
 */
@Composable
fun TextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MoyeotaColor.Primary600,
    style: TextStyle = MoyeotaType.ButtonMd,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MoyeotaShape.Small)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, style = style, color = if (enabled) color else MoyeotaColor.TextPlaceholder, maxLines = 1)
        if (trailingIcon != null) trailingIcon()
    }
}

/**
 * 아바타. [imageUrl] 이 있으면 서버 프로필 사진을 그리고, **그릴 수 없을 때는 [label]**(없으면 회색 원)
 * 으로 되돌아간다.
 *
 * ### 네 경우에 무엇이 보이는가
 *
 * | 경우 | 보이는 것 |
 * |---|---|
 * | [imageUrl] 이 null/빈 값 | [label] 또는 빈 회색 원 — **요청을 만들지 않는다** |
 * | 로딩 중 | 같음 |
 * | 성공 | 사진 |
 * | 실패(403·타임아웃·디코드 실패) | 같음 |
 *
 * null 을 `AsyncImage` 에 넘겨 `fallback` 으로 처리하지 않는 이유: 이 앱에서 사진 없음은 **정상
 * 경우**(유저 요약이 없는 멤버·조회 실패)라, 요청을 만들고 `NullRequestDataException` 을 던지는
 * 경로를 타는 것보다 분기로 거르는 쪽이 의도를 정확히 말한다.
 *
 * `error`/`fallback` 에 `Painter` 를 넘기지 않는 이유도 같다 — 대체 표시가 글자([label])라
 * Painter 로 옮기려면 `DrawScope` + `TextMeasurer` 로 다시 써야 한다. 대신 **아래에 깔고 성공했을
 * 때만 가린다.** 덤으로 `@Preview` 가 공짜로 안전해진다(프리뷰 샌드박스에는 네트워크가 없어 항상
 * 실패 → 지금과 똑같은 회색 원이 보인다).
 *
 * **이 장치는 로더의 crossfade 가 꺼져 있어야 성립한다.** Coil 은 `onSuccess` 를 CrossfadePainter 를
 * 설치한 **뒤에** 부르므로, 켜져 있으면 대체 표시를 지운 뒤 100ms 동안 빈 원만 남는다. 그래서
 * `MoyeotaApplication.newImageLoader` 가 crossfade 를 켜지 않는다(그쪽 주석에 근거).
 *
 * ### [imageDrawn] 의 `remember` 키가 [imageUrl] 인 이유
 * 호출부에 `LazyColumn` 은 없다 — 전부 `verticalScroll` + `forEach` 이고 `key()` 를 쓰지 않는다.
 * 그래도 **위치 기반 슬롯 재사용은 일어난다**: 채팅 목록이 최근 메시지 순으로 재정렬되거나
 * 매칭 대기의 멤버가 한 명 나가 목록이 밀리면, 같은 위치의 컴포지션이 **다른 방·다른 사람**을 받는다.
 * 키가 없으면 그때 `imageDrawn = true` 가 물려 내려와 **새 사람의 대체 표시가 가려진 채 앞 사람 사진이
 * 남는다.** `onLoading`/`onError` 에서도 false 로 되돌리는 건 같은 이유다.
 */
@Composable
fun AvatarCircle(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    label: String? = null,
    imageUrl: String? = null,
) {
    var imageDrawn by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier.size(size).background(MoyeotaColor.SurfaceSoft, CircleShape).border(1.dp, MoyeotaColor.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null && !imageDrawn) {
            Text(text = label, style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute)
        }
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                // 이름을 바로 옆에서 읽어 주는 자리들이라 사진은 장식이다 — 중복 낭독을 막는다.
                contentDescription = null,
                modifier = Modifier
                    // size(size) 를 다시 쓰지 않는다. AsyncImage 는 intrinsic size 가 없어
                    // (내부 MeasurePolicy 가 layout(minWidth, minHeight)) 크기를 받지 못하면
                    // 0x0 으로 측정되고 **이미지는 멀쩡히 로드되는데 화면에는 안 보인다.**
                    .matchParentSize()
                    // padding 이 clip **앞**이어야 한다 — 꽉 찬 원형 이미지는 부모의 1dp
                    // 헤어라인을 덮는다(부모 modifier → 자식 순으로 그려진다).
                    .padding(1.dp)
                    .clip(CircleShape),
                // 기본값이 Fit 이라 그대로 두면 비정사각 이미지가 원 안에서 레터박스가 된다.
                contentScale = ContentScale.Crop,
                onLoading = { imageDrawn = false },
                onSuccess = { imageDrawn = true },
                onError = { imageDrawn = false },
            )
        }
    }
}

/**
 * 선택 칩 — 앱의 **단 하나뿐인 칩** (디자인 QA G6, 2026-11). 16 매칭 조건, 16a 출발지/도착지 토글,
 * 17 탐색 모드·날짜, 21 약속 프리셋이 모두 이걸 쓴다.
 *
 * - 보이는 높이 36dp, **터치 영역 48dp**([minimumInteractiveComponentSize]), 그림자 없음
 * - 미선택: 흰 바탕 + [MoyeotaColor.Border] 테두리 + [MoyeotaColor.TextBody] 글자
 * - 선택: 옅은 파랑 바탕 + 파란 테두리 + 진한 파랑 글자
 *
 * 선택을 **솔리드 파랑으로 칠하지 않는 이유**: 한 화면에 솔리드 파랑은 CTA 하나만 둔다(디자인 문서
 * "primary 를 아껴 쓴다"). 예전 16 은 흰 그림자 칩 + 솔리드 파랑 선택 칩, 17 은 옅은 칩으로 두 갈래였다.
 *
 * @param horizontalPadding 글자 좌우 여백. 둘뿐인 토글(16a 출발지/도착지)처럼 넓게 누르게 할 때만 늘린다
 */
@Composable
fun MoyeotaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp = 14.dp,
) {
    val bg = if (selected) MoyeotaColor.Primary50 else MoyeotaColor.SurfaceCanvas
    val fg = when {
        !enabled -> MoyeotaColor.TextPlaceholder
        selected -> MoyeotaColor.Primary600
        else -> MoyeotaColor.TextBody
    }
    val border = if (selected) MoyeotaColor.Primary500 else MoyeotaColor.Border
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape)
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MoyeotaType.ButtonMd, color = fg, maxLines = 1, softWrap = false)
        }
    }
}

// 바텀시트 핸들
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 4.dp)
            .background(MoyeotaColor.TextAsh, CircleShape),
    )
}

// 지도 자리표시 — SDK 없이 와이어프레임처럼 격자 배경
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MoyeotaColor.SurfaceSoft)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .size(height = 2.dp, width = Dp.Unspecified)
                .background(MoyeotaColor.Border),
        )
        Text(
            text = "지도 영역",
            style = MoyeotaType.CaptionMd,
            color = MoyeotaColor.TextPlaceholder,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
