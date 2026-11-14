package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.CheckIcon
import com.moyeota.core.designsystem.component.MoyeotaIconSize
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.TextLinkButton
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaSpacing
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.presentation.core.SupportLinks
import com.moyeota.presentation.core.openWebPage

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
 * 불릿은 **두 줄로 유지한다** — 화면 높이가 두 줄 기준으로 맞춰져 있다.
 * (2026-11 디자인 QA A1: 그림자 카드를 걷어내고 페이지 위에 바로 놓았다 — 카드 하나에 두 줄을 담으면
 * "강조 상자"가 되어 바로 아래 CTA 와 시선을 다툰다.)
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

/** 주소가 있으면 링크로, 없으면 그냥 글자로 붙인다. 12sp 회색 문장에서 링크만 파랑 + 밑줄이다. */
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
 * @param onEmailStart 「이메일로 시작하기」 → 10 프로필 설정 (가입 1단계)
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
            .background(MoyeotaColor.SurfaceCanvas),
    ) {
        StatusBarSpacer()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoyeotaSpacing.ScreenGutter),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "모여타",
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
            )

            Spacer(Modifier.height(32.dp))
            Text(
                text = "믿을 수 있는 사람과\n택시를 나눠 타요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                // 학교 이메일 인증은 가입 필수 경로가 아니다(마이페이지에서 나중에 추가하는 컨셉).
                // 「학생만 · 학교 이메일로 확인」은 이제 사실이 아니라 지웠다.
                text = "구글 또는 아이디로 간편하게 시작해요",
                style = MoyeotaType.BodyMd,
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 「학교 인증을 마친 학생만 매칭돼요」는 삭제 — 가입에 학교 인증 단계가 없어 사실이 아니다
                LoginTrustBullets.forEach { TrustBulletRow(text = it) }
            }

            Spacer(Modifier.height(24.dp))
        }

        // 「이메일로 시작하기」 묶음은 스크롤 밖 = 화면 하단 고정.
        // 본문이 짧아 고정 Spacer 로 CTA 위치를 잡으면 본문 아래에 빈 공간이 남는다.
        // 하단 고정이면 남는 높이를 스크롤 영역이 흡수한다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MoyeotaSpacing.ScreenGutter),
        ) {
            if (errorMessage != null) {
                com.moyeota.core.designsystem.component.NoticeBanner(
                    kind = com.moyeota.core.designsystem.component.NoticeKind.ERROR, text = errorMessage)
                Spacer(Modifier.height(12.dp))
            }
            com.moyeota.core.designsystem.component.SecondaryButton(
                text = if (googleBusy) "로그인 중…" else "Google로 계속하기",
                onClick = onGoogle, enabled = !googleBusy,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryCtaButton(
                text = "아이디로 가입하기",
                onClick = onEmailStart,
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "이미 계정이 있어요",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                )
                Spacer(Modifier.size(4.dp))
                // 글자 크기 그대로 눌리던 링크(≈19dp, 눌림 표시 없음)를 48dp 터치 + 물결로 바꿨다(디자인 QA G12)
                TextLinkButton(
                    text = "로그인",
                    onClick = onLogin,
                    color = MoyeotaColor.Primary600,
                )
            }

            Spacer(Modifier.height(4.dp))
            // 문구가 두 문서를 **이름으로** 부르므로 그 두 조각만 링크로 만든다. 2026-10-14 까지는
            // 줄 전체에 아무 일도 하지 않는 clickable 이 걸려 있었다(주소가 없어 열 곳이 없었다).
            // 이제 주소가 생겼고, 동의를 안내하는 자리에서 그 문서에 닿지 못하면 안내가 반쪽이다.
            // 11sp `#9AA1AC`(2.6:1)는 읽혀야 하는 동의 고지에 너무 흐렸다 — 캡션 크기·TextMute 로 올린다
            Text(
                text = consentNotice,
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }

        NavigationBarSpacer()
    }
}

/**
 * 회색 체크 + 안내 문구 한 줄.
 *
 * 예전엔 22dp 파란 원 안의 흰 체크였다 — 화면 아래 파란 CTA 와 같은 색 덩어리가 두 개 더 생겨
 * "어디를 눌러야 하나"가 흐려졌다. 불릿은 누르는 것이 아니라 읽는 것이라 회색 선 아이콘으로 낮춘다.
 */
@Composable
private fun TrustBulletRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckIcon(size = MoyeotaIconSize.Sm, tint = MoyeotaColor.TextMute)
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MoyeotaType.BodyMd,
            color = MoyeotaColor.TextBody,
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
