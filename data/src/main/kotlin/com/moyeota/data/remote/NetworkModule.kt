package com.moyeota.data.remote

import com.moyeota.data.remote.auth.AuthHeaderInterceptor
import com.moyeota.data.remote.auth.RetrofitTokenRefresher
import com.moyeota.data.remote.auth.TokenAuthenticator
import com.moyeota.data.session.TokenHolder
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkModule {

    // ignoreUnknownKeys: 서버가 필드를 추가해도 앱이 죽지 않게 한다.
    // 기본값이 있는 DTO 필드는 explicitNulls 와 무관하게 누락 시 기본값이 쓰인다.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * API 별로 Retrofit 을 새로 만들지 않도록 baseUrl 단위로 한 번만 구성한다.
     *
     * **클라이언트는 둘이다.**
     * - `authClient`: 인증 엔드포인트(가입/로그인/재발급/로그아웃) 전용. Bearer 도 401 재발급도 붙지 않는다.
     *   재발급 요청이 401 을 맞았을 때 다시 재발급을 트리거하는 무한 루프를 구조적으로 차단한다.
     * - `apiClient`: 나머지 도메인 API. Bearer 부착 + 401 시 재발급 후 1회 재시도.
     *
     * 두 클라이언트는 `newBuilder()` 로 파생시켜 커넥션 풀과 디스패처를 공유한다.
     *
     * [debugLogging] 은 호출자가 주입한다. data 는 라이브러리 모듈이라 자체 BuildConfig.DEBUG 가
     * app 의 빌드 타입과 일치하지 않으므로 여기서 판단하지 않는다.
     * 켜지면 요청/응답 본문 전체가 logcat 에 남는다 — 채팅 메시지와 **인증 토큰**이 포함되므로
     * 릴리즈에서는 반드시 꺼져야 한다.
     */
    fun create(baseUrl: String, debugLogging: Boolean, tokenHolder: TokenHolder): Apis {
        val baseClient = OkHttpClient.Builder()
            .apply {
                if (debugLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                    )
                }
            }
            .build()

        val authApi = retrofit(baseUrl, baseClient).create(AuthApi::class.java)

        val apiClient = baseClient.newBuilder()
            .addInterceptor(AuthHeaderInterceptor(tokenHolder))
            .authenticator(TokenAuthenticator(tokenHolder, RetrofitTokenRefresher(authApi)))
            .build()
        val retrofit = retrofit(baseUrl, apiClient)

        return Apis(
            auth = authApi,
            matching = retrofit.create(MatchingApi::class.java),
            place = retrofit.create(PlaceApi::class.java),
            chat = retrofit.create(ChatApi::class.java),
            dispatch = retrofit.create(DispatchApi::class.java),
            // 신고 두 엔드포인트 모두 @CurrentUser — 반드시 Bearer 가 붙는 apiClient 로 만든다.
            report = retrofit.create(ReportApi::class.java),
            // 인증 API 와 이름이 비슷하지만 반드시 이쪽(apiClient) 이다 — 내 정보 조회는 토큰 필수다.
            user = retrofit.create(UserApi::class.java),
        )
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    data class Apis(
        val auth: AuthApi,
        val matching: MatchingApi,
        val place: PlaceApi,
        val chat: ChatApi,
        val dispatch: DispatchApi,
        val report: ReportApi,
        val user: UserApi,
    )
}
