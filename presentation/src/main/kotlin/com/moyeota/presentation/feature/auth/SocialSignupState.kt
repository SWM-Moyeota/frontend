package com.moyeota.presentation.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.moyeota.domain.model.GoogleLoginResult

/** SavedStateHandle / Bundle에 넣지 않는다. 회전은 유지하고 프로세스 종료 시 폐기한다. */
class SocialSignupState : ViewModel() {
    var ticket: GoogleLoginResult.SignupRequired? by mutableStateOf(null)
        private set
    var draft: SignupDraft by mutableStateOf(SignupDraft())
    fun begin(value: GoogleLoginResult.SignupRequired) {
        ticket = value
        draft = SignupDraft(name = value.name.orEmpty())
    }
    fun clear() { ticket = null; draft = SignupDraft() }
}
