package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.domain.model.Gender
import com.moyeota.presentation.core.BirthDateTransformation
import com.moyeota.presentation.core.PhoneNumberTransformation
import java.time.LocalDate

private val LabelGray = Color(0xFF8A93A0)
private val CounterGray = Color(0xFF9AA1AC)
private val SlateGray = Color(0xFF4B5563)
private val InfoCardBlue = Color(0xFFF1F5FD)
private val TrackGray = Color(0xFFE6EAF0)

/**
 * 10 · 프로필 만들기 [S05] — **가입 1단계(1/2), 가입 플로우의 첫 화면**
 *
 * 진입: 04 「이메일로 시작하기」 · 04a 「가입하기」 / 뒤로 → 진입 화면 / 「다음」 → 12 매너 서약
 *
 * 이 화면 하나가 서버 가입(`POST /api/v1/users`)이 요구하는 7개 필드를 **모두** 받는다.
 * 원래 실명·생년월일·성별·휴대폰은 09 본인 인증에서 받았지만, 그 화면의 전제였던 통신사
 * 휴대폰 인증이 MVP 범위에서 빠지면서 인증 없이 값만 묻는 화면이 됐다. 그래서 09 를 경로에서
 * 들어내고 입력만 여기로 옮겼다 — 물어보는 값의 개수는 그대로고, 화면 하나가 줄었다.
 *
 * 필드가 8개라 세 절로 끊어 읽게 했다:
 * - **프로필** — 닉네임 (상대에게 보이는 유일한 이름). 아바타는 단일 기본색 실루엣이다 —
 *   아바타 색을 저장할 서버 필드가 없어 고르게 하지 않는다
 * - **기본 정보** — 이름(실명) · 생년월일 · 성별 · 휴대폰 번호 (서버 가입 필수값)
 * - **로그인 정보** — 아이디 · 비밀번호 · 이메일 (계정 자격증명)
 *
 * 이용약관·개인정보 동의 체크는 여기 두지 않는다 — 12 매너 서약의 동의 묶음으로 합쳤다.
 * 한 가입에서 동의를 두 화면이 나눠 받으면 무엇에 동의했는지가 흩어진다.
 *
 * 2026-09 서버 변경으로 **닉네임이 가입 필수 필드**가 됐다. 예전 「표시 이름」은 가입 API 에
 * 자리가 없어 어디로도 가지 않는 값이었지만, 이제 [SignupDraft.nickname] 으로 그대로 서버에 간다.
 * 규칙도 서버 VO 와 같은 [NicknamePolicy] 로 맞췄다(2~10자 한글·영문·숫자).
 *
 * **만 [AuthPolicy.MIN_AGE] 세 미만은 이 화면에서 막는다.** 개인정보 보호법상 법정대리인 동의 없이는
 * 가입시킬 수 없고, 동의 절차를 만드는 대신 가입 자체를 받지 않기로 했다(개인정보 처리방침의
 * 고지와 동작이 같아야 한다). 서버에는 연령 제한이 없어 **앱이 유일한 방어선**이다 —
 * 판정은 [AuthPolicy.isOldEnough], 문구는 [birthDateError] 가 갖는다.
 *
 * 2026-10 서버 변경으로 **휴대폰 문자 인증이 가입 선행 조건**이 됐다. 인증을 별도 화면으로 빼지 않고
 * 휴대폰 입력란 바로 아래 [PhoneVerificationBlock] 으로 넣었다 — 이유는 그 함수 KDoc 에 적었다.
 * 그래서 이 화면은 서버 가입 **9개 값**(기존 8 + `verificationToken`)을 모두 책임지고,
 * 인증을 끝내지 않으면 「다음」이 열리지 않는다(토큰 없이 제출하면 가입이 전부 400 이다).
 *
 * @param nicknameCheck 중복 확인 결과. 입력이 멈춘 뒤 [ProfileSetupRoute] 가 서버에 물어 갱신한다.
 * @param onNicknameChange 입력이 바뀔 때마다 호출 — 디바운스는 Route 가 한다.
 * @param phoneVerify 문자 인증 상태. 전이 규칙은 [PhoneVerifyState] 의 순수 함수들이 갖고,
 *   이 화면은 그 결과를 그리고 번호 입력란을 잠그는 일만 한다.
 * @param nowMillis 카운트다운(인증번호 3분·재전송 1분) 기준 시각. Route 의 ViewModel 이 틱마다 올려 준다.
 * @param onPhoneDigitsChange 번호가 바뀔 때마다 호출 — 다른 번호가 되면 인증이 무효가 된다.
 * @param onRequestVerificationCode 「인증번호 받기」. 번호를 인자로 넘기는 이유는 입력값의 주인이
 *   이 화면이기 때문이다 — ViewModel 이 번호를 따로 들고 있으면 두 곳이 어긋날 수 있다.
 * @param today 만 나이 계산 기준일. 기기 시계를 기본값으로 두되 **파라미터로 받는다** —
 *   연령 판정의 경계값(생일 당일)은 시계를 고정하지 않으면 검증할 수 없고, 프리뷰도 날짜에 흔들린다.
 * @param onNext 「다음」 → 12 매너 서약. [SignupDraft] 가 서버로 갈 9개 값 전부다.
 */
