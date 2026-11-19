package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.MoyeotaTabHeader
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.tabularNums
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom
import kotlin.math.ceil

/**
 * 24 · 채팅 목록 — 내가 속한 채팅방(GET /chat-rooms/me)
 *
 * 이동:
 * - 방 행 탭 → 24 채팅방 (onRoomClick)
 * - 방 행 길게 누르기 → 알림 끄기/켜기 · 나가기 메뉴 (onRoomLongClick — [ChatRoomActionSheet])
 * - 하단탭 → 14/17/35 (onTabSelect)
 *
 * 제목은 **참여자 닉네임**([ChatRoomListItem.peerTitle] · `chatRoomPeerTitle`), 그 아래에 **경로 한 줄**과
 * **마지막 메시지** 한 줄([chatRoomSubtitle]), 우측에 마지막 메시지 시각과 안읽음 점.
 * 정렬은 Route 가 이미 마친 상태로 넘어온다(`chatRoomSortKey` — 마지막 활동 시각 내림차순).
 * 알림을 끈 방은 제목 옆에 사선 그은 종이 붙는다([MutedMark]) — 켜진 방은 아무것도 붙지 않는다.
 *
 * @param notice 길게 누르기 메뉴의 동작이 실패했을 때 목록 위에 띄우는 한 줄
 */
@Composable
fun ChatListScreen(
    rooms: List<ChatRoomListItem> = emptyList(),
    notice: String? = null,
    onRoomClick: (MyChatRoom) -> Unit = {},
    onRoomLongClick: (ChatRoomListItem) -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    // 흰 바탕 위 꽉 찬 행 목록(디자인 QA C1). 예전엔 회색 화면 위에 그림자 진 큰 카드 하나를 띄우고 그 안에
    // 행을 넣었다 — 카드가 행을 한 번 더 감싸 목록이 "판 위의 판"으로 보였다. 메신저 목록의 관습대로 행이
    // 화면 폭을 다 쓰고, 행 사이는 글 시작선에 맞춘 얇은 선 하나로 나눈다.
    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()
        MoyeotaTabHeader(title = "채팅")
        // 스크롤 밖 머리 아래에 둔다 — 아래쪽 행에서 메뉴를 썼어도 보이고, 스크린리더가 바로 읽는다
        if (notice != null) {
            NoticeBanner(
                kind = NoticeKind.ERROR,
                text = notice,
                modifier = Modifier
                    .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.size(MoyeotaSpacing.Xs))
            if (rooms.isEmpty()) {
                Text(
                    text = "아직 참여 중인 채팅방이 없어요. 동승에 합류하면 채팅방이 열려요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                    modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Lg),
                )
            } else {
                rooms.forEachIndexed { index, item ->
                    ChatRoomRow(
                        item = item,
                        onClick = { onRoomClick(item.room) },
                        onLongClick = { onRoomLongClick(item) },
                    )
                    if (index != rooms.lastIndex) {
                        HorizontalDivider(
                            color = MoyeotaColor.Hairline,
                            // 글 시작선(여백 16 + 아바타 48 + 간격 12)에 맞춘다 — 아바타 열은 끊김 없이 이어진다
                            modifier = Modifier.padding(start = ChatRowTextStart),
                        )
                    }
                }
            }
            Spacer(Modifier.size(MoyeotaSpacing.Md))
        }

        MoyeotaBottomBar(selected = MoyeotaTab.CHAT, onSelect = onTabSelect)
    }
}

/** 행 아바타 지름 — 40 → 48dp(디자인 QA C1). 부제가 두 줄인 행(약 76dp)에서도 얼굴이 작아 보이지 않게 */
private val ChatRowAvatarSize = 48.dp

/** 행 글 묶음이 시작하는 x — 구분선도 여기서 시작한다 */
private val ChatRowTextStart = MoyeotaSpacing.ScreenGutter + ChatRowAvatarSize + MoyeotaSpacing.Sm

