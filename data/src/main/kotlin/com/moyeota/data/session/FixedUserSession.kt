package com.moyeota.data.session

import com.moyeota.domain.session.UserSession

// 인증 연동 전 임시 세션. 백엔드도 memberId 를 JWT 에서 뽑는 작업이 TODO 로 남아 있어
// 양쪽 모두 고정 사용자(1L)를 전제로 동작시킨다. 인증이 붙으면 이 클래스만 교체하면 된다.
class FixedUserSession(override val currentUserId: Long = DEFAULT_USER_ID) : UserSession {
    companion object {
        const val DEFAULT_USER_ID = 1L
    }
}
