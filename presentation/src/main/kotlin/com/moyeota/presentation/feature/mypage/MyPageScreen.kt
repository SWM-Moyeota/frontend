package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.presentation.core.SupportLinkTargets
import com.moyeota.presentation.core.UserNameState
import com.moyeota.presentation.core.appVersionName
import com.moyeota.presentation.core.openAppNotificationSettings
import com.moyeota.presentation.core.openSupportEmail
import com.moyeota.presentation.core.openWebPage

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val SoftDivider = Color(0xFFE4E9F0)
private val CardShadow = Color(0x0F1B2A4A)

/**
 * 35 · 마이페이지 · 설정 [S24]
 *
 * 진입: 하단탭 마이페이지
 *
 * 구성: 프로필 카드 → 「설정」 카드 → 「안내」 카드 → 「계정」 카드 → 버전 → (디버그) 개발자 옵션
 *
 * ```
 * 프로필 카드 (이름 / 닉네임 바꾸기)         ›
 * [설정]  알림 설정                          ↗
 * [안내]  개인정보 처리방침 / 이용약관 / 문의하기   ↗   ← 한 카드, 구분선
 * [계정]  로그아웃 / 탈퇴하기(빨강)            ›   ← 한 카드, 구분선
 *         탈퇴하면 계정과 저장한 정보가 사라지고…
 *                      v1.0                      ← 가운데
 * (디버그) 개발자 옵션
 * ```
 *
 * 이동(디스크립션):
 * - 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24 (onTabSelect)
 * - 프로필 카드 → 36 프로필 수정 (onEditProfile)
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
 * 상태(디스크립션):
 * - **없는 기능은 행을 만들지 않는다.** 탑승 기록 · 고객센터는 서버 API 가 없어 빠져 있고
 *   (`docs/BACKEND-GAP-CLEANUP.md`), 매너 점수 · 마일리지 · 결제 수단도 같은 이유로 없다.
 *   레퍼런스에 있는 「로그인 수단 안내」도 두지 않았다 — 지금 이 화면이 아는 서버 값은 이름뿐이고,
 *   로그인 수단은 추측해서 적을 값이 아니다.
 * - 「안내」 행은 [links] 의 주소가 null 이면 그리지 않고, 셋 다 null 이면 **카드와 머리말까지**
 *   사라진다(`myPageGroups` 가 판정하고 `MyPageMenuRulesTest` 가 고정한다).
 * - 앱 안에 알림 토글을 두지 않는 이유는 [openAppNotificationSettings] 의 설명과 같다
 *   (서버에 알림 설정 저장 API 가 없어 앱만의 토글은 지키지 못할 약속이 된다).
 * - 이름만 서버 값이다(GET /local/users/info — 닉네임, 없으면 실명). 조회 전에는 자리만 비워 둔다.
 * - 하단 버전 표기는 설치된 패키지의 versionName 이다(읽기 실패 시 숨김).
 * - 개발자 옵션(동승/택시 모드 강제)은 [modeDebug] 가 있을 때만, 즉 디버그 빌드에서만 그린다.
 */
