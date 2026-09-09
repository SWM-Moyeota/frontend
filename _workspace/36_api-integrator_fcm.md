# 36 · api-integrator — FCM 푸시 연동 (승객 앱)

**결과: E2E 성공. 에뮬레이터에 실제 푸시 알림이 표시됐다.**
증거 스크린샷 `_workspace/screenshots/fcm_01_driver_arrived.png`.

---

## 1. 연동한 엔드포인트

| 메서드 | 경로 | 응답 | 인증 | 실서버 검증 |
|---|---|---|---|---|
| PUT | `/api/v1/users/me/fcm-token` | 204 No Content | Bearer 필수 | ✅ 실측 204 |
| DELETE | `/api/v1/users/me/fcm-token` | 204 No Content | Bearer 필수 | ✅ 실측 204 |

기준: `user/interfaces/UserController.java` + 실서버(localhost:8080) curl/앱 실측. **문서-실제 불일치 없음.**

### 요청/응답 shape

```
PUT /api/v1/users/me/fcm-token
Authorization: Bearer <access>
{"token":"<FCM 기기 토큰>"}
→ 204 (본문 없음)

DELETE /api/v1/users/me/fcm-token
Authorization: Bearer <access>
→ 204 (본문 없음)
```

- 본문 키는 `token` 하나. 백엔드 `RegisterFcmTokenRequest(@NotBlank String token)` 와 1:1.
- **대상 사용자는 본문에 싣지 않는다** — `@CurrentUser` 가 Bearer 에서 뽑는다.
- 프리픽스 주의: 같은 `UserApi` 안에서 내 정보 조회는 `/api/v1/local/...`, 푸시 토큰은 `/api/v1/users/...` 다.
  백엔드 컨트롤러가 `LocalUserController` / `UserController` 로 나뉘어 생긴 비대칭이고, 한쪽에 맞춰
  통일하면 404 가 아니라 **401** 이 나서 세션 만료로 오인하기 쉽다.
  `AuthenticatedPathContractTest` 가 경로와 HTTP 동사(PUT/DELETE)를 못 박는다.

### 수신 메시지 shape (서버 → 앱)

`dispatch/infrastructure/FcmPassengerNotifier.notifyDriverArrived(partyId)` 가 파티 전원에게 멀티캐스트:

```json
{ "type": "DRIVER_ARRIVED", "partyId": "2" }
```

- **데이터 전용 메시지다** (`putData` 만, `setNotification` 없음). 시스템이 알림을 대신 그려 주지
  않으므로 앱이 `onMessageReceived` 에서 직접 표시해야 한다 — 여기서 안 그리면 알림은 어디에도 안 뜬다.
- `partyId` 는 **문자열**(`String.valueOf(partyId)`)이다.
- 표시 문구는 서버가 주지 않는다. 전적으로 앱 `strings.xml` 소유:
  - 제목 `기사님이 도착했어요`
  - 본문 `탑승 장소에서 기사님이 기다리고 계세요.`

---

## 2. 변경 파일

### data / domain
- `data/remote/dto/UserDtos.kt` — `FcmTokenRequest(token)` 추가
- `data/remote/UserApi.kt` — `registerFcmToken(@Body)`, `deleteFcmToken()` 추가
- `data/push/FcmTokenRegistrar.kt` **(신규)** — 토큰 수명주기 단일 창구
- `data/repository/RemoteAuthRepository.kt` — 생성자에 `FcmTokenRegistrar` 추가, 로그인/로그아웃에 등록/해제 삽입

**도메인 인터페이스를 만들지 않았다.** presentation 은 푸시 토큰을 전혀 보지 않는다 —
등록/해제가 걸리는 지점이 앱 시작·토큰 회전(app)과 로그인/로그아웃(data)뿐이라 화면 계약에 올릴 이유가 없다.
`RideRepository` 등 기존 도메인 시그니처 **변경 없음 → compose-builder 영향 없음.**

