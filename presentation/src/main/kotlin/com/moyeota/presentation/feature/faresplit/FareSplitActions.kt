package com.moyeota.presentation.feature.faresplit

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 정산 화면이 **앱 밖으로 나가는** 두 가지 — 토스 송금과 클립보드 복사.
 *
 * 화면이 아니라 여기 모아 둔 이유는 `ExternalIntents.kt` 와 같다: 받아 줄 앱이 없으면 Intent 는
 * 던지고([ActivityNotFoundException]), 그 처리를 행마다 되풀이하면 한 곳만 빠뜨려도 크래시가 된다.
 * 다만 **같은 파일에 두지는 않았다** — 그쪽은 실패를 토스트로 삼키고 끝내는데, 이쪽의 실패는
 * 화면이 **다음 길을 가리켜야 하는** 사실이라 호출부로 돌려줘야 한다([TOSS_OPEN_FAILED_NOTICE]).
 *
 * ### 로그를 한 줄도 남기지 않는다 ★
 * 두 함수가 다루는 값은 **계좌번호와 송금 딥링크**다(딥링크 쿼리에 `accountNo` 가 그대로 들어 있다).
 * 실패를 디버깅하려고 `Log.d(TAG, deeplink)` 를 한 줄 넣으면 그 계좌번호가 logcat 으로 새어 나가고,
 * 서버는 같은 값을 **암호화해 저장하며 정산이 끝나면 지운다.** 서버가 지키는 것을 앱이 흘리면
 * 그 보호가 무의미해진다. 그래서 여기서는 성공/실패 불리언만 돌려주고, 무엇이 실패했는지는
 * 화면이 자기 문맥으로 말한다.
 */

/**
 * 토스 송금 화면을 연다. **열지 못했으면 false** — 그때 화면은 복사 버튼을 가리킨다.
 *
 * ### 실패가 정상 경로다
 * 서버 주석 원문: 「비공식 규격이라 열리지 않을 수 있어 계좌·금액 복사 버튼을 함께 둔다」.
 * `supertoss://` 를 받아 줄 앱이 없는 기기(토스 미설치·에뮬레이터)가 흔하고, 토스가 스킴을 바꿔도
 * 같은 결과가 된다. 그래서 이 false 는 「오류」가 아니라 **분기**다.
 *
 * [deeplink] 를 앱이 만들지 않는다 — 서버가 준 문자열을 그대로 받는다
 * ([com.moyeota.domain.model.FareSplitDetail.tossDeeplink] KDoc).
 *
 * `resolveActivity` 로 미리 묻지 않는 이유: API 30 부터 패키지 가시성 제한 때문에 `queries` 선언이
 * 없으면 **설치돼 있어도 null** 을 준다. 그러면 토스가 있는 기기에서 버튼이 사라진다.
 * `startActivity` 는 암시적 VIEW 인텐트에 그 제한을 받지 않으므로 **던지게 하고 잡는다.**
 *
 * ### `supertoss` 스킴만 연다 ★ (PR #106 리뷰)
 * 서버 문자열은 신뢰 경계 밖이다. 서버 오류나 중간자로 `https://…`(피싱 페이지)·`intent://`·다른 앱
 * 스킴이 들어오면, 금액·계좌가 떠 있는 화면에서 사용자를 그대로 엉뚱한 곳으로 보낸다. 그래서
 * 스킴이 [TOSS_DEEPLINK_SCHEME] 가 아니면 **열지 않고 false** — 화면은 「열지 못했다」와 같은 길
 * (복사 버튼)로 간다. `SecurityException`(내보내지 않은 액티비티로 해석되는 URI)도 같은 실패로 받는다.
 */
internal fun Context.openTossTransfer(deeplink: String): Boolean {
    if (!isTossDeeplink(deeplink)) return false

    return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deeplink)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/** 토스 송금 딥링크의 스킴. 서버 `tossDeeplink` 주석의 `supertoss://send?…` 와 같다. */
internal const val TOSS_DEEPLINK_SCHEME = "supertoss"

/**
 * 열어도 되는 토스 딥링크인가 — **스킴이 정확히 [TOSS_DEEPLINK_SCHEME]** 이어야 한다(대소문자 무시).
 * `Uri` 를 쓰지 않는 순수 함수라 단위 테스트가 값으로 못 박는다(`FareSplitActionGateTest`).
 */
internal fun isTossDeeplink(deeplink: String): Boolean {
    val schemeEnd = deeplink.indexOf("://")
    if (schemeEnd <= 0) return false
    return deeplink.substring(0, schemeEnd).equals(TOSS_DEEPLINK_SCHEME, ignoreCase = true)
}

/**
 * 클립보드에 한 줄 복사한다. 복사하지 못했으면 false다.
 *
 * ### 토스트를 띄우지 않는다
 * API 33(Android 13)부터 **OS 가 스스로 복사 확인 UI** 를 띄운다. 거기에 앱 토스트를 더하면 같은
 * 사실을 두 번 말하고, 그 아래의 계좌번호를 가린다. 그래서 피드백은 **버튼 글자를 잠깐 바꾸는
 * 것**으로 하고(그 자리에서 보인다, API 32 이하에서도 유일한 신호가 된다) 이 함수는 결과만 돌려준다.
 *
 * [ClipboardManager.setPrimaryClip] 을 감싸는 이유: 기기 정책(기업 MDM·일부 OEM)이 클립보드를 막으면
 * 던진다. 그때 「복사했어요」를 띄우면 **붙여넣기가 비어 있는데 성공했다고 말한** 것이 된다.
 *
 * @param label 클립보드 항목 설명(「계좌번호」·「금액」). 일부 런처가 미리보기에 쓴다 —
 *   **값을 넣지 말 것**(계좌번호가 알림 영역에 노출된다).
 */
internal fun Context.copyToClipboard(label: String, text: String): Boolean {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false

    return runCatching { manager.setPrimaryClip(ClipData.newPlainText(label, text)) }.isSuccess
}
