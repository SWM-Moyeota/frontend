package com.moyeota.domain.analytics

import com.moyeota.domain.model.Gender
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

/**
 * 사용자 행동 이벤트를 밖(Mixpanel)으로 보내는 통로.
 *
 * **무엇을 기록할지**는 여기([AnalyticsEvent])서 닫힌 sealed class 로 정하고, **어디로 보낼지**만 app 모듈이
 * [install] 로 꽂는다. domain 은 Mixpanel 을 모른다. 생성자 주입이 아니라 전역인 이유: 열 몇 곳의 ViewModel
 * 생성자와 그만큼의 테스트 fake 를 함께 바꿀 만한 값어치가 없고, `Log` 처럼 **부수효과 없는 진단 출력**이기
 * 때문이다. 기본값이 무동작이라 아무것도 꽂지 않은 단위 테스트는 그대로 통과한다.
 *
 * ### 이벤트를 sealed class 로 닫아 두는 이유
 * 자리마다 문자열을 새로 지으면 같은 행동이 `match_request`·`request_match`·`matchRequested` 로 흩어져
 * Mixpanel 퍼널이 끊긴다. 이름과 프로퍼티 키는 [AnalyticsEvent.name]·[AnalyticsEvent.properties] 한 곳이
 * 결정하고, 대시보드가 그 이름을 보고 있으므로 **바꾸면 대시보드와 iOS 도 함께 고쳐야 한다**(`AnalyticsTest` 가 못 박는다).
 *
 * ### iOS 와 1바이트도 다르면 안 된다
 * 이벤트 이름·프로퍼티 키·값 상수·기록 시점은 `frontend-ios` 의 `Analytics.swift` 와 같다. 두 플랫폼이 같은 이름으로
 * 보내야 Mixpanel 퍼널이 플랫폼을 가리지 않고 합쳐진다.
 *
 * ### 보내면 안 되는 것
 * 실명·휴대폰·이메일·좌표·채팅 본문·방 id·동승자 식별자. 지역은 **시/구 단위**([administrativeArea])까지만,
 * 나이는 **연령대**([ageBand])까지만 싣는다. 사용자 식별자(publicId)는 app 모듈의 리포터가 `identify` 로
 * 따로 다루고, 이벤트 프로퍼티에는 넣지 않는다.
 */
object Analytics {

    fun interface Sink {
        fun track(event: AnalyticsEvent)
    }

    // 설치는 메인 스레드(앱 시작), 기록은 아무 스레드에서나 일어난다.
    private val installedSink = AtomicReference<Sink?>(null)

    /** app 모듈이 앱 시작 때 한 번 꽂는다. 꽂기 전의 기록은 조용히 버린다. */
    fun install(sink: Sink) {
        installedSink.set(sink)
    }

    /** 테스트끼리 설치가 새지 않게 비운다. */
    fun reset() {
        installedSink.set(null)
    }

    /**
     * 이벤트를 기록한다. **절대 던지지 않고, 호출부를 기다리게 하지 않는다** — 분석은 부가 기능이라
     * 여기서 터지거나 느려지면 사용자가 하려던 일이 막힌다.
     */
    fun track(event: AnalyticsEvent) {
        val sink = installedSink.get() ?: return
        try {
            sink.track(event)
        } catch (_: Exception) {
            // 리포터의 실패는 사용자 흐름과 무관하다 — 삼킨다
        }
    }
}

/**
 * 이벤트 프로퍼티 값. Mixpanel 이 받는 원시 타입만 둔다 — 중첩 구조를 허용하면 모델 통째로 실어 보내는
 * 자리가 생기고, 그 안에 개인정보가 섞여 들어가는 걸 타입이 막지 못한다.
 */
sealed interface AnalyticsValue {
    data class Str(val value: String) : AnalyticsValue
    data class Int(val value: kotlin.Int) : AnalyticsValue
    data class Bool(val value: Boolean) : AnalyticsValue
}

