# 93 · main → develop 병합 해소 리포트 (승객 앱)

- 브랜치: `merge/main-into-develop` (origin/develop 기반 + `git merge origin/main` 충돌 8건 해소)
- 판단 원칙: 인프라(인증·GPS·지도·타 화면) = main / 신고 UX·API shape = develop(확정 흐름도 + 현행 백엔드)
- 스펙 진실: `backend/.../report/interfaces/ReportController.java` (컨트롤러 소스 기준 — **실서버 미검증**)
  - `POST /api/v1/reports` body `{partyId?, latitude?, longitude?}` → 200 `{reportId}` (@CurrentUser)
  - `PATCH /api/v1/reports/call-result` body `{called}` → 204 (**reportId 경로 없음**, @CurrentUser)

## 파일별 채택 내역

| 파일 | 해소 |
|---|---|
| `ReportApi.kt` (add/add) | **develop 채택**. call-result 는 reportId 없는 경로. "인증 계층 없음 — 401" 주석은 main 병합으로 인증이 생겨 삭제, apiClient(Bearer) 필수 주석으로 교체 |
| `dto/ReportDtos.kt` (add/add) | **develop 채택** (`partyId: Long?` — 서버 record 박싱 타입). main 의 "reporterId 를 싣지 않는다" 주석만 이어받음 |
| `domain/RideRepository.kt` | **main 베이스**(previewRoute 유지) + develop 신고 계약 2개 통일: `reportEmergency(partyId: Long?): Long`, `confirmEmergencyCall(called: Boolean)`. develop 측 `cancelReady`/`startMatching` 은 **제거** (아래) |
| `RemoteRideRepository.kt` | **main 베이스**(previewRoute·toAssignedDriver) + develop 신고 구현. 고정 좌표(서울시청) TODO 를 **주입형 실측 소스** `currentLocation: suspend () -> Pair<Double,Double>?` 로 교체 — 측위 실패 시 null 전송(서버 허용, 가짜 좌표 금지). confirm 은 reportId 없이 PATCH. 신고 API 는 Bearer 붙는 apiClient 로 생성 |
| `NetworkModule.kt` | **양측 합집합**: `dispatch`+`user`(main) + `report`(공통) — 전부 apiClient(Bearer + 401 재발급) 소속 |
| `AppContainer.kt` | **main 베이스**(SessionManager·BASE_URL·auth) + `rideRepository = RemoteRideRepository(apis.matching, reportApi = apis.report, currentLocation = ReportLocationProvider::current)`. main 의 `reportRepository` 제거 |
| `EmergencyScreen.kt` | **develop 채택** (사유 없음 · 3초 홀드 → 신고 발사 + 즉시 112 ACTION_DIAL → ON_RESUME 복귀 → "통화하셨나요?" 다이얼로그 → PATCH). main 의 인셋 정리만 이식: `StatusBarMock`→`StatusBarSpacer`, 홈 인디케이터 목업→`NavigationBarSpacer` |
| `MainNavGraph.kt` | **main 베이스**(인증 플로우·전 화면 배선). 27 등록부만 develop `EmergencyRoute(repository = rideRepository, partyId = activePartyId ?: selectedPartyId ?: createdPartyId, onBack, onReportSubmitted)` — main 의 실 partyId(`activePartyId`) 우선. `reportRepository` 파라미터 사슬(MainNavGraph→MainNavHost→MainActivity) 제거 |

## 제거한 main 측 자체 신고 스택 (develop 계약으로 통합)

- `domain/repository/ReportRepository.kt`, `domain/model/NewReport.kt`
- `data/repository/RemoteReportRepository.kt`, `data/remote/ReportMappers.kt`
- `presentation/feature/chat/EmergencyRoute.kt` (develop 의 EmergencyRoute 가 EmergencyScreen.kt 안에 있어 같은 패키지 중복 선언이었음)
- `data/src/test/.../ReportMappersTest.kt` (구스펙 `{reportId}/call-result` 전제)
- `AuthenticatedPathContractTest`: `api/v1/reports/{reportId}/call-result` 단언 → `api/v1/reports/call-result` 로 갱신 (main 주석은 "백엔드가 되돌렸다(실측)"였으나 현행 컨트롤러 소스가 reportId 없는 경로 — 컨트롤러 우선, 실서버 미검증 플래그)

## 제거한 develop 측 잔재

- `RideRepository.cancelReady/startMatching`: 병합된 `MatchingApi`(main 판)에 대응 엔드포인트가 없고(main 이 백엔드 @CurrentUser 전환에 맞춰 정리), presentation 어디에서도 호출하지 않으며, 병합된 `DummyRideRepository` 도 구현하지 않음 → 컴파일 불가 데드 코드라 제거

## 새로 추가

- `app/.../ReportLocationProvider.kt`: FusedLocation 1회 측위(권한 없으면 즉시 null, fresh fix 3s 타임아웃 → lastLocation 1s 폴백 → null). Task→suspend 어댑터 자체 구현(추가 의존성 없음). `app/build.gradle.kts` 에 `play-services-location` 추가 (기존 presentation 과 같은 버전 카탈로그 항목)

## Repository 시그니처 (compose-builder/qa 공유용)

```kotlin
suspend fun reportEmergency(partyId: Long?): Long      // POST /api/v1/reports, 실패 시 한국어 IllegalStateException — 화면은 다이얼 우선
suspend fun confirmEmergencyCall(called: Boolean)      // PATCH /api/v1/reports/call-result (reportId 없음)
```

## 검증

- 충돌 마커 0 · `./gradlew :app:assembleDebug :data:testDebugUnitTest` **BUILD SUCCESSFUL** — :data 테스트 110건 / 실패 0 (ReportDtosTest·AuthenticatedPathContractTest 포함)
- push 안 함 (리더 검토 대기)

## 남은 리스크

1. **실서버 미검증**: 신고 2개 엔드포인트는 컨트롤러 소스 기준. 백엔드가 call-result 경로를 오간 이력이 있어(main 주석) 재변경 시 `AuthenticatedPathContractTest` 가 먼저 깨진다.
2. **rideSummary 하드코딩**: 27 화면의 "지금 타고 있는 차" 카드는 기본값("부산대 정문 → 서면역 · 12가 3456"). main 도 실데이터를 내려주지 않았음 — 26 운행 중 화면이 파티 상세를 갖게 되면 `EmergencyRoute(rideSummary=)` 로 실값 주입 필요.
3. **신고 좌표 측위**: 위치 권한이 한 번도 허용되지 않았으면 좌표 null 로 신고된다(정상 동작이나 운영팀 대응 정보 감소). 권한 요청은 지도 화면 흐름에 위임.
4. `RemoteRideRepositoryTest` 는 신고 미배선 생성(`RemoteRideRepository(api)`)만 다룬다 — 신고 성공 경로는 ReportDtosTest(직렬화)와 실기 검증으로 커버.
