package com.moyeota.app

import android.content.Context
import android.util.Log
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.moyeota.domain.analytics.Analytics
import com.moyeota.domain.analytics.AnalyticsEvent
import com.moyeota.domain.analytics.AnalyticsValue
import com.moyeota.domain.analytics.analyticsValue
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * [Analytics] 가 올린 이벤트를 Mixpanel 로 보낸다. domain 은 「무엇을」 정하고, 여기서 「어디로」만 꽂는다.
 * **Mixpanel 을 아는 파일은 이것 하나다.** (iOS `MixpanelAnalyticsReporter.swift` 와 같은 규칙.)
 *
 * ### 토큰이 없으면 아무것도 하지 않는다
 * `BuildConfig.MIXPANEL_TOKEN`(← local.properties / 환경변수 `MIXPANEL_TOKEN`)이 비어 있으면 SDK 를
 * 초기화하지 않고 sink 도 꽂지 않는다. 그러면 [Analytics.track] 은 무동작이다 — 토큰 없이 빌드한 앱
 * (fork·CI·새 팀원)이 깨지거나 남의 프로젝트로 이벤트를 쏘는 일이 없다.
 *
 * ### 사용자 식별 — `identify` 는 세션 상태를 따라간다
 * 로그인 전에는 SDK 가 만든 기기 익명 id, 로그인하면 서버 `publicId`(uuid) 로 `identify`, 로그아웃·탈퇴면 `reset`.
 * Mixpanel 은 익명 id 와 publicId 를 자동으로 합치므로(ID merge) 가입 전 온보딩 화면 조회가 가입 뒤의 같은 사람으로
 * 이어진다 — 「가입 당일 매칭 완료율」·리텐션이 성립하는 전제다. 어디서 로그인·로그아웃이 일어났든
 * [UserSession.authState] 하나만 보면 되므로 화면 코드에는 식별 호출이 없다.
 *
 * SDK 8.12.0 의 `identify(id)` 는 `identify(id, markAsUserId = true)` 로 위임되고, 그 안에서 People 도 같은 id 로
 * 식별한다(`PeopleImpl.identify_people`, 바이트코드로 확인) — 그래서 `people.identify` 를 따로 부르지 않는다.
 *
 * ### 보내는 값
 * 이벤트 프로퍼티는 [AnalyticsEvent.properties] 가 전부다 — 여기서 덧붙이는 건 빌드 종류(`build`) 슈퍼 프로퍼티뿐이다.
 * 가입 때의 성별·연령대는 사람 프로필(People)에도 올린다 — 「20대 남성이 많다」를 코호트로 묶어 보려면 이벤트가 아니라
 * 프로필에 있어야 한다. `identify` 전에 올라온 프로필 값은 SDK 가 쥐고 있다가 식별되면 보낸다.
 *
 * 자동 이벤트(`trackAutomaticEvents`)는 끈다 — 앱 실행·세션 같은 SDK 기본 이벤트는 우리 퍼널 이름과 섞여
 * 대시보드만 어지럽히고, 화면 조회는 `MainNavGraph` 가 `view_screen` 으로 직접 보낸다. (8.12.0 의 Autocapture 는
 * `MixpanelOptions` 로 켜야만 동작하고 이 `getInstance` 오버로드는 끈 채로 둔다.)
 */
object MixpanelAnalyticsReporter {

    private const val TAG = "MoyeotaAnalytics"

    /** `identify`·`reset` 을 세션에 맞추는 구독. 프로세스 수명만큼 산다. */
    private var sessionObserver: Job? = null

    /**
     * [Analytics] 에 sink 를 꽂고 세션을 따라 사용자를 식별한다. `MoyeotaApplication` 이 컨테이너를 조립한 뒤 한 번 부른다.
     *
     * @return SDK 를 켰는가. 토큰이 비어 있으면 false 다.
     */
    fun install(
        context: Context,
        token: String,
        session: UserSession,
        debuggable: Boolean,
        scope: CoroutineScope,
    ): Boolean {
        if (token.isBlank()) {
            Log.i(TAG, "MIXPANEL_TOKEN 이 비어 있다 — 이벤트를 보내지 않는다")
            return false
        }
        val mixpanel = MixpanelAPI.getInstance(context, token, /* trackAutomaticEvents = */ false)
        // logcat(태그 MixpanelAPI.*)에 보낸 이벤트·플러시가 찍힌다 — 「이벤트가 심어졌나」를 콘솔까지 가지 않고 확인하는 길.
        // release 에서는 끈다. (MPConfig.setEnableLogging → MPLog 레벨. 매니페스트 meta-data 와 같은 스위치다.)
        mixpanel.setEnableLogging(debuggable)
        mixpanel.registerSuperProperties(JSONObject().put("build", if (debuggable) "debug" else "release"))

        Analytics.install { event ->
            // track 은 SDK 안의 워커 스레드로 넘어가므로 어느 스레드에서 불러도 되고 호출부를 기다리게 하지 않는다.
            mixpanel.track(event.name, event.properties.toJson())
            if (event is AnalyticsEvent.SignupCompleted) {
                mixpanel.people.set(
                    JSONObject()
                        .put("gender", event.gender.analyticsValue)
                        .put("age_band", event.ageBand),
                )
            }
        }

        sessionObserver?.cancel()
        // **메인 디스패처에서 구독한다.** 화면(ViewModel, Main)이 탈퇴·로그아웃 API 성공 뒤 같은 코루틴 안에서
        // `delete_account` 를 track 하는데, 세션 전이(Unauthenticated)를 IO 스레드가 받으면 그 reset 이 track 보다
        // 먼저 SDK 큐에 들어가 탈퇴 이벤트가 익명 id 로 찍힐 수 있다. Main 으로 두면 reset 은 화면 코루틴이
        // 손을 놓은 **뒤**에야 처리된다 — iOS 리포터가 @MainActor Task 로 구독하는 것과 같은 보장이다.
        sessionObserver = scope.launch(Dispatchers.Main) {
            var identified = false
            session.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        mixpanel.identify(state.userUuid)
                        identified = true
                    }
                    AuthState.Unauthenticated -> {
                        // 앱 시작 때의 「미로그인」에는 reset 하지 않는다 — 익명 id 가 실행마다 바뀌면 가입 전 행동이 끊긴다.
                        // 로그아웃·탈퇴·세션 만료로 **로그인 상태에서 빠져나온 때**만 끊는다.
                        if (identified) {
                            mixpanel.reset()
                            identified = false
                        }
                    }
                    AuthState.Unknown -> Unit
                }
            }
        }
        return true
    }

    private fun Map<String, AnalyticsValue>.toJson(): JSONObject = JSONObject().also { json ->
        for ((key, value) in this) {
            when (value) {
                is AnalyticsValue.Str -> json.put(key, value.value)
                is AnalyticsValue.Int -> json.put(key, value.value)
                is AnalyticsValue.Bool -> json.put(key, value.value)
            }
        }
    }
}
