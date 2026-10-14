# 119 · qa-verifier — 백엔드 변경 연동 + 매칭방 SSE 전환 검증 리포트

대상: `feat/backend-sync-sse` @ `81d132c` (코드 무수정 · 검증만)
입력: `00_input_backend-sync-sse.md`, `114`, `115`, `116`, `117`, `118`
백엔드: 로컬 `localhost:8080` (origin/develop `af1e4cf`, H2 인메모리, `TAXI_ENABLED=false`, 고정 인증코드 `123456`)
스크린샷·로그: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/qa119/`

## 0. 검증 환경 — 에뮬레이터를 바꿨다 (중요)

지정된 `emulator-5554` 는 **다른 Claude 세션이 기사 앱(`com.moyeota.driver`)을 실시간으로 조작 중**이었다.
`com.moyeota` 를 포그라운드로 올려도 수 초 내에 기사 앱이 다시 올라왔고(20초 간격 UI 해시 변화로 확인),
UI 주도 QA 가 불가능했다. **5554 는 건드리지 않고**(앱 설치만 1회 수행, 종료·삭제 없음)
기존 AVD 목록에 있던 **`Pixel_6_QA` 를 `emulator-5560` 으로 콜드부트**해 전 과정을 그 기기에서 수행했다.

- APK: `MOYEOTA_BASE_URL=http://10.0.2.2:8080/ ./gradlew :app:assembleDebug` → `adb install -r -t`
- 계정은 전부 새로 만들었다: `qasse1~qasse4`(curl), `qasse13`(앱 가입 흐름)
- 입력: uiautomator dump → `input tap`. 영문은 `input text`(키이벤트는 IME 자동수정이 글자를 망가뜨렸다)

---

## 1. 요약

| 항목 | 결과 |
|---|---|
| A1 빌드·유닛테스트 | **통과** — `:data` 259 / `:presentation` 174, 실패·에러 0 |
| A2 SSE 포맷 교차 비교 | **통과** — 4종 전부 백엔드 소스와 일치 |
| A3 `/status`·`fingerprint` 제거 | **통과** — 호출 0건, 동작 코드에 지문 없음 |
| B4 **폴링 0회** | **부분 통과** — 21 자신은 0회, **17 탐색의 4초 폴링이 계속 돈다(D-1)** |
| B5 입장 반영 | **통과** — 상세 재조회 0회 |
| B6 퇴장 반영 | **통과** — 상세 재조회 0회 |
| B7 방 종료 | **통과** — 타인 finish(SSE closed) · 앱 「합승 완료」 둘 다 홈 복귀 |
| B8 **끊김·폴백·복구** | **통과** — readTimeout 45초 실측 확인, 복구 시 catch-up 조회 0회 |
| B9 중복 구독 없음 | **통과** — 21↔채팅 3왕복에 `/events` 추가 0건 |
| C10 **합류 교착 복구** | **통과** — 409 → 상세 확인 → 21 정상 진입 |
| D11 정상 가입 | **통과** — 서버에 계정 실제 생성 확인 |
| D12 쿨다운 | **통과** — 카운트다운 동작, 쿨다운 중 탭 시 요청 0건 |
| D13 3분 만료 | 아래 §6 참조 |
| D14 중복 번호 | **통과** — 입력칸 열리지 않음 + 안내·로그인 링크 |
| D15 가입 실패 후 재시도 | **통과** — 재인증 요구 없음 |
| D16 12 → 뒤로 | **통과** — 인증 상태·입력값 유지 |
| E17 회귀 | **통과** — 크래시 0건, 개발자 옵션 동작 |

**결론 두 줄**
1. **「폴링 0회」는 21 매칭 대기 화면 자체로는 완전히 달성됐다**(135초 체류 중 방 상세·`/status` 모두 0건).
   다만 백스택에 남은 **17 탐색 화면이 4초마다 목록을 계속 긁어** 사용자 관점의 "평상시 네트워크 0"은 아니다(D-1).
