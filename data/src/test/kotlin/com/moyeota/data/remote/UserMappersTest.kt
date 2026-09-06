package com.moyeota.data.remote

import com.moyeota.data.remote.dto.UserProfileResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserMappersTest {

    // NetworkModule 과 같은 설정. 서버가 필드를 추가해도 파싱이 깨지지 않아야 한다.
    private val json = Json { ignoreUnknownKeys = true }

    /** 실서버(localhost:8080, testuser1) 응답 원문 그대로. */
    @Test
    fun `실측 응답을 그대로 파싱한다`() {
        val body = """{"uuid":"01a06145-3caf-7614-a3bd-cee6e25316b1","name":"김성윤"}"""

        val dto = json.decodeFromString<UserProfileResponse>(body)

        assertEquals("01a06145-3caf-7614-a3bd-cee6e25316b1", dto.uuid)
        assertEquals("김성윤", dto.name)
        assertEquals("김성윤", dto.toDomain().name)
    }

    /**
     * 닉네임도 프로필 실명도 없으면 서버가 `orElse(null)` 로 null 을 내려보낸다.
     * Jackson 이 null 을 지우지 않으므로 **키는 남고 값만 null** 이다 — 논-널 선언이면 여기서 터진다.
     */
    @Test
    fun `이름이 null 이어도 파싱된다`() {
        val dto = json.decodeFromString<UserProfileResponse>("""{"uuid":"u-1","name":null}""")

        assertNull(dto.name)
        assertEquals("u-1", dto.toDomain().uuid)
        assertNull(dto.toDomain().name)
    }

    // 서버가 나중에 null 필드를 빼도록 바뀌어도 살아남아야 한다.
    @Test
    fun `이름 키가 아예 없어도 파싱된다`() {
        val dto = json.decodeFromString<UserProfileResponse>("""{"uuid":"u-1"}""")

        assertNull(dto.toDomain().name)
    }

    /**
     * 빈 문자열도 도메인에서는 null 이다 — 화면이 "없음"을 두 가지 방식으로 판정하게 두지 않는다.
     */
    @Test
    fun `공백뿐인 이름은 없음으로 정규화한다`() {
        assertNull(UserProfileResponse(uuid = "u-1", name = "").toDomain().name)
        assertNull(UserProfileResponse(uuid = "u-1", name = "   ").toDomain().name)
    }

    @Test
    fun `모르는 필드가 늘어나도 파싱이 깨지지 않는다`() {
        val body = """{"uuid":"u-1","name":"김성윤","imageUrl":"https://x","badgeId":3}"""

        assertEquals("김성윤", json.decodeFromString<UserProfileResponse>(body).toDomain().name)
    }
}
