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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import com.moyeota.presentation.core.ErrorBox
import com.moyeota.presentation.core.LoadingBox
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.StatusBarSpacer
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

// 채팅 메시지 표시 모델. 서버 도메인 모델(domain.model.ChatMessage)은 Route 에서 이 형태로 옮긴다.
data class ChatUiMessage(
    val text: String,
    val isMine: Boolean,
    val senderName: String? = null,
    val timeLabel: String? = null,
    val meta: String? = null, // 내 메시지 좌측 메타 (예: "읽음 2 · 6:41")
    val isLocationShare: Boolean = false, // 위치 공유 안내 말풍선
)

/**
 * 검색 바·결과 목록 상태. [results] 가 null 이면 아직 묻지 않았다(검색어가 짧다) — 빈 목록(결과 없음)과 구분한다.
 * 기본값은 「닫힘」이라 검색을 안 쓰는 Preview 는 이 인자를 넘기지 않아도 된다.
 */
data class ChatSearchUi(
    val open: Boolean = false,
    val query: String = "",
    val results: List<ChatUiMessage>? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
)

// 서버 연동 전 미리보기용 더미 대화
private val DummyMessages = listOf(
    ChatUiMessage(text = "정문 앞 편의점에 있어요", isMine = false, senderName = "김OO", timeLabel = "오후 6:39"),
    ChatUiMessage(text = "2분 뒤 도착합니다", isMine = true, meta = "읽음 2 · 6:41"),
    ChatUiMessage(text = "탭하면 지도에서 함께 봐요", isMine = false, senderName = "이OO", timeLabel = "오후 6:42", isLocationShare = true),
    ChatUiMessage(text = "확인했어요", isMine = true),
)

/**
 * 24 · 채팅 [S16] — 24a 메뉴 오버레이 · 24b 공유 시트 포함
 *
 * 이동(디스크립션):
 * - 뒤로 → 22 탑승 상세 (onBack)
 * - 「⋮」 → 24a 메뉴 열림 (내부 상태)
 * - 「＋」 → 24b 공유 시트 열림 (내부 상태)
 * - 상단 「매칭 화면으로 →」 배너 탭 → 21/25/26 진행 단계 (onOpenMatching — 진행 중인 방의 채팅방일 때만 보인다)
 * - 상단 「실시간 위치 공유 중」 배너 탭 → 26 운행 중 (onOpenRideOngoing)
 * - 24a 「채팅방 알림 끄기/켜기」 → 24 (서버 음소거 토글 — onToggleMute, 부제에 「알림 꺼짐」)
 * - 24a 「채팅방 나가기」 → 14 홈 (onLeaveChat) — 진행 중 탑승이 있으면 재확인 다이얼로그
 * - 24b 「실시간 위치 공유 시작」 → 26 운행 중 (onStartLocationShare)
 * - 하단탭 → 14/17/35 (onTabSelect)
 * - 헤더 검색 아이콘 → 상단이 검색 바로 바뀌고 본문이 결과 목록이 된다(onOpenSearch / onCloseSearch).
 *   검색어 2자 이상부터 서버(GET …/messages/search)에 묻고, 결과는 최신순 · 「이전 결과 더 보기」로 페이징.
 *   결과를 눌러 그 메시지로 점프하는 건 아직 없다 — 서버가 위치(오프셋)를 주지 않아 커서 페이징으로는
 *   해당 메시지까지 몇 페이지를 더 받아야 하는지 모른다.
 *
 * 유효값: 메시지 1~500자, 공백만 입력 시 전송 비활성.
 */