2. **끊김 후 복구는 설계대로 작동한다.** 소켓이 조용히 죽는 진짜 블랙홀에서도 **마지막 바이트로부터 정확히 45초**에
   `SocketTimeoutException` 이 올라와 폴백이 2.2초 뒤 시작됐고, 망이 돌아오면 2.7초 만에 재연결해
   **상세 재조회 0회로** 화면이 서버 상태와 일치했다.

---

## 2. A · 정적 검증

### A1 빌드 + 유닛 테스트 (`--rerun` 강제 재실행)

```
MOYEOTA_BASE_URL=http://10.0.2.2:8080/ ./gradlew :app:assembleDebug \
  :data:testDebugUnitTest :presentation:testDebugUnitTest --rerun --console=plain
→ BUILD SUCCESSFUL
```

| 모듈 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `:data` | **259** | 0 | 0 | 0 |
| `:presentation` | **174** | 0 | 0 | 0 |

이번 작업이 추가·개편한 클래스: `PartySseEventTest` 15, `PartyMappersTest` 33, `RemoteRideRepositoryTest` 15,
`AuthenticatedPathContractTest` 17, `AuthMappersTest` 30, `RemoteAuthRepositoryTest` 33,
`WaitingSseStateTest` 20, `JoinFailureMessageTest` 9, `PhoneVerifyStateTest` 39, `SignupDraftTest` 9,
`PartyDetailPollingTest` 8, `RideStartSignalTest` 5, `RideFinishSignalTest` 4.
보고서 114/118 이 적은 `:data 264/259` 와 숫자가 다른 건 두 보고서가 서로 다른 시점에 센 값이기 때문이고,
**현재 트리 기준 확정값은 259** 다.

### A2 SSE 이벤트 4종 — 생산자/소비자 동시 읽기

백엔드 `RedisPartyChangeNotifier` · `PartySseRegistry` · `PartySseChannel` · `MemberJoined`/`MemberLeft`/`PartyDetailResult`
와 프론트 `PartySseEvent.kt` · `PartyDtos.kt` 를 나란히 놓고 비교했다.

| 이벤트 | 백엔드 실제 발행 | 프론트 파싱 | 판정 |
|---|---|---|---|
| `connected` | `emitter.send(event().name("connected").data(snapshot, APPLICATION_JSON))` = `PartyDetailResult` 전체 | `PartySseEvent.Snapshot(PartyDetailResponse)` | **일치** |
| `member-joined` | `objectMapper.writeValueAsString(MemberJoined(MemberInfo member, String status))` | `MemberJoinedPayload(member, status)` | **일치** |
| `member-left` | `MemberLeft(UUID publicId, String status)` — publicId null 가능 | `MemberLeftPayload(publicId: String?, status)` | **일치** |
| `closed` | `String.valueOf(partyId)` — **JSON 아님** | `EVENT_CLOSED ->` data 를 아예 보지 않음 | **일치** |
| heartbeat | `@Scheduled(fixedRate=30_000)` `comment("ping")` | 주석은 okhttp-sse 가 삼킴 · readTimeout 45초로만 활용 | **일치** |

추가 확인
- **필드명 케이스**: 백엔드에 `PropertyNamingStrategy`/`SNAKE_CASE` 설정이 없다(`src/main/resources`·`src/main/java` 전수 grep 0건)
  → Jackson 기본 camelCase. 프론트 DTO 와 1:1. **snake/camel 불일치 없음.**
- **래핑 unwrap**: `PartySseMessage` 는 Redis 채널 전용 봉투이고 `PartySseChannel` 이 `m.data()` 를 그대로
  SSE 프레임에 싣는다 → 앱이 받는 건 내부 JSON 그대로. **이중 래핑 없음.**
- **nullability 비대칭**: `MemberInfo` 의 네 문자열 필드는 서버가 `MemberInfo(null,null,null,null,rideCount)` 를
  내려보낼 수 있고(`MemberInfo.of` 의 summary==null 분기) 프론트가 전부 nullable 로 받는다. **대칭.**
- `member-left` 의 `leftMemberId` 는 `PartySseChannel` 이 `closeMember()` 로만 쓰고 프레임에 싣지 않는다 →
  **나간 당사자의 스트림은 이벤트 없이 끊긴다**(114 §2-2 기술과 일치).
