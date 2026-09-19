package com.moyeota.data.repository

import com.moyeota.data.remote.ConfigApi
import com.moyeota.data.remote.toAppConfig
import com.moyeota.domain.model.AppConfig
import com.moyeota.domain.repository.AppConfigRepository

class RemoteAppConfigRepository(
    private val api: ConfigApi,
) : AppConfigRepository {
    override suspend fun getConfig(): AppConfig = api.getConfig().toAppConfig()
}
