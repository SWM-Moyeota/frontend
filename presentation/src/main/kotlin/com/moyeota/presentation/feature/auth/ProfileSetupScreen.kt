package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private val LabelGray = Color(0xFF8A93A0)
private val CounterGray = Color(0xFF9AA1AC)
private val SlateGray = Color(0xFF4B5563)
private val InfoCardBlue = Color(0xFFF1F5FD)
private val TrackGray = Color(0xFFE6EAF0)

/**
 * 10 · 프로필 만들기 [S05] — **가입 1단계(1/3), 가입 플로우의 첫 화면**
 *
 * 진입: 04 「이메일로 시작하기」 · 04a 「가입하기」 / 뒤로 → 진입 화면 / 「다음」 → 11 안심 설정
 *
 * 이 화면 하나가 서버 가입(`POST /api/v1/users`)이 요구하는 7개 필드를 **모두** 받는다.
 * 원래 실명·생년월일·성별·휴대폰은 09 본인 인증에서 받았지만, 그 화면의 전제였던 통신사
 * 휴대폰 인증이 MVP 범위에서 빠지면서 인증 없이 값만 묻는 화면이 됐다. 그래서 09 를 경로에서
 * 들어내고 입력만 여기로 옮겼다 — 물어보는 값의 개수는 그대로고, 화면 하나가 줄었다.
 *
 * 필드가 7개라 세 절로 끊어 읽게 했다:
 * - **프로필** — 아바타 톤 · 표시 이름 (상대에게 보이는 이름)
 * - **기본 정보** — 이름(실명) · 생년월일 · 성별 · 휴대폰 번호 (서버 가입 필수값)
 * - **로그인 정보** — 아이디 · 비밀번호 · 이메일 (계정 자격증명)
 *
 * 이용약관·개인정보 동의 체크는 여기 두지 않는다 — 12 매너 서약의 동의 묶음으로 합쳤다.
 * 한 가입에서 동의를 두 화면이 나눠 받으면 무엇에 동의했는지가 흩어진다.
 *
 * @param onNext 「다음」 → 11 안심 설정. [SignupDraft] 는 서버로 갈 7개 값 그대로이고,
 *   표시 이름은 가입 API 에 자리가 없어 따로 넘긴다(호출부에서 아직 쓰지 않는다).
 */
