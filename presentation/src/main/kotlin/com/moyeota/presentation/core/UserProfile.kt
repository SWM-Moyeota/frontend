package com.moyeota.presentation.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 로그인한 사용자의 표시용 이름 상태.
 *
 * [Loading] 과 `Resolved(null)` 을 굳이 나누는 이유: 조회가 아직 안 끝난 것과
 * "서버에 이름이 없다"는 건 화면이 달리 그려야 한다(전자는 빈 자리/스켈레톤, 후자는 폴백 문구).
 * 한 상태로 뭉치면 로딩 중에 "이름 미설정"이 스쳐 보인다.
 */
sealed interface UserNameState {
    /** 미로그인이거나 아직 조회 중 — 이름 자리를 비워 둔다. */
    data object Loading : UserNameState

    /** 조회 완료. [name] 이 null 이면 서버에 이름이 없거나 조회에 실패한 것 — 화면이 폴백한다. */
    data class Resolved(val name: String?) : UserNameState
}

/**
 * 세션당 한 번만 이름을 받아 오는 공용 홀더.
 *
 * NavHost 바깥(액티비티 ViewModelStore)에 두고 홈·마이페이지가 같은 인스턴스를 본다
 * — 화면마다 부르면 탭을 오갈 때마다 같은 응답을 반복해서 받게 된다.
 * `AuthRepository.getMyProfile()` 이 캐시하지 않는다고 명시돼 있으므로(31 보고서 §3)
 * 캐시는 이 계층의 책임이다.
 *
 * 로그인 사용자가 바뀌면(= uuid 가 바뀌면) 다시 받는다. 로그아웃하면 [UserNameState.Loading] 으로
 * 되돌려, 다음 사용자의 이름이 뜨기 전에 이전 사용자의 이름이 남지 않게 한다.
 *
 * **실패해도 조용히 넘어간다.** 이름은 인사말과 프로필 카드의 부가 정보라,
 * 이것 때문에 화면 전체가 에러로 막히면 손해가 더 크다.
 */
class UserProfileViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _userName = MutableStateFlow<UserNameState>(UserNameState.Loading)
    val userName: StateFlow<UserNameState> = _userName.asStateFlow()

    init {
        viewModelScope.launch {
            repository.authState
                .map { (it as? AuthState.Authenticated)?.userUuid }
                .distinctUntilChanged()
                .collect { uuid ->
                    if (uuid == null) {
                        // 미로그인/복원 전 — 조회하지 않는다(토큰이 없어 401 이 확정이다).
                        _userName.value = UserNameState.Loading
                    } else {
                        _userName.value = UserNameState.Loading
                        _userName.value = UserNameState.Resolved(
                            runCatching { repository.getMyProfile().name }.getOrNull(),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: AuthRepository) = viewModelFactory {
            initializer { UserProfileViewModel(repository) }
        }
    }
}
