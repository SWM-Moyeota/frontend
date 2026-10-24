# 실시간 매칭 API 조사 — 앱 연동 판단

조사일 2026-10-23 · 조사 범위 backend `origin/develop` (= `origin/main`, 아래 참조) · 코드 변경 없음(조사만)

> **2026-10-24 기준으로 아래 내용 중 한 가지가 바뀌었다.** 「`currentMembers` 가 없다」는 전제가
> 깔린 대목(§4.4 표, §6 비교표, §0 보류 근거 ④, 구현 메모)은 **Backend #260 으로 해소됐다** —
> §7-2 와 `07_realtime-matching-qa.md` 의 「후속」을 보라. 나머지 분석은 그대로 유효하다.

---

## 0. 결론 요약

| 질문 | 답 |
|---|---|
| 새 API 가 prod 에 있나 | **있다. 지금 떠 있다.** 실측: `https://api.moyeota.co.kr/v3/api-docs` 에 `POST /api/v1/matching/rooms/realtime` 등록됨 |
| 기존 API 가 깨졌나 | **아니다.** 지도 영역 조회·목록·상세 응답 shape 무변경. 앱의 깨지는 파일 **없음** |
| 앱이 지금 붙어야 하나 | **화면 노출은 아직 하지 말 것.** OSRM 운영 전제가 **미확인**이고, 실패 모드가 "후보가 0건일 때는 성공처럼 보이고 후보가 생기는 순간 502" 라서 스모크 테스트로 안전을 확인할 수 없다 |
| 붙일 가치는 있나 | **크다.** 앱이 주석으로 명시해 둔 공백(`DestinationScreen.kt:404` 「도착지로 거르는 길이 없다」)을 정확히 메우는 API다 |

**가장 중요한 한 가지**: 이 API 는 반경 300m 안에 방이 0건이면 OSRM 을 **아예 호출하지 않고** `200 []` 를 돌려준다(`OsrmWalkingClient.java:35,50`). 서비스 초기에는 대부분의 호출이 0건이므로, prod 에 붙여 눌러 봐도 **정상으로 보인다**. OSRM 이 죽어 있으면 후보가 처음 잡히는 그 순간 — 즉 이 기능이 처음으로 쓸모 있어지는 순간 — `502 ROUTE_SEARCH_FAILED` 가 뜬다. "눌러 봤는데 잘 되던데요" 가 근거가 될 수 없는 구조다.

---

## 1. 새 API 계약 전문

### 1.1 엔드포인트

```
POST /api/v1/matching/rooms/realtime
Authorization: Bearer <access token>   (필수)
Content-Type: application/json
```

근거: `backend/src/main/java/team/codingforest/moyeota/matching/party/PartyController.java:102-107`
```java
@PostMapping("/matching/rooms/realtime")
public ResponseEntity<List<MatchCandidate>> getListCandidate(@CurrentUser Long memberId, @RequestBody RealTimeMatchingRequest request)
```
- `@RequestMapping("/api/v1")` 은 클래스 레벨 (`PartyController.java:32`)
- 토큰 필수 — Security 가 `/api/v1/auth/` 밖 전 경로에 `anyRequest().authenticated()` 를 건다. 실측으로 토큰 없이 호출하면 **401**.
- **조회인데 POST 다.** 캐시 불가, Retrofit 은 `@Body` 로 보내야 한다.

### 1.2 요청 바디

근거: `matching/party/dto/RealTimeMatchingRequest.java:3`
```java
public record RealTimeMatchingRequest(double departureLat, double departureLng, double destLat, double destLng) {}
```

| 필드 | 타입 | 널 허용 | 주의 |
|---|---|---|---|
| `departureLat` | double | **불가** | primitive. 필드 누락 시 Jackson 이 `0.0` 을 넣는다 |
| `departureLng` | double | **불가** | 같음 |
| `destLat` | double | **불가** | **이름이 `destLat` 이다** — 기존 API 의 `destinationLat` 과 다르다 |
| `destLng` | double | **불가** | **`destinationLng` 아님** |

**함정 1 — 필드명 비대칭**: 요청은 `destLat`/`destLng`, 응답은 `destinationLat`/`destinationLng`. 기존 `OpenPartyRequestDto` 는 `destinationLat` 을 쓴다. DTO 를 복사해 쓰면 좌표가 `0.0` 으로 가고, 그게 바로 함정 2 로 이어진다.

**함정 2 — 누락이 400 으로 나온다**: 전부 primitive 라 필드를 빼면 `0.0` 이 되고, `new Location(0.0, 0.0)` 이 서비스 지역(위도 33~39, 경도 124~132) 밖이라 `OUT_OF_SERVICE_AREA` 400 이 떨어진다 (`matching/party/domain/Location.java:8-15`). Swagger 의 「좌표 누락 또는 서비스 지역 밖」이 이 뜻이다. 즉 **400 을 받으면 사용자가 서비스 지역 밖인 게 아니라 우리 DTO 가 틀렸을 가능성이 먼저다.**

### 1.3 성공 응답 — `200 OK`

**최상위가 배열이다.** 기존 목록처럼 `{"list":[...]}` 로 감싸지 않는다.

