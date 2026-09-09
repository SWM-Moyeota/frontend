package com.moyeota.presentation.feature.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType

private val ScreenBackground = Color(0xFFF5F7FA)
private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xFF191600)
private val TextSlate = Color(0xFF4B5563)
private val TextFaint = Color(0xFF9AA1AC)
private val CardShadow = Color(0x0F1B2A4A)

/**
 * 04 · 시작 — 로그인 방식 [S01]
 *
 * 진입: 03 시작하기 · 온보딩 완료 후 재실행 · 로그아웃 직후
 *
 * @param onEmailStart 「이메일로 시작하기」 → 09 본인 인증 (가입 1단계)
 * @param onLogin 「로그인」(이미 계정이 있어요) → 04a 아이디 로그인
 * @param showRetryBanner 카카오 인증 취소/실패 시 true — 화면 유지 + 「다시 시도해 주세요」 배너
 *
 * 「카카오로 3초 만에 시작」은 **의도적으로 미구현**이다 — 백엔드에 소셜 로그인 엔드포인트가 없다.
 * 버튼을 지우는 대신 탭하면 「준비 중」 안내를 띄운다: 지워버리면 나중에 붙일 자리를 잃고,
 * 눌러서 가입 플로우로 보내면 카카오로 가입한 줄 아는 사용자가 생긴다.
 */
@Composable
fun LoginScreen(
    onEmailStart: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    showRetryBanner: Boolean = false,
) {
    var kakaoNoticeShown by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        StatusBarSpacer()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "모여타",
                color = MoyeotaColor.InkPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )

            Spacer(Modifier.height(36.dp))
            Text(
                text = "믿을 수 있는 사람과\n택시를 나눠 타요",
                color = MoyeotaColor.InkPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
                letterSpacing = (-0.52).sp,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                // 학교 이메일 인증은 가입 필수 경로가 아니다(마이페이지에서 나중에 추가하는 컨셉).
                // 「학생만 · 학교 이메일로 확인」은 이제 사실이 아니라 지웠다.
                text = "아이디로 간편하게 가입해요",
                color = MoyeotaColor.TextMute,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.28).sp,
            )

            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(18.dp), ambientColor = CardShadow, spotColor = CardShadow)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    // 불릿이 3개에서 2개로 줄어 카드가 세로로 납작해졌다 —
                    // 행 간격·상하 여백을 함께 줄여 예전 비율(여백 ≒ 행 간격)을 유지한다
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 「학교 인증을 마친 학생만 매칭돼요」는 삭제 — 가입에 학교 인증 단계가 없어 사실이 아니다
                TrustBulletRow(text = "같은 성별끼리만 매칭돼요")
                TrustBulletRow(text = "운행 중 위치를 지인과 공유할 수 있어요")
            }

            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KakaoYellow)
                    .clickable { kakaoNoticeShown = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "카카오로 3초 만에 시작",
                    color = KakaoLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
            }

            if (showRetryBanner) {
                Spacer(Modifier.height(12.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = "다시 시도해 주세요")
            } else if (kakaoNoticeShown) {
                Spacer(Modifier.height(12.dp))
                NoticeBanner(
                    kind = NoticeKind.INFO,
                    text = "카카오 로그인은 준비 중이에요. 이메일로 시작해 주세요",
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 「이메일로 시작하기」 묶음은 스크롤 밖 = 화면 하단 고정.
        // 통계 카드가 빠지면서 본문이 짧아졌는데, 예전처럼 고정 Spacer 로 CTA 위치를 잡으면
        // 본문 아래에 빈 공간이 남는다. 하단 고정이면 남는 높이를 스크롤 영역이 흡수하고,
        // 안내 배너가 떠도 CTA 가 튀지 않는다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            PrimaryCtaButton(
                text = "이메일로 시작하기",
                onClick = onEmailStart,
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "이미 계정이 있어요",
                    color = MoyeotaColor.TextMute,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.26).sp,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "로그인",
                    color = MoyeotaColor.Primary500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.26).sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLogin,
                    ),
                )
            }

            Spacer(Modifier.height(11.dp))
            Text(
                text = "가입하면 이용약관 · 개인정보 처리방침에 동의하게 돼요",
                color = TextFaint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = (-0.22).sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    // 미연결 — 약관 뷰 필요
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            Spacer(Modifier.height(28.dp))
        }

        NavigationBarSpacer()
    }
}

// 파란 체크 원 + 안내 문구 한 줄
@Composable
private fun TrustBulletRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MoyeotaColor.Primary500, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CheckMarkIcon(iconSize = 12.dp, color = Color.White)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = text,
            color = TextSlate,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            letterSpacing = (-0.28).sp,
        )
    }
}

// 아이콘 라이브러리 없이 그리는 체크 표시
@Composable
internal fun CheckMarkIcon(iconSize: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.8f)
            lineTo(w * 0.85f, h * 0.25f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenPreview() {
    LoginScreen(
        onEmailStart = {},
        onLogin = {},
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenRetryBannerPreview() {
    LoginScreen(
        onEmailStart = {},
        onLogin = {},
        showRetryBanner = true,
    )
}
