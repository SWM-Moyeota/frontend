package com.moyeota.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * DataStore Preferences 기반 "진행 중인 방" 저장소.
 * 세션 저장소([com.moyeota.data.session.DataStoreTokenStorage])와 같은 이유로 DataStore 를 쓴다
 * (SharedPreferences 의 메인 스레드 디스크 I/O 회피, Jetpack Security deprecate).
 *
 * 세션과 **파일을 나눈** 이유: 로그아웃은 세션 파일을 통째로 지우는데(`prefs.clear()`),
 * 같은 파일에 방 id 를 두면 그 순간 함께 날아간다. 반대로 방 기억이 세션 수명보다 오래 살아야 할
 * 일도 없지만, 계정 가드는 uuid 비교로 하는 편이 파일 결합보다 명시적이다.
 */
class DataStoreActivePartyStorage(context: Context) : ActivePartyStorage {

    // applicationContext 로 고정 — Activity 를 잡고 있으면 누수된다.
    private val dataStore: DataStore<Preferences> = context.applicationContext.activePartyDataStore

    override suspend fun load(): RememberedParty? {
        val prefs = dataStore.data.first()
        val partyId = prefs[KEY_PARTY_ID]
        val ownerUuid = prefs[KEY_OWNER_UUID]
        // 주인을 모르는 기억은 계정 가드를 걸 수 없으니 없는 것으로 친다.
        if (partyId.isNullOrBlank() || ownerUuid.isNullOrBlank()) return null
        return RememberedParty(partyId, ownerUuid)
    }

    override suspend fun save(party: RememberedParty) {
        dataStore.edit { prefs ->
            prefs[KEY_PARTY_ID] = party.partyId
            prefs[KEY_OWNER_UUID] = party.ownerUuid
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_PARTY_ID = stringPreferencesKey("active_party_id")
        val KEY_OWNER_UUID = stringPreferencesKey("active_party_owner_uuid")
    }
}

// 프로세스당 하나만 존재해야 한다(중복 생성 시 DataStore 가 IllegalStateException 을 던진다).
private val Context.activePartyDataStore: DataStore<Preferences> by preferencesDataStore(name = "moyeota_active_party")