/** 매칭 신청의 두 길. 16 도착지 확인에서 **방을 만드는** 쪽과 20 합류 확인에서 **남의 방에 들어가는** 쪽. */
enum class MatchRequestMethod(val rawValue: String) {
    CREATE("create"),
    JOIN("join"),
}

/**
 * 앱이 기록하는 모든 이벤트. 네이밍은 `동작_대상` snake_case — `view_screen`, `click_create_party`.
 *
 * 핵심 가치 퍼널은 **가입 → 매칭 신청 → 매칭 완료**다:
 * `complete_signup` → `request_match` → `complete_match`. Mixpanel 퍼널·리텐션 보고서는 이 세 이름을 본다.
 *
 * | 종류 | 이벤트 | 어디서 |
 * |---|---|---|
 * | View | `view_screen` | 내비게이션 스택의 맨 위가 바뀔 때(`MainNavGraph`) |
 * | Click | `click_create_party`·`click_join_party` | 버튼을 눌러 요청이 **나가는 순간** |
 * | Action | 나머지 | 서버가 성공을 돌려준 **뒤** — 눌렀다와 됐다를 가른다 |
 */
sealed class AnalyticsEvent {

    /**
     * 화면이 맨 위에 올라왔다. [screen] 은 라우트 **패턴**(예: `matching/waiting`, `chat/room/{roomId}`) —
     * 인자를 채우기 전 문자열이라 채팅방 id 같은 값이 섞이지 않는다.
     */
    data class ScreenViewed(val screen: String) : AnalyticsEvent()

    /**
     * 가입 + 자동 로그인까지 끝났다(12 매너 서약). 성별·연령대는 가입 폼의 값에서 **여기서 한 번만** 나온다 —
     * 서버가 프로필 조회로 돌려주지 않아 나중에는 알 수 없다. [ageBand] 는 [ageBand] 함수의 결과다.
     */
    data class SignupCompleted(val gender: Gender, val ageBand: String) : AnalyticsEvent()

    /** 아이디 로그인 성공(04a). */
    data object LoginCompleted : AnalyticsEvent()

    /** 16 「방 만들기」를 눌러 요청이 나갔다. */
    data class PartyCreateClicked(val capacity: Int) : AnalyticsEvent()

    /** 20 「합류하기」를 눌러 요청이 나갔다. */
    data object PartyJoinClicked : AnalyticsEvent()

    /**
     * 매칭 신청이 서버에 받아들여졌다 — 방을 만들었거나 남의 방에 들어갔다. 핵심 퍼널 2단계.
     *
     * [originArea]·[destinationArea] 는 시/구 단위([administrativeArea]). 합류 쪽은 방 상세에 주소가 없어
     * null 이다. [estimatedFare] 는 서버가 방 생성 응답에 실어 준 경로 전체 예상 요금(원)이다.
     * [capacity] 가 null 인 건 합류 응답에 정원이 없고 상세도 못 읽은 드문 경우뿐이다 — 0 으로 지어내지 않는다.
     */
    data class MatchRequested(
        val method: MatchRequestMethod,
        val capacity: Int?,
        val originArea: String?,
        val destinationArea: String?,
        val estimatedFare: Int?,
    ) : AnalyticsEvent()

    /**
     * 매칭 신청이 거절됐다. [reason] 은 서버 코드가 아니라 도메인 예외의 판정을 옮긴 짧은 상수다
     * ([MatchFailureReason] 참고). 「왜 신청이 안 되는가」를 세는 자리다.
     */
    data class MatchRequestFailed(val method: MatchRequestMethod, val reason: String) : AnalyticsEvent()

    /**
     * 정원이 찼다 — 「같이 타면 택시비가 싸진다」를 사용자가 **경험하는 순간**. 핵심 퍼널 3단계.
     *
     * 21 매칭 대기가 방 상태를 정원 충족(MATCHED) 이상으로 처음 보았을 때 기록한다. 그 화면에 들어온 뒤
     * 얼마나 기다렸는지가 [waitedSeconds] 다(신청 시점이 아니라 화면 진입 기준).
     */
    data class MatchCompleted(val capacity: Int, val memberCount: Int, val waitedSeconds: Int) : AnalyticsEvent()

