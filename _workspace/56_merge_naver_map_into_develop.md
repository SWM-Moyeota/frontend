# 56 · feature/naver-map 계열(PR #5·#6·#10) → develop 통합

- 작성: 2026-09-09
- 브랜치: `merge/naver-map-into-develop` (워크트리 `…/scratchpad/merge-check`, **미푸시**)
- 머지 커밋: `1f6fa98` (parents `5bc9993` develop + `1d54873` feature/mvp1-followup)

## 0. 머지 대상 선정

`origin/feature/naver-map` 대신 `origin/feature/mvp1-followup` 을 머지했다.

- `git diff origin/feature/mvp1-followup origin/feature/naver-map` = `.github/workflows/{auto-assign,claude-review}.yml` 뿐 (7줄).
  즉 mvp1-followup 이 naver-map 트리의 상위 집합이고, 유일한 차이인 워크플로는 mvp1-followup 쪽이
  이미 develop 과 동기화된 버전이다(`git diff origin/develop origin/feature/mvp1-followup -- .github/` = 빈 diff).
- naver-map 에만 있는 커밋 2개(`e48b575`, `7780531`)는 mvp1-followup 을 naver-map 으로 끌어온 머지 커밋이라 내용 기여 없음.
- 정책 4(워크플로는 develop 유지) 는 자동 충족 — 머지 후에도 `git diff origin/develop HEAD -- .github/` 빈 diff 확인.

## 1. 충돌 파일별 해결

머지 충돌은 5건. 그 외 파일은 자동 병합.

### 1-1. `app/build.gradle.kts` — 양쪽 유지 (정책 3)
develop 은 `play.services.location`(ReportLocationProvider), mvp1-followup 은 `kotlinx.coroutines.android`(FCM
fire-and-forget) 를 각각 추가. **둘 다 유지**, 주석도 둘 다 보존.

### 1-2. `app/.../AppContainer.kt` — 양쪽 배선 합침 (정책 1+2)
- develop: `reportLocationProvider` + `RemoteRideRepository(apis.matching, reportApi=…, currentLocation=…)`
- mvp1-followup: `fcmTokenRegistrar` + `RemoteAuthRepository(auth, user, session, fcmTokenRegistrar)` +
  `RemoteRideRepository(apis.matching, sessionManager)`

결과:
```kotlin
private val reportLocationProvider = ReportLocationProvider(context.applicationContext)
val fcmTokenRegistrar = FcmTokenRegistrar(apis.user, sessionManager)
val authRepository = RemoteAuthRepository(apis.auth, apis.user, sessionManager, fcmTokenRegistrar)
val rideRepository = RemoteRideRepository(
    apis.matching, sessionManager,
    reportApi = apis.report, currentLocation = reportLocationProvider::current,
)
```

### 1-3. `data/.../RemoteRideRepository.kt` — import/KDoc 충돌만, 생성자는 자동 병합
생성자는 git 이 이미 `(api, session, local, reportApi, currentLocation)` 로 합쳐 놓았다. 충돌은 import 블록과
클래스 KDoc 뿐이라 **양쪽 import 유지 + KDoc 통합**(session 주입 이유 + reportApi/currentLocation 기본값 근거).

### 1-4. `presentation/.../MainNavGraph.kt` — 양쪽 파라미터 모두 제거
충돌 3곳 모두 `userSession: UserSession`(develop) vs `reportRepository: ReportRepository`(mvp1-followup) 의
파라미터 선언·전달이었다. **둘 다 삭제**했다. 근거:

- `reportRepository`: develop 이 `ReportRepository`/`RemoteReportRepository`/`ReportMappers`/`NewReport` 를 삭제하고
  신고를 `RideRepository.reportEmergency()` 로 흡수했다(정책 1). 머지 결과 트리에 그 파일들이 없고,
  본문의 `EmergencyRoute(repository = rideRepository, …)` 도 develop 판이 살아남았다.
- `userSession`: mvp1-followup 이 `DestinationConfirmRoute`/`RideDetailRoute`/`ChatRoute` 에서 `userSession`
  파라미터를 제거하고 Repository 에 세션을 주입하는 구조로 바꿨다(정책 2). 머지 후 `presentation/` 전체에
  `userSession` 사용처가 0건이라 파라미터만 남아 있었다. 미사용 import(`UserSession`, `ReportRepository`)도 제거.

### 1-5. `app/.../MainActivity.kt` — 위와 동일
`MainNavGraph(...)` 호출에서 `userSession = container.userSession` / `reportRepository = …` 두 인자 모두 제거.

## 2. 정책을 벗어나거나 리더 확인이 필요한 결정

### D-1. 신고 `call-result` 경로: develop 판 채택 (`{reportId}` 없음) — **확인 요청**
정책 1 단서("naver-map 계열이 신고 API에 `@CurrentUser`·경로 변경을 반영했다면 그 계약 변경은 유지")에 걸리는 지점.
실제로는 **양쪽 다 `@CurrentUser`(reporterId 없음)** 이고, 다른 건 call-result 경로뿐이다.

