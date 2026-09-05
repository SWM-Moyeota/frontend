package com.moyeota.data.remote

import com.moyeota.data.remote.dto.CallResultRequestDto
import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.data.remote.dto.ReportResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 서버 스펙 기준: report/interfaces/ReportController.java + application/dto/*.java
// (ReportRequest{partyId,latitude,longitude} / ReportResponse{reportId} / CallResultRequest{called})
class ReportDtosTest {

    // NetworkModule 과 동일한 구성으로 검증한다.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `신고 요청을 서버 필드명 그대로 직렬화한다`() {
        val encoded = json.encodeToString(
            ReportRequestDto.serializer(),
            ReportRequestDto(partyId = 7L, latitude = 37.5665, longitude = 126.9780),
        )

        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals(7L, obj.getValue("partyId").jsonPrimitive.long)
        assertEquals(37.5665, obj.getValue("latitude").jsonPrimitive.double, 0.0001)
        assertEquals(126.9780, obj.getValue("longitude").jsonPrimitive.double, 0.0001)
    }

    // partyId 는 화면에서 알 수 없으면 null — 기본값이라 본문에서 아예 빠진다.
    // 서버 ReportRequest 는 세 필드 모두 박싱 타입이라 누락을 그대로 받는다.
    @Test
    fun `partyId 가 null 이면 요청 본문에서 제외된다`() {
        val encoded = json.encodeToString(
            ReportRequestDto.serializer(),
            ReportRequestDto(partyId = null, latitude = 37.5665, longitude = 126.9780),
        )

        val obj = json.parseToJsonElement(encoded).jsonObject
        assertFalse(obj.containsKey("partyId"))
        assertTrue(obj.containsKey("latitude"))
        assertTrue(obj.containsKey("longitude"))
    }

    @Test
    fun `신고 응답 JSON 을 실제 서버 shape 그대로 역직렬화한다`() {
        // 실서버 응답 기준 (2026-09-06 드라이버 세션 검증: 200 {"reportId":...})
        val decoded = json.decodeFromString<ReportResponse>("""{"reportId":42}""")

        assertEquals(42L, decoded.reportId)
    }

    @Test
    fun `통화 결과 요청을 서버 필드명 그대로 직렬화한다`() {
        val encodedTrue = json.encodeToString(
            CallResultRequestDto.serializer(),
            CallResultRequestDto(called = true),
        )
        val encodedFalse = json.encodeToString(
            CallResultRequestDto.serializer(),
            CallResultRequestDto(called = false),
        )

        assertTrue(json.parseToJsonElement(encodedTrue).jsonObject.getValue("called").jsonPrimitive.boolean)
        // called=false 는 @NotNull 검증 대상이라 기본값 생략 없이 반드시 본문에 실려야 한다.
        assertFalse(json.parseToJsonElement(encodedFalse).jsonObject.getValue("called").jsonPrimitive.boolean)
    }
}