@Composable
fun ProfileSetupScreen(
    onBack: () -> Unit,
    onNext: (SignupDraft) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    nicknameCheck: NicknameCheckState = NicknameCheckState.Idle,
    onNicknameChange: (String) -> Unit = {},
    phoneVerify: PhoneVerifyState = PhoneVerifyState(),
    nowMillis: Long = 0L,
    onPhoneDigitsChange: (String) -> Unit = {},
    onRequestVerificationCode: (phoneDigits: String) -> Unit = {},
    onVerificationCodeChange: (String) -> Unit = {},
    onSubmitVerificationCode: () -> Unit = {},
    onPhoneEditRequested: () -> Unit = {},
    onGoToLogin: () -> Unit = {},
) {
    // rememberSaveable 이어야 하는 이유: 12 매너 서약에서 가입이 409(닉네임 중복)로 실패하면
    // 「닉네임 바꾸기」가 이 화면으로 popBackStack 한다. 그 사이 이 엔트리는 컴포지션에서 빠져 있어
    // 평범한 remember 였다면 여기 여덟 값이 전부 날아가고 사용자는 가입 폼을 처음부터 다시 채워야 한다.
    var nickname by rememberSaveable { mutableStateOf("") }

    var realName by rememberSaveable { mutableStateOf("") }
    var birthDigits by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf<Gender?>(null) }
    var phoneDigits by rememberSaveable { mutableStateOf("") }

    var loginId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 복원되어 돌아온 닉네임은 중복 확인을 다시 받아야 한다. ViewModel 은 살아남아 직전 결과
    // (예: Available)를 그대로 들고 있는데, 되돌아온 이유가 바로 그 닉네임이 이미 선점됐다는
    // 409 이기 때문이다 — 다시 묻지 않으면 선점된 닉네임에 초록 「사용 가능」이 남는다.
    LaunchedEffect(Unit) {
        if (nickname.isNotBlank()) onNicknameChange(nickname)
    }

    // [유효값 검증 · 프로필]
    // · 닉네임 2~10자 (카운터 「n / 10」 실시간 갱신) — 서버 Nickname VO 와 같은 규칙
    // · 한글·영문·숫자 허용, 공백·특수문자·이모지 불가
    // · 금칙어·욕설 필터, 운영자 사칭어(모여타·관리자) 차단 — 앱 자체 정책
    // · 중복은 서버가 판정한다: 입력이 멈추면 Route 가 조회해 [nicknameCheck] 로 돌려준다
    val nicknameFormatError = if (nickname.isEmpty()) null else NicknamePolicy.validate(nickname)
    val nicknameError: String? = nicknameFormatError ?: when (nicknameCheck) {
        // 서버가 형식으로 400 을 준 경우 — 앱 규칙과 어긋난 문자가 있다는 뜻이라 같은 문구로 안내한다
        NicknameCheckState.InvalidFormat -> "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요"
        NicknameCheckState.Taken -> "이미 사용 중인 닉네임이에요"
        else -> null
    }

    // [유효값 검증 · 기본 정보] — 09 에서 쓰던 규칙을 그대로 옮겼다
    val realNameValid = PersonNameRegex.matches(realName)
    val realNameError =
        if (realName.isNotEmpty() && !realNameValid) "이름은 한글 또는 영문 2~20자로 입력해 주세요" else null

    // 8자리를 채웠을 때만 실제로 있는 날짜인지까지 본다(20250230 같은 값 차단)
    val birthDate = parseBirthDate(birthDigits, today)
    // 거절 사유 셋(없는 날짜 · 오늘 이후 · 만 14세 미만)을 각각 다른 문구로 말한다.
    // 규칙과 문구는 birthDateError 가 갖는다 — 09 본인 인증도 같은 함수를 쓰므로 두 화면이 어긋나지 않는다.
    val birthError = birthDateError(birthDigits, today)
    // 「다음」 판정은 문구와 같은 근거를 본다: 할 말이 남아 있으면 넘어가지 않는다
    val birthValid = birthDate != null && birthError == null

    // 휴대폰 — 010 + 숫자 8자리, 하이픈은 표시에만. 판정은 인증 블록과 같은 함수를 쓴다
    // (「인증번호 받기」는 활성인데 「다음」은 번호가 틀렸다고 막는 어긋남을 구조적으로 없앤다)
    val phoneValid = isCompletePhoneDigits(phoneDigits)
    val phoneError = when {
        phoneDigits.isEmpty() -> null
        phoneDigits.length >= 3 && !phoneDigits.startsWith("010") ->
            "010으로 시작하는 휴대폰 번호만 입력할 수 있어요"
        phoneDigits.length in 3..10 || phoneValid -> null
        else -> "휴대폰 번호는 010 + 숫자 8자리로 입력해 주세요"
    }

    // [유효값 검증 · 로그인 정보] — 서버 Bean Validation 과 같은 규칙(AuthPolicy). 최종 판정은 서버
    val loginIdError = when {
        loginId.isEmpty() -> null
        !AuthPolicy.isValidLoginId(loginId) -> "영소문자로 시작하는 영소문자·숫자·_ 4~20자로 입력해 주세요"
        else -> null
    }
    val passwordError = when {
        password.isEmpty() -> null
        !AuthPolicy.isValidPassword(password) -> "영문·숫자·특수문자를 각각 하나 이상 포함해 8자 이상"
        else -> null
    }
    val emailError = when {
        email.isEmpty() -> null
        !SimpleEmailRegex.matches(email) -> "이메일 형식으로 입력해 주세요"
        email.length > AuthPolicy.EMAIL_MAX_LENGTH -> "이메일은 ${AuthPolicy.EMAIL_MAX_LENGTH}자를 넘을 수 없어요"
        else -> null
    }

    val basicValid = realNameValid && birthValid && gender != null && phoneValid
    // 문자 인증 없이 제출하면 서버가 가입을 400 으로 전부 거절한다 — 여기서 먼저 막는다
    val phoneVerified = phoneVerify.stage == PhoneVerifyStage.Verified
    val accountValid = loginId.isNotEmpty() && loginIdError == null &&
        password.isNotEmpty() && passwordError == null &&
        email.isNotEmpty() && emailError == null
    // 중복 확인이 아직 진행 중이거나 네트워크로 실패했어도 다음 단계는 막지 않는다
    // — 서버가 가입 시점에 409 로 최종 판정하고, 12 화면이 그 실패를 배너로 되돌려 준다.
    val isValid = NicknamePolicy.isValid(nickname) && nicknameError == null &&
        basicValid && phoneVerified && accountValid

    Column(modifier = modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                // 가입은 10 → 12 두 단계다 (09 본인 인증은 MVP 범위에서 빠졌다)
                Text(
                    text = "1 / 2",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = LabelGray,
                )
            },
        )
        ProfileStepProgressBar(progress = 1f / 2f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "가입 정보를 알려주세요",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                // 실명·성별은 가입 시 서버에 저장된다 — "저장하지 않아요"로 안내하면 사실과 다르다
                text = "실명과 성별은 매칭 안전을 위해서만 사용해요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(22.dp))
            ProfileAvatarPlaceholder(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(28.dp))
            ProfileSectionHeader(
                title = "프로필",
                description = "탑승 상대에게는 실명 대신 닉네임으로 보여요",
            )
            Spacer(Modifier.height(16.dp))
            MoyeotaTextField(
                value = nickname,
                // 공백은 규칙상 못 쓰는 문자다 — 오류로 튕기기보다 조용히 걸러 받는다
                onValueChange = { new ->
                    nickname = new.filter { !it.isWhitespace() }.take(NicknamePolicy.MAX_LENGTH)
                    onNicknameChange(nickname)
                },
                label = "닉네임",
                placeholder = "2~10자 한글·영문·숫자",
                errorText = nicknameError,
                helperText = when {
                    nicknameError != null -> null
                    nicknameCheck == NicknameCheckState.Checking -> "사용할 수 있는지 확인하고 있어요"
                    nickname.isNotEmpty() -> "탑승 상대에게는 「$nickname」 으로 보여요"
                    else -> null
                },
                trailing = {
                    Text(
                        text = "${nickname.length} / ${NicknamePolicy.MAX_LENGTH}",
                        style = MoyeotaType.CaptionMd,
                        fontWeight = FontWeight.Medium,
                        color = CounterGray,
                    )
                },
            )
            // 사용 가능 확인만 초록으로 따로 세운다 — 나머지 상태는 필드가 자기 색으로 그린다.
            // 확인에 실패한 경우(네트워크)는 아무 말도 하지 않는다: 사용자가 고칠 수 있는 게 없다.
            if (nicknameError == null && nicknameCheck == NicknameCheckState.Available) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "사용 가능한 닉네임이에요",
                    style = MoyeotaType.CaptionMd,
                    fontWeight = FontWeight.Medium,
                    color = MoyeotaColor.Success500,
                )
            }

            Spacer(Modifier.height(28.dp))
            ProfileSectionHeader(
                title = "기본 정보",
                description = "가입 확인과 동성 매칭에 쓰는 정보예요",
            )

            Spacer(Modifier.height(16.dp))
            MoyeotaTextField(
                value = realName,
                onValueChange = { realName = it.take(20) },
                label = "이름",
                placeholder = "실명을 입력해 주세요",
                errorText = realNameError,
            )

            Spacer(Modifier.height(20.dp))
            // value 는 원시 숫자만. 하이픈은 VisualTransformation 이 그린다 — 표시 문자열을 value 로
            // 넘기면 길이가 튀는 순간 커서가 옛 인덱스에 남아 입력 순서가 뒤집힌다(D-7).
            MoyeotaTextField(
                value = birthDigits,
                onValueChange = { new -> birthDigits = new.filter { it.isDigit() }.take(8) },
                label = "생년월일",
                placeholder = "2000-01-01",
                errorText = birthError,
                // 다 쓰고 나서 "가입할 수 없어요"를 보는 대신 미리 안다. 오류가 있으면 필드가 그쪽을 띄운다
                helperText = BirthDateHelperText,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = BirthDateTransformation,
            )

            Spacer(Modifier.height(20.dp))
            Text(text = "성별", style = MoyeotaType.BodySm, color = LabelGray)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SignupChoiceChip(
                    text = "남성",
                    selected = gender == Gender.MALE,
                    onClick = { gender = Gender.MALE },
                    modifier = Modifier.weight(1f),
                )
                SignupChoiceChip(
                    text = "여성",
                    selected = gender == Gender.FEMALE,
                    onClick = { gender = Gender.FEMALE },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                // 09 의 「성별은 동성 매칭에만 써요」 안내 카드를 필드 바로 아래 한 줄로 옮겼다
                // — 필드가 7개인 화면에서 카드를 하나 더 세우면 읽는 흐름이 끊긴다
                text = "동성 매칭 확인에만 쓰고 프로필에는 표시되지 않아요",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = LabelGray,
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = phoneDigits,
                onValueChange = { new ->
                    phoneDigits = new.filter { it.isDigit() }.take(11)
                    // 번호가 바뀌면 인증이 무효다 — 무효화 판정은 Route 의 순수 전이가 한다
                    onPhoneDigitsChange(phoneDigits)
                },
                label = "휴대폰 번호",
                placeholder = "010-1234-5678",
                // 형식 오류(앱 판정)를 서버 응답 문구보다 앞세운다 — 고칠 곳이 더 구체적이다
                errorText = phoneError ?: phoneVerify.phoneMessage,
                // 「다음」이 왜 잠겨 있는지를 비활성 버튼만으로는 알 수 없다 — 아직 인증을 시작하지
                // 않았을 때만 한 줄로 알려준다(인증 중·완료 상태는 블록이 스스로 말한다)
                helperText = if (phoneVerify.stage == PhoneVerifyStage.Idle) {
                    "인증번호를 받아 확인해야 가입할 수 있어요"
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneNumberTransformation,
                // 인증을 끝낸 번호는 잠근다: 서버가 토큰을 번호에 묶으므로 한 글자만 고쳐도 가입이 400 이다.
                // 고치려면 블록의 「번호 변경」을 눌러 인증을 버리는 쪽으로만 길을 둔다.
                enabled = !phoneVerified,
            )
            PhoneVerificationBlock(
                state = phoneVerify,
                nowMillis = nowMillis,
                phoneDigits = phoneDigits,
                onRequestCode = { onRequestVerificationCode(phoneDigits) },
                onCodeChange = onVerificationCodeChange,
                onSubmitCode = onSubmitVerificationCode,
                onPhoneEditRequested = onPhoneEditRequested,
                onGoToLogin = onGoToLogin,
            )

            Spacer(Modifier.height(28.dp))
            ProfileSectionHeader(
                title = "로그인 정보",
                description = "다음에 로그인할 때 쓰는 아이디와 비밀번호예요",
            )

            Spacer(Modifier.height(16.dp))
            MoyeotaTextField(
                value = loginId,
                // 규칙상 영소문자만 허용된다 — 대문자를 눌러도 조용히 낮춰 받는다
                onValueChange = { new -> loginId = new.filter { !it.isWhitespace() }.lowercase().take(20) },
                label = "아이디",
                placeholder = "moyeota",
                errorText = loginIdError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = password,
                onValueChange = { password = it.take(AuthPolicy.PASSWORD_MAX_LENGTH) },
                label = "비밀번호",
                placeholder = "영문·숫자·특수문자 조합 8자 이상",
                errorText = passwordError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    Text(
                        text = if (passwordVisible) "숨기기" else "보기",
                        style = MoyeotaType.CaptionMd,
                        fontWeight = FontWeight.Bold,
                        color = CounterGray,
                        modifier = Modifier.clickable { passwordVisible = !passwordVisible },
                    )
                },
            )

            Spacer(Modifier.height(20.dp))
            MoyeotaTextField(
                value = email,
                onValueChange = { new -> email = new.filter { !it.isWhitespace() } },
                label = "이메일",
                placeholder = "moyeota@example.com",
                errorText = emailError,
                helperText = "비밀번호를 잊었을 때 쓰는 주소예요",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InfoCardBlue, RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "사진은 올리지 않아요",
                    style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = MoyeotaColor.InkPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // 가입 중에는 어떤 인증 배지도 발급되지 않는다(학교·재직 인증은 가입 경로에 없다)
                    // — 배지를 근거로 들면 없는 것을 있다고 말하는 셈이라 매너 기록만 남겼다
                    text = "얼굴 사진 없이도 매너 기록으로 서로를 확인할 수 있어요",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = SlateGray,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 미입력·규칙 위반 시 「다음」 비활성
            PrimaryCtaButton(
                text = "다음",
                onClick = {
                    val birth = birthDate ?: return@PrimaryCtaButton
                    val genderValue = gender ?: return@PrimaryCtaButton
                    onNext(
                        SignupDraft(
                            email = email,
                            nickname = nickname.trim(),
                            name = realName.trim(),
                            // 하이픈 없는 숫자열로 넘긴다 — **문자 인증에 쓴 번호와 글자 단위로 같은 값**이다.
                            // 서버는 번호를 해시해 토큰과 대조하므로(하이픈은 서버도 떼므로 결과는 같다)
                            // 표기가 갈리면 "인증은 됐는데 가입이 400" 을 디버깅할 때 번호부터 의심하게 된다.
                            phoneNumber = phoneDigits,
                            birthDate = birth,
                            gender = genderValue,
                            loginId = loginId,
                            password = password,
                            // 인증 블록이 받아 둔 토큰. 비어 있으면 「다음」이 열리지 않는다(isValid)
                            verificationToken = phoneVerify.token,
                        ),
                    )
                },
                enabled = isValid,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "닉네임은 나중에 바꿀 수 있어요",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = CounterGray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        NavigationBarSpacer()
    }
}

