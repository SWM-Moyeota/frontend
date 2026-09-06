package com.moyeota.presentation.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * 기기의 실제 좌표 1건.
 *
 * 지도 SDK 타입([com.naver.maps.geometry.LatLng])에 묶지 않는다 — 지도가 없는 화면
 * (15 목적지 입력의 출발지 기본값)도 같은 값을 쓰기 때문이다.
 *
 * @param accuracyMeters 수평 오차 반경(m, 68% 신뢰). 지도가 정확도 원을 그리는 데 쓴다.
 *   **null 이면 기기가 정확도를 보고하지 않은 fix** — 원을 그리지 않는다(없는 정확도를
 *   0m 로 단정하면 실제보다 정확해 보인다)
 * @param bearingDegrees 진행 방향(도, 0 = 북). 정지 상태에서는 GPS bearing 이 잡음이라
 *   null 로 내려온다 — 방향 표시가 제자리에서 빙빙 도는 것을 막는다
 */
@Immutable
data class UserCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
)

/**
 * 위치 권한 + 현재 좌표 상태.
 *
 * @param isGranted 정밀·대략 중 **하나라도** 허용됐는지. 지도 중심을 잡는 용도라 대략 위치로 충분하다
 * @param coordinates 실제 좌표. **null 이면 아직 못 받았거나 권한이 없다** — 호출부가 폴백 좌표를 쓴다
 * @param onRequestPermission 권한 재요청. 안내 UI 의 버튼에 연결한다
 */
@Immutable
data class MyLocationState(
    val isGranted: Boolean,
    val coordinates: UserCoordinates?,
    val onRequestPermission: () -> Unit,
)

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * 지도 화면용 갱신 주기 — **1초**.
 *
 * 파란 점이 걸음 속도(≈1.4m/s)를 눈에 띄는 지연 없이 따라오려면 이 정도가 필요하다.
 * 10초 주기에서는 한 번 튈 때 10m 이상을 순간이동해 「위치가 안 맞는다」로 체감된다.
 * 구독은 화면이 보이는 동안(STARTED 이상)에만 살아 있으므로 백그라운드 소모는 없다.
 */
const val MapLocationIntervalMs = 1_000L

/** 지도가 없는 화면(15 목적지 입력)용 — 출발지 좌표 한 번이면 충분해 주기를 늦춘다 */
const val FormLocationIntervalMs = 5_000L

/**
 * 이동 거리 필터를 걸지 않는다(0m).
 *
 * 필터를 걸면 제자리에서 정확도만 개선되는 fix 가 통째로 버려져, 처음 잡힌 부정확한 좌표에
 * 그대로 고착된다. 갱신 빈도는 주기(interval)로 이미 제한된다.
 */
private const val MinUpdateDistanceM = 0f

/**
 * 마커 갱신에 쓸 수 있는 오차 반경 한계(m).
 *
 * 이보다 나쁜 fix(주로 셀타워·와이파이 위치)는 파란 점을 수백 m 밖으로 끌고 가 「위치가
 * 틀렸다」는 인상을 만든다. 다만 좌표가 **하나도 없을 때**는 대략 위치라도 보여 주는 편이
 * 빈 지도보다 낫기 때문에 첫 fix 는 정확도와 무관하게 받는다.
 */
private const val AccuracyGateM = 30f

/**
 * 이 시간 넘게 새 좌표를 못 받았으면 정확도가 나빠도 받아들인다.
 *
 * 게이트만 두면 실내처럼 GPS 가 계속 나쁜 환경에서 파란 점이 옛 좌표에 영원히 얼어붙는다.
 * 오래된 정확한 점보다 방금 들어온 대략적인 점이 낫다.
 */
private const val StaleFixNanos = 20_000_000_000L // 20초

/**
 * bearing 을 신뢰할 최소 속도(m/s).
 *
 * 정지 상태의 GPS bearing 은 잡음이라 그대로 반영하면 방향 표시가 제자리에서 회전한다.
 * 속도 정보가 아예 없는 fix 는 판단 근거가 없으므로 bearing 을 그대로 쓴다.
 */
