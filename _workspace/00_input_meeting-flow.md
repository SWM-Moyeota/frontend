# 약속 확정 · 출발지 도착 · 출발 — 작업 지시

**날짜:** 2026-11-06 **브랜치:** `feature/meeting-confirm-arrive-depart` ← `develop` (1d1c8ee)

## 왜

백엔드와 앱의 엔드포인트를 **전수 대조**했다(서버 62 · 앱 46).

```
앱이 부르는데 서버에 없는 것   0개   ← 택시 제거(PR #80) 뒤 깨진 호출 없음
서버에 있는데 앱에 없는 것    16개   ← 그중 앱 몫 9개 + SSE 3종
```

이번에 넣는 것은 그중 **방을 끝내는 정식 경로** 셋이다. 서버 Swagger 가 앱이 쓰는
`POST /rooms/{id}/finish` 를 **「구버전 앱용 이전 경로」**라고 적어 두었다.

## 지금 앱이 못 하는 것

| | |
|---|---|
| 방 상세의 `roomType`·`departureAt`·`confirmed`·`members[].arrived` | **DTO 에 필드 자체가 없다.** 방이 확정됐는지, 누가 도착했는지 읽지 못한다 |
| `POST /rooms/{id}/confirm` | 약속 시간 확정(방장) |
| `POST /rooms/{id}/arrive` | 출발지 도착 |
| `POST /rooms/{id}/depart` | 출발 — 방 종료 + **노쇼 기록** |
| SSE `recruiting-closed`·`confirmed`·`member-arrived` | 서버는 7종을 보내는데 앱은 4종만 읽는다 |

노쇼 제재(`restrictedUntil`)는 앱이 **읽기만** 한다. 그걸 만드는 `/depart` 가 없어서
제재가 생길 경로 자체가 앱에 없었다.

## 고정한 도메인 계약 (리더가 정함 — 에이전트가 바꾸지 말 것)

```kotlin
// domain/model/Ride.kt
val departureAt: String? = null   // ISO-8601 문자열 그대로. RideRecord·ChatMessage 와 같은 관례
val isConfirmed: Boolean = false  // 서버 confirmed
val roomType: RoomType = RoomType.REALTIME

enum class RoomType { REALTIME, RESERVATION }   // 모르는 값은 REALTIME

// domain/model/User.kt
val hasArrived: Boolean = false   // 서버 MemberInfo.arrived

// domain/repository/RideRepository.kt
suspend fun confirmMeeting(partyId: Long, departureAt: String)
suspend fun arriveAtDeparture(partyId: Long, latitude: Double, longitude: Double)
suspend fun departParty(partyId: Long)

// domain/model/PartyEvent.kt — 셋 추가
data class RecruitingClosed(val status: String, val version: Long? = null) : PartyEvent
data class Confirmed(val status: String, val departureAt: String?, val version: Long? = null) : PartyEvent
data class MemberArrived(val publicId: String, val version: Long? = null) : PartyEvent
```

## 서버 계약 (실물 확인)

### `POST /matching/rooms/{partyId}/confirm` — 약속 시간 확정
- 본문 `{ "departureAt": "2026-11-03T08:30:00Z" }`, 204
- 방장만. **실시간 방만.** 멤버 2명 이상, 모집 중(ACTIVE/COMPLETED), 한 번만
- 시간은 **지금부터 1시간 안**, 지난 시각 불가
- 확정하면 모집이 끝나고(ACTIVE→COMPLETED) **아무도 나갈 수 없다**
- 400 `INVALID_MEETING_TIME` / 403 `NOT_PARTY_HOST`·`NOT_PARTY_MEMBER` /
  409 `NOT_REALTIME_PARTY`·`ALREADY_CONFIRMED`·`PARTY_NOT_RECRUITING`·`NOT_ENOUGH_MEMBERS`
- SSE `confirmed { status, departureAt, version }`

### `POST /matching/rooms/{partyId}/arrive` — 출발지 도착
- 본문 `{ "latitude": 37.4979, "longitude": 127.0276 }`, 204
- **약속 시간 10분 전부터**, 방 출발지 **100m 안**(서버가 PostGIS 로 잰다)
- 이미 도착한 사람이 또 눌러도 204(시각 유지, version·SSE 없음) — **멱등**
- 400 `TOO_FAR_FROM_DEPARTURE`·`OUT_OF_SERVICE_AREA` / 403 `NOT_PARTY_MEMBER` /
  409 `PARTY_CLOSED`·`PARTY_NOT_CONFIRMED`·`ARRIVAL_NOT_OPEN`
- SSE `member-arrived { publicId, version }`

### `POST /matching/rooms/{partyId}/depart` — 출발
- 본문 없음, 204. **방장이 아니어도 된다** — 도착한 멤버면 누구나
- 약속 시간 **뒤**에만. 멤버 2명 이상
- **그 순간 도착하지 않은 멤버는 노쇼로 기록**된다(약속 시간부터 노쇼 횟수 × 24시간 제한)
- 409 `PARTY_CLOSED`·`PARTY_NOT_CONFIRMED`·`NOT_ENOUGH_MEMBERS`·`MEETING_NOT_STARTED`·`NOT_ARRIVED`
- SSE `closed { status: FINISHED }` — 서버가 연결을 끊는다

## 화면 설계 (리더가 정함)

전부 **21 매칭 대기** 안에서 끝낸다. 새 화면 0개.

| 상태 | 보이는 것 |
|---|---|
| 모집 중 | 지금과 같음 |
| 정원 참 · 미확정 · **방장** | 「약속 시간 정하기」 — **프리셋 버튼**(10·20·30·45·60분 후) |
| 정원 참 · 미확정 · 비방장 | 「방장이 약속 시간을 정하는 중이에요」 |
| 확정됨 | 약속 시간 표시 + 멤버별 도착 체크 |
| 확정됨 · 약속 10분 전부터 · 내가 미도착 | 「출발지 도착했어요」 |
| 확정됨 · 내가 도착 · 약속 시간 지남 | 「출발」 |

**시간을 프리셋으로 하는 이유:** 서버가 1시간 이내만 받는다. 자유 입력은
`INVALID_MEETING_TIME` 을 사용자가 만들 수 있게 한다 — 만들 수 없는 UI 가 낫다.

**「동승 완료」(`/finish`)는 확정 전 방에서만 남긴다.** 확정된 방에 누르면 서버가
409 `USE_DEPART_FOR_CONFIRMED_PARTY` 로 거절한다.

## 건드리지 말 것

- `AppConfig` 의 `minVersionCode`·`recommendedVersionCode` (백엔드 #277 대기)
- `PartyMappers` 의 `else -> RideStatus.RECRUITING` 폴백
- 채팅 메시지 신고 경로
- `local.properties`, 백엔드 레포

## 완료 기준

```bash
./gradlew :app:assembleDebug :data:testDebugUnitTest :domain:testDebugUnitTest :presentation:testDebugUnitTest
```
