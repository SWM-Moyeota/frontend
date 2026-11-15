package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable data class LoginMethodsDto(val hasPassword: Boolean, val methods: List<LoginMethodDto>)
@Serializable data class LoginMethodDto(val provider: String, val status: String)
@Serializable data class LinkCodeDto(val code: String)
@Serializable data class LinkVerificationDto(val verificationToken: String, val expiresInSeconds: Int)
@Serializable data class GoogleLinkDto(val idToken: String, val verificationToken: String)