private const val MinBearingSpeedMps = 0.5f

/**
 * 위치 권한을 요청하고 현재 좌표를 구독하는 공용 상태.
 *
 * 컴포저블이 화면에 있는 동안에만 구독한다(백그라운드 위치 없음). 권한이 없거나 좌표를
 * 한 번도 못 받으면 [MyLocationState.coordinates] 가 null 로 남고, 호출부는 각자의 폴백
 * 좌표(앱 공통 `DemoOrigin`)를 쓰면 된다 — **어느 경로에서도 예외를 던지지 않는다.**
 *
 * @param autoRequestPermission 진입 시 권한 다이얼로그를 자동으로 띄운다. 지도가 핵심인
 *   합승 탭(17~19)은 true, 검색이 핵심이라 다이얼로그가 방해되는 15 목적지 입력은 false 로 쓴다
 * @param updateIntervalMs 갱신 주기. 지도에 파란 점을 그리는 화면은 기본값
 *   [MapLocationIntervalMs](1초), 좌표를 한 번만 쓰는 화면은 [FormLocationIntervalMs] 를 준다
 */
@Composable
fun rememberMyLocationState(
    autoRequestPermission: Boolean = true,
    updateIntervalMs: Long = MapLocationIntervalMs,
): MyLocationState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    var coordinates by remember { mutableStateOf<UserCoordinates?>(null) }
    // 화면이 보이는 동안(STARTED 이상)에만 구독한다 — 1초 주기를 백그라운드로 끌고 가지 않는다
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    // 어떤 fix 를 채택할지 판단하는 상태. 구독이 끊겼다 붙어도(백그라운드 왕복) 기준이 유지돼야
    // 하므로 구독 이펙트 밖에서 remember 한다
    val gate = remember { FixGate() }
    // 화면 회전으로 다이얼로그가 다시 뜨지 않게 요청 사실을 보존한다
    var requested by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // 정밀·대략 중 하나만 허용돼도 지도 중심은 잡힌다
        granted = result.values.any { it }
    }

    // launcher 는 컴포지션 동안 동일 인스턴스라 콜백도 고정된다 (불필요한 재구성 방지)
    val requestPermission: () -> Unit = remember(launcher, activity) {
        {
            if (activity != null && requested && !activity.canPromptForLocation()) {
                // 영구 거부(USER_FIXED) 상태 — 시스템이 다이얼로그 없이 즉시 거부만 돌려준다.
                // 그대로 launch 하면 버튼이 아무 반응 없는 것처럼 보이므로 설정 화면으로 보낸다.
                // 설정에서 허용하고 돌아오면 아래 ON_RESUME 재확인이 상태를 받아온다.
                activity.openAppSettings()
            } else {
                requested = true
                launcher.launch(LocationPermissions)
            }
        }
    }

    LaunchedEffect(autoRequestPermission, granted) {
        if (autoRequestPermission && !granted && !requested) requestPermission()
    }

    // 설정 화면에서 권한을 바꾸고 돌아오는 경로 — 복귀할 때마다 실제 상태를 다시 읽는다.
    // 같은 옵저버가 화면 가시성(구독 on/off)도 함께 본다.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> visible = true
                Lifecycle.Event.ON_STOP -> visible = false
                Lifecycle.Event.ON_RESUME -> {
                    val now = context.hasLocationPermission()
                    if (now != granted) granted = now
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(granted, visible, updateIntervalMs, context) {
        if (!granted) {
            // 권한이 회수되면 마지막 좌표를 계속 쓰지 않는다
            coordinates = null
            gate.reset()
            onDispose {}
        } else if (!visible) {
            // 화면 밖에서는 구독하지 않는다. 마지막 좌표는 그대로 두어 복귀 시 빈 지도가 되지 않게 한다
            onDispose {}
        } else {
            val stop = startLocationUpdates(context, updateIntervalMs) { location ->
                // 좌표 자체가 무의미하면(널섬·NaN) 게이트 기준까지 오염되므로 먼저 거른다
                val fix = location.toUserCoordinatesOrNull() ?: return@startLocationUpdates
                // 정확도 게이트를 통과한 fix 만 마커를 옮긴다 (수백 m 튀는 셀타워 위치 차단)
                if (gate.accept(location)) coordinates = fix
            }
            onDispose { stop() }
        }
    }

    return MyLocationState(
        isGranted = granted,
        coordinates = coordinates,
        onRequestPermission = requestPermission,
    )
}

