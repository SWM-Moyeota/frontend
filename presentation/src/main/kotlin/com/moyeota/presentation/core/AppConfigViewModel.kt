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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AppConfig"

/** 시작 시 조회가 실패했을 때의 재시도 간격. 서버 재기동·네트워크 복구 정도를 기다린다. */
private val RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 15_000L)

/**
 * 서버 운영 설정(`GET /api/v1/config`). NavHost 바깥(액티비티 스코프)에 두어 화면들이 한 값을 공유한다
 * (profileViewModel 과 같은 이유).
 *
 * 지금 이 설정이 정하는 것은 **강제 업데이트 게이트 하나뿐**이다
 * ([AppConfig.minVersionCode]·[AppConfig.recommendedVersionCode] → [updateRequirement]).
 *
 * - 시작 시 한 번 읽고, 실패하면 [RETRY_DELAYS_MS] 간격으로 몇 번 더 시도한다. 그래도 못 받으면
 *   [AppConfig.Default] 로 간다 — 두 기준이 **null 이라 아무도 막히지 않는다.**
 * - 앱이 다시 앞으로 올 때 아직 못 받았으면 다시 시도한다([loadIfUnresolved]).
 *
 * **디버그 강제값(`setDebugOverride`)은 지웠다.** 그 장치는 `taxiEnabled` 하나를 뒤집어 동승/택시
 * 화면을 골라 보기 위한 것이었는데, 기사·배차를 걷어내면서 서버가 그 값을 보내지 않게 되고
 * 도메인에서도 필드가 사라졌다 — 덮어쓸 값이 없다. 되살릴 이유가 생긴다면(새 운영 플래그) 그때
 * **그 플래그에 맞춰** 다시 세우는 쪽이 맞다. 「서버가 준 값」(`serverConfig`)과 「화면이 따를 값」
 * ([config])을 갈라 두었던 것도 같은 이유로 하나로 합쳤다 — 이제 둘이 같은 값이다.
 */
class AppConfigViewModel(
    private val repository: AppConfigRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(AppConfig.Default)

    /** 화면이 따를 설정 — 서버가 준 그대로다(덮어쓰는 장치가 없다). */
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val _resolved = MutableStateFlow(false)

    /** 서버 응답을 한 번이라도 받았는가. false 면 지금 값은 기본값이다. */
    val resolved: StateFlow<Boolean> = _resolved.asStateFlow()

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
                    _config.value = it
                    _resolved.value = true
                    Log.i(TAG, "서버 설정 수신 — 최소=${it.minVersionCode} 권장=${it.recommendedVersionCode}")
                    return@launch
                }
                val wait = RETRY_DELAYS_MS.getOrNull(attempt++)
                if (wait == null) {
                    Log.w(TAG, "설정 조회 실패(재시도 소진) — 기본값으로 진행(게이트 없음): ${result.exceptionOrNull()?.message}")
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

    companion object {
        fun factory(repository: AppConfigRepository) = viewModelFactory {
            initializer { AppConfigViewModel(repository) }
        }
    }
}
