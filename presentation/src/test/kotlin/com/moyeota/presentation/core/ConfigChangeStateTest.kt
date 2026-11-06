package com.moyeota.presentation.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 구성 변경(회전 · 다크모드 전환 · 글꼴 크기 · 언어)에서 **방 id 가 살아남는지**, 그리고 방을
 * 모르는 상태에서 **예시 방이 그려질 길이 없는지**를 못 박는다.
 *
 * 2026-10-15 결함: `MainNavGraph` 의 `createdPartyId`·`activePartyId` 가 `remember` 였다. 회전하면
 * 두 값은 사라지는데 [androidx.navigation.compose.rememberNavController] 의 백스택은 **복원되므로**
 * 21·25 목적지로 돌아왔고, 그 라우트가 방 id 없이 화면을 불러 **예시 방**(센텀시티역→서면역 ·
 * 3,600원)을 그렸다 — 진짜 방에 들어 있던 사용자에게 남의 가짜 방이 뜬 것이다. 되돌릴 길도
 * 막혀 있었다: 진행 중 방 재진입의 빗장(`startupStageHandled`)은 `rememberSaveable` 이라 true 로
 * 복원돼 즉시 return 했다.
 *
 * **이 테스트가 덮지 못하는 것**: 실제 구성 변경은 액티비티 재생성이라 JUnit 으로 재현할 수 없다.
 * 이 모듈에는 Robolectric·compose-ui-test 가 없어(`presentation/build.gradle.kts` 의
 * testImplementation 은 junit 하나다) `rememberSaveable` 의 저장·복원을 **실행해** 확인할 수단이
 * 없다. 그래서 여기서 보는 것은 **코드가 내린 결정**이다 — 어떤 상태가 saveable 로 선언돼 있고,
 * null 분기가 무엇을 그리는가. 회전 뒤 화면이 실제로 내 방을 가리키는지는 실기(에뮬레이터
 * 회전 또는 adb 로 글꼴 크기 변경)로만 확인된다.
 *
 * 소스를 문자열로 읽는 테스트라 서식을 크게 바꾸면 깨질 수 있다. 깨지면 **먼저 결정이 그대로인지
 * 확인하고** 정규식을 고쳐라 — 결정을 되돌리면서 테스트를 지우는 것이 이 테스트가 막는 일이다.
 */
class ConfigChangeStateTest {

    /**
     * `presentation/src/main/kotlin/com/moyeota/presentation` 을 찾는다.
     *
     * Gradle 테스트의 작업 디렉터리는 보통 모듈 디렉터리지만 거기에 기대지 않는다 — 상위로
     * 올라가며 양쪽 후보를 다 본다(루트에서 실행돼도 찾는다).
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

    // ───────────────────────── 1. 방 id 는 살아남는다 ─────────────────────────

    /**
     * 20·21·22 가 「어느 방인가」를 묻는 값. 둘 다 `Long?` 이라 Bundle 이 그대로 담는다 —
     * 저장 비용이 없고 [android.os.TransactionTooLargeException] 위험도 없다.
     *
     * 셋이었다. `activePartyId`(25·26·27 이 읽던 「지금 배차·운행 중인 방」)는 그 세 화면이
     * 사라지면서 독자를 잃어 함께 지웠다 — 여기 남겨 두면 없는 선언을 찾다 실패한다.
     */
    @Test
    fun `화면 사이로 넘기는 방 id 는 구성 변경에서 살아남는다`() {
        val navGraph = source("core/MainNavGraph.kt")
        listOf("createdPartyId", "selectedPartyId").forEach { name ->
            assertTrue(
                "$name 이 rememberSaveable 이 아니다 — 회전하면 방 id 가 날아가고 21·25 가 어느 방인지 모르게 된다",
                Regex("""var\s+$name\s+by\s+rememberSaveable""").containsMatchIn(navGraph),
            )
        }
    }

    /**
     * 16 방 생성 모달이 쓰는 좌표. [com.moyeota.domain.model.Place] 는 문자열 둘·실수 둘이라
     * 담아도 가볍다(`PlaceSaver`). 사라지면 16 이 「출발지가 없어요」로 떨어져, 사용자가 방금
     * 끝낸 15 를 다시 하게 된다.
     */
    @Test
    fun `15 에서 확정한 출발지와 도착지도 구성 변경에서 살아남는다`() {
        val navGraph = source("core/MainNavGraph.kt")
        listOf("confirmedOrigin", "confirmedDestination").forEach { name ->
            assertTrue(
                "$name 이 rememberSaveable 이 아니다 — 회전하면 16 이 고른 경로를 잃는다",
                Regex("""var\s+$name\s+by\s+rememberSaveable""").containsMatchIn(navGraph),
            )
        }
    }

