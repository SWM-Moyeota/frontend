package com.moyeota.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// 백엔드 matching 도메인 응답 record 와 필드 1:1 대응
// (origin/develop: matching/application/dto/*.java)
// 서버 record 필드가 전부 박싱 타입(Long/Integer/Double)이라 null 이 올 수 있는 자리는 기본값으로 방어한다.
@Serializable
data class PartyListResponse(
    val list: List<PartyItem> = emptyList(),
) {
    @Serializable
    data class PartyItem(
        val partyId: Long,
        val departure: String = "",
        val destination: String = "",
        val currentMembers: Int = 0,
        val capacity: Int = 0,
        val status: String = "",
    )
}

@Serializable
data class PartyDetailResponse(
    val id: Long,
    val hostId: Long? = null,
    val departureLat: Double? = null,
    val departureLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val departure: String = "",
    val destination: String = "",
    val capacity: Int = 0,
    val currentMembers: Int = 0,
    val departureRadius: Int = 0,
    val destinationRadius: Int = 0,
    val status: String = "",
    val createdAt: String? = null,
    val members: List<MemberInfo> = emptyList(),
) {
    @Serializable
    data class MemberInfo(
        val memberId: Long,
        // 서버 record 컴포넌트명은 isHost. Jackson 버전에 따라 boolean is- 접두사를
        // 벗겨 "host" 로 직렬화하는 경우가 있어 두 이름을 모두 허용한다.
        @OptIn(ExperimentalSerializationApi::class)
        @JsonNames("host")
        val isHost: Boolean = false,
        val joinedAt: String? = null,
    )
}

// POST /api/v1/matching/rooms 요청 본문 (백엔드 OpenPartyRequest)
@Serializable
data class OpenPartyRequestDto(
    val hostId: Long,
    val departureLat: Double,
    val departureLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departure: String,
    val destination: String,
    val capacity: Int,
    val departureRadius: Int,
    val destinationRadius: Int,
)

// POST /api/v1/matching/rooms 응답 (백엔드 OpenPartyResponse — members 목록 없음)
@Serializable
data class OpenPartyResponse(
    val id: Long,
    val hostId: Long? = null,
    val departureLat: Double? = null,
    val departureLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val departure: String = "",
    val destination: String = "",
    val capacity: Int = 0,
    val currentMembers: Int = 0,
    val departureRadius: Int = 0,
    val destinationRadius: Int = 0,
    val status: String = "",
    val createdAt: String? = null,
)