@Composable
fun MyPageScreen(
    // 로그인 사용자의 표시 이름(닉네임). 조회 전에는 Loading(자리만 비움), 서버에 이름이 없으면 Resolved(null).
    userName: UserNameState = UserNameState.Loading,
    onEditProfile: () -> Unit = {},          // → 36 프로필 수정
    onLogout: () -> Unit = {},               // → 04a 로그인 (세션 정리 후)
    onDeleteAccount: () -> Unit = {},        // → 37 회원 탈퇴
    onTabSelect: (MoyeotaTab) -> Unit = {},  // → 14 / 17 / 24
    modeDebug: ModeDebugOptions? = null,     // 디버그 빌드 전용 개발자 옵션. 릴리스는 null
    // 「안내」 카드가 여는 주소. 기본값은 운영 값이고, 프리뷰·테스트가 빈 상태를 넣어 본다.
    links: SupportLinkTargets = SupportLinkTargets(),
) {
    // 오탭 한 번으로 세션이 날아가지 않도록 확인을 한 단계 둔다 (와이어프레임의 확인 다이얼로그)
    var logoutConfirming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionLabel = remember(context) { context.appVersionName()?.let { "v$it" } }
    val groups = remember(links) { myPageGroups(links) }

    // 화면은 「무엇을 누르면 어디로 간다」만 안다. Intent 는 core/ExternalIntents 가 맡는다.
    // when 이 빠짐없이 갈라야 하므로(sealed), 행 종류가 늘면 여기서 컴파일이 막힌다.
    val onAction: (MyPageAction) -> Unit = { action ->
        when (action) {
            MyPageAction.NotificationSettings -> context.openAppNotificationSettings()
            is MyPageAction.OpenWeb -> context.openWebPage(action.url)
            is MyPageAction.SendMail -> context.openSupportEmail(action.address)
            MyPageAction.Logout -> logoutConfirming = true
            MyPageAction.DeleteAccount -> onDeleteAccount()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        StatusBarSpacer()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "마이페이지",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(17.dp))
            // 프로필 카드 → 36 프로필 수정. 꺾쇠는 "눌러서 갈 곳이 있다"는 표시다
            // — 갈 화면이 없던 동안에는 일부러 꺾쇠도 탭 동작도 두지 않았다.
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(104.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .clickable { onEditProfile() }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 아바타의 인증 체크 배지는 두지 않는다 — 서버가 학교·재직 인증 여부를 주지 않는
                // 지금은 체크 표시 자체가 거짓 신호다.
                AvatarCircle(size = 56.dp)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    when (userName) {
                        // 조회 전 — 폴백 문구가 스쳐 보이지 않게 자리만 잡아 둔다
                        UserNameState.Loading -> Box(
                            modifier = Modifier
                                .size(width = 88.dp, height = 18.dp)
                                .background(SoftDivider, RoundedCornerShape(6.dp)),
                        )
                        is UserNameState.Resolved -> Text(
                            text = userName.name ?: "이름 미설정",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (userName.name != null) MoyeotaColor.InkPrimary else GrayMute,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 36 에서 바꿀 수 있는 게 닉네임 하나뿐이라 그렇게 적는다 — 「프로필 수정」 이라
                        // 적어 두면 사진·전화번호까지 바꿀 수 있다는 기대로 들어오게 된다.
                        text = "닉네임 바꾸기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayMute,
                    )
                }
                ChevronRightIcon()
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
                    fontSize = 11.sp,
                    color = GrayAsh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            if (modeDebug != null) {
                Spacer(Modifier.height(32.dp))
                ModeDebugSection(options = modeDebug)
            }

            Spacer(Modifier.height(24.dp))
        }

        // 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24
        MoyeotaBottomBar(selected = MoyeotaTab.MYPAGE, onSelect = onTabSelect)
    }

    if (logoutConfirming) {
        AlertDialog(
            onDismissRequest = { logoutConfirming = false },
            title = { Text(text = "로그아웃할까요?", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = { Text(text = "다시 이용하려면 아이디로 로그인해야 해요", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    logoutConfirming = false
                    onLogout()
                }) {
                    Text(text = "로그아웃", fontWeight = FontWeight.Bold, color = MoyeotaColor.Danger500)
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirming = false }) {
                    Text(text = "취소", color = GrayMute)
                }
            },
            containerColor = MoyeotaColor.SurfaceCanvas,
        )
    }
}

/**
 * 머리말 + 카드 + (있으면) 카드 아래 한 줄.
 *
 * 행마다 아이콘을 두지 않는다 — 이 앱에는 아이콘 자산이 없고, 글자 네 개짜리 목록에서 급조한
 * 아이콘은 구분이 아니라 장식이다. 대신 오른쪽 기호 두 개에만 뜻을 준다
 * ([MyPageRowMark]: ↗ = 앱 밖, › = 앱 안).
 *
 * 빈 그룹은 `myPageGroups` 가 미리 걸러내므로 여기 오지 않는다. 그래도 방어로 한 줄 둔다
 * — 빈 카드는 "고장난 메뉴"로 보이고, 머리말만 남으면 더 이상하다.
 */
