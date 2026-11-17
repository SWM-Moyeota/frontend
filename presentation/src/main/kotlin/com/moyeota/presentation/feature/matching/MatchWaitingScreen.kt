package com.moyeota.presentation.feature.matching

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.MapOverlayPill
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.DangerTextButton
import com.moyeota.core.designsystem.component.MapSheetScaffold
import com.moyeota.core.designsystem.component.MoyeotaChip
import com.moyeota.core.designsystem.component.MoyeotaConfirmDialog
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.SoftButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.fitMapCamera
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.core.designsystem.theme.tabularNums
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.User
import com.moyeota.presentation.core.HostBadge
import com.moyeota.presentation.core.MeBadge
import com.moyeota.presentation.core.location.UserCoordinates
import com.moyeota.presentation.core.MemberAvatar
import com.moyeota.presentation.core.displayNickname
import com.moyeota.presentation.core.hostDisplayNameOrNull
import com.moyeota.presentation.core.isWithdrawn
import com.moyeota.presentation.core.rideCountLabel
import com.moyeota.presentation.core.showsHostBadge
import java.time.Instant

/**
 * 아래 [MatchWaitingScreenPreview] **전용** 예시 방. 프로덕션 경로에서는 쓰이지 않는다.
 *
 * 예전에는 이 값이 [MatchWaitingScreen] 의 `ride` **기본 인자**이기도 했다. 그래서 호출부가 방을
 * 모르는 상태(구성 변경에 방 id 가 날아간 21)에서 `ride` 를 빼고 부르면, 진짜 방에 들어 있던
 * 사용자에게 이 센텀시티역→서면역 방이 떴다(출시 차단 결함, 2026-10-15). 기본값을 걷어내 호출부가
 * **반드시** 방을 넘기게 했다 — 같은 사고가 다시 나려면 컴파일이 먼저 깨진다.
 */
private val waitingRideDummy = Ride(
    id = "ride-21",
    origin = "센텀시티역 3번 출구",
    destination = "서면역 1번 출구",
    departureLabel = "지금 출발",
    capacity = 3,
    members = listOf(User("me", "부산가자", "", 0.0, 5, isMe = true)),
    farePerPerson = 3600,
    totalFare = 10800,
    status = RideStatus.RECRUITING,
)

/**
 * 21 · 매칭 대기 [S11]
 *
 * 이동(디스크립션):
 * - 「그만 찾기」 → 방 나가기(DELETE /matching/leave) 후 14 홈 (onCancelSearch — 뒤로가기도 동일 처리)
 *   둘 다 **확인 다이얼로그를 거친다** — 뒤로가기 한 번에 방이 날아가면 사용자가 놀란다(QA O-1).
 *   [onCancelSearch] 는 사용자가 「그만 찾기」를 확인한 뒤에만 호출된다.
 *
 *   **나가기는 대기 단계 전부에서 가능하다** — 모집 중([RideStatus.RECRUITING] = 서버 ACTIVE)이든
 *   정원이 찬 뒤([RideStatus.MATCHED] = 서버 COMPLETED)든. 서버 `Party.leave` 의 `ensureRecruiting()`
 *   이 그 두 값을 통과시키고, 정원이 찬 방에서 나가면 서버가 방을 ACTIVE 로 되돌려 다시 모집한다.
 *   예전에 정원이 찬 뒤의 나가기를 막았던 것은 **기사 모드 이야기다**(곧 배차가 시작돼 409 였다) —
 *   그 모드가 사라지면서 막을 이유도 사라졌다([canLeaveParty]).
 *
 *   남은 비활성 구간은 **닫힌 방 한 프레임**뿐이다: 서버가 방을 닫은 순간과 Route 가 그걸 알아채
 *   화면을 떠나는 순간 사이. 그 틈에 눌리면 409 로만 끝나므로 status 를 보고 즉시 버튼을 비활성
 *   문구로 바꾸고, 뒤로가기는 방을 유지한 채 홈으로만 보낸다([onExitKeepingParty]).
 *
 *   반대로 **403 `NOT_PARTY_MEMBER`(= 이미 나갔다)는 실패로 보이지 않는다** — 데이터 계층이 성공으로
 *   되돌려 나가기 성공과 같은 길로 보낸다. 204 응답이 유실된 사용자가 다시 눌러 같은 문구에 갇히지
 *   않게 하려는 것이다([com.moyeota.domain.model.PartyLeaveException] KDoc).
 * - 카드 탭 → 22 탑승 상세 (onCardClick)
 * - 「채팅 열기」 → 24 채팅방(독립 목적지, 뒤로가기로 여기 복귀) — [onOpenChat] 이 null 이 아닐 때만 그린다.
 *   채팅방은 **정원이 찬 뒤에** 생기므로 모집 중에는 대개 null 이다. 눌러도 열 것이 없는 버튼을 두지 않는다.
 * - **다음 화면이 없다.** 정원이 차도 방은 이 화면에 머문다 — 기사·배차를 걷어낸 뒤로 서버가
 *   `DISPATCHING`·`ONGOING` 을 보낼 수 없어 25 배차 현황·26 운행 중이 함께 사라졌다. 여기서 나가는
 *   길은 「합승 완료」(방장) · 「그만 찾기」/「방 나가기」 · 방이 닫힘 — 셋 다 14 홈이다.
 * - 매칭 조건(인원)은 **읽기 전용**이다. 방을 만들 때(16) 정해진 값이고 **바꾸는 API 가 없다** —
 *   방장이 있더라도(서버 `MemberInfo.host`) 조건을 고치는 엔드포인트는 없다. 눌러도 아무 일 없는
 *   「수정」 버튼을 두는 대신 카드 아래 한 줄로 이유를 적는다.
 *   「탐색 반경」 행은 지웠다(2026-10-14) — 서버가 반경을 요청에서 빼고 고정값(500m)으로 정하면서
 *   **사용자가 정한 값이 아니게 됐다.** 게다가 합류에 거리 검사가 없고 목록은 지도 뷰포트만 보므로
 *   그 숫자는 아무것도 걸러내지 않는다. 바꿀 수도 없고 효과도 없는 값을 조건으로 보여 주지 않는다.
 *
 * 도메인 변경(2026-08-30): 「전원 준비 → 방장이 출발」 흐름이 백엔드에서 사라졌다
 * (`/matching/ready`, `/matching/start` 삭제). 그래서 이 화면에는 준비/매칭 시작 버튼이 없다.
 * 그 자리를 한동안 「정원이 차면 서버가 스스로 기사 매칭을 시작한다」가 메웠지만 그 흐름도 사라졌다 —
 * 지금 정원이 찬 방이 하는 일은 **채팅으로 만날 곳을 정하고 방장이 「합승 완료」로 닫는 것**이다.
 *
 * 서버 계약(2026-09): `members[]` 가 닉네임·탑승 횟수·publicId 를 함께 주면서, 기다리는 동안
 * **누가 모였는지**를 실제로 보여줄 수 있게 됐다. 내 줄은 `isMe`(publicId == 세션 uuid)로
 * 판정한다 — 예전처럼 고정 memberId 1 과 비교하지 않는다.
 *
 * 방장 표시(2026-10-15): `members[]` 에 `host` 가 함께 오면서(Backend #240) **방장을 말할 수 있게
 * 됐다** — 2026-08-30 에 지운 「`joinedAt` 최소값 추정」과 달리 이건 **서버 값**이다. 그래서
 * ① 「합승 완료」를 방장에게만 보이고(서버가 그 API 를 방장 전용으로 좁혔다, Backend #237)
 * ② 멤버 목록에 「방장」 배지를 붙인다([com.moyeota.presentation.core.showsHostBadge] —
 * 폴백 때문에 **내 줄에는 붙지 않는다**).
 *
 * 약속 흐름(2026-11-06): **약속 확정 → 출발지 도착 → 출발**이 이 화면 안으로 들어왔다. 새 화면은 0개다 —
 * 서버가 그 셋을 같은 방의 상태 전이로 다루므로(`/confirm`·`/arrive`·`/depart`) 화면을 쪼개면 같은 방을
 * 두 군데서 그리게 된다. 단계 판정은 [meetingStage] **한 곳**이고, 이 화면은 그 결과로 블록 하나를 고른다.
 *
 * | [MeetingStage] | 시트 하단에 들어가는 것 |
 * |---|---|
 * | `RECRUITING` | 없음(예전과 같다) |
 * | `CONFIRM_BY_ME` | 「몇 분 뒤에 만날까요?」 + 프리셋 칩 10·20·30·45·60분 |
 * | `CONFIRM_BY_HOST` | 방장이 정한다는 안내([meetingHostNotice]) — **비활성 버튼을 두지 않는다** |
 * | `FINISH_ONLY` | 없음 — 「합승 완료」가 그대로 그 자리다 |
 * | `CONFIRMED_WAITING` | 「약속 10분 전부터…」 안내([ARRIVE_NOT_OPEN_NOTICE]) |
 * | `ARRIVE_OPEN` | 「출발지 도착했어요」(+ 권한이 없으면 허용 안내) |
 * | `ARRIVED_WAITING` | 「도착했다고 알렸어요…」([ARRIVED_WAITING_NOTICE]) |
 * | `DEPART_OPEN` | 「출발」 — **확인 다이얼로그가 노쇼를 숫자로 말한다**([departConfirmMessage]) |
 *
 * 세 가지가 이 흐름에서 특히 조심한 자리다.
 * 1. **확정되면 나갈 수 없다.** 서버가 확정과 함께 이탈을 막으므로 「방 나가기」를 같은 순간에
 *    비활성 문구로 바꾼다([canLeaveParty]) — 나갈 수 있다고 믿은 사람이 안 나타나면 노쇼가 된다.
 * 2. **확정되면 「합승 완료」가 사라진다.** 그 방을 끝내는 길은 「출발」뿐이고(409
 *    `USE_DEPART_FOR_CONFIRMED_PARTY`), 완료로 닫히면 **노쇼 기록 없이** 방이 끝난다([canFinishParty]).
 * 3. **100m 를 앱이 재지 않는다.** 도착 버튼은 좌표만 보내고, 멀면 서버가 400
 *    `TOO_FAR_FROM_DEPARTURE` 로 답한다 — 그 사유를 그대로 보여 준다([arriveFailureMessage]).
 *
 * 레이아웃(2026-09-08): 배경이 레이더 애니메이션에서 **실지도**로 바뀌었다. 출발지·도착지 마커와
 * 경로, 내 위치 파란 점을 그린다. **탐색 반경 원은 2026-10-14 에 지웠다** — 서버가 반경을
 * 고정값으로 정하고 매칭은 그 값을 읽지 않으므로, 원은 「이 범위에서 찾는다」는 없는 약속이었다.
 * 시트는 16 도착지 확인과 같은 [MapSheetScaffold] 로 접었다 폈다 한다 —
 * 접힘 = 헤드라인 · 진행바 · 「그만 찾기」만 남고 지도가 넓어진다.
 */
