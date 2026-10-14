package com.moyeota.presentation.core

/**
 * 앱이 지금 어느 모드로 화면을 그리는가 — 동승(실배포) / 택시(개발 중).
 *
 * 값은 서버 설정([com.moyeota.domain.model.AppConfig.taxiEnabled]) 하나에서 나오고, 디버그 빌드에서만
 * 개발자가 강제로 덮어쓸 수 있다(35 마이페이지의 개발자 옵션). 모드가 가르는 것은 **문구와 버튼**이다
 * — 21 대기 화면의 「합승 완료」/「기사님 찾는 중」, 나가기 허용 범위. 어느 화면으로 가는지는
 * 서버가 주는 방 status 가 정한다. 모드별 화면 표는 `docs/MODE-ROUTES.md`.
 */
enum class AppMode {
    /** 동승 모드 — 사람을 모아 채팅으로 만나고 「합승 완료」로 끝낸다. 실사용자가 쓰는 모드. */
    COMPANION,

    /** 택시 모드 — 정원이 차면 서버가 기사를 부르고 25 배차 → 26 운행 → 33 도착으로 이어진다. */
    TAXI,
    ;

    val taxiEnabled: Boolean get() = this == TAXI

    val label: String get() = if (this == TAXI) "택시" else "동승"

    companion object {
        fun of(taxiEnabled: Boolean): AppMode = if (taxiEnabled) TAXI else COMPANION
    }
}

/**
 * 화면이 실제로 따를 택시 모드 값. 디버그 강제값([override])이 있으면 그것, 없으면 서버 값([server]).
 * 릴리스 빌드는 강제값을 세울 UI 가 없어 항상 서버 값이다.
 */
internal fun effectiveTaxiEnabled(server: Boolean, override: Boolean?): Boolean = override ?: server

/**
 * 진행 중 방의 단계가 지금 모드와 어긋나는가 — 동승 모드인데 서버가 배차·운행 단계를 줬다.
 *
 * 동승 모드 서버는 `DISPATCHING`·`ONGOING` 을 내지 않는다. 그런데도 보인다면 서버는 택시 모드이고
 * 앱이 든 설정이 낡은 것이다(시작 시 조회 실패 → 기본값, 또는 운영 중 전환). 이때 **status 를 따른다**
 * — 실제로 기사가 오고 있는 방을 21 대기 화면에 가둬 두는 쪽이 더 큰 사고다. 대신 설정을 다시 읽어
 * 다음 화면부터 문구가 맞게 한다.
 */
internal fun isStageOutsideMode(stage: ActiveStage, taxiEnabled: Boolean): Boolean =
    !taxiEnabled && stage in TaxiOnlyStages

private val TaxiOnlyStages = setOf(ActiveStage.DRIVER_SEARCH, ActiveStage.DRIVER_COMING, ActiveStage.ONGOING)