// 파라미터 기본값(방 제목·부제·더미 대화)은 **Preview 전용**이다.
// 실제 진입(ChatRoute)은 서버 ChatRoom 의 출발지 → 목적지와 조회한 메시지를 항상 넘긴다.
@Composable
fun ChatScreen(
    roomTitle: String = "서면역 동승",
    roomSubtitle: String = "3명 · 오후 6:45 출발",
    hasOngoingRide: Boolean = true,
    messages: List<ChatUiMessage> = DummyMessages,
    input: String = "",
    sending: Boolean = false,
    errorMessage: String? = null,
    onInputChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenRideOngoing: () -> Unit = {},
    /**
     * 이 방의 파티가 아직 진행 중일 때 매칭 단계 화면으로 되돌아가는 길. **null 이면 그리지 않는다** —
     * 끝난 방의 채팅에서 「매칭 화면으로」를 눌러 봐야 갈 곳이 없다.
     *
     * 21·25·26 에서 「채팅 열기」로 들어온 사용자에게는 뒤로가기가 이미 복귀 경로지만,
     * 채팅 **탭 목록**에서 들어온 사용자에게는 이 버튼이 유일한 길이다.
     */
    onOpenMatching: (() -> Unit)? = null,
    onStartLocationShare: () -> Unit = {},
    onLeaveChat: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
    /** 이 방의 푸시 알림 음소거 여부(서버 값). 24a 메뉴의 「알림 끄기/켜기」와 부제의 「알림 꺼짐」이 따른다 */
    muted: Boolean = false,
    onToggleMute: () -> Unit = {},
    /** 24a 「채팅방 나가기」를 보여 줄지. 진행 중인 내 방의 채팅방이면 false(되돌아올 길이 없다) */
    canLeave: Boolean = true,
    search: ChatSearchUi = ChatSearchUi(),
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchRetry: () -> Unit = {},
    onSearchLoadMore: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) } // 24a
    var shareSheetOpen by remember { mutableStateOf(false) } // 24b
    var leaveConfirmOpen by remember { mutableStateOf(false) }

    val sendEnabled = input.isNotBlank() && input.length <= 500 && !sending

    // 검색 중 뒤로가기는 방을 나가는 게 아니라 검색을 닫는다 — 카카오톡·기본 메시지 앱과 같은 기대
    BackHandler(enabled = search.open) { onCloseSearch() }

    // 키보드가 올라온 만큼 화면을 줄인다. enableEdgeToEdge() 로 창이 IME 에 맞춰 리사이즈되지 않아
    // imePadding 이 없으면 입력 바와 하단탭이 키보드 뒤로 숨는다(실기 QA).
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeVisible = imeBottomPx > 0
    // 메시지 리스트는 항상 **마지막 대화**를 보여 준다 — 새 메시지·키보드 등장으로 영역이 줄 때 모두
    val listScroll = rememberScrollState()
    LaunchedEffect(messages.size, imeVisible, listScroll.maxValue) {
        listScroll.animateScrollTo(listScroll.maxValue)
    }

    Box(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // 헤더 (흰 배경). 검색을 열면 제목 줄이 통째로 검색 바로 바뀐다 — 제목 아래에 한 줄 더 얹는
            // 것보다 대화가 보이는 높이를 안 뺏고, 「지금 검색 중」이 분명하다.
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                StatusBarSpacer()
                if (search.open) {
                    SearchHeader(
                        query = search.query,
                        onQueryChange = onSearchQueryChange,
                        onClose = onCloseSearch,
                    )
                } else Row(
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
                    // 부제가 경로(역지오코딩된 전체 주소)로 바뀌면서 길어졌다. weight 로 남는 폭을
                    // 다 쓰되 **말줄임**한다 — 예전처럼 폭을 안 잡으면 두 줄로 흘러 우측 아이콘을 덮는다.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = roomTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoyeotaColor.InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (muted) "$roomSubtitle · 알림 꺼짐" else roomSubtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenSearch() },
                    ) {
                        SearchBoxIcon()
                    }
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

            // 검색 중에는 배너·대화·입력 바 대신 결과 목록만. 닫으면 원래 화면으로 돌아온다.
            if (search.open) {
                SearchResults(
                    search = search,
                    onRetry = onSearchRetry,
                    onLoadMore = onSearchLoadMore,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            // 「매칭 화면으로 →」 — 진행 중인 방의 채팅방에서만 뜬다
            if (!search.open && onOpenMatching != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(MoyeotaColor.Primary50)
                        .clickable { onOpenMatching() }
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 왼쪽 문구만 줄어든다 — weight 없이 두면 좁은 폰에서 오른쪽 링크가 밀려 잘린다
                    Text(
                        text = "진행 중인 탑승이 있어요",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "매칭 화면으로 →",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary600,
                        maxLines = 1,
                    )
                }
            }

            // 「실시간 위치 공유 중」 배너 — 탭 → 26 운행 중.
            // 문구 두 개가 weight 없이 나란히 있으면 좁은 폰에서 Row 가 넘쳐 **마지막 자식인 토글이
            // 0dp 로 찌그러진다**(실기 QA: 토글이 작게 보이는 문제). 문구 묶음만 남는 폭을 쓰고 줄어들게 한다.
            if (!search.open) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(BannerBg)
                    .clickable { onOpenRideOngoing() }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(MoyeotaColor.Waiting500, CircleShape))
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "실시간 위치 공유 중",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BannerStrong,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    // 부제는 자리가 모자라면 말줄임 — 제목과 토글은 항상 온전히 보인다
                    Text(
                        text = "동승자에게 내 위치가 보여요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BannerSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.width(10.dp))
                TogglePill(on = true, onColor = MoyeotaColor.Primary500)
            }

            // 메시지 리스트 + (24a/24b 오버레이 영역)
            if (!search.open) Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(listScroll)
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
                                // 서버 음소거 토글 — 눌러서 끄고, 다시 눌러 켠다(예전엔 끄기만 있는 로컬 상태였다)
                                text = if (muted) "채팅방 알림 켜기" else "채팅방 알림 끄기",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onToggleMute()
                                        menuOpen = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                            if (canLeave) {
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

            // 입력 바 — 검색 중엔 숨긴다(결과 목록 위에서 전송할 일이 없고, 키보드는 검색 바가 쓴다)
            if (!search.open) Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                HorizontalDivider(color = MoyeotaColor.Hairline)
                // 전송·수신 실패 안내 — 입력한 내용은 지우지 않는다
                if (errorMessage != null) {
                    NoticeBanner(
                        kind = NoticeKind.ERROR,
                        text = errorMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
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
                            onValueChange = { if (it.length <= 500) onInputChange(it) }, // 서버 검증 1~1000자, UI 는 500자
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
                            .clickable(enabled = sendEnabled) { onSend() },
                        contentAlignment = Alignment.Center,
                    ) {
                        SendArrowIcon()
                    }
                }
            }

            // 24는 하단탭 노출 화면 (공통 규칙). 다만 키보드가 올라오면 숨긴다 —
            // 키보드 바로 위에 탭바가 얹히면 대화가 보이는 높이만 그만큼 줄어든다.
            if (!imeVisible) {
                MoyeotaBottomBar(selected = MoyeotaTab.CHAT, onSelect = onTabSelect)
            }
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
private fun MessageRow(message: ChatUiMessage) {
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

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

/**
 * 검색 모드 헤더 — 뒤로가기(닫기) · 입력 · 지우기. 열리자마자 포커스를 잡아 키보드를 올린다.
 * 검색은 입력하는 동안 자동으로 나가므로(ViewModel 디바운스) IME 「검색」 버튼은 키보드만 내린다.
 */
@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClose() },
        ) {
            BackArrowIcon(modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(InputPillBg, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { if (it.length <= 100) onQueryChange(it) }, // 서버 한도 1000자 — 검색어로는 100자면 넉넉하다
                textStyle = MoyeotaType.BodyMd.copy(color = MoyeotaColor.InkPrimary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (query.isEmpty()) {
                Text(text = "대화 내용 검색", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GrayAsh)
            }
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "지우기",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.Primary600,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onQueryChange("") },
            )
        }
    }
}

