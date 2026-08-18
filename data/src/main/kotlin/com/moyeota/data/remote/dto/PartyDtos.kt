package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 matching 도메인 응답 record 와 필드 1:1 대응
@Serializable
data class PartyListResponse(
    val list: List<PartyItem>,
) {
    @Serializable
    data class PartyItem(
        val partyId: Long,
        val departure: String,
        val destination: String,
        val currentMembers: Int,
        val capacity: Int,
        val status: String,
    )
}

@Serializable
data class PartyDetailResponse(
    val id: Long,
    val hostId: Long,
    val departureLat: Double,
    val departureLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departure: String,
    val destination: String,
    val capacity: Int,
    val currentMembers: Int,
    val departureRadius: Int,
    val destinationRadius: Int,
    val status: String,
    val createdAt: String,
    val members: List<MemberInfo>,
) {
    @Serializable
    data class MemberInfo(
        val memberId: Long,
        val isHost: Boolean,
        val joinedAt: String,
    )
}
