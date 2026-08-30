package com.moyeota.domain.session

// 앱에 아직 인증/로그인이 없어 서버가 요구하는 memberId·userId·X-User-Id 를 얻을 곳이 없다.
// 화면이 "현재 사용자"를 참조하는 지점을 이 인터페이스 하나로 모아두고,
// 실제 인증이 붙으면 구현체만 교체한다. (백엔드도 동일하게 TODO: JWT 에서 추출)
interface UserSession {
    val currentUserId: Long
}
