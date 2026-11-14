package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.ChevronIcon
import com.moyeota.core.designsystem.component.ExternalLinkIcon
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaConfirmDialog
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.MoyeotaTabHeader
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.SectionLabel
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.presentation.core.SupportLinkTargets
import com.moyeota.presentation.core.UserNameState
import com.moyeota.presentation.core.appVersionName
import com.moyeota.presentation.core.openAppNotificationSettings
import com.moyeota.presentation.core.openSupportEmail
import com.moyeota.presentation.core.openWebPage
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 35 · 마이페이지 · 설정 [S24]
 *
 * 진입: 하단탭 마이페이지
 *
 * 구성: 프로필 카드 → (제재 배너) → 「탑승」 카드 → 「설정」 카드 → 「안내」 카드 → 「계정」 카드 → 버전
 *
 * ```
 * 프로필 카드 (사진 · 이름 / 닉네임·사진 바꾸기)  ›
 * ▓ 노쇼 2건으로 11월 7일 오후 3:20까지 방을 만들거나 … ▓  ← 2026-11-06 추가, 제재 중에만
 * [탑승]  지난 탑승 기록                       ›   ← 2026-11-01 추가
 *         노쇼 기록                           ›   ← 2026-11-06 추가
 * [설정]  알림 설정                          ↗
 * [안내]  개인정보 처리방침 / 이용약관 / 문의하기   ↗   ← 한 카드, 구분선
 * [계정]  로그아웃                           ›
 *         탈퇴하기                                ← 카드 밖 회색 글자 링크 (2026-11 디자인 QA M2)
 *         탈퇴하면 계정과 저장한 정보가 사라지고…
 *                      v1.0                      ← 가운데
 * ```
 *
 * 이동(디스크립션):
 * - 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24 (onTabSelect)
 * - 프로필 카드 → 36 프로필 수정 (onEditProfile)
 * - 「지난 탑승 기록」 → 34 지난 탑승 기록 (onRideHistory)
 * - 「노쇼 기록」 → 38 노쇼 기록·이의 신청 (onNoShowRecords)
 * - 「알림 설정」 → OS 앱 알림 설정 (앱 밖)
 * - 「개인정보 처리방침」 · 「이용약관」 → 브라우저 (앱 밖)
 * - 「문의하기」 → 메일 앱, 제목·환경 정보 자동 입력 (앱 밖)
 * - 「로그아웃」 → 확인 다이얼로그 후 04a 로그인 (onLogout)
 * - 「탈퇴하기」 → 37 회원 탈퇴 (onDeleteAccount)
 *
 * ### 2026-10-14 재구성 — 무엇을 바꿨고 왜인가
 * 1. **「안내」 카드가 생겼다.** 약관·개인정보 주소가 없어 숨어 있던 행이 주소가 생겨 드러났다
 *    (`SupportLinks`). 스토어 심사는 **앱 안에서 개인정보 처리방침에 닿는 경로**를 본다.
 * 2. **로그아웃이 카드 행이 됐다.** 그전엔 13sp 회색 글자 한 줄이어서, 탈퇴(번듯한 카드 행)보다
 *    작았다 — 훨씬 자주 쓰는 기능이 더 작아 위계가 뒤집혀 있었다. 이제 탈퇴와 같은 56dp 행이고
 *    **같은 「계정」 카드 안에** 나란히 있다. 둘을 가르는 것은 크기가 아니라 색(탈퇴만 빨강)과
 *    확인 단계다: 로그아웃은 확인 다이얼로그, 탈퇴는 37 에서 비밀번호 + 또 한 번의 다이얼로그.
 *    (그전의 오탭 보호는 40dp 간격이었는데, 간격으로 떼어 놓으려면 로그아웃을 작게 둬야 했다.)
 * 3. **버전은 맨 아래 가운데로.** 로그아웃 줄 오른쪽에 있던 값이라, 로그아웃이 카드로 들어가면서
 *    같이 들어가면 행 안에 뜻이 다른 두 정보가 섞인다.
 *
 * ### 2026-11 디자인 QA — 떠 있는 카드를 걷어 냈다
 * - 카드의 그림자를 없앴다. 회색 바탕 위 흰 카드는 면의 차이만으로 묶음이 읽힌다 — 그림자까지 얹으면
 *   카드마다 떠 있어 화면 전체가 들뜬다(G4). 모서리 18 → 12dp, 안쪽 여백 20 → 16dp, 프로필 카드 104 → 80dp
 * - **「탈퇴하기」는 카드 밖으로 뗐다**(M2). 위 2번의 「색으로 가른다」는 한 카드 안에 빨간 행을 두는 방식이었는데,
 *   되돌릴 수 없는 동작은 자주 쓰는 행 옆이 아니라 **공간으로도** 떨어져 있어야 한다(destructive-nav-separation).
 *   그래서 로그아웃만 카드에 남기고, 탈퇴는 카드 아래 회색 글자 링크로 둔다 — 37 이 이미 빨간
 *   「탈퇴하기」 버튼과 비밀번호 확인을 갖고 있어 여기서까지 빨강으로 부를 이유가 없다.
 *
 * 상태(디스크립션):
 * - **없는 기능은 행을 만들지 않는다.** 고객센터는 서버 API 가 없어 빠져 있고
 *   (`docs/BACKEND-GAP-CLEANUP.md`), 매너 점수 · 마일리지 · 결제 수단도 같은 이유로 없다.
 *   **「지난 탑승 기록」만 2026-11-01 에 들어왔다** — 서버에 `GET /api/v1/users/me/parties` 가
 *   생겨 「없다」가 더 이상 사실이 아니게 됐다(`docs/BACKEND-GAP-CLEANUP.md` §6).
 *   레퍼런스에 있는 「로그인 수단 안내」도 두지 않았다 — 지금 이 화면이 아는 서버 값은 이름과
 *   프로필 사진뿐이고, 로그인 수단은 추측해서 적을 값이 아니다.
 * - 「안내」 행은 [links] 의 주소가 null 이면 그리지 않고, 셋 다 null 이면 **카드와 머리말까지**
 *   사라진다(`myPageGroups` 가 판정하고 `MyPageMenuRulesTest` 가 고정한다).
 * - 앱 안에 알림 토글을 두지 않는 이유는 [openAppNotificationSettings] 의 설명과 같다
 *   (서버에 알림 설정 저장 API 가 없어 앱만의 토글은 지키지 못할 약속이 된다).
 * - **제재 배너가 「왜 막혔나」를 말하는 유일한 자리다**(2026-11-06). 노쇼 횟수 × 24시간 동안 방
 *   만들기·합류·예약·실시간 후보가 **전부 403 `NO_SHOW_RESTRICTED`** 로 막히는데, 그 403 을 받는
 *   화면들은 「지금 이 동작이 막혔다」까지만 말할 수 있다 — 기한과 횟수는 프로필 조회에만 있다.
 *   그래서 막힌 사람이 「내 계정에 무슨 문제가 있나」를 찾아 오는 이 화면에 둔다. 판정과 문구는
 *   [noShowSanction] 이고, **제한이 없고 노쇼도 0건이면 배너 자체가 없다**(「제재 없음」 배지를
 *   만들지 않는다 — 아무 일 없는 사용자에게 제재 어휘를 가르칠 이유가 없다).
 * - 이름과 프로필 사진이 서버 값이다(GET /local/users/info — 이름은 닉네임, 없으면 실명).
 *   둘 다 한 번의 조회에서 함께 오고, **조회 전에는 둘 다 자리만 비워 둔다**(스켈레톤·빈 원).
 *   사진을 고른 적 없는 사용자에게도 서버는 기본 이미지 주소를 주므로, 로딩 중에 그리면
 *   기본 이미지가 한 번 스쳐 보인다. 사진은 36 에서 서버가 주는 16장 중에 고른다.
 * - 하단 버전 표기는 설치된 패키지의 versionName 이다(읽기 실패 시 숨김).
 *
 * 개발자 옵션 절은 없다. 유일한 항목이었던 「동승/택시 모드 강제」가 모드와 함께 사라졌고
 * (서버가 `taxiEnabled` 를 보내지 않는다), 빈 절만 남기면 디버그 빌드에 아무것도 없는 제목이 뜬다.
 * 새 개발자 옵션이 필요해지면 그때 절을 다시 세운다.
 */
