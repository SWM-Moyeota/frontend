package com.moyeota.domain.model

data class User(
    val id: String,
    val nickname: String,
    val verifiedLabel: String, // 예: "성결대 인증", "직장 인증"
    val rating: Double,
    val rideCount: Int,
)
