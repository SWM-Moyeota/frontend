package com.moyeota.presentation.core

import android.content.Context
import android.os.Build
import com.moyeota.domain.model.AppConfig

/**
 * 서버가 요구하는 버전과 설치된 버전을 견줘 무엇을 할지 고른다.
 *
 * ### 왜 이게 **첫 게시 전에** 들어가야 하나
 * 앱이 스토어에 한 번 나가면 그 코호트는 영영 업데이트를 강제할 수 없다. 나중에 서버가
 * `minVersionCode` 를 내려줘도 **그 필드를 모르는 빌드**는 조용히 무시하기 때문이다
 * (`Json { ignoreUnknownKeys = true }`). 서버 계약이 깨지는 변경을 해야 하는 날,
 * 그 사용자들은 영구히 고장난 앱을 들고 있게 된다. 되돌릴 수 없는 유일한 문이라 미리 만들어 둔다.
 *
 * ### 막지 않는 경우가 더 중요하다
 * 네 경우에 **모두 통과**다. 하나라도 틀리면 멀쩡한 사용자가 잠긴다.
 * 1. 서버가 필드를 안 준다 → null → 통과. **지금 서버가 이 상태다.**
 * 2. 설정 조회가 실패했다 → [AppConfig.Default] 의 두 필드가 null → 통과.
 *    서버 장애로 전원이 잠기는 쪽이 낡은 앱이 도는 것보다 훨씬 나쁘다.
 * 3. 설치 버전을 못 읽었다 → [UNKNOWN_VERSION_CODE] → 통과.
 * 4. 서버가 0 이나 음수를 줬다 → 통과. 실수로 0 을 넣어 전원을 잠그는 사고를 막는다.
 */
enum class UpdateRequirement {
    /** 아무것도 하지 않는다. */
    NONE,

    /** 한 번 알리고 넘어간다. 막지 않는다. */
    RECOMMENDED,

    /** 빠져나갈 수 없는 화면을 띄운다. */
    BLOCKED,
}

/** 설치 버전을 읽지 못했을 때의 값. 이 값이면 **어떤 기준으로도 막지 않는다.** */
const val UNKNOWN_VERSION_CODE = -1L

/**
 * 순수 함수 — 네트워크·Context 없이 테스트된다.
 *
 * 차단이 권장보다 **먼저** 판정된다. 서버가 둘 다 줬고 설치 버전이 둘 다 밑돌면 차단이다.
 */
fun updateRequirement(config: AppConfig, installedVersionCode: Long): UpdateRequirement {
    if (installedVersionCode <= 0L) return UpdateRequirement.NONE
    if (config.minVersionCode.isBlocking(installedVersionCode)) return UpdateRequirement.BLOCKED
    if (config.recommendedVersionCode.isBlocking(installedVersionCode)) return UpdateRequirement.RECOMMENDED
    return UpdateRequirement.NONE
}

/** 서버가 준 기준이 쓸 만하고, 설치 버전이 그 아래인가. 0·음수·null 은 「기준 없음」이다. */
private fun Int?.isBlocking(installedVersionCode: Long): Boolean =
    this != null && this > 0 && installedVersionCode < this.toLong()

/**
 * 설치된 `versionCode`. 못 읽으면 [UNKNOWN_VERSION_CODE] 다.
 *
 * presentation 모듈에는 app 의 `BuildConfig` 가 없다 — 35 마이페이지가 버전 이름을 얻는 길
 * ([appVersionName])과 **같은 방식**으로 PackageManager 에서 읽는다.
 *
 * `longVersionCode` 는 API 28 부터다. minSdk 가 24 라 그 아래에서는 deprecated 된
 * `versionCode` 를 쓴다 — 여기서 분기를 빼면 24~27 에서 `NoSuchMethodError` 로 **죽는다.**
 */
fun Context.appVersionCode(): Long = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
}.getOrDefault(UNKNOWN_VERSION_CODE)
