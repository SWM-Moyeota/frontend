package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import com.moyeota.domain.model.Place
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
