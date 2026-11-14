package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.moyeota.core.designsystem.component.BellOffIcon
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.theme.modalShadow
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.ChatException
import com.moyeota.domain.model.PartyLeaveException
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.isHostedByMe

/**
 * 채팅 목록에서 길게 누른 방의 「나가기」가 **무엇에서** 나가는가.
 *
 * 서버에는 나가는 길이 둘이다 — 채팅방만 나가는 `DELETE /chat-rooms/{id}/users` 와 동승에서 나가는
 * `DELETE /matching/leave/{partyId}`. 진행 중인 방에서 채팅방만 나가면 동승은 남은 채 대화로 돌아올 길이
 * 없다(채팅방 안 메뉴가 그 방에서 「나가기」를 감추는 이유다). 그래서 진행 중인 방의 「나가기」는 **동승에서**
 * 나가고, 채팅방은 서버가 함께 정리한다(`PartyMemberLeftEvent` → 채팅방 멤버 정리).
 *
 * 채팅방 상태로는 가를 수 없다 — 서버가 끝난 방의 채팅방을 닫지 않아(백엔드 #335) 목록의 `status` 가
 * 늘 `ACTIVE` 다. 그래서 방 상세를 읽어 판정한다([chatLeavePlan]).
 */
sealed interface ChatLeavePlan {
    /** 끝났거나 취소된 방, 이미 내가 빠진 방, 찾을 수 없는 방 — 채팅방만 나가 목록에서 치운다 */
    data class ChatOnly(val chatRoomId: Long) : ChatLeavePlan

    /**
     * 진행 중이고 도착 전 — 동승에서 나간다(develop: 도착을 누르기 전이면 어떤 방이든 나갈 수 있다).
     *
     * @param alone 나 혼자인 방 — 나가는 순간 방이 닫힌다(`CANCELED`)
     * @param cancelsMeeting 약속이 정해진 실시간 방에 둘뿐 — 남은 한 명만 남아 서버가 방을 취소한다
     * @param capacity 정원 — 21 의 「그만 찾기」와 같은 `MatchCanceled` 이벤트를 남기려고 들고 간다
     * @param memberCount 나가기 직전 인원(나 포함) — 같은 이유
     */
    data class LeaveParty(
        val chatRoomId: Long,
        val partyId: Long,
        val isHost: Boolean,
        val alone: Boolean,
        val cancelsMeeting: Boolean,
        val capacity: Int,
        val memberCount: Int,
    ) : ChatLeavePlan

    /** 도착을 누른 뒤 — 서버가 409 `ARRIVED_MEMBER_CANNOT_LEAVE` 로 막는다. 누르기 전에 말한다 */
    data object Blocked : ChatLeavePlan
}

/**
 * 방 상세([ride])로 「나가기」의 뜻을 정한다. 방을 못 찾았으면([ride] null) 채팅방만 정리한다 —
 * 방이 없는데 채팅방이 목록에 남아 있다면 치우는 것 말고 할 일이 없다.
 */
internal fun chatLeavePlan(chatRoomId: Long, ride: Ride?): ChatLeavePlan {
    ride ?: return ChatLeavePlan.ChatOnly(chatRoomId)
    if (ride.status == RideStatus.COMPLETED || ride.status == RideStatus.CANCELED) {
        return ChatLeavePlan.ChatOnly(chatRoomId)
    }
    val me = ride.members.firstOrNull { it.isMe } ?: return ChatLeavePlan.ChatOnly(chatRoomId)
    if (me.hasArrived) return ChatLeavePlan.Blocked
    val partyId = ride.id.toLongOrNull() ?: return ChatLeavePlan.ChatOnly(chatRoomId)
    return ChatLeavePlan.LeaveParty(
        chatRoomId = chatRoomId,
        partyId = partyId,
        isHost = ride.isHostedByMe,
        alone = ride.members.size == 1,
        cancelsMeeting = ride.status == RideStatus.MATCHED && ride.members.size == 2,
        capacity = ride.capacity,
        memberCount = ride.members.size,
    )
}

/** 나가기 확인창 문구. [dismissText] 가 null 이면 버튼이 하나다(막힌 경우) */
internal data class ChatLeaveDialogCopy(
    val title: String,
    val body: String,
    val confirmText: String,
    val dismissText: String?,
    val destructive: Boolean,
)

internal fun chatLeaveDialog(plan: ChatLeavePlan): ChatLeaveDialogCopy = when (plan) {
    is ChatLeavePlan.ChatOnly -> ChatLeaveDialogCopy(
        title = "채팅방을 나갈까요?",
        body = "나가면 이 대화가 목록에서 사라지고 다시 들어올 수 없어요",
        confirmText = "나가기",
        dismissText = "취소",
        destructive = true,
    )
    is ChatLeavePlan.LeaveParty -> ChatLeaveDialogCopy(
        title = "방에서 나갈까요?",
        body = when {
            plan.alone -> "혼자 있는 방이라 나가면 방이 닫혀요"
            plan.cancelsMeeting -> "$LEAVE_PARTY_BODY\n남은 분 혼자가 되어 약속이 취소돼요"
            plan.isHost -> "$LEAVE_PARTY_BODY\n방장은 남은 분 중 먼저 들어온 분에게 넘어가요"
            else -> LEAVE_PARTY_BODY
        },
        confirmText = "나가기",
        dismissText = "취소",
        destructive = true,
    )
    ChatLeavePlan.Blocked -> ChatLeaveDialogCopy(
        title = "지금은 나갈 수 없어요",
        body = ARRIVED_CANNOT_LEAVE,
        confirmText = "확인",
        dismissText = null,
        destructive = false,
    )
}

