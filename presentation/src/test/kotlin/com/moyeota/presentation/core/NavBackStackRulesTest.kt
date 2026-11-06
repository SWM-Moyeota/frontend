package com.moyeota.presentation.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백스택·뒤로가기·탈퇴 안내에 대해 **`MainNavGraph` 가 내린 결정**을 못 박는다.
 *
 * 2026-10-28 결함 묶음이 출발점이다(아래 첫 두 줄은 25 배차·26 운행이 있던 시절 이야기다 —
 * 그 두 화면은 기사·배차와 함께 사라졌고, 남은 진행 화면은 21 매칭 대기 하나다):
 * - 진행 화면이 `resetTo`(목적지 1개 스택)로 진입해, 뒤로가기가 그 한 장을 pop 해 **빈 화면**이 됐다.
 * - 진행 화면의 **시스템** 뒤로가기(버튼·제스처)가 NavController 기본 pop 이라 화면이 정한 규칙을
 *   전부 우회했다 — 21 에서 누르면 15 목적지 검색으로 떨어지고, 거기서 방을 또 만들면 서버가 409.
 * - 홈이 스택 바닥이 아니게 되면 하단탭이 「교체」가 아니라 「쌓기」가 됐다.
 * - 홈 탭 복귀만 `saveState` 가 없어 지도 카메라·스크롤이 홈 경유에서만 초기화됐다.
 * - 가입 초안이 `remember` 라 회전하면 12 매너 서약에서 가입을 마칠 수 없었다.
 * - 탈퇴 플래그가 "요청을 보냈다"에 섰던 탓에, 401 로 끝난 탈퇴에도 「탈퇴가 처리됐어요」가 떴다.
 *
 * **이 테스트가 덮지 못하는 것**: 백스택·`popUpTo`·`BackHandler` 는 실행해 봐야 알 수 있는데
 * 이 모듈에는 Robolectric·compose-ui-test 가 없다(`presentation/build.gradle.kts` 의
 * testImplementation 은 junit 하나다. [ConfigChangeStateTest] 가 같은 한계를 먼저 적었다).
 * 그래서 여기서 보는 것은 **소스에 적힌 결정**이다 — 어떤 상태가 saveable 로 선언돼 있고, 어느
 * 목적지가 뒤로가기를 직접 받고, 어떤 플래그가 안내의 근거인가. 실제 스택 모양은 실기(에뮬레이터
 * 에서 21 시스템 뒤로가기 · 회전 · 탭 왕복)로만 확인된다.
 *
 * 소스를 문자열로 읽는 테스트라 서식을 크게 바꾸면 깨질 수 있다. 깨지면 **먼저 결정이 그대로인지
 * 확인하고** 정규식을 고쳐라 — 결정을 되돌리면서 테스트를 지우는 것이 이 테스트가 막는 일이다.
 * 더 나은 방법(테스트 의존성 추가)은 이 작업 범위 밖이라 보고서에 제안으로만 남겼다.
 */
class NavBackStackRulesTest {

