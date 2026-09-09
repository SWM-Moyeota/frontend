package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.AuthPolicy
import java.time.LocalDate

/**
 * 가입 폼 화면들이 공유하는 입력 검증·소형 컴포넌트.
 *
 * 여기 모인 것들은 원래 09 본인 인증 화면 안의 private 선언이었다. 09 가 가입 경로에서 빠지고
 * 실명·생년월일·성별·휴대폰 입력이 10 프로필 만들기로 합쳐지면서, **같은 규칙을 두 파일이
 * 각자 복제하지 않도록** 패키지 공용으로 끌어올렸다(09 파일은 재사용 대비로 남아 있다).
 * 규칙 자체는 하나도 바꾸지 않았다 — 옮기기만 했다.
 */

/**
 * 유효값 검증: 이름 — 한글 또는 영문 2~20자 (서버 `AuthPolicy.NAME_MAX_LENGTH` = 20).
 *
 * 한글 전용이던 규칙을 넓혔다: 에뮬레이터에는 한글 IME 가 없어 한글만 받으면 QA 가 막히고,
 * 서버도 한글을 강제하지 않는다.
 */
internal val PersonNameRegex = Regex("^[가-힣a-zA-Z]{2,20}$")

/**
 * 닉네임 규칙 — 서버 도메인 VO `Nickname` 과 **같은 정규식**이다(앞뒤 공백 strip 후
 * `^[가-힣a-zA-Z0-9]{2,10}$`). 화면이 같은 기준으로 먼저 걸러 400 왕복을 줄일 뿐,
 * 최종 판정은 서버다.
 *
 * 2026-09 서버 변경으로 닉네임이 가입 필수가 되면서, 예전 「표시 이름 3~8자」 규칙을 대체했다.
 * 금칙어 목록은 서버에 없는 앱 자체 정책이라 그대로 유지한다.
 */
internal object NicknamePolicy {
    /** 규칙 자체는 도메인 [AuthPolicy] 가 갖는다 — 여기서는 "왜 틀렸는지"만 문구로 옮긴다. */
    const val MAX_LENGTH = AuthPolicy.NICKNAME_MAX_LENGTH
    private const val MIN_LENGTH = AuthPolicy.NICKNAME_MIN_LENGTH
    private val CharRegex = Regex("^[가-힣a-zA-Z0-9]+$")

    // 금칙어·욕설 + 운영자 사칭어(모여타·관리자)
    private val BannedWords = listOf(
        "모여타", "관리자", "운영자", "admin",
        "시발", "씨발", "병신", "새끼", "지랄", "미친", "좆", "썅",
    )

    fun isValid(raw: String): Boolean = validate(raw) == null

    /**
     * 형식 오류 문구. 규칙을 만족하면 null.
     * 빈 입력은 "아직 안 썼다"이지 오류가 아니므로 호출부가 먼저 걸러 쓴다.
     */
    fun validate(raw: String): String? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> "닉네임을 입력해 주세요"
            !CharRegex.matches(value) -> "한글·영문·숫자만 쓸 수 있어요 (공백·특수문자·이모지 불가)"
            !AuthPolicy.isValidNickname(value) -> "닉네임은 ${MIN_LENGTH}~${MAX_LENGTH}자로 입력해 주세요"
            BannedWords.any { value.contains(it) } -> "사용할 수 없는 닉네임이에요"
            else -> null
        }
    }
}

/** 생년월일 입력 하한 — 이보다 이르면 오타로 본다 */
private const val MIN_BIRTH_YEAR = 1900

/**
 * YYYYMMDD 8자리를 [LocalDate] 로. 형식이 맞아도 실제로 없는 날짜(20250230)면 null.
 *
 * [LocalDate.of] 는 그런 값에 예외를 던지므로 여기서 삼키고 null 로 바꾼다
 * — 화면은 "8자리는 찼는데 날짜가 아니다"를 필드 에러로 보여주면 된다.
 */
internal fun parseBirthDate(digits: String): LocalDate? {
    if (digits.length != 8) return null
    val year = digits.take(4).toIntOrNull() ?: return null
    if (year < MIN_BIRTH_YEAR) return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
    // 미래 날짜는 생년월일일 수 없다
    return date.takeIf { !it.isAfter(LocalDate.now()) }
}

/**
 * 서버가 형식을 최종 판정하므로 여기서는 "@ 와 . 가 있는 한 덩어리"만 본다.
 * 정교한 RFC 검사를 흉내 내면 정상 주소를 막는 쪽 사고가 더 흔하다.
 */
internal val SimpleEmailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** 선택지 칩 안의 글자색 (미선택 상태) */
private val SlateGray = Color(0xFF4B5563)

/**
 * 가입 폼의 한 줄 선택지 칩 — 성별 2택처럼 값이 몇 개 안 되는 선택에 쓴다.
 * (09 의 통신사 4택에서 쓰던 모양 그대로다)
 */
@Composable
internal fun SignupChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val base = if (selected) {
        modifier
            .height(44.dp)
            .background(MoyeotaColor.Primary50, shape)
            .border(1.5.dp, MoyeotaColor.Primary500, shape)
    } else {
        modifier
            .height(44.dp)
            .shadow(2.dp, shape)
            .background(Color.White, shape)
    }
    Box(
        modifier = base.clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MoyeotaType.BodySm.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) MoyeotaColor.Primary500 else SlateGray,
        )
    }
}
