package com.moyeota.domain.repository

import com.moyeota.domain.model.AppConfig

interface AppConfigRepository {
    /**
     * GET /api/v1/config — 토큰 없이 호출 가능(permitAll). 앱 시작 시 한 번 읽는다.
     * 실패는 예외로 올라온다 — 호출부가 [AppConfig.Default] 로 떨어뜨릴지 정한다.
     */
    suspend fun getConfig(): AppConfig
}
