package com.moyeota.presentation.core

import java.io.File
import org.junit.Assert.assertFalse
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
 * - 색은 `MoyeotaColor` 만 — `Color(0x…)`·`Color(255, …)` 금지
 * - 글자 크기는 `MoyeotaType` 만 — `fontSize =`·`13.sp` 금지
 *
 * 걸리면: 가장 가까운 토큰으로 바꾼다. 토큰에 없는 값이 정말 필요하면 디자인시스템에 토큰을 먼저 만든다.
 *
 * ### 무늬
 * - 색 [ColorPattern] `\bColor\((0x|\d)` — 앞에 `\b` 를 둬 `ChipColor(2)` 같은 **다른 이름**을 잡지 않는다
 * - 글자 [FontSizePattern] `fontSize\s*=` 또는 `(?<![\w.])\d+(\.\d+)?\s*\.sp\b` — 숫자 앞이 글자·`.` 이면 숫자
 *   리터럴이 아니라 이름의 일부라(`item1.sp`) 건너뛴다. `13.sp`·`13.5.sp` 는 잡는다
 *
 * ### 알려진 한계
 * 소스를 줄 단위 정규식으로 읽을 뿐 파서가 아니다([codePart]). 아래는 알고 둔 구멍이다.
 * - `*` 로 시작하는 줄은 KDoc 줄로 보고 통째로 건너뛴다 — 곱셈 연산자로 시작하는 이어진 줄이 있으면 그 줄은 보지 못한다
 * - 문자열 안의 `//` 는 앞이 `:` 일 때(`"https://…"`)만 주석이 아닌 것으로 본다 — `"a//b"` 같은 문자열이면 그 뒤를
 *   주석으로 잘라 같은 줄 뒤쪽의 직접 지정을 놓친다
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
                    if (pattern.containsMatchIn(codePart(line))) "${file.relativeTo(sourceRoot)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
    }

    @Test
    fun `화면 코드는 색을 직접 정의하지 않는다`() {
        val found = violations(ColorPattern)

        assertTrue("MoyeotaColor 토큰을 쓴다:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun `화면 코드는 글자 크기를 직접 정하지 않는다`() {
        val found = violations(FontSizePattern)

        assertTrue("MoyeotaType 토큰을 쓴다:\n" + found.joinToString("\n"), found.isEmpty())
    }

    /** 검사가 실제로 걸리는지 — 무늬가 틀리면 위 두 테스트는 늘 통과해 뜻이 없다(iOS `DesignTokenGuardTest` 와 같은 자리). */
    @Test
    fun `검사 무늬는 직접 지정을 잡고 토큰 사용과 다른 이름과 주석은 지나친다`() {
        fun hits(pattern: Regex, line: String) = pattern.containsMatchIn(codePart(line))

        assertTrue(hits(ColorPattern, "private val GrayMute = Color(0xFF8A93A0)"))
        assertTrue(hits(ColorPattern, "val c = Color(255, 0, 0)"))
        assertTrue(hits(ColorPattern, "background(androidx.compose.ui.graphics.Color(0xFF000000))"))
        assertFalse(hits(ColorPattern, "color = MoyeotaColor.TextMute"))
        assertFalse("다른 이름의 생성자는 색이 아니다", hits(ColorPattern, "val chip = ChipColor(2)"))
        assertFalse(hits(ColorPattern, "// 예전엔 Color(0xFF8A93A0) 였다"))
        // 문자열 안의 `//`(URL) 뒤도 본다 — 주석으로 보고 잘라 내면 그 뒤의 직접 지정을 놓친다
        assertTrue(hits(ColorPattern, """val url = "https://moyeota.co.kr"; val c = Color(0xFF8A93A0)"""))
        assertFalse(hits(ColorPattern, "val c = MoyeotaColor.TextMute // 예전엔 Color(0xFF8A93A0)"))

        assertTrue(hits(FontSizePattern, "Text(text = t, fontSize = 13.sp)"))
        assertTrue(hits(FontSizePattern, "val size = 13.sp"))
        assertTrue(hits(FontSizePattern, "val size = 13.5.sp"))
        assertTrue(hits(FontSizePattern, "val size = 13 .sp"))
        assertFalse(hits(FontSizePattern, "style = MoyeotaType.BodySm"))
        assertFalse("이름 끝의 숫자는 숫자 리터럴이 아니다", hits(FontSizePattern, "val size = item1.sp"))
        assertFalse(hits(FontSizePattern, " * 예전엔 `13.sp` 였다"))
    }
}

/** 화면 코드에서 색을 직접 만드는 꼴 — `Color(0x…)`·`Color(255, …)`. 다른 이름(`ChipColor(2)`)은 지나친다. */
private val ColorPattern = Regex("""\bColor\((0x|\d)""")

/** 화면 코드에서 글자 크기를 직접 정하는 꼴 — `fontSize =`·`13.sp`. 이름 끝의 숫자(`item1.sp`)는 지나친다. */
private val FontSizePattern = Regex("""fontSize\s*=|(?<![\w.])\d+(\.\d+)?\s*\.sp\b""")

/**
 * 검사할 코드 부분. 줄 주석·KDoc 줄은 통째로 건너뛰고, 코드 뒤 주석은 잘라 낸다 — 단 `"https://…"` 같은 문자열 안의
 * `//` 는 주석이 아니다(앞이 `:` 인 `//` 는 남긴다). 예전엔 거기서 잘려 뒤쪽을 못 봤다. 한계는 클래스 KDoc 에 있다.
 */
private fun codePart(line: String): String {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return ""
    return line.replace(Regex("""(?<!:)//.*$"""), "")
}