- 상태 전이 매핑도 양쪽이 맞다: 서버 `ACTIVE→RECRUITING`, `COMPLETED→MATCHED`, `FINISHED→COMPLETED`.
  21 의 `isWaitingForMembers` 는 RECRUITING·MATCHED 이고 `LaunchedEffect(status)` 의 `onPartyClosed` 는
  앱 COMPLETED(=서버 FINISHED)라 **정원 충족이 종료로 오인되지 않는다.**

### A3 삭제된 API · 지문

| 확인 | 결과 |
|---|---|
| 앱이 `/matching/rooms/{id}/status` 를 부르는가 | **0건.** 전 세션 logcat 전수 검색 0. 소스에도 호출 없음(주석·테스트 가드만) |
| `MatchingApi.getPartyStatus` 잔존 | 없음. `AuthenticatedPathContractTest.kt:151` 이 리플렉션으로 부활을 막는다 |
| `fingerprint` 를 읽는 동작 코드 | 없음. 남은 문자열은 전부 KDoc/주석(5곳)과 회귀 가드 테스트(7곳) |
| 실서버 `/status` | `curl → 404` (삭제 확인) |
| `changed` 이벤트 잔존 참조 | 없음 |
| Routes 상수 ↔ `MainNavGraph` 등록 | 미등록 navigate 대상 없음(`DESTINATION_CONFIRM` 은 `dialog()`, `chatRoom(id)` 는 패턴 등록) |

---

## 3. B · 매칭방 SSE (핵심)

시나리오: 방 1(생성자 `qasse2`, 정원 3, 부산대 좌표) → 앱(`qasse1`)이 17 탐색 → 20 → 합류 → 21 체류.

### B4 폴링 0회 — **실측 숫자**

측정 창 **04:27:32.513 ~ 04:29:47.535 = 135.0초**, 앱은 21 매칭 대기 화면에 계속 머묾.

| 요청 | 건수 | 판정 |
|---|---|---|
| `GET /api/v1/matching/rooms/1` (방 상세) | **0** | **통과** — 평상시 폴링 0회 달성 |
| `GET /api/v1/matching/rooms/1/events` (SSE) | **0** (추가 연결 없음) | **통과** — 화면 진입 시 1건으로 끝, 재연결 0 |
| `GET /api/v1/matching/rooms/1/status` | **0** | **통과** |
| `POST /api/v1/auth/reissue` | 2 | 정상 — 액세스 토큰 2분 만료. **SSE 는 끊기지 않았다** |
| `GET /api/v1/matching/rooms?swLat=…` (17 탐색 목록) | **34** | **실패 — D-1** |

화면 진입 시점 1회성 호출(폴링 아님): `POST /join` 1 + `GET /matching/rooms/1` **2** + `GET …/events` 1.
상세가 2건인 이유는 D-2 참조.

### B5 입장 반영 — 통과

curl 로 `qasse3` 합류(`{"status":"COMPLETED","currentMembers":3}`) → **4초 내** 앱이
`지금 같은 방향 3명(나 외 2명)` + 멤버 목록에 `qassethree` 추가 + 「합승 완료」 버튼 노출.
그 사이 발생한 요청은 **17 탐색 폴링 2건뿐, 방 상세 0건.** → 변경분만으로 그렸다.
스크린샷 `11_after_join.png`

### B6 퇴장 반영 — 통과

curl `DELETE /api/v1/matching/leave/1` (204) → **4초 내** `2명(나 외 1명)` 으로 감소, `qassethree` 제거,
버튼이 「그만 찾기」로 복귀. **방 상세 0건.** 스크린샷 `12_after_leave.png`

### B7 방 종료 — 통과(두 경로 모두)

1. **타인 finish → SSE `closed`**: 정원 3 충족 후 curl 로 `qasse2` 가 `POST /matching/rooms/1/finish`(204)
   → 앱이 **약 6초 내 홈(14)** 으로 이동. 확인용 상세 재조회 없음. 스크린샷 `20_after_close.png`
