package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.moyeota.presentation.core.RoutePlace
import com.moyeota.presentation.core.RoutePlaceLine
import com.moyeota.presentation.core.routePlaces

/**
 * 24 · 채팅 목록 — 내가 속한 채팅방(GET /chat-rooms/me)
 *
 * 이동:
 * - 방 행 탭 → 24 채팅방 (onRoomClick)
 * - 방 행 길게 누르기 → 알림 끄기/켜기 · 나가기 메뉴 (onRoomLongClick — [ChatRoomActionSheet])
 * - 하단탭 → 14/17/35 (onTabSelect)
 *
 * 제목은 **참여자 닉네임**([ChatRoomListItem.peerTitle] · `chatRoomPeerTitle`), 부제는 **마지막 메시지**
 * (없으면 「출발지:」·「도착지:」 두 줄 — [chatRoomSubtitle]), 우측에 마지막 메시지 시각과 안읽음 점.
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
 * 목록 부제 — 한 줄 글([Line]) 또는 경로 두 줄([Route]).
 *
 * 경로를 「강남역 → 판교역」 한 줄로 쓰지 않고 「출발지: 강남역」·「도착지: 판교역」 **두 줄**로 쓴다
 * (2026-11, 오너 UX 피드백) — 장소 이름이 길면(역지오코딩 실주소) 한 줄의 화살표 뒤가 잘려 도착지가
 * 통째로 사라졌다([RoutePlace]). 24 채팅방 **헤더**는 그대로 한 줄 경로다(그쪽은 폭이 넉넉하다).
 */
internal sealed interface ChatRoomSubtitle {
    /** 한 줄 — 마지막 메시지 미리보기, 또는 제목이 이미 경로일 때의 방 상태. */
    data class Line(val text: String) : ChatRoomSubtitle

    /** 경로 두 줄. 닫힌 방이면 첫 줄 이름표 앞에 「종료 · 」가 붙는다. */
    data class Route(val places: List<RoutePlace>) : ChatRoomSubtitle
}

/**
 * 목록 부제 — **마지막 메시지 미리보기**가 있으면 그것, 없으면 경로(+종료 여부).
 * 위치 공유 메시지는 본문이 좌표라 사람 말로 바꾼다. 삭제된 메시지는 서버가 이미 「삭제된 메시지입니다」로 준다.
 *
 * **제목이 이미 경로면 경로를 또 쓰지 않는다** — 참여자를 못 받은 방은 제목이 「출발 → 도착」으로
 * 떨어지는데([chatRoomListTitle]), 그 아래에 출발지·도착지 두 줄이 또 오면 같은 말이 세 줄이다.
 * 그때 부제는 방 상태(「진행 중」·「종료된 방」) 한 줄이다.
 */
internal fun chatRoomSubtitle(item: ChatRoomListItem): ChatRoomSubtitle {
    val room = item.room.room
    val active = room.status == ChatRoomStatus.ACTIVE
    val last = item.room.membership.lastMessage
    if (last != null) {
        val preview = when (last.type) {
            // 이모지를 앞에 붙이지 않는다 — 글꼴마다 모양이 달라지고 문장만으로 뜻이 선다(디자인 QA C2)
            ChatMessageType.LOCATION -> "위치를 공유했어요"
            ChatMessageType.TEXT -> last.content
        }
        return ChatRoomSubtitle.Line(if (active) preview else "종료 · $preview")
    }
    if (item.peerTitle == null) return ChatRoomSubtitle.Line(if (active) "진행 중" else "종료된 방")
    val places = routePlaces(room.departure, room.destination)
    return ChatRoomSubtitle.Route(
        if (active) places else places.mapIndexed { index, place ->
            if (index == 0) place.copy(label = "종료 · ${place.label}") else place
        },
    )
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
    val title = chatRoomListTitle(item)
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
        // 제목이 부르는 사람의 얼굴([ChatRoomListItem.peerImageUrl]) — 제목과 **같은 멤버 목록**에서
        // 뽑아 Route 가 실어 보낸다. 참여자를 못 받은 방은 제목이 경로로 떨어지고 여기도 회색 원이다.
        // 부제가 두 줄이 되어 행이 높아져도 아바타는 행 가운데에 선다.
        AvatarCircle(size = ChatRowAvatarSize, imageUrl = item.peerImageUrl, modifier = Modifier.align(Alignment.CenterVertically))
        Spacer(Modifier.size(MoyeotaSpacing.Sm))
        // 글 묶음과 오른쪽 시각은 **제목의 기준선**에 맞춘다 — 부제가 두 줄이 된 행에서 시각이 행 가운데로
        // 내려가면 어느 줄의 시각인지 흐려진다(디자인 QA 4-10).
        Column(modifier = Modifier.weight(1f).alignByBaseline()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 제목이 길어도 종이 밀려나지 않게 제목만 줄어든다(fill = false — 짧은 제목 바로 옆에 붙는다)
                Text(
                    text = title,
                    style = MoyeotaType.HeadingMd,
                    color = MoyeotaColor.InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (membership.notificationMuted) MutedMark()
            }
            Spacer(Modifier.height(2.dp))
            when (subtitle) {
                is ChatRoomSubtitle.Line -> Text(
                    text = subtitle.text,
                    style = MoyeotaType.BodySm,
                    // 안 읽은 방은 미리보기를 진하게 — 배지와 함께 「새 대화」를 두 신호로 알린다
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) MoyeotaColor.InkPrimary else MoyeotaColor.TextMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                is ChatRoomSubtitle.Route -> subtitle.places.forEachIndexed { index, place ->
                    // 두 줄 사이 2dp(디자인 QA 4-10)
                    if (index > 0) Spacer(Modifier.height(2.dp))
                    RoutePlaceLine(place)
                }
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