    /**
     * `selectedPartner`([com.moyeota.domain.model.User])는 **일부러** `remember` 로 남겼다.
     * Bundle 에 담을 타입이 아니고, 없을 때 23 은 거짓을 말하지 않는다(안내로 되돌린다).
     *
     * 짝이었던 `finishedRide`([com.moyeota.domain.model.Ride])는 선언째 사라졌다 — 그 값의 독자가
     * 33 도착 완료 하나였고, 그 화면이 26 운행 중과 함께 삭제됐다.
     *
     * 이 테스트는 그 결정을 **기록**한다 — 누군가 "일관성" 때문에 기계적으로 saveable 로 바꾸면
     * 큰 객체가 저장 상태에 들어간다. 바꿀 이유가 생기면 Saver 를 두고 이 테스트를 고쳐라.
     */
    @Test
    fun `큰 도메인 객체는 저장 상태에 담지 않는다`() {
        val navGraph = source("core/MainNavGraph.kt")
        listOf("selectedPartner").forEach { name ->
            assertTrue(
                "$name 이 저장 상태로 바뀌었다 — Ride·User 는 Bundle 에 담을 크기가 아니다(근거는 선언부 주석)",
                Regex("""var\s+$name\s+by\s+remember\s*\{""").containsMatchIn(navGraph),
            )
        }
    }

    // ──────────────── 2. 방을 모를 때 예시 방을 그리지 않는다 ────────────────

    /**
     * 21 의 `partyId == null` 분기. 20·22·23 과 같은 방식 — 사실대로 말하고 되돌린다.
     *
     * 분기 **안**을 본다: 화면 호출(`MatchWaitingScreen(`)이 아니라 [ErrorBox] 가 있어야 한다.
     * 「잠시 후 다시 시도」류 문구도 함께 막는다 — 되돌아가는 것 말고는 사용자가 할 수 있는 게
     * 없는 자리다.
     *
     * 짝이었던 25 배차 현황(`DispatchStatusRoute`)은 사라졌다. 같은 트랩이 새 화면에 생기는 것은
     * 아래 「화면 기본 인자」 테스트가 presentation 전체를 훑어 막는다.
     */
    @Test
    fun `방 id 가 없으면 21 은 예시 방 대신 사실을 말한다`() {
        mapOf(
            "feature/matching/MatchWaitingRoute.kt" to "MatchWaitingScreen(",
        ).forEach { (path, screenCall) ->
            val body = source(path)
                .substringAfter("if (partyId == null) {")
                .substringBefore("        return")
            assertTrue("$path: 방 id 없음 분기가 ErrorBox 를 그리지 않는다", body.contains("ErrorBox("))
            assertTrue(
                "$path: 방 id 없음 분기가 화면을 직접 그린다 — 예시 방이 다시 샐 수 있다",
                !body.contains(screenCall),
            )
            assertTrue(
                "$path: 할 수 없는 일을 안내한다 — 이 자리에서 다시 불러올 방법은 없다",
                !body.contains("잠시 후"),
            )
        }
    }

    /**
     * 프로덕션 화면의 **기본 인자로 도메인 데이터를 지어내지 않는다.**
     *
     * 이 결함의 마지막 고리였다 — `ride: Ride = waitingRideDummy` 가 있는 한, 방을 모르는 호출부는
     * 컴파일을 통과하면서 가짜 방을 그린다. 기본값을 걷어낸 지금은 같은 사고가 나려면 컴파일이
     * 먼저 깨진다. 예시 방 자체는 `@Preview` 가 명시적으로 넘겨 쓰므로 남겨 뒀다.
     *
     * 범위를 21 로 좁히지 않고 presentation 전체를 본다 — 같은 트랩이 25b·25c·17 에도 있었고
     * (2026-10-15 함께 제거), 새 화면에 다시 생기는 것을 여기서 막는다.
     *
     * 타입 목록에서 `AssignedDriver` 를 뺐다 — 기사·배차와 함께 **도메인에서 사라진 타입**이라
     * 그 이름의 파라미터는 더 이상 존재할 수 없다(남겨 두면 영영 아무것도 걸러내지 않는 이름이 된다).
     */
    @Test
    fun `화면 기본 인자로 도메인 데이터를 지어내지 않는다`() {
        // "들여쓴 파라미터: 도메인타입 = null·빈값이 아닌 무엇" 을 찾는다.
        val fabricated = Regex(
            """^\s+\w+: (?:List<)?(?:Ride|User|ChatRoom|ChatMessage|Place|FavoritePlace)>?\??\s*=\s*(?!null|emptyList\(\)|emptyMap\(\))\S""",
            RegexOption.MULTILINE,
        )
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                fabricated.findAll(file.readText()).map { "${file.name}: ${it.value.trim()}" }
            }
            .toList()
        assertEquals(
            "화면 기본 인자가 도메인 데이터를 지어낸다 — 호출부가 값을 빼먹으면 가짜가 그려진다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }
}
