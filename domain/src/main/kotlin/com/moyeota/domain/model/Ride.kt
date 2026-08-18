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
)