근거: `matching/party/dto/MatchCandidate.java:3`, `matching/party/dto/NearbyPartySummary.java:3`
```java
public record MatchCandidate(NearbyPartySummary summary, int totalSeconds) {}
public record NearbyPartySummary(Long id, Double departureLat, Double departureLng,
                                 Double destinationLat, Double destinationLng,
                                 String departure, String destination, Integer capacity) {}
```

실제 shape (prod OpenAPI `MatchCandidate`/`NearbyPartySummary` 스키마와 일치 확인):
```json
[
  {
    "summary": {
      "id": 123,
      "departureLat": 37.4979,
      "departureLng": 127.0276,
      "destinationLat": 37.5006,
      "destinationLng": 127.0364,
      "departure": "강남역 2번 출구",
      "destination": "선릉역",
      "capacity": 3
    },
    "totalSeconds": 412
  }
]
```

| 필드 | 타입 | 널 허용 | 의미 |
|---|---|---|---|
| `summary.id` | Long | 박스 타입(이론상 null) | **방 id. 이름이 `id` 다** — 기존 목록의 `partyId` 와 다르다 |
| `summary.departureLat/Lng` | Double | 박스 타입 | 방의 출발 좌표 |
| `summary.destinationLat/Lng` | Double | 박스 타입 | 방의 도착 좌표. **기존 목록에는 없던 값** |
| `summary.departure` | String | 박스 타입 | 출발 라벨 |
| `summary.destination` | String | 박스 타입 | 도착 라벨 |
| `summary.capacity` | Integer | 박스 타입 | 정원 |
| `totalSeconds` | int | **불가** | 도보 시간 **합(초)** = 내 출발지→방 출발지 + 방 도착지→내 목적지 |

전부 DB NOT NULL 컬럼에서 오므로 실무상 null 은 오지 않을 것이나, 박스 타입이므로 앱 DTO 는 **nullable + 기본값**으로 방어한다(스킬 규칙).

#### 응답에 **없는** 것 — 이게 연동 설계를 가른다

| 없는 값 | 왜 문제인가 |
|---|---|
| **`currentMembers`** | 「2/3 명」을 못 보여준다. 2/3 방은 바로 출발하고 1/3 방은 한참 기다리므로 **사용자 결정에 가장 중요한 숫자**다. 기존 Explore 목록(`PartyItem`)에는 있다 |
| `status` | 쿼리가 `status='ACTIVE'` 로 고정이라 불필요하긴 하다 |
| `estimatedFare` / `estimatedTime` | 「1인당 얼마」를 못 보여준다 |
| `route` | 경로 폴리라인 없음 |

