package com.moyeota.data.remote

import com.moyeota.data.remote.dto.AppConfigResponse
import com.moyeota.domain.model.AppConfig

fun AppConfigResponse.toAppConfig(): AppConfig = AppConfig(taxiEnabled = taxiEnabled)
