package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AddressResponseDto
import com.moyeota.data.remote.dto.ApiErrorDto
import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.FavoritePlaceRequestDto
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.ReverseAddress
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response

private val errorJson = Json { ignoreUnknownKeys = true }

private const val ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND"

fun PlaceSearchListResponse.PlaceSearchItem.toPlace(): Place = Place(
    name = name,
    roadName = roadName,
    latitude = latitude,
    longitude = longitude,
)

// 즐겨찾기는 name 이 아니라 placeName 으로 필드명이 다르다. 여기서만 흡수한다.
fun FavoritePlaceListResponse.FavoritePlaceItem.toFavoritePlace(): FavoritePlace = FavoritePlace(
    name = placeName,
    roadName = roadName,
    latitude = latitude,
    longitude = longitude,
    sequence = placeSequence,
)

/**
 * 역지오코딩 응답을 도메인으로 옮긴다. "주소 없음"은 실패가 아니라 null 이다.
 *
 * 성공(2xx)이어도 세 필드가 전부 비어 오면(네이버가 roadaddr/addr 어느 쪽도 못 채운 경우)
 * 표시할 게 없으므로 404 와 같은 취급으로 null 을 준다 — 호출측 분기를 한 갈래로 유지한다.
 * 404 는 서버가 의도한 ADDRESS_NOT_FOUND 일 때만 null 이고, 경로 오타 같은 404 는 그대로 던져 드러낸다.
 */
fun Response<AddressResponseDto>.toReverseAddressOrNull(): ReverseAddress? {
    if (isSuccessful) return body()?.toReverseAddressOrNull()
    if (code() == 404 && errorCode() == ADDRESS_NOT_FOUND) return null
    throw HttpException(this)
}

fun AddressResponseDto.toReverseAddressOrNull(): ReverseAddress? {
    val road = roadAddress?.takeIf { it.isNotBlank() }
    val jibun = jibunAddress?.takeIf { it.isNotBlank() }
    // 서버 Address.display() 와 같은 폴백 순서. address 가 이미 조합돼 오므로 그것이 1순위다.
    val display = address?.takeIf { it.isNotBlank() } ?: road ?: jibun ?: return null
    return ReverseAddress(address = display, roadAddress = road, jibunAddress = jibun)
}

/** 에러 본문의 code. 본문이 없거나 ErrorResponse shape 이 아니면 null. */
private fun Response<*>.errorCode(): String? = runCatching {
    errorBody()?.string()?.takeIf { it.isNotBlank() }?.let {
        errorJson.decodeFromString<ApiErrorDto>(it).code
    }
}.getOrNull()

fun Place.toFavoriteRequestDto(): FavoritePlaceRequestDto = FavoritePlaceRequestDto(
    placeName = name,
    roadName = roadName,
    latitude = latitude,
    longitude = longitude,
)
