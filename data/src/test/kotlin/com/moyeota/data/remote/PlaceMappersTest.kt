package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AddressResponseDto
import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import com.moyeota.domain.model.Place
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PlaceMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `장소 검색 응답을 Place 로 매핑한다`() {
        val item = PlaceSearchListResponse.PlaceSearchItem(
            name = "성결대학교",
            roadName = "경기 안양시 만안구 성결대학로 53",
            latitude = 37.3799,
            longitude = 126.9310,
        )

        val place = item.toPlace()

        assertEquals("성결대학교", place.name)
        assertEquals("경기 안양시 만안구 성결대학로 53", place.roadName)
        assertEquals(37.3799, place.latitude, 0.0001)
        assertEquals(126.9310, place.longitude, 0.0001)
    }

    // 검색은 name, 즐겨찾기는 placeName 으로 서버 필드명이 다르다 — 매퍼가 이 차이를 흡수해야 한다.
    @Test
    fun `즐겨찾기 응답의 placeName 을 도메인 name 으로 매핑한다`() {
        val item = FavoritePlaceListResponse.FavoritePlaceItem(
            placeName = "집",
            roadName = "서울 강남구 테헤란로 123",
            latitude = 37.5013,
            longitude = 127.0396,
            placeSequence = 2,
        )

        val favorite = item.toFavoritePlace()

        assertEquals("집", favorite.name)
        assertEquals(2, favorite.sequence)
    }

    @Test
    fun `즐겨찾기 등록 요청은 name 을 placeName 으로 보낸다`() {
        val dto = Place(
            name = "회사",
            roadName = "경기 성남시 분당구 판교역로 166",
            latitude = 37.3948,
            longitude = 127.1112,
        ).toFavoriteRequestDto()

        assertEquals("회사", dto.placeName)
        assertEquals("경기 성남시 분당구 판교역로 166", dto.roadName)
    }

    @Test
    fun `검색 결과가 없거나 list 키가 누락돼도 빈 목록으로 처리한다`() {
        val decoded = json.decodeFromString<PlaceSearchListResponse>("{}")

        assertEquals(emptyList<PlaceSearchListResponse.PlaceSearchItem>(), decoded.list)
    }

    @Test
    fun `실제 서버 응답 JSON 을 그대로 역직렬화한다`() {
        val body = """
            {"list":[{"name":"판교역","roadName":"경기 성남시 분당구 판교역로 160","latitude":37.3947,"longitude":127.1112}]}
        """.trimIndent()

        val decoded = json.decodeFromString<PlaceSearchListResponse>(body)

        assertEquals(1, decoded.list.size)
        assertEquals("판교역", decoded.list[0].toPlace().name)
    }

    // --- 역지오코딩 (GET /api/v1/places/reverse) ---
    // 아래 3개 응답 본문은 localhost:8080 실서버 curl 결과 그대로다.

    @Test
    fun `역지오코딩 정상 응답을 ReverseAddress 로 매핑한다`() {
        val body = """
            {"address":"부산광역시 금정구 부산대학로63번길 2 부산대학교","roadAddress":"부산광역시 금정구 부산대학로63번길 2 부산대학교","jibunAddress":"부산광역시 금정구 장전동 40"}
        """.trimIndent()

        val address = Response.success(json.decodeFromString<AddressResponseDto>(body))
            .toReverseAddressOrNull()!!

        assertEquals("부산광역시 금정구 부산대학로63번길 2 부산대학교", address.address)
        assertEquals("부산광역시 금정구 부산대학로63번길 2 부산대학교", address.roadAddress)
        assertEquals("부산광역시 금정구 장전동 40", address.jibunAddress)
    }

    // 도로명이 없는 좌표(실측 35.0,128.0). roadAddress 만 null 이고 address 는 지번으로 채워져 온다.
    @Test
    fun `roadAddress 가 null 이어도 address 는 지번으로 유지된다`() {
        val body = """
            {"address":"경상남도 사천시 서포면 자혜리 1230-17","roadAddress":null,"jibunAddress":"경상남도 사천시 서포면 자혜리 1230-17"}
        """.trimIndent()

        val address = Response.success(json.decodeFromString<AddressResponseDto>(body))
            .toReverseAddressOrNull()!!

        assertEquals("경상남도 사천시 서포면 자혜리 1230-17", address.address)
        assertNull(address.roadAddress)
        assertEquals("경상남도 사천시 서포면 자혜리 1230-17", address.jibunAddress)
    }

    // 바다에 핀을 찍는 건 정상 유저 행동이라 예외가 아니라 null 이어야 한다.
    @Test
    fun `주소 없는 좌표의 404 ADDRESS_NOT_FOUND 는 예외가 아니라 null 이다`() {
        val response = errorResponse(
            404,
            """{"code":"ADDRESS_NOT_FOUND","message":"해당 좌표의 주소를 찾을 수 없습니다."}""",
        )

        assertNull(response.toReverseAddressOrNull())
    }

    @Test
    fun `한국 밖 좌표의 400 INVALID_COORDINATES 는 예외로 던진다`() {
        val response = errorResponse(
            400,
            """{"code":"INVALID_COORDINATES","message":"좌표가 올바르지 않습니다."}""",
        )

        val e = assertThrows(HttpException::class.java) { response.toReverseAddressOrNull() }
        assertEquals(400, e.code())
    }

    // 경로 오타 등 서버가 의도하지 않은 404 까지 null 로 삼키면 연동 실패가 조용히 묻힌다.
    @Test
    fun `ADDRESS_NOT_FOUND 가 아닌 404 는 예외로 던진다`() {
        val response = errorResponse(404, """{"timestamp":"2026-09-01","status":404,"error":"Not Found"}""")

        assertThrows(HttpException::class.java) { response.toReverseAddressOrNull() }
    }

    @Test
    fun `세 필드가 모두 비면 표시할 주소가 없으므로 null 이다`() {
        assertNull(AddressResponseDto().toReverseAddressOrNull())
    }

    private fun errorResponse(code: Int, body: String): Response<AddressResponseDto> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))
}
