package com.moyeota.app

import android.content.Context
import android.util.Log
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

/**
 * 이벤트를 Mixpanel 로 보내는 길. **Mixpanel 을 아는 파일은 이것 하나다.**
 * (iOS `MixpanelAnalyticsReporter.swift` 와 같은 규칙.) 언제 track·identify·reset 하는지는
 * [AnalyticsReporters] 가 세 도구 공통으로 정한다.
 *
 * ### 토큰이 없으면 아무것도 하지 않는다
 * `BuildConfig.MIXPANEL_TOKEN`(← local.properties / 환경변수 `MIXPANEL_TOKEN`)이 비어 있으면 SDK 를
 * 초기화하지 않고 null 을 돌려준다 — 토큰 없이 빌드한 앱(fork·CI·새 팀원)이 깨지거나 남의 프로젝트로
 * 이벤트를 쏘는 일이 없다.
 *
 * SDK 8.12.0 의 `identify(id)` 는 `identify(id, markAsUserId = true)` 로 위임되고, 그 안에서 People 도 같은 id 로
 * 식별한다(`PeopleImpl.identify_people`, 바이트코드로 확인) — 그래서 `people.identify` 를 따로 부르지 않는다.
 *
 * ### 보내는 값
 * 이벤트 프로퍼티는 `AnalyticsEvent.properties` 가 전부다 — 여기서 덧붙이는 건 빌드 종류(`build`) 슈퍼 프로퍼티뿐이다.
 *
 * 자동 이벤트(`trackAutomaticEvents`)는 끈다 — 앱 실행·세션 같은 SDK 기본 이벤트는 우리 퍼널 이름과 섞여
 * 대시보드만 어지럽히고, 화면 조회는 `MainNavGraph` 가 `view_screen` 으로 직접 보낸다. (8.12.0 의 Autocapture 는
 * `MixpanelOptions` 로 켜야만 동작하고 이 `getInstance` 오버로드는 끈 채로 둔다.)
 */
object MixpanelAnalyticsReporter {

    private const val TAG = "MoyeotaAnalytics"

    /** @return Mixpanel 로 가는 길. 토큰이 비어 있으면 null 이다(SDK 를 켜지 않는다). */
    fun create(context: Context, token: String, debuggable: Boolean): AnalyticsDestination? {
        if (token.isBlank()) {
            Log.i(TAG, "MIXPANEL_TOKEN 이 비어 있다 — Mixpanel 로 보내지 않는다")
            return null
        }
        val mixpanel = MixpanelAPI.getInstance(context, token, /* trackAutomaticEvents = */ false)
        // logcat(태그 MixpanelAPI.*)에 보낸 이벤트·플러시가 찍힌다 — 「이벤트가 심어졌나」를 콘솔까지 가지 않고 확인하는 길.
        // release 에서는 끈다. (MPConfig.setEnableLogging → MPLog 레벨. 매니페스트 meta-data 와 같은 스위치다.)
        mixpanel.setEnableLogging(debuggable)
        mixpanel.registerSuperProperties(JSONObject().put("build", if (debuggable) "debug" else "release"))

        return object : AnalyticsDestination {
            override val name = "Mixpanel"

            // track 은 SDK 안의 워커 스레드로 넘어가므로 어느 스레드에서 불러도 되고 호출부를 기다리게 하지 않는다.
            override fun track(event: String, properties: Map<String, Any>) =
                mixpanel.track(event, JSONObject(properties))

            override fun identify(userUuid: String) = mixpanel.identify(userUuid)

            override fun setProfile(properties: Map<String, Any>) = mixpanel.people.set(JSONObject(properties))

            override fun reset() = mixpanel.reset()
        }
    }
}
