package com.moyeota.presentation.feature.mypage

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
import androidx.compose.ui.graphics.Color
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
import com.moyeota.presentation.core.UserNameState

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val SoftDivider = Color(0xFFE4E9F0)
private val CardShadow = Color(0x0F1B2A4A)

/**
 * 35 · 마이페이지 · 설정 [S24]
 *
 * 진입: 하단탭 마이
 *
 * 구성: 프로필 카드(이름만) → 로그아웃(+버전)
 *
 * 이동(디스크립션):
 * - 하단탭 홈 / 합승 / 채팅 → 14 / 17 / 24 (onTabSelect)
 * - 「로그아웃」 → 확인 시트 후 04a 로그인 (onLogout)
 * - 탈퇴하기는 서버에 탈퇴 API 가 없어 화면에서 뺐다
 * - 알림 설정 · 고객센터/신고 내역은 백엔드 API 가 없어 화면에서 뺐다
 *
 * 상태(디스크립션):
 * - 매너 점수 · 마일리지 · 안심 설정 · 결제 수단은 서버 근거가 없는 2차 항목이라 화면에서 뺐다.
 * - 탑승 횟수 요약·탑승 기록은 내 탑승 집계/이력 API 가 없어 뺐다. 이름만 서버 값이다(GET /users/info).
 * - 프로필 카드는 갈 화면(프로필 편집)이 없어 탭 동작·꺾쇠 없이 표시만 한다.
 * - 하단 버전 표기는 설치된 패키지의 versionName 이다(읽기 실패 시 숨김).
 * - 개발자 옵션(동승/택시 모드 강제)은 [modeDebug] 가 있을 때만, 즉 디버그 빌드에서만 그린다.
 */
@Composable
fun MyPageScreen(
    // 로그인 사용자의 실명. 조회 전에는 Loading(자리만 비움), 서버에 이름이 없으면 Resolved(null).
    userName: UserNameState = UserNameState.Loading,
    onLogout: () -> Unit = {},               // → 04a 로그인 (세션 정리 후)
    onTabSelect: (MoyeotaTab) -> Unit = {},  // → 14 / 17 / 24
    modeDebug: ModeDebugOptions? = null,     // 디버그 빌드 전용 개발자 옵션. 릴리스는 null
) {
    // 오탭 한 번으로 세션이 날아가지 않도록 확인을 한 단계 둔다 (와이어프레임의 확인 다이얼로그)
    var logoutConfirming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionLabel = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.let { "v$it" }
        } catch (e: Exception) {
            null
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
            // 프로필 카드 — 갈 화면이 없어 표시 전용 (꺾쇠·탭 동작 없음)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(104.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MoyeotaColor.SurfaceCanvas)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 아바타의 인증 체크 배지는 뺐다 — 아래 인증 라벨과 같은 이유로,
                // 서버가 인증 여부를 주지 않는 지금은 체크 표시 자체가 거짓 신호다.
                AvatarCircle(size = 56.dp)
                Spacer(Modifier.size(14.dp))
                Column {
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
                    // 인증 배지는 서버가 인증 정보를 주기 전까지 두지 않는다 (학교·재직 인증 API 없음).
                }
            }

            Spacer(Modifier.height(24.dp))
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

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MyPageScreenPreview() {
    MyPageScreen(userName = UserNameState.Resolved("김성윤"))
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "개발자 옵션")
@Composable
private fun MyPageScreenDebugPreview() {
    MyPageScreen(
        userName = UserNameState.Resolved("김성윤"),
        modeDebug = ModeDebugOptions(serverTaxiEnabled = false, serverResolved = true, override = true, onOverride = {}),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "이름 미설정")
@Composable
private fun MyPageScreenNoNamePreview() {
    MyPageScreen(userName = UserNameState.Resolved(null))
}
