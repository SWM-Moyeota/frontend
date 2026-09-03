package com.moyeota.data.session

/** 디스크에 남기는 세션 한 벌. 셋 중 하나라도 없으면 세션이 성립하지 않으므로 통째로 다룬다. */
data class StoredSession(
    val userUuid: String,
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 세션 영속화 경계. 구현체 교체(암호화 저장소 도입 등)와 테스트용 가짜를 위해 인터페이스로 둔다.
 */
interface TokenStorage {
    /** 저장된 세션이 없거나 일부만 남아 깨졌으면 null. */
    suspend fun load(): StoredSession?

    suspend fun save(session: StoredSession)

    suspend fun clear()
}
