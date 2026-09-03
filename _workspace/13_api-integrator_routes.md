# 13. API 연동 — 경로 미리보기 (POST /api/v1/matching/routes)

작업일: 2026-09-01 / 담당: api-integrator
선행 산출물: `_workspace/01_api-integrator_report.md` 4절(호출 불가 판정)의 후속 — **봉인 해제 완료**

## 1. 연동한 엔드포인트

| 메서드 | 경로 | 비고 |
|---|---|---|
| POST | `/api/v1/matching/routes` | 방 생성 전 좌표만으로 보는 경로 미리보기 |

백엔드가 `@GetMapping` → `@PostMapping` 으로 수정하면서 열렸다.
(이전: GET + `@RequestBody` 조합이라 OkHttp 5.1.0 이 요청 생성을 거부 — "method GET must not have a request body")

소스: `backend/.../matching/interfaces/PartyController.java:51` `preView(@RequestBody RouteRequest req)`

## 2. 실제 요청/응답 shape — **실서버 검증 완료** (localhost:8080)

요청 (`RouteRequest` record, 필드 4개 모두 primitive `double` → null 불가):

```json
{"departureLat":35.2313,"departureLng":129.0838,"destinationLat":35.1580,"destinationLng":129.0594}
```

응답 (컨트롤러가 `RoutePreviewResponse` 가 아니라 **도메인 record `RouteEstimate` 를 그대로 반환** —
필드명은 양쪽이 같아 앱에는 영향 없음. `Integer`/`String` 이라 셋 다 null 가능):

```json
{"estimateFare":12800,"estimateTime":35,"path":"ub`vEwtzrW?BQxA@JDNFRJNBL?Lv@TjBh@…"}
```

curl 실행 결과 그대로다(축약). 요금·시간·폴리라인 모두 Naver Directions 실제 응답 기반.

### 주의: 폴리라인 필드명이 두 벌이다

| 엔드포인트 | 폴리라인 필드명 |
|---|---|
| `POST /matching/routes` | **`path`** |
| `GET /matching/rooms/{id}`, `POST /matching/rooms` | `route` |

같은 인코딩 폴리라인인데 이름이 다르다. DTO 를 공유하지 말 것.

## 3. 확정 시그니처 — **변경 없음** (순수 구현 교체)

```kotlin
// domain/repository/RideRepository.kt
suspend fun previewRoute(
    departureLat: Double,
    departureLng: Double,
    destinationLat: Double,
    destinationLng: Double,
): RouteEstimate
```

```kotlin
// domain/model/Dispatch.kt (기존 그대로)
data class RouteEstimate(
    val estimatedFare: Int,
    val estimatedMinutes: Int,
    val encodedPath: String,
)
```

시그니처·도메인 모델 모두 이전과 동일하다. **compose-builder 쪽 변경 불필요** —
달라진 것은 "항상 예외를 던지던 구현이 이제 실제 값을 돌려준다"는 동작뿐이다.

매핑: `estimateFare → estimatedFare`, `estimateTime → estimatedMinutes`, `path → encodedPath`.
서버가 null 을 주면 각각 `0 / 0 / ""` 로 방어한다(`PartyMappers.toRouteEstimate`).

## 4. 변경 파일

| 파일 | 변경 |
|---|---|
| `data/remote/MatchingApi.kt` | 주석 처리된 구식 선언 2안 삭제 → `@POST("api/v1/matching/routes")` + `@Body RouteRequestDto` 실선언 |
| `data/remote/dto/PartyDtos.kt` | 주석만 갱신(GET→POST, 반환이 도메인 record 임을 명시). `RouteRequestDto`·`RouteEstimateResponse` 는 이미 정의돼 있어 재사용 |
| `data/remote/PartyMappers.kt` | 변경 없음 — `toRouteEstimate()`, `routeRequestDto()` 기존 함수 그대로 사용 |
| `data/repository/RemoteRideRepository.kt` | `throw ApiNotAvailableException` → `api.previewRoute(routeRequestDto(...)).toRouteEstimate()` |
| `data/repository/DummyRideRepository.kt` | 상수 반환 → 직선거리 기반 요금·시간 + 출발·도착 2점 인코딩 폴리라인 |
| `domain/repository/RideRepository.kt` | KDoc 갱신(호출 불가 경고 제거) |
| `domain/repository/ApiNotAvailableException.kt` | **삭제** — 프로덕션 코드에 다른 사용처 없음(grep 확인, `_workspace` 문서 언급만 잔존) |

### DummyRideRepository 더미 산식

서울 택시 기준 `4,800원 + 직선거리km × 1,300원`, `직선거리km ÷ 22km/h × 60분 + 3분`.
경로는 출발·도착을 잇는 2점짜리 Google Encoded Polyline(precision 5)이라 지도에 직선이 그려진다.
빈 문자열 상수보다 화면 확인에 쓸모 있고, 인코딩 형식이 서버 `path`/`route` 와 동일하다.

## 5. 테스트

추가/갱신:
- `PartyMappersTest.경로 미리보기 요청 본문 필드명이 백엔드 RouteRequest 와 일치한다` (신규)
  — 직렬화 결과를 문자열로 고정. 필드명이 하나라도 어긋나면 Spring 이 조용히 `0.0` 으로 바인딩해
  엉뚱한 경로가 나오므로 회귀 방지 가치가 크다.
- `RemoteRideRepositoryTest.경로 미리보기는 좌표 4개를 본문으로 보내고 path 를 encodedPath 로 돌려준다` (교체)
  — 기존 "호출 불가 예외" 테스트를 대체. Fake 응답은 실서버 body 축약본.
- `RemoteRideRepositoryTest.더미 경로 미리보기는 거리에 비례한 요금과 두 점짜리 폴리라인을 돌려준다` (신규)
- `PartyMappersTest` 의 기존 `path → encodedPath` / null 방어 테스트 2건은 그대로 통과.

결과:

```
./gradlew :data:testDebugUnitTest :domain:compileDebugKotlin --console=plain
BUILD SUCCESSFUL in 6s

./gradlew :app:assembleDebug --console=plain
BUILD SUCCESSFUL in 2s
```

## 6. 후속 / 미해결

- **presentation 잔여 주석 2곳(코드 동작엔 영향 없음, 이번 범위 밖)** — "previewRoute 는 GET+body 라
  앱에서 호출 불가"라는 문구가 남아 있다. 이제 사실이 아니다:
  - `presentation/.../feature/matching/DispatchStatusScreen.kt:135`
  - `presentation/.../feature/home/DestinationConfirmModal.kt:173-174`
- **미활용 기능** — 엔드포인트는 열렸지만 아직 호출하는 화면이 없다.
  `DestinationConfirmModal`(도착지 확인 모달)은 방 생성 전이라 폴리라인이 없어 마커만 찍고 있는데,
  이제 `previewRoute` 로 경로·예상요금을 채울 수 있다. compose-builder 판단 필요.
- **qa-verifier 교차 검증 요청** — 위 "미활용" 상태 확인(연동은 됐으나 화면 미연결)과,
  실기에서 방 생성 플로우가 여전히 정상인지(회귀 없음) 확인.
