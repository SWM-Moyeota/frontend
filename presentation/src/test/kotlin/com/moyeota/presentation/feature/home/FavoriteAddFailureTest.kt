package com.moyeota.presentation.feature.home

import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.FavoritePlaceException
import com.moyeota.domain.model.FavoritePlacePolicy
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.ReverseAddress
import com.moyeota.domain.repository.PlaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 15 목적지의 **★ 담기가 실패했을 때 무슨 말을 하는가.**
 *
 * 등록 실패는 **409 두 가지**이고 상태 코드로는 안 갈린다 —
 * `FAVORITE_PLACE_DUPLICATED` 와 `FAVORITE_PLACE_LIMIT_EXCEEDED`.
 * 예전에는 둘을 「자주 가는 곳에 담지 못했어요 (중복이거나 10개를 넘었을 수 있어요)」 하나로 뭉갰다.
 * 그게 나쁜 까닭은 **사용자가 할 일이 정반대**라는 것이다:
 *
 * | | 중복 | 상한 초과 |
 * |---|---|---|
 * | 사실 | 원한 상태가 이미 참이다 | 자리가 없다 |
 * | 할 일 | 없다 | 하나를 뺀다 |
 *
 * 뭉개면 이미 담아 둔 사용자는 「10개를 넘었나?」 싶어 목록을 열어 세어 보고, 10칸이 찬 사용자는
 * 「이미 담았나?」 싶어 같은 ★ 를 다시 누른다. 둘 다 서버가 이미 가려 준 것을 화면이 숨겨서 생긴다.
 */