@Composable
fun MyPageScreen(
    // 로그인 사용자의 표시 이름(닉네임)·프로필 사진 주소·**노쇼 횟수와 제한 기한**. 넷 다 같은 한 번의
    // 조회(GET /local/users/info)에서 오므로 한 상태로 받는다 — 파라미터를 쪼개면 "이름은 왔는데
    // 제재는 아직"이라는 있을 수 없는 조합이 타입으로 표현돼 버린다(아바타 호출부 주석의 그 이유).
    // 조회 전에는 Loading(이름·사진 자리를 비우고 제재 배너도 그리지 않는다).
    userName: UserNameState = UserNameState.Loading,
    onEditProfile: () -> Unit = {},          // → 36 프로필 수정
    onRideHistory: () -> Unit = {},          // → 34 지난 탑승 기록
    onNoShowRecords: () -> Unit = {},        // → 38 노쇼 기록·이의 신청
    onLogout: () -> Unit = {},               // → 04a 로그인 (세션 정리 후)
    onLoginMethods: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},        // → 37 회원 탈퇴
    onTabSelect: (MoyeotaTab) -> Unit = {},  // → 14 / 17 / 24
    // 「안내」 카드가 여는 주소. 기본값은 운영 값이고, 프리뷰·테스트가 빈 상태를 넣어 본다.
    links: SupportLinkTargets = SupportLinkTargets(),
) {
    // 오탭 한 번으로 세션이 날아가지 않도록 확인을 한 단계 둔다 (와이어프레임의 확인 다이얼로그)
    var logoutConfirming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionLabel = remember(context) { context.appVersionName()?.let { "v$it" } }
    val groups = remember(links) { myPageGroups(links) }

    // 제재 배너. 프로필 조회가 끝나기 전(Loading)에는 그리지 않는다 — 이름·사진과 같은 이유다
    // (조회 전에 「제한 없음」을 그리는 일도, 「제한 중」을 그리는 일도 없어야 한다).
    //
    // 기한이 지나는 순간을 **초 단위로 따라가지 않는다.** 키가 [userName] 뿐이라 화면을 열어 둔
    // 채로 24시간 제한이 끝나면 배너가 잠깐 남는데, 그걸 없애려면 타이머를 돌려야 하고 그 타이머는
    // 마이페이지에 상주하는 유일한 반복 작업이 된다. 서버가 풀린 사실은 다음 조회에서 null 로 온다.
    val sanction = remember(userName) {
        (userName as? UserNameState.Resolved)?.let {
            noShowSanction(noShowCount = it.noShowCount, restrictedUntil = it.restrictedUntil)
        }
    }

    // 화면은 「무엇을 누르면 어디로 간다」만 안다. Intent 는 core/ExternalIntents 가 맡는다.
    // when 이 빠짐없이 갈라야 하므로(sealed), 행 종류가 늘면 여기서 컴파일이 막힌다.
    val onAction: (MyPageAction) -> Unit = { action ->
        when (action) {
            MyPageAction.NotificationSettings -> context.openAppNotificationSettings()
            is MyPageAction.OpenWeb -> context.openWebPage(action.url)
            is MyPageAction.SendMail -> context.openSupportEmail(action.address)
            MyPageAction.RideHistory -> onRideHistory()
            MyPageAction.NoShowRecords -> onNoShowRecords()
            MyPageAction.LoginMethods -> onLoginMethods()
            MyPageAction.Logout -> logoutConfirming = true
            MyPageAction.DeleteAccount -> onDeleteAccount()
        }
    }

    // 묶음 목록(inset-grouped) 화면이라 바탕은 SurfaceSoft 다 — 흰 카드가 면의 차이로 묶음이 된다(G2)
    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // 탭 첫 화면의 머리줄 — 채팅·동승 탭과 같은 크기·여백이다(G7)
            MoyeotaTabHeader(title = "마이페이지")

            Spacer(Modifier.height(8.dp))
            // 프로필 카드 → 36 프로필 수정. 꺾쇠는 "눌러서 갈 곳이 있다"는 표시다
            // — 갈 화면이 없던 동안에는 일부러 꺾쇠도 탭 동작도 두지 않았다.
            Row(
                modifier = Modifier
                    .padding(horizontal = MoyeotaSpacing.ScreenGutter)
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(MoyeotaShape.Card)
                    .background(MoyeotaColor.SurfaceCanvas)
                    .clickable { onEditProfile() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 아바타의 인증 체크 배지는 두지 않는다 — 서버가 학교·재직 인증 여부를 주지 않는
                // 지금은 체크 표시 자체가 거짓 신호다.
                //
                // 사진 주소를 따로 받지 않고 [userName] 에서 꺼낸다 — 이름과 사진은 같은 한 번의
                // 조회(GET /local/users/info)에서 함께 오는 값이라, 파라미터를 하나 더 두면
                // "이름은 왔는데 사진은 아직"이라는 있을 수 없는 조합이 타입으로 표현돼 버린다.
                //
                // Loading 이면 `as?` 가 null 을 주고, 그래서 **로딩 중에는 요청조차 만들지 않는다.**
                // 바로 아래 이름이 스켈레톤인 것과 같은 이유다 — 서버는 고른 적 없는 사용자에게도
                // 기본 이미지 주소를 주므로, 로딩 중에 그리면 기본 이미지가 한 번 스쳐 보인다.
                //
                // label 은 비워 둔다. 이니셜을 깔면 사진 로드에 실패했을 때 옆 18sp 이름과 같은 글자가
                // 두 번 보인다 — 여기는 이름이 바로 옆에 있는 자리라 이니셜이 주는 정보가 없다.
                AvatarCircle(
                    size = 56.dp,
                    imageUrl = (userName as? UserNameState.Resolved)?.imageUrl,
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    when (userName) {
                        // 조회 전 — 폴백 문구가 스쳐 보이지 않게 자리만 잡아 둔다
                        UserNameState.Loading -> Box(
                            modifier = Modifier
                                .size(width = 88.dp, height = 18.dp)
                                .background(MoyeotaColor.Hairline, MoyeotaShape.Small),
                        )
                        is UserNameState.Resolved -> Text(
                            text = userName.name ?: "이름 미설정",
                            style = MoyeotaType.HeadingLg,
                            color = if (userName.name != null) MoyeotaColor.InkPrimary else MoyeotaColor.TextMute,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 36 에서 바꿀 수 있는 것을 **빠짐없이 적는다.** 사진을 고를 수 있게 되면서
                        // 「닉네임 바꾸기」는 거짓이 됐지만, 그렇다고 「프로필 수정」으로 넓히지는 않았다
                        // — 실명·생년월일·성별·휴대폰·이메일은 서버에 수정 경로가 아예 없어서
                        // 「프로필 수정」을 보고 전화번호를 바꾸려 들어온 사람은 헛걸음을 한다.
                        // 바꿀 수 있는 둘을 그대로 적는 쪽이 들어가기 전에 알려 준다.
                        // (36 의 상단바 제목은 「프로필 수정」이다 — 그쪽은 들어온 뒤에 보는 글이라
                        //  무엇을 바꿀 수 있는지는 화면 안의 입력칸이 이미 말하고 있다.)
                        // 세 번째 값이 수정 가능해지면 이 라벨은 더 못 늘어난다. 그때가 「프로필 수정」
                        // 으로 넓힐 자리다.
                        text = "닉네임·사진 바꾸기",
                        style = MoyeotaType.CaptionMd,
                        color = MoyeotaColor.TextMute,
                    )
                }
                ChevronIcon()
            }

            // 제재 배너는 **프로필 카드 바로 아래**다. 「계정에 무슨 문제가 있나」를 찾아 들어온
            // 사람이 스크롤하지 않고 보는 자리이고, 바로 다음 카드의 둘째 행이 「노쇼 기록」이라
            // 배너가 가리키는 길이 같은 화면 안에 보인다.
            //
            // 디자인시스템의 NoticeBanner 를 그대로 쓴다 — 이 앱의 「화면 단위 안내」는 이 컴포넌트
            // 하나이고(공통 규칙), 제재를 위해 새 카드 모양을 만들면 이 화면에만 있는 시각 언어가
            // 하나 늘어난다. 색은 두 가지뿐이다: 지금 막혀 있으면 ERROR, 횟수만 남았으면 WAITING.
            if (sanction != null) {
                Spacer(Modifier.height(12.dp))
                NoticeBanner(
                    kind = when (sanction.kind) {
                        NoShowSanctionKind.RESTRICTED -> NoticeKind.ERROR
                        NoShowSanctionKind.COUNTED -> NoticeKind.WAITING
                    },
                    text = sanction.text,
                    modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
                )
            }

            Spacer(Modifier.height(24.dp))
            // 머리말 + 흰 카드 + 구분선으로 나뉜 행들. 같은 그룹의 행은 한 카드 안에 묶는다
            // — 카드가 그룹의 경계고, 그래서 머리말이 무엇에 걸리는 말인지가 눈에 보인다.
            groups.forEach { group -> MyPageMenuCard(group = group, onAction = onAction) }

            // 버전은 맨 아래 가운데. 사용자가 찾는 값이 아니라 **물어볼 때 읽어 주는** 값이라
            // 눈에 걸리지 않는 자리에 둔다(문의 메일에도 같은 값이 자동으로 들어간다).
            if (versionLabel != null) {
                Text(
                    text = versionLabel,
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextMute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MoyeotaSpacing.ScreenGutter),
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24
        MoyeotaBottomBar(selected = MoyeotaTab.MYPAGE, onSelect = onTabSelect)
    }

    if (logoutConfirming) {
        // 앱 공용 확인 다이얼로그(G11). 확인 버튼은 예전처럼 빨강 — 세션이 끝나는 이탈이다
        MoyeotaConfirmDialog(
            title = "로그아웃할까요?",
            body = "다시 이용하려면 아이디로 로그인해야 해요",
            confirmText = "로그아웃",
            onConfirm = {
                logoutConfirming = false
                onLogout()
            },
            onDismiss = { logoutConfirming = false },
            dismissText = "취소",
            destructive = true,
        )
    }
}

/**
 * 머리말 + 카드 + (있으면) 카드 밖 회색 링크 + (있으면) 그 아래 한 줄.
 *
 * 행마다 아이콘을 두지 않는다 — 글자 네 개짜리 목록에서 행마다 붙인 아이콘은 구분이 아니라 장식이다.
 * 대신 오른쪽 기호 두 개에만 뜻을 준다([MyPageRowMark]: 바깥 화살표 = 앱 밖, 꺾쇠 = 앱 안). 두 기호는
 * 디자인시스템의 그린 아이콘 한 벌([ExternalLinkIcon]·[ChevronIcon], 16dp)을 쓴다.
 *
 * **되돌릴 수 없는 행([MyPageRow.danger])은 카드에 넣지 않는다**(디자인 QA M2) — 카드 아래 회색 글자
 * 링크로 떼어 자주 쓰는 행과 공간으로 떨어뜨린다. 모델(`myPageGroups`)의 행 순서와 동작은 그대로다.
 *
 * 빈 그룹은 `myPageGroups` 가 미리 걸러내므로 여기 오지 않는다. 그래도 방어로 한 줄 둔다
 * — 빈 카드는 "고장난 메뉴"로 보이고, 머리말만 남으면 더 이상하다.
 */
@Composable
private fun MyPageMenuCard(group: MyPageGroup, onAction: (MyPageAction) -> Unit) {
    if (group.rows.isEmpty()) return
    val cardRows = group.rows.filterNot { it.danger }
    val linkRows = group.rows.filter { it.danger }
    SectionLabel(
        text = group.label,
        modifier = Modifier.padding(horizontal = MoyeotaSpacing.ScreenGutter),
    )
    if (cardRows.isNotEmpty()) {
        Column(
            modifier = Modifier
                .padding(horizontal = MoyeotaSpacing.ScreenGutter)
                .fillMaxWidth()
                .clip(MoyeotaShape.Card)
                .background(MoyeotaColor.SurfaceCanvas),
        ) {
            cardRows.forEachIndexed { index, row ->
                if (index > 0) {
                    // 구분선은 글자 시작선에 맞춰 들여 둔다 — 카드 폭을 꽉 채우면 행이 카드에서 떨어져 보인다
                    HorizontalDivider(
                        color = MoyeotaColor.Hairline,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onAction(row.action) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.text,
                        style = MoyeotaType.BodyMd,
                        color = MoyeotaColor.InkPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    when (row.mark) {
                        MyPageRowMark.EXTERNAL -> ExternalLinkIcon()
                        MyPageRowMark.CHEVRON -> ChevronIcon()
                    }
                }
            }
        }
    }
    linkRows.forEach { row ->
        // 글자 링크의 안쪽 여백(8dp)만큼 덜 들여 글자 시작을 카드 가장자리(16dp)에 맞춘다
        TextLinkButton(
            text = row.text,
            onClick = { onAction(row.action) },
            color = MoyeotaColor.TextMute,
            style = MoyeotaType.BodySm,
            modifier = Modifier.padding(start = MoyeotaSpacing.ScreenGutter - 8.dp, top = 4.dp),
        )
    }
    if (group.caption != null) {
        Text(
            // 카드(와 탈퇴 링크) **아래**에 둔다. 행 안에 넣으면 56dp 행이 두 줄로 늘어나 다른 행과 높이가
            // 달라지고, 카드 위에 두면 머리말과 붙어 무엇에 걸리는 말인지 흐려진다.
            text = group.caption,
            style = MoyeotaType.CaptionMd,
            color = MoyeotaColor.TextMute,
            modifier = Modifier.padding(
                start = MoyeotaSpacing.ScreenGutter,
                end = MoyeotaSpacing.ScreenGutter,
                top = if (linkRows.isEmpty()) 8.dp else 0.dp,
            ),
        )
    }
    Spacer(Modifier.height(24.dp))
}

// 운영 주소가 채워진 지금 모습 — 「안내」 카드에 세 행이 선다
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "주소 있음(운영)")
@Composable
private fun MyPageScreenPreview() {
    MyPageScreen(userName = UserNameState.Resolved("성윤"))
}

// 제재 중 — 프로필 카드 아래에 빨간 배너가 서고, 「노쇼 기록」 행이 그 길이다.
// `restrictedUntil` 을 **지금 기준으로** 만든다: 고정 문자열을 쓰면 그 날짜가 지나는 순간
// 프리뷰가 조용히 「제한 없음」으로 바뀌어 이 상태를 더 이상 보여 주지 못한다.
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "노쇼 제재 중")
@Composable
private fun MyPageScreenRestrictedPreview() {
    MyPageScreen(
        userName = UserNameState.Resolved(
            name = "성윤",
            noShowCount = 2,
            restrictedUntil = Instant.now().plus(20, ChronoUnit.HOURS).toString(),
        ),
    )
}

// 제한은 풀렸지만 횟수가 남은 상태 — 노랑 배너로 바뀌고 30일 만료를 말한다.
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "노쇼 횟수만 남음")
@Composable
private fun MyPageScreenCountedPreview() {
    MyPageScreen(userName = UserNameState.Resolved(name = "성윤", noShowCount = 1))
}

