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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.presentation.core.SupportLinks
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
 * 구성: 프로필 카드(이름 + 꺾쇠) → 「설정」 카드 → 「지원」 카드 → 로그아웃(+버전) → (디버그) 개발자 옵션
 *
 * 이동(디스크립션):
 * - 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24 (onTabSelect)
 * - 프로필 카드 → 36 프로필 수정 (onEditProfile)
 * - 「알림 설정」 → OS 앱 알림 설정 (앱 밖)
 * - 「문의하기」 → 메일 앱, 제목·환경 정보 자동 입력 (앱 밖)
 * - 「이용약관」 · 「개인정보 처리방침」 → 브라우저 (앱 밖)
 * - 「로그아웃」 → 확인 다이얼로그 후 04a 로그인 (onLogout)
 *
 * 상태(디스크립션):
 * - **없는 기능은 행을 만들지 않는다.** 탈퇴하기 · 탑승 기록 · 고객센터는 서버 API 가 없어 빠져 있고
 *   (`docs/BACKEND-GAP-CLEANUP.md`), 매너 점수 · 마일리지 · 결제 수단도 같은 이유로 없다.
 * - 약관 · 개인정보 · 문의 행은 [SupportLinks] 의 주소가 null 이면 그리지 않는다. 운영 주소가
 *   정해지기 전인 지금은 셋 다 null 이라 「지원」 카드 자체가 보이지 않는다 — 「준비 중」을 두는 대신이다.
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
    onTabSelect: (MoyeotaTab) -> Unit = {},  // → 14 / 17 / 24
    modeDebug: ModeDebugOptions? = null,     // 디버그 빌드 전용 개발자 옵션. 릴리스는 null
) {
    // 오탭 한 번으로 세션이 날아가지 않도록 확인을 한 단계 둔다 (와이어프레임의 확인 다이얼로그)
    var logoutConfirming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionLabel = remember(context) { context.appVersionName()?.let { "v$it" } }

    // 앱 밖으로 나가는 행들. 화면은 "무엇을 누르면 어디로 나간다"만 알고, Intent 는 core/ExternalIntents 가 맡는다.
    val settingsItems = listOf(
        MyPageMenuItem("알림 설정") { context.openAppNotificationSettings() },
    )
    // 주소가 없는 행은 아예 만들지 않는다 — 눌러도 아무 일 없는 행을 두지 않기 위해서다.
    val supportItems = buildList {
        SupportLinks.CONTACT_EMAIL?.let { email ->
            add(MyPageMenuItem("문의하기") { context.openSupportEmail(email) })
        }
        SupportLinks.TERMS_URL?.let { url ->
            add(MyPageMenuItem("이용약관") { context.openWebPage(url) })
        }
        SupportLinks.PRIVACY_URL?.let { url ->
            add(MyPageMenuItem("개인정보 처리방침") { context.openWebPage(url) })
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
            MyPageMenuCard(label = "설정", items = settingsItems)
            MyPageMenuCard(label = "지원", items = supportItems)

            // 로그아웃 → 04a (확인 후). logout() 은 실패하지 않는다 — 로컬 세션을 먼저 비우고
            // 서버 무효화는 최선 노력이라, 오프라인에서도 사용자가 갇히지 않는다.
            // 탈퇴하기는 서버에 탈퇴 API 가 없어 두지 않는다. 버전은 로그아웃과 같은 줄 오른쪽에.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "로그아웃",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayMute,
                    modifier = Modifier.clickable { logoutConfirming = true },
                )
                Spacer(Modifier.weight(1f))
                if (versionLabel != null) {
                    Text(
                        text = versionLabel,
                        fontSize = 11.sp,
                        color = GrayAsh,
                    )
                }
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
 * 마이페이지 메뉴 한 줄.
 *
 * 지금은 모든 행이 앱 밖(OS 설정 · 메일 · 브라우저)으로 나가므로 전부 외부 표시(↗)를 단다.
 * 앱 안에서 이동하는 유일한 지점인 프로필 카드만 꺾쇠(›)를 쓴다 — 두 기호가 "여기서 앱을 벗어나는가"를
 * 가른다. 아이콘은 두지 않는다: 행이 네 개 이하인 목록에서 아이콘은 구분이 아니라 장식이다.
 */
private class MyPageMenuItem(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * 머리말 + 카드 안 메뉴 목록. [items] 가 비면 **카드도 머리말도 그리지 않는다**
 * — 주소가 정해지지 않은 「지원」 그룹이 빈 카드로 남지 않게 하는 장치다.
 */
@Composable
private fun MyPageMenuCard(label: String, items: List<MyPageMenuItem>) {
    if (items.isEmpty()) return
    Text(
        text = label,
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
        items.forEachIndexed { index, item ->
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
                    .clickable { item.onClick() }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MoyeotaColor.InkPrimary,
                    modifier = Modifier.weight(1f),
                )
                ExternalLinkIcon()
            }
        }
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

// 외부로 나간다는 표시(↗). 글꼴에 따라 빠질 수 있는 유니코드 화살표 대신 직접 그린다
@Composable
private fun ExternalLinkIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        // 대각선 ↗
        drawLine(GrayAsh, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.22f), strokeWidth, StrokeCap.Round)
        // 화살머리 두 변
        drawLine(GrayAsh, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.78f, h * 0.22f), strokeWidth, StrokeCap.Round)
        drawLine(GrayAsh, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.78f, h * 0.58f), strokeWidth, StrokeCap.Round)
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

// 운영 주소가 없는 지금 모습 — 「지원」 카드가 통째로 빠지고 「설정」 만 남는다
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MyPageScreenPreview() {
    MyPageScreen(userName = UserNameState.Resolved("성윤"))
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
