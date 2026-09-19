package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AppConfigResponse
import retrofit2.http.GET

/**
 * 서버 운영 설정. `AppConfigController` — permitAll 이라 **토큰 없는 클라이언트**로 부른다
 * (인증 클라이언트로 불러도 되지만, 만료 토큰이 붙으면 401 재발급이 끼어들어 앱 시작이 늦어진다).
 */
interface ConfigApi {
    @GET("api/v1/config")
    suspend fun getConfig(): AppConfigResponse
}
