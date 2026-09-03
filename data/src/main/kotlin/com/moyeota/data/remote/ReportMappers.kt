package com.moyeota.data.remote

import com.moyeota.data.remote.dto.ReportRequestDto
import com.moyeota.domain.model.NewReport

fun NewReport.toRequestDto(): ReportRequestDto = ReportRequestDto(
    partyId = partyId,
    latitude = latitude,
    longitude = longitude,
)