/**
 * 결과 목록. 네 가지 상태를 문구로 구분한다 — 안 물어봄(짧은 검색어) / 찾는 중 / 없음 / 실패.
 * 결과는 서버 순서 그대로(최신 → 과거). 말풍선이 아니라 한 줄 카드다 — 누가·언제·무엇을 한눈에 훑는 용도라
 * 대화 레이아웃(좌우 정렬)을 그대로 쓰면 오히려 읽기 어렵다.
 */
@Composable
private fun SearchResults(
    search: ChatSearchUi,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = search.results
    val keyword = search.query.trim()
    Box(modifier = modifier) {
        when {
            search.errorMessage != null && results.isNullOrEmpty() ->
                ErrorBox(message = search.errorMessage, onRetry = onRetry)
            search.loading && results == null -> LoadingBox()
            keyword.length < SEARCH_MIN_LENGTH -> SearchHint("검색어를 ${SEARCH_MIN_LENGTH}글자 이상 입력해 주세요")
            results != null && results.isEmpty() -> SearchHint("「$keyword」에 맞는 메시지가 없어요")
            results != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "검색 결과 ${results.size}개${if (search.hasMore) " 이상" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.TextMute,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                results.forEach { message ->
                    SearchResultRow(message = message, keyword = keyword)
                    Spacer(Modifier.height(8.dp))
                }
                if (search.errorMessage != null) {
                    NoticeBanner(kind = NoticeKind.ERROR, text = search.errorMessage, modifier = Modifier.padding(vertical = 6.dp))
                }
                if (search.hasMore) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(12.dp))
                            .clickable(enabled = !search.loadingMore) { onLoadMore() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (search.loadingMore) "불러오는 중…" else "이전 결과 더 보기",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (search.loadingMore) GrayMute else MoyeotaColor.Primary600,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GrayMute)
    }
}

@Composable
private fun SearchResultRow(message: ChatUiMessage, keyword: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (message.isMine) "나" else (message.senderName ?: "동승자"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (message.isMine) MoyeotaColor.Primary600 else MoyeotaColor.TextBody,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            message.timeLabel?.let {
                Text(text = it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = GrayAsh)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = highlightKeyword(message.text, keyword),
            fontSize = 14.sp,
            color = MoyeotaColor.InkPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 본문에서 검색어와 겹치는 구간 전부를 굵게·파랗게. 대소문자를 가리지 않는다(서버 LIKE 도 그렇다) */
internal fun highlightKeyword(text: String, keyword: String): AnnotatedString = buildAnnotatedString {
    append(text)
    for (range in keywordRanges(text, keyword)) {
        addStyle(SpanStyle(color = MoyeotaColor.Primary600, fontWeight = FontWeight.Bold), range.first, range.last + 1)
    }
}

/** [text] 안에서 [keyword] 가 나오는 모든 [start, end) 구간. 겹치지 않게 앞에서부터 찾는다. 빈 검색어면 없음 */
internal fun keywordRanges(text: String, keyword: String): List<IntRange> {
    if (keyword.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val at = text.indexOf(keyword, from, ignoreCase = true)
        if (at < 0) break
        ranges += at until at + keyword.length
        from = at + keyword.length
    }
    return ranges
}

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
