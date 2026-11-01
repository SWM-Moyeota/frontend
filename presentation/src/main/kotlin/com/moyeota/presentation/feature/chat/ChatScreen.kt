package com.moyeota.presentation.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.moyeota.domain.model.ChatReportReason

// 와이어프레임 색 (core token 미정의 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val GraySlate = Color(0xFF4B5563)
private val InputPillBg = Color(0xFFF1F3F7)
private val BubbleShadow = Color(0x1A1B2A4A)

/**
 * 「바닥에 붙어 있다」고 보는 거리. 이 안에 있으면 새 메시지가 올 때 따라 내려가고, 그보다 위로
 * 올려 읽는 중이면 자동 스크롤을 하지 않는다.
 *
 * 120dp 로 둔 근거: 말풍선 한 칸이 대략 44dp + 간격 16dp(= 60dp)라 **두 줄쯤**이다. 1dp 로 바닥을
 * 엄격히 요구하면 애니메이션이 1px 못 미친 순간이나 손가락이 살짝 스친 뒤에 따라가기가 끊긴다.
 * 반대로 더 넓게 잡으면 「마지막 몇 개를 다시 읽는 중」인 사람까지 끌어내린다.
 */
private val AUTO_SCROLL_SLOP = 120.dp

/**
 * 입력창이 **받아 주는** 최대 글자 수 — 서버 한도([MAX_CONTENT_LENGTH], 1000자)보다 넉넉하다.
 *
 * **한도와 역할이 다르다.** 여기서 한도대로 자르면 한도를 넘겨 볼 수가 없어 「넘었다」는 사실을
 * 말해 줄 기회 자체가 사라진다. 예전엔 그보다 더해서 **500자**에서 잘랐고(서버 한도의 절반),
 * 그 바람에 ViewModel 의 1000자 검사와 안내가 둘 다 도달 불가능한 죽은 코드였다 — 사용자는
 * 500자에서 **이유도 모르고 조용히** 막혔다.
 *
 * 그래서 한도를 말하는 일은 [chatInputUi] 의 카운터가 맡고(입력하는 동안 계속 보인다), 이 값은
 * **붙여넣기 폭주만** 막는다 — 한 줄 입력창이 소설 한 편을 들고 있을 이유는 없다. 한도의 2배로
 * 둔 것도 그 때문이다: 넘긴 상태를 **보여 줄 수 있을 만큼** 넉넉하고, 수십만 자 붙여넣기는 막는다.
 */
private const val INPUT_HARD_LIMIT = 2_000

/**
 * 글자 수 카운터가 **보이기 시작하는** 길이 — 한도([MAX_CONTENT_LENGTH])에서 이만큼 남은 지점.
 *
 * 왜 「항상」이 아닌가: 평상시 채팅 한 줄은 수십 자다. 「12/1000자」가 늘 떠 있으면 이 화면에서
 * 가장 중요한 영역(말풍선)의 높이를 **상시로** 빼앗고, 아무 의미 없는 숫자가 글자마다 바뀌며
 * 눈에 들어온다. 이 코드베이스가 「후보 0건이면 섹션을 그리지 않는다」·「추정 실패면 칩을 감춘다」로
 * 일관되게 택한 쪽과 같은 판단이다.
 *
 * 왜 「넘은 순간」도 아닌가: 1001자에서야 나타나면 **버튼이 잠기는 순간과 줄이 생기는 순간이
 * 같아진다.** 그러면 ① 경고가 아니라 사후 통보가 되고 ② 입력 바가 그 프레임에 한 줄만큼
 * 높아져서 **누르려던 전송 버튼이 위로 움직이며** 잠긴다. 한도보다 앞서 띄우면, 잠기는 순간에
 * 카운터는 **이미 화면에 있고** 색만 바뀐다 — 레이아웃은 그 순간 움직이지 않는다.
 *
 * 100자인 근거: 한 줄 입력창에 더 칠 수 있는 분량(두세 문장)이라 「곧 한도」를 알아차리고 줄일
 * 시간이 되고, 900자까지 치는 메시지는 사실상 붙여넣기이므로 평상시 시야에는 영영 안 나타난다.
 * (0자에서 2000자를 **한 번에** 붙여넣으면 등장과 잠김이 같은 프레임이다 — 그 경우만은 피할 수
 * 없고, 그때도 빨간 「2000/1000자」가 왜 잠겼는지를 말한다.)
 *
 * `internal` 인 이유는 하나뿐이다 — `ChatInputLimitTest` 가 **「카운터는 잠김보다 먼저 뜬다」**
 * (`COUNTER_VISIBLE_FROM < MAX_CONTENT_LENGTH`)를 이 상수로 못 박는다. 그 부등식이 깨지면 위의
 * ②(버튼이 움직이며 잠긴다)가 그대로 돌아온다. 화면 밖에서 쓰는 값은 아니다.
 */
internal const val COUNTER_VISIBLE_FROM = MAX_CONTENT_LENGTH - 100

/**
 * 입력창 한 상태에서 **파생되는 것 전부** — 카운터 문구와 전송 가능 여부.
 *
 * 둘을 **한 함수에서 함께** 뽑는 것이 요점이다: 길이를 두 번 재면 세는 기준이 갈리고, 그러면
 * 「999자인데 버튼이 잠김」·「넘었는데 버튼이 살아 있음」 같은 어긋남이 생긴다(실제로 과거에
 * 입력창 500자 컷과 ViewModel 1000자 검사가 갈려 안내가 죽은 코드였다).
 *
 * @param counter 카운터 문구. **null 이면 줄을 그리지 않는다**([COUNTER_VISIBLE_FROM])
 * @param overLimit 한도를 넘겼다 — 카운터를 강조하고 전송을 잠근 **그 이유**다
 * @param sendEnabled 전송 버튼 활성
 */
internal data class ChatInputUi(
    val counter: String?,
    val overLimit: Boolean,
    val sendEnabled: Boolean,
)

