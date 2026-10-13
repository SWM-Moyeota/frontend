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
 * YYYYMMDD 8자리를 **달력에 실제로 있는 날짜**로. 과거·미래는 따지지 않는다.
 *
 * [LocalDate.of] 는 없는 날짜(20250230)에 예외를 던지므로 여기서 삼키고 null 로 바꾼다.
 * 미래 판정을 여기서 하지 않는 이유: 호출부가 "날짜가 아니다"와 "미래다"를 **다른 문구로**
 * 말해야 하는데, 둘을 한 null 로 뭉개면 구분할 방법이 없다([birthDateError]).
 */
private fun parseCalendarDate(digits: String): LocalDate? {
    if (digits.length != 8) return null
    val year = digits.take(4).toIntOrNull() ?: return null
    if (year < MIN_BIRTH_YEAR) return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

/**
 * YYYYMMDD 8자리를 **생년월일로 쓸 수 있는** [LocalDate] 로. 아니면 null.
 *
 * 없는 날짜(20250230)와 미래 날짜를 모두 거른다 — 화면은 이 값이 null 이면 「다음」을 닫고,
 * 왜 닫혔는지는 [birthDateError] 가 말한다. 두 함수가 같은 입력에 대해 어긋나지 않게
 * 같은 [today] 를 받는다.
 *
 * @param today 미래 판정 기준일. 기본값은 기기 시계이고, 테스트는 고정 날짜를 넣는다.
 *   **연령 제한은 여기서 보지 않는다** — 만 14세 미만도 "실제로 있는 과거 날짜"이므로
 *   파싱은 통과시키고, 가입을 막는 일은 [birthDateError] 와 화면의 「다음」 판정이 한다.
 */
internal fun parseBirthDate(digits: String, today: LocalDate = LocalDate.now()): LocalDate? =
    parseCalendarDate(digits)?.takeIf { !it.isAfter(today) }

/**
 * 생년월일 입력의 오류 문구. 쓸 말이 없으면 null.
 *
 * 거절 사유가 셋이고 **할 일이 전부 다르므로 한 문구도 겹치지 않는다**:
 * 1. 달력에 없는 날짜(20250230) → 숫자를 고치면 된다
 * 2. 오늘 이후([AuthPolicy.isValidBirthDate]) → 역시 숫자를 고치면 된다. 보내면 서버가 400 이다
 * 3. 만 [AuthPolicy.MIN_AGE] 세 미만([AuthPolicy.isOldEnough]) → **고칠 게 없다. 가입이 안 된다**
 *
 * 3번을 1·2번과 같은 문구("입력이 올바르지 않습니다" 류)로 접으면 사용자는 날짜를 몇 번 고쳐
 * 보다가 왜 막혔는지 끝까지 모른다. 반대로 1·2번에 "만 14세 미만은…"을 띄우면 2005 를 2025 로
 * 잘못 친 사람에게 엉뚱한 사실을 말하게 된다.
 *
 * 8자리를 채우기 전에는 아무 말도 하지 않는다 — "아직 안 썼다"는 틀린 게 아니다(닉네임과 같은 규칙).
 *
 * @param today 만 나이 계산 기준일. 기본값은 기기 시계다.
 */
internal fun birthDateError(digits: String, today: LocalDate = LocalDate.now()): String? {
    if (digits.length < 8) return null
    val date = parseCalendarDate(digits) ?: return "실제로 있는 날짜를 YYYYMMDD 로 입력해 주세요"
    // 사용자 지역의 오늘(date.isAfter)과 서버 @Past 기준(UTC)을 함께 본다 — 어느 쪽이든 보낼 수 없는 값이다
    if (date.isAfter(today) || !AuthPolicy.isValidBirthDate(date)) {
        return "생년월일은 오늘 이전 날짜로 입력해 주세요"
    }
    if (!AuthPolicy.isOldEnough(date, today)) {
        return "만 ${AuthPolicy.MIN_AGE}세 미만은 가입할 수 없어요. 생년월일을 다시 확인해 주세요"
    }
    return null
}

/** 생년월일 입력란 아래에 늘 띄우는 안내 — 다 쓰고 나서 막히는 대신 **미리** 알려준다. */
internal val BirthDateHelperText = "만 ${AuthPolicy.MIN_AGE}세 이상만 가입할 수 있어요"

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
