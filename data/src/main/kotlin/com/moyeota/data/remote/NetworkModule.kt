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

    /**
     * SSE 읽기 타임아웃(초). 서버 heartbeat 30초의 1.5배 — 끊김 감지 수단이 이것뿐이라 유한값이어야 한다.
     * 자세한 근거는 [create] 안 `streamClient` 주석 참고.
     */
    internal const val SSE_READ_TIMEOUT_SECONDS = 45L

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
            // 배포 서버(CloudFront 뒤 오리진)는 콜드 상태에서 첫 응답이 7~8초까지 걸려 기본 10초가 빠듯하다
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .apply {
                if (debugLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                    )
                }
            }
            .build()

        val publicRetrofit = retrofit(baseUrl, baseClient)
        val authApi = publicRetrofit.create(AuthApi::class.java)
        // 앱 설정은 permitAll — 토큰을 붙이지 않는다. 만료 토큰이 붙으면 401 재발급이 끼어들어 앱 시작이 늦어진다.
        val configApi = publicRetrofit.create(ConfigApi::class.java)

        val apiClient = baseClient.newBuilder()
            .addInterceptor(AuthHeaderInterceptor(tokenHolder))
            .authenticator(TokenAuthenticator(tokenHolder, RetrofitTokenRefresher(authApi)))
            .build()
        val retrofit = retrofit(baseUrl, apiClient)

        // SSE(매칭방 변화 신호) 전용 — 보통 API 와 같은 인증(Bearer + 401 재발급)을 쓰되 readTimeout 만 늘린다.
        //
        // **0(무제한)이 아니라 유한값인 게 핵심이다.** SSE 에는 OkHttp 의 pingInterval 이 듣지 않는다
        // (ping 은 WebSocket 프레임이다) — 즉 소켓이 조용히 죽어도(NAT 만료·셀↔와이파이 핸드오프)
        // OkHttp 가 알아챌 수단이 readTimeout 뿐이다. 무제한으로 두면 onFailure 가 영영 불리지 않아
        // 앱이 "SSE 살아있음"으로 굳고, 폴링을 끈 대기 화면이 영구히 멈춘다.
        //
        // 값은 서버 heartbeat 주기에서 나온다: 서버가 **30초**마다 `:ping` 주석을 보내므로(Backend #218,
        // 이전 5초) 연결이 살아 있으면 읽기가 30초 안에 반드시 깨어난다. 45초는 그 1.5배 —
        // 한 번 흘린 ping 은 견디고, 두 번 연달아 없으면 끊긴 것으로 보고 재연결에 들어간다.
        // 30초보다 짧게 두면 멀쩡한 연결을 매번 끊고, 훨씬 길게 두면 죽은 연결을 그만큼 오래 붙들고 있는다.
        val streamClient = apiClient.newBuilder()
            .readTimeout(SSE_READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // 웹소켓 전용 클라이언트. 인증 인터셉터를 붙이지 않는다 — STOMP 인증은 CONNECT **프레임** 헤더이고,
        // 401 재발급 Authenticator 가 업그레이드 요청에 끼어들 이유도 없다.
        // readTimeout 은 끈다(상시 연결이라 "응답이 늦다"는 개념이 없다). 대신 ping 으로 살아있음을 확인한다.
        val socketClient = baseClient.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        return Apis(
            socketClient = socketClient,
            streamClient = streamClient,
            auth = authApi,
            config = configApi,
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
        /** 채팅 실시간 수신(STOMP)이 쓰는 OkHttp. 인증 인터셉터가 붙지 않은 순정 클라이언트다 */
        val socketClient: OkHttpClient,
        /** SSE 수신이 쓰는 OkHttp. 인증은 붙어 있고 readTimeout 만 [SSE_READ_TIMEOUT_SECONDS] 로 늘렸다 */
        val streamClient: OkHttpClient,
        val auth: AuthApi,
        /** 서버 운영 설정(택시 모드 등). 토큰 없는 클라이언트 */
        val config: ConfigApi,
        val matching: MatchingApi,
        val place: PlaceApi,
        val chat: ChatApi,
        val dispatch: DispatchApi,
        val report: ReportApi,
        val user: UserApi,
    )
}
