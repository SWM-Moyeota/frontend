package com.moyeota.presentation.core

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.AppConfig
import com.moyeota.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AppConfig"

/**
 * 서버 운영 설정(`GET /api/v1/config`) — 앱 시작 시 **한 번** 읽는다. NavHost 바깥(액티비티 스코프)에 두어
 * 화면들이 한 값을 공유한다(profileViewModel 과 같은 이유).
 *
 * 받기 전·실패 시엔 [AppConfig.Default](기사 모드)다. 잘못 판단해도 「기사 대기에서 안 넘어감」 정도지,
 * 「기사 배정된 방을 합승 완료로 닫음」 같은 사고는 아니다 — 서버가 그 호출을 409 로 막기도 한다.
 */
class AppConfigViewModel(
    private val repository: AppConfigRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(AppConfig.Default)
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { repository.getConfig() }
                .onSuccess { _config.value = it }
                .onFailure { Log.w(TAG, "설정 조회 실패 — 기본값(기사 모드)으로 진행: ${it.message}") }
        }
    }

    companion object {
        fun factory(repository: AppConfigRepository) = viewModelFactory {
            initializer { AppConfigViewModel(repository) }
        }
    }
}