2. **앱의 「합승 완료」**: 방 2를 정원까지 채운 뒤 앱에서 「합승 완료」 → 확인 다이얼로그 → `POST …/finish`
   → 홈 복귀. 스크린샷 `28_finish_dialog.png`, `29_home_after_finish.png`

(부수 확인) 117 §8-7 의 우려 — **30분 만료 경로도 `closed` 를 발행한다**:
`PartyService.expire()` 가 `partyChangeNotifier.closed(partyId)` 를 호출한다(소스 확인).
다만 **정원을 못 채운 ACTIVE 방은 스윕 대상이 아니다**(`findCompletedBefore`) — 영구히 열려 있다.

### B8 끊김 · 폴백 · 복구 — **실측 숫자**

#### (가) 비행기 모드 — 소켓이 즉시 끊기는 경우

| 사건 | 시각(기기) | 기준점 대비 |
|---|---|---|
| 비행기 모드 ON | 04:30:54 | — |
| `PartyEvents: 연결 끊김 … Software caused connection abort` | 04:30:55.087 | **+1.1초** |
| 첫 폴백 상세 조회 `GET /matching/rooms/1` | 04:30:57.496 | 끊김 인지 **+2.4초** |
| 이후 폴백 주기 | 04:31:03.5 / 09.5 / 15.6 / 21.7 / 27.8 / 33.8 / 39.8 / 45.8 / 51.9 / 57.9 / 04:32:03.9 / 09.9 / 16.0 / 22.6 / 28.6 / 34.7 | **6.0~6.6초 간격 · 107초 동안 17회** |
| SSE 재연결 시도 | 약 3초 간격(총 ~33회) | `RECONNECT_DELAY_MS=3000` 그대로, **무한 고속 루프 아님** |
| 끊긴 동안 화면 | `2명` 유지, **에러 화면으로 덮이지 않음** | 설계대로 |
| 끊긴 동안 서버 변경 | curl 로 `qasse3` 합류(3/3, COMPLETED) | — |
| 비행기 모드 OFF | 04:32:42 | — |
| `PartyEvents: 연결됨` | 04:32:44.740 | **+2.7초** |
| 재연결 이후 상세 조회(catch-up) | **0건** | **통과** — 스냅샷이 전부 보정 |
| 복구 후 화면 | `3명(나 외 2명)` + `qassethree` + 「합승 완료」 = 서버와 일치 | **통과** |

#### (나) 진짜 블랙홀 — **readTimeout 45초 실증** (이게 핵심)

비행기 모드는 소켓을 즉시 파괴해 `readTimeout` 경로를 타지 않는다. 그래서 `adb root` + iptables 로
**양방향 패킷을 조용히 버려**(연결은 열린 채, RST 없음) 「조용히 죽은 연결」을 만들었다.

```
adb shell iptables -I INPUT  1 -s 10.0.2.2 -p tcp --sport 8080 -j DROP
adb shell iptables -I OUTPUT 1 -d 10.0.2.2 -p tcp --dport 8080 -j DROP
```

| 사건 | 시각 | 해석 |
|---|---|---|
| 패킷 차단 시작 | 04:36:40 | 직전 `:ping` 은 최대 30초 전에 도착 |
| `PartyEvents: 연결 끊김 … timeout`(`SocketTimeoutException`) | **04:36:56.526** | 차단 후 16.5초 = **마지막 바이트로부터 45초** → `SSE_READ_TIMEOUT_SECONDS=45` 가 **그대로 동작** |
| 첫 폴백 상세 조회 | 04:36:58.753 | 끊김 인지 **+2.2초** |
| 2번째 폴백 | 04:37:24.803 | 간격 26초 — 요청 자체가 20초 readTimeout 을 소진한 뒤 실패하므로 `6+20`. 설계상 예상되는 값 |
| SSE 재연결 시도 | 13초 간격 | 10초 connectTimeout + 3초 delay. 역시 폭주 없음 |
| 차단 중 화면 | `3명` 유지, 에러 아님 | 설계대로 |
| 차단 해제 후 `연결됨` | 04:38:32.547 | 재연결 성공 |
| 재연결 이후 catch-up 상세 | **0건** | **통과** |

