package com.moyeota.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 긴급 신고에 싣는 실측 좌표 1건 (RemoteRideRepository.currentLocation 주입용).
 *
 * 신고는 위치보다 빨라야 한다 — 그래서:
 * - 위치 권한이 없으면 즉시 null (권한 요청 UI 를 띄우지 않는다 — 지도 화면이 이미 요청한다)
 * - 신선한 fix 는 [CURRENT_FIX_TIMEOUT_MS] 안에만 기다리고, 실패하면 캐시(lastLocation)로 폴백
 * - 그래도 없으면 null — 서버 ReportRequest 가 좌표 null 을 허용하므로 가짜 좌표를 지어내지 않는다
 */
class ReportLocationProvider(private val context: Context) {

    suspend fun current(): Pair<Double, Double>? {
        if (!hasLocationPermission()) return null
        val fix = freshFix() ?: cachedFix()
        return fix?.let { it.latitude to it.longitude }
    }

    // 정밀·대략 중 하나면 충분하다 — 신고에는 대략 위치도 없는 것보다 낫다.
    private fun hasLocationPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // hasLocationPermission() 통과 후에만 호출된다
    private suspend fun freshFix(): Location? = withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) {
        val cancellation = CancellationTokenSource()
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .awaitOrNull()
        } finally {
            cancellation.cancel() // 타임아웃/취소 시 측위 요청도 함께 끊는다
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun cachedFix(): Location? = withTimeoutOrNull(CACHED_FIX_TIMEOUT_MS) {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.awaitOrNull()
    }

    // kotlinx-coroutines-play-services 의존성 없이 Task 를 suspend 로 바꾼다.
    // 실패·취소는 예외 대신 null — 위치는 어떤 경우에도 신고를 막으면 안 된다.
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }

    private companion object {
        const val CURRENT_FIX_TIMEOUT_MS = 3_000L
        const val CACHED_FIX_TIMEOUT_MS = 1_000L
    }
}