// 주소를 전부 비웠을 때 — 「안내」 카드가 통째로 사라지고 「설정」·「계정」 만 남는다.
// 페이지가 내려가 긴급히 null 로 되돌려야 할 때의 모습이다(SupportLinks KDoc).
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "주소 없음")
@Composable
private fun MyPageScreenNoLinksPreview() {
    MyPageScreen(userName = UserNameState.Resolved("성윤"), links = SupportLinkTargets.None)
}

// 주소가 하나만 비었을 때 — 그 행만 빠지고 카드는 남는다(행끼리 독립)
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "문의 메일만 없음")
@Composable
private fun MyPageScreenPartialLinksPreview() {
    MyPageScreen(
        userName = UserNameState.Resolved("성윤"),
        links = SupportLinkTargets(contactEmail = null),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "이름 미설정")
@Composable
private fun MyPageScreenNoNamePreview() {
    MyPageScreen(userName = UserNameState.Resolved(null))
}

// 36 에서 사진을 고른 사용자. **프리뷰에서는 회색 원이 보이는 게 정상이다** — 프리뷰 샌드박스에는
// 네트워크가 없어 요청이 실패하고, AvatarCircle 이 그걸 빈 원으로 떨어뜨린다. 그래서 이 프리뷰가
// 고정하는 건 사진 자체가 아니라 **사진이 실린 상태에서도 카드 레이아웃이 그대로인가**다
// (56dp 원 + 12dp 간격 + 두 줄 글자 + 꺾쇠가 80dp 카드 안에 들어가는가). 사진은 실기에서 본다.
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "프로필 사진 있음")
@Composable
private fun MyPageScreenWithPhotoPreview() {
    MyPageScreen(
        userName = UserNameState.Resolved(
            name = "성윤",
            imageUrl = "https://cdn.moyeota.co.kr/profile/presets/03.png",
        ),
    )
}
