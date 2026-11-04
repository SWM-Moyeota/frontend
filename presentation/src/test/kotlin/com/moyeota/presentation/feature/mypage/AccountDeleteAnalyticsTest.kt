package com.moyeota.presentation.feature.mypage

import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.AuthError
import com.moyeota.domain.model.AuthException
import com.moyeota.presentation.analytics.AnalyticsRecorderRule
import com.moyeota.presentation.analytics.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 마이 · 회원 탈퇴 — `delete_account` 는 서버가 204 를 돌려준 뒤에만 1번(iOS 와 같이 onDeleted 직후). */
class AccountDeleteAnalyticsTest {

    @get:Rule val rule = AnalyticsRecorderRule()

    @Test
    fun `성공하면 1번`() {
        val vm = AccountDeleteViewModel(FakeAuthRepository())
        vm.onPasswordChange("pw123456!")
        var deleted = false

        vm.delete(onDeleted = { deleted = true })

        assertEquals(true, deleted)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.AccountDeleted), rule.recorded)
    }

    @Test
    fun `실패하면 보내지 않는다`() {
        val repo = FakeAuthRepository().apply { onDeleteAccount = { throw AuthException(AuthError.PASSWORD_MISMATCH) } }
        val vm = AccountDeleteViewModel(repo)
        vm.onPasswordChange("wrong")

        vm.delete()

        assertEquals(emptyList<AnalyticsEvent>(), rule.recorded)
    }
}
