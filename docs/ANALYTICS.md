# 행동 이벤트 (Mixpanel) — Android

iOS(`frontend-ios/docs/ANALYTICS.md`)와 **같은 계약**이다. 이벤트 이름·프로퍼티 키·값 상수·기록 시점이 1바이트도 다르면
Mixpanel 퍼널이 플랫폼별로 갈라진다. 바꿔야 하면 iOS·보드를 같이 바꾼다.

## 구조

```
[화면/ViewModel]  행동이 "성공한 자리"에서 Analytics.track(event) 한 줄
       ▼
[domain Analytics]  sealed class 로 이벤트 이름·프로퍼티를 결정. Mixpanel 을 모른다. sink 가 없으면 무동작
       ▼
[app MixpanelAnalyticsReporter]  SDK 초기화 / track / identify·reset / super property "build"
       ▼
[Mixpanel 프로젝트 codingforest (4080134) — iOS 와 같은 프로젝트]
```

| 자리 | 파일 |
|---|---|
| 이벤트 정의·sink·변환 함수(`administrativeArea`·`ageBand`) | `domain/src/main/kotlin/com/moyeota/domain/analytics/Analytics.kt` |
| 생성·합류 실패 사유(도메인 예외, iOS 와 같은 프로퍼티 분기) | `domain/src/main/kotlin/com/moyeota/domain/model/PartyError.kt` 의 `PartyOpenException`·`PartyJoinException` |
| Mixpanel 리포터 (SDK 를 아는 유일한 파일) | `app/src/main/java/com/moyeota/app/MixpanelAnalyticsReporter.kt` |
| 설치 호출 | `app/src/main/java/com/moyeota/app/MoyeotaApplication.kt` (컨테이너 조립 직후) |
| `view_screen` | `presentation/.../core/MainNavGraph.kt` (`currentBackStackEntryFlow`) |
| `complete_signup` | `presentation/.../feature/auth/SignupSubmitRoute.kt` |
| `complete_login` | `presentation/.../feature/auth/LoginFormRoute.kt` |
| `click_create_party` · `request_match(create)` · `fail(create)` | `presentation/.../feature/home/DestinationConfirmRoute.kt` (`createFailureReason`) |
| `click_join_party` · `request_match(join)` · `fail(join)` | `presentation/.../feature/explore/JoinConfirmRoute.kt` (`joinFailureReason`) |
| `complete_match` · `cancel_match` · `finish_party` | `presentation/.../feature/matching/MatchWaitingRoute.kt` (`publish` → `trackMatchCompletionIfNeeded`, `isMatchFull`) |
| `delete_account` | `presentation/.../feature/mypage/AccountDeleteRoute.kt` (탈퇴 API 204 직후) |

테스트: `domain/src/test/.../analytics/AnalyticsTest.kt`(이름 고정·area·ageBand), `presentation/src/test/...` 의
`*AnalyticsTest`(녹음기 sink 를 꽂고 「성공했을 때만, 한 번만」 확인).

## 이벤트 계약

네이밍: 이벤트 `동작_대상` snake_case, 프로퍼티 키·값 상수 소문자 snake_case. 값 없는 프로퍼티는 **키를 뺀다**
(0·"" 로 지어내지 않는다). 값 타입은 string / int / bool 만.

### 핵심 퍼널

| 순서 | 이벤트 | 기록 시점 | 프로퍼티 |
|---|---|---|---|
| 1 | `complete_signup` | 가입 API 성공 **그리고** 자동 로그인까지 성공했을 때 | `gender`: `male`/`female`, `age_band`: `10s`/`20s`/…/`60s+` |
| 2 | `request_match` | 방 만들기 또는 합류가 서버에 **받아들여졌을 때** | `method`: `create`/`join`, `capacity`, `origin_area`, `destination_area`(시/구, create 만), `estimated_fare`(원) |
| 3 | `complete_match` | 매칭 대기 화면이 status 를 **MATCHED 이상으로 처음 봤을 때, 1회** | `capacity`, `member_count`, `waited_seconds`(대기 화면 진입 기준) |

