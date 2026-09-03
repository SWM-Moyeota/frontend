package com.moyeota.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * DataStore Preferences 기반 토큰 저장소.
 *
 * **EncryptedSharedPreferences 대신 DataStore 를 고른 이유**
 * - androidx.security:security-crypto(Jetpack Security)는 구글이 deprecate 했고 후속 stable 이 없다.
 *   지금 붙이면 곧 마이그레이션 부채가 된다.
 * - SharedPreferences 의 동기 API 는 메인 스레드 디스크 I/O 를 유발한다. DataStore 는 suspend/Flow 라
 *   앱 시작 시 복원을 코루틴으로 처리할 수 있다.
 *
 * **한계(의도적 트레이드오프)**: 저장 내용은 암호화되지 않는다. 방어선은 앱 전용 내부 저장소
 * (다른 앱 접근 불가) + 기기 전체 암호화이며, 루팅/디버거블 단말에서는 토큰이 읽힐 수 있다.
 * 완화책으로 액세스 토큰 수명이 짧고(30분) 리프레시는 회전되며 로그아웃 시 서버에서 무효화된다.
 */
class DataStoreTokenStorage(context: Context) : TokenStorage {

    // applicationContext 로 고정 — Activity 를 잡고 있으면 누수된다.
    private val dataStore: DataStore<Preferences> = context.applicationContext.sessionDataStore

    override suspend fun load(): StoredSession? {
        val prefs = dataStore.data.first()
        val userUuid = prefs[KEY_USER_UUID]
        val accessToken = prefs[KEY_ACCESS_TOKEN]
        val refreshToken = prefs[KEY_REFRESH_TOKEN]
        // 부분 저장(앱 강제 종료 등)은 세션으로 인정하지 않는다 — 반쪽 세션은 401 루프의 원인이 된다.
        if (userUuid.isNullOrBlank() || accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            return null
        }
        return StoredSession(userUuid, accessToken, refreshToken)
    }

    override suspend fun save(session: StoredSession) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_UUID] = session.userUuid
            prefs[KEY_ACCESS_TOKEN] = session.accessToken
            prefs[KEY_REFRESH_TOKEN] = session.refreshToken
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USER_UUID = stringPreferencesKey("user_uuid")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}

// 프로세스당 하나만 존재해야 한다(중복 생성 시 DataStore 가 IllegalStateException 을 던진다).
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "moyeota_session")
