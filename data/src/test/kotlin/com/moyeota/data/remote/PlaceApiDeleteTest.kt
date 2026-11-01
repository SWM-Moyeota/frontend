package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FavoritePlaceDeleteRequestDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.HTTP

/**
 * **즐겨찾기 삭제 3종이 실제로 어떤 요청으로 나가는지**를 진짜 Retrofit 으로 확인한다.
 * 하네스는 [UserApiDeleteBodyTest] 의 `service()` 를 그대로 베꼈다 — `chain.proceed()` 를 부르지
 * 않는 인터셉터라 소켓이 열리지 않고, `validateEagerly(true)` 로 인터페이스 전체를 `create()`
 * 시점에 파싱한다.
 *
 * ### 왜 리플렉션 검사(AuthenticatedPathContractTest)만으로는 부족한가
 * 그쪽은 애노테이션 값만 읽으므로 **본문에 무엇이 실리는지**를 못 본다. 다건 삭제의 유일한 계약이
 * 「키 이름이 `placeIds` 다」인데, 이름이 틀려도 컴파일과 실행은 통과하고 서버가 모르는 키를
 * 무시해 400 `INVALID_PLACE_IDS` 로만 드러난다(실측 2026-11-01, 로컬 `:8080`: `{"ids":[2]}` → 400).
 * 그 실패는 「서버가 거부했다」처럼 보여 원인이 앱 쪽 오타임을 가린다.
 *
 * 응답 계약(실측 2026-11-01, 로컬 `:8080`, `moyeota2`):
 * - 세 경로 모두 성공이 **204 No Content**, 본문 없음
 * - 단건·다건의 404 는 `FAVORITE_PLACE_NOT_FOUND` (없는 것·남의 것을 구분하지 않는다)
 * - `/all` 은 이미 비어 있으면 404 (멱등이 아니다)
 */
class PlaceApiDeleteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> service(
        service: Class<T>,
        status: Int = 204,
        body: String = "",
        captured: MutableList<Request> = mutableListOf(),
    ): T {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    captured += chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message(if (status == 204) "No Content" else "Response")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .validateEagerly(true)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(service)
    }

    private fun Request.bodyAsString(): String? = body?.let { requestBody ->
        Buffer().also { requestBody.writeTo(it) }.readUtf8()
    }

    /**
     * 핵심 검사. 동사가 POST 여야 하고(서버 `@PostMapping`), 본문 키가 `placeIds` 여야 한다.
     * 둘 중 하나라도 틀리면 실패 모양이 405 가 아니라 **400** 이라 원인을 가린다.
     */
    @Test
    fun `다건 삭제는 POST 로 placeIds 를 싣는다`() {
        val captured = mutableListOf<Request>()
        val api = service(PlaceApi::class.java, captured = captured)

        runBlocking { api.deleteFavoritePlaces(FavoritePlaceDeleteRequestDto(listOf(1L, 2L))) }

        val request = captured.single()
        assertEquals("POST", request.method)
        assertEquals(
            "http://localhost/api/v1/users/me/favorite-places/batch-delete",
            request.url.toString(),
        )
        assertEquals("""{"placeIds":[1,2]}""", request.bodyAsString())
    }

    /** 단건은 id 를 **경로**에 싣고 본문이 없다. 쿼리로 돌리면 서버 `{placeId}` 매핑에 닿지 못한다. */
    @Test
    fun `한 건 삭제는 DELETE 에 placeId 를 경로로 싣는다`() {
        val captured = mutableListOf<Request>()
        val api = service(PlaceApi::class.java, captured = captured)

        runBlocking { api.deleteFavoritePlace(3L) }

        val request = captured.single()
        assertEquals("DELETE", request.method)
        assertEquals("http://localhost/api/v1/users/me/favorite-places/3", request.url.toString())
        assertNull("단건 삭제에 본문이 실렸다", request.body)
        assertNull("placeId 가 쿼리로 나갔다", request.url.query)
    }

    /**
     * 전체 삭제는 `/all` 리터럴 경로다. 실측에서 리터럴이 `{placeId}` 템플릿을 이겨 204 가 왔고,
     * placeId 가 Long 이라 "all" 이 그쪽으로 흘러들 수도 없다.
     */
    @Test
    fun `전체 삭제는 DELETE all 이고 본문이 없다`() {
        val captured = mutableListOf<Request>()
        val api = service(PlaceApi::class.java, captured = captured)

        runBlocking { api.deleteAllFavoritePlaces() }

        val request = captured.single()
        assertEquals("DELETE", request.method)
        assertEquals("http://localhost/api/v1/users/me/favorite-places/all", request.url.toString())
        assertNull("전체 삭제에 본문이 실렸다", request.body)
    }

    /** 세 메서드 모두 204 + 빈 본문으로 끝난다 — 돌려받을 값이 없으니 「안 터진다」가 확인할 전부다. */
    @Test
    fun `204 는 본문 없이 성공으로 끝난다`() {
        val api = service(PlaceApi::class.java, status = 204, body = "")

        runBlocking {
            api.deleteFavoritePlace(3L)
            api.deleteFavoritePlaces(FavoritePlaceDeleteRequestDto(listOf(3L)))
            api.deleteAllFavoritePlaces()
        }
    }

    /**
     * **함정 자체를 기록해 두는 음성 대조군.** `@HTTP(method = "DELETE", hasBody = true)` 로
     * batch-delete 를 선언해도 **Retrofit 은 받아들인다** — 즉 컴파일도 파싱도 이것을 막지 않는다.
     *
     * 그렇게 나간 요청은 405 가 아니라 400
     * `{"code":"INVALID_REQUEST","message":"placeId: 형식이 올바르지 않습니다."}` 를 받는다(실측
     * 2026-11-01, 로컬 `:8080`). `DELETE .../favorite-places/{placeId}` 매핑이 "batch-delete" 를
     * placeId 로 잡아 Long 변환에 실패하기 때문이다 — **조용히 엉뚱한 엔드포인트로 간다.**
     * 서버 없이 잡을 수 있는 자리는 동사를 못 박는 테스트뿐이다
     * (`AuthenticatedPathContractTest.즐겨찾기 다건 삭제는 POST 다`).
     */
    @Test
    fun `다건 삭제를 DELETE 로 선언해도 Retrofit 은 막지 않는다 - 그래서 테스트로 막는다`() {
        val captured = mutableListOf<Request>()
        val api = service(WrongVerbBatchDeleteApi::class.java, captured = captured)

        runBlocking { api.deleteFavoritePlaces(FavoritePlaceDeleteRequestDto(listOf(1L))) }

        val request = captured.single()
        assertTrue(
            "Retrofit 이 @HTTP DELETE + 본문을 거부했다 — 그렇다면 운영 코드의 동사 고정은 덜 급해진다",
            request.method == "DELETE" && request.body != null,
        )
        // 이 요청이 서버에 닿으면 405 가 아니라 `{placeId}` 매핑의 400 이 온다(위 KDoc 참고).
        assertEquals(
            "http://localhost/api/v1/users/me/favorite-places/batch-delete",
            request.url.toString(),
        )
    }

    /** 음성 대조군 전용. 운영 코드에 이 모양이 들어가면 안 된다는 것을 보여주기 위한 선언이다. */
    private interface WrongVerbBatchDeleteApi {
        @HTTP(
            method = "DELETE",
            path = "api/v1/users/me/favorite-places/batch-delete",
            hasBody = true,
        )
        suspend fun deleteFavoritePlaces(@Body request: FavoritePlaceDeleteRequestDto)
    }
}
