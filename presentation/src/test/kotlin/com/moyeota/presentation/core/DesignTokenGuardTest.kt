package com.moyeota.presentation.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 코드가 **디자인 토큰을 우회하지 않는지** 소스를 읽어 확인한다 (디자인 QA G1·G3, 2026-11).
 *
 * "AI 가 만든 것 같다"는 피드백의 가장 큰 원인은 화면마다 따로 정한 값이었다 — 회색 네 가지, 페이지 회색 다섯 가지,
 * 글자 크기 열여섯 가지가 파일마다 `private val … = Color(0x…)`·`fontSize = …` 로 다시 정의돼 있었다.
 * 한 번 걷어내도 새 화면이 같은 방식으로 값을 들이면 금방 돌아온다. lint/detekt 규칙을 새로 들이는 대신
 * (의존성을 늘리지 않는다) 이 테스트가 그 문을 닫는다.
 *
 * - 색은 `MoyeotaColor` 만 — `Color(0x…)` 금지
 * - 글자 크기는 `MoyeotaType` 만 — `fontSize =` 금지
 *
 * 걸리면: 가장 가까운 토큰으로 바꾼다. 토큰에 없는 값이 정말 필요하면 디자인시스템에 토큰을 먼저 만든다.
 */
class DesignTokenGuardTest {

    // 유닛 테스트의 작업 디렉터리는 모듈 루트다(presentation/)
    private val sourceRoot = File("src/main/kotlin/com/moyeota/presentation")

    private fun violations(pattern: Regex): List<String> {
        assertTrue("소스 경로를 찾지 못했다: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (pattern.containsMatchIn(code)) "${file.relativeTo(sourceRoot)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
    }

    @Test
    fun `화면 코드는 색을 직접 정의하지 않는다`() {
        val found = violations(Regex("""Color\(0x"""))

        assertTrue("MoyeotaColor 토큰을 쓴다:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `화면 코드는 글자 크기를 직접 정하지 않는다`() {
        val found = violations(Regex("""fontSize\s*="""))

        assertTrue("MoyeotaType 토큰을 쓴다:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