### app
- `AppContainer.kt` — `fcmTokenRegistrar` 생성·공개, `RemoteAuthRepository` 에 주입
- `MoyeotaApplication.kt` — 앱 수명 코루틴 스코프 + `onFcmTokenAvailable(token)` 단일 진입점
- `MoyeotaFirebaseMessagingService.kt` — `DRIVER_ARRIVED` 분기 + 토큰을 Application 으로 전달
- `res/values/strings.xml` — 알림 문구 2건
- `app/build.gradle.kts` — `kotlinx-coroutines-android` 추가

### 빌드 (요구사항 1 — **이미 되어 있었다**)
google-services 플러그인(루트 `apply false` + app), 버전 카탈로그 `googleServices`/`firebaseBom`,
Firebase BOM + `firebase-messaging`, 매니페스트 서비스 등록, 알림 채널(IMPORTANCE_HIGH) 전부 기존 상태로 존재.
이번 작업에서 손댄 건 코루틴 의존성 하나뿐이다.

### 테스트
- `data/src/test/.../push/FcmTokenRegistrarTest.kt` **(신규, 12개)**
- `data/src/test/.../remote/AuthenticatedPathContractTest.kt` — 경로 2건 + 동사 검증, `path()` 헬퍼에 PUT 지원
- `data/src/test/.../repository/RemoteAuthRepositoryTest.kt` — 로그인/로그아웃 연동 5건 추가

`./gradlew :data:testDebugUnitTest :app:assembleDebug` **통과.**

---

## 3. 토큰 수명주기 설계

```
앱 시작 → FirebaseMessaging.getToken()  ─┐
onNewToken / onRegistered (토큰 회전)   ─┼→ MoyeotaApplication.onFcmTokenAvailable()
                                         │    → appScope.launch { registrar.onTokenAvailable() }
                                         │        로그인 상태? → PUT
                                         └        미로그인?   → 보류(pendingToken)

로그인 성공 (RemoteAuthRepository.login, 세션 연 직후) → registrar.registerPendingToken() → PUT
로그아웃  (RemoteAuthRepository.logout, 세션 비우기 전) → registrar.unregister()        → DELETE
```

### 설계상 중요한 세 가지

**① 순서 — 해제는 세션을 비우기 전에.**
`logout()` 은 원래 로컬 세션을 먼저 비운다. DELETE 를 그 뒤에 두면 Bearer 가 사라져 401 이 나고,
서버에는 죽은 토큰이 남아 **다음 사용자가 이 기기로 로그인할 때까지 남의 도착 알림이 이 기기로 온다.**
그래서 호출을 화면/앱 계층에 두지 않고 세션을 여닫는 `RemoteAuthRepository` 가 직접 끼워 넣는다.
`RemoteAuthRepositoryTest.로그아웃은 세션을 비우기 전에 푸시 토큰을 해제한다` 가 해제 시점의
액세스 토큰을 붙잡아 순서를 못 박는다.

**② `AuthState.Unknown` 을 미로그인으로 단정하지 않는다.**
앱 시작 직후 세션은 디스크 복원이 끝날 때까지 항상 `Unknown` 이다. 이걸 미로그인으로 보면
**이미 로그인한 사용자의 앱 시작 등록이 매번 보류로 새어 나가**, 재로그인 전까지 알림을 못 받는다.
`authState.first { it !is Unknown }` 로 확정될 때까지 기다렸다 판단한다.

**③ 실패는 전부 삼킨다 (`CancellationException` 제외).**
푸시 등록 실패는 치명적이지 않다 — 잃는 건 도착 알림 하나고, 위로 던지면 **로그인이 실패한 것처럼 보인다.**
재시도는 다음 자연 발생 시점(앱 재시작·토큰 회전·재로그인)에 맡긴다. 별도 재시도 루프 없음.

**보류 토큰은 메모리에만 둔다.** 프로세스가 죽으면 사라지지만 복구 경로가 이미 있다 —
`MoyeotaApplication` 이 앱 시작마다 `getToken()` 으로 토큰을 다시 흘려보낸다.
디스크에 또 하나의 진실을 만들어 세션과 어긋나게 두는 것보다 안전하다.

