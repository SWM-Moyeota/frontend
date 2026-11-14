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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.presentation.core.SupportLinks
import com.moyeota.presentation.core.openWebPage

private val ScreenBackground = Color(0xFFF5F7FA)
private val TextSlate = Color(0xFF4B5563)
private val TextFaint = Color(0xFF9AA1AC)
private val CardShadow = Color(0x0F1B2A4A)

/**
 * 04 시작 화면의 신뢰 불릿 두 줄. 둘 다 서버 동작으로 확인한 사실이다.
 *
 * - 휴대폰 인증: 문자 인증 토큰이 없으면 서버가 가입을 `USER207 PHONE_NOT_VERIFIED` 로 거절한다
 *   (`AuthError.PHONE_NOT_VERIFIED`). 앱만의 검사가 아니라 **서버가 막는다**.
 * - 탑승 횟수: 서버 `PartyDetailResult.MemberInfo.rideCount` 를 20·21·22·23 이 그대로 그린다
 *   (`User.rideCountLabel`).
 *
 * 2026-10-13 에 지운 두 줄과 이유:
 * - 「같은 성별끼리만 매칭돼요」 — **성별 조건 매칭이 없다.** 성별은 가입 때 받아 `UserProfile` 에
 *   저장될 뿐이고, 백엔드 `matching` 패키지 어디에도 성별이 등장하지 않는다.
 * - 「운행 중 위치를 지인과 공유할 수 있어요」 — **위치 공유 기능이 없다**
 *   (`docs/BACKEND-GAP-CLEANUP.md` §1 에서 보호자 공유·동승자 위치 보고를 전부 제거했다).
 *
 * 불릿은 **두 줄로 유지한다** — 카드 여백이 두 줄 기준으로 맞춰져 있다(아래 주석 참고).
 */
internal val LoginTrustBullets = listOf(
    "휴대폰 인증을 마친 사람만 가입해요",
    "함께 탈 사람의 탑승 횟수가 보여요",
)

/**
 * 「가입하면 이용약관 · 개인정보 처리방침에 동의하게 돼요」 — **문서 이름 두 조각만** 링크다.
 *
 * 문장은 주소가 있든 없든 똑같다. 동의하게 된다는 사실 자체는 주소와 무관하게 참이기 때문이다
 * (가입하면 실제로 그 두 문서에 동의한다). 주소가 null 인 문서만 링크 없는 평범한 글자로 남는다
 * — 35 마이페이지가 행을 숨기는 것과 같은 원칙이다(죽은 링크를 보여 주지 않는다).
 *
 * 링크를 누르면 [onOpen] 이 불린다. [LinkAnnotation.Url] 의 기본 동작(플랫폼 URI 핸들러)에
 * 맡기지 않는 이유: **브라우저가 없는 기기에서 그 경로는 예외를 던진다.** 앱의 다른 외부 이동과
 * 똑같이 `core/ExternalIntents` 를 지나게 해서 안내 토스트로 바꾼다.
 *
 * 줄 전체를 탭 영역으로 두지 않는다 — 두 문서를 가려낼 수 없어서다(그전에는 줄 전체에
 * 아무 일도 하지 않는 `clickable` 이 걸려 있었다).
 */
internal fun signupConsentNotice(
    termsUrl: String? = SupportLinks.TERMS_URL,
    privacyUrl: String? = SupportLinks.PRIVACY_URL,
    onOpen: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append("가입하면 ")
    appendLegalDocument(label = "이용약관", url = termsUrl, onOpen = onOpen)
    append(" · ")
    appendLegalDocument(label = "개인정보 처리방침", url = privacyUrl, onOpen = onOpen)
    append("에 동의하게 돼요")
}

/** 주소가 있으면 링크로, 없으면 그냥 글자로 붙인다. 11sp 회색 문장에서 링크만 파랑 + 밑줄이다. */
private fun AnnotatedString.Builder.appendLegalDocument(
    label: String,
    url: String?,
    onOpen: (String) -> Unit,
) {
    if (url == null) {
        append(label)
        return
    }
    withLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = MoyeotaColor.Link,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
        ) { onOpen(url) },
    ) {
        append(label)
    }
}