`complete_match` 정원 판정은 **status 로만** 한다: `MATCHED` 충족, `RECRUITING`·`COMPLETED`·`CANCELED` 아님.
(예전에는 `DISPATCHING`·`ONGOING` 도 충족에 들어갔다. 둘은 기사·배차와 함께 지웠다 — **iOS 와 대시보드의 같은 판정도 함께 맞춰야 한다.**)
스냅샷·SSE·폴링 어느 길로 알게 되든 ViewModel 인스턴스당 한 번.

### 나머지

| 종류 | 이벤트 | 기록 시점 | 프로퍼티 |
|---|---|---|---|
| View | `view_screen` | 내비게이션 top 이 바뀔 때마다(초기 포함) | `screen`: 라우트 **패턴**(`home`, `matching/waiting`, `chat/room/{roomId}`) |
| Click | `click_create_party` | 「방 만들기」 요청 직전 | `capacity` |
| Click | `click_join_party` | 「합류하기」 요청 직전 | 없음 |
| Action | `complete_login` | 아이디 로그인 성공 | 없음 |
| Action | `fail_match_request` | 생성·합류 거절 | `method`, `reason` |
| Action | `cancel_match` | 「그만 찾기」 성공 | `capacity`, `member_count` |
| Action | `finish_party` | 동승 모드 「합승 완료」 성공 | `member_count` |
| Action | `delete_account` | 탈퇴 API 가 204 | 없음 |

**은퇴한 이름 — `finish_ride` · `trigger_emergency`.** 유일한 기록 지점이던 26 운행 중·27 긴급 신고를
택시 제거(2026-11-04)로 지워 보내는 곳이 없어졌다. 보드에 옛 데이터가 남아 있으니 **다른 뜻으로
되살려 쓰지 않는다** — 이름이 같고 뜻이 다른 두 시기가 한 퍼널에 섞인다.

`fail_match_request.reason` (`MatchFailureReason` 상수 — 서버 코드가 아니라 도메인 예외의 프로퍼티 판정을 옮긴 것, iOS `openFailureReason`/`joinFailureReason` 과 동일):

| method | reason | 판정 |
|---|---|---|
| create | `already_in_party` | `PartyOpenException.isAlreadyInAnotherParty` |
| create | `same_departure_destination` / `out_of_service_area` / `invalid_capacity` / `route_unavailable` | `isSameDepartureDestination` / `isOutOfServiceArea` / `isInvalidCapacity` / `isRouteUnavailable` |
| join | `party_full` / `party_closed` / `party_not_found` / `already_in_party` / `already_joined` / `network` | `PartyJoinException.isPartyFull` / `isPartyClosed` / `isPartyNotFound` / `isAlreadyInAnotherParty` / `isAlreadyJoined` / `isNetwork` |
| 공통 | `unknown` | 그 외 |

합류에서 「이 방에 이미 합류됨」(`PartyJoinException.isAlreadyInThisParty`)은 실패가 아니라 성공 경로다 — `fail_match_request` 없이 `request_match(join)` 을 세고 화면을 넘긴다(iOS `finishJoin` 과 동일. 서버에 내 자리가 있다는 뜻이라 퍼널 2단계로 집계).

### 공통 프로퍼티 · People

- `build`: `debug`/`release` — 리포터가 super property 로 등록. 운영/개발 프로젝트를 나누지 않는 대신 보고서에 Filter `build = release`.
- OS·앱 버전·기기 모델은 SDK 가 자동으로 붙인다. `platform` 은 따로 넣지 않는다(`$os` 로 가른다).
- `complete_signup` 때 People 에 `gender`·`age_band` 를 `set` 한다.

## 보내지 않는 것

실명·휴대폰·이메일·로그인 아이디·비밀번호·채팅 본문·좌표·장소명·번지·방 id·동승자 식별자.
- `origin_area`/`destination_area` 는 도로명 주소에서 **시/도 + 시/군/구**까지만(`administrativeArea`).
  `"서울 관악구 남부순환로 1820"` → `"서울 관악구"`, `"경기 안양시 만안구 성결대학로 53"` → `"경기 안양시 만안구"`, `"부산대로"` → 없음.