> **판정: readTimeout 45초는 실제로 동작한다.** heartbeat 가 30초이므로 조용히 죽은 연결은
> **최악 45초 · 최선 약 15초** 안에 감지되고, 폴백은 그 2~3초 뒤 시작된다.
> 지시서의 기대("끊긴 뒤 45~60초 안에 폴백 시작")를 충족한다.

### B9 중복 구독 없음 — 통과

21 ↔ 채팅방을 **3회 왕복**(「채팅 열기」 → 뒤로).

| 요청 | 건수 |
|---|---|
| `GET /matching/rooms/1/events` | **0** (기존 연결 유지) |
| `GET /matching/rooms/1` | **0** |
| `PartyEvents` 로그(연결됨/끊김) | **0줄** |
| 채팅 쪽 요청 | `chat-rooms/1` 3, `.../messages` 6, `.../users` 9, `ws-chat` 3 (= 왕복 3회 정상) |

`MatchWaitingViewModel` 이 백스택 엔트리에 묶여 살아 있으므로 재구독이 없다.
「연결됨 → 끊김」이 3초 주기로 번갈아 반복되는 무한 루프는 **전 세션 어디에도 없었다.**

---

## 4. C · 합류 교착 복구 (C10) — 통과 ★ 이번 수정의 핵심

재현 절차
1. 앱(`qasse1`)이 방 1을 나가 홈으로 (서버 1/3)
2. **curl 로 `qasse1` 을 방 1에 합류**(200, 2/3) — 앱은 이 사실을 모른다
3. 같은 토큰으로 한 번 더 join → `409 {"code":"ALREADY_JOINED_OTHER_PARTY"}` (교착 재현 확인)
4. 앱: 동승 탭 → 방 카드 「합류」 → 20 → 「이 탑승에 합류하기」

관측된 요청 순서
```
--> POST /api/v1/matching/rooms/1/join      <-- 409
--> GET  /api/v1/matching/rooms/1           <-- 200   ← recoverMembership: 내 publicId 가 members 에 있는지 확인
--> GET  /api/v1/matching/rooms/1 (+ events)          ← 21 진입
PartyEvents: 연결됨 partyId=1
```
결과: **「합류하지 못했어요」 없이 21 대기 화면 정상 진입**(`2명(나 외 1명)`, `qassetwo`·`qasseone`).
스크린샷 `19_deadlock_recovered.png`

부수 확인 — 백엔드 D-B 재현: `PartyService.join` 이 `validateNotInOngoingParty()` 를 `party.join()` **보다 먼저**
호출하므로 같은 방 재합류도 `ALREADY_JOINED_OTHER_PARTY` 로 온다. 114 §3 D-B 가 **실서버에서 사실로 확인됐다.**

---

## 5. D · 가입 문자 인증

진입: 04 로그인 → 「가입하기」 → 10 프로필(1/2) → 12 매너 서약(2/2)

### D11 정상 가입 — 통과
번호 `010-1111-0013` → 「인증번호 받기」 → `123456` 입력 시 **버튼 없이 자동 확인** → 「휴대폰 인증 완료」 + 「번호 변경」 + 번호 입력란 잠김.
가입 제출 본문(logcat BODY):
```json
{"loginId":"qasse13","password":"Passw0rd!","nickname":"qanick11","name":"QaTest",
 "birthDate":"1999-05-05T00:00:00Z","phoneNumber":"01011110013","gender":"MALE",
 "email":"qa13@example.com","verificationToken":"f293db5a-6a33-438b-981b-a2931b6c8594"}
```
→ `201` → 자동 로그인 `200` → 「가입이 끝났어요」.
**서버 실제 확인**: `POST /auth/login {qasse13}` → 200, `GET /local/users/info` → `{"uuid":"01a131af-…","name":"qanick11"}`.
(115 §11 ① 교차 확인 — send·verify·register **세 요청 모두 하이픈 없는 `01011110013`**)

