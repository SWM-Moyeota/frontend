# 노쇼 제재·이의 신청 + 예약 방 — 작업 지시

**날짜:** 2026-11-06 **브랜치:** `feature/noshow-objection-and-reservations` ← `develop` (140e775)

## 왜 — 전수 대조의 마지막 조각

```
서버 62 · 앱 46          앱이 부르는데 서버에 없는 것 0개
PR #80  택시 제거 (머지)
PR #82  약속 확정·도착·출발 (머지)   ← 여기서 앱이 노쇼를 **만들** 수 있게 됐다
이 PR   노쇼 제재·이의 + 예약 방     ← 남은 전부
```

**보류:** 채팅 위치 공유 3개(`POST/DELETE /chat-rooms/{id}/location/sharing`, `POST .../location`) —
사용자가 이전에 보류 결정했다(개인정보 처리방침 문구 + `ACCESS_BACKGROUND_LOCATION` 고지 필요).
운영자 API 3개는 앱 대상이 아니다.

---

# A. 노쇼 — 지금 앱은 제재를 **보여 주지도** 못한다

실측 결과 세 가지가 전부 비어 있다.

| | 상태 |
|---|---|
| `GET /local/users/info` 의 `noShowCount`·`restrictedUntil` | 앱 `UserProfileResponse` 에 **필드가 없다** |
| 403 `NO_SHOW_RESTRICTED` | 앱 코드에 그 문자열이 **한 군데도 없다** |
| `GET /matching/no-shows`, `POST /rooms/{id}/no-show-objection` | **없다** |

즉 제재당한 사용자는 방 만들기·참여가 막히는데 **왜인지 알 수 없고**, 억울해도 **이의를 낼 길이 없다.**
PR #82 로 앱이 노쇼를 만드는 쪽을 갖췄으니 받는 쪽도 갖춰야 한다.

## 서버 계약

### `GET /api/v1/local/users/info` — 두 필드가 더 있다
```
noShowCount    int        지금 노쇼 횟수. 건마다 그 방 약속 시간부터 30일 지나면 빠진다.
                          이의가 인정돼 무효가 된 노쇼는 세지 않는다
restrictedUntil Instant?  이 시각까지 방 만들기·참여 불가. 제한 없으면 null
```

### `GET /api/v1/matching/no-shows` — 내 노쇼 기록
200 `{ "noShows": [ … ] }`, 노쇼 시각 최신순 **최대 20개**
```
partyId Long · departure String · destination String
meetingAt Instant   약속 시간 — 이용 제한을 여기서부터 센다
noShowAt  Instant   노쇼로 찍힌 시각(누가 출발을 누른 때 또는 서버 자동 정리)
voided    boolean   운영자가 이의를 받아들여 무효로 돌렸는지. true 면 노쇼로 치지 않는다
objectionStatus String?   PENDING / ACCEPTED / REJECTED, 안 냈으면 null
```
**`objectionStatus == null && !voided` 인 줄에만 이의 신청 버튼을 보여 준다**(서버 Swagger 지시).

### `POST /api/v1/matching/rooms/{partyId}/no-show-objection` — 이의 신청
본문 `{ "reason": "…" }` **1~500자**(앞뒤 공백 제거), **201**(본문 없음)
- 방마다 **한 번만**
- 400 `INVALID_REQUEST`(공백·500자 초과) / 403 `NOT_PARTY_MEMBER` / 404 `PARTY_NOT_FOUND`
- 409 `NOT_NO_SHOW`(이 방에서 노쇼가 아니거나 이미 무효) · `ALREADY_OBJECTED`
- 인정되면 그 노쇼가 무효가 되고 탑승 기록·횟수에 다시 잡히며 **지금 걸린 제한이 풀린다**

### 403 `NO_SHOW_RESTRICTED`
방 만들기(`POST /matching/rooms`), 합류(`POST /rooms/{id}/join`), 예약 생성
(`POST /matching/reservations`), 실시간 후보(`POST /rooms/realtime`) 넷 모두에서 온다.

---

# B. 예약 방 — 서버에 4개, 앱에 0개

