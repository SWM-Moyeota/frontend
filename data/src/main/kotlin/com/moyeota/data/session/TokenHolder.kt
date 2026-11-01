package com.moyeota.data.session

/**
 * OkHttp 인터셉터/Authenticator 가 보는 동기 토큰 창구.
 *
 * 저장소(DataStore)는 suspend API 인데 [okhttp3.Interceptor] 와 [okhttp3.Authenticator] 는
 * non-suspend 라 그 간극을 여기서 메운다. 모든 메서드는 **OkHttp 의 I/O 스레드에서만** 호출된다는
 * 전제라 잠깐의 블로킹이 허용된다 — 메인 스레드에서 부르면 안 된다.
 */
interface TokenHolder {
    /** 미로그인이면 null. 앱 시작 직후라면 디스크 복원이 끝날 때까지 기다린 뒤 답한다. */
    fun currentAccessToken(): String?

    fun currentRefreshToken(): String?

    /** 재발급 성공 — 회전된 리프레시까지 함께 갱신한다. */
    fun onTokensRefreshed(accessToken: String, refreshToken: String)

    /**
     * 재발급 **거절**(서버가 "이 리프레시는 못 쓴다"고 답했다) 또는 리프레시 부재 —
     * 세션을 비우고 로그아웃 상태로 전환한다.
     *
     * **네트워크·타임아웃·5xx 로 답을 못 받은 경우는 여기 오지 않는다.** 그건 세션을 비울 근거가
     * 못 되기 때문이다([com.moyeota.data.remote.auth.RefreshResult] KDoc 에 사정을 적었다).
     */
    fun onSessionExpired()
}
