package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.ChatMessageType
import com.moyeota.domain.model.ChatRoomStatus
import com.moyeota.domain.model.MyChatRoom

private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/**
 * 24 · 채팅 목록 — 내가 속한 채팅방(GET /chat-rooms/me)
 *
 * 이동:
 * - 방 행 탭 → 24 채팅방 (onRoomClick)
 * - 하단탭 → 14/17/35 (onTabSelect)
 *
 * 제목은 **참여자 닉네임**([ChatRoomListItem.peerTitle] · `chatRoomPeerTitle`), 부제는 **마지막 메시지**
 * (없으면 경로 — [chatRoomSubtitle]), 우측에 마지막 메시지 시각과 안읽음 점.
 * 정렬은 Route 가 이미 마친 상태로 넘어온다(`chatRoomSortKey` — 마지막 활동 시각 내림차순).
 */
@Composable
fun ChatListScreen(
    rooms: List<ChatRoomListItem> = emptyList(),
    onRoomClick: (MyChatRoom) -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
            StatusBarSpacer()
            Text(
                text = "채팅",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.size(16.dp))
            if (rooms.isEmpty()) {
                Text(
                    text = "아직 참여 중인 채팅방이 없어요. 합승에 합류하면 채팅방이 열려요",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoyeotaColor.SurfaceCanvas),
                ) {
                    rooms.forEachIndexed { index, item ->
                        ChatRoomRow(item = item, onClick = { onRoomClick(item.room) })
                        if (index != rooms.lastIndex) {
                            HorizontalDivider(
                                color = MoyeotaColor.Hairline,
                                modifier = Modifier.padding(start = 66.dp, end = 20.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }

        MoyeotaBottomBar(selected = MoyeotaTab.CHAT, onSelect = onTabSelect)
    }
}

/**
 * 목록 부제 — **마지막 메시지 미리보기**가 있으면 그것, 없으면 경로(+종료 여부).
 * 위치 공유 메시지는 본문이 좌표라 사람 말로 바꾼다. 삭제된 메시지는 서버가 이미 「삭제된 메시지입니다」로 준다.
 */
internal fun chatRoomSubtitle(item: ChatRoomListItem): String {
    val room = item.room.room
    val last = item.room.membership.lastMessage
    if (last != null) {
        val preview = when (last.type) {
            ChatMessageType.LOCATION -> "📍 위치를 공유했어요"
            ChatMessageType.TEXT -> last.content
        }
        return if (room.status == ChatRoomStatus.ACTIVE) preview else "종료 · $preview"
    }
    val routeLabel = "${room.departure} → ${room.destination}"
    return when {
        // 참여자를 못 받은 방은 제목이 경로라, 부제까지 경로면 같은 문장이 두 번이다 — 방 상태를 쓴다
        item.peerTitle == null -> if (room.status == ChatRoomStatus.ACTIVE) "진행 중" else "종료된 방"
        room.status == ChatRoomStatus.ACTIVE -> routeLabel
        else -> "종료 · $routeLabel"
    }
}

@Composable
private fun ChatRoomRow(item: ChatRoomListItem, onClick: () -> Unit) {
    val room = item.room.room
    val membership = item.room.membership
    val title = item.peerTitle ?: "${room.departure} → ${room.destination}"
    val subtitle = chatRoomSubtitle(item)
    val timeLabel = membership.lastMessage?.createdAt?.toListTimeLabel()
    val unread = membership.hasUnread
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(size = 40.dp)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                // 안 읽은 방은 미리보기를 진하게 — 배지와 함께 「새 대화」를 두 신호로 알린다
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                color = if (unread) MoyeotaColor.InkPrimary else GrayMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (timeLabel != null) {
                Text(
                    text = timeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                )
                Spacer(Modifier.size(6.dp))
            }
            // 남의 마지막 메시지가 읽음 커서 뒤에 있으면 안읽음 배지. 서버가 개수는 아직 안 줘 점으로만
            if (unread) {
                Box(Modifier.size(8.dp).background(MoyeotaColor.Primary500, CircleShape))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatListScreenPreview() {
    ChatListScreen()
}
