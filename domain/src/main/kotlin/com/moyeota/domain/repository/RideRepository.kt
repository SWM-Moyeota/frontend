package com.moyeota.domain.repository

import com.moyeota.domain.model.AssignedDriver
import com.moyeota.domain.model.NewParty
import com.moyeota.domain.model.PartyEvent
import com.moyeota.domain.model.PartyFinishException
import com.moyeota.domain.model.PartyJoinException
import com.moyeota.domain.model.PartyStatus
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RouteEstimate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface RideRepository {
    fun getNearbyParties(): List<Ride>

    // 조회 — GET /api/v1/matching/rooms, /rooms/{partyId}
    /** 목록 응답에는 좌표·요금·경로가 없다(partyId/출발/도착/인원/정원/상태만). 상세는 [getPartyDetail]. */
    suspend fun getParties(): List<Ride>

    /**
     * 지도 범위(남서 [swLat]/[swLng] ~ 북동 [neLat]/[neLng]) 안의 모집 중 방 목록.
     * GET /api/v1/matching/rooms?swLat=&swLng=&neLat=&neLng=
     *
     * [getParties] 와 같은 [Ride] 목록을 돌려주되, **출발 좌표(originLat/originLng)가 채워져 있다**
     * — 지도 핀을 찍는 데 쓴다. 도착 좌표·요금·경로는 여전히 없으니 [getPartyDetail] 로 받는다.
     */
    suspend fun getPartiesWithin(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double,
    ): List<Ride>

    /**
     * GET /api/v1/matching/rooms/{partyId} — 방 상세. 참여자가 아니어도 되고(403 없음),
     * 방이 없으면 404 PARTY_NOT_FOUND.
     *
     * **주기 조회가 필요한 화면(25 배차 상태·26 운행 중)은 이걸 부른다.** 예전에는 상태·인원·지문만
     * 담은 가벼운 `GET /rooms/{id}/status` 를 읽고 지문이 달라진 주기에만 상세를 읽었지만,
     * 그 엔드포인트가 백엔드에서 사라졌다(Backend `04f6be7` — 매칭방 변화는 SSE 가 유일한 전달 경로가
     * 되면서 폴링용으로 만든 상태 조회와 지문이 쓰이지 않게 됐다).
     *
     * 그래서 **이 호출은 비싸다**: 서버가 쿼리 3개(방 + 멤버 요약 + 탑승 횟수)를 돌리고 경로 폴리라인까지
     * 실어 온다. 부하테스트에서 방 상세 폴링이 전체 요청의 45% 였다(Backend #177). 주기 조회에 쓰는
     * 화면은 **간격을 충분히 벌리고**(25·26 은 7·8초), 받은 [Ride] 가 들고 있던 것과 `==` 로 같으면
     * 화면 상태를 갱신하지 않아야 한다(불필요한 recomposition 방지).
     */
    suspend fun getPartyDetail(partyId: Long): Ride

    /**
     * 이 방의 **변경분(delta) 스트림**(SSE `GET /api/v1/matching/rooms/{partyId}/events`).
     *
     * 수집하는 동안만 연결되고, 끊기면 알아서 다시 붙는다. 멤버가 아니거나(403) 방이 없으면(404)
     * 스트림이 끝난다(재시도하지 않는다). 방이 닫히면 [PartyEvent.Closed] 뒤에 끝난다.
     *
     * **받는 순서는 늘 [PartyEvent.Snapshot] → 그 뒤의 변경분이다.** 구독하자마자 서버가 방 상세 전체를
     * 스냅샷으로 주고, 그 뒤로 [PartyEvent.MemberJoined]·[PartyEvent.MemberLeft] 만 온다. 끊겨 다시
     * 붙을 때도 **스냅샷이 먼저** 오므로, 그동안 놓친 변화는 거기서 전부 보정된다.
     *
     * 그래서 **화면은 평상시 폴링도, 재연결 직후의 catch-up 조회도 하지 않아야 한다** — 이번 설계의 핵심이다.
     * 신호마다 [getPartyDetail] 을 다시 읽던 예전 구조(서버 `changed` 이벤트)는 **없어졌다**:
     * 대기 화면의 상세 폴링이 서버 요청의 45% 였고(Backend #177), 변경분이 오는 지금은 그 재조회가
     * 같은 내용을 다시 받는 낭비다. 폴링은 [PartyEvent.Disconnected] 동안의 **폴백**으로만 남긴다
     * (채팅 소켓과 같은 정책 — 살아 있으면 0회, 죽었을 때만).
     *
     * 다시 읽어야 하는 경우는 하나뿐이다: [PartyEvent.MemberLeft] 의 publicId 가 null 일 때
     * (누가 나갔는지 서버도 모른다).
     *
     * ⚠️ **같은 방을 두 곳에서 동시에 수집하지 말 것.** 서버가 구독을 받을 때 **같은 사용자의 기존 연결을
     * 먼저 끊는다**(`PartySseRegistry.closeMember`) — 한 화면이 두 번 수집하면 두 연결이 서로를 끊고
     * 번갈아 재연결하는 무한 루프가 된다. 수집은 화면(ViewModel)당 **정확히 한 번**이다.
     *
     * ⚠️ **이 스트림은 best-effort 다.** 서버가 커밋 후 단일 스레드 큐로 발행하고 **실패하면 재발행하지
     * 않는다**(Redis Pub/Sub 도 at-most-once). 그래서 "SSE 가 유일한 근거"인 화면은 만들지 않는다 —
     * 끊김 동안의 폴백 폴링과, 재연결 [PartyEvent.Snapshot] 의 전체 보정이 반드시 함께 있어야 한다.
     *
     * **배차·운행 전이는 이 스트림에 오지 않는다** — 서버 발행처가 참여·퇴장·종료뿐이라
     * MATCHING→DRIVER_ASSIGNED→IN_RIDE→FINISHED 는 신호가 없다(FCM 도 `DRIVER_ARRIVED` 하나뿐).
     * 그 단계의 화면(25·26)은 폴링을 유지하며, 읽는 대상은 [getPartyDetail] 이다 — 그 화면들이 쓰던
     * 가벼운 상태 조회가 없어졌기 때문이다(Backend `04f6be7`).
     *
     * 기본 구현은 아무것도 내지 않는다(더미·테스트 저장소).
     */
    fun observePartyEvents(partyId: Long): Flow<PartyEvent> = emptyFlow()

    // 액션 — 성공 후 화면이 갱신해야 하는 대상은 아래 주석 참고.
    /**
     * POST /api/v1/matching/rooms. 생성된 방을 그대로 돌려주므로 MatchWaiting 으로 바로 넘길 수 있다.
     *
     * 생성자는 **로그인 토큰이 정한다**(@CurrentUser) — 합류·나가기와 같다. 요청 본문에 사용자 id 를
     * 싣지 않는다. 생성 응답에는 멤버 목록이 없어 인원 수만큼 자리 표시용 멤버가 채워지므로,
     * 실제 멤버 식별자가 필요하면 [getPartyDetail] 로 다시 읽는다.
     *
     * 앱이 정하는 조건은 **좌표·라벨·정원뿐**이다([NewParty]) — 반경은 서버가 고정값으로 정한다.
     */
    suspend fun createParty(request: NewParty): Ride

    /**
     * 합류. POST /api/v1/matching/rooms/{partyId}/join.
     * 합류자는 **로그인 토큰이 정한다**(@CurrentUser) — memberId 를 넘기지 않는다.
     *
     * **돌려주는 것은 방 상세가 아니라 합류 직후의 [PartyStatus](상태·인원)다**(Backend #213).
     * 예전엔 응답이 곧 방 상세라 그대로 다음 화면에 넘길 수 있었지만, 이제 **동승자 목록이 오지 않는다** —
     * 목록이 필요하면 [getPartyDetail] 을 따로 불러야 한다. 다만 합류 후 바로 대기 화면(21)으로 가는
     * 흐름이라면 그 화면이 구독하는 [observePartyEvents] 의 첫 [PartyEvent.Snapshot] 이 상세 전체를
     * 주므로, 재조회를 거기에 맡기는 선택도 가능하다.
     *
     * 돌려주는 [PartyStatus.status] 로 "내가 마지막 자리를 채웠는지"를 알 수 있다 — 정원이 차면 서버가
     * 그 자리에서 정원 충족 처리까지 끝낸다(COMPLETED, 기사 모드면 이어서 MATCHING). 앱은 트리거하지 않는다.
     *
     * 실패는 모두 [PartyJoinException] 으로 올라온다(401 미로그인 / 409 `ALREADY_JOINED_OTHER_PARTY`·
     * `PARTY_FULL`·`PARTY_CLOSED` / 404 `PARTY_NOT_FOUND` / 네트워크). 화면은 그 프로퍼티로 분기한다.
     *
     * **그중 하나는 실패가 아니다.** [PartyJoinException.isAlreadyInThisParty] 가 true 면 **서버에는
     * 내가 이미 그 방 멤버로 들어가 있다** — 데이터 계층이 방 상세로 확인한 결과이고,
     * [PartyJoinException.joinedStatus] 에 그 방의 현재 상태가 담겨 온다. 화면은 이 경우를
     * **합류 성공과 같은 길로** 보내야 한다. 그러지 않으면 그 계정은 재시도도 409, 다른 방도 409 라
     * 30분 스윕 전까지 아무 방에도 못 들어간다(합류는 서버가 멤버 추가를 먼저 커밋한다).
     */
    suspend fun joinParty(partyId: Long): PartyStatus

    /**
     * 나가기. DELETE /api/v1/matching/leave/{partyId}. 나가는 주체는 토큰이 정한다.
     * 성공 시 Explore 목록 재조회 필요.
     *
     * 그 방의 멤버가 아니면 403 `NOT_PARTY_MEMBER`. 마지막 멤버가 나가면 방은 CANCELED 로 닫힌다(실측).
     */
    suspend fun leaveParty(partyId: Long)

    /**
     * POST /api/v1/matching/rooms/{partyId}/finish (204) — **기사 없이** 합승을 끝낸다.
     * 1차 배포 모드([AppConfig.taxiEnabled][com.moyeota.domain.model.AppConfig.taxiEnabled] = false)에서
     * 정원이 찬(`COMPLETED`) 방을 닫는다 — 한 명이 닫으면 **모두의** 방이 `FINISHED` 가 된다.
     *
     * **누가 닫을 수 있는지가 서버에서 바뀌었다**: Backend #237(develop 머지, 2026-10 기준 운영 미배포)이
     * 이 API 를 **방장 전용**으로 좁혔다. 방장은 저장되는 값이 아니라 **지금 멤버 중 가장 먼저 합류한
     * 사람**이고(`Party.hostId()`), 방장이 나가면 자동 승계된다. 앱은 아직 방장을 식별할 수단이 없다 —
     * 서버가 주는 `hostId` 는 내부 PK `Long` 인데 멤버 목록에는 `publicId` UUID 뿐이다(Backend #240 요청).
     * 그래서 **버튼 노출로 막지 못하고, 실패 사유로 안내한다.**
     *
     * 실패는 모두 [PartyFinishException] 으로 올라온다 — **403·409 가 각각 두 가지 뜻**이라
     * (403 `NOT_PARTY_HOST`·`NOT_PARTY_MEMBER` / 409 `PARTY_NOT_COMPLETED`·`DRIVER_ALREADY_ASSIGNED`)
     * 화면은 상태 코드가 아니라 그 예외의 프로퍼티로 분기한다. 모르는 코드·본문 없음은 code = null 이고
     * 화면이 일반 문구로 떨어진다.
     */
    suspend fun finishParty(partyId: Long)

    /**
     * GET /api/v1/matching/rooms/{partyId}/driver — 배정된 기사 요약.
     * 기사 미배정(taxiDriverId == null)이면 서버가 예외를 던지므로 status 가
     * DRIVER_ASSIGNED/IN_RIDE 일 때만 호출한다. 기사명·별점은 서버에 아직 없다.
     */
    suspend fun getAssignedDriver(partyId: Long): AssignedDriver

    // 긴급 신고 — POST /api/v1/reports, PATCH /api/v1/reports/call-result

    /**
     * 긴급 신고 접수 — 서버가 파티·위치를 저장하고 신고 id 를 반환한다.
     * 위치는 데이터 계층이 채운다 (주입된 측위 소스에서 실측 — 실패 시 null 전송, 서버가 허용).
     * 저장 실패 시 한국어 메시지의 [IllegalStateException] 을 던진다 —
     * 화면은 실패 여부와 무관하게 112 다이얼을 먼저 열어야 한다 (통화 최우선).
     *
     * @param partyId 신고 대상 파티. 화면에서 알 수 없으면 null (서버는 REPORT_NOT_ALLOWED
     *   응답 — 저장 실패로 처리된다)
     * @return 서버가 발급한 신고 id (reportId)
     */
    suspend fun reportEmergency(partyId: Long?): Long

    /**
     * 112 다이얼에서 앱 복귀 후 "실제로 통화했는지" 결과를 저장한다.
     * reportId 는 보내지 않는다 — 서버가 토큰 주체(@CurrentUser)의 신고에 기록한다.
     * 실패 시 한국어 메시지의 [IllegalStateException] 을 던진다.
     */
    suspend fun confirmEmergencyCall(called: Boolean)

    /**
     * POST /api/v1/matching/routes — 방을 만들기 전 좌표만으로 보는 경로 미리보기.
     *
     * 예상 요금·소요시간과 인코딩 폴리라인([RouteEstimate.encodedPath])을 돌려준다.
     * 이미 만들어진 방의 경로는 [getPartyDetail] 의 `routePolyline` 으로 얻는다.
     */
    suspend fun previewRoute(
        departureLat: Double,
        departureLng: Double,
        destinationLat: Double,
        destinationLng: Double,
    ): RouteEstimate
}