/**
 * 입력창 상태 → [ChatInputUi]. **길이는 `trim()` 뒤에 센다.**
 *
 * 그게 [ChatRoomViewModel.send] 가 보는 기준이기 때문이다 — 그쪽은 `text.trim()` 한 값으로
 * 빈 문자열과 한도를 판정하고 그 값을 서버로 보낸다. 화면이 trim 전 길이를 세면 공백을 섞어 둔
 * 사용자에게 **「999자인데 버튼이 잠김」**(화면은 1002, 서버로 갈 값은 999)이 보인다.
 *
 * 전송 잠금은 **카운터가 보이는 것에 대한 대가**로만 둔다: 버튼만 잠그면 예전 500자 컷 시절의
 * 「조용히 막힘」으로 반쯤 되돌아간다 — 누르지 않아도 이유가 보여야 한다. 그래서 잠금 조건
 * (`overLimit`)과 표시 조건이 같은 값에서 나오고, 넘긴 상태에서는 카운터가 **반드시** 보인다
 * ([COUNTER_VISIBLE_FROM] < [MAX_CONTENT_LENGTH] 이므로 자동으로 성립한다).
 *
 * 넘겼을 때 숫자만 빨갛게 바꾸지 않고 「줄여야 보낼 수 있어요」를 붙이는 이유: 회색 전송 버튼과
 * 빨간 숫자 사이의 인과를 사용자가 **추측하지 않아도** 되게 한다. 색만으로 말하면 색을 구분하기
 * 어려운 사용자에게는 아무 말도 하지 않은 것과 같다.
 *
 * [ChatRoomViewModel.send] 의 한도 검사는 **지우지 않는다** — 화면이 잠그더라도 ViewModel 은
 * 자기 입력을 자기가 검증해야 한다(테스트가 그 경로를 직접 부르고, 서버도 같은 한도로 거절한다).
 */
internal fun chatInputUi(input: String, sending: Boolean): ChatInputUi {
    val length = input.trim().length
    val overLimit = length > MAX_CONTENT_LENGTH
    return ChatInputUi(
        counter = when {
            length < COUNTER_VISIBLE_FROM -> null
            overLimit -> "$length/${MAX_CONTENT_LENGTH}자 — 줄여야 보낼 수 있어요"
            else -> "$length/${MAX_CONTENT_LENGTH}자"
        },
        overLimit = overLimit,
        // 공백만 입력했을 때의 비활성은 그대로다 — trim 뒤 0자면 보낼 내용이 없다.
        sendEnabled = length in 1..MAX_CONTENT_LENGTH && !sending,
    )
}

/**
 * 채팅 메시지 표시 모델. 서버 도메인 모델(domain.model.ChatMessage)은 Route 에서 이 형태로 옮긴다.
 *
 * @property id 서버 메시지 id. 신고(`chatMessageId`)가 **이 값을 쓴다**. 아직 서버에서 되돌아오지
 *   않은 내 말풍선(`PendingMessage`)과 Preview 더미는 [NO_SERVER_MESSAGE_ID] 라 신고 대상이
 *   되지 않는다([canReport]). **기본값은 지우지 말 것** — Preview 더미 메시지들이 이 인자를
 *   넘기지 않는다.
 * @property senderImageUrl 상대 말풍선 아바타에 그릴 사진. 메시지 응답에는 없고 참여자 조회로 세운
 *   사전에서 온다(`ChatRoute.chatMemberImageUrls`). 내 메시지는 아바타 자리가 없어 항상 null 이고,
 *   없으면 아바타가 회색 원으로 떨어진다. **기본값은 지우지 말 것** — Preview 더미 메시지들이 이
 *   인자를 넘기지 않는다.
 */
data class ChatUiMessage(
    val text: String,
    val isMine: Boolean,
    val senderName: String? = null,
    val timeLabel: String? = null,
    val meta: String? = null, // 내 메시지 좌측 메타 (예: "읽음 2 · 6:41")
    val senderImageUrl: String? = null,
    val id: Long = NO_SERVER_MESSAGE_ID,
)

/**
 * 「서버 id 를 아직(또는 끝내) 모른다」는 뜻의 [ChatUiMessage.id].
 *
 * 0 인 근거: 서버가 `chatMessageId` 를 `@Positive` 로 받아 0 을 **400 으로 거절한다**
 * ([ChatReportReason.MIN_MESSAGE_ID]). 즉 「신고에 쓸 수 없는 값」을 서버와 같은 기준으로 고른
 * 것이고, 음수 임시 id 를 쓰는 것보다 판정이 하나뿐이어서 어긋날 데가 없다.
 */
internal const val NO_SERVER_MESSAGE_ID = 0L

/**
 * 이 말풍선에 **신고 길을 만들어도 되는가.**
 *
 * 두 조건 다 서버가 400 으로 거절하는 것을 미리 접는 것이다:
 * - `!isMine` — 내 메시지를 신고하면 400 `CHAT_CANNOT_REPORT_SELF` 다. 애초에 길을 만들지 않는다.
 *   길을 만들어 두면 사용자가 자기 말을 꾹 눌러 사유까지 고른 끝에 거절당한다.
 * - `id >= MIN_MESSAGE_ID` — 서버 id 가 없는 말풍선(전송 확인 대기 중인 내 메시지, Preview 더미)은
 *   보낼 `chatMessageId` 가 없다. 0 을 보내면 400 `INVALID_REQUEST` 다.
 *
 * 둘째 조건은 첫째에 거의 포함되지만(id 가 없는 건 지금은 내 메시지뿐이다) **따로 본다** —
 * 나중에 남의 말풍선을 서버 확인 전에 그리는 길이 생기면 첫째 조건만으로는 0 이 새어 나간다.
 */
internal fun ChatUiMessage.canReport(): Boolean =
    !isMine && id >= ChatReportReason.MIN_MESSAGE_ID

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