### `POST /api/v1/matching/reservations` — 생성
```
departureLat/Lng · destinationLat/Lng · departure · destination (전부 필수)
capacity    2~3   ★ 실시간 방은 1~3 인데 예약은 2 이상이다
departureAt Instant  지금부터 7일 이내
```
200 생성된 방(`OpenPartyResponse`). **경로·예상 요금·예상 시간은 비어 있다.**
참여·나가기·상세·SSE 는 **기존 방 API 를 그대로 쓴다.**
- 400 `SAME_DEPARTURE_DESTINATION`·`INVALID_CAPACITY`·`OUT_OF_SERVICE_AREA`·`INVALID_DEPARTURE_TIME`
- 403 `NO_SHOW_RESTRICTED` / 409 `ALREADY_JOINED_OTHER_PARTY`·`PARTY_TIME_CONFLICT`(출발 2시간 안 다른 예약)

### `GET /api/v1/matching/reservations?from=&to=` — 기간 목록
`Instant` 두 개. 400 `INVALID_TIME_RANGE`(from ≥ to)

### `GET /api/v1/matching/reservations/soon?swLat=&swLng=&neLat=&neLng=` — 곧 출발
뷰포트 안 **30분 내** 출발. 400 `INVALID_MAP_BOUNDS`

### `GET /api/v1/matching/reservations/search?depLat=&depLng=&date=&destLat=&destLng=` — 검색
출발지 **1km** 안, `date`(한국 날짜 `yyyy-MM-dd`) 하루, 최대 100개.
`date` 는 **오늘~7일 뒤**, 오늘이면 이미 출발한 방은 빠진다.
도착지는 **선택이고 둘 다 보내거나 둘 다 뺀다** — 보내면 항목에
`destinationNearby`(도착지도 1km 안인지)가 붙고 **true 가 먼저**, 그 안에서 출발 시각 순.
빼면 출발 시각 순이고 `destinationNearby` 는 null.
- 400 `INCOMPLETE_DESTINATION`(둘 중 하나만) · `INVALID_SEARCH_DATE` · `OUT_OF_SERVICE_AREA`

### 응답 항목 (목록·검색 공통)
```
partyId · departure · destination · currentMembers · capacity · status
departureLat/Lng · destinationLat/Lng · departureAt
destinationNearby Boolean?   ← 검색에만, 도착지를 보냈을 때만
```

---

## 고정한 도메인 계약 (리더가 정함)

```kotlin
// domain/model/User.kt 가 아니라 프로필 쪽
data class MyProfile(            // 이미 있으면 필드만 더한다
    val noShowCount: Int = 0,
    val restrictedUntil: String? = null,   // ISO-8601 문자열 관례
    …
)

// domain/model/NoShow.kt (신규)
data class NoShowRecord(
    val partyId: Long,
    val departure: String,
    val destination: String,
    val meetingAt: String?,
    val noShowAt: String?,
    val isVoided: Boolean,
    val objectionStatus: ObjectionStatus?,   // null = 안 냈다
)
enum class ObjectionStatus { PENDING, ACCEPTED, REJECTED }   // 모르는 값은 PENDING 으로 떨어뜨리지 말고 null

// Ride 는 그대로 쓴다 — 예약 방도 방이다(roomType = RESERVATION 이 이미 있다)
```

**예약 방을 위한 새 도메인 모델을 만들지 말 것.** 서버가 「참여·나가기·상세·SSE 는 기존 방 API」라고
했고 앱에도 `Ride`·`RoomType` 이 이미 있다. 목록 항목은 `Ride` 로 매핑한다 — 경로·요금이 비어 있는
것은 `Ride` 의 기존 nullable 필드가 이미 표현한다.

## 건드리지 말 것

- `AppConfig` 의 `minVersionCode`·`recommendedVersionCode` (백엔드 #277 대기)
- `PartyMappers` 의 `else -> RideStatus.RECRUITING` 폴백
- PR #82 가 넣은 약속 확정·도착·출발 로직
- 채팅 위치 공유 (보류)
- `local.properties`, 백엔드 레포

## 완료 기준

```bash
./gradlew :app:assembleDebug :data:testDebugUnitTest :domain:testDebugUnitTest :presentation:testDebugUnitTest
```
