package com.moyeota.domain.model

/**
 * 매칭방 변화 신호(SSE `GET /api/v1/matching/rooms/{partyId}/events`).
 *
 * 서버는 이제 **「바뀌었다」가 아니라 무엇이 바뀌었는지**를 싣는다(Backend #218). 예전 `changed`
 * 이벤트는 **사라졌다** — 신호를 받을 때마다 방 상세를 다시 읽던 구조가, 대기 화면 폴링과 합쳐
 * 서버 요청의 절반 가까이를 차지했기 때문이다(Backend #177 부하테스트에서 상세 폴링만 45%).
 *
 * 그래서 이 타입은 「재조회 트리거」가 아니라 **그 자체로 화면을 갱신할 수 있는 변경분**이다.
 * - 구독 직후 [Snapshot] 이 방 상세 **전체**를 준다. 끊겨 다시 붙을 때마다 또 온다 — 그 사이
 *   놓친 변화를 스냅샷이 통째로 보정하므로 **catch-up 조회도, 평상시 폴링도 필요 없다**.
 * - 그 뒤로는 [MemberJoined]·[MemberLeft] 로 한 명씩 들고 나는 것만 온다. 둘 다 **변경 후 방 상태**를
 *   함께 실어 주므로, 정원이 차서 생기는 전이(모집 중 → 정원 충족/배차 시작)도 이 신호로 알 수 있다.
 *
 * 반면 **배차·운행 전이(MATCHING→DRIVER_ASSIGNED→IN_RIDE→FINISHED)는 이 스트림에 오지 않는다** —
 * 서버의 발행처가 `PartyService` 의 참여·퇴장·종료(join/leave/finish/expire)뿐이다. 그 단계를 보는
 * 화면(25 배차 현황·26 운행)은 폴링을 유지해야 한다.
 *
 * 순서가 뒤바뀌거나 신호가 유실될 수 있다(Redis Pub/Sub 은 at-most-once). 재연결이 곧 [Snapshot] 이므로
 * 어긋남은 다음 연결에서 바로잡힌다 — 다만 연결이 **살아 있는 동안** 어긋났다면 바로잡을 길이 없으니,
 * 화면은 느린 안전망 확인(`GET /rooms/{id}/status` 의 지문 비교)을 [Disconnected] 때만이 아니라
 * 아주 긴 주기로 남겨 두는 선택을 할 수 있다.
 */
sealed interface PartyEvent {
    /**
     * 구독 직후 서버가 준 방 상세 전체(`connected` 이벤트의 data = `PartyDetailResult`).
     *
     * **재연결 때마다 다시 오므로 catch-up 조회가 필요 없다** — 끊긴 사이 들고 나간 사람, 그동안의
     * 상태 전이가 모두 이 한 덩어리에 반영돼 있다. 화면은 이 [ride] 로 들고 있던 상태를 **덮으면** 된다.
     */
    data class Snapshot(val ride: Ride) : PartyEvent

    /**
     * 누가 들어왔다(`member-joined`). [member] 는 방 상세의 멤버와 **같은 모양**이라 그대로 목록에 덧붙일 수 있고,
     * [User.isMe] 판정도 상세와 같은 근거(publicId == 세션 uuid)로 채워져 온다.
     *
     * [status] 는 **들어온 뒤의 방 상태**다. 마지막 자리가 채워지면 서버가 그 자리에서 정원 충족 처리까지
     * 끝내고 그 결과를 싣기 때문에, 「정원이 찼다」는 전이도 이 신호 하나로 알 수 있다.
     */
    data class MemberJoined(val member: User, val status: RideStatus) : PartyEvent

    /**
     * 누가 나갔다(`member-left`). [status] 는 나간 뒤의 방 상태다.
     *
     * **[publicId] 가 null 이면 누가 나갔는지 서버도 모른다** — 탈퇴 등으로 유저 요약을 찾지 못한 경우이고,
     * 서버는 그래도 신호는 보낸다. 그때 화면은 지울 멤버를 특정할 수 없으므로 **상세를 다시 받아야 한다**.
     *
     * 내가 나간 경우에는 이 신호가 오지 않는다 — 서버가 내 연결을 **이벤트 없이** 끊는다(`closeMember`).
     *
     * [hostPublicId] 는 **승계 후의 방장**이다(Backend #240). 서버의 방장은 현재 멤버 중
     * `min(joinedAt, memberId)` 라 방장이 나가면 다음으로 이른 멤버가 자동 승계되는데, 그 사실이
     * 실리는 자리가 여기다 — 이 값을 버리면 방장이 나간 뒤 승계된 사람이 자기 권한을 모른다
     * (서버는 이 신호를 다시 보내지 않으므로 다음 스냅샷까지 영영 모른다).
     *
     * **null 이면 서버도 새 방장의 publicId 를 말하지 못했다** — 승계된 멤버의 유저 요약을 찾지
     * 못한 경우다(`PartyService.leave` 의 `hostSummary == null`). 로그인한 본인의 요약은 늘 찾히므로
     * null 은 사실상 "새 방장은 내가 아니다" 와 같고, 그때는 들고 있던 방장 표시를 **그대로 둔다**.
     */
    data class MemberLeft(
        val publicId: String?,
        val status: RideStatus,
        val hostPublicId: String? = null,
    ) : PartyEvent

    /**
     * 방이 닫혔다(`closed` — 합승 완료·만료·마지막 멤버 이탈). 서버가 이 뒤에 그 방의 모든 구독을 끊는다.
     *
     * 이 이벤트의 data 는 JSON 이 아니라 partyId 숫자 문자열이라 실을 값이 없다 — 종결 신호로만 쓴다.
     */
    data object Closed : PartyEvent

    /** 연결이 끊겼다(서버 신호가 아니라 앱이 알아챈 상태). 곧 다시 붙고, 그때 [Snapshot] 이 전체를 보정한다 */
    data object Disconnected : PartyEvent
}
