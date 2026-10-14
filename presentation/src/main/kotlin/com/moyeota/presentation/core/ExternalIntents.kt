package com.moyeota.presentation.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * 앱 밖으로 나가는 이동 — OS 알림 설정 · 메일 앱 · 브라우저.
 *
 * 화면이 아니라 여기 모아 둔 이유는 두 가지다.
 * 1. **받아 줄 앱이 없으면 Intent 는 던진다**([ActivityNotFoundException]). 메일 앱이 지워진 기기,
 *    브라우저가 없는 에뮬레이터 이미지가 실제로 있다. 그 처리를 행마다 되풀이하면 한 곳만
 *    빠뜨려도 마이페이지에서 크래시가 난다 — 여기서 전부 잡아 안내 토스트로 바꾼다.
 * 2. 화면은 「무엇을 누르면 어디로 나간다」만 알면 된다. 버전·기종 같은 환경 수집은 UI 가 아니다.
 *
 * 안내를 스낵바가 아니라 토스트로 내는 이유: 35 마이페이지에는 Scaffold·SnackbarHost 가 없고,
 * 이 안내는 화면이 보관할 상태가 아니라 "방금 그 동작이 안 됐다"는 일회성 사실이다.
 * 호스트를 세우려면 화면 골격을 바꿔야 하는데, 지금 이 한 줄 때문에 그럴 값은 아니다.
 */

/** 문의 메일 제목 — 받는 쪽에서 필터·분류할 수 있게 고정한다. */
private const val SUPPORT_MAIL_SUBJECT = "[모여타 문의]"

/**
 * 설치된 패키지의 versionName (`1.0.0`). 읽기 실패하면 null.
 *
 * 35 의 버전 표기와 문의 메일 본문이 같은 값을 써야 하므로 한 곳에 둔다
 * — 두 곳에서 각자 읽으면 한쪽만 포맷이 바뀌어도 사용자가 알려 주는 버전과 표기가 어긋난다.
 */
fun Context.appVersionName(): String? = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull()

/**
 * OS 의 **앱별 알림 설정**을 연다.
 *
 * 앱 안에 알림 on/off 를 두지 않는 이유: 서버에 알림 설정 저장 API 가 없다. 앱이 자체 토글을
 * 들고 있으면 꺼 둔 채 기기를 바꾸면 되살아나고, 서버는 그대로 푸시를 보낸다 — 지키지 못할 약속이다.
 * OS 설정은 채널 단위로 실제로 적용되는 **유일한 진짜 스위치**라 그쪽으로 넘긴다.
 *
 * API 26 부터 앱별 알림 화면이 생겼다. 그 아래(24·25)는 앱 상세 설정으로 폴백한다
 * — 거기서도 「알림」 항목으로 들어갈 수 있다.
 */
fun Context.openAppNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    }
    startActivitySafely(intent, "설정 앱을 열 수 없어요. 휴대폰 설정 → 앱 → 모여타 → 알림에서 바꿀 수 있어요")
}

/**
 * 문의 메일을 **제목과 환경 정보까지 채운 채** 메일 앱으로 띄운다.
 *
 * `ACTION_SENDTO` + `mailto:` 를 쓴다 — `ACTION_SEND` 로 보내면 메신저·클라우드까지 후보로 떠서
 * 사용자가 문의를 메일이 아닌 곳으로 보내게 된다. 주소는 URI 에 넣고 제목·본문은 extra 로 준다:
 * 쿼리스트링만 쓰면 일부 메일 앱이 제목을 무시한다.
 */
fun Context.openSupportEmail(address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(address)}")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        putExtra(Intent.EXTRA_SUBJECT, SUPPORT_MAIL_SUBJECT)
        putExtra(
            Intent.EXTRA_TEXT,
            supportMailBody(
                appVersion = appVersionName(),
                androidRelease = Build.VERSION.RELEASE,
                deviceModel = Build.MODEL,
            ),
        )
    }
    // 메일 앱이 없으면 주소라도 알려 준다 — 문의할 길이 아예 없는 것보다 낫다
    startActivitySafely(intent, "메일 앱이 없어요. $address 로 보내 주세요")
}

/** 외부 웹 페이지(약관 · 개인정보 처리방침)를 기본 브라우저로 연다. */
fun Context.openWebPage(url: String) {
    startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "브라우저를 열 수 없어요. $url 을 직접 열어 주세요")
}

/**
 * 문의 메일 본문 — 사용자가 적을 자리를 위에 비워 두고, 아래에 환경 정보를 미리 채운다.
 *
 * 환경 정보를 넣는 이유: 없으면 문의 한 건마다 "앱 버전·기종이 무엇인가요"를 되묻는 왕복이
 * 한 번 더 생긴다. 읽기에 실패한 값은 비워 두지 않고 「알 수 없음」으로 적는다
 * — 줄이 사라지면 받는 쪽은 사용자가 지운 것인지 앱이 못 읽은 것인지 구분할 수 없다.
 */
internal fun supportMailBody(appVersion: String?, androidRelease: String?, deviceModel: String?): String =
    buildString {
        appendLine()
        appendLine()
        appendLine("--- 아래는 확인용 정보예요. 지우지 않고 보내 주세요 ---")
        appendLine("앱 버전: ${appVersion ?: "알 수 없음"}")
        appendLine("Android: ${androidRelease ?: "알 수 없음"}")
        append("기기: ${deviceModel ?: "알 수 없음"}")
    }

private fun Context.startActivitySafely(intent: Intent, fallbackMessage: String) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, fallbackMessage, Toast.LENGTH_LONG).show()
    }
}
