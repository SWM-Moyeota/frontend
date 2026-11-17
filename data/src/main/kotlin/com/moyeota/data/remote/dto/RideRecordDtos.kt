package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/v1/users/me/parties` 의 기본 조회 개수. 서버 기본값과 같은 20 이다.
 *
 * 서버가 받는 범위는 **1~50** 이고 밖이면 400 `INVALID_PAGE_SIZE` 다
 * (실측 2026-11-01, 로컬 `:8080`: `size=0` → 400, `size=51` → 400, `size=50` → 200).
 * 이 상수를 0 이나 100 으로 바꾸면 목록이 통째로 비고, 화면은 「기록이 없어요」를 그린다
 * — 에러와 빈 목록이 사용자에게 같은 모양이 되므로 원인을 쫓을 단서가 사라진다.
 */
const val RIDE_RECORD_PAGE_SIZE = 20

/**
 * `GET /api/v1/users/me/parties` 한 페이지. 서버 `RideRecordResponse`.
 *
 * **이름이 함정이다.** 경로가 `parties` 라 「내 방 목록」처럼 읽히지만 서버 태그는 「탑승 기록」이고
 * **끝난 탑승만** 나온다. 그리고 `b16551b7` 부터 **도착을 누른 멤버만** 그 방의 기록을 갖는다
 * (「탑승 횟수·탑승 기록은 도착한 멤버만 센다」) — 약속 장소에 가지 않은 사람에게는 그 방이
 * 아예 없다.
 * 진행 중인 방은 절대 들어오지 않는다 — 실측(2026-11-01, 로컬 `:8080`): 내가 유일 멤버인
 * `status=MATCHING` 방을 하나 들고 이 엔드포인트를 불러도 `{"rides":[],"nextCursor":null,"hasNext":false}`
 * 였다. 그래서 이것으로 [com.moyeota.domain.repository.ActivePartyRepository] 의 로컬 기억을
 * **대체할 수 없다.** 그 공백은 서버에 `GET /matching/rooms/me` 가 생겨야 메워진다.
 *
 * 탈퇴 경고를 이걸로 미리 막으려 해도 안 된다 — 409 `USER110` 의 판정식은 서버
 * `existsOngoingByMemberId`(진행 중 상태 = ACTIVE·COMPLETED. 기사·배차 상태 셋은 서버 enum 에서
 * 삭제됐다)인데 이 응답은 그중 아무것도 주지 않는다. 「막을 방이 없다」고 잘못 판정해 그대로 409 를 맞고, 사용자는
 * 앱이 확인했다고 믿는다 — 지금보다 나빠진다.
 */
@Serializable
data class RideRecordPageResponse(
    val rides: List<RideRecordResponse> = emptyList(),
    /**
     * 다음 페이지 커서(= 이 페이지 마지막 `partyId`). **[hasNext] 가 false 면 항상 null** 이다
     * (서버 `RideRecordService` 가 `hasNext ? page.getLast().getMatchId() : null` 로 만든다).
     *
     * 실측 두 응답: `size=1` 1페이지 → `nextCursor=69, hasNext=true`,
     * `cursor=69` 2페이지 → `nextCursor=null, hasNext=false`.
     */
    val nextCursor: Long? = null,
    /**
     * 더 받을 페이지가 있는가. **이 값만 보고 다음 페이지를 부른다.**
     *
     * false 인데도 부르면 `cursor` 없이 **첫 페이지를 다시 받아** 목록이 무한히 중복된다.
     * 반대로 진행 중인 방 id 를 커서로 넣으면 400 `INVALID_CURSOR` 다(실측: 하차 전 방 `cursor=54`).
     */
    val hasNext: Boolean = false,
)

/**
 * 탑승 기록 한 건. 서버 `RideRecordItem`.
 *
 * **`status` 필드가 없다** — 기록은 전부 하차 완료라 상태 개념이 없다. 방 상세
 * ([PartyDetailResponse])를 베껴 `status` 를 넣으면 `ignoreUnknownKeys`(`NetworkModule`) 때문에
 * 조용히 기본값이 되어 화면이 **모든 기록에 같은 상태 칩**을 그린다. `capacity`·좌표·`route`·
 * `version` 도 같은 이유로 여기 없다.
 *
 * ### 🔴 `dropoffTime` 은 **없어졌다** — 날짜를 그걸로 그리면 전부 빈칸이 된다 ★
 * 서버가 하차 시각을 없애고 **승차 시각 하나로 바로잡았다**(`135df701`:
 * 「탑승 기록의 하차 시각을 승차 시각(pickup_time)으로 바로잡는다」). 지금 `RideRecordItem` 에
 * 있는 시각은 [pickupTime] 뿐이다.
 *
 * `ignoreUnknownKeys` 정책 때문에 **없는 키는 조용히 null 이 된다** — 즉 `dropoffTime` 을 그대로
 * 받아 두면 파싱은 멀쩡하고 **모든 기록 카드의 날짜만 사라진다.** 에러가 아니라 빈칸이라 QA 가
 * 「날짜가 왜 안 보이지」로만 보게 되는 자리라서, 필드를 지워 **컴파일로 잡게** 한다.
 *
 * ### [pickupTime] 의 뜻도 바뀌었다
 * 「기사가 탑승 처리한 시각」이 아니라 **방장이 출발을 누른 시각**이다. 아무도 출발을 누르지 않아
 * 서버가 자동 정리한 방은 **약속 시각**이 들어간다(서버 Swagger 원문).
 *
 * ### [totalFare]·[farePerPerson] 은 택시비 정산에서 온다 — **함께 채워지거나 함께 빈다**
 * 서버 원문: 「정산이 없거나 내가 정산의 받는 사람·보내는 사람이 아니면 null」. 즉 null 은 결함이
 * 아니라 **「이 방에 정산이 없다」**는 뜻이다. **0 으로 접지 마라** — 화면이 「0원」을 그리면 거짓말이다.
 */
@Serializable
data class RideRecordResponse(
    val partyId: Long = 0L,
    val departure: String? = null,
    val destination: String? = null,
    /** 승차 시각 — **방장이 출발을 누른 시각**(자동 정리된 방은 약속 시각). 이 응답의 유일한 시각이다. */
    val pickupTime: String? = null,
    /** 이 방의 택시비 정산에 **받는 사람이 넣은 실제 금액**. 정산이 없으면 null. */
    val totalFare: Int? = null,
    /**
     * 내 부담 금액. **1/N 이 아니다** — 방장이 사람마다 적어 넣은 금액이다
     * (`fe6ba35a`: 「택시비 정산을 방장이 사람마다 부담 금액을 넣는 장부로 다시 짠다」).
     *
     * 서버 원문: 「보내는 사람은 받는 사람이 정한 내가 보낼 금액, 받는 사람은 택시비에서 다른
     * 사람들이 보낼 금액을 뺀 금액」. 그래서 **같은 방을 보는 두 사람이 다른 값을 받는다.**
     *
     * **앱이 다시 계산하지 마라.** `totalFare / currentMembers` 로 되돌리면 방장이 손으로 조정한
     * 금액(누가 더 멀리 갔다 같은 사정)이 전부 뭉개진다. 앱의 `farePerPersonWon`(14·15 의 예상 요금)도
     * 끌어다 쓰면 안 된다 — 10원 단위로 맞추므로 실제 금액과 어긋난다.
     */
    val farePerPerson: Int? = null,
    /** 함께 탄 인원(나 포함). 정원(`capacity`)이 아니다 — 서버가 정원을 주지 않아 「2/3명」은 못 만든다. */
    val currentMembers: Int = 0,
    val members: List<RideRecordMemberResponse> = emptyList(),
)

/**
 * 함께 탄 사람. 서버 `RideRecordMember` — **필드가 셋뿐이다.** 방 상세의 `MemberInfo` 와 달리
 * `host`·`rideCount`·`badgeId` 가 없다. 그래서 기록 화면에는 「방장」 표시를 그릴 수 없고,
 * 「이게 나인가」도 서버가 말해 주지 않아 [publicId] 비교로 앱이 직접 만든다.
 *
 * 탈퇴한 동승자는 [publicId] 는 그대로, [nickname] 이 `"탈퇴한 사용자"`(서버가 직접 넣는 값),
 * [imageUrl] 이 기본 이미지 주소로 온다. 사용자 요약 자체가 없으면 **셋 다 null** 이다.
 */
@Serializable
data class RideRecordMemberResponse(
    val publicId: String? = null,
    val nickname: String? = null,
    val imageUrl: String? = null,
)
