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
import androidx.compose.runtime.remember
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
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
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
 * @param onKakaoStart 「카카오로 3초 만에 시작」 → 05 계정 유형 선택 (카카오 인증 성공 시)
 * @param onEmailStart 「이메일로 시작하기」 → 05 계정 유형 선택
 * @param onLogin 「로그인」(이미 계정이 있어요) → 14 홈
 * @param weeklyRiderCount 주간 집계 값 — 집계 실패 시 null 로 전달하면 문구 숨김
 * @param showRetryBanner 카카오 인증 취소/실패 시 true — 화면 유지 + 「다시 시도해 주세요」 배너
 */
@Composable
fun LoginScreen(
    onKakaoStart: () -> Unit,
    onEmailStart: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    weeklyRiderCount: Int? = 312,
    showRetryBanner: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
    ) {
        StatusBarMock()

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
                text = "부산대 학생만 · 학교 이메일 인증으로 확인해요",
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
                    .padding(horizontal = 20.dp, vertical = 19.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TrustBulletRow(text = "학교 인증을 마친 학생만 매칭돼요")
                TrustBulletRow(text = "동성끼리 같은 차를 타도록 설정할 수 있어요")
                TrustBulletRow(text = "운행 중 위치를 지인과 공유할 수 있어요")
            }

            if (weeklyRiderCount != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(18.dp), ambientColor = CardShadow, spotColor = CardShadow)
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = "이번 주 부산대에서",
                        color = Color(0xFF8A93A0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                        letterSpacing = (-0.24).sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "${weeklyRiderCount}명이 함께 탔어요",
                        color = MoyeotaColor.InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 29.sp,
                        letterSpacing = (-0.4).sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KakaoYellow)
                    .clickable(onClick = onKakaoStart),
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
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(66.dp))
            }

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

        HomeIndicatorMock()
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

// 와이어프레임 하단 홈 인디케이터 목업
@Composable
internal fun HomeIndicatorMock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 135.dp, height = 5.dp)
                .background(MoyeotaColor.InkPrimary, RoundedCornerShape(2.5.dp)),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenPreview() {
    LoginScreen(
        onKakaoStart = {},
        onEmailStart = {},
        onLogin = {},
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenRetryBannerPreview() {
    LoginScreen(
        onKakaoStart = {},
        onEmailStart = {},
        onLogin = {},
        showRetryBanner = true,
    )
}
