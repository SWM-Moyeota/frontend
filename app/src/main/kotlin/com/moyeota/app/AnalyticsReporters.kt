package com.moyeota.app

import android.util.Log
import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.AnalyticsValue
import com.moyeota.domain.analytics.analyticsValue
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 분석 도구 하나로 가는 길. Mixpanel·Amplitude·PostHog 가 각자 하나씩 구현한다 —
 * **SDK 를 아는 파일은 도구마다 그 리포터 하나뿐이다.**
 *
 * 값은 [AnalyticsValue] 를 푼 원시 타입(String·Int·Boolean)만 온다.
 */
interface AnalyticsDestination {
    /** 로그에 찍을 이름. */
    val name: String

    /** 이벤트 한 건. SDK 안의 큐로 넘기기만 하고 호출부를 기다리게 하지 않는다. */
    fun track(event: String, properties: Map<String, Any>)

    /** 로그인한 사용자(서버 `publicId`)로 식별한다. 기기 익명 id 와의 병합은 각 SDK 가 한다. */
    fun identify(userUuid: String)

    /** 사람 프로필에 값을 올린다. **[identify] 된 뒤에만 불린다**([AnalyticsReporters] 가 보장). */
    fun setProfile(properties: Map<String, Any>)

    /** 로그아웃·탈퇴 — 식별을 끊고 새 익명 id 로 넘어간다. */
    fun reset()
}

/**
 * [Analytics] 가 올린 이벤트를 켜진 도구 **모두에게 같은 이름·같은 프로퍼티로** 나눠 보내고,
 * 세션 상태를 따라 사용자를 식별한다. 도구가 늘어도 「언제 identify·reset 하는가」는 여기 한 곳이 정한다.
 *
 * ### 도구 하나가 고장 나도 나머지는 받는다
 * 호출마다 따로 잡는다 — [Analytics.track] 이 통째로 삼키게 두면 첫 도구의 예외가 뒤의 도구까지 굶긴다.
 *
 * ### 사용자 식별 — `identify` 는 세션 상태를 따라간다
 * 로그인 전에는 SDK 가 만든 기기 익명 id, 로그인하면 서버 `publicId`(uuid) 로 `identify`, 로그아웃·탈퇴면 `reset`.
 * 세 도구 모두 익명 id 와 publicId 를 합치므로 가입 전 온보딩 화면 조회가 가입 뒤의 같은 사람으로 이어진다 —
 * 「가입 당일 매칭 완료율」·리텐션이 성립하는 전제다. 어디서 로그인·로그아웃이 일어났든
 * [UserSession.authState] 하나만 보면 되므로 화면 코드에는 식별 호출이 없다.
 *
 * ### 프로필 값은 식별될 때까지 쥐고 있는다
 * 가입 때의 성별·연령대는 사람 프로필에도 올린다 — 「20대 남성이 많다」를 코호트로 묶으려면 이벤트가 아니라
 * 프로필에 있어야 한다. 그런데 `complete_signup` 은 화면 코루틴에서, `identify` 는 아래 구독에서 일어나 **순서가
 * 정해져 있지 않다.** 익명 상태에서 프로필을 올리면 PostHog 는 버리므로(personProfiles = IDENTIFIED_ONLY),
 * 식별 전에 온 값은 쥐고 있다가 `identify` 직후에 보낸다.
 */
object AnalyticsReporters {

    private const val TAG = "MoyeotaAnalytics"

    /** `identify`·`reset` 을 세션에 맞추는 구독. 프로세스 수명만큼 산다. */
    private var sessionObserver: Job? = null

    /**
     * [Analytics] 에 sink 를 꽂고 세션 구독을 시작한다. `MoyeotaApplication` 이 컨테이너를 조립한 뒤 한 번 부른다.
     * [destinations] 가 비어 있으면(키가 하나도 없는 빌드) 아무것도 꽂지 않아 [Analytics.track] 은 무동작이다.
     *
     * @param observeOn 세션 구독이 도는 곳. 테스트만 바꾼다 — 운영은 반드시 메인이다(아래 주석).
     */
    fun install(
        destinations: List<AnalyticsDestination>,
        session: UserSession,
        scope: CoroutineScope,
        observeOn: CoroutineContext = Dispatchers.Main,
    ) {
        sessionObserver?.cancel()
        sessionObserver = null
        if (destinations.isEmpty()) return

        val lock = Any()
        var identified = false
        var pendingProfile: Map<String, Any> = emptyMap()

        fun each(action: String, block: (AnalyticsDestination) -> Unit) {
            for (destination in destinations) {
                try {
                    block(destination)
                } catch (e: Exception) {
                    // 값을 찍지 않는다 — 예외 메시지에 프로퍼티가 섞여 나올 수 있다.
                    warn("${destination.name} $action 실패: ${e.javaClass.simpleName}")
                }
            }
        }

        Analytics.install { event ->
            val properties = event.properties.toPlain()
            each("track") { it.track(event.name, properties) }
            if (event is AnalyticsEvent.SignupCompleted) {
                val profile = mapOf<String, Any>(
                    "gender" to event.gender.analyticsValue,
                    "age_band" to event.ageBand,
                )
                val sendNow = synchronized(lock) {
                    if (!identified) pendingProfile = pendingProfile + profile
                    identified
                }
                if (sendNow) each("setProfile") { it.setProfile(profile) }
            }
        }

        // **메인 디스패처에서 구독한다.** 화면(ViewModel, Main)이 탈퇴·로그아웃 API 성공 뒤 같은 코루틴 안에서
        // `delete_account` 를 track 하는데, 세션 전이(Unauthenticated)를 IO 스레드가 받으면 그 reset 이 track 보다
        // 먼저 SDK 큐에 들어가 탈퇴 이벤트가 익명 id 로 찍힐 수 있다. Main 으로 두면 reset 은 화면 코루틴이
        // 손을 놓은 **뒤**에야 처리된다 — iOS 리포터가 @MainActor Task 로 구독하는 것과 같은 보장이다.
        sessionObserver = scope.launch(observeOn) {
            session.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        each("identify") { it.identify(state.userUuid) }
                        val profile = synchronized(lock) {
                            identified = true
                            pendingProfile.also { pendingProfile = emptyMap() }
                        }
                        if (profile.isNotEmpty()) each("setProfile") { it.setProfile(profile) }
                    }
                    AuthState.Unauthenticated -> {
                        // 앱 시작 때의 「미로그인」에는 reset 하지 않는다 — 익명 id 가 실행마다 바뀌면 가입 전 행동이 끊긴다.
                        // 로그아웃·탈퇴·세션 만료로 **로그인 상태에서 빠져나온 때**만 끊는다.
                        val wasIdentified = synchronized(lock) {
                            identified.also {
                                identified = false
                                // 식별되지 못한 채 남은 값이 다음 사람의 프로필에 붙으면 안 된다.
                                pendingProfile = emptyMap()
                            }
                        }
                        if (wasIdentified) each("reset") { it.reset() }
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
    }

    private fun Map<String, AnalyticsValue>.toPlain(): Map<String, Any> = mapValues { (_, value) ->
        when (value) {
            is AnalyticsValue.Str -> value.value
            is AnalyticsValue.Int -> value.value
            is AnalyticsValue.Bool -> value.value
        }
    }

    // android.util.Log 는 JVM 단위 테스트에서 스텁이라 던진다 — 진단 출력이 흐름을 깨면 안 된다.
    private fun warn(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