/**
 * 목록 행에서 제목 아래 오는 두 줄 — 경로와 마지막 메시지.
 *
 * ### 경로는 메시지가 있어도 늘 보인다 (2026-11, 오너 피드백)
 * 예전엔 마지막 메시지가 있으면 미리보기가 경로 자리를 차지해, 대화가 오간 방은 **어디서 어디로 가는
 * 방인지** 목록에서 알 수 없었다(경로는 메시지가 없는 방에서만 보였다).
 *
 * @property route 경로 한 줄([RouteLine]). 참여자를 못 받은 방은 **제목이 이미 경로**라 null 이다
 *   ([chatRoomListTitle]) — 같은 말을 두 번 쓰지 않는다
 * @property preview 마지막 메시지 미리보기. 메시지가 없으면 null — 다만 제목이 경로인 방은 방 상태
 *   (「진행 중」·「종료된 방」)를 대신 쓴다. 그 방은 경로 줄이 없어 제목 아래가 비기 때문이다
 */
internal data class ChatRoomSubtitle(val route: RouteLine?, val preview: String?)

/** 「강남역 → 판교역」 한 줄. [closed] 면 앞에 「종료 · 」가 붙는다 — 닫힌 방의 표시는 이 줄이 맡는다. */
internal data class RouteLine(val departure: String, val destination: String, val closed: Boolean = false)

/**
 * 목록 부제 — 경로 한 줄 + 마지막 메시지 미리보기.
 * 위치 공유 메시지는 본문이 좌표라 사람 말로 바꾼다. 삭제된 메시지는 서버가 이미 「삭제된 메시지입니다」로 준다.
 *
 * 닫힌 방의 「종료 · 」는 **한 번만** 붙인다 — 경로 줄이 있으면 그 줄에, 제목이 경로인 방이면 미리보기 줄에.
 */
internal fun chatRoomSubtitle(item: ChatRoomListItem): ChatRoomSubtitle {
    val room = item.room.room
    val active = room.status == ChatRoomStatus.ACTIVE
    val preview = item.room.membership.lastMessage?.let { last ->
        when (last.type) {
            // 이모지를 앞에 붙이지 않는다 — 글꼴마다 모양이 달라지고 문장만으로 뜻이 선다(디자인 QA C2)
            ChatMessageType.LOCATION -> "위치를 공유했어요"
            ChatMessageType.TEXT -> last.content
        }
    }
    if (item.peerTitle == null) {
        val line = when {
            preview == null -> if (active) "진행 중" else "종료된 방"
            active -> preview
            else -> "종료 · $preview"
        }
        return ChatRoomSubtitle(route = null, preview = line)
    }
    return ChatRoomSubtitle(route = RouteLine(room.departure, room.destination, closed = !active), preview = preview)
}

/**
 * 경로 한 줄의 폭 나누기 — 출발지와 도착지가 **각자** 쓸 폭(px).
 *
 * 「출발 → 도착」을 통째로 끝에서 줄이면, 출발지가 길 때(역지오코딩 실주소가 들어온다) 화살표 뒤가 잘려
 * **도착지가 통째로 사라진다.** 그래서 둘을 따로 줄인다:
 * - 둘 다 들어가면 그대로
 * - 한쪽이 절반 이하면 그쪽은 다 보이고, 남은 폭을 긴 쪽이 쓴다
 * - 둘 다 절반을 넘으면 반씩 — 양쪽 다 앞부분이 남는다
 */
internal fun splitRouteWidths(available: Int, departure: Int, destination: Int): Pair<Int, Int> {
    if (available <= 0) return 0 to 0
    if (departure + destination <= available) return departure to destination
    val half = available / 2
    return when {
        departure <= half -> departure to available - departure
        destination <= half -> available - destination to destination
        else -> half to available - half
    }
}

