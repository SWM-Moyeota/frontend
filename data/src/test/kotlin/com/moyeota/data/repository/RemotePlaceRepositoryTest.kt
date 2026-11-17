package com.moyeota.data.repository

import com.moyeota.data.remote.PlaceApi
import com.moyeota.data.remote.dto.AddressResponseDto
import com.moyeota.data.remote.dto.FavoritePlaceDeleteRequestDto
import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.FavoritePlaceRequestDto
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import com.moyeota.domain.model.FavoritePlaceException
import com.moyeota.domain.model.FavoritePlacePolicy
import com.moyeota.domain.model.Place
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * 즐겨찾기 **등록·삭제의 실패 처리**를 못 박는다. 서버가 **한 상태 코드에 여러 뜻을 담으므로**
 * 「상태 코드만으로 다루지 않는다」가 이 저장소의 판단이고, 경로마다 갈린다:
 *
 * - 삭제 단건·다건의 404 → **올린다.** 서버가 「없는 것」과 「남의 것」을 같은 404 로 숨기므로
 *   성공으로 접으면 남의 행을 지우려는 시도까지 성공으로 보이고, 화면은 서버와 어긋난 목록을 든다.
 * - `/all` 의 404 → **삼킨다.** 「이미 비어 있다」는 부른 쪽이 원한 상태가 이미 참이라는 뜻이다.
 * - 등록의 409 → **코드를 실어 올린다.** 중복과 상한 초과가 같은 409 인데 사용자가 할 일이 반대다.
 */
class RemotePlaceRepositoryTest {

    /**
     * 못 박는 것: **목록이 비어 보일 때도 전체 삭제가 끝까지 간다.** 매퍼가 placeId·좌표 없는 행을
     * 버리므로 화면의 목록이 비어 있어도 서버에는 행이 남아 있을 수 있고, 전체 삭제가 그 행을 치우는
     * 유일한 길이다. 404 를 그대로 올리면 사용자는 아무 일도 안 했는데 「지우지 못했어요」를 본다.
     */
    @Test
    fun `전체 삭제는 이미 비어 있다는 404 를 실패로 올리지 않는다`() = runBlocking {
        val api = FakePlaceApi(allDeleteError = httpException(404, FAVORITE_PLACE_NOT_FOUND_BODY))

        // 던지면 테스트가 실패한다 — 확인할 것은 「안 터진다」가 전부다.
        RemotePlaceRepository(api).deleteAllFavoritePlaces()

        assertEquals(1, api.allDeleteCalls)
    }

