package com.moyeota.app

import android.content.Context
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.TrackingOptions
import com.amplitude.common.Logger
import com.amplitude.core.ServerZone
import com.amplitude.core.events.Identify

/**
 * 이벤트를 Amplitude 로 보내는 길. **Amplitude 를 아는 파일은 이것 하나다.**
 * Mixpanel 과 같은 이벤트 이름·프로퍼티를 그대로 보낸다([AnalyticsReporters]).
 *
 * ### 키가 없으면 아무것도 하지 않는다
 * `BuildConfig.AMPLITUDE_API_KEY`(← local.properties / 환경변수 `AMPLITUDE_API_KEY`)가 비어 있으면 SDK 를
 * 만들지 않고 null 을 돌려준다 — Mixpanel 과 같은 규칙.
 *
 * ### 자동 수집은 전부 끈다 (SDK 1.37.1 소스로 확인)
 * - `autocapture = emptySet()` — 기본값은 `SESSIONS` 라 `session_start`·`session_end` 가 자동으로 찍힌다.
 *   우리 퍼널 이름과 섞이지 않게 끈다. 세션 id 자체는 이벤트에 계속 붙으므로 세션 단위 분석은 그대로 된다.
 *   화면 조회는 `MainNavGraph` 가 `view_screen` 으로 직접 보낸다.
 * - `enableAutocaptureRemoteConfig = false` — 켜 두면 Amplitude 콘솔에서 **원격으로** autocapture(화면·터치)를
 *   켤 수 있다. 무엇을 보낼지는 코드와 처리방침이 정해야 하므로 막는다.
 * - `enableDiagnostics = false` — SDK 자체 진단 전송.
 * - 광고 ID·App Set ID·위경도를 읽지 않는다([TrackingOptions]). 매니페스트에서 `AD_ID` 권한도 지워 둔 상태다.
 *   국가·지역·도시는 서버가 IP 로 추정해 붙인다(Mixpanel 과 같은 수준).
 *
 * ### `build` 프로퍼티
 * Amplitude 에는 슈퍼 프로퍼티가 없어 이벤트마다 `build` 를 덧붙인다 — 보고서에서 `build = release` 로 거르는 건
 * Mixpanel 과 같다.
 */
object AmplitudeAnalyticsReporter {

    private const val TAG = "MoyeotaAnalytics"

    /**
     * @param serverZone `US`(기본) 또는 `EU`. Amplitude 프로젝트를 만든 데이터 리전과 **같아야** 이벤트가 들어간다.
     * @return Amplitude 로 가는 길. 키가 비어 있으면 null 이다.
     */
    fun create(context: Context, apiKey: String, serverZone: String, debuggable: Boolean): AnalyticsDestination? {
        if (apiKey.isBlank()) {
            Log.i(TAG, "AMPLITUDE_API_KEY 가 비어 있다 — Amplitude 로 보내지 않는다")
            return null
        }
        val amplitude = Amplitude(
            Configuration(
                apiKey = apiKey,
                context = context.applicationContext,
                serverZone = if (serverZone.equals("EU", ignoreCase = true)) ServerZone.EU else ServerZone.US,
                autocapture = emptySet(),
                trackingOptions = TrackingOptions().disableAdid().disableAppSetId().disableLatLng(),
                enableAutocaptureRemoteConfig = false,
                enableDiagnostics = false,
            ),
        )
        // logcat(태그 Amplitude)에 큐잉·업로드가 찍힌다. release 에서는 끈다.
        amplitude.logger.logMode = if (debuggable) Logger.LogMode.DEBUG else Logger.LogMode.OFF
        val build = if (debuggable) "debug" else "release"

        return object : AnalyticsDestination {
            override val name = "Amplitude"

            // track 은 SDK 의 코루틴 큐로 넘어가므로 호출부를 기다리게 하지 않는다.
            override fun track(event: String, properties: Map<String, Any>) {
                amplitude.track(event, properties + ("build" to build))
            }

            // 익명 시절 이벤트는 같은 device id 로 묶여 있어 Amplitude 가 이 user id 의 사람으로 합친다.
            override fun identify(userUuid: String) {
                amplitude.setUserId(userUuid)
            }

            override fun setProfile(properties: Map<String, Any>) {
                val identify = Identify()
                for ((key, value) in properties) {
                    when (value) {
                        is String -> identify.set(key, value)
                        is Int -> identify.set(key, value)
                        is Boolean -> identify.set(key, value)
                    }
                }
                amplitude.identify(identify)
            }

            // user id 를 비우고 device id 를 새로 만든다 — 같은 기기의 다음 사람이 앞사람과 합쳐지지 않게.
            override fun reset() {
                amplitude.reset()
            }
        }
    }
}