### D12 쿨다운 — 통과
발송 직후 「**56**초 후 다시 받기」 + 「남은 시간 2:56」. 8초 뒤 「36초 후…」, 이후 「19초 후…」로 정상 감소.
**쿨다운 중 버튼 탭 → `auth/sms/send` 요청 0건**(비활성 실동작 확인). 스크린샷 `35_code_sent.png`

### D14 중복 번호 — 통과
이미 가입된 `010-1111-0001` 로 「인증번호 받기」 →
**인증번호 입력칸이 열리지 않고** 번호 입력란 아래에 「이미 가입된 번호예요. 로그인해서 이어가 주세요」 +
「로그인하러 가기」 링크. 스크린샷 `34_dup_phone.png`

### D15 가입 실패 후 재시도 — 통과 ★
아이디를 기존 계정(`qasse1`)으로 두고 제출 → `409` → 12 화면에 「이미 사용 중인 아이디예요」.
뒤로 → 10 (인증 상태 유지) → **아이디만** `qasse13` 으로 수정 → 다시 제출.
재시도 구간 요청: `POST /auth/register → 201`, `POST /auth/login → 200`.
**`auth/sms/send` · `auth/sms/verify` 요청 0건 — 재인증을 요구하지 않았고 같은 `verificationToken` 을 재사용했다.**

> 참고: **닉네임 중복은 12 까지 가지 못한다.** 10 에서 클라이언트 중복 검사가 「이미 사용 중인 닉네임이에요」로
> 미리 막는다. 116 §9 #9 가 적은 "닉네임 중복 → 12 → 409" 는 검사와 제출 사이 경합에서만 발생하므로,
> 같은 성질의 경로를 **아이디 중복(409 USER101)** 으로 대체 검증했다.

### D16 12 → 뒤로 — 통과
인증 완료 → 「다음」 → 12 매너 서약 → **뒤로** → 10 복귀 시
「휴대폰 인증 완료」·「번호 변경」이 그대로 남아 있고 번호·아이디·이메일 등 입력값도 유지됐다.
(116 §7 의 "정상 뒤로가기에서 인증이 날아가는 함정"은 재현되지 않았다.) 스크린샷 `44_back_preserved.png`

### D13 3분 만료
§6 참조.

---

## 6. 결함 목록 (심각도순)

### D-1 · 중 — 17 탐색 화면의 4초 폴링이 21 대기 화면에서도 계속 돈다

- **증상**: 21 에 머무는 135초 동안 `GET /api/v1/matching/rooms?swLat=…` 가 **34회**(약 4초 간격) 발생.
  21·20·채팅 등 탐색 위에 쌓인 **모든 화면**에서 동일.
- **파일**: `presentation/src/main/kotlin/com/moyeota/presentation/feature/explore/ExploreRoute.kt:39`
  (`NEARBY_POLL_INTERVAL_MS = 4_000L`), 루프는 같은 파일 `:85~97` `observeRequests()`
- **원인**: `ExploreViewModel` 이 탐색 NavBackStackEntry 에 묶여 있고 `viewModelScope` 는 lifecycle 을 보지 않는다.
  탐색이 백스택에 남아 있는 한(=앞으로 이동했을 뿐 pop 되지 않은 한) `while (isActive) { delay(4s); query() }` 가 계속 돈다.
- **재현**: 동승 탭 → 시트 스와이프업 → 카드 「합류」 → 21 진입 → logcat 2분 관찰.
- **영향**: 이번 작업의 목표 문장(「21 매칭 대기: 평상시 폴링 0회」)은 21 자신에 대해서는 지켜졌지만,
  **사용자 단말이 내보내는 실제 요청은 0이 아니다.** 지문·`/status` 제거로 아낀 양을 이 루프가 상쇄한다.