@Composable
private fun MyPageMenuCard(group: MyPageGroup, onAction: (MyPageAction) -> Unit) {
    if (group.rows.isEmpty()) return
    Text(
        text = group.label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = GrayAsh,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas),
    ) {
        group.rows.forEachIndexed { index, row ->
            if (index > 0) {
                // 구분선은 글자 시작선에 맞춰 들여 둔다 — 카드 폭을 꽉 채우면 행이 카드에서 떨어져 보인다
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SoftDivider),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onAction(row.action) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    // 되돌릴 수 없는 행만 색이 다르다. 카드 안에서 유일한 빨강이라야 경고로 읽힌다.
                    color = if (row.danger) MoyeotaColor.Danger500 else MoyeotaColor.InkPrimary,
                    modifier = Modifier.weight(1f),
                )
                when (row.mark) {
                    MyPageRowMark.EXTERNAL -> ExternalLinkIcon()
                    MyPageRowMark.CHEVRON -> ChevronRightIcon()
                }
            }
        }
    }
    if (group.caption != null) {
        Text(
            // 카드 **아래**에 둔다. 행 안에 넣으면 56dp 행이 두 줄로 늘어나 다른 행과 높이가 달라지고,
            // 카드 위에 두면 머리말과 붙어 무엇에 걸리는 말인지 흐려진다.
            text = group.caption,
            fontSize = 11.sp,
            color = GrayAsh,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
}

// 아이콘 라이브러리 없이 그리는 꺾쇠(›) — 앱 안에서 다음 화면으로 간다는 표시
@Composable
private fun ChevronRightIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        drawLine(GrayAsh, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.66f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(GrayAsh, Offset(w * 0.38f, h * 0.76f), Offset(w * 0.66f, h * 0.5f), strokeWidth, StrokeCap.Round)
    }
}

/**
 * 외부로 나간다는 표시(↗). 글꼴에 따라 빠질 수 있는 유니코드 화살표 대신 직접 그린다.
 *
 * 같은 패키지의 37 회원 탈퇴도 이것을 쓴다(「자세히 보기」) — 기호의 뜻이 두 화면에서 어긋나면
 * 규칙 자체가 무의미해진다. 세 번째 화면이 쓰게 되면 `core/designsystem` 으로 올릴 자리다.
 */
@Composable
internal fun ExternalLinkIcon(modifier: Modifier = Modifier, tint: Color = GrayAsh) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        // 대각선 ↗
        drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.22f), strokeWidth, StrokeCap.Round)
        // 화살머리 두 변
        drawLine(tint, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.78f, h * 0.22f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.78f, h * 0.58f), strokeWidth, StrokeCap.Round)
    }
}

/**
 * 디버그 빌드 전용 개발자 옵션 — 서버 모드와 무관하게 동승/택시 화면을 강제한다.
 *
 * 택시 화면(25·26)은 서버가 배차 status 를 줘야 열리므로, 동승 서버에 택시를 강제해도 21 의 문구·버튼만
 * 바뀐다. 25·26 까지 보려면 `TAXI_ENABLED=true` 로 띄운 로컬 서버가 필요하다(docs/MODE-ROUTES.md).
 */
data class ModeDebugOptions(
    /** 서버가 준 값(강제 미적용). */
    val serverTaxiEnabled: Boolean,
    /** 서버 응답을 받았는가. false 면 서버 값은 아직 기본값이다. */
    val serverResolved: Boolean,
    /** 강제값. null 이면 서버 값을 따른다. */
    val override: Boolean?,
    val onOverride: (Boolean?) -> Unit,
)

@Composable
private fun ModeDebugSection(options: ModeDebugOptions) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "개발자 옵션 · 디버그 빌드에만 보여요",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = GrayAsh,
        )
        Spacer(Modifier.height(8.dp))
        val serverLabel = when {
            !options.serverResolved -> "미수신 (기본값 동승)"
            options.serverTaxiEnabled -> "택시"
            else -> "동승"
        }
        Text(
            text = "서버 모드: $serverLabel",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(text = "서버 값", selected = options.override == null) { options.onOverride(null) }
            ModeChip(text = "동승 강제", selected = options.override == false) { options.onOverride(false) }
            ModeChip(text = "택시 강제", selected = options.override == true) { options.onOverride(true) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "강제값은 앱을 완전히 종료하면 풀려요. 25 배차·26 운행 화면은 택시 모드 서버가 있어야 열려요.",
            fontSize = 11.sp,
            color = GrayAsh,
        )
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else GrayMute,
        )
    }
}

// 운영 주소가 채워진 지금 모습 — 「안내」 카드에 세 행이 선다
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "주소 있음(운영)")
@Composable
private fun MyPageScreenPreview() {
    MyPageScreen(userName = UserNameState.Resolved("성윤"))
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "개발자 옵션")
@Composable
private fun MyPageScreenDebugPreview() {
    MyPageScreen(
        userName = UserNameState.Resolved("성윤"),
        modeDebug = ModeDebugOptions(serverTaxiEnabled = false, serverResolved = true, override = true, onOverride = {}),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "이름 미설정")
@Composable
private fun MyPageScreenNoNamePreview() {
    MyPageScreen(userName = UserNameState.Resolved(null))
}