### 로그아웃 타임아웃 (설계 판단)
로그아웃 경로에 네트워크 호출이 하나 늘어난다. 서버가 죽어 있으면 OkHttp 기본 타임아웃
(연결+읽기 최대 20초)만큼 사용자가 버튼을 누른 채 기다리게 되어, 기존 "로그아웃은 절대 실패/지연되지 않는다"
계약이 실질적으로 깨진다. `withTimeoutOrNull(2_000ms)` 로 상한을 걸고 초과하면 포기한다.

---

## 4. E2E 검증 (에뮬레이터 emulator-5554 / Pixel_6, 실서버 localhost:8080)

**사전 상태:** 백엔드·에뮬레이터 모두 **꺼져 있었다.** 지시문에는 "localhost:8080 실행 중"이라 되어
있었으나 실제로는 미기동이라 직접 기동했다(`./gradlew bootRun`, Redis 는 docker 로 이미 6379 LISTEN).
백엔드는 H2 인메모리라 데이터가 비어 있어 계정을 재시드했다.

**시드:** `testuser1`(승객, userId=1) / `testdriver1`(기사, userId=2, driverId=1), 비밀번호 `Passw0rd!`.
자기 방에는 콜을 받을 수 없어(`SELF_DISPATCH_NOT_ALLOWED`) 승객·기사 계정이 반드시 달라야 한다.

### 결과 타임라인 (실측 로그)

| 시각 | 단계 | 증거 |
|---|---|---|
| 23:10:07 | 앱 시작, FCM 토큰 확보 (미로그인 → 보류) | `D MoyeotaFcm: FCM 토큰 확인, 서버 등록 시도`, PUT 없음 |
| 23:11:06 | 로그인 성공 → 보류분 등록 | `--> PUT .../fcm-token` → `<-- 204` |
| 23:11:06 | 서버 반영 | `UserFcmTokenService: 승객 FCM 토큰 등록 userId=1` |
| 23:12:12 | 방 시드(capacity 1) → 매칭 시작 → 콜 발송 | `기사 호출 partyId=2, radius=1000m, 신규=1명` |
| 23:12:25 | 기사 accept → **arrive** | 둘 다 204 |
| 23:12:25 | **FCM 발송 성공** | `[기사 도착 알림] 전송완료 partyId=2, 성공=1, 실패=0` |
| 23:12:2x | **에뮬레이터에 알림 표시** (앱은 백그라운드) | 아래 참조 |
| 23:13:43 | 로그아웃 → DELETE **먼저**, 그다음 logout | `--> DELETE fcm-token`(43.746) → `--> POST auth/logout`(44.616) |
| 23:13:43 | 서버 반영 | `승객 FCM 토큰 삭제 userId=1` |
| 23:14:20 | 재로그인 → 보류 토큰 재등록 | `PUT → 204`, `승객 FCM 토큰 등록 userId=1` |

**알림 실표시 확인 (`dumpsys notification`):**
```
pkg=com.moyeota id=50 importance=4 channel=moyeota_default flags=AUTO_CANCEL
  android.title=String (기사님이 도착했어요)
  android.text=String (탑승 장소에서 기사님이 기다리고 계세요.)
```
`id=50` 은 `"2".hashCode()` — 파티 단위 알림 id 가 의도대로 동작했다.
알림 탭 → `mCurrentFocus=com.moyeota/com.moyeota.app.MainActivity` 로 앱이 열리는 것까지 확인.

### 스크린샷
- `screenshots/fcm_01_driver_arrived.png` — **알림 표시 (핵심 증거)**
- `screenshots/fcm_02_tap_opens_app.png` — 탭 → 앱 진입
- `screenshots/fcm_03_logout.png` — 로그아웃 확인 다이얼로그
- `screenshots/fcm_04_logged_out.png` — 정리 완료(로그인 화면)