    /** 21 「그만 찾기」·「방 나가기」가 성공했다 — 퍼널에서 빠져나간 사람과 그때의 인원. */
    data class MatchCanceled(val capacity: Int, val memberCount: Int) : AnalyticsEvent()

    /** 동승 모드의 「합승 완료」가 성공했다(방을 닫음). */
    data class PartyFinished(val memberCount: Int) : AnalyticsEvent()

    /** 회원 탈퇴가 204 를 받았다. (Android 는 아직 탈퇴 API 가 배선되지 않아 기록 지점이 없다 — 계약만 둔다.) */
    data object AccountDeleted : AnalyticsEvent()

    /** Mixpanel 이벤트 이름. **대시보드와 iOS 가 이 문자열을 본다** — 바꾸면 양쪽을 함께 고친다. */
    val name: String
        get() = when (this) {
            is ScreenViewed -> "view_screen"
            is SignupCompleted -> "complete_signup"
            LoginCompleted -> "complete_login"
            is PartyCreateClicked -> "click_create_party"
            PartyJoinClicked -> "click_join_party"
            is MatchRequested -> "request_match"
            is MatchRequestFailed -> "fail_match_request"
            is MatchCompleted -> "complete_match"
            is MatchCanceled -> "cancel_match"
            is PartyFinished -> "finish_party"
            AccountDeleted -> "delete_account"
        }

    /**
     * 이벤트 프로퍼티. 키는 snake_case 로 고정한다. null 인 값은 **키를 빼고** 보낸다 — Mixpanel 에서
     * 「값 없음」과 「빈 문자열」이 다른 집계가 되지 않게.
     */
    val properties: Map<String, AnalyticsValue>
        get() = when (this) {
            is ScreenViewed -> mapOf("screen" to AnalyticsValue.Str(screen))
            is SignupCompleted -> mapOf(
                "gender" to AnalyticsValue.Str(gender.analyticsValue),
                "age_band" to AnalyticsValue.Str(ageBand),
            )
            LoginCompleted, PartyJoinClicked, AccountDeleted -> emptyMap()
            is PartyCreateClicked -> mapOf("capacity" to AnalyticsValue.Int(capacity))
            is MatchRequested -> compact(
                "method" to AnalyticsValue.Str(method.rawValue),
                "capacity" to capacity?.let(AnalyticsValue::Int),
                "origin_area" to originArea?.let(AnalyticsValue::Str),
                "destination_area" to destinationArea?.let(AnalyticsValue::Str),
                "estimated_fare" to estimatedFare?.let(AnalyticsValue::Int),
            )
            is MatchRequestFailed -> mapOf(
                "method" to AnalyticsValue.Str(method.rawValue),
                "reason" to AnalyticsValue.Str(reason),
            )
            is MatchCompleted -> mapOf(
                "capacity" to AnalyticsValue.Int(capacity),
                "member_count" to AnalyticsValue.Int(memberCount),
                "waited_seconds" to AnalyticsValue.Int(waitedSeconds),
            )
            is MatchCanceled -> mapOf(
                "capacity" to AnalyticsValue.Int(capacity),
                "member_count" to AnalyticsValue.Int(memberCount),
            )
            is PartyFinished -> mapOf("member_count" to AnalyticsValue.Int(memberCount))
        }

    private fun compact(vararg pairs: Pair<String, AnalyticsValue?>): Map<String, AnalyticsValue> =
        pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
}

/** [Gender] → 이벤트·People 프로퍼티 값. iOS 와 같은 소문자 상수(`male`/`female`/`undisclosed`). */
val Gender.analyticsValue: String
    get() = when (this) {
        Gender.MALE -> "male"
        Gender.FEMALE -> "female"
        Gender.UNDISCLOSED -> "undisclosed"
    }

/**
 * `fail_match_request.reason` 상수. 서버 코드 문자열 그대로가 아니라 도메인 예외의 판정을 옮긴 것 —
 * 서버가 enum 이름을 바꿔도 보고서가 끊기지 않는다. iOS `openFailureReason`/`joinFailureReason` 과 같은 값.
 */
