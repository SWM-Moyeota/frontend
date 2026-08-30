package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FavoritePlaceListResponse
import com.moyeota.data.remote.dto.FavoritePlaceRequestDto
import com.moyeota.data.remote.dto.PlaceSearchListResponse
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Place

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

fun Place.toFavoriteRequestDto(): FavoritePlaceRequestDto = FavoritePlaceRequestDto(
    placeName = name,
    roadName = roadName,
    latitude = latitude,
    longitude = longitude,
)
