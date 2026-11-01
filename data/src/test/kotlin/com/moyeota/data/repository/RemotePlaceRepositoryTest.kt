package com.moyeota.data.repository

import com.moyeota.data.remote.PlaceApi
import com.moyeota.data.remote.dto.AddressResponseDto
import com.moyeota.data.remote.dto.FavoritePlaceDeleteRequestDto
import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.FavoritePlaceRequestDto
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import com.moyeota.domain.model.FavoritePlaceException
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
 * 즐겨찾기 **삭제 3종의 실패 처리**를 못 박는다. 세 경로 모두 멱등이 아니라서(실측 2026-11-01,
 * 로컬 `:8080`) 「404 를 어떻게 다루는가」가 이 저장소의 유일한 판단이고, 그 판단이 두 갈래다:
 *
 * - 단건·다건의 404 → **올린다.** 서버가 「없는 것」과 「남의 것」을 같은 404 로 숨기므로
 *   성공으로 접으면 남의 행을 지우려는 시도까지 성공으로 보이고, 화면은 서버와 어긋난 목록을 든다.
 * - `/all` 의 404 → **삼킨다.** 「이미 비어 있다」는 부른 쪽이 원한 상태가 이미 참이라는 뜻이다.
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

    private fun httpException(code: Int, body: String) = HttpException(
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())),
    )

    private class FakePlaceApi(
        private val singleDeleteError: Throwable? = null,
        private val batchDeleteError: Throwable? = null,
        private val allDeleteError: Throwable? = null,
    ) : PlaceApi {
        val singleDeleteCalls = mutableListOf<Long>()
        val batchDeleteCalls = mutableListOf<List<Long>>()
        var allDeleteCalls = 0
            private set

        override suspend fun searchPlaces(query: String) = PlaceSearchListResponse()

        override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
            Response.success(AddressResponseDto())

        override suspend fun addFavoritePlace(request: FavoritePlaceRequestDto) = Unit

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
    }
}