@Composable
fun ProfileSetupScreen(
    onBack: () -> Unit,
    onNext: (displayName: String, info: SignupDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(0) }

    var realName by remember { mutableStateOf("") }
    var birthDigits by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<Gender?>(null) }
    var phoneDigits by remember { mutableStateOf("") }

    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // [유효값 검증 · 프로필]
    // · 표시 이름 3~8자 (카운터 「n / 8」 실시간 갱신)
    // · 한글·영문·숫자 허용, 공백·특수문자·이모지 불가
    // · 금칙어·욕설 필터, 운영자 사칭어(모여타·관리자) 차단
    // · 중복 허용 (고유 식별자 아님) — 별도 중복 검사 없음
    val nameError: String? = when {
        name.isEmpty() -> null
        !profileNameCharRegex.matches(name) -> "한글·영문·숫자만 쓸 수 있어요 (공백·특수문자·이모지 불가)"
        name.length < 3 -> "표시 이름은 3~8자로 입력해 주세요"
        profileBannedWords.any { name.contains(it) } -> "사용할 수 없는 표시 이름이에요"
        else -> null
    }

    // [유효값 검증 · 기본 정보] — 09 에서 쓰던 규칙을 그대로 옮겼다
    val realNameValid = PersonNameRegex.matches(realName)
    val realNameError =
        if (realName.isNotEmpty() && !realNameValid) "이름은 한글 또는 영문 2~20자로 입력해 주세요" else null

    // 8자리를 채웠을 때만 실제로 있는 날짜인지까지 본다(20250230 같은 값 차단)
    val birthDate = parseBirthDate(birthDigits)
    val birthError = when {
        birthDigits.length < 8 -> null
        birthDate == null -> "실제로 있는 날짜를 YYYYMMDD 로 입력해 주세요"
        else -> null
    }

    // 휴대폰 — 010 + 숫자 8자리, 하이픈은 표시에만
    val phoneValid = phoneDigits.length == 11 && phoneDigits.startsWith("010")
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

    val basicValid = realNameValid && birthDate != null && gender != null && phoneValid
    val accountValid = loginId.isNotEmpty() && loginIdError == null &&
        password.isNotEmpty() && passwordError == null &&
        email.isNotEmpty() && emailError == null
    val isValid = name.isNotEmpty() && nameError == null && basicValid && accountValid

    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                // 가입은 10 → 11 → 12 세 단계다 (09 본인 인증은 MVP 범위에서 빠졌다)
                Text(
                    text = "1 / 3",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = LabelGray,
                )
            },
        )
        ProfileStepProgressBar(progress = 1f / 3f)

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
            ProfileAvatarPlaceholder(
                tint = profileAvatarPalette[selectedColor],
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                profileAvatarPalette.forEachIndexed { index, swatch ->
                    ProfileColorSwatch(
                        swatch = swatch,
                        selected = index == selectedColor,
                        onClick = { selectedColor = index },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            ProfileSectionHeader(
                title = "프로필",
                description = "탑승 상대에게는 실명 대신 표시 이름으로 보여요",
            )
            Spacer(Modifier.height(16.dp))
            MoyeotaTextField(
                value = name,
                onValueChange = { name = it.take(8) },
                label = "표시 이름",
                placeholder = "예) 김OO",
                errorText = nameError,
                helperText = if (nameError == null && name.isNotEmpty()) {
                    "탑승 상대에게는 「$name」 으로 보여요"
                } else {
                    null
                },
                trailing = {
                    Text(
                        text = "${name.length} / 8",
                        style = MoyeotaType.CaptionMd,
                        fontWeight = FontWeight.Medium,
                        color = CounterGray,
                    )
                },
            )

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
                onValueChange = { new -> phoneDigits = new.filter { it.isDigit() }.take(11) },
                label = "휴대폰 번호",
                placeholder = "010-1234-5678",
                errorText = phoneError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneNumberTransformation,
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
                        name,
                        SignupDraft(
                            email = email,
                            name = realName.trim(),
                            // 서버에는 하이픈이 든 형식으로 보낸다 — 표시용 포매터를 그대로 재사용
                            phoneNumber = PhoneNumberTransformation.format(phoneDigits),
                            birthDate = birth,
                            gender = genderValue,
                            loginId = loginId,
                            password = password,
                        ),
                    )
                },
                enabled = isValid,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "표시 이름은 나중에 바꿀 수 있어요",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = CounterGray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        NavigationBarSpacer()
    }
}

private val profileNameCharRegex = Regex("^[가-힣a-zA-Z0-9]+$")

// 금칙어·욕설 + 운영자 사칭어(모여타·관리자)
private val profileBannedWords = listOf(
    "모여타", "관리자", "운영자", "admin",
    "시발", "씨발", "병신", "새끼", "지랄", "미친", "좆", "썅",
)

/** 필드 묶음의 머리말 — 7개 필드를 세 덩어리로 끊어 읽게 한다 */
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

private data class ProfileAvatarSwatch(val outer: Color, val inner: Color)

private val profileAvatarPalette = listOf(
    ProfileAvatarSwatch(outer = Color(0xFFD8E0F2), inner = Color(0xFF8296C8)),
    ProfileAvatarSwatch(outer = Color(0xFFD5EEDF), inner = Color(0xFF41BA83)),
    ProfileAvatarSwatch(outer = Color(0xFFE3DDF6), inner = Color(0xFF9A82DC)),
    ProfileAvatarSwatch(outer = Color(0xFFF4E3D0), inner = Color(0xFFDFA366)),
)

// 와이어프레임의 사람 실루엣 아바타 (사진 업로드 없음 — 정책상 미지원)
@Composable
private fun ProfileAvatarPlaceholder(tint: ProfileAvatarSwatch, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(88.dp).clip(CircleShape)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = tint.outer)
            // 머리
            drawCircle(
                color = tint.inner,
                radius = size.width * 0.155f,
                center = Offset(size.width / 2f, size.height * 0.40f),
            )
            // 어깨
            drawCircle(
                color = tint.inner,
                radius = size.width * 0.30f,
                center = Offset(size.width / 2f, size.height * 0.98f),
            )
        }
    }
}

@Composable
private fun ProfileColorSwatch(
    swatch: ProfileAvatarSwatch,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MoyeotaColor.Primary500, CircleShape)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(swatch.outer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(13.dp).background(swatch.inner, CircleShape))
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
            onNext = { _, _ -> },
        )
    }
}