### Firebase 프로젝트 일치 (지시문 확인 요청 항목)
- `app/google-services.json`: `project_id = moyeota-b1654`, package `com.moyeota`
- 백엔드 `secrets/moyeota-service-account.json`: `project_id = moyeota-b1654`
- **실발송으로 검증됨** — `성공=1, 실패=0`. 프로젝트 불일치 없음.

---

## 5. 특이사항 · 후속 작업

### 이번 범위 밖 (명시적으로 남긴 것)
1. **partyId 딥링크.** 알림 인텐트에 `MoyeotaFirebaseMessagingService.EXTRA_PARTY_ID` 로 실어만 뒀고
   `MainActivity` 는 아직 읽지 않는다 — 탭하면 앱만 열린다. 해당 파티 상세/운행 화면으로 보내려면
   MainActivity 가 `intent.getStringExtra(EXTRA_PARTY_ID)` 를 읽어 네비게이션해야 한다.
   PendingIntent requestCode 를 알림 id 와 맞춰 뒀으므로(0 고정 아님) 딥링크를 붙여도
   `FLAG_UPDATE_CURRENT` 로 앞선 extra 가 덮이지 않는다.
2. **데이터 전용 메시지 우선순위.** 서버가 `setPriority(HIGH)` / `AndroidConfig` 를 지정하지 않아
   기본 normal priority 다. Doze 상태의 실기기에서는 전달이 지연될 수 있다(에뮬레이터 실측은 즉시).
   **백엔드 요청 후보:** `AndroidConfig.builder().setPriority(HIGH)`.
3. **알림 채널 세분화.** 현재 `moyeota_default` 하나(IMPORTANCE_HIGH)를 공유한다. 도착/채팅/공지를
   나누려면 채널 분리가 필요하다.

### 발견한 백엔드 사항
- **`http/matching.http` 가 낡았다** (2군데): 경로가 `/api/matching/rooms`(실제 `/api/v1/matching/rooms`),
  본문에 `hostId`(실제 `creatorId`, 그마저 토큰이 이겨서 무시됨). 이번엔 컨트롤러 소스를 기준으로 삼았다.
- **매칭 시작 트리거는 "정원 충족" 하나뿐이다.** `MatchingStartedEvent` 발행 지점은
  `PartyApplicationService.open()` / `join()` 의 `isFull()` 분기 둘뿐 — 별도 "매칭 시작" 엔드포인트가 없다.
  그래서 E2E 는 `capacity:1` 방으로 즉시 매칭을 태웠다.
- **기사 FCM 은 아직 미등록 상태다.** arrive 직전 로그에
  `FcmCallNotifier: FCM 토큰이 등록된 기사가 없음 partyId=2, 후보=1명` 이 남는다.
  기사 앱은 이번 범위가 아니고 승객 도착 알림에는 영향 없다. 기사 앱 연동 시
  `PUT /api/v1/drivers/fcm-token` 이 대응 엔드포인트다.

### qa-verifier 교차 검증 요청
- 경계면: `PUT/DELETE /api/v1/users/me/fcm-token` ↔ 로그인/로그아웃 화면 (마이 탭 → 로그아웃)
- 특히 확인 요청: **오프라인 로그아웃**(비행기 모드)에서 로그아웃이 2초 안에 끝나고 로그인 화면으로
  빠지는지 — `withTimeoutOrNull` 상한이 실제로 먹히는지는 실기 확인이 필요하다.

---

## 6. Repository 시그니처 (compose-builder 공유용)

**변경 없음.** `AuthRepository` 를 포함해 domain 의 어떤 인터페이스도 시그니처가 바뀌지 않았다.
`RemoteAuthRepository` 의 **생성자**에만 인자가 하나 늘었고(app 모듈 `AppContainer` 에서 주입 완료),
`login()` / `logout()` 의 계약·동작은 화면 관점에서 동일하다. **presentation 쪽 작업 불필요.**