/**
 * 04 · 시작 — 로그인 방식 [S01]
 *
 * 진입: 03 시작하기 · 온보딩 완료 후 재실행 · 로그아웃 직후
 *
 * @param onEmailStart 「일반 회원가입」 → 10 프로필 설정 (가입 1단계)
 * @param onLogin 「로그인」(이미 계정이 있어요) → 04a 아이디 로그인
 *
 * 구글 버튼은 앱 계층 Credential Manager로 연결한다. 신규 회원은 프로필·문자 인증으로 이어진다.
 *
 * 신뢰 불릿은 [LoginTrustBullets] 에 있다 — 가입 전 마지막으로 "모여타가 무엇을 해 주는가"를
 * 말하는 자리라 **코드로 확인한 사실만** 적는다(2026-10-13).
 */
@Composable
fun LoginScreen(
    onEmailStart: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onGoogle: () -> Unit = {},
    googleBusy: Boolean = false,
    errorMessage: String? = null,
) {
    val context = LocalContext.current
    // 문장은 고정이므로 재구성마다 다시 만들지 않는다 (링크 두 개짜리 AnnotatedString)
    val consentNotice = remember(context) { signupConsentNotice { url -> context.openWebPage(url) } }

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
                text = "구글 또는 아이디로 간편하게 시작해요",
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
                LoginTrustBullets.forEach { TrustBulletRow(text = it) }
            }

            Spacer(Modifier.height(24.dp))
        }

        // 「일반 회원가입」 묶음은 스크롤 밖 = 화면 하단 고정.
        // 본문이 짧아 고정 Spacer 로 CTA 위치를 잡으면 본문 아래에 빈 공간이 남는다.
        // 하단 고정이면 남는 높이를 스크롤 영역이 흡수한다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            if (errorMessage != null) {
                com.moyeota.core.designsystem.component.NoticeBanner(
                    kind = com.moyeota.core.designsystem.component.NoticeKind.ERROR, text = errorMessage)
                Spacer(Modifier.height(12.dp))
            }
            GoogleContinueButton(busy = googleBusy, onClick = onGoogle)
            Spacer(Modifier.height(12.dp))
            PrimaryCtaButton(
                text = "일반 회원가입",
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
            // 문구가 두 문서를 **이름으로** 부르므로 그 두 조각만 링크로 만든다. 2026-10-14 까지는
            // 줄 전체에 아무 일도 하지 않는 clickable 이 걸려 있었다(주소가 없어 열 곳이 없었다).
            // 이제 주소가 생겼고, 동의를 안내하는 자리에서 그 문서에 닿지 못하면 안내가 반쪽이다.
            Text(
                text = consentNotice,
                color = TextFaint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = (-0.22).sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
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

/**
 * 「Google로 계속하기」 — 왼쪽에 Google 로고, 가입과 로그인을 한 버튼으로 받는다.
 * 모양은 디자인 시스템 SecondaryButton(테두리 알약, 52dp)과 같게 맞춘다.
 */
@Composable
private fun GoogleContinueButton(busy: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        enabled = !busy,
        shape = androidx.compose.foundation.shape.CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (!busy) MoyeotaColor.BorderStrong else MoyeotaColor.Border),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MoyeotaColor.InkPrimary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
    ) {
        if (!busy) {
            // 로고는 장식이다 - 버튼 글자가 이미 Google 을 말하므로 스크린리더에 따로 읽히지 않게 한다
            androidx.compose.foundation.Image(
                imageVector = GoogleLogo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text = if (busy) "로그인 중…" else "Google로 계속하기", style = MoyeotaType.ButtonLg)
    }
}

/** Google "G" 로고(18x18). 리소스 폴더가 없는 모듈이라 벡터를 코드로 만든다. 색·모양은 Google 브랜드 지침의 원본 그대로 둔다. */
private val GoogleLogo: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    fun androidx.compose.ui.graphics.vector.ImageVector.Builder.part(color: Long, d: String) = addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(color)),
    )
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "GoogleLogo", defaultWidth = 18.dp, defaultHeight = 18.dp, viewportWidth = 18f, viewportHeight = 18f,
    ).apply {
        part(0xFF4285F4, "M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.615z")
        part(0xFF34A853, "M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332C2.438 15.983 5.482 18 9 18z")
        part(0xFFFBBC05, "M3.964 10.71c-.18-.54-.282-1.117-.282-1.71s.102-1.17.282-1.71V4.958H.957C.347 6.173 0 7.548 0 9s.348 2.827.957 4.042l3.007-2.332z")
        part(0xFFEA4335, "M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0 5.482 0 2.438 2.017.957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z")
    }.build()
}