object MatchFailureReason {
    const val ALREADY_IN_PARTY = "already_in_party"
    const val SAME_DEPARTURE_DESTINATION = "same_departure_destination"
    const val OUT_OF_SERVICE_AREA = "out_of_service_area"
    const val INVALID_CAPACITY = "invalid_capacity"
    const val ROUTE_UNAVAILABLE = "route_unavailable"
    const val PARTY_FULL = "party_full"
    const val PARTY_CLOSED = "party_closed"
    const val PARTY_NOT_FOUND = "party_not_found"
    const val ALREADY_JOINED = "already_joined"
    const val NETWORK = "network"
    const val UNKNOWN = "unknown"
}

/**
 * 도로명 주소에서 **시/도 + 시/군/구** 까지만 잘라 낸다. 그 아래(동·도로명·번지)는 버린다.
 *
 *     "경기 안양시 만안구 성결대학로 53" → "경기 안양시 만안구"
 *     "서울 관악구 남부순환로 1820"      → "서울 관악구"
 *     "부산대로"                        → null (주소가 아니다)
 *
 * 「관악구 출발이 많다」 같은 지역 분석에 쓸 만큼은 남기고, 사람을 특정할 만큼은 남기지 않는다 — 번지까지
 * 실으면 출발지가 곧 집 주소가 된다. 서버 `PlaceSearchResponse.roadName`·역지오코딩 `address` 가 전체
 * 도로명 주소라서 첫 토큰이 시/도, 그다음이 시/군/구다(광역시 산하 구, 도 산하 시의 구 모두 포함).
 *
 * 판정이 서지 않으면 null 이다 — 지어내지 않는다. 첫 토큰이 시·도로 끝나지 않으면 주소가 아니라고 본다.
 */
fun administrativeArea(address: String): String? {
    val tokens = address.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val province = tokens.firstOrNull()?.takeIf(::isProvinceToken) ?: return null
    val parts = mutableListOf(province)
    for (token in tokens.drop(1)) {
        if (!isDistrictToken(token)) break
        parts += token
        // 「경기 안양시 만안구」처럼 시 아래 구까지 두 단계. 그 아래 읍·면·동은 버린다.
        if (parts.size == 3) break
    }
    // 시/도만 있고 시/군/구가 없으면 지역이라 부를 것이 없다(세종은 서버 주소도 「세종특별자치시 …동」 꼴이다).
    if (parts.size < 2) return null
    return parts.joinToString(" ")
}

private val SHORT_PROVINCES = setOf(
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
)

private fun isProvinceToken(token: String): Boolean {
    // 축약형(서울·경기·부산…)과 정식 명칭(서울특별시·경기도·부산광역시…) 둘 다 온다.
    if (token in SHORT_PROVINCES) return true
    return token.endsWith("특별시") || token.endsWith("광역시") || token.endsWith("특별자치시") ||
        token.endsWith("특별자치도") || token.endsWith("도")
}

// 한 글자짜리 「구」·「시」는 지명이 아니다.
private fun isDistrictToken(token: String): Boolean =
    token.length >= 2 && (token.endsWith("구") || token.endsWith("시") || token.endsWith("군"))

/**
 * 생년월일을 **연령대**로 접는다 — `"10s"`·`"20s"`·…·`"60s+"`. 만 나이 기준이고 생일 당일부터 다음 나이다.
 *
 * 생년월일 자체를 보내지 않는 이유: 생년월일 + 성별 + 출발 지역이면 사람이 좁혀진다. 「20대 남성이 많다」를
 * 알기에는 연령대로 충분하다. [today] 를 받는 건 경계값(생일 전날·당일)을 검증하기 위해서다.
 */
fun ageBand(birthDate: LocalDate, today: LocalDate = LocalDate.now()): String {
    var age = today.year - birthDate.year
    if (today.isBefore(birthDate.plusYears(age.toLong()))) age -= 1
    return when {
        age < 20 -> "10s"
        age >= 60 -> "60s+"
        else -> "${age / 10 * 10}s"
    }
}
