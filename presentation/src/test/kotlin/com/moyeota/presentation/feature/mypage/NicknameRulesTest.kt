package com.moyeota.presentation.feature.mypage

import com.moyeota.presentation.feature.auth.NicknameCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 36 프로필 수정의 저장 가능 조건과 오류 문구.
 *
 * 규칙 자체(2~10자 한글·영문·숫자)는 10 프로필 만들기의 `NicknamePolicy` 가 갖고 있고 거기서 검증한다.
 * 여기서 확인하는 건 **수정 화면에만 있는 판단**이다 — "지금 쓰고 있는 이름과 같은가",
 * "중복 확인이 아직 안 끝났어도 저장을 눌러도 되는가".
 */
class NicknameRulesTest {

    @Test
    fun `유효하고 현재 이름과 다르면 저장할 수 있다`() {
        assertTrue(canSaveNickname("길동이", currentNickname = "성윤", check = NicknameCheckState.Available))
    }

    @Test
    fun `현재 이름과 같으면 저장할 게 없다`() {
        assertFalse(canSaveNickname("성윤", currentNickname = "성윤", check = NicknameCheckState.Idle))
        // 앞뒤 공백은 서버 VO 도 strip 하므로 같은 값으로 본다
        assertTrue(isNicknameUnchanged(" 성윤 ", "성윤"))
    }

    @Test
    fun `서버에 이름이 없으면 어떤 유효한 값이든 저장할 수 있다`() {
        assertTrue(canSaveNickname("길동이", currentNickname = null, check = NicknameCheckState.Idle))
        assertFalse(isNicknameUnchanged("길동이", null))
    }

    @Test
    fun `2자 미만이거나 10자를 넘으면 저장할 수 없다`() {
        assertFalse(canSaveNickname("가", currentNickname = "성윤", check = NicknameCheckState.Idle))
        // 11자 — 입력 단계에서 take(10) 으로 걸리지만, 규칙 자체도 거절해야 한다
        assertFalse(canSaveNickname("가나다라마바사아자차카", currentNickname = "성윤", check = NicknameCheckState.Idle))
        // 경계값 10자는 통과한다 (서버 @Size(min=2,max=10) 과 같은 기준)
        assertTrue(canSaveNickname("가나다라마바사아자차", currentNickname = "성윤", check = NicknameCheckState.Idle))
    }

    @Test
    fun `빈 입력은 저장 불가지만 오류 문구는 띄우지 않는다`() {
        assertFalse(canSaveNickname("", currentNickname = "성윤", check = NicknameCheckState.Idle))
        // "아직 안 썼다"는 틀린 게 아니다 — 꺼진 「저장」 으로 충분하다(10 과 같은 규칙)
        assertNull(nicknameEditError("", currentNickname = "성윤", check = NicknameCheckState.Idle))
    }

    @Test
    fun `중복이면 저장할 수 없고 그 사실을 문구로 말한다`() {
        assertFalse(canSaveNickname("길동이", currentNickname = "성윤", check = NicknameCheckState.Taken))
        assertEquals(
            "이미 사용 중인 닉네임이에요",
            nicknameEditError("길동이", currentNickname = "성윤", check = NicknameCheckState.Taken),
        )
    }

    @Test
    fun `서버 형식 오류도 저장을 막는다`() {
        assertFalse(canSaveNickname("길동이", currentNickname = "성윤", check = NicknameCheckState.InvalidFormat))
        assertEquals(
            "닉네임은 한글·영문·숫자 2~10자로 입력해 주세요",
            nicknameEditError("길동이", currentNickname = "성윤", check = NicknameCheckState.InvalidFormat),
        )
    }

    @Test
    fun `확인 중이거나 확인에 실패해도 저장은 막지 않는다 - 최종 판정은 서버 409`() {
        assertTrue(canSaveNickname("길동이", currentNickname = "성윤", check = NicknameCheckState.Checking))
        assertTrue(canSaveNickname("길동이", currentNickname = "성윤", check = NicknameCheckState.Unknown))
        // 고칠 수 있는 게 없는 상태라 아무 말도 하지 않는다
        assertNull(nicknameEditError("길동이", currentNickname = "성윤", check = NicknameCheckState.Unknown))
    }

    @Test
    fun `저장 중에는 다시 누를 수 없다`() {
        assertFalse(
            canSaveNickname("길동이", currentNickname = "성윤", check = NicknameCheckState.Available, saving = true),
        )
    }

    @Test
    fun `내 닉네임을 그대로 두면 Taken 이 와도 남의 것처럼 말하지 않는다`() {
        // 자기 닉네임을 서버에 물으면 당연히 「사용 중」이 돌아온다 — 그 응답을 그대로 띄우면 안 된다
        assertNull(nicknameEditError("성윤", currentNickname = "성윤", check = NicknameCheckState.Taken))
    }

    @Test
    fun `형식 오류 문구는 10 프로필 만들기의 규칙을 그대로 쓴다`() {
        // 특수문자·금칙어 판정은 NicknamePolicy 몫 — 여기서는 그 문구가 그대로 올라오는지만 본다
        assertNotNull(nicknameEditError("길동!", currentNickname = "성윤", check = NicknameCheckState.Idle))
        assertNotNull(nicknameEditError("모여타", currentNickname = "성윤", check = NicknameCheckState.Idle))
        assertFalse(canSaveNickname("모여타", currentNickname = "성윤", check = NicknameCheckState.Available))
    }
}