private const val LEAVE_PARTY_BODY = "동승에서 빠지고 이 채팅방도 함께 사라져요"
private const val ARRIVED_CANNOT_LEAVE = "출발지에 도착했다고 누른 뒤에는 방에서 나갈 수 없어요"

/** 길게 누르기 메뉴의 알림 항목 — 지금 상태의 **반대**를 적는다 */
internal fun chatMuteActionLabel(muted: Boolean): String = if (muted) "알림 켜기" else "알림 끄기"

/**
 * 나가기 실패 → 사용자 문구. 서버 코드 문자열은 develop 기준이다(`ARRIVED_MEMBER_CANNOT_LEAVE`·`PARTY_CLOSED`).
 * 판정과 요청 사이에 상태가 바뀐 경우라 목록을 다시 읽게 안내한다.
 */
internal fun chatListLeaveFailureMessage(e: Throwable): String {
    val code = (e as? PartyLeaveException)?.code
    return when (code) {
        "ARRIVED_MEMBER_CANNOT_LEAVE" -> ARRIVED_CANNOT_LEAVE
        "PARTY_CLOSED" -> "이미 끝난 방이에요. 목록을 새로 불러와 주세요"
        else -> "나가지 못했어요. 잠시 뒤 다시 시도해 주세요"
    }
}

/** 채팅방 나가기의 403(참여자 아님)은 **이미 나간 것**이다 — 사용자가 할 일이 같아 성공으로 친다 */
internal fun isAlreadyOutOfChat(e: Throwable): Boolean = (e as? ChatException)?.isNotParticipant == true

/**
 * 채팅 목록 행을 길게 눌렀을 때 뜨는 메뉴 — 알림 끄기/켜기 · 나가기 · 취소.
 *
 * 모양은 24 채팅방의 메시지 동작 시트(`MessageActionSheet`)를 따른다 — 이 앱엔 시트 컴포넌트가 없고,
 * 같은 「길게 누르기 → 아래 카드」를 두 모양으로 만들면 둘 다 흔들린다. 맨 위에 방 이름을 적는다 —
 * 무엇을 눌렀는지 보여야 엉뚱한 방에서 나가지 않는다.
 *
 * @param leaveChecking 방 상태를 아직 읽는 중이고 사용자가 「나가기」를 이미 눌렀다 — 판정이 끝나면 확인창으로 넘어간다
 */
@Composable
internal fun ChatRoomActionSheet(
    title: String,
    muted: Boolean,
    leaveChecking: Boolean,
    onToggleMuted: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MoyeotaColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Lg)
                .fillMaxWidth()
                .modalShadow(MoyeotaShape.Dialog)
                .clip(MoyeotaShape.Dialog)
                .background(MoyeotaColor.SurfaceCanvas)
                // 시트 안을 눌러도 닫히지 않게 한다 — 메시지 동작 시트와 같은 처리
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
        ) {
            Text(
                text = title,
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = MoyeotaSpacing.Md,
                    end = MoyeotaSpacing.Md,
                    top = MoyeotaSpacing.Md,
                    bottom = MoyeotaSpacing.Xs,
                ),
            )
            ActionRow(text = chatMuteActionLabel(muted), color = MoyeotaColor.InkPrimary, onClick = onToggleMuted)
            HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = MoyeotaSpacing.Md))
            ActionRow(
                text = if (leaveChecking) "방 상태를 확인하고 있어요" else "나가기",
                color = if (leaveChecking) MoyeotaColor.TextMute else MoyeotaColor.Danger500,
                enabled = !leaveChecking,
                onClick = onLeave,
            )
            HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = MoyeotaSpacing.Md))
            ActionRow(text = "취소", color = MoyeotaColor.TextBody, onClick = onDismiss)
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = MoyeotaType.BodyMd,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = MoyeotaSpacing.Md, vertical = MoyeotaSpacing.Md),
    )
}

/** 목록 행 제목 오른쪽의 「알림 꺼짐」 표시 — 켜진 방은 아무것도 붙이지 않는다. 스크린리더에도 읽힌다 */
@Composable
internal fun MutedMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "알림 꺼짐" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(MoyeotaSpacing.Xs))
        // 자리표시 회색(2.6:1)이 아니라 TextMute(4.8:1) — 장식이 아니라 상태를 알리는 표시라 3:1 을 넘겨야 한다
        BellOffIcon(size = MoyeotaIconSize.Sm, tint = MoyeotaColor.TextMute)
    }
}