/**
 * 사유 시트에 그릴 줄 — **서버 enum 하나당 한 줄**이고 순서가 그대로 화면 순서다.
 *
 * 문구를 enum 쪽([ChatReportReason])에 두지 않은 이유: 서버 enum 의 주석은 「욕설, 비방, 혐오
 * 표현」처럼 분류 설명이고, 사용자가 고르는 줄은 「괴롭힘이나 욕설이에요」처럼 자기 경험을
 * 고르는 말이다. 같은 자리에 두면 둘 중 하나가 다른 쪽에 끌려간다.
 *
 * `internal` 인 이유는 하나뿐이다 — `MessageReportGuardTest` 가 **enum 값을 하나도 빠뜨리지
 * 않는지**를 이 목록으로 못 박는다. 서버가 사유를 늘렸을 때 고를 수 없는 사유가 생기면
 * 「그 밖의 이유예요」로 뭉개지는데, 그건 운영이 분류로 조치하는 설계를 무력화한다.
 */
internal val chatReportReasonLabels: List<Pair<ChatReportReason, String>> = listOf(
    ChatReportReason.ABUSE to "괴롭힘이나 욕설이에요",
    ChatReportReason.SPAM to "광고나 도배예요",
    ChatReportReason.FRAUD to "사기나 금전 요구예요",
    ChatReportReason.SEXUAL to "성적으로 불쾌해요",
    ChatReportReason.ETC to "그 밖의 이유예요",
)

/**
 * 신고 전송의 **결과 한 줄** — 시트를 닫을지와 배너 색을 함께 정한다.
 *
 * @property accepted 서버가 접수했는가(201). 배너 색이 성공/실패로 갈린다.
 * @property retryable **같은 내용으로 다시 보내서 달라질 수 있는** 실패인가. 참이면 시트를
 *   **열어 둔다** — 적어 둔 상세 수백 자를 연결이 한 번 끊긴 것 때문에 버리게 하지 않는다.
 *   거짓이면(접수됨·이미 신고함·참여자 아님·메시지 없음) 닫는다. 「이미 신고한 메시지」를
 *   재시도로 열어 두면 **사유를 바꿔도 영원히 같은 409** 라 사용자를 가둔다(실측).
 */
data class ChatReportOutcome(
    val message: String,
    val accepted: Boolean,
    val retryable: Boolean = false,
)

/**
 * 상세 내용 입력창이 **받아 주는** 최대 글자 수 — 서버 한도의 2배.
 *
 * 입력창 쪽과 같은 판단이다([INPUT_HARD_LIMIT]): 한도대로 자르면 「넘었다」를 말해 줄 기회가
 * 사라지므로 한도는 카운터가 말하게 두고, 이 값은 붙여넣기 폭주만 막는다.
 */
private const val REPORT_DESCRIPTION_HARD_LIMIT = ChatReportReason.MAX_DESCRIPTION_LENGTH * 2

/**
 * 상세 내용 카운터가 **보이기 시작하는** 길이. 한도보다 100자 앞서 나타난다 —
 * 「신고하기」가 잠기는 순간에 카운터가 **이미 화면에 있고 색만 바뀌게** 하기 위해서다
 * ([COUNTER_VISIBLE_FROM] 과 같은 근거).
 */
internal const val REPORT_COUNTER_VISIBLE_FROM = ChatReportReason.MAX_DESCRIPTION_LENGTH - 100

/**
 * 사유 시트 한 상태에서 **파생되는 것 전부** — 카운터 문구와 전송 가능 여부.
 * [ChatInputUi] 와 같은 이유로 한 함수에서 함께 뽑는다(길이를 두 번 재면 기준이 갈린다).
 */
internal data class ChatReportSubmitUi(
    val counter: String?,
    val overLimit: Boolean,
    val submitEnabled: Boolean,
)

/**
 * 「신고하기」를 **누를 수 있는가**와 그 이유를 함께 뽑는다.
 *
 * 세 가지를 서버에 묻지 않고 여기서 접는다 — 전부 **누르기 전부터 알 수 있는** 거절이다:
 * - 사유를 안 골랐다 → 서버는 400 `INVALID_REQUEST`(「신고 사유는 필수입니다」)
 * - 「기타」인데 상세가 비었다(공백만 있어도 같다) → 400 `CHAT_REPORT_DESCRIPTION_REQUIRED`
 * - 상세가 [ChatReportReason.MAX_DESCRIPTION_LENGTH]자를 넘었다 → 400 `INVALID_REQUEST`
 *
 * 잠그되 **이유를 먼저 보여 준다**: 사유를 고르기 전에는 카운터가 없고, 「기타」를 고르면
 * 입력창과 안내가 함께 나타나며, 한도에 가까워지면 카운터가 먼저 뜬다
 * ([REPORT_COUNTER_VISIBLE_FROM]). 이 화면의 입력창이 세운 규칙([chatInputUi])과 같다.
 */
internal fun chatReportSubmitUi(
    reason: ChatReportReason?,
    description: String,
    submitting: Boolean = false,
): ChatReportSubmitUi {
    val length = description.trim().length
    val max = ChatReportReason.MAX_DESCRIPTION_LENGTH
    val overLimit = length > max
    val needsDescription = reason?.requiresDescription == true
    return ChatReportSubmitUi(
        // 상세가 필요 없고 아무것도 안 썼으면 줄을 그리지 않는다 — 의미 없는 「0/500자」가
        // 시트 높이를 상시로 빼앗는다.
        counter = when {
            overLimit -> "$length/${max}자 — 줄여야 보낼 수 있어요"
            length >= REPORT_COUNTER_VISIBLE_FROM -> "$length/${max}자"
            else -> null
        },
        overLimit = overLimit,
        submitEnabled = reason != null &&
            !overLimit &&
            !submitting &&
            (!needsDescription || length >= 1),
    )
}

// Preview 전용 더미 대화 — 실제 진입(ChatRoute)은 서버에서 조회한 메시지를 넘긴다.
// 상대 메시지에만 id 를 준다 — Preview 에서도 신고 길이 내 말풍선에 생기지 않는 것을 눈으로 본다.
private val PreviewMessages = listOf(
    ChatUiMessage(text = "정문 앞 편의점에 있어요", isMine = false, senderName = "동승자", timeLabel = "오후 1:02", id = 41L),
    ChatUiMessage(text = "2분 뒤 도착합니다", isMine = true, meta = "읽음 2 · 1:03", id = 42L),
    ChatUiMessage(text = "확인했어요", isMine = true, id = 43L),
)