private fun Context.hasLocationPermission(): Boolean =
    LocationPermissions.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/**
 * 시스템이 아직 권한 다이얼로그를 띄워 줄지.
 *
 * 한 번 거부한 뒤에도 rationale 을 보여줄 수 있는 상태면 true. 영구 거부(USER_FIXED)면
 * false 가 되고, 이때 launch 를 해봐야 다이얼로그 없이 거부만 돌아온다.
 */
private fun Activity.canPromptForLocation(): Boolean =
    LocationPermissions.any { shouldShowRequestPermissionRationale(it) }

private fun Activity.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }
}

/**
 * 어떤 fix 를 화면에 반영할지 판단한다.
 *
 * 위치 제공자는 정확도가 제각각인 fix 를 섞어 보낸다. 들어오는 대로 다 쓰면 GPS 로 5m 까지
 * 좁혀 놓은 파란 점이 다음 순간 500m 짜리 네트워크 fix 로 끌려가 눈에 띄게 튄다.
 * 반대로 게이트만 두면 나쁜 환경에서 점이 영영 얼어붙는다 — 그래서 「개선 / 오래됨」 예외를 둔다.
 *
 * 컴포지션 동안 살아 있는 객체라 백그라운드 왕복으로 구독이 끊겼다 붙어도 기준이 유지된다.
 */
private class FixGate {
    private var hasFix = false
    private var acceptedAccuracy: Float? = null
    private var acceptedAtNanos = 0L

    fun reset() {
        hasFix = false
        acceptedAccuracy = null
        acceptedAtNanos = 0L
    }

    fun accept(location: Location): Boolean {
        val atNanos = location.elapsedRealtimeNanos
        // 제공자를 바꿔 탈 때 뒤늦게 도착하는 옛 fix — 시간을 거스르는 갱신은 하지 않는다
        if (hasFix && atNanos < acceptedAtNanos) return false

        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val accepted = when {
            // 아직 아무것도 못 보여 주고 있다 — 대략 위치가 빈 지도보다 낫다
            !hasFix -> true
            // 정확도를 모르면 판단 근거가 없다. 새 fix 를 믿는다
            accuracy == null -> true
            accuracy <= AccuracyGateM -> true
            // 게이트 밖이어도 지금 쓰는 값보다 정확하면 개선이다
            acceptedAccuracy == null || accuracy < acceptedAccuracy!! -> true
            // 오래 굶었으면 정확도를 양보한다
            atNanos - acceptedAtNanos >= StaleFixNanos -> true
            else -> false
        }
        if (accepted) {
            hasFix = true
            acceptedAccuracy = accuracy
            acceptedAtNanos = atNanos
        }
        return accepted
    }
}

/** 0,0(널섬)·NaN 같은 무의미한 fix 는 폴백보다 나쁘므로 버린다 */
private fun Location.toUserCoordinatesOrNull(): UserCoordinates? {
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    if (latitude == 0.0 && longitude == 0.0) return null
    return UserCoordinates(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy() && accuracy > 0f) accuracy else null,
        // 서 있을 때의 bearing 은 잡음이라 버린다 (방향 표시가 제자리에서 도는 것 방지)
        bearingDegrees = if (hasBearing() && (!hasSpeed() || speed >= MinBearingSpeedMps)) {
            bearing
        } else {
            null
        },
    )
}

