package com.moyeota.app

import android.content.Context
import android.util.Log
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * 이벤트를 PostHog 로 보내는 길. **PostHog 를 아는 파일은 이것 하나다.**
 * Mixpanel 과 같은 이벤트 이름·프로퍼티를 그대로 보낸다([AnalyticsReporters]).
 *
 * ### 키가 없으면 아무것도 하지 않는다
 * `BuildConfig.POSTHOG_API_KEY`(← local.properties / 환경변수 `POSTHOG_API_KEY`, `phc_` 로 시작하는 프로젝트 토큰)가
 * 비어 있으면 SDK 를 켜지 않고 null 을 돌려준다 — Mixpanel 과 같은 규칙.
 *
 * ### 자동 수집은 전부 끈다 (SDK 3.77.1 소스로 확인)
 * PostHog 는 기본값이 「많이 켜짐」이라 하나씩 끈다. 무엇을 보낼지는 `AnalyticsEvent` 와 처리방침이 정한다.
 * - 앱 생명주기(`Application Installed/Opened/Backgrounded`)·딥링크·화면(`$screen`) 자동 이벤트 — 우리 퍼널 이름과
 *   섞인다. 화면 조회는 `MainNavGraph` 가 `view_screen` 으로 직접 보낸다.
 * - 푸시 구독·푸시 열람 수집 — FCM 토큰과 알림 제목·본문이 PostHog 로 간다.
 * - 세션 리플레이 — **화면 녹화**다. 실명·채팅이 그대로 찍히므로 켜려면 처리방침부터 바꿔야 한다.
 * - 피처 플래그 미리 받기·`$feature_flag_called` — 플래그를 쓰지 않는다.
 * 터치·분노 클릭·설문·예외 자동 수집은 기본값이 꺼짐이라 건드리지 않는다.
 *
 * ### 사람 프로필
 * `personProfiles` 는 기본값(IDENTIFIED_ONLY)을 쓴다 — 익명 이벤트는 프로필 없이 쌓이다가 `identify` 때 그 사람으로
 * 합쳐진다. 그래서 프로필 값은 식별된 뒤에만 올린다([AnalyticsReporters] 가 순서를 맞춘다).
 */
object PostHogAnalyticsReporter {

    private const val TAG = "MoyeotaAnalytics"

    /**
     * @param host 수집 주소. US 는 `https://us.i.posthog.com`(기본), EU 는 `https://eu.i.posthog.com`.
     *   PostHog 프로젝트를 만든 리전과 **같아야** 이벤트가 들어간다.
     * @return PostHog 로 가는 길. 키가 비어 있으면 null 이다.
     */
    fun create(context: Context, apiKey: String, host: String, debuggable: Boolean): AnalyticsDestination? {
        if (apiKey.isBlank()) {
            Log.i(TAG, "POSTHOG_API_KEY 가 비어 있다 — PostHog 로 보내지 않는다")
            return null
        }
        val config = PostHogAndroidConfig(projectToken = apiKey, host = host).apply {
            captureApplicationLifecycleEvents = false
            captureDeepLinks = false
            captureScreenViews = false
            capturePushNotificationSubscriptions = false
            capturePushNotificationOpened = false
            sessionReplay = false
            preloadFeatureFlags = false
            sendFeatureFlagEvent = false
            // logcat(태그 PostHog)에 큐잉·플러시가 찍힌다. release 에서는 끈다.
            debug = debuggable
        }
        PostHogAndroid.setup(context.applicationContext, config)
        // `build` 는 이벤트마다 덧붙인다. `PostHog.register("build", …)` 는 **조용히 무시된다** — "build" 가 SDK 내부
        // 저장 키(앱 빌드 번호)라 등록이 막혀 있다(PostHogPreferences.ALL_INTERNAL_KEYS, 에뮬레이터 logcat 으로 확인).
        // 이벤트 프로퍼티로 싣는 건 막지 않는다.
        val build = if (debuggable) "debug" else "release"

        return object : AnalyticsDestination {
            override val name = "PostHog"

            // capture 는 SDK 의 실행기 큐로 넘어가므로 호출부를 기다리게 하지 않는다.
            override fun track(event: String, properties: Map<String, Any>) =
                PostHog.capture(event = event, properties = properties + ("build" to build))

            override fun identify(userUuid: String) = PostHog.identify(distinctId = userUuid)

            override fun setProfile(properties: Map<String, Any>) =
                PostHog.setPersonProperties(userPropertiesToSet = properties)

            override fun reset() = PostHog.reset()
        }
    }
}