/**
 * 24 · 채팅 [S16] — 24a 메뉴 오버레이 포함
 *
 * 이동(디스크립션):
 * - 뒤로 → 22 탑승 상세 (onBack)
 * - 「⋮」 → 24a 메뉴 열림 (내부 상태)
 * - 상단 「매칭 화면으로 →」 배너 탭 → 21/25/26 진행 단계 (onOpenMatching — 진행 중인 방의 채팅방일 때만 보인다)
 * - 24a 「채팅방 알림 끄기/켜기」 → 24 (서버 음소거 토글 — onToggleMute, 부제에 「알림 꺼짐」)
 * - 24a 「채팅방 나가기」 → **항상 재확인 다이얼로그** → 14 홈 (onLeaveChat)
 * - **상대 말풍선 롱프레스** → 신고 액션 시트 → 사유 시트 → 24 (onReportMessage).
 *   내 말풍선에는 길이 없다 — 서버가 본인 신고를 400 으로 거절한다([ChatUiMessage.canReport]).
 *   상단바 메뉴(24a)에 둘 수 없는 이유는 서버가 `chatMessageId` 를 필수로 받아 **대상 메시지를
 *   반드시 지목해야** 하는데 거기서는 고를 길이 없기 때문이다.
 * - 하단탭 → 14/17/35 (onTabSelect)
 * - 헤더 검색 아이콘 → 상단이 검색 바로 바뀌고 본문이 결과 목록이 된다(onOpenSearch / onCloseSearch).
 *   검색어 2자 이상부터 서버(GET …/messages/search)에 묻고, 결과는 최신순 · 「이전 결과 더 보기」로 페이징.
 *   결과를 눌러 그 메시지로 점프하는 건 아직 없다 — 서버가 위치(오프셋)를 주지 않아 커서 페이징으로는
 *   해당 메시지까지 몇 페이지를 더 받아야 하는지 모른다.
 *
 * 유효값: 메시지 1~[MAX_CONTENT_LENGTH]자(서버 `SendMessageRequest @Size(max = 1000)` 와 같은 수),
 * 공백만 입력 시 전송 비활성. 한도를 넘으면 **입력은 막지 않고**(붙여넣기 폭주만 [INPUT_HARD_LIMIT])
 * 입력창 위 카운터가 넘긴 사실을 말하고 전송 버튼을 잠근다([chatInputUi]) — 잠금은 **이유가 이미
 * 보이는 것에 대한 대가**다. 예전에 500자로 잘라 버려 안내가 영영 안 보였고, 사용자는 왜 막혔는지
 * 알 수 없었다. ViewModel 의 한도 검사는 그래도 남는다(방어 — [ChatRoomViewModel.send]).
 */
