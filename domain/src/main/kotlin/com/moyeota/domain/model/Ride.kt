package com.moyeota.domain.model

enum class RideStatus { RECRUITING, MATCHED, DISPATCHING, ONGOING, COMPLETED }

data class Ride(
    val id: String,
    val origin: String,
    val destination: String,
    val departureLabel: String, // 예: "지금 출발", "18:30"
    val capacity: Int,
    val members: List<User>,
    val farePerPerson: Int,
    val totalFare: Int,
    val status: RideStatus,
    // 아래는 서버 연동으로 채워지는 값. 더미/목록 응답에는 없어 기본값을 둔다.
    // hostId 는 "내가 방장인가"(= 매칭 시작 버튼 노출) 판단에 쓴다.
    val hostId: String? = null,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
)