| | mvp1-followup | develop (채택) |
|---|---|---|
| POST 신고 | `api/v1/reports` | `api/v1/reports` (동일) |
| PATCH 통화결과 | `api/v1/reports/{reportId}/call-result` | `api/v1/reports/call-result` |
| `ReportRequestDto.partyId` | `Long` (필수) | `Long?` (백엔드 record 가 박싱 타입) |

develop 을 택한 근거: (a) 정책 1 이 "신고 흐름은 develop 확정안"이라고 못박음, (b) develop 쪽 주석이
"백엔드가 `{reportId}` 경로를 오간 이력이 있고 **현행 컨트롤러는 reportId 없이 토큰 주체의 최근 신고에 기록**한다"고
명시, (c) `AuthenticatedPathContractTest` 가 develop 경로를 고정하고 통과한다.
→ **백엔드 현행 컨트롤러가 정말 `/api/v1/reports/call-result` 인지 리더가 최종 확인 요망.**
`@CurrentUser`·reporterId 제거라는 계약 변경 자체는 양쪽 동일하므로 손실 없음.

### D-2. `AppContainer.userSession` 은 남겨 뒀다
`MainNavGraph` 에서 파라미터를 뺐으므로 지금은 사용처가 없는 public val 이다. 삭제하지 않은 이유는
AppContainer 가 DI 컨테이너이고 "화면이 세션을 얻는 단일 출처"라는 문서적 역할이 있어서다.
정리를 원하면 한 줄 삭제로 끝난다(정책 2 가 "제거 또는 유지"를 허용).

### D-3. `EmergencyRoute.kt` 파일 소멸은 의도된 결과
mvp1-followup 에는 `feature/chat/EmergencyRoute.kt` 가 별도 파일로 있었으나, develop 판은 같은 컴포저블을
`EmergencyScreen.kt` 안에 통합해 뒀다. 머지 결과 `EmergencyScreen.kt` 는 **develop 과 바이트 단위로 동일**하고
(`git diff origin/develop -- …/EmergencyScreen.kt` 빈 diff) `EmergencyRoute` 는 그 안에 있다. 컴파일 통과로 확인.

## 3. 검증

### 빌드·유닛 테스트 — 통과
```
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain
BUILD SUCCESSFUL in 26s   (126 tasks)
```
- `:data:testDebugUnitTest` 188 tests / 0 failures
- `:presentation:testDebugUnitTest` 22 tests / 0 failures
- 경고 2건은 기존 것(`quadraticBezierTo` deprecated, `MyRidesScreen` always-true) — 이번 머지와 무관.

### 에뮬레이터 스모크 (Pixel_6 콜드부트, `https://api.moyeota.p-e.kr/`, 크래시 0)
휴대폰 미사용. 진행 경로와 결과:

| # | 화면 | 결과 |
|---|------|------|
| 1 | 앱 실행 → 홈 | 세션 복원되어 바로 홈. 네이버 실지도 + "스모크이님, 좋은 저녁이에요"(auth v2 프로필 조회) |
| 2 | 합승 탭 | 실지도 + "진행 중 탑승 · 서면역 방향" 배너, 반경 내 대기 없음 문구 정상 |
| 3 | 내 탑승 → 실시간 위치 보기 (26 운행 중) | 실지도·타임라인·보호자 공유 정상 |
| 4 | **27 긴급 신고** | **develop 확정안 확인** — "3초간 길게 눌러 신고" 버튼 + "3초를 채우면 즉시 112 통화 화면이 열려요" |
| 5 | 홈 → 15 목적지 검색 | 장소 검색 API 실결과(서면역 부산1호선 1번출구) |
| 6 | 16 도착지 확인 | 실경로·요금 추정(예상 1분 · 총 4,800원), 인원/반경/매칭방식 UI 정상 |
| 7 | 21 매칭 대기 | 방 생성 성공, "스모크이 **나**" 배지 = 세션 주입 isMe 판정 동작 |
| 8 | 그만 찾기 → 홈 | 방 나가기 성공(테스트 데이터 잔여 없음) |

- `adb logcat -b crash` 비어 있음, `FATAL EXCEPTION` 0건.
- 스모크 후 에뮬레이터 종료 완료(`adb devices` 빈 목록).
- 미검증: **로그인 화면 자체**(이 AVD 에 기존 앱 데이터가 남아 세션이 복원됨). 인증 v2 로그인 신규 경로는
  유닛 테스트(`RemoteAuthRepositoryTest`, `AuthMappersTest`)로만 커버됨.

## 4. 리더 확인 필요 사항

1. **D-1** 신고 `call-result` 경로 (`/api/v1/reports/call-result`, reportId 없음) 가 백엔드 현행과 맞는지.
2. **D-2** `AppContainer.userSession` 미사용 public val 을 남길지 지울지.
3. 푸시·PR 생성은 지시대로 하지 않았다. `merge/naver-map-into-develop` 은 워크트리
   `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/merge-check`
   에 커밋만 되어 있다.