/** 필드 묶음의 머리말 — 8개 필드를 세 덩어리로 끊어 읽게 한다 */
@Composable
private fun ProfileSectionHeader(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MoyeotaType.BodyMd,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MoyeotaType.CaptionMd,
            fontWeight = FontWeight.Medium,
            color = LabelGray,
        )
    }
}

// 아바타 기본색 (와이어프레임 톤 — core token 미정의)
private val AvatarOuter = Color(0xFFD8E0F2)
private val AvatarInner = Color(0xFF8296C8)

// 와이어프레임의 사람 실루엣 아바타 (사진 업로드 없음 — 정책상 미지원)
@Composable
private fun ProfileAvatarPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(88.dp).clip(CircleShape)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = AvatarOuter)
            // 머리
            drawCircle(
                color = AvatarInner,
                radius = size.width * 0.155f,
                center = Offset(size.width / 2f, size.height * 0.40f),
            )
            // 어깨
            drawCircle(
                color = AvatarInner,
                radius = size.width * 0.30f,
                center = Offset(size.width / 2f, size.height * 0.98f),
            )
        }
    }
}

@Composable
private fun ProfileStepProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .background(TrackGray, RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MoyeotaColor.Primary500, RoundedCornerShape(2.dp)),
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileSetupScreenPreview() {
    MoyeotaTheme {
        ProfileSetupScreen(
            onBack = {},
            onNext = {},
        )
    }
}
