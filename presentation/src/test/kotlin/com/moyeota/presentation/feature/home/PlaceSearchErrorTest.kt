package com.moyeota.presentation.feature.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 목적지 검색의 **실패 문구가 결과를 가리지 않는지**를 못 박는다.
 *
 * 2026-10-15 결함(QA 실기 발견): 빠르게 타이핑하면 서버가 200 과 정상 목록을 줘도 화면에
 * 「장소를 검색하지 못했어요」만 보이고 결과가 한 건도 안 떴다. 사슬은 셋이었다.
 *
 * 1. 글자를 칠 때마다 `searchJob?.cancel()` 이 직전 요청을 끊는다 — 정상 동작이다.
 * 2. 그 취소가 `CancellationException` 으로 올라오는데 `catch (e: Exception)` 하나가 **그것까지**
 *    받아 실패 문구를 세웠다. 취소는 실패가 아니다.
 * 3. 뒤이어 **성공한** 검색이 문구를 지우지 않았고, 화면은 에러를 `when` 의 첫 가지로 두므로
 *    (`DestinationScreen`) 결과가 그 문구에 가려졌다.
 *
 * 셋 중 하나만 되돌려도 증상이 되살아나므로 셋 다 본다.
 *
 * **이 테스트가 덮지 못하는 것**: 실제 타이핑·취소 타이밍은 재현하지 않는다. 이 모듈에는
 * `kotlinx-coroutines-test` 가 없어(`presentation/build.gradle.kts` 의 testImplementation 은
 * junit 하나다) ViewModel 의 코루틴을 돌릴 수단이 없다. 그래서 보는 것은 **코드가 내린 결정**이고,
 * 사용자 눈에 결과가 실제로 보이는지는 실기로만 확인된다 — 15 목적지 검색에서 글자를 빠르게
 * 지웠다 쳤다 반복하면 된다.
 *
 * 소스를 문자열로 읽으므로 서식을 크게 바꾸면 깨질 수 있다. 깨지면 **먼저 결정이 그대로인지
 * 확인하고** 정규식을 고쳐라 — 결정을 되돌리면서 테스트를 지우는 것이 이 테스트가 막는 일이다.
 */
class PlaceSearchErrorTest {

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

    /** `runSearch` 본문만 떼어 본다 — 같은 파일의 즐겨찾기 조회 등 다른 `catch` 에 걸리지 않게. */
    private fun runSearchBody(): String {
        val route = source("feature/home/DestinationRoute.kt")
        val start = route.indexOf("private fun runSearch(")
        assertTrue("runSearch 를 찾지 못했다 — 이름이 바뀌었으면 이 테스트도 같이 고쳐라", start >= 0)
        val next = route.indexOf("\n    fun ", start)
        return route.substring(start, if (next > start) next else route.length)
    }

    @Test
    fun `검색 취소는 실패로 취급하지 않는다`() {
        val body = runSearchBody()
        assertTrue(
            "runSearch 가 CancellationException 을 따로 받지 않는다 — catch (Exception) 하나로 받으면 " +
                "타이핑하는 족족 실패 문구가 선다(글자마다 searchJob.cancel 이 돈다)",
            Regex("""catch\s*\(\s*\w+\s*:\s*CancellationException\s*\)""").containsMatchIn(body),
        )
        assertTrue(
            "CancellationException 을 다시 던지지 않는다 — 삼키면 구조적 동시성이 깨진다",
            Regex("""catch\s*\(\s*(\w+)\s*:\s*CancellationException\s*\)\s*\{[^}]*throw\s+\1""")
                .containsMatchIn(body),
        )
    }

    @Test
    fun `검색에 성공하면 이전 실패 문구를 지운다`() {
        val body = runSearchBody()
        val success = Regex("""searchResults\s*=\s*results[^)]*""").find(body)?.value
            ?: Regex("""[^\n]*searchResults\s*=\s*results[^\n]*""").find(body)?.value
        assertTrue("성공 분기(searchResults = results)를 찾지 못했다", success != null)
        assertTrue(
            "성공했는데 searchErrorMessage 를 null 로 지우지 않는다 — 화면이 에러를 먼저 그리므로 " +
                "결과가 와도 그 문구에 가려 한 건도 안 보인다",
            Regex("""searchResults\s*=\s*results\s*,\s*searchErrorMessage\s*=\s*null""")
                .containsMatchIn(body.replace(Regex("\\s+"), " ")) ||
                Regex("""searchErrorMessage\s*=\s*null\s*,\s*searchResults\s*=\s*results""")
                    .containsMatchIn(body.replace(Regex("\\s+"), " ")),
        )
    }

    @Test
    fun `화면은 실패 문구를 결과보다 먼저 그린다 - 그래서 문구를 지우는 것이 중요하다`() {
        val screen = source("feature/home/DestinationScreen.kt")
        val whenStart = screen.indexOf("searchErrorMessage != null ->")
        val resultsStart = screen.indexOf("searchResults.isEmpty()")
        assertTrue("검색 결과 영역의 when 분기를 찾지 못했다", whenStart >= 0 && resultsStart >= 0)
        assertTrue(
            "에러 분기가 결과 분기보다 뒤에 있다 — 이 테스트의 전제(에러가 결과를 가린다)가 바뀌었으니 " +
                "runSearch 가 문구를 지워야 하는 이유도 다시 따져라",
            whenStart < resultsStart,
        )
    }
}
