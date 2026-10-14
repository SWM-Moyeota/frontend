package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthPolicy
import com.moyeota.presentation.feature.auth.authErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 37 회원 탈퇴의 순수 로직 — 제출 가능 판정, 실패를 어디에 그릴지, 경고 문구가 서버 동작과 맞는지.
 *
 * 문구를 테스트하는 게 과해 보일 수 있지만 **여기서는 문구가 계약이다.** 지워지지 않는 것을
 * 지워진다고 적으면 Play 심사와 개인정보처리방침 양쪽에 걸리고, 되돌릴 수 없는 동작이라
 * 사용자가 그 문구를 믿고 누른 뒤에는 고칠 기회가 없다.
 */
class AccountDeleteRulesTest {

    // ─────────────────────────── 제출 가능 판정 ───────────────────────────

    @Test
    fun `비밀번호를 쓰면 탈퇴를 누를 수 있다`() {
        assertTrue(canSubmitAccountDeletion("Passw0rd!"))
    }

    @Test
    fun `빈 비밀번호로는 누를 수 없다 - 서버 NotBlank 에 걸릴 요청이다`() {
        assertFalse(canSubmitAccountDeletion(""))
    }

    /**
     * 공백만 넣으면 서버가 400 `INVALID_REQUEST` 를 주는데, 그 문구("입력한 정보를 다시 확인해
     * 주세요")로는 사용자가 무엇을 고쳐야 할지 모른다. 보내기 전에 막는다.
     */
    @Test
    fun `공백만 넣어도 누를 수 없다`() {
        assertFalse(canSubmitAccountDeletion("   "))
        assertFalse(canSubmitAccountDeletion("\t"))
    }

    /** 중복 제출 차단(공통 규칙). 탈퇴는 멱등이 아니다 — 두 번째는 404·401 이 된다. */
    @Test
    fun `보내는 중에는 다시 누를 수 없다`() {
        assertFalse(canSubmitAccountDeletion("Passw0rd!", submitting = true))
    }

    /**
     * **앱이 비밀번호 규칙을 다시 검사하지 않는다.** 서버는 `@NotBlank` 만 보고 해시를 비교하므로
     * (`UserDeletionRequest`), `AuthPolicy.isValidPassword` 로 좁히면 규칙이 바뀌기 전에 가입한
     * 계정이 **자기 비밀번호를 정확히 넣고도 버튼을 못 누르는** 상태가 된다.
     */
    @Test
    fun `가입 규칙에 안 맞는 비밀번호도 보낼 수 있다 - 판정은 서버가 한다`() {
        val tooShort = "a1!"
        assertFalse("전제가 깨졌다 — 이 값이 가입 규칙을 통과한다", AuthPolicy.isValidPassword(tooShort))

        assertTrue(canSubmitAccountDeletion(tooShort))
    }

    // ─────────────────────────── 실패를 어디에 그리는가 ───────────────────────────

    /**
     * 비밀번호 오타만 입력란 오류다 — 이 화면에서 고칠 수 있는 유일한 실패다.
     * 나머지를 입력란으로 보내면 고칠 게 없는데 입력란이 빨개져 사용자가 비밀번호를 계속 다시 친다.
     */
    @Test
    fun `비밀번호 불일치만 입력란으로 간다`() {
        assertEquals(
            AccountDeleteErrorSlot.PASSWORD_FIELD,
            accountDeleteErrorSlot(AuthError.PASSWORD_MISMATCH),
        )
    }

    @Test
    fun `이 화면에서 못 고치는 실패는 전부 배너로 간다`() {
        listOf(
            // 합승 화면에서 나와야 풀린다
            AuthError.ONGOING_PARTY_EXISTS,
            // 기다려야 한다
            AuthError.NETWORK,
            // 로그인으로 가야 한다
            AuthError.SESSION_EXPIRED,
            AuthError.UNKNOWN,
            AuthError.INVALID_REQUEST,
        ).forEach { error ->
            assertEquals(error.name, AccountDeleteErrorSlot.BANNER, accountDeleteErrorSlot(error))
        }
    }

    // ─────────────────────────── 문구 ───────────────────────────