메우려면 후보마다 `getPartyDetail(id)` 를 또 불러야 하는데, 그 호출은 서버 쿼리 3개 + route 폴리라인이고(`RideRepository.kt:44-46`) 후보가 최대 5개라 **최대 15 쿼리**다. 부하테스트에서 상세 폴링이 이미 전체 요청의 45% 였다(Backend #177). → **백엔드에 `currentMembers` 추가를 요청하는 쪽이 옳다.** (§7)

### 1.4 에러 응답

바디는 전역 핸들러의 `ErrorResponse(code, message)` — `common/exception/GlobalExceptionHandler.java:40-41`.
`code` 는 enum 이름 그대로다(`MatchingErrorCode.getCode()` = `name()`, `MatchingErrorCode.java:53-56`).

| HTTP | code | message | 발생 조건 |
|---|---|---|---|
| 400 | `OUT_OF_SERVICE_AREA` | 서비스 지역을 벗어난 좌표입니다. | 좌표가 위 33~39 / 경 124~132 밖. **필드 누락도 여기로 떨어진다**(§1.2 함정 2) |
| 401 | `USER005` / `USER002` / `USER001` | 로그인이 필요합니다 / 만료 / 무효·탈퇴 | 토큰 없음·만료·무효 |
| 409 | `ALREADY_JOINED_OTHER_PARTY` | 이미 참여 중인 방이 있습니다. | **내가 이미 진행 중인 방에 들어가 있으면 후보 조회 자체가 막힌다** (`PartyService.java:219`) |
| 502 | `ROUTE_SEARCH_FAILED` | 경로 조회에 실패했습니다. 잠시 후 다시 시도해주세요. | OSRM 호출 실패·비정상 응답 (`OsrmWalkingClient.java:78-86`) |

```json
{"code":"OUT_OF_SERVICE_AREA","message":"서비스 지역을 벗어난 좌표입니다."}
```

검증 순서 주의: `Location` 생성(좌표 검증)이 `validateNotInOngoingParty` **보다 먼저**다(`PartyService.java:217-219`). 좌표도 틀리고 진행 중 방도 있으면 **400** 이 온다.

### 1.5 서버 내부 동작 — 2단 랭킹

`PartyService.realtimeMatch` (`PartyService.java:216-240`):

1. **PostGIS 로 후보 추리기** — `parties.findMatchesWithinRadius(..., RADIUS)`, `RADIUS = 300` (`PartyService.java:58`)
   `PartyJpaRepository.java:63-83` 네이티브 쿼리:
   ```sql
   WHERE m.status = 'ACTIVE'
     AND ST_DWithin(m.departure_geog,   <내 출발지>, 300)
     AND ST_DWithin(m.destination_geog, <내 목적지>, 300)
   ORDER BY ST_Distance(departure_geog, <내 출발지>) + ST_Distance(destination_geog, <내 목적지>)
   limit 5
   ```
   - **AND 다.** 출발지와 도착지가 **둘 다** 300m 안이어야 한다.
   - 정렬·절단 기준은 **직선거리 합**.
2. **OSRM 으로 도보 시간 계산** — `/table/v1/foot/` 를 **2회** 호출 (`OsrmWalkingClient.java:27,34,49`)
   - 1회차: 내 출발지 → 후보 N개의 출발지
   - 2회차: 후보 N개의 도착지 → 내 목적지
   - 후보가 0건이면 **호출하지 않는다** (`:35`, `:50` 의 `isEmpty()` 가드)
3. **도보 시간 합으로 재정렬** — `totalSeconds` 오름차순. OSRM 이 특정 쌍에 null 을 주면 그 후보는 `.filter` 로 **조용히 빠진다**(`PartyService.java:236`) → 응답 길이가 1~5 사이 어디든 될 수 있다.

**2단 랭킹의 귀결**: 직선거리로 상위 5개를 먼저 자르고 **그 안에서만** 도보 시간 순위를 매긴다. 직선거리 6위인데 도보 경로가 훨씬 좋은 방(강·고가·대로를 안 건너는 방)은 **영영 나오지 않는다**. `95059cb` 가 「출발지 거리만으로 자르면 좋은 후보가 빠질 수 있음」을 인지해 거리 **합**으로 바꿨지만, 5개 절단이 도보 랭킹보다 앞에 있는 구조 자체는 남아 있다.

---

## 2. 기존 API 가 바뀌었나 — **아니다**

`284a950 → origin/develop` 에서 앱이 쓰는 매칭 API 의 **응답 shape 변경 없음**. 실측 확인:

| 앱이 쓰는 것 | 상태 | 근거 |
|---|---|---|
| `GET /api/v1/matching/rooms` (전체) | 그대로 | `PartyController.java:71-74` 핸들러 유지 |
| `GET /api/v1/matching/rooms?swLat..` (지도 영역) | **그대로** | `PartyController.java:86-92` 무변경. prod OpenAPI `PartyItem` 스키마 = 앱 `PartyDtos.kt:17-26` 과 필드·타입 일치 |
| `GET /api/v1/matching/rooms/{id}` (상세) | 필드 **추가**만 | `version`, `members[].host` 추가 — 다른 PR(#251/#240) 소관. `ignoreUnknownKeys` 라 무해 |
| `POST /rooms`, `/join`, `DELETE /leave`, `/finish`, `/driver`, `/routes` | 무변경 | `git diff 284a950 origin/develop -- PartyController.java` 에 해당 변경 없음 |

`git diff 284a950 origin/develop -- domain/PartySummary.java dto/PartyListResponse.java` → **빈 diff**. 지도 영역 조회가 읽는 읽기 모델과 응답 DTO 가 글자 하나 안 바뀌었다.

**앱에서 깨지는 파일: 없음.** `MatchingApi.kt`, `PartyDtos.kt`, `PartyMappers.kt`, `RemoteRideRepository.kt`, `ExploreRoute.kt` 모두 손댈 필요 없다.

### 2.1 기존 API 를 대체하나 — 아니다, 완전히 다른 용도의 추가다

| | 지도 영역 조회 (기존) | 실시간 매칭 (신규) |
|---|---|---|
| 입력 | 지도 뷰포트 사각형 | 내 출발지 + **내 목적지** |
| 거르는 기준 | 방 **출발지**가 사각형 안 | 방 출발지·**도착지** 둘 다 300m 안 |
| 정렬 | `createdAt desc` | **도보 시간 합 오름차순** |
| 상한 | 100 (`MAP_LIST_LIMIT`) | **5** |
| 인원 정보 | `currentMembers` **있음** | **없음** |
| 쓰임새 | 지도 핀 뿌리기 (17 Explore) | 「내 경로에 맞는 방」 추천 |

→ Explore 지도는 그대로 두고, **방 만들기 직전 흐름에 추가**하는 게 맞다. (§6)

### 2.2 부수 효과 — 인덱스

`1926932` 가 V20 에서 `idx_match_room_status_location`(B-tree, 지도 영역 `between` 조회용)을 지웠다가 `95059cb` 가 **되돌렸다**. 즉 지금은 유지되고 있다(`PartyEntity.java` @Index 복원, `V20` 에서 DROP 제거). 지도 영역 조회 성능 퇴행 **없음**. 처음 PR 그대로 머지됐다면 Explore 가 느려졌을 사안이라 기록해 둔다.

---

## 3. 운영 전제 — PostGIS 는 OK, OSRM 은 **미확인(부정 쪽 증거 다수)**

### 3.1 이 코드는 이미 prod 에 배포돼 있다

```
git merge-base --is-ancestor 426a527 origin/main  →  YES
git rev-list --count origin/main..origin/develop  →  0
origin/main 최신: 71994e4 Merge pull request #245 from SWM-Moyeota/develop
```
`deploy.yml:4,21` — `main` 머지 → `moyeota-prod`. 즉 **실시간 매칭은 dev 전용이 아니라 운영 배포 상태다.**

실측(읽기 전용 probe, 2026-10-23):
```
GET  /api/v1/matching/rooms            → 401   (앱이 떠 있다. 503 아님)
POST /api/v1/matching/rooms/realtime   → 401   (경로 존재, Security 단에서 거부)
GET  /v3/api-docs                      → 200   (POST /api/v1/matching/rooms/realtime 등록 확인)
```

### 3.2 PostGIS — **prod 에서 동작 확인됨 (간접 증거, 강함)**

`V20__add_postgis_location_index_to_match_room.sql:1-12` 가 `CREATE EXTENSION IF NOT EXISTS postgis` + 생성 컬럼 2개(`departure_geog`, `destination_geog`) + GIST 인덱스 2개를 만든다.

prod 는 `flyway.enabled: true` + `ddl-auto: validate` (`application-prod.yaml:11-15`). V20 이 실패하면 **앱이 기동하지 못한다**. 그런데 위 probe 가 ALB 의 502/503 이 아니라 **앱 자신의 Security 필터가 내는 401** 을 돌려줬다 → 앱이 떠 있다 → **V20 이 prod RDS 에서 통과했다.** PostGIS 확장은 prod 에 있다.

(엄밀히는 "아직 V20 이 적용된 버전이 배포되지 않았을" 가능성이 남지만, 배포된 `/v3/api-docs` 에 realtime 엔드포인트가 있다는 것이 같은 커밋의 배포를 증명한다.)

### 3.3 OSRM — **저장소 안에 "띄우는" 장치가 없다**

| 확인한 것 | 결과 |
|---|---|
| `application.yaml:119-120` | `osrm.base-url: ${OSRM_BASE_URL:http://localhost:5000}` — 기본값이 localhost:5000 |
| `application-prod.yaml` / `application-dev.yaml` | `osrm` 오버라이드 **없음** |
| 저장소 전체 `grep -i osrm` | `.gitignore`, `application.yaml`, `infra/osrm/Dockerfile`, `osrm-image.yml` **뿐** |
| `.github/workflows/osrm-image.yml` | ECR(`moyeota-osrm`)에 **이미지를 올리는 것까지만**. 배포·실행 단계 없음 |
| `.github/workflows/deploy.yml:36-73` | `bootJar` → S3 → CodeDeploy. OSRM 관련 단계 **없음** |
| `appspec.yml` | `app.jar` 하나만 배포 |
| `infra/ec2/bootstrap.sh:17` | `openjdk-25-jre-headless prometheus-node-exporter prometheus-redis-exporter alloy ruby unzip` — **컨테이너 런타임(docker/podman)을 아예 설치하지 않는다** |
| `infra/ec2/moyeota.service` | `java -jar app.jar` 하나 |
| `docker-compose.yml` | redis + postgis **만**. OSRM 서비스 없음 (로컬 개발용이고 app 서비스조차 없다) |

→ **앱 호스트에서 OSRM 컨테이너가 돌 수 없다.** 런타임이 없다.
→ `OSRM_BASE_URL` 은 SSM Parameter Store(`/moyeota/prod/*`, `load-env.sh:18-20`)에서 올 수 있고 **그 값은 우리가 읽을 수 없다 → 미확인**. 별도 호스트/ECS/EC2 에 저장소 밖에서 띄워 뒀을 가능성은 있다.

**미확인 판정**: OSRM 이 prod 에서 실제로 응답하는지 **확인 불가**. 하지만 저장소 증거는 전부 "아직 안 띄웠다" 쪽을 가리킨다.

### 3.4 확인할 수 없는 이유 — 실패가 숨는 구조

OSRM 이 죽어 있어도 **후보가 0건이면 200 `[]` 가 나온다** (`OsrmWalkingClient.java:35,50` → `PartyService.java:235` 가 빈 스트림). 서비스 초기에는 300m×300m AND 조건을 만족하는 방이 거의 없으니 **대부분의 호출이 0건**이다.

| OSRM 상태 | 후보 0건 | 후보 1건 이상 |
|---|---|---|
| 살아 있음 | `200 []` | `200 [...]` |
| 죽어 있음 | **`200 []`** ← 구분 불가 | `502 ROUTE_SEARCH_FAILED` |

즉 **`200 []` 는 OSRM 건강의 증거가 아니다.** 「붙여서 눌러 봤는데 괜찮던데요」로는 검증되지 않는다. 확인하는 유일한 길은 백엔드에 직접 묻는 것이다(§7-1).

타임아웃도 함께 봤다: OSRM 클라이언트는 connect 2s + read 2s (`OsrmApiConfig.java:16-18`), 호출 2회 → 최악 약 8초. 앱 OkHttp 는 connect 10s / read 20s (`NetworkModule.kt:44-45`) 로 더 길다 → **앱이 먼저 끊지 않고 서버의 502 를 제대로 받는다.** 이 부분은 문제 없다.

### 3.5 테스트 커버리지 — 이 경로는 한 번도 실행된 적이 없다

```
git grep "findNearbyParties|findMatchesWithinRadius|realtimeMatch|RealTimeMatching" -- 테스트 전체
→ PartyJpaTest.java:121 한 줄뿐
```
- `PartyJpaTest.java:117-123`: 인메모리 가짜가 `return List.of();` — 「PostGIS 반경 쿼리는 메모리로 흉내 내지 않는다」
- `PartyServiceTest.java:983-993`: `FakeWalkRouteFinder` 가 좌표마다 60초 고정 반환 — **기존 테스트를 컴파일되게 만들려고 주입한 것**이고 `realtimeMatch` 자체를 호출하는 테스트는 없다
- `PartyControllerTest` 에 realtime 테스트 없음
- `src/integrationTest/` 에도 없음 (`PartyLockQueryPostgresTest` 뿐)

→ **네이티브 PostGIS 쿼리, record 프로젝션(네이티브 쿼리에 record 를 매핑한다 — Spring Boot 4.1.0), OSRM 인덱스 매핑, 2단 랭킹 — 어느 것도 테스트로 한 번 지나간 적이 없다.** 컨트롤러 단위 테스트도 없다. `355d6bd` 는 "깨진 테스트를 고친" 커밋이고 새 경로를 덮은 커밋이 아니다.

참고로 OSRM 인덱스 산술은 손으로 검산해 봤고 **맞다**: `fromTo` 는 sources=0 / destinations=1..N 에 `durations[0][i]`, `toFrom` 은 sources=0..N-1 / destinations=N 에 `durations[i][0]`. 여기서 틀린 건 없다.

---

## 4. 300m 반경 / 상위 5개가 사용자에게 어떻게 보이나

### 4.1 300m — 양쪽 AND 라 체감상 매우 좁다

- 출발지 **그리고** 도착지가 **각각** 300m 안. 한쪽만 맞으면 안 나온다.
- 도보 300m ≈ 4분. 「출발지도 4분, 도착지도 4분 안쪽」 — 합리적인 범위지만, **매칭이 성립할 확률이 매우 낮다**.
- **초기 서비스에서는 거의 항상 빈 목록이다.** UI 를 "항상 뭔가 보이는 영역"으로 설계하면 빈 상태가 기본 상태가 된다. → 빈 결과를 **실패로 보이게 하지 말 것**. 「지금은 맞는 방이 없어요 → 방 만들기」로 자연스럽게 흘려야 한다.
- **방이 저장하는 500m 와 어긋난다**: 방 생성 시 `Radius.DEFAULT` = 500m 가 `departureRadius`/`destinationRadius` 로 저장되는데(`PartyService.java:80`), 이 쿼리는 그 값을 **읽지 않고** 서버 상수 300m 를 쓴다(`PartyService.java:58`). 앱은 이미 반경을 도메인에 태우지 않기로 결정해 뒀고(`Ride.kt:62-67` 「아무것도 걸러내지 않는 숫자를 사용자가 고른 조건으로 보여 주게 된다」) 그 판단은 **여전히 유효하다**. 300m 도 사용자가 고를 수 없는 서버 상수다 — 화면에 「300m 이내」라고 못 박기보다 「걸어서 N분」(= `totalSeconds`)으로 보여주는 게 정직하다.

### 4.2 상위 5개 — 짧은 추천 목록이지 탐색 목록이 아니다

- 최대 5개. 페이지네이션·「더 보기」 없음. → **짧은 카드 리스트**가 맞고, 무한 스크롤은 틀리다.
- OSRM 이 null 을 준 후보가 조용히 빠지므로 **0~5개 사이 아무 길이나 올 수 있다**. 「5개 고정」을 가정한 UI(5칸 플레이스홀더 등)는 금물.
- 직선거리 5개 절단이 도보 랭킹보다 **먼저**라, 사용자 눈에는 「분명 걸어서 더 가까운 방이 있는데 안 보인다」가 가능하다. 다만 300m AND 조건에서 6개 이상 몰리는 밀도는 초기에 사실상 없으므로 **현 단계에서는 실질 영향 없음**.

### 4.3 `totalSeconds` 를 어떻게 보여줄까

`totalSeconds` = 탑승지까지 걷는 시간 **+** 하차지에서 목적지까지 걷는 시간. **차 타는 시간은 안 들어있다.**

- 「총 7분 걸어요」처럼 **도보임을 명시**해야 한다. 「7분 소요」라고 쓰면 사용자는 목적지까지 7분으로 읽는다 — 심각한 오독.
- 두 구간을 나눠 보여줄 수는 **없다**(합만 온다). 나눠 보여주고 싶다면 백엔드 요청 사항(§7-3).
- 정렬은 이미 서버가 해 줬다. 앱에서 다시 정렬하지 말 것 — 받은 순서가 계약이다.

### 4.4 「인원」을 못 보여주는 게 가장 아픈 지점

후보 카드에 「2/3명」을 못 쓴다(§1.3). 사용자 입장에서 2/3 방(바로 출발)과 1/3 방(언제 찰지 모름)은 완전히 다른 선택지인데, 둘을 구분해 보여줄 수가 없다. 기존 Explore 카드보다 **정보가 적은** 추천 카드가 된다. → §7-2.

---

## 5. 앱 현재 구현 대조

| 앱 | 파일 | 하는 일 |
|---|---|---|
| 17 Explore 지도 | `presentation/.../feature/explore/ExploreRoute.kt:211` | `repository.getPartiesWithin(area.sw/ne)` — 뷰포트로만 조회, 폴링 |
| Repository 계약 | `domain/.../repository/RideRepository.kt:28-33` | `getPartiesWithin(swLat, swLng, neLat, neLng): List<Ride>` |
| 구현 | `data/.../repository/RemoteRideRepository.kt:68-73` | `api.getPartiesWithin(...).list.map { it.toRide() }` |
| API | `data/.../remote/MatchingApi.kt:38-44` | `@GET("api/v1/matching/rooms")` + 4 `@Query` |
| DTO | `data/.../remote/dto/PartyDtos.kt:9-27` | `PartyListResponse.PartyItem` |
| 15 도착지 입력 | `presentation/.../feature/home/DestinationScreen.kt` | 출발·도착 검색 → 「경로 확인하기」 |
| 16 경로 미리보기 모달 | `presentation/.../feature/home/DestinationConfirmRoute.kt` | `previewRoute` → 요금·경로 → `createParty` |
| 네비게이션 | `presentation/.../core/MainNavGraph.kt:544-578` | `DESTINATION` → `DESTINATION_CONFIRM`(dialog) → `MATCH_WAITING` |
| base URL | `app/build.gradle.kts:45` | `https://api.moyeota.co.kr/` (prod) |

**앱이 이미 적어 둔 공백** — `DestinationScreen.kt:403-405`:
> 「같은 방향 사람만 보여드려요」는 지웠다 — 17 탐색 목록은 **지도 영역**으로만 거른다 (`getPartiesWithin`, 서버도 "출발지가 영역 안인 ACTIVE 방"). **도착지로 거르는 길이 없다.**

이 API 가 바로 그 「도착지로 거르는 길」이다. 연동 가치가 분명한 이유다.

**꽂을 자리도 이미 있다**: 16 모달(`DESTINATION_CONFIRM`)은 출발지·도착지가 **둘 다 확정된** 유일한 지점이고, 바로 다음 동작이 「방 만들기」다. 「만들기 전에 이미 있는 방을 보여준다」가 정확히 들어맞는다.

---

## 6. 앱이 붙어야 하나 — **DTO 층까지는 지금, 화면 노출은 조건부 보류**

### 6.1 판단

| | |
|---|---|
| **붙이는 쪽 근거** | ① prod 에 배포돼 있다 ② 앱이 주석으로 명시한 공백을 정확히 메운다 ③ 기존 API 를 안 깨는 순수 추가 ④ 꽂을 화면(16)이 이미 있다 |
| **보류 근거** | ① OSRM prod 기동 **미확인** + 저장소에 띄우는 장치 없음(§3.3) ② 실패가 `200 []` 로 숨어 **스모크 테스트로 검증 불가**(§3.4) ③ 백엔드 테스트 커버리지 0(§3.5) ④ `currentMembers` 없어 카드가 기존보다 빈약(§4.4) |

**권고**: §7-1(OSRM 확인)이 "예" 로 돌아오기 전에는 **사용자에게 노출하지 않는다.** 노출하면 「추천 방이 생기는 순간 에러」라는, 가장 안 좋은 타이밍의 실패를 사용자가 먼저 발견한다.

그렇다고 손놓을 필요는 없다. 계약이 prod OpenAPI 로 **확정**됐으므로 data/domain 층은 지금 만들어도 버려질 위험이 없다.

- **1단계 (지금 가능)**: DTO + 매퍼 + Repository + 단위 테스트. 화면 연결 없음. qa-verifier 검증 대상은 매퍼 테스트까지.
- **2단계 (§7-1 확인 후)**: 16 모달에 후보 섹션 노출. compose-builder 작업.

### 6.2 1단계 연동 계획

> ⚠️ 아래는 **계획이다. 이번 조사에서 코드는 한 줄도 쓰지 않았다.**

**DTO** — `data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt` 에 추가 (새 파일 만들지 않는다)

```
@Serializable
data class RealTimeMatchingRequestDto(
    val departureLat: Double,
    val departureLng: Double,
    val destLat: Double,       // ← destinationLat 아님. 서버 record 필드명 그대로
    val destLng: Double,
)

@Serializable
data class MatchCandidateResponse(
    val summary: NearbyPartySummaryDto? = null,
    val totalSeconds: Int = 0,
) {
    @Serializable
    data class NearbyPartySummaryDto(
        val id: Long,                            // ← partyId 아님
        val departureLat: Double? = null,
        val departureLng: Double? = null,
        val destinationLat: Double? = null,
        val destinationLng: Double? = null,
        val departure: String = "",
        val destination: String = "",
        val capacity: Int = 0,
    )
}
```
최상위가 배열이므로 **래퍼 DTO 를 만들지 않는다**.

**API** — `MatchingApi.kt`
```
@POST("api/v1/matching/rooms/realtime")
suspend fun getRealtimeCandidates(@Body request: RealTimeMatchingRequestDto): List<MatchCandidateResponse>
```

**도메인 모델** — `domain/src/main/kotlin/com/moyeota/domain/model/Ride.kt` 에 추가
```
data class MatchCandidate(
    val ride: Ride,              // id/origin/destination/capacity/좌표만 채워진 Ride
    val walkSeconds: Int,        // totalSeconds. 도보 합이라는 걸 이름에 박는다
)
```
`Ride` 를 재사용하되 `members = emptyList()`, `farePerPerson/totalFare = 0`, `status = RECRUITING`(쿼리가 ACTIVE 고정), `currentMembers` 는 **표현할 자리가 없다**(§7-2 로 해결하거나 UI 에서 생략).
필드명을 `totalSeconds` 가 아니라 `walkSeconds` 로 바꾸는 이유: 「총 소요시간」으로 오독되는 걸 도메인 이름에서 막는다(§4.3).

**Repository 시그니처 — compose-builder 에게 통지할 내용**
```
// domain/repository/RideRepository.kt
suspend fun findRealtimeCandidates(
    departureLat: Double,
    departureLng: Double,
    destinationLat: Double,
    destinationLng: Double,
): List<MatchCandidate>
```
- 반환: 서버가 도보 시간 합 오름차순으로 정렬한 **0~5개**. **앱에서 재정렬 금지.**
- 빈 리스트는 **정상**이다(§4.1). 에러로 처리하지 말 것.
- 구현체 **둘 다** 갱신 필요: `RemoteRideRepository`, `DummyRideRepository` (+ 테스트의 Fake 2개: `RemoteRideRepositoryTest.kt:397`, `RemoteActivePartyRepositoryTest.kt:349` 주변)

**에러 변환** — `PartyError.kt` 패턴으로 `RealtimeMatchException` 신설 권장:
| 서버 | 프로퍼티 | 화면 문구(안) |
|---|---|---|
| 502 `ROUTE_SEARCH_FAILED` | `isRouteUnavailable` | 「지금은 추천을 못 가져왔어요」 + 방 만들기로 흘림 |
| 409 `ALREADY_JOINED_OTHER_PARTY` | `isAlreadyInParty` | 후보 섹션 **자체를 숨긴다**(이미 진행 중인 방이 있으면 새 방도 못 만든다) |
| 400 `OUT_OF_SERVICE_AREA` | `isOutOfServiceArea` | 「서비스 지역이 아니에요」 — 단, 우리 DTO 버그를 먼저 의심(§1.2) |

**테스트** — `data/src/test/kotlin/com/moyeota/data/` 에 매퍼 단위 테스트:
1. 최상위 배열 + 중첩 `summary` 역직렬화
2. `id` → `Ride.id`(String) 변환
3. `totalSeconds` → `walkSeconds`
4. `summary` null·필드 누락 방어
5. **빈 배열 → 빈 리스트**(에러 아님)
6. 받은 순서 보존(재정렬 안 함)

`./gradlew :data:testDebugUnitTest` 로 확인.

### 6.3 2단계 — 화면 (compose-builder 협의 사항, §7-1 이후)

- **위치**: 16 `DestinationConfirmRoute` 모달. 경로 미리보기(요금·시간) **아래**, 「방 만들기」 CTA **위**에 후보 섹션.
- **호출 시점**: `previewRoute` 와 **같이 1회**. 폴링하지 않는다(POST 조회 + 후보당 OSRM 2회 호출이라 비싸다).
- **카드 1장**: 출발 라벨 → 도착 라벨, 「걸어서 총 N분」, 「최대 N명」. **인원 현황은 못 쓴다**(§7-2 해결 시 추가).
- **탭하면**: 기존 `JOIN_CONFIRM`(19) 로 `partyId` 를 넘긴다 — 합류 흐름은 **이미 있는 걸 그대로 쓴다**.
- **빈 결과**: 섹션을 아예 그리지 않거나 한 줄 안내. 「없음」을 강조하지 않는다 — 초기에는 이게 기본 상태다.
- **Explore(17) 는 건드리지 않는다.** 뷰포트 조회는 목적이 다르다(§2.1).

---

## 7. 백엔드에 요청할 것 (우선순위 순)

1. **[차단] OSRM 이 prod 에 떠 있나? `/moyeota/prod/OSRM_BASE_URL` 값은?**
   저장소에는 ECR 이미지 빌드 워크플로만 있고 배포·실행 장치가 없다. `bootstrap.sh` 는 컨테이너 런타임을 설치하지 않는다. → **앱 연동 가능 시점이 이 답에 달려 있다.** 안 떠 있다면 지금 prod 에서 이 API 는 후보가 잡히는 순간 502 다.
2. ~~**[높음] `NearbyPartySummary` 에 `currentMembers` 추가**~~ → **해결(Backend #260, 2026-10-24)**
   「2/3명」이 추천 카드에서 가장 중요한 정보인데 없었다. 앱이 메우려면 후보당 `getPartyDetail` = 최대 15 쿼리 + route 폴리라인이고, 상세 폴링이 이미 서버 요청의 45% 였다(#177). `findNearbyParties` 의 `select` 에 서브쿼리 한 줄이면 된다고 적었고, 실제로 그렇게 들어왔다:
   ```sql
   CAST((SELECT COUNT(*) FROM user_match_room um WHERE um.match_id = m.id) AS integer) AS current_members
   ```
   record 가 `Integer currentMembers` 를 9번째 필드로 받는다. **앱은 `Int? = null` 로 받는다** — 0 은 모집 중인 방에서 나올 수 없는 값이라 「모른다」와 구별해야 하기 때문이다(`PartyDtos.kt`).
3. **[높음] OSRM 장애 시 502 대신 degrade 를 고려해 달라**
   지금은 OSRM 이 죽으면 기능 전체가 502 다. 직선거리 순(이미 SQL 이 정렬해 둔 순서) 후보를 `totalSeconds: null` 로 돌려주면, 「걸어서 몇 분」만 빠진 **쓸 수 있는 목록**이 된다. 앱 쪽에서 가장 효과가 큰 변경이다.
4. **[중간] 테스트 추가** → **절반 해결(Backend #260)**
   `realtimeMatch` 와 PostGIS 네이티브 쿼리(record 프로젝션 포함)를 지나는 테스트가 **하나도 없었다**(§3.5). #260 이 `NearbyPartyQueryPostgresTest` 를 붙여 **네이티브 쿼리 → record 매핑**은 덮였다(인원 포함 조회, 정원 찬 방 제외). **`realtimeMatch` 서비스 경로(OSRM 호출·정렬·LIMIT 5)는 여전히 테스트가 없다.**
5. **[낮음] 요청 필드명 `destLat`/`destLng` → `destinationLat`/`destinationLng`**
   같은 컨트롤러의 `OpenPartyRequest` 와 응답 `NearbyPartySummary` 는 `destination*` 인데 요청만 `dest*` 다. 응답의 `id` 도 기존 목록은 `partyId` 다. **클라이언트가 조용히 틀리는 종류의 비대칭**이고, 틀리면 `0.0` 이 가서 400 으로 나타난다. 아직 붙은 클라이언트가 없는 지금이 바꿀 수 있는 유일한 시점.
6. **[낮음] `realtimeMatch` 에 `@Transactional(readOnly = true)` 누락**
   같은 클래스의 `findActiveParties`(:163), `findActivePartiesWithin`(:181) 에는 붙어 있다. 읽기 전용 2쿼리라 실害는 작지만 일관성 문제.
7. **[낮음] 300m 상수 vs 방이 저장하는 500m**
   방은 `Radius.DEFAULT`=500m 를 저장하는데 이 쿼리는 서버 상수 300m 를 쓴다(§4.1). 어느 쪽이 의도인지, 300m 를 설정값으로 뺄 생각인지 확인 필요.

---

## 8. 「미확인」 목록 (추측하지 않은 것)

| 항목 | 상태 | 왜 확인 못 했나 |
|---|---|---|
| **OSRM 컨테이너 prod 기동 여부** | **미확인** | 저장소에 실행 장치 없음. SSM `/moyeota/prod/OSRM_BASE_URL` 은 읽을 권한 없음. 저장소 밖에서 띄웠을 가능성 배제 불가 |
| **`200 [...]` 실응답 1건** | **미검증** | 토큰이 없어 prod 호출 불가(401). 백엔드를 띄우지 않았다(지시 준수) |
| 네이티브 쿼리 → record 프로젝션 실동작 | **미확인** | 테스트가 없고(§3.5) 실호출을 못 했다. Spring Boot 4.1.0 에서 지원될 것으로 보이나 **확인하지 않았다** |
| 300m AND 조건의 실제 매칭 성공률 | **미확인** | prod 데이터 분포를 볼 수 없다 |
| V20 이 prod RDS 에 실제 적용됐는지 | **간접 확인(강함)** | 직접 조회 불가. 앱 기동(401) + 배포된 OpenAPI 에 realtime 존재로 추론(§3.2) |
| `ALREADY_JOINED_OTHER_PARTY` 가 16 모달에서 실제로 발생하는 빈도 | **미확인** | 앱 흐름상 진행 중 방이 있으면 `createParty` 도 막히므로 동일 게이팅으로 보이나 실측 안 함 |
| prod 503 의 원인 | **미확인** | 조사 시점(2026-10-23 21:30경)에는 prod 가 **정상 응답**했다. 배포가 끝난 것으로 보이나 단정하지 않는다 |

### 문서 vs 실제 불일치

- **`backend/http/*.http` 에 실시간 매칭 시나리오가 없다** (`http/matching.http` 포함). 요청 예시의 근거는 **컨트롤러 소스 + 배포된 prod OpenAPI** 다.
- 배포된 OpenAPI 의 `GET /api/v1/matching/rooms` 는 **지도 영역 변형 하나만** 보여주고 `swLat` 등을 `required: true` 로 표시한다. 하지만 코드에는 파라미터 없는 전체 목록 핸들러도 **그대로 있다**(`PartyController.java:71-74`) — springdoc 이 같은 경로의 두 핸들러를 하나로 합친 문서 artifact 다. 앱의 `getParties()`(무파라미터)는 **계속 동작한다**. 실제 코드를 우선한다.
- `api-integration` 스킬 문서는 컨트롤러 위치를 `matching/interfaces/PartyController.java` 로 적어 두었으나 실제 경로는 **`matching/party/PartyController.java`** 다. `MatchingApi.kt:19` 의 주석도 옛 경로를 가리킨다. (스킬·주석 갱신 필요 — 이번 작업 범위 밖)
