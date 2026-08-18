package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

// 와이어프레임 색 (core token 미정의 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val BannerBg = Color(0xFFFDF6E3)
private val BannerStrong = Color(0xFF8A5806)
private val BannerSub = Color(0xFF8A6414)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val GraySlate = Color(0xFF4B5563)
private val SystemChipBg = Color(0xFFE9EDF3)
private val InputPillBg = Color(0xFFF1F3F7)
private val BubbleShadow = Color(0x1A1B2A4A)

// 채팅 메시지 로컬 모델 (더미 대화 + 로컬 전송)
data class ChatMessage(
    val text: String,
    val isMine: Boolean,
    val senderName: String? = null,
    val timeLabel: String? = null,
    val meta: String? = null, // 내 메시지 좌측 메타 (예: "읽음 2 · 6:41")
    val isLocationShare: Boolean = false, // 위치 공유 안내 말풍선
)

/**
 * 24 · 채팅 [S16] — 24a 메뉴 오버레이 · 24b 공유 시트 포함
 *
 * 이동(디스크립션):
 * - 뒤로 → 22 탑승 상세 (onBack)
 * - 「⋮」 → 24a 메뉴 열림 (내부 상태)
 * - 「＋」 → 24b 공유 시트 열림 (내부 상태)
 * - 상단 「실시간 위치 공유 중」 배너 탭 → 26 운행 중 (onOpenRideOngoing)
 * - 24a 「채팅방 알림 끄기」 → 24 (알림 off 로컬 적용)
 * - 24a 「채팅방 나가기」 → 14 홈 (onLeaveChat) — 진행 중 탑승이 있으면 재확인 다이얼로그
 * - 24b 「실시간 위치 공유 시작」 → 26 운행 중 (onStartLocationShare)
 * - 하단탭 → 14/17/35 (onTabSelect)
 * - [미연결] 없음 (헤더 검색 아이콘은 스펙 미정 — 무동작)
 *
 * 유효값: 메시지 1~500자, 공백만 입력 시 전송 비활성.
 */
@Composable
fun ChatScreen(
    roomTitle: String = "서면역 동승",
    roomSubtitle: String = "3명 · 오후 6:45 출발",
    hasOngoingRide: Boolean = true,
    onBack: () -> Unit = {},
    onOpenRideOngoing: () -> Unit = {},
    onStartLocationShare: () -> Unit = {},
    onLeaveChat: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    val messages = remember {
        listOf(
            ChatMessage(text = "정문 앞 편의점에 있어요", isMine = false, senderName = "김OO", timeLabel = "오후 6:39"),
            ChatMessage(text = "2분 뒤 도착합니다", isMine = true, meta = "읽음 2 · 6:41"),
            ChatMessage(text = "탭하면 지도에서 함께 봐요", isMine = false, senderName = "이OO", timeLabel = "오후 6:42", isLocationShare = true),
            ChatMessage(text = "확인했어요", isMine = true),
        ).toMutableStateList()
    }
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) } // 24a
    var shareSheetOpen by remember { mutableStateOf(false) } // 24b
    var muted by remember { mutableStateOf(false) } // 알림 off
    var leaveConfirmOpen by remember { mutableStateOf(false) }

    val sendEnabled = input.isNotBlank() && input.length <= 500

    Box(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 헤더 (흰 배경)
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                StatusBarMock()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onBack() },
                    ) {
                        BackArrowIcon(modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = roomTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                        )
                        Text(
                            text = if (muted) "$roomSubtitle · 알림 꺼짐" else roomSubtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SearchBoxIcon() // 스펙 미정 — 무동작
                    Spacer(Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        KebabIcon()
                    }
                }
            }

            // 「실시간 위치 공유 중」 배너 — 탭 → 26 운행 중
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(BannerBg)
                    .clickable { onOpenRideOngoing() }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(MoyeotaColor.Waiting500, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(text = "실시간 위치 공유 중", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BannerStrong)
                Spacer(Modifier.width(10.dp))
                Text(text = "동승자에게 내 위치가 보여요", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BannerSub)
                Spacer(Modifier.weight(1f))
                TogglePill(on = true, onColor = MoyeotaColor.Primary500)
            }

            // 메시지 리스트 + (24a/24b 오버레이 영역)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    // 시스템 칩 — 매칭 완료
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .background(SystemChipBg, RoundedCornerShape(13.dp))
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                        ) {
                            Text(text = "매칭 완료 · 오후 6:38", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextMute)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    messages.forEachIndexed { index, message ->
                        MessageRow(message = message)
                        if (index != messages.lastIndex) Spacer(Modifier.height(16.dp))
                    }
                }

                // 24a · 메뉴 오버레이 — 배경 탭 → 24
                if (menuOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { menuOpen = false },
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 51.dp, end = 16.dp)
                                .width(164.dp)
                                .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = BubbleShadow)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MoyeotaColor.SurfaceCanvas),
                        ) {
                            Text(
                                text = "채팅방 알림 끄기",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        muted = true // 알림 off 적용 후 24 복귀
                                        menuOpen = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                            HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
                            Text(
                                text = "채팅방 나가기",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        menuOpen = false
                                        // 탑승 이탈과 별개 — 진행 중 탑승이 있으면 재확인 다이얼로그
                                        if (hasOngoingRide) leaveConfirmOpen = true else onLeaveChat()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                        }
                    }
                }

                // 24b · 공유 시트 — 배경 탭 → 24
                if (shareSheetOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { shareSheetOpen = false },
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = BubbleShadow)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MoyeotaColor.SurfaceCanvas)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { /* 카드 내부 탭은 닫지 않음 */ },
                        ) {
                            Text(
                                text = "공유하기",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
                            )
                            HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        shareSheetOpen = false
                                        onStartLocationShare() // → 26 운행 중 (공유 on)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    text = "실시간 위치 공유 시작",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.InkPrimary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "내 위치를 동승자에게 실시간으로 보여줘요",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GraySlate,
                                )
                            }
                        }
                    }
                }
            }

            // 입력 바
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                HorizontalDivider(color = MoyeotaColor.Hairline)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 「＋」 → 24b 공유 시트
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { shareSheetOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        PlusIcon()
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(InputPillBg, RoundedCornerShape(24.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = { if (it.length <= 500) input = it }, // 1~500자
                            textStyle = MoyeotaType.BodyMd.copy(color = MoyeotaColor.InkPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (input.isEmpty()) {
                            Text(text = "메시지 보내기", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GrayAsh)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // 전송 — 공백만 입력 시 비활성, 전송하면 리스트에 추가
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (sendEnabled) MoyeotaColor.Primary500 else MoyeotaColor.TextAsh,
                                CircleShape,
                            )
                            .clickable(enabled = sendEnabled) {
                                messages.add(ChatMessage(text = input.trim(), isMine = true))
                                input = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        SendArrowIcon()
                    }
                }
            }

            // 24는 하단탭 노출 화면 (공통 규칙)
            MoyeotaBottomBar(selected = MoyeotaTab.CHAT, onSelect = onTabSelect)
            HomeIndicator()
        }

        // 채팅방 나가기 재확인 다이얼로그 (진행 중 탑승 존재 시)
        if (leaveConfirmOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MoyeotaColor.Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { leaveConfirmOpen = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = BubbleShadow)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MoyeotaColor.SurfaceCanvas)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(24.dp),
                ) {
                    Text(
                        text = "채팅방을 나갈까요?",
                        style = MoyeotaType.HeadingMd,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "채팅방 나가기는 탑승 이탈과 별개예요. 진행 중인 탑승은 유지돼요.",
                        style = MoyeotaType.BodySm,
                        color = GraySlate,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(MoyeotaColor.SurfaceSoft, RoundedCornerShape(12.dp))
                                .clickable { leaveConfirmOpen = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "취소", style = MoyeotaType.ButtonMd, color = GraySlate)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(MoyeotaColor.Primary500, RoundedCornerShape(12.dp))
                                .clickable {
                                    leaveConfirmOpen = false
                                    onLeaveChat() // → 14 홈
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "나가기", style = MoyeotaType.ButtonMd, color = MoyeotaColor.TextOnDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    if (message.isMine) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End,
        ) {
            if (message.meta != null) {
                Text(text = message.meta, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayAsh)
                Spacer(Modifier.width(8.dp))
            }
            // 내 말풍선 — Primary500 / 흰 글씨 (토큰 chat-bubble)
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .background(
                        MoyeotaColor.Primary500,
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(text = message.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.TextOnDark)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AvatarCircle(size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = message.senderName.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    // 상대 말풍선 — 와이어프레임: 흰 카드 + 그림자 (토큰 chat-bubble)
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .shadow(4.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp), spotColor = BubbleShadow)
                            .background(
                                MoyeotaColor.SurfaceCard,
                                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        if (message.isLocationShare) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LocationPinIcon()
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "실시간 위치 공유 중",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MoyeotaColor.Primary600,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(text = message.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = GraySlate)
                            }
                        } else {
                            Text(text = message.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.InkPrimary)
                        }
                    }
                    if (message.timeLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(text = message.timeLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayAsh)
                    }
                }
            }
        }
    }
}