- **이번 커밋의 회귀는 아니다**: `900e828`(직전 커밋)에도 동일한 상수·루프가 있다. 다만 목표와 충돌하므로 보고한다.
- **수정 제안(compose-builder)**: 폴링을 화면 가시성에 묶는다.
  `ExploreRoute` 에서 `LifecycleResumeEffect`/`repeatOnLifecycle(Lifecycle.State.RESUMED)` 로 수집하거나,
  ViewModel 에 `onVisible()/onHidden()` 을 두고 `observeRequests()` 의 while 루프를 그 신호로 게이트.
  (21 이 `realtimeAlive` 로 폴백을 켜고 끄는 것과 같은 모양.)

### D-2 · 하 — 21 진입 때 방 상세를 2회(합류 경로는 3회) 읽는다

- **증상**: 21 진입 시 `GET /matching/rooms/{id}` 가 **2건** 연속(04:27:09.201 / .226). 합류 교착 복구 경로에서는 3건.
- **파일**:
  - `presentation/.../feature/matching/MatchWaitingRoute.kt:224` — `refresh()` → `readDetail()` (1회차)
  - `presentation/.../core/MainNavGraph.kt:554` — `activePartyViewModel.rememberParty(joinedPartyId.toString())`
    → `presentation/.../core/ActiveParty.kt:144~149` `rememberParty()` → `reload()` → `resolve()`
    → `data/.../RemoteActivePartyRepository.kt:103` `rideRepository.getPartyDetail(id)` (2회차)
- **영향**: 기능 영향 없음(둘 다 1회성). 다만 상세는 서버 쿼리 3개 + 경로 폴리라인이라 진입마다 1왕복이 버려진다.
- **수정 제안(api-integrator/compose-builder)**: `rememberParty(partyId)` 가 방금 합류/생성해 id 를 아는 경우
  `reload()` 를 생략하거나, `resolve()` 결과를 21 의 초기 상태로 넘겨 `refresh()` 의 첫 조회를 건너뛴다.

### D-3 · 하 — 방을 떠난 뒤 `GET /api/v1/matching/rooms/0` 호출(404)

- **증상**: 「합승 완료」·방 종료 직후 `GET /api/v1/matching/rooms/0` 이 1~2회 발생하고 **404**.
- **파일**: `data/src/main/kotlin/com/moyeota/data/repository/RemoteActivePartyRepository.kt`
  `findFromChatRooms()`(≈:85~92)가 채팅방의 `partyId` 를 그대로 조회하고,
  `verify()`(≈:99~101)는 `toLongOrNull()` 만 보고 **0 이나 음수를 거르지 않는다**.
- **영향**: 404 를 "확실한 아니오"로 처리하므로 기능 영향은 없다. 불필요한 왕복 + 서버 에러 로그 오염.
- **수정 제안**: `verify()` 에서 `val id = partyId.toLongOrNull()?.takeIf { it > 0 } ?: return Verdict.Inactive`.

### D-4 · 하(문서) — `USER207` 의 백엔드 enum 이름 불일치

- 보고서 `116` §4 와 `115` 가 `PHONE_NOT_VERIFIED` 로 적었으나 백엔드 실제 enum 은
  `UserErrorCode.SMS_NOT_VERIFIED(BAD_REQUEST, "USER207", "휴대폰 인증이 필요합니다.")` 다.
- **동작 영향 없음** — 앱은 `AuthMappers` 에서 **코드 문자열 `USER207`** 로 분기한다.
- 문서/상수 이름만 서버와 맞춰 두면 다음 사람이 enum 이름으로 grep 할 때 헤매지 않는다.

### D-5 · 하(백엔드 재송부) — 이미 보고된 2건을 실서버에서 확인

1. **D-B(114 §3) 확정**: 같은 방 재합류도 `ALREADY_JOINED_OTHER_PARTY` 로 온다(§4 에서 curl 로 직접 재현).
   `existsOngoingByMemberId` 가 합류 대상 방을 제외하지 않는다. 앱은 상세 조회로 우회 중이지만,
   **상세 조회까지 실패하면 "이 방"이 "다른 방"으로 접힌다**(117 §8-5).
   요청: `existsOngoing` 에서 `partyId` 제외 또는 재합류 멱등 200.
