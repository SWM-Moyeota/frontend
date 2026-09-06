package com.moyeota.data.remote.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwtSubjectTest {

    /**
     * 실서버(localhost:8080)가 발급한 진짜 액세스 토큰이다.
     * 페이로드: `{"sub":"01a06109-f878-7706-aab0-5466fb311d26","tokenType":"ACCESS","iat":…,"exp":…}`
     * 같은 계정의 가입 응답 `uuid` 와 값이 일치함을 curl 로 확인했다 — 이게 이 함수의 존재 이유다.
     *
     * 서명 부분은 검증하지 않으므로 만료돼도 이 테스트는 유효하다.
     */
    private val realAccessToken =
        "eyJhbGciOiJIUzM4NCJ9." +
            "eyJzdWIiOiIwMWEwNjEwOS1mODc4LTc3MDYtYWFiMC01NDY2ZmIzMTFkMjYiLCJ0b2tlblR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODgzMzQ0MzgsImV4cCI6MTc4ODMzNTAzOH0." +
            "sI2xDMQovdZviTV-CWILm7epqbHw9hudGN0YYQ_3O2Qgt8A9NSjH4saWN1fEai_G"

    @Test
    fun `실서버 액세스 토큰에서 사용자 UUID 를 읽는다`() {
        assertEquals("01a06109-f878-7706-aab0-5466fb311d26", jwtSubject(realAccessToken))
    }

    /**
     * 서버 페이로드는 base64**url** 이고 패딩도 없다. 표준 알파벳만 아는 디코더로 읽으면
     * `-`/`_` 가 섞인 순간 깨지는데, UUID 만 든 실제 페이로드에는 그 문자가 잘 안 나와서
     * 위 테스트만으로는 드러나지 않는다. 그래서 둘 다 들어가는 페이로드를 일부러 만든다.
     */
    @Test
    fun `패딩 없는 base64url 페이로드도 디코딩한다`() {
        // {"sub":"?wv~("} → URL-safe 인코딩 결과에 `-` 와 `_` 가 모두 들어가고 패딩은 없다.
        val payload = "eyJzdWIiOiI_d3Z-KCJ9"
        assertEquals("?wv~(", jwtSubject("header.$payload.signature"))
    }

    @Test
    fun `JWT 형식이 아니면 null 이다`() {
        assertNull("점이 없다", jwtSubject("not-a-jwt"))
        assertNull("구획이 둘뿐이다", jwtSubject("header.payload"))
        assertNull("빈 문자열", jwtSubject(""))
    }

    @Test
    fun `페이로드가 깨졌거나 sub 가 없으면 null 이다`() {
        // base64 로 안 풀리는 문자열
        assertNull(jwtSubject("header.!!!not-base64!!!.signature"))
        // 풀리지만 JSON 이 아니다
        assertNull(jwtSubject("header.aGVsbG8gd29ybGQ.signature"))
        // JSON 이지만 sub 가 없다 → {"tokenType":"ACCESS"}
        assertNull(jwtSubject("header.eyJ0b2tlblR5cGUiOiJBQ0NFU1MifQ.signature"))
        // sub 가 빈 문자열이다 → {"sub":""}
        assertNull(jwtSubject("header.eyJzdWIiOiIifQ.signature"))
    }
}
