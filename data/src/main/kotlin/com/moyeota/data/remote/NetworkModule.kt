package com.moyeota.data.remote

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
     * [debugLogging] 은 호출자가 주입한다. data 는 라이브러리 모듈이라 자체 BuildConfig.DEBUG 가
     * app 의 빌드 타입과 일치하지 않으므로 여기서 판단하지 않는다.
     * 켜지면 요청/응답 본문 전체가 logcat 에 남는다 — 채팅 메시지와 향후 인증 토큰이 포함되므로
     * 릴리즈에서는 반드시 꺼져야 한다.
     */
    fun create(baseUrl: String, debugLogging: Boolean): Apis {
        val retrofit = retrofit(baseUrl, debugLogging)
        return Apis(
            matching = retrofit.create(MatchingApi::class.java),
            place = retrofit.create(PlaceApi::class.java),
            chat = retrofit.create(ChatApi::class.java),
        )
    }

    private fun retrofit(baseUrl: String, debugLogging: Boolean): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
        if (debugLogging) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
            )
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    data class Apis(
        val matching: MatchingApi,
        val place: PlaceApi,
        val chat: ChatApi,
    )
}