// 방 제목·부제·메시지는 기본값이 없다 — 실제 진입(ChatRoute)은 서버 ChatRoom 의 출발지 → 목적지와
// 조회한 메시지를 항상 넘긴다.
// 「매칭 완료 · 시각」 시스템 칩은 없앴다 — 방 생성 시각이 모델에 없어 모든 방에 같은 고정 문구가 보였다.
// 24b 「실시간 위치 공유」 시트와 「＋」 버튼도 없앴다. **API 가 없어서가 아니다** — 서버에는 REST 3개
// (`POST`·`DELETE /api/v1/chat-rooms/{id}/location/sharing`, `POST /api/v1/chat-rooms/{id}/location`)
// 와 STOMP 2개(`/chat-rooms/{id}/location`, 스냅샷 `/chat-rooms/{id}/location/sync`)가 다 있다.
// 안 붙인 이유는 둘이고 둘 다 코드 밖에 있다. (1) 2026-10-08 에 이 기능을 걷으면서 개인정보
// 처리방침·매너 서약에서 위치 수집 고지를 함께 줄였다 — 되살리려면 그 문구부터 되돌려야 한다.
// (2) 서버의 REST 좌표 전송은 백그라운드용인데 매니페스트에 `ACCESS_BACKGROUND_LOCATION` 이 없고,
// 그 권한은 Play Console 용도 신고와 시연 영상을 요구한다. 포그라운드만이면 권한 없이 가능하다.
@Composable
fun ChatScreen(
    roomTitle: String,
    roomSubtitle: String,
    messages: List<ChatUiMessage>,
    input: String = "",
    sending: Boolean = false,
    errorMessage: String? = null,
    onInputChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onBack: () -> Unit = {},
    /** 현재 화면에 이 진입 요소가 없다 — 진행 중 방은 [onOpenMatching] 배너가 21/25/26 으로 보낸다. 호출부 시그니처만 유지한다 */
    @Suppress("UNUSED_PARAMETER") onOpenRideOngoing: () -> Unit = {},
    /**
     * 이 방의 파티가 아직 진행 중일 때 매칭 단계 화면으로 되돌아가는 길. **null 이면 그리지 않는다** —
     * 끝난 방의 채팅에서 「매칭 화면으로」를 눌러 봐야 갈 곳이 없다.
     *
     * 21·25·26 에서 「채팅 열기」로 들어온 사용자에게는 뒤로가기가 이미 복귀 경로지만,
     * 채팅 **탭 목록**에서 들어온 사용자에게는 이 버튼이 유일한 길이다.
     */
    onOpenMatching: (() -> Unit)? = null,
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
    /** 신고를 보내는 중 — 사유 시트의 「신고하기」를 잠근다(같은 신고를 두 번 보내지 않는다) */
    reportSubmitting: Boolean = false,
    /**
     * 직전 신고의 결과. null 이면 아직 없다 — ViewModel 이 전송을 시작할 때 **반드시 null 로
     * 되돌린다.** 같은 결과가 연달아 오면(같은 메시지를 또 신고 → 같은 409) 값이 안 바뀌어
     * 시트를 닫는 효과가 발화하지 않는다.
     */
    reportOutcome: ChatReportOutcome? = null,
    /**
     * 신고 전송. 상세는 비어 있으면 null 로 접어서 넘긴다 — 서버가 공백을 「기타」에서 거절한다.
     * 「누가」는 서버가 메시지에서 찾으므로 메시지 id 하나로 끝난다.
     */
    onReportMessage: (messageId: Long, reason: ChatReportReason, description: String?) -> Unit = { _, _, _ -> },
) {
    var menuOpen by remember { mutableStateOf(false) } // 24a
    var leaveConfirmOpen by remember { mutableStateOf(false) }

    // ── 메시지 신고 ───────────────────────────────────────────────────────────
    // 시트 상태는 **화면이 들고 있다** — 24a 메뉴·나가기 다이얼로그와 같은 자리다(이 파일의
    // 기존 규칙). ViewModel 로 올려야 하는 것은 전송과 그 결과뿐이라, 고른 사유와 적던 상세가
    // 오갈 필요가 없다. 호출부 시그니처도 콜백 하나로 끝난다.
    /** 꾹 누른 메시지 — null 이면 액션 시트도 안 열렸다 */
    var reportTarget by remember { mutableStateOf<ChatUiMessage?>(null) }
    /** 액션 시트(「이 메시지 신고하기」)를 지나 사유 시트까지 왔는가 */
    var reasonPickerOpen by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf<ChatReportReason?>(null) }
    var reportDescription by remember { mutableStateOf("") }

    /**
     * **지금 시트에서** 「신고하기」를 눌렀는가. 직전 결과(`reportOutcome`)는 다음 신고까지 남아
     * 있으므로 이 플래그가 없으면, 실패한 뒤 **다른 메시지**를 꾹 눌렀을 때 앞 실패 문구가 새
     * 시트 안에 그대로 떠서 방금 누른 것의 답으로 읽힌다.
     */
    var reportSubmitted by remember { mutableStateOf(false) }

    fun closeReport() {
        reportTarget = null
        reasonPickerOpen = false
        reportReason = null
        reportDescription = ""
        reportSubmitted = false
    }

    fun openReport(target: ChatUiMessage) {
        closeReport()
        reportTarget = target
    }

    // 결과가 오면 **다시 보내서 달라질 실패가 아닌 한** 시트를 닫는다. 「이미 신고한 메시지」를
    // 열어 두면 사유를 바꿔도 영원히 같은 409 라(실측) 사용자가 빠져나갈 수 없다.
    LaunchedEffect(reportOutcome) {
        if (reportOutcome != null && !reportOutcome.retryable) closeReport()
    }

    // 카운터와 전송 가능 여부를 **한 곳에서** 뽑는다([chatInputUi]) — 두 곳에서 길이를 세면
    // 기준이 갈려 「999자인데 버튼이 잠김」이 생긴다. 잠그기 전에 이유를 먼저 보여 준다는 규칙도
    // 그 함수 KDoc 에 있다.
    val inputUi = chatInputUi(input = input, sending = sending)
    val sendEnabled = inputUi.sendEnabled

    // 검색 중 뒤로가기는 방을 나가는 게 아니라 검색을 닫는다 — 카카오톡·기본 메시지 앱과 같은 기대
    BackHandler(enabled = search.open) { onCloseSearch() }

    // 신고 시트가 열려 있으면 뒤로가기가 그걸 먼저 닫는다. 없으면 꾹 눌러 시트를 띄운 사용자가
    // 뒤로가기로 **방을 떠나 버린다** — 바깥을 탭하는 길만 두면 시트가 화면을 덮은 상태에서
    // 유일하게 익숙한 탈출구가 방 이탈로 이어진다.
    BackHandler(enabled = reportTarget != null) { closeReport() }

    // 키보드가 올라온 만큼 화면을 줄인다. enableEdgeToEdge() 로 창이 IME 에 맞춰 리사이즈되지 않아
    // imePadding 이 없으면 입력 바와 하단탭이 키보드 뒤로 숨는다(실기 QA).
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val imeVisible = imeBottomPx > 0
    // 메시지 리스트는 **바닥에 붙어 있는 동안만** 마지막 대화로 따라간다(새 메시지·키보드 등장 모두).
    val listScroll = rememberScrollState()
    val autoScrollSlopPx = with(LocalDensity.current) { AUTO_SCROLL_SLOP.toPx() }
    // 「바닥에 붙어 있는가」 — 자동 스크롤의 유일한 조건.
    //
    // 판정 시점이 중요하다. 새 메시지가 붙거나 키보드가 올라오는 **그 순간**에는 maxValue 가 먼저 늘고
    // value 는 아직 그대로라 거리가 벌어져 있다 — 그때의 거리로 재면 바닥에 있던 사람도 「올려 읽는 중」이
    // 된다(키보드를 올릴 때마다 바닥을 놓친다). 그래서 **스크롤이 멈춘 시점**의 위치만 근거로 삼는다.
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listScroll, autoScrollSlopPx) {
        snapshotFlow { listScroll.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) stickToBottom = listScroll.maxValue - listScroll.value <= autoScrollSlopPx
        }
    }
    LaunchedEffect(messages.size, imeVisible, listScroll.maxValue) {
        // 지난 대화를 올려 읽는 중이면 가만히 둔다 — 읽던 자리를 바닥으로 끌어내리면 자리를 잃는다.
        // (`listScroll.maxValue` 가 키에 있어 레이아웃이 바뀔 때마다 재발화하므로, 가드가 없으면
        //  새 메시지뿐 아니라 **아무 레이아웃 변화에도** 바닥으로 끌려갔다.)
        if (stickToBottom) listScroll.animateScrollTo(listScroll.maxValue)
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

            // 메시지 리스트 + (24a/24b 오버레이 영역)
            if (!search.open) Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(listScroll)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    messages.forEachIndexed { index, message ->
                        // 신고 길은 **신고할 수 있는 말풍선에만** 붙는다([ChatUiMessage.canReport]) —
                        // 내 말풍선이나 서버 id 가 없는 말풍선에 붙이면 서버 400 을 왕복해 받는다.
                        MessageRow(
                            message = message,
                            onRequestReport = if (message.canReport()) {
                                { target -> openReport(target) }
                            } else null,
                        )
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
                                        // **언제나** 재확인을 받는다 — 나가기는 어느 방에서든 되돌릴 수 없다.
                                        // 서버가 나간 참여자를 되살리지 못하고(leaveConfirm 다이얼로그 본문),
                                        // 앱에 재합류를 부르는 화면도 없다. 예전에는 「진행 중 탑승이 있을 때만」
                                        // 물었는데 그 플래그가 상수 false 로 고정돼 있어 **한 번도 묻지 않았고**,
                                        // 다이얼로그 자체가 도달 불가능한 죽은 코드였다(실기 QA).
                                        leaveConfirmOpen = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                            }
                            HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
                            // 신고를 **여기서 실행할 수는 없다** — 서버가 `chatMessageId` 를 필수로 받아
                            // 어떤 메시지인지 정해지지 않으면 보낼 수 없다. 그렇다고 입구를 롱프레스
                            // 하나로 두면 **안전 기능인데 아무도 못 찾는다**(눌러도 시각 반응이 없어
                            // 우연히 발견될 여지도 없다). 실행은 못 해도 **안내는 여기 둘 수 있다.**
                            Text(
                                text = "신고는 메시지를 꾹 눌러요",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MoyeotaColor.TextMute,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            // 입력 바 — 검색 중엔 숨긴다(결과 목록 위에서 전송할 일이 없고, 키보드는 검색 바가 쓴다)
            if (!search.open) Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                HorizontalDivider(color = MoyeotaColor.Hairline)
                // 신고 결과 — 전송 실패 안내와 **같은 자리**다. 시트가 닫히면서 결과가 보여야
                // 하므로 시트 안이 아니라 여기에 둔다.
                //
                // 시트가 열려 있는 동안에는 감춘다: 지난 결과를 덮어쓰지 않고 들고 있기 때문에
                // (다음 신고를 보낼 때 ViewModel 이 비운다) 새 신고를 적는 중에 **앞 신고의
                // 문구가 남아 보이면** 방금 누른 것의 답으로 읽힌다.
                if (reportOutcome != null && reportTarget == null) {
                    NoticeBanner(
                        kind = if (reportOutcome.accepted) NoticeKind.SUCCESS else NoticeKind.ERROR,
                        text = reportOutcome.message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                // 전송·수신 실패 안내 — 입력한 내용은 지우지 않는다
                if (errorMessage != null) {
                    NoticeBanner(
                        kind = NoticeKind.ERROR,
                        text = errorMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                // 글자 수 — **입력창 바로 위**. 한도에 가까워지면 나타나고, 넘기면 강조된다
                // ([chatInputUi]). 넘긴 상태에서 전송 버튼이 잠기는데, 그 이유가 **누르지 않아도**
                // 이 자리에 있어야 한다는 것이 이 줄의 존재 이유다.
                //
                // 자리를 여기로 둔 근거: 입력 바 Column 안이라 말풍선 목록(weight)이 그만큼 줄고,
                // 나타날 때 입력 pill 과 전송 버튼이 한 줄 높이만큼 **위로** 올라간다. 그 움직임은
                // 한도보다 100자 앞서(= 잠기기 전에) 한 번만 일어나므로, 버튼이 잠기는 순간에는
                // 레이아웃이 움직이지 않는다([COUNTER_VISIBLE_FROM]).
                // 말풍선 위에 띄우는(overlay) 안은 버렸다 — 마지막 메시지를 가린다.
                inputUi.counter?.let { counter ->
                    Text(
                        text = counter,
                        fontSize = 12.sp,
                        fontWeight = if (inputUi.overLimit) FontWeight.Bold else FontWeight.Medium,
                        // 넘긴 상태는 **실제로 막힌** 상태라 경고색이 맞다(16 의 1인 안내와 반대 —
                        // 그쪽은 정상 선택이라 회색이다). 넘기기 전 예고는 보조 문구색 그대로 둔다.
                        color = if (inputUi.overLimit) MoyeotaColor.Danger500 else GrayAsh,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                            // 서버 한도는 1000자([MAX_CONTENT_LENGTH])지만 여기서 자르지 않는다 —
                            // 넘긴 사실은 위 카운터가 말한다([INPUT_HARD_LIMIT] KDoc). 여기서 막는
                            // 것은 붙여넣기 폭주(2000자)뿐이고, 그 값은 그대로 둔다.
                            onValueChange = { if (it.length <= INPUT_HARD_LIMIT) onInputChange(it) },
                            textStyle = MoyeotaType.BodyMd.copy(color = MoyeotaColor.InkPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (input.isEmpty()) {
                            Text(text = "메시지 보내기", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GrayAsh)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // 전송 — 공백만 입력·한도 초과·전송 중이면 비활성([chatInputUi]).
                    // 한도 초과로 잠긴 경우의 **이유는 바로 위 카운터**가 들고 있다.
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

        // 채팅방 나가기 재확인 다이얼로그 — 나가기를 누르면 **항상** 거친다(되돌릴 수 없다)
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
                    // 「되돌릴 수 없다」를 먼저 적는다 — 이 다이얼로그가 있는 이유다.
                    // 서버가 나간 참여자를 되살리지 못하고(chat_room_user 복합키에 leftAt 만 찍힌다)
                    // 앱에도 다시 들어가는 화면이 없다.
                    Text(
                        text = "한 번 나가면 이 대화로 다시 들어올 수 없어요.\n" +
                            "채팅방 나가기는 탑승 이탈과 별개라 진행 중인 탑승은 유지돼요.",
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

        // 신고 액션 시트 — 꾹 누른 말풍선 하나에 대한 동작 목록. 지금은 한 줄뿐이지만 24a 메뉴와
        // 같은 모양으로 둔 이유는 다음 줄(예: 메시지 복사)이 들어올 자리이기 때문이다.
        val target = reportTarget
        if (target != null && !reasonPickerOpen) {
            MessageActionSheet(
                onReport = { reasonPickerOpen = true },
                onDismiss = { closeReport() },
            )
        }

        // 사유 선택 시트 — 나가기 다이얼로그와 같은 모양(Scrim + 18dp 라운드 카드).
        if (target != null && reasonPickerOpen) {
            ReportReasonSheet(
                quotedText = target.text,
                reason = reportReason,
                description = reportDescription,
                submitting = reportSubmitting,
                // 이 시트에서 보낸 것의 답만 시트 안에 적는다([reportSubmitted]).
                failureMessage = reportOutcome?.takeIf { it.retryable && reportSubmitted }?.message,
                onReasonSelect = { picked ->
                    reportReason = picked
                    // 「기타」가 아닌 사유로 바꾸면 적어 둔 상세를 버린다 — 그 사유에서는 상세가
                    // 화면에 보이지 않는데 몰래 함께 전송되면 사용자가 쓴 줄 모르는 글이 신고에 붙는다.
                    if (!picked.requiresDescription) reportDescription = ""
                },
                onDescriptionChange = { typed ->
                    // 자르지 않고 받아 두되 붙여넣기 폭주만 막는다 — 한도는 카운터가 말한다.
                    if (typed.length <= REPORT_DESCRIPTION_HARD_LIMIT) reportDescription = typed
                },
                onSubmit = { picked ->
                    reportSubmitted = true
                    onReportMessage(
                        target.id,
                        picked,
                        // 공백만 적은 상세는 **없는 것과 같다** — 서버가 「기타」에서 isBlank 를
                        // 400 으로 거절하므로 전송 버튼이 이미 잠겨 있고, 그 밖의 사유에서는
                        // 빈 문자열을 실어 보낼 이유가 없다.
                        reportDescription.trim().takeIf { it.isNotBlank() },
                    )
                },
                onDismiss = { closeReport() },
            )
        }
    }
}

/**
 * 메시지 하나에 대한 동작 시트 — 모양은 24a 메뉴 오버레이를 그대로 베꼈다
 * (전면 `Box` + 배경 탭 닫기 + 18dp 라운드 + 그림자. Scrim 은 나가기 다이얼로그 쪽을 따랐다 —
 * 24a 와 달리 이건 「무엇을 눌렀는지」가 중요해서 뒤를 가려 대상이 분명해야 한다).
 * **새 디자인을 만들지 않았다**:
 * 이 앱에는 `ModalBottomSheet` 선례가 한 건도 없고(디자인시스템에 시트 컴포넌트가 없다),
 * 메시지 단위 동작 UI 자체가 이게 처음이라 기준을 하나 더 만들면 둘 다 흔들린다.
 *
 * 24a 와 달리 **아래쪽에 붙인다** — 꾹 누른 손가락이 화면 가운데~아래에 있어서, 오른쪽 위에
 * 열리는 24a 자리로 두면 눈과 손이 둘 다 움직여야 한다.
 */
@Composable
private fun MessageActionSheet(
    onReport: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = BubbleShadow)
                .clip(RoundedCornerShape(18.dp))
                .background(MoyeotaColor.SurfaceCanvas)
                // 시트 안을 눌러도 닫히지 않게 한다 — 나가기 다이얼로그와 같은 처리
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
        ) {
            Text(
                text = "이 메시지 신고하기",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReport() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
            HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                text = "취소",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GraySlate,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

/**
 * 사유 선택 시트. 모양은 **나가기 확인 다이얼로그**(이 파일의 Scrim + 18dp 카드)를 베꼈다.
 *
 * 색에 대한 판단: 빨강([com.moyeota.core.designsystem.theme.MoyeotaColor] 의 Danger/Safety 계열)을
 * **쓰지 않는다.** 이 앱에서 빨강 신고 요소는 27 긴급 신고(112 통화)로 가는 길이고
 * `SafetyButton` 은 KDoc 이 「긴급 신고 전용, 다른 곳 사용 금지」로 못 박았다. 메시지 신고는
 * 112 를 부르는 일이 아니라서 같은 색을 쓰면 두 신고가 구분되지 않는다 — 평상시 색으로 두고
 * 확인 버튼만 Primary 로 강조한다.
 *
 * @param quotedText 신고 대상 말풍선의 글. **무엇을 신고하는지 보여 주지 않으면** 꾹 누른 것이
 *   맞는 메시지인지 확인할 길이 없다(목록이 그동안 새 메시지로 밀려 있을 수 있다).
 * @param failureMessage 다시 보내서 달라질 수 있는 실패만 들어온다 — 시트를 열어 둔 이유를 적는다.
 */
@Composable
private fun ReportReasonSheet(
    quotedText: String,
    reason: ChatReportReason?,
    description: String,
    submitting: Boolean,
    failureMessage: String?,
    onReasonSelect: (ChatReportReason) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: (ChatReportReason) -> Unit,
    onDismiss: () -> Unit,
) {
    val submit = chatReportSubmitUi(reason = reason, description = description, submitting = submitting)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 「기타」를 고르면 입력창이 열려 키보드가 올라온다 — imePadding 이 없으면 시트가
            // 화면 가운데에 그대로 있어 **「신고하기」 버튼이 키보드 뒤로 숨는다**(쓴 글을
            // 보낼 수가 없다). 이 화면의 입력 바가 같은 이유로 같은 처리를 한다.
            .imePadding()
            .background(MoyeotaColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = BubbleShadow)
                .clip(RoundedCornerShape(18.dp))
                .background(MoyeotaColor.SurfaceCanvas)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                // 사유 5줄 + 입력창 + 버튼이 작은 폰에서 화면을 넘긴다 — 스크롤을 둔다.
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = "신고 사유를 알려 주세요",
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                // 홑낫표로 인용한다 — 공통 규칙(인용값은 「」)
                text = "「${quotedText}」",
                style = MoyeotaType.BodySm,
                color = GraySlate,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))

            // 사유는 **하나만** 고른다 — `selectableGroup` 이 없으면 보조기술에 독립된 버튼 다섯 개로
            // 들려 「하나만 고르는 선택」이라는 규칙이 사라진다(10 프로필 만들기의 성별 칩과 같은 레시피).
            Column(modifier = Modifier.selectableGroup()) {
                chatReportReasonLabels.forEachIndexed { index, (value, label) ->
                    val selected = reason == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onReasonSelect(value) },
                            )
                            // 되돌릴 수 없는 신고의 입력이라 터치 타깃을 48dp 로 채운다
                            .heightIn(min = 48.dp)
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ★ **색만으로 선택을 표시하지 않는다.** 이 앱에는 색 하나로만 가르는 자리가
                        // 한 곳도 없다 — 항상 (도형 또는 채움) + (굵기 또는 그림자)가 함께 바뀐다.
                        // 색약 사용자에게 글자 색·굵기 차이만 남으면 무엇을 골랐는지 알 수 없다.
                        ReportReasonMark(selected = selected)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MoyeotaColor.Primary600 else MoyeotaColor.InkPrimary,
                        )
                    }
                    if (index != chatReportReasonLabels.lastIndex) {
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                    }
                }
            }

            // 「기타」만 상세가 필수다 — 고르기 전에는 입력창을 그리지 않는다. 늘 띄워 두면
            // 「안 써도 되는 칸」이 시트를 길게 만들고, 쓰면 보내지는지도 알 수 없다.
            if (reason?.requiresDescription == true) {
                Spacer(Modifier.height(16.dp))
                // 디자인시스템의 `MoyeotaTextField` 를 쓰지 않았다 — 그 컴포넌트는 입력 박스를
                // **52dp 고정 높이**로 그려서 여러 줄을 쓰면 글이 잘려 보인다(500자를 받는 칸이다).
                // 그래서 이 파일이 이미 두 번 한 것(검색 바·메시지 입력 pill)과 같은 모양으로
                // 직접 그린다. 새 스타일을 만든 게 아니라 같은 pill 을 **높이만** 늘린 것이다.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .background(InputPillBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        textStyle = MoyeotaType.BodyMd.copy(color = MoyeotaColor.InkPrimary),
                        // 여러 줄로 받는다 — 상황을 적는 칸이라 한 줄로 가로 스크롤되면 쓴 글을
                        // 다시 읽을 수가 없다.
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (description.isEmpty()) {
                        Text(
                            text = "어떤 점이 문제였는지 알려 주세요",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayAsh,
                        )
                    }
                }
                submit.counter?.let { counter ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = counter,
                        style = MoyeotaType.CaptionMd,
                        color = if (submit.overLimit) MoyeotaColor.Danger600 else GrayMute,
                    )
                }
            }

            if (failureMessage != null) {
                Spacer(Modifier.height(12.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = failureMessage)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(MoyeotaColor.SurfaceSoft, RoundedCornerShape(12.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "취소", style = MoyeotaType.ButtonMd, color = GraySlate)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            // 잠긴 상태를 **색으로도** 말한다 — 눌리지 않는 이유는 위 카운터나
                            // 안 고른 사유 줄에 이미 보인다.
                            if (submit.submitEnabled) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceSoft,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = submit.submitEnabled) { reason?.let(onSubmit) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (submitting) "보내는 중" else "신고하기",
                        style = MoyeotaType.ButtonMd,
                        color = if (submit.submitEnabled) MoyeotaColor.TextOnDark else GrayAsh,
                    )
                }
            }
        }
    }
}