    /** 404 만 삼킨다는 경계. 500 까지 삼키면 서버 장애가 「다 지웠어요」로 보인다. */
    @Test
    fun `전체 삭제의 500 은 그대로 올린다`() = runBlocking {
        val api = FakePlaceApi(allDeleteError = httpException(500, """{"code":"INTERNAL","message":"서버 오류"}"""))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).deleteAllFavoritePlaces() }
        }

        assertEquals(500, e.httpStatus)
        assertTrue("500 이 「없음」으로 접혔다", !e.isNotFound)
    }

    /**
     * 못 박는 것: 빈 선택으로는 **서버를 부르지 않는다.** 서버는 400 `INVALID_PLACE_IDS` 를 주고
     * (실측: `{"placeIds":[]}` → 400), 그 에러가 화면에 「지우지 못했어요」로 서는데 사용자가 고른
     * 것은 애초에 없다.
     */
    @Test
    fun `다건 삭제에 빈 목록을 주면 서버를 부르지 않는다`() = runBlocking {
        val api = FakePlaceApi()

        RemotePlaceRepository(api).deleteFavoritePlaces(emptyList())

        assertEquals(emptyList<List<Long>>(), api.batchDeleteCalls)
    }

    /** 키 이름이 틀리면 서버가 조용히 400 을 준다 — 저장소를 거쳐도 `placeIds` 로 실리는지 본다. */
    @Test
    fun `다건 삭제는 고른 id 를 placeIds 로 그대로 넘긴다`() = runBlocking {
        val api = FakePlaceApi()

        RemotePlaceRepository(api).deleteFavoritePlaces(listOf(19L, 20L))

        assertEquals(listOf(listOf(19L, 20L)), api.batchDeleteCalls)
    }

    /**
     * 못 박는 것: **전부-아니면-전무의 404 를 성공으로 접지 않는다.** 실측에서 `[19, 999999]` 는
     * 404 였고 19 도 지워지지 않았다 — 성공으로 접으면 화면이 「지웠다」고 믿고 목록에서 빼
     * 서버와 영구히 어긋난다.
     */
    @Test
    fun `다건 삭제의 404 는 도메인 예외로 올라온다`() {
        val api = FakePlaceApi(batchDeleteError = httpException(404, FAVORITE_PLACE_NOT_FOUND_BODY))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).deleteFavoritePlaces(listOf(19L, 999_999L)) }
        }

        assertTrue("404 FAVORITE_PLACE_NOT_FOUND 가 「없음」으로 읽히지 않았다", e.isNotFound)
        assertEquals("FAVORITE_PLACE_NOT_FOUND", e.code)
    }

    /**
     * 못 박는 것: 단건 삭제의 404 도 올린다. 같은 id 두 번째도, 없는 id 도, **남의 id 도** 같은
     * 404 라서(실측) 성공으로 접으면 남의 행 삭제 시도까지 성공으로 보인다.
     */
    @Test
    fun `한 건 삭제의 404 는 성공으로 접히지 않는다`() {
        val api = FakePlaceApi(singleDeleteError = httpException(404, FAVORITE_PLACE_NOT_FOUND_BODY))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).deleteFavoritePlace(19L) }
        }

        assertTrue(e.isNotFound)
    }

    /**
     * `{code,message}` 가 아닌 에러 본문에서도 터지지 않아야 한다 — `POST .../favorite-places/all` 의
     * 405 가 Spring 기본 `{timestamp,status,error,path}` 다(실측). 코드 파싱이 null 로 떨어지고
     * 상태 코드만 근거로 남는다.
     */
    @Test
    fun `code 가 없는 에러 본문도 상태 코드만으로 다룬다`() {
        val body = """{"timestamp":"2026-11-01T00:00:00Z","status":405,"error":"Method Not Allowed","path":"/x"}"""
        val api = FakePlaceApi(singleDeleteError = httpException(405, body))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).deleteFavoritePlace(19L) }
        }

        assertEquals(405, e.httpStatus)
        assertEquals(null, e.code)
    }

    /**
     * ★ 못 박는 것: **등록 실패도 [FavoritePlaceException] 으로 좁혀진다.**
     *
     * 한동안 등록만 이 경계를 안 거쳐 raw [HttpException] 이 화면까지 올라갔다. presentation 은
     * retrofit 을 의존하지 않으므로(`presentation/build.gradle.kts` 에 domain 만 있다) 화면이 할 수
     * 있는 일은 `catch (Exception)` 뿐이었고, 실제로 두 409 를 「중복이거나 10개를 넘었을 수 있어요」
     * 한 문구로 뭉갰다. 이 테스트가 그 되돌이를 막는다 — 코드가 실려 와야 화면이 가를 수 있다.
     */
    @Test
    fun `등록 실패도 코드를 실은 FavoritePlaceException 으로 올린다`() {
        val api = FakePlaceApi(addError = httpException(409, FAVORITE_PLACE_LIMIT_EXCEEDED_BODY))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).addFavoritePlace(place()) }
        }

        assertEquals(409, e.httpStatus)
        assertTrue("상한 초과를 가리지 못했다 — 화면이 「하나를 빼 주세요」를 말할 수 없다", e.isLimitExceeded)
        assertTrue("상한 초과가 중복으로도 참이면 두 문구가 겹친다", !e.isDuplicated)
        // 화면 문구가 이 상수로 「N개까지」를 만든다 — 서버 원문과 어긋나면 다른 수를 말하게 된다
        assertTrue(
            "서버 원문의 상한과 FavoritePlacePolicy.MAX_COUNT 가 어긋났다: ${e.serverMessage}",
            e.serverMessage!!.contains("${FavoritePlacePolicy.MAX_COUNT}개"),
        )
    }

    /**
     * 같은 409 의 다른 뜻. **상태 코드로는 안 갈리므로** 코드로만 갈린다 —
     * 상한은 하나를 빼면 담기고, 중복은 할 일이 없다.
     */
    @Test
    fun `등록의 409 두 가지를 코드로 가른다`() {
        val api = FakePlaceApi(addError = httpException(409, FAVORITE_PLACE_DUPLICATED_BODY))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).addFavoritePlace(place()) }
        }

        assertTrue(e.isDuplicated)
        assertTrue("중복이 상한 초과로도 참이면 「10개를 넘었어요」라고 거짓말한다", !e.isLimitExceeded)
    }

    /** 400 `INVALID_COORDINATES` 같은 다른 실패를 409 로 추측하지 않는다. */
    @Test
    fun `등록의 400 은 409 사유로 추측하지 않는다`() {
        val body = """{"code":"INVALID_COORDINATES","message":"좌표가 올바르지 않습니다."}"""
        val api = FakePlaceApi(addError = httpException(400, body))

        val e = assertThrows(FavoritePlaceException::class.java) {
            runBlocking { RemotePlaceRepository(api).addFavoritePlace(place()) }
        }

        assertEquals(400, e.httpStatus)
        assertTrue(!e.isDuplicated && !e.isLimitExceeded && !e.isNotFound)
    }

    private fun place() = Place(
        name = "판교역",
        roadName = "경기 성남시 분당구 판교역로 160",
        latitude = 37.3947,
        longitude = 127.1112,
    )

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())),
    )

    private class FakePlaceApi(
        private val singleDeleteError: Throwable? = null,
        private val batchDeleteError: Throwable? = null,
        private val allDeleteError: Throwable? = null,
        private val addError: Throwable? = null,
    ) : PlaceApi {
        val singleDeleteCalls = mutableListOf<Long>()
        val batchDeleteCalls = mutableListOf<List<Long>>()
        var allDeleteCalls = 0
            private set

        override suspend fun searchPlaces(query: String) = PlaceSearchListResponse()

        override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
            Response.success(AddressResponseDto())

        override suspend fun addFavoritePlace(request: FavoritePlaceRequestDto) {
            addError?.let { throw it }
        }

        override suspend fun getFavoritePlaces() = FavoritePlaceListResponse()

        override suspend fun deleteFavoritePlace(placeId: Long) {
            singleDeleteCalls += placeId
            singleDeleteError?.let { throw it }
        }

        override suspend fun deleteFavoritePlaces(request: FavoritePlaceDeleteRequestDto) {
            batchDeleteCalls += request.placeIds
            batchDeleteError?.let { throw it }
        }

        override suspend fun deleteAllFavoritePlaces() {
            allDeleteCalls++
            allDeleteError?.let { throw it }
        }
    }

    private companion object {
        /** 실측 본문 그대로(2026-11-01, 로컬 `:8080`) — 없는 것·남의 것·두 번째 삭제가 모두 이것이다. */
        const val FAVORITE_PLACE_NOT_FOUND_BODY =
            """{"code":"FAVORITE_PLACE_NOT_FOUND","message":"등록되지 않은 즐겨찾기입니다."}"""

        /** 서버 `PlaceErrorCode` 원문 그대로. 상한값이 [FavoritePlacePolicy.MAX_COUNT] 와 같아야 한다. */
        const val FAVORITE_PLACE_LIMIT_EXCEEDED_BODY =
            """{"code":"FAVORITE_PLACE_LIMIT_EXCEEDED","message":"자주가는 장소는 최대 10개까지만 등록할 수 있습니다."}"""

        const val FAVORITE_PLACE_DUPLICATED_BODY =
            """{"code":"FAVORITE_PLACE_DUPLICATED","message":"이미 등록된 장소입니다."}"""
    }
}
