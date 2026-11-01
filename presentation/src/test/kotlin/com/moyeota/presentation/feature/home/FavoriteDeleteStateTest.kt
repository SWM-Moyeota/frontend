package com.moyeota.presentation.feature.home

import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.FavoritePlaceException
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.ReverseAddress
import com.moyeota.domain.repository.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 14 홈의 즐겨찾기 **삭제가 어느 경로로 나가고 실패를 어떻게 다루는가** — [HomeViewModel] 과
 * Repository 사이만 본다(이 모듈에는 Robolectric 도 compose-ui-test 도 없다).
 *
 * data 쪽 `RemotePlaceRepositoryTest` 는 「404 를 올리는가/삼키는가」까지만 보고, 이 테스트는
 * 그 위를 본다 — **화면이 404 를 성공으로 접지 않는가**, **선택 1곳도 다건 경로로 가는가**,
 * 그리고 **목록이 비어 보일 때도 전체 삭제를 부르는가**. 그 셋이 이 묶음의 결함 지점이다.
 */
// `Dispatchers.setMain`·`resetMain`·`advanceUntilIdle` 은 아직 opt-in 이 필요하다
// (kotlinx-coroutines-test 1.10.2). 테스트 전용이고 대체 API 가 없어 클래스 단위로 받는다.
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteDeleteStateTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 못 박는 것: 선택이 **1곳이어도 단건 삭제를 쓰지 않는다.**
     *
     * 서버가 전부-아니면-전무라(실측 2026-11-01, 로컬 `:8080`) 다건 경로는 「3개 중 1개만 지워진」
     * 상태를 만들지 않는다. 선택 수에 따라 경로를 갈라 두면 2곳을 고른 사용자만 그 보장을 받는
     * 꼴이 되고, 단건을 N번 돌리는 구현으로 되돌아가는 문턱도 낮아진다.
     */
    @Test
    fun `선택이 한 곳이어도 다건 삭제 경로로 보낸다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(favorites = listOf(favorite(19), favorite(20)))
        val viewModel = HomeViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.toggleFavoriteEditing()
        viewModel.toggleFavoriteSelection(favorite(19))
        viewModel.deleteSelectedFavorites()
        advanceUntilIdle()

        assertEquals(listOf(listOf(19L)), repository.batchDeletes)
        assertEquals("선택 1곳을 단건 삭제로 보냈다", emptyList<Long>(), repository.singleDeletes)
    }

    /** 성공하면 편집 모드를 닫고 목록을 다시 받는다 — 낙관적 제거를 하지 않으므로 재조회가 필수다. */
    @Test
    fun `다건 삭제가 성공하면 편집 모드를 닫고 목록을 다시 받는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(favorites = listOf(favorite(19)))
        val viewModel = HomeViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.toggleFavoriteEditing()
        viewModel.toggleFavoriteSelection(favorite(19))

        val loadsBefore = repository.loadCalls
        viewModel.deleteSelectedFavorites()
        advanceUntilIdle()

        assertFalse("성공인데 편집 모드가 남아 있다", viewModel.editState.value.editing)
        assertEquals(emptySet<Long>(), viewModel.editState.value.selectedIds)
        assertEquals(null, viewModel.editState.value.errorMessage)
        assertEquals(loadsBefore + 1, repository.loadCalls)
    }

    /**
     * 못 박는 것: **404 를 성공으로 접지 않는다.** 서버가 「이미 지워졌다」·「없다」·「남의 것」을
     * 같은 404 `FAVORITE_PLACE_NOT_FOUND` 로 숨기므로(실측) 성공으로 접으면 남의 행을 지우려는
     * 시도까지 성공으로 보인다. 대신 문구를 띄우고 **목록을 다시 받아** 서버와 맞춘다.
     *
     * 선택도 비운다 — 실패한 선택을 남기면 사라진 id 를 다시 보내 같은 404 를 반복한다.
     */
    @Test
    fun `다건 삭제의 404 를 성공으로 접지 않고 목록을 다시 받는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(
            favorites = listOf(favorite(19)),
            batchDeleteError = notFound(),
        )
        val viewModel = HomeViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.toggleFavoriteEditing()
        viewModel.toggleFavoriteSelection(favorite(19))

        val loadsBefore = repository.loadCalls
        viewModel.deleteSelectedFavorites()
        advanceUntilIdle()

        val state = viewModel.editState.value
        assertEquals("이미 지워졌거나 없는 곳이에요. 목록을 다시 불러왔어요", state.errorMessage)
        assertTrue("실패인데 편집 모드가 닫혔다 — 다시 고를 자리가 없어진다", state.editing)
        assertEquals(emptySet<Long>(), state.selectedIds)
        assertEquals("실패했는데 목록을 다시 받지 않았다", loadsBefore + 1, repository.loadCalls)
    }

    /** 404 가 아닌 실패는 사유를 가리지 않는다 — 사용자가 할 수 있는 일이 재시도뿐이다. */
    @Test
    fun `404 가 아닌 실패는 일반 문구로 알린다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(
            favorites = listOf(favorite(19)),
            batchDeleteError = FavoritePlaceException(code = null, serverMessage = null, httpStatus = 500),
        )
        val viewModel = HomeViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.toggleFavoriteEditing()
        viewModel.toggleFavoriteSelection(favorite(19))

        viewModel.deleteSelectedFavorites()
        advanceUntilIdle()

        assertEquals("즐겨찾기를 지우지 못했어요", viewModel.editState.value.errorMessage)
    }

    /** 빈 선택은 서버가 400 `INVALID_PLACE_IDS` 다 — 아예 부르지 않는다. */
    @Test
    fun `아무것도 고르지 않으면 삭제를 부르지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(favorites = listOf(favorite(19)))
        val viewModel = HomeViewModel(repository)
        viewModel.toggleFavoriteEditing()

        viewModel.deleteSelectedFavorites()
        advanceUntilIdle()

        assertEquals(emptyList<List<Long>>(), repository.batchDeletes)
    }

    /**
     * ★ **이 묶음에서 가장 중요한 테스트다.** 목록이 비어 보여도 전체 삭제는 서버까지 간다.
     *
     * `placeId` 나 좌표가 없는 레거시 행은 매퍼가 버려서 목록에 안 나오는데 서버에서는 10칸 중
     * 하나를 계속 차지한다 — 고를 수 없으니 per-item 삭제로는 영영 닿지 못하고, 10칸이 그렇게
     * 막히면 사용자는 새 장소를 담을 수도 자리를 비울 수도 없다(그 전까지 탈출구가 회원 탈퇴였다).
     * 「목록이 비었으니 부를 필요 없다」는 최적화를 넣는 순간 그 탈출구가 사라진다.
     */
    @Test
    fun `목록이 비어 보여도 전체 삭제는 서버를 부른다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(favorites = emptyList())
        val viewModel = HomeViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.toggleFavoriteEditing()

        viewModel.deleteAllFavorites()
        advanceUntilIdle()

        assertEquals(1, repository.allDeletes)
        assertEquals(null, viewModel.editState.value.errorMessage)
    }

    /**
     * 편집 모드를 닫으면 선택이 비워진다. 안 비우면 다음에 열었을 때 사용자가 고르지도 않은 곳이
     * 선택된 채 「선택한 N곳 삭제」가 활성된다 — 되돌릴 수 없는 삭제라 그 상태를 남겨선 안 된다.
     */
    @Test
    fun `편집 모드를 닫으면 선택이 비워진다`() = runTest(mainDispatcher) {
        val viewModel = HomeViewModel(FakePlaceRepository(favorites = listOf(favorite(19))))
        viewModel.toggleFavoriteEditing()
        viewModel.toggleFavoriteSelection(favorite(19))
        assertEquals(setOf(19L), viewModel.editState.value.selectedIds)

        viewModel.toggleFavoriteEditing()

        assertEquals(emptySet<Long>(), viewModel.editState.value.selectedIds)
        assertFalse(viewModel.editState.value.editing)
    }

    /** 같은 행을 두 번 누르면 선택이 풀린다 — 체크박스의 기본 기대다. */
    @Test
    fun `같은 즐겨찾기를 다시 누르면 선택이 풀린다`() = runTest(mainDispatcher) {
        val viewModel = HomeViewModel(FakePlaceRepository(favorites = listOf(favorite(19))))
        viewModel.toggleFavoriteEditing()

        viewModel.toggleFavoriteSelection(favorite(19))
        viewModel.toggleFavoriteSelection(favorite(19))

        assertEquals(emptySet<Long>(), viewModel.editState.value.selectedIds)
    }

    private fun favorite(id: Long) = FavoritePlace(
        id = id,
        name = "장소$id",
        roadName = "서울 강남구 테헤란로 $id",
        latitude = 37.4979,
        longitude = 127.0276,
        sequence = id.toInt(),
    )

    /** 실측 본문의 코드 그대로 — 없는 것·남의 것·두 번째 삭제가 모두 이것이다. */
    private fun notFound() = FavoritePlaceException(
        code = "FAVORITE_PLACE_NOT_FOUND",
        serverMessage = "등록되지 않은 즐겨찾기입니다.",
        httpStatus = 404,
    )

    /**
     * ★ 못 박는 것: **조회 실패는 「등록한 곳이 없음」이 아니다.**
     *
     * 둘을 같게 두면 지하철에서 조회가 끊긴 사용자에게 화면이 「즐겨찾기를 등록하면…」을 보여 주고,
     * 편집을 열면 「전체 삭제로 비울 수 있어요」까지 권한다. 통신이 돌아오는 순간
     * `DELETE /favorite-places/all` 이 204 로 성공해 **서버의 멀쩡한 즐겨찾기가 전부 사라진다.**
     * 되돌릴 수 없다. 그래서 `loaded` 가 「전체 삭제」를 그릴지의 유일한 근거다.
     */
    @Test
    fun `조회에 실패하면 전체 삭제의 근거가 서지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(loadError = RuntimeException("네트워크"))
        val viewModel = HomeViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.editState.value
        assertFalse("조회에 실패했는데 loaded 가 섰다 — 전체 삭제가 그려진다", state.loaded)
        assertTrue("실패를 상태로 남기지 않으면 화면이 빈 목록과 구분할 수 없다", state.loadFailed)
    }

    /** 반대 경우 — 받아 왔고 결과가 비었으면 전체 삭제의 근거가 선다(레거시 행 탈출구). */
    @Test
    fun `조회에 성공하면 결과가 비어도 전체 삭제의 근거가 선다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(favorites = emptyList())
        val viewModel = HomeViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.editState.value.loaded)
        assertFalse(viewModel.editState.value.loadFailed)
    }

    /**
     * 못 박는 것: **조회가 실패해도 이미 보여 주던 목록을 비우지 않는다.**
     * 비우면 사용자는 자기 즐겨찾기가 사라진 줄 안다.
     */
    @Test
    fun `조회 실패가 이미 받아 둔 목록을 지우지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(favorites = listOf(favorite(19, "집")))
        val viewModel = HomeViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(1, viewModel.favorites.value.size)

        repository.failNextLoad = true
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("실패가 목록을 비웠다", 1, viewModel.favorites.value.size)
        assertTrue(viewModel.editState.value.loadFailed)
    }

    /** 편집을 여닫는다고 조회의 사실([loaded])이 뒤집히면 「전체 삭제」가 사라진다. */
    @Test
    fun `편집을 여닫아도 조회 성공 사실은 남는다`() = runTest(mainDispatcher) {
        val viewModel = HomeViewModel(FakePlaceRepository(favorites = emptyList()))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.toggleFavoriteEditing()
        viewModel.toggleFavoriteEditing()

        assertTrue(viewModel.editState.value.loaded)
    }

    private fun favorite(id: Long, name: String) = FavoritePlace(
        id = id,
        name = name,
        roadName = "도로명",
        latitude = 37.0,
        longitude = 127.0,
        sequence = id.toInt(),
    )

    private class FakePlaceRepository(
        private val favorites: List<FavoritePlace> = emptyList(),
        private val batchDeleteError: Throwable? = null,
        private val singleDeleteError: Throwable? = null,
        private val allDeleteError: Throwable? = null,
        private val loadError: Throwable? = null,
    ) : PlaceRepository {
        /** 두 번째 조회부터 실패시키고 싶을 때 — 「이미 받아 둔 목록을 지우지 않는다」를 보려면 필요하다 */
        var failNextLoad = false
        val batchDeletes = mutableListOf<List<Long>>()
        val singleDeletes = mutableListOf<Long>()
        var allDeletes = 0
            private set
        var loadCalls = 0
            private set

        override suspend fun searchPlaces(query: String): List<Place> = emptyList()

        override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseAddress? = null

        override suspend fun addFavoritePlace(place: Place) = Unit

        override suspend fun getFavoritePlaces(): List<FavoritePlace> {
            loadCalls++
            loadError?.let { throw it }
            if (failNextLoad) throw RuntimeException("네트워크")
            return favorites
        }

        override suspend fun deleteFavoritePlace(id: Long) {
            singleDeletes += id
            singleDeleteError?.let { throw it }
        }

        override suspend fun deleteFavoritePlaces(ids: List<Long>) {
            batchDeletes += ids
            batchDeleteError?.let { throw it }
        }

        override suspend fun deleteAllFavoritePlaces() {
            allDeletes++
            allDeleteError?.let { throw it }
        }
    }
}
