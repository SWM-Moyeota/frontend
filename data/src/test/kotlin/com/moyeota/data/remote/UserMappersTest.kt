package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ProfileImageResponse
import com.moyeota.data.remote.dto.UserProfileResponse
import com.moyeota.domain.model.ProfileImage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

    /**
     * **제재 두 필드가 도메인까지 흐르는지.** 서버 `MyProfileResponse` 에는 2026-11-06 기준
     * `noShowCount`·`restrictedUntil` 이 있고, 앱 DTO 에 그 자리가 없던 동안 `ignoreUnknownKeys` 가
     * **조용히 버리고 있었다** — 즉 「서버가 안 준다」가 아니라 「앱이 안 받는다」였고, 그래서 제재당한
     * 사용자는 방 만들기·합류가 403 으로 막히는 이유를 알 수 없었다.
     *
     * 본문 모양은 서버 컨트롤러 테스트(`LocalUserControllerUnitTest`)가 못 박은 것과 같다 —
     * `restrictedUntil` 은 숫자(epoch)가 아니라 **ISO-8601 문자열**이다(레포에 Jackson
     * `WRITE_DATES_AS_TIMESTAMPS` 설정이 없고 `@JsonFormat` 도 없다).
     */
    @Test
    fun `노쇼 횟수와 제한 기한이 도메인까지 흐른다`() {
        val body = """
            {"uuid":"u-1","name":"김성윤","imageUrl":"https://cdn.moyeota.co.kr/profile/presets/01.png",
             "noShowCount":2,"restrictedUntil":"2026-11-04T08:30:00Z"}
        """.trimIndent()

        val info = json.decodeFromString<UserProfileResponse>(body).toDomain()

        assertEquals(2, info.noShowCount)
        assertEquals("2026-11-04T08:30:00Z", info.restrictedUntil)
    }

    /**
     * 제한이 없으면 서버가 **키는 남기고 값만 null** 로 준다(`@JsonInclude` 가 레포 전체에 없어
     * Jackson 기본값 `ALWAYS` 다 — 서버 테스트가 `jsonPath("$.restrictedUntil").value(nullValue())`
     * 로 확인한다). 논-널로 선언했다면 **제재가 없는 평범한 사용자**의 내 정보 조회가 통째로 터져
     * 로그인 직후 화면이 열리지 않는다 — 가장 흔한 경우에서 깨지는 쪽이다.
     */
    @Test
    fun `제한 기한이 null 이면 제한 없음으로 읽는다`() {
        val body = """{"uuid":"u-1","name":"김성윤","noShowCount":0,"restrictedUntil":null}"""

        val info = json.decodeFromString<UserProfileResponse>(body).toDomain()

        assertEquals(0, info.noShowCount)
        assertNull(info.restrictedUntil)
    }

    /**
     * 구버전 서버에는 두 키가 **아예 없다**. 기본값으로 떨어져야 하고, 그 기본값은
     * 「제재 없음」과 같은 모양이어야 한다 — 화면이 「모른다」와 「없다」를 따로 다루지 않기 때문이다
     * ([com.moyeota.domain.model.UserProfileInfo.noShowCount] KDoc).
     */
    @Test
    fun `제재 키가 아예 없어도 파싱되고 기본값으로 떨어진다`() {
        val info = json.decodeFromString<UserProfileResponse>("""{"uuid":"u-1","name":"김성윤"}""").toDomain()

        assertEquals(0, info.noShowCount)
        assertNull(info.restrictedUntil)
    }

    /**
     * ⚠️ **소수점 아래가 붙어도 문자열로 그대로 흘러야 한다.** 서버 컬럼이 `timestamp(6)` 이라
     * 실제 값에 마이크로초가 실릴 수 있다. 이 계층이 파싱하지 않는 것이 그 대비이고
     * (`Instant` 로 바꿨다면 고정 패턴을 쓴 코드가 여기서 깨진다), 화면은 `Instant.parse` 로 읽는다.
     */
    @Test
    fun `마이크로초가 붙은 제한 기한도 그대로 흐른다`() {
        val body = """{"uuid":"u-1","noShowCount":1,"restrictedUntil":"2026-11-04T08:30:00.123456Z"}"""

        assertEquals(
            "2026-11-04T08:30:00.123456Z",
            json.decodeFromString<UserProfileResponse>(body).toDomain().restrictedUntil,
        )
    }

    /**
     * 이름·이미지와 달리 **제한 기한에는 공백 정규화를 걸지 않는다.** 두 필드의 「없음」은 뜻이 다르다:
     * 이름의 null 은 「표시할 이름이 없다」이고 이쪽은 **「제한이 없다」는 사실 판정**이다. 정규화를
     * 끼우면 서버가 뭔가 보냈는데 비어 있었던 경우가 「제한 없음」으로 조용히 접히고, 화면은 막힌
     * 사용자에게 아무 안내도 하지 않는다. 그래서 **빈 문자열은 빈 문자열로 올라가고**, 읽을 수 없는
     * 값이라는 판정은 화면이 한다(`NoShowRecordsDisplay` 가 파싱 실패를 「제한 없음」이 아니라
     * 「기한 미표시」로 떨어뜨린다).
     */
    @Test
    fun `제한 기한의 공백을 매퍼가 접지 않는다`() {
        assertEquals("", UserProfileResponse(uuid = "u-1", restrictedUntil = "").toDomain().restrictedUntil)
        assertEquals("  ", UserProfileResponse(uuid = "u-1", restrictedUntil = "  ").toDomain().restrictedUntil)
    }

    @Test
    fun `모르는 필드가 늘어나도 파싱이 깨지지 않는다`() {
        // DTO 가 가진 필드를 여기 쓰면 "모르는 필드"가 아니게 되어 아무것도 검사하지 않는 테스트가 된다.
        val body = """{"uuid":"u-1","name":"김성윤","badgeId":3,"mannerScore":4.5}"""

        assertEquals("김성윤", json.decodeFromString<UserProfileResponse>(body).toDomain().name)
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
