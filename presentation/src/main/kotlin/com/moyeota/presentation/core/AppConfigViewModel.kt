package com.moyeota.presentation.core

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.domain.model.AppConfig
import com.moyeota.domain.repository.AppConfigRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AppConfig"

/** 시작 시 조회가 실패했을 때의 재시도 간격. 서버 재기동·네트워크 복구 정도를 기다린다. */
private val RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 15_000L)

/**
 * 서버 운영 설정(`GET /api/v1/config`). NavHost 바깥(액티비티 스코프)에 두어 화면들이 한 값을 공유한다
 * (profileViewModel 과 같은 이유).
 *
 * - 시작 시 한 번 읽고, 실패하면 [RETRY_DELAYS_MS] 간격으로 몇 번 더 시도한다. 그래도 못 받으면
 *   [AppConfig.Default](동승 모드)로 간다 — 실사용자 서버가 동승 모드라 틀려도 실제 서비스와 같은 화면이다.
 * - 앱이 다시 앞으로 올 때 아직 못 받았으면 다시 시도한다([loadIfUnresolved]).
 * - 디버그 빌드는 [setDebugOverride] 로 서버 값을 덮어쓸 수 있다(35 마이페이지 개발자 옵션).
 *   메모리에만 들고 있어 프로세스가 죽으면 서버 값으로 돌아간다.
 */
class AppConfigViewModel(
    private val repository: AppConfigRepository,
) : ViewModel() {

    private val _serverConfig = MutableStateFlow(AppConfig.Default)

    /** 서버가 준 그대로의 값(디버그 강제값 미적용). 개발자 옵션의 「서버 값」 표시용. */
    val serverConfig: StateFlow<AppConfig> = _serverConfig.asStateFlow()

    private val _override = MutableStateFlow<Boolean?>(null)

    /** 디버그 강제값. null 이면 서버 값을 따른다. */
    val debugOverride: StateFlow<Boolean?> = _override.asStateFlow()

    private val _resolved = MutableStateFlow(false)

    /** 서버 응답을 한 번이라도 받았는가. false 면 지금 값은 기본값이다. */
    val resolved: StateFlow<Boolean> = _resolved.asStateFlow()

    /** 화면이 따를 설정 — 강제값이 있으면 강제값, 없으면 서버 값. */
    val config: StateFlow<AppConfig> = combine(_serverConfig, _override) { server, override ->
        AppConfig(taxiEnabled = effectiveTaxiEnabled(server.taxiEnabled, override))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig.Default)

    private var loading: Job? = null

    init {
        load()
    }

    /** 서버 값을 다시 읽는다. 이미 읽는 중이면 겹쳐 띄우지 않는다. */
    fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            var attempt = 0
            while (true) {
                val result = runCatching { repository.getConfig() }
                result.onSuccess {
                    _serverConfig.value = it
                    _resolved.value = true
                    Log.i(TAG, "서버 설정 수신 — 모드=${AppMode.of(it.taxiEnabled).label}")
                    return@launch
                }
                val wait = RETRY_DELAYS_MS.getOrNull(attempt++)
                if (wait == null) {
                    Log.w(TAG, "설정 조회 실패(재시도 소진) — 기본값(동승 모드)으로 진행: ${result.exceptionOrNull()?.message}")
                    return@launch
                }
                Log.w(TAG, "설정 조회 실패 — ${wait / 1000}초 뒤 재시도: ${result.exceptionOrNull()?.message}")
                delay(wait)
            }
        }
    }

    /** 복귀(onResume)마다 부른다. 아직 서버 값을 못 받았을 때만 다시 시도한다. */
    fun loadIfUnresolved() {
        if (!_resolved.value) load()
    }

    /** 디버그 전용 — 서버 값과 무관하게 모드를 고정한다. null 이면 해제. */
    fun setDebugOverride(taxiEnabled: Boolean?) {
        _override.value = taxiEnabled
        Log.i(TAG, "디버그 모드 강제: ${taxiEnabled?.let { AppMode.of(it).label } ?: "해제(서버 값)"}")
    }

    companion object {
        fun factory(repository: AppConfigRepository) = viewModelFactory {
            initializer { AppConfigViewModel(repository) }
        }
    }
}
