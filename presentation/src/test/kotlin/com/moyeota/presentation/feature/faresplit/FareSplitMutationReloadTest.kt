package com.moyeota.presentation.feature.faresplit

import com.moyeota.domain.model.FareSplitDetail
import com.moyeota.domain.model.FareSplitDraft
import com.moyeota.domain.model.FareSplitException
import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.MyFareSplit
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.repository.FareSplitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **본문 없는 204 뒤에 상세를 다시 읽는가** — 이 작업에서 가장 비싼 불변식이다.
 *
 * ### 왜 ViewModel 을 직접 돌려 보는가 ★
 * 「보냈어요」·「받았어요」·「아직 안 들어왔어요」는 **응답 본문이 없다.** 즉 서버가 무엇을 바꿨는지
 * 앱이 알 길이 없고, 한 행만 낙관적으로 고치면 **마지막 확인이 정산 전체를 닫는 것**(COMPLETED +
 * 계좌번호 삭제)을 놓친다. 그러면 닫힌 정산에 계좌번호가 남아 보이고 「받았어요」가 그대로 떠 있다.
 *
 * 그 결함은 **화면으로도, 순수 함수 테스트로도 보이지 않는다.** 재조회를 빼도 화면은 멀쩡히
 * 그려지고([transferActionsOf] 는 받은 상세대로 정확히 판정한다), 다만 **그 상세가 과거**다.
 * 그래서 「호출 순서」를 값으로 못 박는 테스트가 따로 필요하다.
 *
 * 화면은 그리지 않는다 — 이 모듈에는 Robolectric 도 compose-ui-test 도 없다. 보는 것은 ViewModel 과
 * Repository 사이의 호출뿐이고, `viewModelScope` 를 돌리려고 Main 디스패처만 바꾼다
 * (`ProfileEditSaveTest` 와 같은 배치·같은 근거).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FareSplitMutationReloadTest {

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
     * 호출을 **순서대로** 적어 두는 저장소. 「불렀는가」가 아니라 「**언제** 불렀는가」를 보기
     * 때문이다 — 쓰기 전에 읽은 한 번과 쓰기 **뒤에** 읽은 한 번을 구분하지 못하면 이 테스트는
     * 재조회가 없어도 통과한다.
     */
    private class RecordingRepository(
        private var detail: FareSplitDetail? = detail(role = FareSplitRole.SENDER),
        private val failure: FareSplitException? = null,
    ) : FareSplitRepository {

        val calls = mutableListOf<String>()

        override suspend fun startFareSplit(partyId: Long, draft: FareSplitDraft): FareSplitDetail {
            calls += "start"
            failure?.let { throw it }
            return requireNotNull(detail)
        }

        override suspend fun getFareSplit(partyId: Long): FareSplitDetail? {
            calls += "get"
            return detail
        }

        override suspend fun editFareSplit(partyId: Long, draft: FareSplitDraft): FareSplitDetail {
            calls += "edit"
            failure?.let { throw it }
            return requireNotNull(detail)
        }

        override suspend fun markSent(partyId: Long) {
            calls += "markSent"
            failure?.let { throw it }
        }

        override suspend fun confirmReceived(partyId: Long, senderPublicId: String) {
            calls += "confirmReceived($senderPublicId)"
            failure?.let { throw it }
        }

        override suspend fun markNotReceived(partyId: Long, senderPublicId: String) {
            calls += "markNotReceived($senderPublicId)"
            failure?.let { throw it }
        }

        override suspend fun getMyFareSplits(status: TransferStatus): List<MyFareSplit> {
            calls += "myList"
            return emptyList()
        }
    }

    /**
     * 방 상세 조회. ViewModel 이 **이 한 가지만** 받으므로(`loadRide`) 가짜도 람다 하나다 —
     * `RideRepository` 17개 멤버를 흉내 내면 정산과 무관한 시그니처 변경마다 이 테스트가 깨진다.
     *
     * 기본값은 **터뜨린다**: 정산 상세가 있는 경우에는 방을 읽을 이유가 없고, 읽는다면 그건
     * 요청이 하나 늘어난 결함이다(끝난 방 카드마다 붙는다).
     */
    private val forbiddenRideLoad: suspend (Long) -> Ride = {
        throw AssertionError("정산 상세가 있는 경우에는 방 상세를 읽지 않아야 한다")
    }

    /**
     * 방 상세 조회가 **실패하는** 경우. `AssertionError` 가 아니라 평범한 예외를 던진다 —
     * 네트워크 실패를 흉내 내는 자리이고, `Error` 를 던지면 ViewModel 의 `catch (e: Exception)` 을
     * 지나쳐 **실제로는 일어나지 않는 경로**를 보게 된다(`Error` 는 잡지 않는 것이 맞다).
     */
    private val failingRideLoad: suspend (Long) -> Ride = {
        throw IllegalStateException("방 상세 조회 실패")
    }

    private fun viewModel(repository: FareSplitRepository) =
        FareSplitDetailViewModel(repository, forbiddenRideLoad, partyId = 42L)

    // ─────────────────── 성공 뒤 재조회 ───────────────────

    /**
     * 「보냈어요」가 성공하면 **그 뒤에** 상세를 다시 읽는다. 홈 배너도 함께 낡지만 그쪽은 홈이
     * 복귀하며 스스로 읽는다([FareSplitBannerViewModel.onScreenStart]).
     */
    @Test
    fun `보냈어요가 성공하면 그 뒤에 상세를 다시 읽는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.markSent()
        advanceUntilIdle()

        assertEquals(listOf("get", "markSent", "get"), repository.calls)
    }

    /** 마지막 확인이 정산 전체를 닫는다 — 재조회가 없으면 지워진 계좌번호가 화면에 남는다. */
    @Test
    fun `받았어요가 성공하면 그 뒤에 상세를 다시 읽는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(detail = detail())
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.confirmReceived(SENDER_A)
        advanceUntilIdle()

        assertEquals(listOf("get", "confirmReceived($SENDER_A)", "get"), repository.calls)
    }

    @Test
    fun `못 받았어요가 성공하면 그 뒤에 상세를 다시 읽는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(detail = detail())
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.markNotReceived(SENDER_A)
        advanceUntilIdle()

        assertEquals(listOf("get", "markNotReceived($SENDER_A)", "get"), repository.calls)
    }

    // ─────────────────── 실패 뒤 처리 ───────────────────

    /**
     * 「낡았다」는 신호는 문구와 **함께** 재조회한다 — 어긋난 화면 위에서 다음 동작을 하면 같은
     * 실패가 되풀이되거나 이미 지워진 계좌번호를 유효한 것처럼 보여 준다.
     */
    @Test
    fun `상세가 낡았다는 실패에도 다시 읽는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(
            detail = detail(),
            failure = failure("FARE_SPLIT_CLOSED", 409),
        )
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.confirmReceived(SENDER_A)
        advanceUntilIdle()

        assertEquals(listOf("get", "confirmReceived($SENDER_A)", "get"), repository.calls)
        assertNotNull(vm.actionError.value)
    }

    /** 입력 실수는 상세가 낡았다는 뜻이 아니다 — 쓸데없는 재조회로 화면을 흔들지 않는다. */
    @Test
    fun `입력 실수 실패에는 다시 읽지 않는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(
            detail = detail(),
            failure = failure("INVALID_REQUEST", 400),
        )
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.confirmReceived(SENDER_A)
        advanceUntilIdle()

        assertEquals(listOf("get", "confirmReceived($SENDER_A)"), repository.calls)
    }

    /**
     * 서버가 식별하지 못한 행은 **부를 publicId 가 없다** — 지어내면 404
     * `FARE_SPLIT_TRANSFER_NOT_FOUND` 다. 화면이 버튼을 잠그지만([transferActionsOf]) ViewModel 도
     * 한 번 더 막는다(잠금이 한 겹뿐이면 그 한 겹이 풀릴 때 조용히 404 가 된다).
     */
    @Test
    fun `publicId 가 비어 있으면 서버를 부르지 않는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(detail = detail())
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.confirmReceived("")
        vm.markNotReceived("")
        advanceUntilIdle()

        assertEquals(listOf("get"), repository.calls)
    }

    /**
     * 두 번 눌린 「받았어요」가 각자 재조회를 걸면 **늦게 끝난 쪽의 낡은 응답이 화면을 되돌린다.**
     * 그래서 쓰기는 하나만 돈다.
     */
    @Test
    fun `쓰기가 도는 중에는 두 번째 호출이 나가지 않는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(detail = detail())
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.confirmReceived(SENDER_A)
        vm.confirmReceived(SENDER_B)
        advanceUntilIdle()

        assertEquals(listOf("get", "confirmReceived($SENDER_A)", "get"), repository.calls)
    }

    // ─────────────────── 조회 ───────────────────

    /** 정산이 없으면 **오류가 아니다** — 「시작할 수 있는가」를 방 상세로 한 번 더 묻는다. */
    @Test
    fun `정산이 없으면 방 상세로 시작 가능 여부를 묻는다`() = runTest(mainDispatcher) {
        val rides = mutableListOf<Long>()
        val repository = RecordingRepository(detail = null)
        val vm = FareSplitDetailViewModel(
            repository,
            loadRide = { id ->
                rides += id
                ride()
            },
            partyId = 42L,
        )
        advanceUntilIdle()

        assertEquals(listOf(42L), rides)
        val state = vm.uiState.value as FareSplitDetailViewModel.UiState.Absent
        assertTrue("방장 + 도착 + 2명이면 시작할 수 있다", state.canStart)
    }

    /**
     * 방 상세 조회가 실패하면 **단정하지 않는다** — `canStart = false` 에 이유 null 이다.
     * 실패를 「당신은 시작할 수 없다」로 읽으면 방장이 자기 방을 정산하지 못하는 막다른 화면이 된다.
     */
    @Test
    fun `방 상세를 못 읽으면 시작할 수 없다고 단정하지 않는다`() = runTest(mainDispatcher) {
        val repository = RecordingRepository(detail = null)
        val vm = FareSplitDetailViewModel(repository, failingRideLoad, partyId = 42L)
        advanceUntilIdle()

        val state = vm.uiState.value as FareSplitDetailViewModel.UiState.Absent
        assertEquals(false, state.canStart)
        assertEquals(null, state.blockedReason)
    }
}