    /**
     * 탈퇴 계열 셋이 서로 다른 말을 해야 한다. 특히 둘은 **같은 409** 라 한 문구로 뭉치기 쉽다:
     * `USER109`(재가입 제한)와 `USER110`(진행 중 합승)은 할 일이 전혀 다르다.
     */
    @Test
    fun `탈퇴 계열 세 에러가 서로 다른 말을 한다`() {
        val messages = listOf(
            AuthError.ONGOING_PARTY_EXISTS,
            AuthError.PASSWORD_MISMATCH,
            AuthError.REJOIN_RESTRICTED,
        ).map { authErrorMessage(it) }

        assertEquals("문구가 겹친다: $messages", messages.size, messages.toSet().size)
        messages.forEach { assertTrue("빈 문구가 있다", it.isNotBlank()) }
        // 409 폴백 문구가 새어 나오면 탈퇴 화면에 아이디 이야기가 뜬다 — 그 자체가 결함 신호다.
        messages.forEach {
            assertFalse("409 폴백 문구가 섞였다: $it", it.contains("아이디"))
        }
    }

    /** 진행 중 합승 안내는 **무엇을 먼저 해야 하는지**를 말해야 한다. "잠시 후 다시"로는 영영 안 풀린다. */
    @Test
    fun `진행 중 합승 안내는 합승에서 나가라고 말한다`() {
        val message = authErrorMessage(AuthError.ONGOING_PARTY_EXISTS)

        assertTrue(message, message.contains("합승"))
        assertTrue(message, message.contains("나간"))
    }

    /** 재가입 제한 기간은 서버 `UserDeletions.RETENTION` 과 같은 수여야 한다. */
    @Test
    fun `재가입 제한 안내의 기간은 서버 상수를 따른다`() {
        assertEquals(30, AuthPolicy.REJOIN_RESTRICTED_DAYS)
        assertTrue(
            authErrorMessage(AuthError.REJOIN_RESTRICTED)
                .contains("${AuthPolicy.REJOIN_RESTRICTED_DAYS}일"),
        )
    }

    // ─────────────────────────── 경고 목록 ───────────────────────────

    /**
     * 네 줄 전부 서버가 실제로 하는 일이어야 한다. 근거는 `UserDeletionService.delete` 와
     * `ChatRoomUserDeletionListener`·`FavoritePlaceUserDeletionListener` 다.
     */
    @Test
    fun `경고 목록은 서버가 실제로 하는 네 가지를 말한다`() {
        val all = AccountDeletionWarnings.joinToString(" ")

        assertEquals(4, AccountDeletionWarnings.size)
        // localUsers.deleteByUserId — 아이디·비밀번호가 삭제된다
        assertTrue(all, all.contains("로그인"))
        // ChatRoomUserDeletionListener + User.DELETED_DISPLAY_NAME
        assertTrue(all, all.contains("채팅방"))
        assertTrue(all, all.contains("탈퇴한 사용자"))
        // FavoritePlaceUserDeletionListener
        assertTrue(all, all.contains("자주 가는 곳"))
        // userDeletions.record → SmsVerificationService.send 의 USER109
        assertTrue(all, all.contains("${AuthPolicy.REJOIN_RESTRICTED_DAYS}일"))
    }

    /**
     * **서버가 하지 않는 일을 약속하지 않는다.** 지난 합승 기록은 지워지지 않는다 —
     * 끝난 방의 멤버로는 남고 이름만 「탈퇴한 사용자」로 가려진다. "모든 기록이 사라져요" 같은
     * 문구를 넣으면 지킬 수 없는 약속이 된다(docs/BACKEND-GAP-CLEANUP.md 의 원칙).
     */
    @Test
    fun `경고 목록은 지워지지 않는 것을 지워진다고 말하지 않는다`() {
        val all = AccountDeletionWarnings.joinToString(" ")

        listOf("모든 기록", "탑승 기록", "전부 삭제", "모두 삭제").forEach { forbidden ->
            assertFalse("서버가 하지 않는 일을 약속한다: $forbidden", all.contains(forbidden))
        }
    }

    /**
     * 진행 중 합승 제약은 경고가 아니라 **실패 사유**다. 앱은 "지금 내가 타고 있는 방"을 서버에
     * 물을 수 없어(그 엔드포인트가 없다) 미리 알 수 없고, 눌러 본 뒤 409 로 알게 된다.
     * 경고 목록에 섞으면 합승이 없는 사용자에게도 못 할 것처럼 읽힌다.
     */
    @Test
    fun `진행 중 합승은 경고가 아니라 실패 사유로 다룬다`() {
        val all = AccountDeletionWarnings.joinToString(" ")

        assertFalse("경고 목록이 실패 사유를 미리 말한다: $all", all.contains("진행 중"))
    }
}