2. **USER204 과적재(115 §10-2)**: "발송 안 함 / 3분 만료 / 이미 인증 성공"이 전부 `SMS_CODE_EXPIRED`.
   앱이 구분할 수 없다.

---

## 7. 미검증 · 제한

| 항목 | 사유 |
|---|---|
| **D13 3분 만료** | 측정 시도했다. 발송 직후 「남은 시간 2:56」 카운트다운이 정상 동작하는 것까지 확인했고, TTL(3분) 경과 후의 「만료됐어요」 전환은 리포트 작성 시점에 대기 중이었다 → **부분 검증(타이머 동작 확인 / 만료 전환 미확인)**. 서버 TTL 은 `SmsVerificationService.CODE_TTL = 3분` 으로 소스 확인됨 |
| 25 배차 상태 · 26 운행 중 (118 §8 요청분) | 서버가 `TAXI_ENABLED=false`(동승 모드)라 두 화면에 **도달 자체가 불가능**하다. 7초/8초 폴링·전이 감지·`shouldPublishDetail` 은 전부 미검증. 택시 모드 서버 + 기사측 API 가 필요하다 |
| `:ping` 30초 heartbeat 직접 관측 | okhttp-sse 가 주석 프레임을 삼켜 앱 로그에 남지 않는다. **readTimeout 45초가 정확히 발화한 사실로 간접 확인**(§3 B8-나) |
| 116 §9 의 나머지 항목(#4 앱 재시작 쿨다운, #5 만료, #6 불일치 5회, #8 번호 변경, #10 토큰 10분 만료, #13 생년월일 오늘) | 지시서가 필수로 지정한 6개(11·12·13·14·15·16) 밖이라 수행하지 않았다 |
| `emulator-5554` 상의 동작 | 다른 세션이 점유 중이라 검증하지 않았다. 모든 결과는 `emulator-5560`(Pixel_6_QA, Android 17, 1080x2400) 기준 |

---

## 8. E · 회귀 (E17)

| 경로 | 결과 |
|---|---|
| 04 로그인(`qasse1`) → 14 홈 | 통과 — `qasseone님, 반가워요` |
| 14 → 17 동승 탐색 (지도·시트·카드) | 통과 — `주변 동승 1개 모집 중`, 카드에 `1/3명` |
| 17 → 20 합류 확인 → 21 | 통과 (§3) |
| 14 → 15 목적지 검색 → 16 조건 → **방 만들기** → 21 | 통과 — `POST /matching/rooms` → 방 2 생성, SSE 연결. 스크린샷 `27_created_waiting.png` |
| 21 → 채팅방 열기 | 통과 — `ws-chat` 연결, 메시지 목록 로드 |
| 35 마이페이지 개발자 옵션(서버 값 / 동승 강제 / 택시 강제) | **통과** — 칩 선택 상태가 바뀐다. 스크린샷 `22a_force_taxi.png` |
| 로그아웃 → 04 | 통과 |
| 가입(10 → 12 → 완료) | 통과 (§5) |
| **크래시** | **0건** — `logcat -b crash` 및 main 버퍼의 `FATAL EXCEPTION` / `ANR in com.moyeota` 모두 **0** |

---

## 9. 담당자별 조치 요청

| 대상 | 내용 |
|---|---|
| **compose-builder** | **D-1**(최우선) — `ExploreRoute.kt` 의 4초 폴링을 화면 가시성에 묶을 것. D-2 의 21 진입 2회 조회도 presentation 쪽 배선 |
| **api-integrator** | **D-3** — `RemoteActivePartyRepository.verify()` 에서 `id <= 0` 거르기. D-2 의 `ActiveParty.rememberParty` → `reload()` 중복 |
| **문서** | **D-4** — `115`/`116` 의 `PHONE_NOT_VERIFIED` → 백엔드 enum 은 `SMS_NOT_VERIFIED`(코드 `USER207`) |
| **백엔드** | **D-5** 2건 재송부 + 118 §7 의 D-C(배차·운행 전이 SSE 부재)는 이번 검증에서 **확인 불가**(동승 모드) — 택시 모드 서버가 준비되면 재검증 필요 |
