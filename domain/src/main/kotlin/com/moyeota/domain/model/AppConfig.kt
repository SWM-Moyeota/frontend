package com.moyeota.domain.model

/**
 * 서버가 앱 시작 시 알려주는 운영 설정(`GET /api/v1/config`).
 *
 * @param taxiEnabled 기사(택시) 기능이 켜져 있는가. **false 면 동승 모드(실배포)** — 정원이 차도 기사를 부르지 않고
 *   방은 `COMPLETED` 에 머문다. 참여자가 「합승 완료」로 닫거나 30분 방치 시 서버가 닫는다.
 *   true(택시 모드 — 개발·발표·평가) 면 정원이 차는 즉시 서버가 기사 매칭을 시작하고 25 배차 → 26 운행으로 이어진다.
 *
 *   앱은 이 값으로 **문구와 버튼만** 가른다. 어느 화면으로 갈지는 서버가 주는 방 status 가 정한다
 *   (동승 모드 서버는 `DISPATCHING`·`ONGOING` 을 내지 않으므로 택시 화면에 닿을 길이 없다).
 */
data class AppConfig(
    val taxiEnabled: Boolean,
) {
    companion object {
        /**
         * 설정을 못 받았을 때의 값 — **동승 모드(false)**.
         *
         * 실사용자가 쓰는 서버는 동승 모드다. 서버가 응답하지 못하는 순간(장애·네트워크)에 택시 모드 문구
         * (「기사님을 찾고 있어요」)가 뜨면 실제 서비스와 다른 화면을 보여주게 된다. 반대로 택시 모드 서버에서
         * 이 기본값에 걸리면 정원이 찬 짧은 순간 「합승 완료」 버튼이 보일 뿐이고, 서버는 기사 매칭이 시작된
         * 방의 완료 호출을 409 로 거절한다 — 사고가 아니라 문구 어긋남이다.
         */
        val Default = AppConfig(taxiEnabled = false)
    }
}
