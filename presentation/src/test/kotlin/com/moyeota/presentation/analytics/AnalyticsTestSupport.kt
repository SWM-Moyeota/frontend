package com.moyeota.presentation.analytics

import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.MatchCandidate
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.NewUser
import com.moyeota.domain.model.PartyDetail
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.ProfileImage
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.model.RouteEstimate
import com.moyeota.domain.model.User
import com.moyeota.domain.model.UserProfileInfo
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.domain.repository.RideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * ViewModel 분석 테스트 공통 규칙 — iOS 의 TaskLocal 녹음기와 같은 역할.
 *
 * - `viewModelScope` 가 쓰는 Main 을 [UnconfinedTestDispatcher] 로 바꾼다(가짜 저장소가 매달리지 않으니 즉시 끝난다).
 *   시간은 흐르지 않으므로 21 의 폴백 폴링 루프(`delay`)는 테스트 동안 한 번도 돌지 않는다.
 * - [Analytics.install] 로 녹음기를 꽂고, 끝나면 [Analytics.reset] 으로 비워 테스트끼리 새지 않게 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsRecorderRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    val recorded = mutableListOf<AnalyticsEvent>()

    fun names(): List<String> = recorded.map { it.name }

    inline fun <reified T : AnalyticsEvent> only(): List<T> = recorded.filterIsInstance<T>()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
        recorded.clear()
        Analytics.install { recorded += it }
    }

    override fun finished(description: Description) {
        Analytics.reset()
        Dispatchers.resetMain()
    }
}

fun testMember(index: Int, isMe: Boolean = false) =
    User(id = "u$index", nickname = "승객$index", verifiedLabel = "", rating = 0.0, rideCount = 0, isMe = isMe)

fun testRide(
    status: RideStatus = RideStatus.RECRUITING,
    capacity: Int = 3,
    members: Int = 1,
    estimatedFare: Int? = null,
) = Ride(
    id = "1", origin = "부산대", destination = "서면", departureLabel = "지금 출발",
    capacity = capacity,
    members = List(members) { testMember(it, isMe = it == 0) },
    farePerPerson = 0, totalFare = 0, status = status, estimatedFare = estimatedFare,
)

/** 필요한 메서드만 람다로 갈아 끼우는 가짜 저장소. 테스트가 쓰지 않는 메서드는 부르면 터진다. */
open class FakeRideRepository : RideRepository {
    var detail: () -> Ride = { error("getPartyDetail 미설정") }
    var create: (NewParty) -> Ride = { error("createParty 미설정") }
    var join: (Long) -> PartyStatus = { error("joinParty 미설정") }
    var leave: (Long) -> Unit = {}
    var finish: (Long) -> Unit = {}
    val events = MutableSharedFlow<PartyEvent>(extraBufferCapacity = 16)

    override suspend fun getPartyDetail(partyId: Long): Ride = detail()
    override suspend fun getPartyDetailWithVersion(partyId: Long): PartyDetail = PartyDetail(detail(), version = null)
    override suspend fun createParty(request: NewParty): Ride = create(request)
    override suspend fun joinParty(partyId: Long): PartyStatus = join(partyId)
    override suspend fun leaveParty(partyId: Long) = leave(partyId)
    override suspend fun finishParty(partyId: Long) = finish(partyId)
    override fun observePartyEvents(partyId: Long): Flow<PartyEvent> = events

    override fun getNearbyParties(): List<Ride> = TODO()
    override suspend fun getParties(): List<Ride> = TODO()
    override suspend fun getPartiesWithin(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Ride> = TODO()
    override suspend fun findRealtimeCandidates(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): List<MatchCandidate> = TODO()
    override suspend fun getAssignedDriver(partyId: Long): AssignedDriver = TODO()
    override suspend fun reportEmergency(partyId: Long?): Long = TODO()
    override suspend fun confirmEmergencyCall(called: Boolean) = TODO()
    override suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate = TODO()
}

class FakeAuthRepository : AuthRepository {
    var registerCalls = 0
    var loginCalls = 0
    var onRegister: (NewUser) -> String = { "uuid" }
    var onLogin: (String, String) -> String = { _, _ -> "uuid" }
    var onDeleteAccount: (String) -> Unit = {}

    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unauthenticated)
    override suspend fun register(newUser: NewUser): String { registerCalls++; return onRegister(newUser) }
    override suspend fun login(loginId: String, password: String): String { loginCalls++; return onLogin(loginId, password) }
    override suspend fun deleteAccount(password: String) = onDeleteAccount(password)

    override suspend fun sendPhoneVerificationCode(phoneNumber: String) = TODO()
    override suspend fun verifyPhoneCode(phoneNumber: String, code: String): String = TODO()
    override suspend fun isNicknameTaken(nickname: String): Boolean = TODO()
    override suspend fun isPhoneRegistered(phoneNumber: String): Boolean = TODO()
    override suspend fun updateProfile(nickname: String?, imageKey: String?) = TODO()
    override suspend fun getProfileImages(): List<ProfileImage> = TODO()
    override suspend fun resetProfileImage() = TODO()
    override suspend fun logout() = TODO()
    override suspend fun getMyProfile(): UserProfileInfo = TODO()
}