    /**
     * `presentation/src/main/kotlin/com/moyeota/presentation` 을 찾는다.
     * [ConfigChangeStateTest] 와 같은 방식 — 작업 디렉터리에 기대지 않고 상위로 올라가며 본다.
     */
    private val sourceRoot: File by lazy {
        val relative = "src/main/kotlin/com/moyeota/presentation"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, relative).takeIf { it.isDirectory }?.let { return@lazy it }
            File(dir, "presentation/$relative").takeIf { it.isDirectory }?.let { return@lazy it }
            dir = dir.parentFile
        }
        error("presentation 소스를 찾지 못했다 (cwd=${File(".").absolutePath})")
    }

    private fun source(path: String): String {
        val file = File(sourceRoot, path)
        assertTrue("소스 파일이 없다: $path", file.isFile)
        return file.readText()
    }

    private fun navGraph(): String = source("core/MainNavGraph.kt")

    /**
     * `composable(Routes.<route>)` 하나의 블록만 잘라낸다 — 다음 `composable(` 선언이 경계다.
     * 중괄호를 세는 대신 이렇게 자르는 이유: 목적지 선언이 파일에서 순서대로 나열돼 있어 충분하고,
     * 정규식으로 균형 잡힌 괄호를 세는 쪽이 더 깨지기 쉽다.
     */
    private fun destinationBlock(source: String, route: String): String {
        val marker = "composable(Routes.$route)"
        assertTrue("$marker 선언이 없다", source.contains(marker))
        return source.substringAfter(marker).substringBefore("composable(")
    }

    // ─────────────── 1. 구성 변경에서 살아남아야 하는 가입 상태 ───────────────

    /**
     * 가입 초안이 사라지면 12 매너 서약의 「동의하고 가입 완료」가 `toNewUser() == null` 로 떨어져
     * 「가입 정보가 부족해요」에서 더 나아갈 수 없다 — 사용자는 **가입 직전에** 원인 불명의 거절을 본다.
     * 재인증 신호(카운터)는 비교 상대가 ViewModel 이라, 이쪽만 0 으로 돌아가면 이후 신호가
     * 「이미 처리한 번호」로 조용히 버려진다.
     */
    @Test
    fun `가입 초안과 재인증 신호는 구성 변경에서 살아남는다`() {
        val navGraph = navGraph()
        assertTrue(
            "signupDraft 가 rememberSaveable 이 아니다 — 회전하면 12 에서 가입을 마칠 수 없다",
            Regex("""var\s+signupDraft\s+by\s+rememberSaveable""").containsMatchIn(navGraph),
        )
        assertTrue(
            "reverifyPhoneRequest 가 rememberSaveable 이 아니다 — 회전 뒤 재인증 신호가 버려진다",
            Regex("""var\s+reverifyPhoneRequest\s+by\s+rememberSaveable""").containsMatchIn(navGraph),
        )
    }

    // ─────────────── 2. 진행 화면의 뒤로가기 ───────────────

    /**
     * 진행 화면은 뒤로가기를 **화면이 정한 규칙**으로 받아야 한다. 헤더 ← 만 배선하면 시스템
     * 뒤로가기가 기본 pop 으로 빠져나가 15·16 방 생성 플로우나 빈 화면으로 떨어진다.
     *
     * 예전에는 셋(21·25·26)을 함께 봤다. 25·26 이 사라져 남은 하나를 보지만 **묻는 것은 같다** —
     * 진행 화면이 늘어나면 이 목록에 더해라.
     */
    @Test
    fun `진행 화면은 시스템 뒤로가기를 직접 받는다`() {
        val navGraph = navGraph()
        listOf("MATCH_WAITING").forEach { route ->
            assertTrue(
                "$route 에 BackHandler 가 없다 — 시스템 뒤로가기가 화면 규칙을 우회한다",
                destinationBlock(navGraph, route).contains("BackHandler"),
            )
        }
    }

    /**
     * 진행 화면은 **홈을 바닥에 남기고** 진입한다 — 목적지 1개 스택에서는 뒤로가기가 그 한 장을
     * pop 해 화면 없는 빈 그래프를 만든다(04a onBack 이 가드를 둔 그 함정).
     *
     * 2026-10-28 에는 26 운행 중이 `resetTo` 로 진입해 실제로 그 빈 화면을 만들었다. 그 화면이
     * 사라진 뒤 **같은 규칙을 받는 자리는 `navigateToStage` 다** — 배너·채팅 헤더·앱 시작 복귀가
     * 모두 거기로 모여 21 로 들어간다. 그래서 묻는 대상을 그쪽으로 옮겼다: 21 을 `resetTo` 로
     * 열지 않고, `popUpTo(HOME) { inclusive = false }` 로 홈을 남기는가.
     */
    @Test
    fun `진행 화면은 스택을 비우고 진입하지 않는다`() {
        val navGraph = navGraph()
        assertFalse(
            "resetTo(Routes.MATCH_WAITING) 가 생겼다 — 뒤로가기가 화면 없는 빈 그래프를 만든다",
            navGraph.contains("resetTo(Routes.MATCH_WAITING)"),
        )
        val stage = navGraph
            .substringAfter("fun navigateToStage(ride: Ride) {")
            .substringBefore("fun openActiveChatRoom")
        assertTrue(
            "navigateToStage 가 홈을 바닥에 남기지 않는다 — 진행 화면 한 장만 남는 스택이 된다",
            Regex("""popUpTo\(Routes\.HOME\)\s*\{\s*inclusive\s*=\s*false\s*\}""")
                .containsMatchIn(stage),
        )
    }

    // ─────────────── 3. 하단탭 ───────────────

    /**
     * 홈 복귀는 pop 이라 **떠나는 탭 엔트리를 폐기한다.** `saveState` 가 없으면 다른 탭 경유
     * (합승→채팅→합승)에서는 보존되는 지도 카메라·스크롤이 홈 경유에서만 초기화된다.
     *
     * 반대로 **무조건** 켜서도 안 된다 — `saveState = true` 인 pop 은 엔트리를 CREATED 로 내려놓고
     * `ViewModelStore.clear` 를 건너뛰므로, 21·22·채팅방에서 홈으로 나오는 길까지 켜면 이미 떠난 방의
     * ViewModel 이 남는다. 그래서 「떠나는 화면이 탭인가」로 가른다.
     */
    @Test
    fun `홈 탭 복귀도 떠나는 탭의 상태를 보관한다`() {
        val navGraph = navGraph()
        val pop = Regex("""popBackStack\(\s*Routes\.HOME,\s*inclusive\s*=\s*false,\s*saveState\s*=\s*(\w+)\s*\)""")
            .find(navGraph)
        assertTrue(
            "홈 복귀 popBackStack 에 saveState 인자가 없다 — 홈 경유에서만 탭 상태가 초기화된다",
            pop != null,
        )
        assertTrue(
            "saveState 가 무조건 true 다 — 진행 화면에서 홈으로 나올 때 그 엔트리의 ViewModel 이 남는다",
            pop?.groupValues?.get(1) != "true",
        )
        assertTrue(
            "떠나는 화면이 탭인지 보지 않는다 — 보관 여부를 가릴 근거가 없다",
            navGraph.contains("in TabRoutes"),
        )
    }

    /** 홈이 스택에 없을 때의 폴백 — 없으면 탭이 교체가 아니라 쌓인다. */
    @Test
    fun `홈이 스택에 없으면 탭 이동이 스택부터 다시 세운다`() {
        val navigateTab = navGraph()
            .substringAfter("fun navigateTab(tab: MoyeotaTab) {")
            .substringBefore("fun navigateToStage")
        assertTrue(
            "navigateTab 이 홈의 존재를 확인하지 않는다 — 홈이 없으면 popUpTo(HOME) 가 무효다",
            navigateTab.contains("currentBackStack"),
        )
        assertTrue(
            "홈이 없을 때 스택을 다시 세우지 않는다",
            navigateTab.contains("resetTo(Routes.HOME)"),
        )
    }

    // ─────────────── 4. 37 탈퇴 안내 ───────────────

    /**
     * 「탈퇴가 처리됐어요」의 근거는 **204 수신**뿐이다. "요청을 보냈다"에 세우면 401 → 재발급 실패로
     * 끝난 탈퇴에도 그 안내가 떠, 계정이 살아 있는 사람에게 거짓을 말한다.
     */
    @Test
    fun `탈퇴 안내는 204 를 받았을 때만 뜬다`() {
        val navGraph = navGraph()
        assertTrue(
            "요청 직전 콜백이 「탈퇴됐다」를 세운다 — 401 로 끝난 탈퇴에도 안내가 뜬다",
            Regex("""onSessionEndExpected\s*=\s*\{\s*accountDeleteRequested\s*=\s*true""")
                .containsMatchIn(navGraph),
        )
        assertTrue(
            "204 수신 콜백(onDeleted)이 배선돼 있지 않다",
            Regex("""onDeleted\s*=\s*\{\s*accountDeleted\s*=\s*true""").containsMatchIn(navGraph),
        )
        val notice = navGraph.substringAfter("signOutNotice = when {").substringBefore("}")
        assertTrue(
            "탈퇴 안내의 근거가 accountDeleted 가 아니다: $notice",
            Regex("""accountDeleted\s*->\s*"탈퇴가 처리됐어요""").containsMatchIn(notice),
        )
        assertTrue(
            "결과를 모르는 상태에서도 「세션이 만료됐어요」를 말한다 — 둘 중 하나는 거짓말이다",
            Regex("""accountDeleteRequested\s*->\s*null""").containsMatchIn(notice),
        )
        assertTrue(
            "탈퇴 성공 직후에 onDeleted() 를 부르지 않는다",
            Regex("""repository\.deleteAccount\([^)]*\)\s*(//[^\n]*\n\s*)*onDeleted\(\)""")
                .containsMatchIn(source("feature/mypage/AccountDeleteRoute.kt")),
        )
    }
}