/**
 * 「[종료 · ]출발 → 도착」 한 줄 — 출발지와 도착지를 따로 줄여 **둘 다 남긴다**([splitRouteWidths]).
 * 스크린리더에는 화살표 대신 「출발지 …, 도착지 …」로 읽힌다.
 */
@Composable
private fun RouteOneLine(route: RouteLine, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val spoken = (if (route.closed) "종료, " else "") + "출발지 ${route.departure}, 도착지 ${route.destination}"
    // 줄인 출발지의 실제 글자 끝. `Text` 상자는 받은 폭을 다 차지해 「…」 뒤가 비므로, 화살표를 상자 끝이 아니라
    // 글자 끝에 붙인다. 출발지를 재는 동안(`measure`) 채워지므로 같은 측정 안에서 읽을 수 있다.
    val departureLayout = remember { TextLayoutHolder() }
    Layout(
        content = {
            Text(text = if (route.closed) "종료 · " else "", style = style, color = color, maxLines = 1)
            Text(
                text = route.departure,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { departureLayout.result = it },
            )
            Text(text = " → ", style = style, color = color, maxLines = 1)
            Text(text = route.destination, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) { measurables, constraints ->
        val (prefix, departure, arrow, destination) = measurables
        val loose = Constraints()
        val prefixPlaceable = prefix.measure(loose)
        val arrowPlaceable = arrow.measure(loose)
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val (departureWidth, destinationWidth) = splitRouteWidths(
            available = (maxWidth - prefixPlaceable.width - arrowPlaceable.width).coerceAtLeast(0),
            departure = departure.maxIntrinsicWidth(Constraints.Infinity),
            destination = destination.maxIntrinsicWidth(Constraints.Infinity),
        )
        val departurePlaceable = departure.measure(Constraints(maxWidth = departureWidth))
        val departureInk = departureLayout.result
            ?.let { ceil(it.getLineRight(0)).toInt() }
            ?.coerceAtMost(departurePlaceable.width)
            ?: departurePlaceable.width
        val destinationPlaceable = destination.measure(Constraints(maxWidth = destinationWidth))
        // (놓을 조각, 다음 조각까지의 폭) — 출발지만 상자 폭 대신 글자 폭만큼 나아간다
        val pieces = listOf(
            prefixPlaceable to prefixPlaceable.width,
            departurePlaceable to departureInk,
            arrowPlaceable to arrowPlaceable.width,
            destinationPlaceable to destinationPlaceable.width,
        )
        val width = pieces.sumOf { it.second }.coerceIn(constraints.minWidth, maxWidth)
        layout(width, pieces.maxOf { it.first.height }) {
            var x = 0
            pieces.forEach { (placeable, advance) ->
                placeable.placeRelative(x, 0)
                x += advance
            }
        }
    }
}

/** 측정 중에 받은 글자 배치를 같은 측정 안에서 읽으려는 그릇 — 상태가 아니라서 다시 그리기를 부르지 않는다. */
private class TextLayoutHolder {
    var result: TextLayoutResult? = null
}

/**
 * 목록 행 제목 — 참여자 닉네임([ChatRoomListItem.peerTitle]), 못 받았으면 경로 한 줄.
 * 경로로 떨어질 때 부제는 경로를 되풀이하지 않는다([chatRoomSubtitle]).
 */
internal fun chatRoomListTitle(item: ChatRoomListItem): String =
    item.peerTitle ?: "${item.room.room.departure} → ${item.room.room.destination}"

@Composable
private fun ChatRoomRow(item: ChatRoomListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val membership = item.room.membership
    val haptic = LocalHapticFeedback.current
    val subtitle = chatRoomSubtitle(item)
    val timeLabel = membership.lastMessage?.createdAt?.toListTimeLabel()
    val unread = membership.hasUnread
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 길게 누르면 메뉴(카카오톡과 같은 손버릇). 손끝에 진동을 줘 메뉴가 「눌러서」 열렸음을 알린다
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "채팅방 메뉴",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Sm),
    ) {
        // 제목이 부르는 사람들의 얼굴([ChatRoomListItem.peerImageUrls]) — 제목과 **같은 멤버 목록**에서
        // 뽑아 Route 가 실어 보낸다. 참여자를 못 받은 방은 제목이 경로로 떨어지고 여기도 회색 원이다.
        // 부제가 두 줄이 되어 행이 높아져도 아바타는 행 가운데에 선다.
        PeerAvatarGroup(
            imageUrls = item.peerImageUrls,
            size = ChatRowAvatarSize,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Spacer(Modifier.size(MoyeotaSpacing.Sm))
        // 글 묶음과 오른쪽 시각은 **제목의 기준선**에 맞춘다 — 부제가 두 줄이 된 행에서 시각이 행 가운데로
        // 내려가면 어느 줄의 시각인지 흐려진다(디자인 QA 4-10).
        Column(modifier = Modifier.weight(1f).alignByBaseline()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 제목이 길어도 종이 밀려나지 않게 제목만 줄어든다(fill = false — 짧은 제목 바로 옆에 붙는다)
                if (item.peerTitle != null) {
                    Text(
                        text = item.peerTitle,
                        style = MoyeotaType.HeadingMd,
                        color = MoyeotaColor.InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    // 참여자를 못 받은 방은 경로가 제목이다 — 제목에서도 도착지가 잘려 사라지지 않게 따로 줄인다
                    val room = item.room.room
                    RouteOneLine(
                        route = RouteLine(room.departure, room.destination),
                        style = MoyeotaType.HeadingMd,
                        color = MoyeotaColor.InkPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                // 셋 이상인 방의 인원(나 포함, [chatRoomMemberCountLabel]) — 이름만으로는 몇 명이 탄 방인지
                // 모른다. 숫자만 쓰면 닉네임 끝 숫자와 붙어 읽혀 「명」을 붙인다. 제목이 길어도 밀려나지 않는다.
                chatRoomMemberCountLabel(item.memberCount)?.let { label ->
                    Text(
                        text = label,
                        style = MoyeotaType.BodySm.tabularNums(),
                        color = MoyeotaColor.TextMute,
                        maxLines = 1,
                        modifier = Modifier.padding(start = MoyeotaSpacing.Xs),
                    )
                }
                if (membership.notificationMuted) MutedMark()
            }
            subtitle.route?.let { route ->
                Spacer(Modifier.height(2.dp))
                // 경로는 보조 정보라 미리보기보다 한 단계 작고 흐리게 둔다 — 눈은 제목 다음에 미리보기로 간다
                RouteOneLine(route = route, style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute)
            }
            subtitle.preview?.let { preview ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MoyeotaType.BodySm,
                    // 안 읽은 방은 미리보기를 진하게 — 배지와 함께 「새 대화」를 두 신호로 알린다
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) MoyeotaColor.InkPrimary else MoyeotaColor.TextMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(MoyeotaSpacing.Xs))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.alignByBaseline()) {
            if (timeLabel != null) {
                Text(
                    text = timeLabel,
                    style = MoyeotaType.CaptionMd.tabularNums(),
                    color = MoyeotaColor.TextMute,
                )
                Spacer(Modifier.size(MoyeotaSpacing.Xs))
            }
            // 안읽음 배지 — 서버가 개수를 주면 숫자(99+ 상한), 개수 없는 구버전 응답이면 점
            if (unread) {
                UnreadBadge(count = membership.unreadCount)
            }
        }
    }
}

/**
 * 묶음 아바타의 얼굴 한 자리 — 한 변이 1 인 상자 안에서 왼쪽 위 모서리([x]·[y])와 지름([size])의 비율.
 */
internal data class FaceSlot(val x: Float, val y: Float, val size: Float)

/**
 * 묶음 아바타 배치 — 얼굴 [count] 개를 한 상자에 모은다(2026-11 오너 피드백, 카카오톡 채팅 목록 모양).
 * - 0·1개: 상자를 꽉 채운 하나(지금까지의 1:1 방 모양)
 * - 2개: 왼쪽 위·오른쪽 아래로 비껴 겹친다
 * - 3개: 위 가운데 하나, 아래 둘 — 삼각형
 * - 4개 이상: 2×2(넘치는 얼굴은 [MAX_PEER_FACES] 에서 이미 잘렸다)
 *
 * 뒤에 오는 얼굴이 위에 그려진다 — 제목의 첫 사람이 맨 뒤(왼쪽 위)다.
 */
internal fun peerFaceSlots(count: Int): List<FaceSlot> = when {
    count <= 1 -> listOf(FaceSlot(0f, 0f, 1f))
    count == 2 -> PairFace.let { d -> listOf(FaceSlot(0f, 0f, d), FaceSlot(1f - d, 1f - d, d)) }
    count == 3 -> TrioFace.let { d -> listOf(FaceSlot((1f - d) / 2f, 0f, d), FaceSlot(0f, 1f - d, d), FaceSlot(1f - d, 1f - d, d)) }
    else -> GridFace.let { d -> listOf(FaceSlot(0f, 0f, d), FaceSlot(1f - d, 0f, d), FaceSlot(0f, 1f - d, d), FaceSlot(1f - d, 1f - d, d)) }
}

// 얼굴 지름 ÷ 상자 한 변. 둘은 크게 겹치고(48dp 상자에서 33dp), 셋은 서로 조금씩 물리며(29dp),
// 넷은 겹치지 않고 사이를 조금 띄운다(23dp).
private const val PairFace = 0.68f
private const val TrioFace = 0.6f
private const val GridFace = 0.48f

/** 겹친 얼굴 사이의 흰 테 두께 — 앞 얼굴이 뒤 얼굴을 파고든 자리가 보여야 두 사람으로 읽힌다. */
private val PeerFaceRing = 1.5.dp

/**
 * 목록 행 아바타 — 상대가 하나면 지금까지처럼 원 하나, 여럿이면 얼굴을 모아 그린다([peerFaceSlots]).
 * 사진은 장식이다(이름은 바로 옆 제목이 읽어 준다).
 */
@Composable
private fun PeerAvatarGroup(imageUrls: List<String?>, size: Dp, modifier: Modifier = Modifier) {
    if (imageUrls.size <= 1) {
        AvatarCircle(size = size, imageUrl = imageUrls.firstOrNull(), modifier = modifier)
        return
    }
    val slots = peerFaceSlots(imageUrls.size)
    Box(modifier.size(size)) {
        imageUrls.forEachIndexed { index, url ->
            val slot = slots[index]
            val face = size * slot.size
            Box(
                modifier = Modifier
                    .offset(x = size * slot.x, y = size * slot.y)
                    .size(face)
                    .background(MoyeotaColor.SurfaceCanvas, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AvatarCircle(size = face - PeerFaceRing * 2, imageUrl = url)
            }
        }
    }
}

/** 안읽음 개수 표기 — 두 자리까지 그대로, 그 위는 「99+」(배지가 행 높이를 넘지 않게). null 은 개수 모름(점) */
internal fun unreadBadgeLabel(count: Int?): String? = when {
    count == null -> null
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

@Composable
private fun UnreadBadge(count: Int?) {
    val label = unreadBadgeLabel(count)
    if (label == null) {
        Box(Modifier.size(8.dp).background(MoyeotaColor.Primary500, CircleShape))
        return
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(MoyeotaColor.Primary500, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MoyeotaType.CaptionSm.tabularNums(),
            fontWeight = FontWeight.SemiBold,
            color = MoyeotaColor.TextOnDark,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatListScreenPreview() {
    ChatListScreen()
}