// 토글 (와이어프레임 pill 46x26 + 흰 노브)
@Composable
private fun TogglePill(on: Boolean, onColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .background(if (on) onColor else MoyeotaColor.TextAsh, RoundedCornerShape(13.dp)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .background(MoyeotaColor.SurfaceCanvas, CircleShape),
        )
    }
}

// 홈 인디케이터 (와이어프레임 하단 검은 바)
@Composable
private fun HomeIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 135.dp, height = 5.dp).background(MoyeotaColor.InkPrimary, CircleShape))
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun SearchBoxIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = Color(0xFFD8DEE8),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(
            color = GraySlate,
            radius = w * 0.16f,
            center = Offset(w * 0.44f, w * 0.44f),
            style = Stroke(stroke),
        )
        drawLine(GraySlate, Offset(w * 0.58f, w * 0.58f), Offset(w * 0.72f, w * 0.72f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun KebabIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val r = 1.7.dp.toPx()
        drawCircle(MoyeotaColor.InkPrimary, r, Offset(w / 2f, h * 0.2f))
        drawCircle(MoyeotaColor.InkPrimary, r, Offset(w / 2f, h * 0.5f))
        drawCircle(MoyeotaColor.InkPrimary, r, Offset(w / 2f, h * 0.8f))
    }
}

@Composable
private fun PlusIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(GraySlate, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), stroke, StrokeCap.Round)
        drawLine(GraySlate, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SendArrowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(Color.White, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.15f), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.2f, h * 0.45f), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.8f, h * 0.45f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun LocationPinIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawCircle(
            color = MoyeotaColor.Primary600,
            radius = w * 0.27f,
            center = Offset(w * 0.5f, h * 0.38f),
            style = Stroke(stroke),
        )
        drawLine(MoyeotaColor.Primary600, Offset(w * 0.3f, h * 0.55f), Offset(w * 0.5f, h * 0.9f), stroke, StrokeCap.Round)
        drawLine(MoyeotaColor.Primary600, Offset(w * 0.7f, h * 0.55f), Offset(w * 0.5f, h * 0.9f), stroke, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatScreenPreview() {
    ChatScreen()
}
