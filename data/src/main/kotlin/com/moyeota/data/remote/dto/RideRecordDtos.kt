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
 * **하차까지 끝난 탑승만** 나온다(서버 `RideRecordJpaRepository` 의 `dropoffTime is not null`).
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
 * [pickupTime]·[totalFare]·[farePerPerson] 이 **셋 다 null 일 수 있다**: 1차 모드(기사 없이
 * 방장이 합승 종료, 서버 `RideRecord.withoutDriver`)로 끝난 방이 그렇다. 요금은 기사가 입력할 때만
 * 채워진다(서버 `dropOff(fare)`). **0 으로 접지 마라** — 화면이 「0원」을 그리면 거짓말이다.
 *
 * [dropoffTime] 은 목록에 들어오는 조건 자체라 사실상 null 이 아니지만, 서버 스키마가 보장하지
 * 않으므로 nullable 로 둔다(파싱이 터져 목록 전체가 사라지는 것보다 낫다).
 */
@Serializable
data class RideRecordResponse(
    val partyId: Long = 0L,
    val departure: String? = null,
    val destination: String? = null,
    val pickupTime: String? = null,
    val dropoffTime: String? = null,
    val totalFare: Int? = null,
    /**
     * 서버가 계산한 1인 부담. `totalFare / currentMembers` **정수 나눗셈(내림)** 이다.
     *
     * **앱이 다시 계산하지 마라.** 앱의 `farePerPersonWon`(14·15 가 쓰는 예상 요금 계산)은
     * 10원 단위로 올림/반올림하므로, 끌어다 쓰면 기록 화면 금액이 실제 결제액과 어긋난다.
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
