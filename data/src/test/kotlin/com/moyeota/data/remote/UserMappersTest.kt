package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ProfileImageResponse
import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.domain.model.ProfileImage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMappersTest {

    // NetworkModule 과 같은 설정. 서버가 필드를 추가해도 파싱이 깨지지 않아야 한다.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 백엔드 `UserResponse(UUID uuid, String name, String imageUrl)` 모양이고 **실측이다** —
     * 2026-11-01 로컬 :8080 에서 `POST /api/v1/auth/register` 가
     * `{"uuid":"01a194d1-…","name":"큐에이일","imageUrl":"https://cdn.moyeota.co.kr/profile/presets/default.png"}`
     * 로 답했고, 같은 모양을 `GET /api/v1/local/users/info` 도 200 으로 돌려줬다.
     * 필드가 둘이던 옛 본문을 그대로 두면 [UserProfileResponse.imageUrl] 이 한 번도
     * 파싱되지 않은 채 통과한다.
     */
    @Test
    fun `백엔드 UserResponse 모양을 그대로 파싱한다`() {
        val body = """
            {"uuid":"01a06145-3caf-7614-a3bd-cee6e25316b1","name":"김성윤",
             "imageUrl":"https://cdn.moyeota.co.kr/profile/presets/01.png"}
        """.trimIndent()

        val dto = json.decodeFromString<UserProfileResponse>(body)

        assertEquals("01a06145-3caf-7614-a3bd-cee6e25316b1", dto.uuid)
        assertEquals("김성윤", dto.name)
        assertEquals("https://cdn.moyeota.co.kr/profile/presets/01.png", dto.imageUrl)
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

    /**
     * 프로필 이미지 주소가 도메인까지 **그대로** 흘러야 아바타가 사진으로 그려진다.
     * 매퍼가 이 필드를 떨어뜨리면 응답은 멀쩡한데 화면만 이니셜로 떨어져, 원인이 서버·CDN·Coil
     * 어디에도 없는 상태가 된다.
     */
    @Test
    fun `imageUrl 이 도메인까지 흐른다`() {
        val dto = UserProfileResponse(
            uuid = "u-1",
            name = "김성윤",
            imageUrl = "https://cdn.moyeota.co.kr/profile/presets/07.png",
        )

        assertEquals("https://cdn.moyeota.co.kr/profile/presets/07.png", dto.toDomain().imageUrl)
    }

    /**
     * 이름과 **같은 규칙**이다 — 두 필드가 서로 다른 "없음"을 가지면 화면이 폴백을 두 갈래로 만든다.
     * 빈 문자열을 그대로 넘기면 Coil 이 그 주소를 실제로 요청해 실패한 뒤에야 이니셜로 떨어진다.
     */
    @Test
    fun `공백뿐인 imageUrl 은 없음으로 정규화한다`() {
        assertNull(UserProfileResponse(uuid = "u-1", imageUrl = "").toDomain().imageUrl)
        assertNull(UserProfileResponse(uuid = "u-1", imageUrl = "   ").toDomain().imageUrl)
    }

    /**
     * 구버전 서버(2026-10-15 배포 이전)에는 이 키가 없다. 논-널로 선언하면 그 서버를 붙인 순간
     * 내 정보 조회가 통째로 터져 로그인 직후 화면이 열리지 않는다.
     */
    @Test
    fun `imageUrl 키가 아예 없어도 파싱된다`() {
        val dto = json.decodeFromString<UserProfileResponse>("""{"uuid":"u-1","name":"김성윤"}""")

        assertNull(dto.toDomain().imageUrl)
    }

    @Test
    fun `모르는 필드가 늘어나도 파싱이 깨지지 않는다`() {
        // DTO 가 가진 필드를 여기 쓰면 "모르는 필드"가 아니게 되어 아무것도 검사하지 않는 테스트가 된다.
        val body = """{"uuid":"u-1","name":"김성윤","badgeId":3,"mannerScore":4.5}"""

        assertEquals("김성윤", json.decodeFromString<UserProfileResponse>(body).toDomain().name)
    }

    /**
     * **소셜 회원 판별은 loginId 유무 하나다** — 서버가 따로 알려 주는 칸이 없다
     * (백엔드 `http/social-login.http` 5번: 「소셜 회원은 loginId 가 null」).
     * 탈퇴 화면이 이 값으로 「비밀번호 입력 + 본문 있는 탈퇴」와 「본문 없는 탈퇴」를 가른다.
     */
    @Test
    fun `loginId 가 없으면 소셜 회원이고 있으면 아이디 회원이다`() {
        val social = json.decodeFromString<UserProfileResponse>("""{"uuid":"u-1","name":"홍길동","loginId":null}""")
        val local = json.decodeFromString<UserProfileResponse>("""{"uuid":"u-2","name":"김성윤","loginId":"hong123"}""")

        assertTrue(social.toDomain().isSocialAccount)
        assertFalse(local.toDomain().isSocialAccount)
    }

    /**
     * 목록 본문은 **엔벨로프 없는 벗은 배열**이다. 그리고 [ProfileImage.key] 는 서버에 보내는 값,
     * [ProfileImage.url] 은 그리는 값이라 **두 필드가 뒤집히면** PATCH 가 400 `USER112` 로 거절되고
     * 격자에는 아무것도 안 그려진다 — 원인이 응답 파싱이라 추적이 어려워 여기서 따로 못 박는다.
     *
     * 값 형태는 백엔드 `ProfileImages` 설정 기준이다(실서버 미호출): key 는 제로패딩된
     * `profile/presets/NN.png`, url 은 그 key 를 CDN 주소에 붙인 값이다.
     */
    @Test
    fun `프로필 이미지 항목은 key 와 url 이 뒤집히지 않는다`() {
        val body = """
            [{"key":"profile/presets/01.png","url":"https://cdn.moyeota.co.kr/profile/presets/01.png"}]
        """.trimIndent()

        val images = json.decodeFromString<List<ProfileImageResponse>>(body).map { it.toDomain() }

        assertEquals(
            listOf(
                ProfileImage(key = "profile/presets/01.png", url = "https://cdn.moyeota.co.kr/profile/presets/01.png"),
            ),
            images,
        )
        // 같은 것을 다른 각도로 — 뒤집히면 key 에 주소가, url 에 S3 키가 들어온다.
        assertTrue("key 에 CDN 주소가 들어왔다 — 두 필드가 뒤집혔다", images.single().key.startsWith("profile/presets/"))
        assertTrue("url 에 S3 키가 들어왔다 — 두 필드가 뒤집혔다", images.single().url.startsWith("https://"))
    }

    /**
     * 설정이 비면 서버는 `[]` 를 준다. 이것도 정상 응답이라 파싱·매핑이 빈 목록으로 끝나야 한다
     * — 예외로 바꾸면 같은 화면의 닉네임 수정까지 막힌다.
     */
    @Test
    fun `빈 이미지 목록도 정상 파싱된다`() {
        val images = json.decodeFromString<List<ProfileImageResponse>>("[]").map { it.toDomain() }

        assertTrue(images.isEmpty())
    }
}
