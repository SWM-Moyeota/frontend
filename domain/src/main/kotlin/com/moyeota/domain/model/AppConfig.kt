package com.moyeota.domain.model

/**
 * 서버가 앱 시작 시 알려주는 운영 설정(`GET /api/v1/config`).
 *
 * @param taxiEnabled 기사(택시) 기능이 켜져 있는가. **false 면 1차 배포 모드** — 정원이 차도 기사를 부르지 않고
 *   방은 `COMPLETED` 에 머문다. 참여자가 「합승 완료」로 닫거나 30분 방치 시 서버가 닫는다.
 *   true(발표·평가 모드) 면 정원이 차는 즉시 서버가 기사 매칭을 시작한다.
 */
data class AppConfig(
    val taxiEnabled: Boolean,
) {
    companion object {
        /**
         * 설정을 못 받았을 때의 값. 서버 기본값(`moyeota.taxi.enabled` 미설정 = true)과 같게 둔다 —
         * 잘못 판단해도 「기사 대기 화면에서 안 넘어감」이지, 「기사 배정된 방을 합승 완료로 닫음」은 아니다.
         */
        val Default = AppConfig(taxiEnabled = true)
    }
}