@Composable
fun MatchWaitingScreen(
    /** 그릴 방. **기본값이 없다** — 예시 방이 프로덕션에 새는 길을 막는다([waitingRideDummy]) */
    ride: Ride,
    /**
     * 약속 단계 판정에 쓰는 **지금 시각**. [ride] 와 같은 이유로 **기본값이 없다** — 기본값을 두면
     * 호출부가 빠뜨렸을 때 도착·출발 버튼이 조용히 영영 뜨지 않는 **기능 손실**이 되고, 그건 화면을
     * 봐서는 알 수 없다. 넘기지 않으면 컴파일이 먼저 깨지는 쪽을 고른다.
     *
     * 안에서 [Instant.now] 를 부르지 않는 이유도 같다: 재구성마다 값이 달라지면 같은 프레임의 두
     * 판정이 어긋날 수 있고(헤드라인은 「확정」인데 버튼은 아직 안 열린 상태), 테스트가 경계를
     * 고정할 수 없다. 출처는 [MatchWaitingViewModel.nowMillis] 의 10초 틱 하나다.
     */
    now: Instant,
    foundCount: Int = 1,
    conditionLabel: String = "3인",
    /**
     * 내 현재 위치. 지도의 파란 점이고, **「출발지 도착했어요」가 서버에 보내는 좌표**다.
     * 권한이 없거나 아직 못 받았으면 null — 점을 찍지 않고, 도착 버튼도 누를 수 없다
     * (보낼 좌표가 없다 — 지어낸 좌표를 보내면 서버의 100m 판정이 거짓말 위에서 돈다).
     */
    myLocation: UserCoordinates? = null,
    /** 위치 권한이 있는가. 없으면 도착 자리에 **비활성 버튼 대신** 허용 안내가 들어간다 */
    locationGranted: Boolean = false,
    /** 위치 권한 재요청(17 탐색의 `LocationPermissionNotice` 와 같은 배선) */
    onRequestLocationPermission: () -> Unit = {},
    actionInProgress: Boolean = false,
    actionErrorMessage: String? = null,
    onCancelSearch: () -> Unit = {},
    /**
     * 「합승 완료」 — 누르면 모두의 방이 닫힌다. 확인 다이얼로그 뒤에만 호출된다.
     *
     * 버튼은 **확정 전 방의 방장에게만** 보인다([canFinishParty]) — 서버가 이 API 를 방장 전용으로
     * 좁혔고(Backend #237), 확정된 방에는 409 `USE_DEPART_FOR_CONFIRMED_PARTY` 로 거절한다.
     * 비방장에게는 같은 자리에 누가 끝내는지를 말하는 안내문이 들어가고 이 콜백은 쓰이지 않는다.
     */
    onFinish: () -> Unit = {},
    /**
     * 「약속 시간 정하기」 — 프리셋이 고른 **분**(지금부터 몇 분 뒤)만 넘긴다. 확인 다이얼로그 뒤에만.
     *
     * 시각을 만들지 않는 이유: 누르는 순간의 시계로 ViewModel 이 만들어야 사용자가 다이얼로그를
     * 들여다본 시간이 서버의 「1시간 안」 한도를 깎지 않는다([MatchWaitingViewModel.confirmMeeting]).
     */
    onConfirmMeeting: (departureAt: String) -> Unit = {},
    /**
     * 「출발지 도착했어요」 — 기기의 현재 좌표를 넘긴다.
     *
     * `(Double, Double)` 이 아니라 [UserCoordinates] 를 넘기는 이유: 위·경도 전치가 컴파일을
     * 통과하는 시그니처를 두지 않는다. 좌표 전치는 이 프로젝트에서 실제로 났던 결함이다.
     */
    onArrive: (UserCoordinates) -> Unit = {},
    /**
     * 「출발」 — 방이 **모두에게** 끝나고 **미도착자가 노쇼로 기록된다**. 확인 다이얼로그 뒤에만.
     *
     * 방장 전용이 아니다 — 서버가 도착한 멤버 누구에게나 열어 두었다([canDepartParty]).
     */
    onDepart: () -> Unit = {},
    /** 방을 **유지한 채** 화면만 벗어난다. 나가기가 막힌 단계의 뒤로가기가 여기로 온다 */
    onExitKeepingParty: () -> Unit = {},
    onCardClick: () -> Unit = {},
    /** 이 방의 채팅방을 연다. **채팅방이 아직 없으면 null** — 버튼 자체를 그리지 않는다 */
    onOpenChat: (() -> Unit)? = null,
) {
    // 나가기 확인 다이얼로그. 화면 안에서만 쓰는 UI 상태라 ViewModel 로 올리지 않는다
    // (12 마이페이지의 로그아웃 확인과 같은 패턴).
    var cancelConfirming by remember { mutableStateOf(false) }
    var finishConfirming by remember { mutableStateOf(false) }
    var departConfirming by remember { mutableStateOf(false) }
    // 약속 확정 확인 — **어느 프리셋을 눌렀는지**를 함께 들고 있어야 다이얼로그가 그 시각을
    // 되읽어 줄 수 있다(칩에는 「10분」만 적혀 있다). null 이면 다이얼로그가 닫힌 상태다.
    /**
     * 확정 다이얼로그가 들고 있는 선택. null 이면 닫혀 있다.
     *
     * **시각을 함께 들고 있는 게 요점이다** — 칩을 누르는 그 순간 한 번 만들어, 다이얼로그 제목과
     * 서버로 보내는 값이 **같은 값**을 쓴다. 분만 들고 있으면 제목과 요청이 시계를 따로 읽어,
     * 그 사이 분이 바뀌면 본 것과 다른 시각이 확정된다
     * (`MatchWaitingViewModel.confirmMeeting` KDoc, 실측 2026-11-06).
     */
    var confirmingPreset by remember { mutableStateOf<ConfirmingPreset?>(null) }

    // 약속 흐름의 **단일 판정**. 화면 어디서도 조건을 다시 세우지 않는다 — meetingStage KDoc 의 표가
    // 리더가 정한 화면 상태표 그대로이고, 아래 `when` 이 빠진 가지를 컴파일에서 잡아 준다.
    val stage = meetingStage(ride, now)
    // 약속 시간이 지났는가 — 단계에 드러나지 않는 한 가지다(미도착이면 ARRIVE_OPEN 에 머문다).
    val meetingTimePassed = isMeetingTimePassed(ride.departureAt, now)

    // 서버가 나가기를 허용하는 단계인가 — 대기 단계이고 **확정 전**이면 참(canLeaveParty KDoc).
    val canLeave = canLeaveParty(ride)
    // 정원이 찼다 — **방을 손으로 닫는 단계**. 방장 여부와 무관한 표시가 이걸 본다.
    val finishStage = isFinishStage(ride.status)
    // 그 단계에서 **내가 방장이고 확정 전일 때만** 「합승 완료」 버튼을 보여 준다.
    // 비방장에게는 비활성 버튼 대신 아래 footer 의 안내문이 간다 — canFinishParty KDoc 참고.
    val canFinish = canFinishParty(ride)
    // 「합승 완료」 안내문을 그릴 자리인가 — **확정된 방에는 그리지 않는다.** 확정 뒤에 「다 타면
    // 방장이 합승 완료를 눌러요」를 띄우면 서버가 거절하는 경로를 기다리게 만든다.
    val showsFinishNotice = finishStage && !ride.isConfirmed && !canFinish

    // 정원 도달 — 채팅으로 만날 곳을 정하는 시점이다. 문구·진행바가 이 경계로 갈린다.
    //
    // [finishStage] 와 거의 같은 사실을 가리키지만 근거가 다르다: 이쪽은 **멤버 목록 길이**([foundCount]),
    // 저쪽은 **서버 status**. 변경분이 유실돼 둘이 어긋날 수 있어([hasMemberDrift] 가 그걸 잡아 상세를
    // 다시 읽는다) 각자의 자리에서 각자를 본다 — 진행바·인원 문구는 목록을, 단계 표시는 서버를 따른다.
    val isFull = foundCount >= ride.capacity
    // 나를 뺀 인원 — **내가 목록에 있을 때만** 센다([otherMemberCount] KDoc).
    val otherCount = otherMemberCount(ride)
    // 도착 인원. 확정된 방에서 사람들이 가장 자주 확인하는 숫자이고, 「출발」 확인 문구가 말하는
    // 노쇼 대상 수와 **같은 목록에서** 나온다 — 사용자가 눈으로 맞춰 볼 수 있어야 한다.
    val arrived = arrivedCount(ride)
    // 진행바가 가리키는 것이 단계에 따라 **바뀐다.** 확정 전에는 「몇 명 모였나」(정원 기준)이고,
    // 확정 뒤에는 모으는 일이 끝났으므로 「몇 명 도착했나」다 — 확정된 방에서 늘 100% 로 꽉 찬
    // 막대는 아무것도 말해 주지 않는다. 기준이 바뀌는 것은 바로 아래 문구가 함께 말해 준다.
    val progress = when {
        ride.isConfirmed -> if (ride.members.isNotEmpty()) {
            (arrived.toFloat() / ride.members.size).coerceIn(0f, 1f)
        } else {
            0f
        }
        ride.capacity > 0 -> (foundCount.toFloat() / ride.capacity).coerceIn(0f, 1f)
        else -> 0f
    }

    // 지도에 그릴 값들. 4초 폴링마다 ride 인스턴스는 새로 오지만 좌표·폴리라인 값은 같으므로
    // (LatLng 은 값 비교) 아래 remember 들이 다시 돌지 않는다 — 카메라가 폴링마다 튀지 않는 이유다.
    val originPosition = latLngOrNull(ride.originLat, ride.originLng)
    val destinationPosition = latLngOrNull(ride.destinationLat, ride.destinationLng)
    val myPosition = latLngOrNull(myLocation?.latitude, myLocation?.longitude)
    val routePath = remember(ride.routePolyline) {
        ride.routePolyline?.let(::decodePolyline).orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceCanvas)) {
        StatusBarSpacer()

        // 헤더 — 뒤로가기는 탐색 취소와 동일 (진행 화면 이탈 = 14 홈).
        //
        // **제목을 두지 않는다**(디자인 QA W1, 2026-11). 예전엔 지금 단계(「같이 탈 사람 찾는 중」 등)를
        // 머리줄 · 지도 위 배지 · 시트 헤드라인이 **세 번** 말했다. 단계는 시트 헤드라인 하나가 말하고,
        // 시트를 접었을 때는 지도 위 배지가 대신한다 — 머리줄은 뒤로가기만 남긴다.
        MoyeotaTopBar(
            title = "",
            onBack = { if (canLeave) cancelConfirming = true else onExitKeepingParty() },
        )

        // 지도(헤더 아래 전부) 위에 드래그 시트 — 16 도착지 확인과 같은 메커니즘
        MapSheetScaffold(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // 16 보다 지도를 더 남긴다. 21 의 펼침 콘텐츠(안내 · 모인 사람 · 조건 카드)는
            // 16 의 매칭 조건 카드보다 짧아서, 16 과 같은 160dp 로 두면 상세 영역이 남아
            // 조건 카드와 「그만 찾기」 사이가 휑하게 빈다. 남는 만큼 지도에 준다.
            // 상세 콘텐츠(안내 배너 · 모인 사람 N줄 · 조건 카드 3줄 · 안내 문구)가 멤버가 늘수록
            // 길어진다. 실기에서 2명일 때 조건 카드 마지막 줄과 안내 문구가 잘려 나가는 것을 확인해
            // 16 과 같은 160dp 로 되돌렸다 — 펼침은 정보를 보는 상태이고, 지도는 접힘이 맡는다.
            // (「탐색 반경」 줄이 빠져 카드가 한 줄 짧아졌다. 여유가 늘었을 뿐이라 값은 그대로 둔다.)
            mapRevealHeight = 160.dp,
            // 접힘 = 핸들 + 헤드라인 + 인원 문구 + 진행바 + 「그만 찾기」
            //
            // 약속 흐름의 두 단계는 푸터가 더 길다. **추정치여도 되는 값**이라(MapSheetDefaults
            // KDoc: 어긋나면 펼침에서 남는 지도 높이만 달라진다) 블록 높이를 눈대중으로 더한다.
            // 그래도 더해 주는 이유: 접힘 상태에서 프리셋 칩이나 권한 안내가 잘리면 **접힌 화면에서
            // 아무것도 할 수 없다** — 접힘은 「지도를 넓게 보는 상태」일 뿐이어야 한다.
            collapsedSheetHeight = when {
                // 「몇 분 뒤에 만날까요?」 + 칩 한 줄 + 1시간 한도 안내
                stage == MeetingStage.CONFIRM_BY_ME -> 288.dp
                // 권한 안내 배너 + 「위치 권한 허용」 버튼이 도착 버튼 자리를 둘로 늘린다
                stage == MeetingStage.ARRIVE_OPEN && !locationGranted -> 264.dp
                else -> 196.dp
            },
            background = { sheet ->
                val widthDp = sheet.containerWidth.value
                val visibleMapHeightDp = sheet.visibleMapHeight.value
                // **출발–도착 전체**(경로 포함)가 들어오게 맞춘다. 펼침·접힘은 보이는 지도 높이만
                // 다르고([visibleMapHeight] 가 그걸 알려 준다) 맞출 대상은 같다 —
                // 반경 원이 사라진 뒤로 펼침에서 따로 출발지 주변만 당겨 볼 이유가 없어졌다.
                // 앵커가 바뀔 때만 값이 변하므로 사용자가 손으로 잡아 둔 줌·중심을 덮어쓰지 않는다.
                val fitted = remember(
                    originPosition,
                    destinationPosition,
                    routePath,
                    widthDp,
                    visibleMapHeightDp,
                ) {
                    // 좌표가 하나뿐이면(목록 응답으로 만든 Ride 등) null 이고, 아래에서
                    // 출발지 중심 기본 줌으로 떨어진다 — 없는 범위를 그려 메우지 않는다.
                    val points = routePath + listOfNotNull(originPosition, destinationPosition)
                    fitMapCamera(points = points, widthDp = widthDp, heightDp = visibleMapHeightDp)
                }
                // 시트에 가리는 만큼을 지도에 알려 카메라 중심이 보이는 영역 기준으로 잡히게 한다
                val mapContentPadding = remember(sheet.settledSheetHeight) {
                    PaddingValues(bottom = sheet.settledSheetHeight)
                }

                if (originPosition != null) {
                    RouteMapView(
                        modifier = Modifier.fillMaxSize(),
                        routePath = routePath,
                        originPosition = originPosition,
                        destinationPosition = destinationPosition,
                        myPosition = myPosition,
                        center = fitted?.first ?: originPosition,
                        zoom = fitted?.second ?: 14.0,
                        contentPadding = mapContentPadding,
                    )
                } else {
                    // 좌표 없는 방(목록 응답으로 만든 Ride 등) — 지도 대신 자리표시자
                    MapPlaceholder(modifier = Modifier.fillMaxSize())
                }

                // 레이더 애니메이션을 대신하는 상태 배지. 지도가 배경이 된 이상 큰 레이더는
                // 지도를 가리기만 해서 「찾는 중」이라는 사실만 남긴다.
                MapOverlayPill(modifier = Modifier.align(Alignment.TopStart).padding(MoyeotaSpacing.ScreenGutter)) {
                    SearchingBadgeContent(
                        // 확정된 방에서는 약속 시각이 **지도 위에서도** 보여야 한다 — 시트를 접어
                        // 지도를 넓게 보는 동안에도 몇 시에 만나는지를 잃지 않는다.
                        // 시각을 못 읽으면 지어내지 않고 「약속 확정」만 적는다.
                        text = when {
                            ride.isConfirmed ->
                                meetingTimeLabel(ride.departureAt)?.let { "$it 약속" } ?: "약속 확정"
                            isFull -> "채팅으로 만나요"
                            else -> "같이 탈 사람 찾는 중"
                        },
                        // 펄스는 「지금도 사람을 찾고 있다」는 신호다 — 다 모였거나 확정된 방에서는
                        // 돌아가는 일이 없으므로 멈춘다(움직이는 점은 그 자체로 진행을 약속한다).
                        steady = isFull || ride.isConfirmed,
                    )
                }
            },
            sheetTop = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Text(
                        text = when {
                            // 확정된 방의 헤드라인은 **약속 시각 그 자체**다. 시각을 못 읽으면
                            // 지어내지 않고 「약속 시간이 정해졌어요」로 떨어진다(meetingHeadline).
                            ride.isConfirmed -> meetingHeadline(ride.departureAt)
                            isFull -> "같이 탈 사람이 다 모였어요"
                            // 「보통 2분 안에 모여요」는 지웠다 — 근거가 될 매칭 소요 통계가 없다.
                            // 기다리는 사람에게 지킬 수 없는 시각을 약속하는 자리다(2026-10-13).
                            else -> "같이 탈 사람을 찾고 있어요"
                        },
                        style = MoyeotaType.HeadingXl,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            // 확정 뒤에 기다리는 것은 사람이 아니라 **도착**이다. 진행바도 같은
                            // 기준으로 바뀌므로(progress) 문구가 그 기준을 말해 줘야 한다.
                            ride.isConfirmed && ride.members.isNotEmpty() ->
                                "${ride.members.size}명 중 ${arrived}명 도착"
                            // 「나 제외」 인원은 isMe 로 센다 — 서버가 publicId 를 주기 전에는 셀 수 없던 값이다
                            otherCount > 0 ->
                                "지금 같은 방향 ${foundCount}명(나 외 ${otherCount}명) · 목표 ${ride.capacity}명"
                            else -> "지금 같은 방향 ${foundCount}명 · 목표 ${ride.capacity}명"
                        },
                        style = MoyeotaType.BodySm.tabularNums(),
                        color = MoyeotaColor.TextMute,
                    )
                    Spacer(Modifier.height(MoyeotaSpacing.Sm))

                    // 진행 바 — 서버가 내려준 실제 인원/정원 비율. 바닥은 구분선과 같은 Hairline(W3)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MoyeotaColor.Hairline, CircleShape),
                    ) {
                        if (progress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .background(MoyeotaColor.Primary500, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            },
            sheetDetail = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    // 정원이 찬 뒤 무슨 일이 일어나는지 알려주는 유일한 지점.
                    // 기사가 없으므로 할 일은 하나다 — 채팅으로 만날 곳을 정하고, 다 탔으면
                    // 방장이 「합승 완료」로 닫는다.
                    NoticeBanner(
                        // 확정된 방은 **기다리는 상태가 아니다** — 만날 시간이 정해져 있다.
                        // WAITING(주황)으로 두면 끝난 일을 미결로 보이게 한다.
                        kind = if (isFull && !ride.isConfirmed) NoticeKind.WAITING else NoticeKind.INFO,
                        text = when {
                            // 확정된 방에서는 만날 곳·시간이 이미 정해졌다. 여기서 또 「정해요」라고
                            // 하면 끝난 일을 시키는 셈이고, 다음에 할 일(도착·출발)은 푸터가 말한다.
                            ride.isConfirmed -> "약속 시간에 출발지에서 만나요"
                            // 방장: 약속 흐름이 주인공이지만 「합승 완료」도 함께 보인다 —
                            // 이미 만나서 같이 탄 사람들은 약속 시간을 정할 이유가 없다.
                            stage == MeetingStage.CONFIRM_BY_ME ->
                                "채팅으로 만날 곳을 정하고 약속 시간을 정해요. 이미 같이 탔다면 「합승 완료」를 눌러 주세요"
                            // 「눌러 주세요」는 **버튼이 보이는 사람에게만** 하는 말이다 —
                            // 비방장에게 하면 없는 버튼을 누르라고 시키는 셈이다.
                            canFinish -> "채팅으로 만날 곳을 정해요. 다 타면 「합승 완료」를 눌러 주세요"
                            // 비방장: 누가 끝내는지는 footer 안내문이 이름까지 말한다 — 여기서 겹쳐 쓰지 않는다.
                            isFull -> "채팅으로 만날 곳을 정해요"
                            else -> "정원이 차면 채팅으로 만날 곳을 정해요"
                        },
                    )
                    Spacer(Modifier.height(16.dp))

                    // 지금까지 모인 사람 — 서버 members[] 를 그대로 그린다(나 포함).
                    // 대기 화면에서 가장 궁금한 것이 "누가 같이 타나"라 조건 카드보다 위에 둔다.
                    //
                    // 확정된 방에서는 같은 목록이 **도착 현황**을 겸한다. 「출발」을 누르면 노쇼로
                    // 기록될 사람이 누구인지 확인할 수 있는 자리가 여기뿐이고, 확인 다이얼로그가
                    // 말하는 숫자([notArrivedCount])도 이 목록에서 나온다 — 눈으로 맞춰 볼 수 있다.
                    if (ride.members.isNotEmpty()) {
                        SectionLabel(
                            text = "지금 모인 사람",
                            trailing = {
                                // 「도착 2/3」 — 확정 전에는 도착 개념이 없어 null 이고 그리지 않는다.
                                arrivalCountLabel(ride)?.let { label ->
                                    Text(
                                        text = label,
                                        style = MoyeotaType.BodySm.tabularNums(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MoyeotaColor.Success600,
                                    )
                                }
                            },
                        )
                        ride.members.forEach { member ->
                            WaitingMemberRow(member = member, confirmed = ride.isConfirmed)
                        }
                        Spacer(Modifier.height(MoyeotaSpacing.Md))
                    }

                    // 조건 — 탭 시 22 탑승 상세.
                    //
                    // 판(배경)을 깔지 않고 시트 위에 바로 줄을 놓는다(디자인 QA W3). 흰 시트 안에 회색 판을 또
                    // 깔면 면이 겹겹이 쌓인다. 「왜 못 바꾸나」를 설명하던 한 줄도 지웠다 — 고칠 곳(연필·
                    // 「수정」)이 없는 줄이라는 것 자체가 그 말을 한다.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCardClick() },
                    ) {
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "출발지", value = ride.origin)
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "도착지", value = ride.destination)
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "매칭 조건", value = conditionLabel)
                        // 「탐색 반경」 행은 지웠다(2026-10-14) — 서버 고정값이고 매칭이 읽지 않는
                        // 숫자였다(KDoc 참고). 지금 카드에 남은 건 전부 실제로 방을 정하는 값이다.
                    }
                    Spacer(Modifier.height(16.dp))
                }
            },
            sheetFooter = {
                if (actionErrorMessage != null) {
                    NoticeBanner(
                        kind = NoticeKind.ERROR,
                        text = actionErrorMessage,
                        modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter, vertical = MoyeotaSpacing.Xs),
                    )
                }

                // 채팅방이 생긴 뒤에만 뜬다. 채팅 **탭**이 아니라 채팅방을 스택에 쌓아 열기 때문에
                // 뒤로가기 한 번이면 이 대기 화면으로 돌아온다.
                //
                // 옅은 파랑 알약([SoftButton]) — 주 CTA(파란 솔리드)와 같은 모양·높이라 한 가족으로 읽히고,
                // 색은 한 단계 아래라 「합승 완료」·「출발」 같은 주 행동을 가리지 않는다(디자인 QA W2).
                if (onOpenChat != null) {
                    SoftButton(
                        text = "채팅 열기",
                        onClick = onOpenChat,
                        modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                    )
                    Spacer(Modifier.height(MoyeotaSpacing.Xs))
                }

                // 약속 흐름 — 단계가 **정확히 하나의 블록**을 고른다. `when` 을 열거형 전체로 두어
                // 서버에 단계가 늘면(MeetingStage 에 값이 더해지면) 컴파일이 먼저 깨지게 한다.
                when (stage) {
                    // 방장: 「몇 분 뒤에 만날까요?」 + 프리셋 칩. 자유 입력을 두지 않는 근거는
                    // MEETING_PRESET_MINUTES KDoc — 서버가 1시간 안만 받아서, 못 고르게 하는 UI 가 낫다.
                    MeetingStage.CONFIRM_BY_ME -> {
                        MeetingPresetBlock(
                            enabled = !actionInProgress,
                            selectedMinutes = confirmingPreset?.minutes,
                            onPick = { minutes ->
                                confirmingPreset = ConfirmingPreset(minutes, meetingPresetIso(now, minutes))
                            },
                            modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                        )
                        Spacer(Modifier.height(MoyeotaSpacing.Xs))
                    }
                    // 비방장: **비활성 버튼을 두지 않는다.** 누를 수 없는 버튼은 왜 못 누르는지
                    // 말해 주지 못하므로, 누가 정하는지와 정해지면 어떻게 되는지를 한 줄로 적는다.
                    MeetingStage.CONFIRM_BY_HOST -> {
                        NoticeBanner(
                            kind = NoticeKind.INFO,
                            text = meetingHostNotice(ride.members.hostDisplayNameOrNull()),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // 확정됐지만 도착 창이 아직 안 열렸다. **숫자를 말해 줘야** 언제 다시 보면 되는지 안다.
                    MeetingStage.CONFIRMED_WAITING -> {
                        NoticeBanner(
                            kind = NoticeKind.INFO,
                            text = ARRIVE_NOT_OPEN_NOTICE,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    MeetingStage.ARRIVE_OPEN -> {
                        // 약속 시간이 지났는데 내가 아직 도착을 알리지 않았다 — 남들 화면에는 「출발」이
                        // 있는데 내 화면에는 없는 상태다. 아무 말도 하지 않으면 그 차이를 알 수 없다.
                        if (meetingTimePassed) {
                            NoticeBanner(
                                kind = NoticeKind.WAITING,
                                text = DEPART_NEEDS_ARRIVAL_NOTICE,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (!locationGranted) {
                            // 권한이 없으면 **비활성 도착 버튼을 두지 않는다** — 17 탐색과 같은 처방:
                            // 왜 필요한지 적고, 다시 요청할 버튼을 준다. 21 은 진입 시 권한을 자동
                            // 요청하지 않으므로(대기 화면의 본질이 지도가 아니다) 이 자리가 그 사람이
                            // 권한 다이얼로그를 처음 보는 지점이다.
                            NoticeBanner(
                                kind = NoticeKind.INFO,
                                text = ARRIVE_NEEDS_PERMISSION_NOTICE,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            PrimaryCtaButton(
                                text = "위치 권한 허용",
                                onClick = onRequestLocationPermission,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        } else {
                            PrimaryCtaButton(
                                // 좌표가 아직 없으면 **보낼 것이 없다.** 지어낸 좌표를 보내면 서버의
                                // 100m 판정이 거짓말 위에서 돌므로, 기다리라고 말하고 막는다.
                                text = when {
                                    actionInProgress -> "알리는 중…"
                                    myLocation == null -> ARRIVE_LOCATING_LABEL
                                    else -> "출발지 도착했어요"
                                },
                                onClick = { myLocation?.let(onArrive) },
                                enabled = !actionInProgress && myLocation != null,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // 도착을 알렸고 약속 시간 전 — 누를 것이 없다. 한 일과 다음에 일어날 일만 적는다.
                    MeetingStage.ARRIVED_WAITING -> {
                        NoticeBanner(
                            kind = NoticeKind.SUCCESS,
                            text = ARRIVED_WAITING_NOTICE,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // 「출발」 — 되돌릴 수 없고 **남에게 제재를 만든다.** 확인 다이얼로그가 반드시 끼어든다.
                    MeetingStage.DEPART_OPEN -> {
                        PrimaryCtaButton(
                            text = if (actionInProgress) "출발하는 중…" else "출발",
                            onClick = { departConfirming = true },
                            enabled = !actionInProgress,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // 약속 흐름이 끼어들지 않는 단계. 아래 「합승 완료」·「그만 찾기」가 예전과 같다.
                    MeetingStage.RECRUITING, MeetingStage.FINISH_ONLY, MeetingStage.CLOSED -> Unit
                }

                // 정원이 찼을 때 — 합승을 끝내는 자리. **확정 전 방 전용**이다: 확정된 방을 끝내는
                // 길은 「출발」 하나이고(409 USE_DEPART_FOR_CONFIRMED_PARTY), 완료로 닫히면
                // **노쇼 기록 없이** 방이 끝나 약속을 어긴 사람이 제재를 면한다 — canFinishParty KDoc.
                //
                // **방장에게만 버튼**을 준다(서버가 방장 전용으로 좁혔다).
                // 비방장에게는 **비활성 버튼을 두지 않는다**: 누를 수 없는 버튼은 왜 못 누르는지
                // 말해 주지 못한다. 대신 같은 자리에 누가 방을 끝내는지를 한 줄로 적는다 —
                // 숨기기만 하면 정원이 찬 뒤 할 일이 하나도 없는 화면이 된다.
                if (canFinish) {
                    PrimaryCtaButton(
                        text = if (actionInProgress) "완료하는 중…" else "합승 완료",
                        onClick = { finishConfirming = true },
                        enabled = !actionInProgress,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                } else if (showsFinishNotice) {
                    NoticeBanner(
                        kind = NoticeKind.INFO,
                        text = finishHostNotice(ride.members.hostDisplayNameOrNull()),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // 나가기 — 매칭 시작·준비 버튼은 도메인에서 사라졌다. 접힘 상태에서도 보이도록 시트 하단에 고정한다.
                //
                // 비활성이 되는 경우가 **둘**이다([canLeaveParty]).
                // ① 닫힌 방 한 프레임 — 서버가 방을 닫았는데 Route 가 아직 화면을 떠나지 않은 순간.
                //    거기서 눌리면 409 로만 끝나므로 누를 수 없게 하고 **실제로 일어난 일**을 적는다.
                // ② **확정된 방** — 서버가 확정과 함께 이탈을 막는다. 이 문구가 중요한 이유는
                //    대가가 제재이기 때문이다: 나갈 수 있다고 믿은 사람이 약속 장소에 가지 않으면
                //    「출발」이 그 사람을 노쇼로 기록한다. 그래서 「못 나간다」를 **지금** 말한다.
                //
                // 숨기지 않고 비활성으로 남기는 이유: 이 자리가 비면 확정된 방에서 뒤로 갈 길이
                // 사라진 것처럼 보인다. 화면을 벗어나는 길은 헤더의 뒤로가기(방 유지)로 열려 있다.
                //
                // 바탕 없는 빨간 글자 알약([DangerTextButton]) — 디자인 문서의 「요청 취소」(button-danger).
                // 예전 회색 r16 상자는 위 파란 알약과 모양이 달라 버튼 셋이 세 모양이었고, 회색은 「되돌리기
                // 어려운 이탈」을 말하지 못했다(디자인 QA W2). 누르면 늘 확인 다이얼로그가 먼저 뜬다.
                DangerTextButton(
                    text = when {
                        !canLeave && ride.isConfirmed -> "약속을 정한 뒤에는 나갈 수 없어요"
                        !canLeave -> "이미 끝난 방이에요"
                        actionInProgress -> "나가는 중…"
                        // 「그만 찾기」가 아닌 건 **단계** 때문이다 — 더는 찾는 중이 아니다.
                        finishStage -> "방 나가기"
                        else -> "그만 찾기"
                    },
                    onClick = { cancelConfirming = true },
                    enabled = canLeave && !actionInProgress,
                    modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                )
                Spacer(Modifier.height(MoyeotaSpacing.Xs))
                NavigationBarSpacer()
            },
        )
    }

    // 「약속 시간 정하기」 확인 — **되돌릴 수 없는 전이**라 반드시 한 번 묻는다.
    //
    // 되돌릴 수 없는 것이 셋이다: 모집이 끝나고 · 아무도 나갈 수 없게 되고 · 시간을 바꿀 수 없다
    // (409 ALREADY_CONFIRMED). 그중 ②가 사용자에게 가장 무겁다 — 약속을 지키지 못하면 노쇼가 된다.
    //
    // 제목이 고른 **시각을 숫자로 되읽어 준다.** 칩에는 「10분」만 적혀 있어서, 그게 몇 시인지
    // 확인할 기회가 여기 한 번뿐이다. 그래서 **여기 적힌 시각이 그대로 서버로 간다** —
    // 칩을 누른 순간 만든 값([confirmingPresetAt])을 제목도 요청도 함께 쓴다.
    confirmingPreset?.let { preset ->
        val timeLabel = meetingTimeLabel(preset.departureAt)
        MoyeotaConfirmDialog(
            title = meetingConfirmTitle(timeLabel),
            body = MEETING_CONFIRM_DIALOG_BODY,
            confirmText = "약속 정하기",
            onConfirm = {
                confirmingPreset = null
                onConfirmMeeting(preset.departureAt)
            },
            dismissText = "아직이요",
            onDismiss = { confirmingPreset = null },
        )
    }

    // 「출발」 확인 — **이 작업에서 가장 조심한 다이얼로그다.**
    //
    // 누르면 모두의 방이 끝나고, 그 순간 도착하지 않은 사람은 **노쇼로 기록된다** — 약속 시간부터
    // (노쇼 횟수 × 24시간) 동안 방을 만들거나 참여할 수 없다. 누르는 사람은 보통 자기 제재가 아니라
    // **남의 제재**를 만들게 되므로, 숫자를 보여 주지 않으면 그 무게를 알 수 없다.
    // 확인 버튼을 Danger 색으로 두는 이유도 같다 — 되돌릴 수 없고 남에게 손해를 준다.
    if (departConfirming) {
        MoyeotaConfirmDialog(
            title = "지금 출발할까요?",
            body = departConfirmMessage(notArrivedCount(ride)),
            confirmText = "출발",
            onConfirm = {
                departConfirming = false
                onDepart()
            },
            dismissText = "아직이요",
            onDismiss = { departConfirming = false },
            destructive = true,
        )
    }

    // 「합승 완료」 확인 — 나 혼자가 아니라 **모두의** 방이 닫히는 행동이라 반드시 한 번 묻는다
    if (finishConfirming) {
        MoyeotaConfirmDialog(
            title = "합승을 완료할까요?",
            body = "같이 탄 사람 모두의 방이 종료돼요. 다 타고 나서 눌러 주세요",
            confirmText = "합승 완료",
            onConfirm = {
                finishConfirming = false
                onFinish()
            },
            dismissText = "아직이요",
            onDismiss = { finishConfirming = false },
        )
    }

    // 뒤로가기·「그만 찾기」 공통 확인 — 확인해야만 실제로 방을 나간다
    if (cancelConfirming) {
        MoyeotaConfirmDialog(
            title = "매칭을 그만둘까요?",
            body = "지금 나가면 이 방에서 빠지고 처음부터 다시 찾아야 해요",
            confirmText = "그만 찾기",
            onConfirm = {
                cancelConfirming = false
                onCancelSearch()
            },
            dismissText = "계속 찾기",
            onDismiss = { cancelConfirming = false },
            destructive = true,
        )
    }
}

/**
 * 「몇 분 뒤에 만날까요?」 + 프리셋 칩 — **방장에게만** 보이는 약속 확정 블록.
 *
 * 칩에는 숫자만 적고(「10분」) 단위는 위의 물음이 말한다 — 393dp 폭에서 「10분 뒤」 다섯 개는 줄을
 * 넘긴다. 몇 시가 되는지는 **확인 다이얼로그가 되읽어 준다**([meetingConfirmTitle]).
 *
 * 누르면 바로 보내지 않고 [onPick] 으로 확인 다이얼로그를 띄운다 — 확정은 되돌릴 수 없고, 그
 * 순간부터 아무도 방을 나갈 수 없다. 누른 칩이 [selectedMinutes] 로 돌아와 선택 상태로 남으므로
 * 다이얼로그 뒤에서 **무엇을 눌렀는지** 보인다.
 *
 * [MoyeotaChip] 을 쓰는 이유: 16 매칭 조건 칩과 **같은 종류의 선택**이고, 전용 버튼을 새로 만들면
 * 같은 일을 하는 두 번째 칩 모양이 생긴다(디자인 시스템 규칙: 2개 이상 화면에서 쓰이는 것만 승격).
 */
@Composable
private fun MeetingPresetBlock(
    enabled: Boolean,
    selectedMinutes: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            // 보내는 중에는 물음 대신 진행을 적는다 — 칩이 흐려지기만 하면 왜 안 눌리는지 알 수 없다.
            // 중복 제출은 `runAction` 이 이미 막으므로, 여기서는 **그 사실을 보여 주기만** 한다.
            text = if (enabled) MEETING_PRESET_PROMPT else "약속 시간을 정하는 중…",
            style = MoyeotaType.BodyStrong,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.height(MoyeotaSpacing.Xxs))
        Text(
            text = MEETING_CONFIRM_HELP,
            style = MoyeotaType.CaptionMd,
            color = MoyeotaColor.TextMute,
        )
        Spacer(Modifier.height(MoyeotaSpacing.Xxs))
        // 칩을 **자기 폭대로** 두고 줄을 가로 스크롤로 만든다. `weight(1f)` 로 다섯 칸을 균등
        // 분할하면 기본 글꼴에서는 들어맞지만(칩당 ≈67dp), 글꼴 크기를 키운 기기에서 14sp 가
        // 그만큼 커져 「10분」이 두 줄로 접히거나 잘린다 — 접근성 설정 하나로 깨지는 레이아웃이다.
        // 자기 폭이면 기본에서는 스크롤이 생기지 않고(≈314dp < 361dp), 커졌을 때만 밀린다.
        // 칩 사이 8dp — 칩은 보이는 높이 36dp 에 터치 48dp 라(공용 칩) 위아래 여백은 칩이 이미 갖는다.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MoyeotaSpacing.Xs),
        ) {
            MEETING_PRESET_MINUTES.forEach { minutes ->
                MoyeotaChip(
                    text = "${minutes}분",
                    selected = selectedMinutes == minutes,
                    onClick = { if (enabled) onPick(minutes) },
                    enabled = enabled,
                )
            }
        }
        Text(
            text = MEETING_PRESET_LIMIT_NOTE,
            style = MoyeotaType.CaptionMd,
            color = MoyeotaColor.TextMute,
        )
    }
}

/**
 * 「도착」 배지 — 확정된 방에서 그 멤버가 서버에 도착을 알렸을 때만 붙는다([showsArrivedBadge]).
 *
 * 색을 Success 로 두는 이유: 「나」(Primary)·「방장」(회색)과 **다른 종류의 사실**이다. 앞의 둘은
 * 신원·역할이고 이건 **지금 일어난 일**이라, 같은 색으로 그리면 같은 종류의 꼬리표로 읽힌다
 * ([HostBadge] 가 [MeBadge] 와 색을 달리한 것과 같은 근거).
 *
 * **미도착에는 배지를 붙이지 않는다.** 확정 직후에는 전원이 미도착이라 「미도착」 배지가 모든 줄에
 * 붙어 아무것도 구별해 주지 못하고, 늦은 사람을 공개적으로 표시하는 꼴이 된다. 몇 명이 남았는지는
 * 「도착 2/3」([arrivalCountLabel])과 출발 확인 문구가 숫자로 말한다.
 */
@Composable
private fun ArrivedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MoyeotaColor.Success50, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = "도착", style = MoyeotaType.CaptionSm, fontWeight = FontWeight.SemiBold, color = MoyeotaColor.Success600)
    }
}

// 대기 화면의 멤버 한 줄 — 아바타(이니셜) · 닉네임(+나·방장·도착 배지) · 탑승 횟수.
// 여기서는 프로필로 들어가지 않는다(23 진입은 20·22 에서만) — 대기 중 흐름을 끊지 않기 위해서다.
//
// [confirmed] 가 필요한 이유: 도착 배지는 **확정된 방에서만** 뜻이 있다(확정 전에는 도착 개념이
// 없고, 서버도 그때는 arrived 를 세우지 않는다). 그 판정은 [showsArrivedBadge] 한 곳이다.
@Composable
private fun WaitingMemberRow(member: User, confirmed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MoyeotaSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(user = member, size = 36.dp)
        Spacer(Modifier.width(MoyeotaSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayNickname,
                    style = MoyeotaType.BodyStrong,
                    color = if (member.isWithdrawn) MoyeotaColor.TextMute else MoyeotaColor.InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isMe) {
                    Spacer(Modifier.width(MoyeotaSpacing.Xs))
                    MeBadge()
                }
                // 「방장」 — 서버가 멤버마다 내려 주는 값이다(추정이 아니다). 내 줄에는 붙지 않는다:
                // 서버가 host 를 빼먹었을 때의 폴백이 내 줄만 true 로 만들기 때문이다(showsHostBadge KDoc).
                if (member.showsHostBadge) {
                    Spacer(Modifier.width(MoyeotaSpacing.Xs))
                    HostBadge()
                }
                // 「도착」 — 확정된 방에서 서버가 arrived 를 세운 줄에만. 내 줄에도 붙는다:
                // 방장 배지와 달리 폴백이 만들어 낸 값이 아니라 **서버가 말한 사실**이라
                // 내 줄에서도 거짓이 될 수 없고, 내가 알렸는지를 확인할 자리가 여기다.
                if (showsArrivedBadge(member, confirmed)) {
                    Spacer(Modifier.width(MoyeotaSpacing.Xs))
                    ArrivedBadge()
                }
            }
            Text(
                text = if (member.isWithdrawn) "탈퇴한 회원" else member.rideCountLabel,
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
            )
        }
    }
}

@Composable
private fun ConditionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
        Spacer(Modifier.width(MoyeotaSpacing.Sm))
        // 출발지·도착지 이름은 16 에서 역지오코딩된 전체 주소일 수 있다(「부산광역시 …63번길 2 …」).
        // 남는 폭을 다 쓰되 라벨을 밀어내지 않도록 weight 로 잡고, 넘치면 말줄임한다.
        Text(
            text = value,
            style = MoyeotaType.BodyMd,
            color = MoyeotaColor.InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 지도 위 「찾는 중」 배지 — 예전 220dp 레이더 애니메이션을 대신한다.
 *
 * 레이더는 **삭제**했다. 배경이 실지도가 된 뒤로는 동심원이 지도를 가리기만 했고, 「어느 범위에서
 * 찾는지」는 애초에 앱이 말할 수 있는 것이 아니다 — 매칭은 반경을 보지 않는다(2026-10-14 에
 * 반경 원도 같은 이유로 지웠다). 남길 가치가 있던 건 「지금도 돌아가고 있다」는 신호 하나라,
 * 점 하나의 펄스로 줄였다.
 *
 * 문구를 인자로 받는 것은 2026-11-06 의 변경이다. 예전에는 「정원이 찼는가」만 받아 두 문구를 골랐는데,
 * 확정된 방이 생기면서 **지도 위에서도 약속 시각이 보여야** 했다(시트를 접어 지도를 넓게 보는 동안
 * 몇 시에 만나는지를 잃지 않게). 어떤 문구인지는 호출부가 더 잘 알므로 여기서 분기를 늘리지 않는다.
 *
 * @param steady 펄스를 멈춘다. 펄스는 「지금도 사람을 찾고 있다」는 신호라, 다 모였거나 확정된 방에서
 *   계속 깜빡이면 **끝난 일이 진행 중인 것처럼** 보인다 — 움직이는 점은 그 자체로 진행을 약속한다
 */
@Composable
private fun SearchingBadgeContent(text: String, steady: Boolean) {
    val transition = rememberInfiniteTransition(label = "searching")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "pulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (steady) {
                        MoyeotaColor.Primary500
                    } else {
                        MoyeotaColor.Primary500.copy(alpha = pulse)
                    },
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MoyeotaType.BodySm,
            fontWeight = FontWeight.SemiBold,
            color = MoyeotaColor.InkPrimary,
        )
    }
}

/**
 * 「나 외 M명」에 쓰는 **나를 뺀** 인원 — 내가 목록에 있을 때만 센다.
 *
 * 세지 않는 경우가 둘이다. 둘 다 `isMe` 가 아무 멤버에도 서지 않는 경우이고, 그때 "나를 뺀 인원"은
 * 전체 인원과 같아진다 — 그러면 화면이 **「지금 같은 방향 2명(나 외 2명)」** 이라는 자기모순을 말한다.
 *
 * | 경우 | 왜 isMe 가 없나 |
 * |---|---|
 * | **내가 이 방 멤버가 아니다** | 유령 대기방 — 서버 목록에 내가 없다(`MatchWaitingViewModel.membershipLost` 가 화면을 떠나게 하지만, 그 전 한 프레임을 그린다) |
 * | 식별자가 없는 목록 | 목록·생성 응답으로 만든 [Ride] 는 자리 표시용 멤버라 publicId 가 없다(`placeholderMembers`) |
 *
 * 0 을 돌려주면 호출부가 **괄호 없는 문구**(「지금 같은 방향 2명 · 목표 3명」)를 쓴다 — 모르는 값을
 * 지어내지 않고 **말하지 않는** 쪽이다. 문구 포맷 자체는 건드리지 않았다.
 */
internal fun otherMemberCount(ride: Ride): Int =
    if (ride.members.none { it.isMe }) 0 else ride.members.count { !it.isMe }

/**
 * 프리뷰의 **고정 시각**. `Instant.now()` 를 쓰지 않는 이유: 프리뷰가 렌더될 때마다 다른 단계를
 * 그리면 어떤 상태를 보고 있는지 알 수 없고, 약속 시각이 과거가 되는 순간 전부 같은 그림이 된다.
 *
 * 아래 프리뷰들은 이 시각을 기준으로 `departureAt` 만 바꿔 **단계 하나씩**을 고정한다
 * ([meetingStage] 의 표 그대로다). 이 한 쌍이 어긋나면 그림이 조용히 다른 단계를 보여 주므로,
 * 숫자를 고칠 때는 두 값을 함께 본다.
 */
private val previewNow: Instant = Instant.parse("2026-11-06T06:00:00Z")

/** 프리뷰 전용 — 나(방장) + 동승자 둘. 도착 여부만 바꿔 쓴다. */
private fun previewMembers(meArrived: Boolean = false, otherArrived: Boolean = false) = listOf(
    User("me", "부산가자", "", 0.0, 5, isMe = true, isHost = true, hasArrived = meArrived),
    User("u2", "해운대", "", 0.0, 12, hasArrived = otherArrived),
    User("u3", "광안리", "", 0.0, 3, hasArrived = otherArrived),
)

/**
 * 프리뷰 전용 — 정원이 찬 방 한 틀. 확정 여부·약속 시각·멤버만 갈아 끼운다.
 *
 * [members] 에 **기본값을 두지 않는다.** `ConfigChangeStateTest` 의 「화면 기본 인자로 도메인
 * 데이터를 지어내지 않는다」가 presentation 전체를 훑어 `List<User> = …` 형태를 막는다 —
 * 프리뷰 헬퍼라도 예외를 두면 그 그물에 구멍이 생기고, 구멍은 프로덕션 화면에도 열린다.
 */
private fun previewFullRide(
    members: List<User>,
    isConfirmed: Boolean = false,
    departureAt: String? = null,
    isHostedByMe: Boolean = true,
) = waitingRideDummy.copy(
    capacity = 3,
    status = RideStatus.MATCHED,
    isConfirmed = isConfirmed,
    departureAt = departureAt,
    members = if (isHostedByMe) members else members.map { it.copy(isHost = it.id == "u2") },
)

/** 모집 중 — 약속 흐름이 끼어들지 않는다(예전 그림 그대로). */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingScreenPreview() {
    MatchWaitingScreen(ride = waitingRideDummy, now = previewNow)
}

/** 정원 참 · 미확정 · **방장** — 「몇 분 뒤에 만날까요?」 프리셋 + 「합승 완료」. */
@Preview(name = "약속 정하기(방장)", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingConfirmByMePreview() {
    MatchWaitingScreen(ride = previewFullRide(previewMembers()), now = previewNow, foundCount = 3)
}

/** 정원 참 · 미확정 · 비방장 — 방장이 정한다는 안내. **비활성 버튼이 없다.** */
@Preview(name = "약속 정하기(비방장)", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingConfirmByHostPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(previewMembers(), isHostedByMe = false),
        now = previewNow,
        foundCount = 3,
    )
}

/** 확정 · 약속 30분 뒤 — 도착 창이 아직 안 열렸다(「약속 10분 전부터…」). */
@Preview(name = "확정 · 도착 전", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingConfirmedWaitingPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(previewMembers(), isConfirmed = true, departureAt = "2026-11-06T06:30:00Z"),
        now = previewNow,
        foundCount = 3,
    )
}

/** 확정 · 약속 5분 전 · 내가 미도착 — 「출발지 도착했어요」(권한·좌표 있음). */
@Preview(name = "확정 · 도착 가능", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingArriveOpenPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(
            members = previewMembers(otherArrived = true),
            isConfirmed = true,
            departureAt = "2026-11-06T06:05:00Z",
        ),
        now = previewNow,
        foundCount = 3,
        myLocation = UserCoordinates(latitude = 35.1688, longitude = 129.1306),
        locationGranted = true,
    )
}

/** 확정 · 도착 창 열림 · **위치 권한 거부** — 비활성 버튼 대신 이유 + 허용 버튼. */
@Preview(name = "확정 · 권한 없음", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingArriveNoPermissionPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(previewMembers(), isConfirmed = true, departureAt = "2026-11-06T06:05:00Z"),
        now = previewNow,
        foundCount = 3,
        locationGranted = false,
    )
}

/** 확정 · 내가 도착 · 약속 시간 전 — 누를 것이 없는 대기 상태. */
@Preview(name = "확정 · 도착 알림 뒤", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingArrivedWaitingPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(
            members = previewMembers(meArrived = true),
            isConfirmed = true,
            departureAt = "2026-11-06T06:05:00Z",
        ),
        now = previewNow,
        foundCount = 3,
        locationGranted = true,
    )
}

