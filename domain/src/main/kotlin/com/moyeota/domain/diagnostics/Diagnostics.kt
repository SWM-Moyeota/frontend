package com.moyeota.domain.diagnostics

import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * 「조용히 삼켜진 실패」를 밖으로 알리는 통로.
 *
 * 이 앱은 **의도적으로 안 죽게** 쓰여 있다. `runCatching {}.getOrNull()`·`quietly {}` 로 실패를
 * 삼키는 자리가 많고, 그건 UX 로는 옳다 — 지하철에서 조회 하나 실패했다고 화면이 죽으면 안 된다.
 * 대가는 **프로덕션에서 아무것도 안 보인다**는 것이다. 전체 소스에 `Log.e` 가 1개뿐이라
 * Play Android vitals 는 깨끗한데 사용자는 앱이 안 된다고 말하는 상태가 될 수 있다.
 *
 * ### 왜 생성자 주입이 아니라 전역인가
 * 이 저장소는 Repository 구현들을 `AppContainer` 로 조립해 넘기는 DI 를 쓴다. 그 선을 따르면
 * Repository·소켓·Authenticator 열 몇 곳의 생성자와 **그만큼의 테스트 fake** 가 함께 바뀐다.
 * 보고용 sink 하나를 꽂으려고 치르기에는 비싼 값이고, 프로덕션 첫 게시 직전에 넓힐 표면이 아니다.
 * `android.util.Log` 와 같은 범주 — **부수효과가 없고 교체가 필요 없는 진단 출력** — 으로 보고
 * 전역에 둔다. 기본값이 무동작이라 단위 테스트는 아무것도 설치하지 않으면 그대로 통과한다.
 *
 * 「무엇을 보고할지」는 여기서 정하고, **어디로 보낼지만** app 모듈이 [install] 로 꽂는다.
 * domain 은 Firebase 를 모른다.
 */
object Diagnostics {

    /** `@Volatile` — 설치는 메인 스레드(Application.onCreate), 보고는 아무 스레드에서나 일어난다. */
    @Volatile
    private var sink: ((FailureSite, Throwable) -> Unit)? = null

    /** app 모듈이 앱 시작 때 한 번 꽂는다. 꽂기 전의 보고는 조용히 버린다. */
    fun install(sink: (FailureSite, Throwable) -> Unit) {
        this.sink = sink
    }

    /** 테스트끼리 설치가 새지 않게 비운다. */
    fun reset() {
        sink = null
    }

    /**
     * 보고할 가치가 있을 때만 보고한다([isWorthReporting]).
     *
     * [httpStatus] 는 **호출부가 알 때만** 넘긴다. domain 은 retrofit 을 모르므로 4xx/5xx 를
     * 스스로 가를 수 없다 — `HttpException.code()` 를 쥐고 있는 data 쪽이 넘겨 준다.
     *
     * **절대 던지지 않는다.** 이 함수는 전부 `catch` 안에서 불리고, 여기서 터지면 삼켜져 있던
     * 실패가 오히려 크래시로 승격된다. 단 [CancellationException] 만은 예외로, 보고하지 않고
     * **그대로 되던진다** — 코루틴 취소를 삼키면 구조적 동시성이 깨진다.
     */
    fun report(site: FailureSite, throwable: Throwable, httpStatus: Int? = null) {
        if (throwable is CancellationException) throw throwable
        if (!isWorthReporting(throwable, httpStatus)) return
        runCatching { sink?.invoke(site, throwable) }
    }
}

/**
 * 보고가 올라오는 지점. 대시보드에서 이 값으로 묶여 보이므로 **개수를 적게 유지한다** —
 * 자리마다 새로 만들면 같은 결함이 여러 줄로 흩어진다.
 */
enum class FailureSite {
    /** 진행 중인 방 복구. 실패하면 사용자가 타고 있던 방으로 못 돌아간다. */
    ACTIVE_PARTY_RESTORE,

    /** 서버가 리프레시 토큰을 **거절**했다 → 강제 로그아웃. 타임아웃은 여기 오지 않는다. */
    TOKEN_REFRESH_REJECTED,

    /** 채팅 소켓이 **연속으로** 재연결에 실패했다. 한 번 실패는 보고하지 않는다. */
    CHAT_SOCKET_RECONNECT,

    /** 즐겨찾기 조회·삭제 실패. */
    FAVORITE_PLACES,

    /** 탑승 기록 조회 실패. */
    RIDE_HISTORY,
}

/**
 * 보고할 가치가 있는 실패인가.
 *
 * 기준을 한곳에 모으는 이유: 자리마다 각자 판단하면 **대시보드가 지하철로 가득 찬다.**
 * 신호가 소음에 묻히면 켜 두나 마나다.
 *
 * | 종류 | 보고 | 왜 |
 * |---|---|---|
 * | [CancellationException] | ✗ | 화면을 벗어나면 **정상적으로** 발생한다. 전부 쓰레기다 |
 * | [IOException] 하위 전부 | ✗ | 타임아웃·DNS·소켓·SSL — 지하철이지 결함이 아니다 |
 * | HTTP 4xx | ✗ | 서버의 정상 판단(401·403·404·409·429)이고 호출부가 이미 처리한다 |
 * | HTTP 5xx | ✓ | 서버 결함이다. 앱이 아는 유일한 창구다 |
 * | 그 밖 | ✓ | 파싱 실패·매퍼의 IllegalArgument/IllegalState·NPE — **앱이나 계약의 결함**이다 |
 *
 * [CancellationException] 을 **가장 먼저** 거른다 — JVM 에서 이 타입은 `IllegalStateException`
 * 하위라, 순서가 밀리면 마지막 `else` 로 새어 전부 보고된다.
 */
internal fun isWorthReporting(throwable: Throwable, httpStatus: Int?): Boolean = when {
    throwable is CancellationException -> false
    throwable is IOException -> false
    httpStatus != null -> httpStatus >= 500
    else -> true
}