/**
 * @param onRequestReport 상대 말풍선을 **꾹 눌렀을 때** 신고 액션 시트를 띄우는 길.
 *   null 이면 롱프레스를 아예 달지 않는다 — 내 말풍선에는 절대 null 로 들어온다
 *   ([ChatUiMessage.canReport]). 내 말풍선 분기에서는 쓰지도 않는다.
 */
@Composable
private fun MessageRow(
    message: ChatUiMessage,
    onRequestReport: ((ChatUiMessage) -> Unit)? = null,
) {
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
            // 이름은 바로 아래에 따로 적으므로 아바타에는 사진만 준다 — 못 받았으면 회색 원이다.
            AvatarCircle(size = 32.dp, imageUrl = message.senderImageUrl)
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
                    val peerBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
                    val reportInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .shadow(4.dp, peerBubbleShape, spotColor = BubbleShadow)
                            .background(MoyeotaColor.SurfaceCard, peerBubbleShape)
                            // 롱프레스는 **padding 보다 앞**에 둔다 — 뒤에 두면 말풍선 안쪽 여백이
                            // 눌리지 않는 띠가 돼서 「글자 위를 정확히 눌러야 열린다」가 된다.
                            // 짧은 탭은 아무 일도 하지 않는다(onClick 이 빈 람다): 이 앱에 메시지
                            // 탭 동작이 없고, indication 도 끈다 — 누를 때마다 물결이 번지면
                            // 말풍선이 버튼처럼 보여 「탭하면 뭔가 있다」는 거짓 신호를 준다.
                            .then(
                                if (onRequestReport == null) Modifier
                                else Modifier.combinedClickable(
                                    interactionSource = reportInteraction,
                                    indication = null,
                                    onClick = {},
                                    onLongClick = { onRequestReport(message) },
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Text(text = message.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MoyeotaColor.InkPrimary)
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChatScreenPreview() {
    ChatScreen(
        roomTitle = "정문 → 역 동승",
        roomSubtitle = "3명 · 출발 대기",
        messages = PreviewMessages,
    )
}

/**
 * 신고 사유의 선택 표시 — **도형**이다.
 *
 * 글자 색·굵기만 바꾸면 색약 사용자에게 무엇을 골랐는지 남지 않는다. 이 앱에는 색 하나로만
 * 선택을 가르는 자리가 한 곳도 없고(10 성별 칩·36 사진 격자·14 즐겨찾기 모두 도형이 함께 바뀐다),
 * 되돌릴 수 없는 신고의 입력이 그 규칙을 처음 어기는 자리가 되면 안 된다.
 *
 * material-icons 의존이 없어 03 인증 유형의 `RadioMark` 와 같은 방식으로 Canvas 없이 그린다.
 */
@Composable
private fun ReportReasonMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas)
            .border(
                width = if (selected) 0.dp else 1.5.dp,
                color = if (selected) MoyeotaColor.Primary500 else MoyeotaColor.Hairline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MoyeotaColor.SurfaceCanvas))
        }
    }
}
