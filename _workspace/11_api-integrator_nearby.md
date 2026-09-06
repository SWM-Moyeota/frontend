# 11. API 연동 — 지도 범위 방 목록 (nearby)

작업일: 2026-09-01 / 범위: `data/`·`domain/`만 (presentation 미변경)

## 연동한 엔드포인트

| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/v1/matching/rooms?swLat=&swLng=&neLat=&neLng=` | 지도 화면 범위(남서·북동 모서리) 안의 ACTIVE 방 목록 |

기존 무파라미터 `GET /api/v1/matching/rooms`(전체 목록)와 **경로가 같다**. 서버
(`PartyController.listWithin`)가 `@GetMapping(value="/matching/rooms", params={"swLat","swLng","neLat","neLng"})`
로 쿼리 파라미터 유무를 보고 핸들러를 고른다. 4개 중 하나라도 빠지면 전체 목록 핸들러로 떨어지므로
**4개를 항상 함께 보내야 한다**.

## 실제 응답 shape (실서버 검증 완료 — http://localhost:8080, curl)

```
GET /api/v1/matching/rooms?swLat=35.20&swLng=129.05&neLat=35.26&neLng=129.12
{"list":[{"partyId":1,"departure":"부산대 정문","destination":"서면역",
          "currentMembers":1,"capacity":3,"status":"ACTIVE",
          "departureLat":35.2313,"departureLng":129.0838}]}
```

- 목록 아이템에 `departureLat`/`departureLng`가 **추가**됐다(서버 record `PartyListResponse.PartyItem`).
- 도착 좌표·요금·경로는 **여전히 목록에 없다** → 필요하면 `getPartyDetail`로 받는다.

### 문서/지시와 실서버 불일치 1건

지시서는 "기존 무파라미터 전체 목록 응답에는 좌표가 없음"이라 했지만, **현재 떠 있는 로컬 서버는
무파라미터 호출에도 `departureLat`/`departureLng`를 실어준다**(같은 `PartyItem` record를 공유하기 때문).
브랜치에 따라 빠질 수 있으므로 DTO 필드를 `Double? = null`로 두어 양쪽 모두 안전하게 처리한다.
→ `getParties()`(전체 목록)로 받은 `Ride`에도 좌표가 채워질 수 있다. 있으면 쓰고 없으면 null로 다뤄야 한다.

## 확정 시그니처 (compose-builder 계약)

```kotlin
// domain/repository/RideRepository.kt
suspend fun getPartiesWithin(
    swLat: Double,
    swLng: Double,
    neLat: Double,
    neLng: Double,
): List<Ride>
```

- 인자 순서: **남서 위도 → 남서 경도 → 북동 위도 → 북동 경도**. 뒤바뀌면 서버가 빈 목록을 돌려준다.
- 반환: 기존 목록 조회(`getParties()`)와 **동일한 `List<Ride>` 컨벤션**.
- 채워지는 필드: `id`, `origin`, `destination`, `capacity`, `members`(인원 수만큼 자리표시 User), `status`,
  그리고 **`originLat`/`originLng`**(지도 핀용).
- 여전히 비는 필드: `destinationLat/Lng`, `estimatedFare`, `estimatedMinutes`, `routePolyline`, `farePerPerson`,
  `totalFare`(0), `hostId`, `driverId`. 핀 탭 후 상세가 필요하면 `getPartyDetail(partyId)`.
- **기존 시그니처 변경 없음** — 순수 추가라 presentation 컴파일이 깨질 일 없다.

## 변경 파일

- `data/src/main/kotlin/com/moyeota/data/remote/MatchingApi.kt` — `getPartiesWithin` (`@Query` 4개, 같은 경로)
- `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt` — `PartyItem`에 `departureLat`/`departureLng` (nullable, 기본 null)
- `data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt` — `PartyItem.toRide()`가 좌표를 `originLat`/`originLng`로 전달
- `data/src/main/kotlin/com/moyeota/data/repository/RemoteRideRepository.kt` — 구현
- `data/src/main/kotlin/com/moyeota/data/repository/DummyRideRepository.kt` — 구현(더미 방 2건에 성결대 좌표 부여 후 범위 필터)
- `data/src/test/.../PartyMappersTest.kt`, `.../RemoteRideRepositoryTest.kt` — 테스트 5건 추가

## 테스트 결과

```
./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin --console=plain
BUILD SUCCESSFUL
```
`PartyMappersTest` 20건 / `RemoteRideRepositoryTest` 6건 포함 data 모듈 46건 전부 통과 (실패 0).

추가한 검증: 실서버 JSON 그대로 역직렬화 → 좌표 전달, 좌표 누락 시 null 방어,
Repository가 남서/북동 인자 순서를 그대로 API에 전달, 더미 저장소 범위 필터.

## qa-verifier 교차 검증 요청 사항

1. 지도 화면이 카메라 이동 시 `getPartiesWithin`을 부르는가 (전체 목록 `getParties`가 아니라).
2. `originLat`/`originLng`가 null인 아이템(구 서버 브랜치)이 와도 지도가 크래시하지 않는가.
3. 좌표 전치 결함(D-1) 재발 여부 — 서버 `departureLat=35.23`(위도), `departureLng=129.08`(경도)가
   그대로 `Ride.originLat`/`originLng`에 들어가는지 핀 위치로 확인.
