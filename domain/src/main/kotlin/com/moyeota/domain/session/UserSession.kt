package com.moyeota.domain.session

import com.moyeota.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * 화면이 "현재 사용자"를 참조하는 단일 출처.
 *
 * 서버가 `@CurrentUser` 로 전환된 뒤(방 생성·합류·나가기·기사위치·즐겨찾기·신고) 그쪽 호출에는
 * memberId 를 넘기지 않는다 — 주체는 Bearer 토큰이 정한다.
 * 이제 [currentUserId] 가 실제로 서버로 나가는 곳은 **채팅 하나뿐**이다.
 *
 * 로그인 여부·사용자 식별은 [authState] / [currentUserUuid] 를 본다.
 */
interface UserSession {
    /**
     * 아직 토큰 기준으로 넘어가지 못한 채팅 API 용 고정 memberId
     * (서버가 여전히 `@RequestHeader("X-User-Id") Long userId` 를 받는다).
     * **로그인한 사용자와 무관하다** — 누가 로그인해도 채팅 서버에는 memberId=1 로 보인다.
     *
     * 매칭(방 생성·합류·나가기)은 이 값을 쓰지 않는다 — 전부 토큰 주체 기준이다.
     * 화면에서 "이 멤버가 나인가"를 이 값으로 비교하는 곳이 남아 있다면, 로그인 계정의 실제
     * 서버 id 가 1 이 아닌 순간 어긋난다 — 채팅이 `@CurrentUser` 로 넘어가 이 프로퍼티가
     * 사라지기 전까지 남는 제약이다.
     */
    val currentUserId: Long

    /** 로그인 상태 스트림. [AuthRepository.authState][com.moyeota.domain.repository.AuthRepository.authState] 와 같은 값이다. */
    val authState: StateFlow<AuthState>

    /** 로그인한 사용자의 서버 UUID. 미로그인/복원 전이면 null. */
    val currentUserUuid: String?
        get() = (authState.value as? AuthState.Authenticated)?.userUuid

    /** 복원이 끝나고 인증된 상태인지. [AuthState.Unknown] 이면 false. */
    val isLoggedIn: Boolean
        get() = authState.value is AuthState.Authenticated

    companion object {
        /** 채팅 헤더가 `@CurrentUser` 로 넘어가기 전까지 쓰는 고정 memberId. */
        const val FIXED_MEMBER_ID = 1L
    }
}
