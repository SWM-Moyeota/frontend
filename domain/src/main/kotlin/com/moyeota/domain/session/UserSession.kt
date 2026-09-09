package com.moyeota.domain.session

import com.moyeota.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * 화면이 "현재 사용자"를 참조하는 단일 출처.
 *
 * **앱은 서버 내부 사용자 PK(Long)를 갖고 있지 않다.** 모든 API 가 `@CurrentUser` 로 전환돼
 * 주체를 Bearer 토큰이 정하기 때문이다(채팅이 마지막이었다). 앱이 아는 식별자는
 * [currentUserUuid] 하나뿐이고, "이게 나인가" 판정은 서버가 내려주는 publicId 와의 비교로 한다.
 *
 * 어떤 응답에도 없는 내부 id 가 필요한 자리는 딱 하나 남아 있다 — 채팅 메시지의 `userId`.
 * 그건 [com.moyeota.data.repository.RemoteChatRepository] 가 내가 보낸 메시지의 응답에서
 * 학습해 세션 동안 들고 있으며, 여기(세션)에는 두지 않는다.
 */
interface UserSession {
    /** 로그인 상태 스트림. [AuthRepository.authState][com.moyeota.domain.repository.AuthRepository.authState] 와 같은 값이다. */
    val authState: StateFlow<AuthState>

    /** 로그인한 사용자의 서버 UUID. 미로그인/복원 전이면 null. */
    val currentUserUuid: String?
        get() = (authState.value as? AuthState.Authenticated)?.userUuid

    /** 복원이 끝나고 인증된 상태인지. [AuthState.Unknown] 이면 false. */
    val isLoggedIn: Boolean
        get() = authState.value is AuthState.Authenticated
}
