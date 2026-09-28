package com.moyeota.domain.model

/**
 * 매칭방 변화 신호(SSE `GET /matching/rooms/{id}/events`). 서버는 상태를 싣지 않고 **"바뀌었다"만** 알린다 —
 * 받으면 [RideRepository.getPartyDetail][com.moyeota.domain.repository.RideRepository.getPartyDetail] 로 다시 읽는다.
 * 순서가 뒤바뀌거나 재연결 중 놓쳐도 재조회 한 번으로 복구되게 한 설계다.
 */
sealed interface PartyEvent {
    /** 연결이 (다시) 붙었다. 끊긴 사이 놓친 변화가 있을 수 있으니 한 번 다시 읽어야 한다 */
    data object Connected : PartyEvent

    /** 멤버가 들어오거나 나갔다 */
    data object Changed : PartyEvent

    /** 방이 닫혔다(합승 완료·만료·마지막 멤버 이탈). 서버가 이 뒤에 연결을 끊는다 */
    data object Closed : PartyEvent

    /** 연결이 끊겼다. 곧 다시 붙는다 — 그동안은 폴링이 메운다 */
    data object Disconnected : PartyEvent
}
