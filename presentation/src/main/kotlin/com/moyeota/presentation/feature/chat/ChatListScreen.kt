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
 * 제목은 **참여자 닉네임**, 부제가 경로다([ChatRoomListItem.peerTitle] · `chatRoomPeerTitle`).
 * 정렬은 Route 가 이미 마친 상태로 넘어온다(`chatRoomSortKey` — 지금은 방 id 내림차순).
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

@Composable
private fun ChatRoomRow(item: ChatRoomListItem, onClick: () -> Unit) {
    val room = item.room.room
    val routeLabel = "${room.departure} → ${room.destination}"
    // 참여자를 못 받은 방은 예전 이름(경로)으로 떨어진다 — 그때는 부제가 방 상태다
    // (제목과 부제에 같은 문장을 두 번 적지 않는다).
    val title = item.peerTitle ?: routeLabel
    val subtitle = when {
        item.peerTitle == null -> if (room.status == ChatRoomStatus.ACTIVE) "진행 중" else "종료된 방"
        room.status == ChatRoomStatus.ACTIVE -> routeLabel
        else -> "종료 · $routeLabel"
    }
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
                // 서버 목록은 마지막 메시지를 주지 않는다 — 경로(+종료 여부)가 여기 들어갈 수 있는 전부다
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 읽음 커서가 없으면 아직 한 번도 안 읽은 방
        if (item.room.membership.lastReadMessageId == null) {
            Box(Modifier.size(8.dp).background(MoyeotaColor.Primary500, CircleShape))
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatListScreenPreview() {
    ChatListScreen()
}