/**
 * 확정 · 내가 도착 · 약속 시간 지남 — 「출발」.
 *
 * 멤버 둘이 미도착인 상태를 고정한다: 이 그림에서 「출발」을 누르면 확인 다이얼로그가
 * **「아직 도착하지 않은 2명은 노쇼로 기록돼요」**라고 말한다([departConfirmMessage]).
 */
@Preview(name = "확정 · 출발 가능", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingDepartOpenPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(
            members = previewMembers(meArrived = true),
            isConfirmed = true,
            departureAt = "2026-11-06T05:55:00Z",
        ),
        now = previewNow,
        foundCount = 3,
        locationGranted = true,
    )
}

/** 확정 · 약속 시간 지남 · **내가 미도착** — 도착 버튼 + 왜 「출발」이 없는지. */
@Preview(name = "확정 · 늦은 미도착", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MatchWaitingLateNotArrivedPreview() {
    MatchWaitingScreen(
        ride = previewFullRide(
            members = previewMembers(otherArrived = true),
            isConfirmed = true,
            departureAt = "2026-11-06T05:55:00Z",
        ),
        now = previewNow,
        foundCount = 3,
        myLocation = UserCoordinates(latitude = 35.1688, longitude = 129.1306),
        locationGranted = true,
    )
}