/**
 * 현재 위치 구독을 시작하고, 해제 함수를 돌려준다.
 *
 * 1. 마지막으로 알려진 위치를 즉시 1회 흘려보낸다 — 첫 fix 를 기다리는 동안 지도가
 *    폴백 좌표에 머무르지 않게 한다. **캐시라 오래됐을 수 있다.**
 * 2. 그래서 곧바로 Fused Provider 의 `getCurrentLocation` 으로 **신규 측위 1회**를
 *    따로 요청한다. 캐시가 없거나(첫 실행) 낡았을 때 첫 정확한 좌표까지 걸리는 시간이
 *    주기 갱신 한 틱을 기다리는 것보다 짧다.
 * 3. Fused Provider 로 [intervalMs] 주기 갱신을 건다.
 * 4. Play services 가 없는 이미지(AOSP 에뮬레이터 등)에서는 프레임워크
 *    [LocationManager] 로 떨어진다.
 *
 * 어느 단계도 예외를 밖으로 던지지 않는다 — 실패하면 콜백이 호출되지 않을 뿐이다.
 */
@SuppressLint("MissingPermission") // 권한이 허용된 경우에만 호출된다 (rememberMyLocationState)
private fun startLocationUpdates(
    context: Context,
    intervalMs: Long,
    onLocation: (Location) -> Unit,
): () -> Unit {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    runCatching { locationManager?.lastKnownLocation() }.getOrNull()?.let(onLocation)

    val fused = runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull()
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(onLocation)
        }
    }
    // 신규 측위 1회 요청. 화면을 떠나면 취소해 측위가 백그라운드로 이어지지 않게 한다
    val firstFixCancel = CancellationTokenSource()
    val fusedTask = if (fused == null) {
        null
    } else {
        runCatching {
            // HIGH_ACCURACY = GPS 하드웨어를 켠다. BALANCED 로 두면 GMS 가 네트워크 위치만
            // 쓰기 때문에 gps provider 로 들어오는 fix(에뮬레이터 `adb emu geo fix` 포함)를
            // 아예 받지 못하고, 택시 픽업 지점으로 쓰기엔 오차도 크다.
            val request = LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                // 제공자가 더 빨리 줄 수 있으면 받는다. 주기의 절반을 하한으로 둬서
                // 콜백이 무한정 몰아치지는 않게 한다
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .setMinUpdateDistanceMeters(MinUpdateDistanceM)
                .build()
            fused.lastLocation.addOnSuccessListener { it?.let(onLocation) }
            runCatching {
                fused
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, firstFixCancel.token)
                    .addOnSuccessListener { it?.let(onLocation) }
            }
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.getOrNull()
    }

    if (fused == null || fusedTask == null) {
        return startFrameworkUpdates(locationManager, intervalMs, onLocation)
    }

    // 구독 등록 자체는 성공했지만 기기에 Play services 가 없거나 낡으면 Task 가 나중에 실패한다.
    // 그때 프레임워크 제공자로 갈아탄다 (콜백·해제 모두 메인 스레드라 경합은 없다).
    var stopped = false
    var frameworkStop: (() -> Unit)? = null
    fusedTask.addOnFailureListener {
        if (!stopped) frameworkStop = startFrameworkUpdates(locationManager, intervalMs, onLocation)
    }
    return {
        stopped = true
        runCatching { firstFixCancel.cancel() }
        runCatching { fused.removeLocationUpdates(callback) }
        frameworkStop?.invoke()
        frameworkStop = null
    }
}

/** Play services 없이 프레임워크 제공자만으로 구독한다 */
@SuppressLint("MissingPermission")
private fun startFrameworkUpdates(
    locationManager: LocationManager?,
    intervalMs: Long,
    onLocation: (Location) -> Unit,
): () -> Unit {
    if (locationManager == null) return {}
    // API 29 이하 프레임워크는 default 메서드를 호출하지 못해 SAM 람다로 만들면
    // AbstractMethodError 가 난다 (minSdk 24) — 4개를 모두 구현한다
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)

        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }
    FrameworkProviders
        .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        .forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    intervalMs,
                    MinUpdateDistanceM,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }
    return { runCatching { locationManager.removeUpdates(listener) } }
}

private val FrameworkProviders = listOf(
    LocationManager.GPS_PROVIDER,
    LocationManager.NETWORK_PROVIDER,
    LocationManager.PASSIVE_PROVIDER,
)

@SuppressLint("MissingPermission")
private fun LocationManager.lastKnownLocation(): Location? =
    FrameworkProviders
        .mapNotNull { provider -> runCatching { getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