// `Dispatchers.setMain`·`resetMain`·`advanceUntilIdle` 은 아직 opt-in 이 필요하다.
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteAddFailureTest {

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
     * 상한 초과는 **할 일이 있는 실패**다 — 하나를 빼면 담긴다. 그 길을 말해 주지 않으면 사용자는
     * 다시 누르기만 반복한다. 수는 [FavoritePlacePolicy.MAX_COUNT] 에서 온다(서버 원문과 같은 값임을
     * data 쪽 `RemotePlaceRepositoryTest` 가 못 박는다).
     */
    @Test
    fun `상한 초과는 몇 개까지인지와 할 일을 함께 말한다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(addError = limitExceeded())
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        val message = viewModel.uiState.value.favoriteActionMessage
        assertEquals(
            "자주 가는 곳은 ${FavoritePlacePolicy.MAX_COUNT}개까지만 담을 수 있어요. 하나를 빼고 다시 담아 주세요",
            message,
        )
    }

    /** 중복은 **할 일이 없는 실패**다 — 「다시」·「하나를 빼고」 같은 말을 붙이지 않는다. */
    @Test
    fun `중복은 할 일을 시키지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(addError = duplicated())
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        val message = viewModel.uiState.value.favoriteActionMessage
        assertEquals("이미 담아 둔 곳이에요", message)
        assertTrue("할 일이 없는데 개수를 말하면 사용자가 목록을 세어 본다", !message!!.contains("개"))
    }

    /** ★ 이 묶음의 요지 — 같은 409 두 개가 서로 다른 말을 한다. */
    @Test
    fun `같은 409 두 가지가 서로 다른 말을 한다`() = runTest(mainDispatcher) {
        fun messageFor(error: Throwable): String? {
            val viewModel = DestinationViewModel(FakePlaceRepository(addError = error))
            advanceUntilIdle()
            viewModel.addFavorite(place("판교역"))
            advanceUntilIdle()
            return viewModel.uiState.value.favoriteActionMessage
        }

        assertNotEquals(messageFor(limitExceeded()), messageFor(duplicated()))
    }

    /**
     * 못 박는 것: **응답을 못 받은 실패는 할 일을 말한다.**
     *
     * 담기가 `favoritePlaceCall` 경계를 쓰게 되면서 비로소 닿는 경우다 — 그 전에는 raw
     * `IOException` 이라 화면이 가릴 수 없었다. 일반 문구(「담지 못했어요」)는 재시도를 말하지
     * 않으므로, 비행기모드인 사용자는 왜인지 모른 채 ★ 를 계속 누른다.
     */
    @Test
    fun `네트워크 실패는 연결을 확인하라고 말한다`() = runTest(mainDispatcher) {
        val offline = FavoritePlaceException(code = null, serverMessage = null, isNetwork = true)
        val repository = FakePlaceRepository(addError = offline)
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        assertEquals(FavoriteNetworkFailureMessage, viewModel.uiState.value.favoriteActionMessage)
        assertNotEquals(
            "네트워크 실패가 일반 문구로 떨어졌다 — 할 일을 말하지 않는다",
            "자주 가는 곳에 담지 못했어요",
            viewModel.uiState.value.favoriteActionMessage,
        )
    }

    /**
     * 모르는 코드는 일반 문구다 — 서버가 `PlaceErrorCode` 를 늘려도 앱이 틀린 말을 하지 않는다
     * (409 라고 「10개를 넘었어요」로 **추측하지 않는다**).
     */
    @Test
    fun `모르는 실패는 사유를 지어내지 않는다`() = runTest(mainDispatcher) {
        val general = "자주 가는 곳에 담지 못했어요"
        val unknown409 = FavoritePlaceException(code = "PLACE_SOMETHING_NEW", serverMessage = null, httpStatus = 409)
        val repository = FakePlaceRepository(addError = unknown409)
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        assertEquals(general, viewModel.uiState.value.favoriteActionMessage)
    }

    /** `FavoritePlaceException` 이 아닌 실패(데이터 계층의 경계가 비는 경우)도 일반 문구로 떨어진다. */
    @Test
    fun `예외 종류를 가리지 못해도 문구는 선다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(addError = IllegalStateException("boom"))
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        assertEquals("자주 가는 곳에 담지 못했어요", viewModel.uiState.value.favoriteActionMessage)
    }

    /**
     * 못 박는 것: **취소는 실패가 아니다.**
     *
     * `catch (Exception)` 하나로 두면 [CancellationException] 까지 삼켜 화면을 떠난 것이
     * 「담지 못했어요」로 보이고, 더 나쁘게는 코루틴 취소가 전파되지 않는다. 빼기 쪽은 처음부터
     * 되던졌는데(`removeFavorite`) 담기 쪽만 빠져 있었다 — 같은 규칙으로 맞춘다.
     */
    @Test
    fun `취소는 실패 문구로 바뀌지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(addError = CancellationException("화면 이탈"))
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        assertNull("취소가 실패 문구로 섰다", viewModel.uiState.value.favoriteActionMessage)
    }

    /** 성공 경로는 그대로다 — 목록을 다시 받아야 ★ 가 채워진다(낙관적 갱신을 하지 않는다). */
    @Test
    fun `담기에 성공하면 목록을 다시 받는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository()
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()
        val loadsBefore = repository.loadCalls

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        assertEquals("판교역을(를) 자주 가는 곳에 담았어요", viewModel.uiState.value.favoriteActionMessage)
        assertEquals("성공했는데 목록을 다시 받지 않았다", loadsBefore + 1, repository.loadCalls)
    }

    /** 실패했으면 목록이 바뀐 게 없다 — 재조회로 사용자를 기다리게 하지 않는다. */
    @Test
    fun `담기에 실패하면 목록을 다시 받지 않는다`() = runTest(mainDispatcher) {
        val repository = FakePlaceRepository(addError = duplicated())
        val viewModel = DestinationViewModel(repository)
        advanceUntilIdle()
        val loadsBefore = repository.loadCalls

        viewModel.addFavorite(place("판교역"))
        advanceUntilIdle()

        assertEquals(loadsBefore, repository.loadCalls)
    }

    /** 실측 본문의 코드·원문 그대로. */
    private fun limitExceeded() = FavoritePlaceException(
        code = "FAVORITE_PLACE_LIMIT_EXCEEDED",
        serverMessage = "자주가는 장소는 최대 10개까지만 등록할 수 있습니다.",
        httpStatus = 409,
    )

    private fun duplicated() = FavoritePlaceException(
        code = "FAVORITE_PLACE_DUPLICATED",
        serverMessage = "이미 등록된 장소입니다.",
        httpStatus = 409,
    )

    private fun place(name: String) = Place(name, "경기 성남시 분당구 판교역로 160", 37.3947, 127.1112)

    private class FakePlaceRepository(
        private val addError: Throwable? = null,
    ) : PlaceRepository {
        var loadCalls = 0
            private set

        override suspend fun searchPlaces(query: String): List<Place> = emptyList()

        override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseAddress? = null

        override suspend fun addFavoritePlace(place: Place) {
            addError?.let { throw it }
        }

        override suspend fun getFavoritePlaces(): List<FavoritePlace> {
            loadCalls++
            return emptyList()
        }

        override suspend fun deleteFavoritePlace(id: Long) = Unit

        override suspend fun deleteFavoritePlaces(ids: List<Long>) = Unit

        override suspend fun deleteAllFavoritePlaces() = Unit
    }
}