- 생년월일은 `age_band` 로 접는다(만 나이, 생일 당일부터 다음 나이. 20 미만 `10s`, 60 이상 `60s+`).
- 사용자 식별자(publicId)는 이벤트 프로퍼티가 아니라 **identify** 로만.

## Mixpanel 연동

| 항목 | 값 |
|---|---|
| SDK | `com.mixpanel.android:mixpanel-android` (`gradle/libs.versions.toml`) |
| 초기화 | `MixpanelAPI.getInstance(context, token, trackAutomaticEvents = false)` — 자동 이벤트 **끔** |
| 로깅 | Debug 빌드만 — 리포터가 `mixpanel.setEnableLogging(debuggable)` 로 켠다. logcat 태그 `MixpanelAPI.*` 에 큐잉·플러시가 찍힌다 |
| super property | `build` = `debug`/`release` |
| 토큰 없음 | SDK 를 켜지 않고 sink 도 안 꽂음 → `Analytics.track` 무동작 |

### 토큰 주입

- Mixpanel 프로젝트: **codingforest (project id 4080134)** — iOS 와 **같은 프로젝트**, 같은 토큰. 플랫폼은 SDK 가 자동으로 붙이는 `$os` 로 가른다
  (보고서 Breakdown `$os`). 나누더라도 플랫폼이 아니라 운영/개발 기준으로 나눈다(`build` super property 로 지금은 충분).
- 로컬: `local.properties`(gitignored) 에 `MIXPANEL_TOKEN=…` → `app/build.gradle.kts` `buildSetting` → `BuildConfig.MIXPANEL_TOKEN`.
  값은 iOS `frontend-ios/Config/Secrets.xcconfig` 의 `MIXPANEL_TOKEN` 과 같다(Settings → Project Settings → Project Token).
- CI: GitHub Secrets `MIXPANEL_TOKEN` 을 `.github/workflows/build.yml` 이 읽는다. **저장소 소유자가 `gh secret set MIXPANEL_TOKEN` 으로 등록해야 한다**(에이전트는 시크릿 쓰기가 막혀 있다). 비어 있어도 빌드는 되고 이벤트만 꺼진다.
- 저장소에 토큰을 커밋하지 않는다.

### 사용자 식별 (identify / reset)

화면 코드에는 식별 호출이 없다. 리포터가 `UserSession.authState` 하나를 구독한다.

| 세션 상태 | 동작 |
|---|---|
| 앱 시작 미로그인 | 아무것도 안 함(기기 익명 id). **reset 하지 않는다** |
| `Authenticated(userUuid)` | `identify(userUuid)` — 익명 id 와 자동 병합 |
| 로그인 상태에서 빠져나옴 | `reset()` — identify 된 적이 있을 때만 |
| `Unknown` | 무시 |

## 이벤트를 더할 때

1. 이 문서와 `frontend-ios/docs/ANALYTICS.md` 의 표에 먼저 적는다.
2. `AnalyticsEvent`(Android sealed class)와 iOS `AnalyticsEvent` 에 같은 케이스를 더하고, 이름 고정 테스트(`AnalyticsTest`)에 한 줄.
3. 행동이 **성공한 자리**에서 track. 탭 자체를 세고 싶으면 `click_*` 을 따로.
4. 프로퍼티에 사람을 특정할 값이 없는지 위 기준으로 본다.

## 남은 일

- Play Console 데이터 보안 양식에 Mixpanel 수집 항목(사용자 ID·기기 ID·앱 상호작용·대략적 위치·성별·연령대)을 적는다.
- ~~개인정보 처리방침에 Mixpanel(미국, Mixpanel Inc.) 국외 이전과 수집 항목 추가.~~ 2026-11-05 반영(`_workspace/legal-site/_changes.md`). SDK 가 자동으로 붙이는 `$carrier`·`$bluetooth_*`·`$has_nfc`·`$has_telephone`·`$manufacturer`·`$brand` 까지 적었으므로, SDK 를 올려 자동 항목이 바뀌면 처리방침도 같이 본다.
- 탈퇴 요청이 오면 서버뿐 아니라 Mixpanel 프로필도 지운다(Users → 프로필 → Delete). `delete_account` 이벤트가 그 사용자를 찾는 열쇠다.
