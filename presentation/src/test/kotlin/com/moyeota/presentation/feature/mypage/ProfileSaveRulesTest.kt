package com.moyeota.presentation.feature.mypage

import com.moyeota.presentation.feature.auth.NicknameCheckState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 36 프로필 수정의 「저장」 게이트 — 닉네임과 사진을 **함께** 보는 판정([canSaveProfile]).
 *
 * `canSaveNickname` 은 「닉네임만의」 판정이고 `NicknameRulesTest` 가 그걸 못 박고 있다.
 * 여기서 확인하는 건 사진이 끼어든 뒤의 합성 규칙이다.
 *
 * 이 테스트가 있는 이유: 사진 선택을 붙이기 전의 이 화면은 게이트를 `canSaveNickname` 하나로
 * 두고 있었다. 그러면 프리필된 닉네임을 그대로 둔 채 사진만 고른 사용자의 「저장」 이
 * **영원히 꺼져 있다** — 첫 테스트가 바로 그 자리다.
 */
class ProfileSaveRulesTest {

    @Test
    fun `닉네임을 그대로 두고 이미지만 바꿔도 저장할 수 있다`() {
        // 이 화면에 들어오는 가장 흔한 경로다: 닉네임은 건드리지 않고 사진만 바꾼다.
        assertTrue(
            canSaveProfile(
                nickname = "성윤",
                currentNickname = "성윤",
                check = NicknameCheckState.Idle,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
            ),
        )
        // 닉네임만의 판정은 같은 입력을 여전히 거절한다 — 그래서 합성 함수가 따로 필요했다.
        assertFalse(canSaveNickname("성윤", currentNickname = "성윤", check = NicknameCheckState.Idle))
    }

    @Test
    fun `기본 이미지로 되돌리기만 골라도 저장할 수 있다`() {
        // Reset 은 PATCH 가 아니라 DELETE 로 나가지만 「저장할 게 있다」는 사실은 Pick 과 같다.
        assertTrue(
            canSaveProfile(
                nickname = "성윤",
                currentNickname = "성윤",
                check = NicknameCheckState.Idle,
                imageIntent = ImageIntent.Reset,
            ),
        )
    }

    @Test
    fun `닉네임도 이미지도 그대로면 저장할 게 없다`() {
        assertFalse(
            canSaveProfile(
                nickname = "성윤",
                currentNickname = "성윤",
                check = NicknameCheckState.Idle,
                imageIntent = ImageIntent.None,
            ),
        )
    }

    @Test
    fun `이미지를 골랐어도 닉네임이 2자 미만이면 저장할 수 없다`() {
        // 깨진 닉네임을 같이 실어 보내면 서버가 400 으로 **요청 전체를 거절**해 이미지도 안 바뀐다.
        // 버튼을 켜 두면 눌러 보고 아무것도 안 바뀐 이유를 스스로 유추해야 한다.
        assertFalse(
            canSaveProfile(
                nickname = "가",
                currentNickname = "성윤",
                check = NicknameCheckState.Idle,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
            ),
        )
    }

    @Test
    fun `저장 중에는 다시 누를 수 없다`() {
        assertFalse(
            canSaveProfile(
                nickname = "길동이",
                currentNickname = "성윤",
                check = NicknameCheckState.Available,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
                saving = true,
            ),
        )
    }

    @Test
    fun `이미지를 골랐어도 닉네임이 중복이면 저장할 수 없다`() {
        // 서버는 imageKey 를 닉네임 중복보다 **먼저** 검사하지만, 둘 중 하나라도 틀리면 요청 전체가
        // 거절된다 — 409 로 돌아오면 사진도 안 바뀐 상태다.
        assertFalse(
            canSaveProfile(
                nickname = "길동이",
                currentNickname = "성윤",
                check = NicknameCheckState.Taken,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
            ),
        )
    }

    @Test
    fun `닉네임 칸이 비어 있는 계정도 이미지만 바꿔 저장할 수 있다`() {
        // 닉네임이 없는 계정에는 서버가 마스킹된 실명을 표시 이름으로 주고, 그 값은 규칙에 안 맞아
        // 입력란을 채우지 않는다(QA 113 O1). 그 빈 칸을 「닉네임을 지웠다」로 읽으면 못 쓰는 값을
        // 건드린 것이 되어 사진만 고른 저장까지 막힌다.
        assertTrue(
            canSaveProfile(
                nickname = "",
                currentNickname = "김*윤",
                check = NicknameCheckState.Idle,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
            ),
        )
        assertFalse(isNicknameEdited("", currentNickname = "김*윤"))
        // 빈 칸인 채로 사진도 안 골랐으면 저장할 게 없다.
        assertFalse(
            canSaveProfile(
                nickname = "",
                currentNickname = "김*윤",
                check = NicknameCheckState.Idle,
                imageIntent = ImageIntent.None,
            ),
        )
    }

    @Test
    fun `닉네임과 이미지를 함께 바꿀 수 있다`() {
        assertTrue(
            canSaveProfile(
                nickname = "길동이",
                currentNickname = "성윤",
                check = NicknameCheckState.Available,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
            ),
        )
    }

    @Test
    fun `되돌리기는 기본 이미지를 쓰면서 아무것도 안 골랐을 때만 숨는다`() {
        // 멱등한 DELETE 를 한 번 더 보내는 것뿐이라 누를 이유가 없다
        assertFalse(canResetProfileImage(usingDefaultImage = true, imageIntent = ImageIntent.None))
        // 16장 중 하나를 쓰고 있다 — 돌아갈 곳이 있다
        assertTrue(canResetProfileImage(usingDefaultImage = false, imageIntent = ImageIntent.None))
        // 이 화면에서 한 장을 골랐다 — 고르기 전으로 되돌릴 수 있어야 한다
        assertTrue(
            canResetProfileImage(
                usingDefaultImage = true,
                imageIntent = ImageIntent.Pick("profile/presets/03.png"),
            ),
        )
    }

    @Test
    fun `기본 이미지 판정은 파일 이름으로 하고 쿼리스트링은 무시한다`() {
        assertTrue(isDefaultProfileImage("https://cdn.moyeota.co.kr/profile/presets/default.png"))
        // 캐시 버스터가 붙는 날 판정이 조용히 뒤집히면 안 된다
        assertTrue(isDefaultProfileImage("https://cdn.moyeota.co.kr/profile/presets/default.png?v=2"))
        assertFalse(isDefaultProfileImage("https://cdn.moyeota.co.kr/profile/presets/01.png"))
        // null 은 조회 실패거나 구버전 서버다 — 「되돌리기」를 권할 근거가 없으므로 기본으로 본다
        assertTrue(isDefaultProfileImage(null))
    }
}
