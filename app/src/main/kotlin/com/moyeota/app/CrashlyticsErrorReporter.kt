package com.moyeota.app

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.moyeota.domain.diagnostics.Diagnostics
import com.moyeota.domain.diagnostics.FailureSite

/**
 * [Diagnostics] 가 올린 실패를 Crashlytics 의 **non-fatal** 로 보낸다.
 *
 * 앱은 계속 돈다. 사용자가 보는 것은 아무것도 바뀌지 않고, 대시보드에만 한 줄이 남는다.
 *
 * ### 원본 예외를 그대로 넘기지 않는다 — 이게 이 파일의 존재 이유다
 * `recordException(throwable)` 에 원본을 넘기면 **예외 메시지가 그대로 Google 로 간다.**
 * 이 앱에서 가장 흔한 보고 대상이 파싱 실패인데, kotlinx.serialization 의 메시지는
 * 실패한 **응답 본문 조각을 그대로 담는다**(`Unexpected JSON token at offset 42: ...`).
 * 그 본문에는 닉네임·실명·휴대폰번호·좌표·채팅 본문이 들어 있을 수 있다. 한 번 나가면
 * 앱에서 지울 수 없고, 처리방침이 「보내지 않습니다」라고 적은 것과 정면으로 어긋난다.
 *
 * 그래서 [sanitize] 가 **종류와 위치만** 남긴 예외를 새로 만든다.
 * - 메시지: 지점 이름 + 예외 **클래스 이름** 사슬. 값은 하나도 없다.
 * - 스택트레이스: 원본 것을 그대로 옮긴다. 파일·메서드·줄 번호뿐이라 안전하고,
 *   이게 있어야 Crashlytics 가 같은 결함을 한 묶음으로 센다.
 * - cause: **연결하지 않는다.** 원인 사슬을 붙이면 그 메시지들이 같이 따라간다.
 *
 * ### 커스텀 키를 쓰지 않는다
 * `setCustomKey` 는 **세션 전역**이라 동시에 두 건이 보고되면 값이 뒤섞인다. 지점 이름은
 * 메시지 맨 앞에 박아 두므로 키 없이도 대시보드에서 갈린다.
 */
internal object CrashlyticsErrorReporter {

    /** [Diagnostics] 에 sink 를 꽂는다. `MoyeotaApplication.onCreate` 에서 한 번 부른다. */
    fun install() {
        Diagnostics.install { site, throwable ->
            // getInstance() 를 **보고 시점에** 부른다. 기본 인자나 프로퍼티 초기화에 두면
            // Firebase 초기화 실패가 첫 보고가 아니라 앱 시작 지점에서 터진다.
            FirebaseCrashlytics.getInstance().recordException(sanitize(site, throwable))
        }
    }

    /**
     * 값이 하나도 실리지 않은 예외를 만든다. 스택트레이스만 원본에서 가져온다.
     *
     * 사슬은 **세 단계까지만** 본다 — 그 아래는 묶음 판정에 보탬이 없고 메시지만 길어진다.
     */
    private fun sanitize(site: FailureSite, throwable: Throwable): Throwable {
        val chain = generateSequence(throwable) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .joinToString(" <- ") { it.javaClass.name }
        return ReportedFailure("$site · $chain").apply {
            stackTrace = throwable.stackTrace
        }
    }

    private const val MAX_CAUSE_DEPTH = 3
}

/**
 * Crashlytics 로 보내는 **정제된** 실패. 메시지에 지점과 예외 클래스 이름만 들어 있다.
 *
 * 이 타입이 따로 있는 이유: 대시보드에서 「앱이 스스로 처리한 실패」와 「진짜 크래시」가
 * 타입 이름으로 바로 갈린다.
 */
internal class ReportedFailure(message: String) : RuntimeException(message)
