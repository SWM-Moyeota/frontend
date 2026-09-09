# 48 · qa-verifier — 채팅 참여자 목록 API 연동 검증

작성 2026-09-08. 입력: `_workspace/46_input_chat_members.md`, `47_api-integrator_chat_members.md`. 비교 기준: `45_qa-verifier_chat_auth.md`.
환경: 백엔드 **localhost:8080(리더 패치본, 사용자 기동분 — 건드리지 않음)**, 에뮬레이터 **Pixel_6(emulator-5554, `-no-snapshot-load` 콜드부트)**, 앱 기본 `BASE_URL=http://10.0.2.2:8080/`, `pm clear com.moyeota` 후 시작.

---

## 1. 종합 판정

| 항목 | 결과 |
|---|---|
| A. 정적(빌드·테스트·DTO 교차·presentation 무변경) | **통과** |
| B. 학습 없이 판정(핵심) | **통과** |
| C. 참여자 조회 1회 · 폴링 중 재조회 없음 · 중간 합류 | **통과** |
| D. 나간 사람 닉네임 유지 | **통과** |
| E. 회귀(계정 전환 캐시 오염 · 로그아웃 폴링 잔존) | **통과** |
| 결함 | **1건 (범위 밖 · FCM 시작 크래시)** |

**결론: 47 산출물(참여자 목록 연동)은 합격.** 45 리포트에 남아 있던 채팅의 두 한계 — ① 세션 첫 전송 전까지 내 과거 메시지가 상대로 보임, ② 상대 이름이 전부 `동승자` 로 폴백 — 이 **둘 다 실기에서 사라진 것을 확인**했다. 발견된 결함 1건은 이번 변경과 무관한 FCM 초기화 경로다(§7).

---

## 2. A. 정적 검증 — 통과

| 검사 | 결과 |
|---|---|
| `./gradlew :data:testDebugUnitTest :app:assembleDebug` | **BUILD SUCCESSFUL** |
| 유닛 테스트 총계 | **190 tests / 0 failures / 0 errors** (15개 클래스) |
| 그중 이번 범위 | `ChatMappersTest` 22 · `RemoteChatRepositoryTest` 20 · `AuthenticatedPathContractTest` 15 — 전부 통과 |
| APK | `app/build/outputs/apk/debug/app-debug.apk` (13:55 빌드 — 소스 최종 수정 13:55:00 이후) |

### presentation 무변경 — 확인

`git diff HEAD --stat` 은 42~44 작업분(미커밋)까지 포함해 presentation 14개 파일을 보여 주므로 그것만으로는 판정할 수 없다. **파일 mtime 으로 47 작업창(13:49~13:55)을 분리해 교차 확인**했다.

```
2026-09-08 13:55:00  data/.../repository/RemoteChatRepository.kt
2026-09-08 13:53:21  data/src/test/.../AuthenticatedPathContractTest.kt
2026-09-08 13:52:02  data/src/test/.../ChatMappersTest.kt
2026-09-08 13:50:12  domain/.../model/Chat.kt · data/.../remote/ChatMappers.kt
2026-09-08 13:49:38  domain/.../repository/ChatRepository.kt
2026-09-08 13:49:20  data/.../dto/ChatDtos.kt · data/.../ChatApi.kt
------------------------------------------------------- (47 작업창 경계)
2026-09-07 18:01:53  presentation/.../chat/ChatRoute.kt      ← 44 산출물
2026-09-07 18:01:00  presentation/.../core/MainNavGraph.kt   ← 44 산출물
```

47 이 건드린 파일은 **data/ 7개 + domain/ 2개뿐**이고 presentation 최신 수정은 전날 18:01 이다. 47 리포트의 "presentation 무변경" 주장은 사실이다.

### ChatMemberResponse ↔ 서버 응답 교차 (실측)

> **주의**: `../backend` 워킹트리(`792cc85 배포 최종 수정`)에는 이 엔드포인트가 **없다** — `ChatRoomUserController.java` 에 `getMembers` 가 없고 `ChatRoomMemberResult.java` 파일 자체가 없다. 8080 에 떠 있는 것은 리더 패치본(별도 빌드)이므로 **소스 대조가 불가능해 실제 응답을 계약의 근거로 삼았다.** (같은 이유로 8080 에는 워킹트리에 없는 **파티 정원 도달 시 채팅방 자동 생성**도 들어 있다 — 45 §9-1 의 이슈는 이 서버에서는 해소돼 있다.)

```
GET /api/v1/chat-rooms/2/users   (참여자 A 토큰)  → 200
[{"userId":4,"publicId":"01a07f60-f124-70c4-8ae8-4d22dcf95ac2","nickname":"큐에이에이","imageUrl":null,"active":true},
 {"userId":5,"publicId":"01a07f60-f194-7f34-b842-8b8a662dd691","nickname":"큐에이비","imageUrl":null,"active":true}]

GET /api/v1/chat-rooms/2/users   (미참여자 C 토큰) → 403 {"code":"CHAT_NOT_PARTICIPANT","message":"채팅방 참여자가 아닙니다."}
POST /chat-rooms/2/messages (B)  → 201 {"id":3,"chatRoomId":2,"userId":5,...}
```

| 서버 응답 필드 | 앱 `ChatMemberResponse` | 결과 |
|---|---|---|
| `userId` (Long, 실제로 내려옴) | `userId: Long? = null` | 일치 (nullable 관용은 구버전 서버 대비, 이 서버에선 항상 채워짐) |
| `publicId` (uuid 문자열) | `publicId: String = ""` | 일치 |
| `nickname` | `nickname: String = ""` | 일치 |
| `imageUrl` (null) | `imageUrl: String? = null` | 일치 |
| `active` | `active: Boolean = true` | 일치 |

- **래핑 없음** — 최상위가 배열이고 `ChatApi.getMembers` 반환형이 `List<ChatMemberResponse>` 다. unwrap 누락 없음.
- 응답에 앱이 모르는 필드 없음, DTO에 서버가 안 주는 필드 없음 (**불일치 0**).
- **조인 키 실증**: 참여자 `userId:5` = B의 메시지 `userId:5`, 참여자 `publicId` = 로그인 토큰의 `sub` 클레임. 이 두 축이 맞아떨어져야 `isMine`/`senderName` 이 성립하는데 실측으로 확인했다.
- 경로·동사·`@Path` 1개 계약은 `AuthenticatedPathContractTest:170,199` 가 리플렉션으로 고정 중.

---

## 3. B. 학습 없이 판정 (핵심) — 통과

시드: A `cmqaa`/큐에이에이, B `cmqab`/큐에이비, C `cmqac`/큐에이씨, D `cmqad`/큐에이디 (전부 `Passw0rd!`).
파티 2(부산대→부산역, 2인) A 개설 → B curl 합류 → 정원 도달로 **채팅방 2 자동 생성**(양쪽 `/chat-rooms/me` 에 등장) → B가 curl 로 메시지 2건 선행 전송.

| 시나리오 | 45 리포트(이전) | 이번 실측 | 증거 |
|---|---|---|---|
| A 앱 최초 방 진입 (**A 는 아직 아무것도 안 보냄**) | 상대 이름 `동승자` | **왼쪽 말풍선 + 닉네임 「큐에이비」** | `chatmem_01_B_nickname_before_any_send.png` |
| A 전송 | 오른쪽 파란 말풍선 | 동일 (`msg-A-first`) | `chatmem_03_A_send_right_bubble.png` |
| **force-stop 후 재진입** | A 과거 메시지가 `동승자`(학습 전 한계) | **즉시 내 파란 말풍선** — 새 프로세스에서 전송 0건 | `chatmem_02_after_forcestop_mine_immediately.png` |

`am force-stop com.moyeota` → 재기동 → 채팅 탭 → 방 진입까지 **전송 없이** 도달했고, `msg-A-first` 가 처음부터 오른쪽 파란 말풍선으로 그려졌다. **45 §4 의 "학습 전 한계"는 사라졌다.**

시각 표기도 정상 유지(서버 `04:58Z` → 화면 `13:58`, 45 결함-3 회귀 없음).

---

## 4. C. 호출 횟수 · 중간 합류 — 통과

### 방 진입 시 참여자 1회, 폴링 중 재조회 없음

logcat OkHttp 기준, **방 진입 시점부터** 집계.

| 구간 | `/chat-rooms/2/users` | `/messages?size=30` | `/messages/after` |
|---|---|---|---|
| 최초 진입 (~21초) | **1** | 1 | 7 |
| force-stop 후 재진입 (~27초) | **1** | 1 | 9 |

폴링(`/messages/after`)이 7~9회 도는 동안 참여자 재조회는 **0회**. 47 의 `unresolved` 가드가 실제로 작동한다.

### 중간 합류 (C가 늦게 합류 후 발화)

C가 `POST /chat-rooms/2/users`(201) 로 합류 → 메시지 전송(`{"id":6,...,"userId":6}`). A 화면은 폴링 한 사이클 만에 **「큐에이씨」 닉네임으로 표시**.

```
14:03:48.125  GET /chat-rooms/2/messages/after?cursor=5   ← 모르는 발신자 6 등장
14:03:48.150  GET /chat-rooms/2/users                     ← 그 로드에서 1회만 재조회
14:03:51.196  GET /chat-rooms/2/messages/after?cursor=6
   … 이후 16회 폴링 (14:04:34 까지) — /users 재조회 0회
```

정확히 **1회 재조회 후 재발 없음**. 증거: `chatmem_04_late_joiner_C_nickname.png`

부수 확인: 이 구간에서 액세스 토큰 만료로 `POST /api/v1/auth/reissue` 1회가 자동 발생했고 폴링이 끊기지 않았다(토큰 리프레시 회귀 없음).

---

## 5. D. 나간 사람 — 통과

B가 `DELETE /chat-rooms/2/users`(200) 로 퇴장. 서버 참여자 목록은 B를 `active:false` 로 계속 내려준다(실측).

**캐시가 아니라 새 조회로 검증하기 위해** 앱을 force-stop 후 재기동해 방에 다시 들어갔다 — 그래도 B의 과거 메시지 2건에 **「큐에이비」 유지**. 증거: `chatmem_05_left_member_nickname_kept.png`

---

## 6. E. 회귀 — 통과

### 계정 전환 캐시 오염 없음 (45 결함-1 재확인)

A 로그아웃 → **C(`cmqac`)로 로그인** — C도 같은 방 2의 참여자라, 캐시가 남으면 즉시 어긋나는 조건이다.

| 메시지 | A 시점 | C 시점(전환 후) | 판정 |
|---|---|---|---|
| `hi from B one/two` (B) | 왼쪽 「큐에이비」 | 왼쪽 「큐에이비」 | 정상 |
| `msg-A-first` (A) | **오른쪽(내 것)** | **왼쪽 「큐에이에이」** | 정상 — 소유권 뒤집힘 |
| `late joiner C here` (C) | 왼쪽 「큐에이씨」 | **오른쪽(내 것)** | 정상 — 학습 없이 |

증거: `chatmem_06_account_switch_C_perspective.png`. 이는 **3인 방에서 발화자를 이름으로 구분**하는 것까지 함께 증명한다(45 §9-2 가 지적한 "전부 동승자 폴백" 해소).

### 로그아웃 후 이전 계정 폴링 잔존 없음 (45 결함-1)

```
14:06:03  DELETE /users/me/fcm-token → POST /auth/logout
14:06:39  POST /auth/login            ← 그 사이 이전 방 요청 0건
14:07:02  GET /chat-rooms/2/users     ← C 세션에서 새로 시작
```

로그아웃~재로그인 구간에 이전 방 폴링 **0건**, 403 **0건**. 채팅 탭도 열려 있던 방으로 복원되지 않고 목록으로 시작(45 결함-2 정리 유지). 회귀 스윕(홈·합승·마이·채팅 탭 순회) 중 4xx/5xx **0건**, 크래시 **0건**.

증거: `chatmem_07_C_room_list_no_stale.png`, `chatmem_08_regression_chat_list.png`

---

## 7. 결함 — 1건 (이번 범위 밖, 담당: api-integrator 또는 리더)

### 결함-1 (높음) — FCM 토큰 조회 실패 시 앱 시작 크래시

**`app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt:49`**

```kotlin
.addOnCompleteListener { task ->
    val token = task.result                 // ← 49행: 실패한 Task 에서 먼저 꺼낸다
    if (!task.isSuccessful || token == null) {   // ← 50행: 여기 도달하지 못한다
        Log.w(TAG, "FCM 토큰 조회 실패", task.exception)
        return@addOnCompleteListener
    }
    onFcmTokenAvailable(token)
}
```

`Task.getResult()` 는 실패한 Task 에서 **`RuntimeExecutionException` 을 던진다.** 가드는 그 다음 줄에 있어 **영영 실행되지 않는 죽은 코드**다. 의도(실패를 로그만 남기고 넘어가기)는 맞는데 순서 때문에 무효가 됐다.

**재현** (이번 세션 1회 실측):
1. `adb shell pm clear com.moyeota` (또는 신규 설치) — FCM 토큰이 캐시에 없는 상태
2. 앱 실행 → FCM 신규 등록 시도 → Google 측이 `INTERNAL_SERVER_ERROR` 반환
3. **메인 스레드 FATAL EXCEPTION → 프로세스 종료** (스플래시도 못 띄우고 런처로 되돌아감)

```
09-08 13:58:36.979 FATAL EXCEPTION: main
com.google.android.gms.tasks.RuntimeExecutionException: java.io.IOException: FCM Registration failed!
	at com.google.android.gms.tasks.zzw.getResult(...)
	at com.moyeota.app.MoyeotaApplication.fetchFcmToken$lambda$0(MoyeotaApplication.kt:49)
Caused by: java.io.IOException: INTERNAL_SERVER_ERROR
	at com.google.firebase.messaging.GmsRpc.handleResponse(GmsRpc.java:315)
```

전체 트레이스: `_workspace/screenshots/chatmem_fcm_crash_trace.txt`

**성격**: 이번 변경(47)과 무관하며 `9a965b7 feat(fcm)` 부터 있던 결함이다. 45 리포트가 "크래시 0건"이었던 건 그때 FCM 등록이 성공했기 때문 — **트리거는 산발적이지만 걸리면 100% 크래시**다. 신규 설치 첫 실행이 가장 위험한 시점이라는 게 문제다(등록 성공 후에는 캐시된 토큰이 쓰여 재현되지 않는다 — 이번에도 이후 4회 실행은 정상, `PUT /users/me/fcm-token` 204).

**제안 수정** (순서만 바꾸면 된다):

```kotlin
.addOnCompleteListener { task ->
    if (!task.isSuccessful) {
        Log.w(TAG, "FCM 토큰 조회 실패", task.exception)
        return@addOnCompleteListener
    }
    val token = task.result
    if (token.isNullOrBlank()) {
        Log.w(TAG, "FCM 토큰이 비어 있음")
        return@addOnCompleteListener
    }
    onFcmTokenAvailable(token)
}
```

**직접 수정하지 않았다.** 1~2줄이긴 하나 (a) 47 의 검증 대상 diff 에 섞이고 (b) 크래시 경로 변경이라 재검증이 따라야 해서, 담당 에이전트가 반영 후 신규 설치 첫 실행으로 재확인하는 편이 맞다고 판단했다.

---

## 8. 참고 (결함 아님)

1. **방 진입이 순차 2왕복이다.** `RemoteChatRepository.getMessages`(101행)에서 `refreshMembersQuietly` 를 `await` 한 뒤에야 `api.getMessages` 를 부른다. 46 입력이 "먼저(또는 병렬)"을 허용했으므로 계약 위반은 아니지만, 진입 지연이 두 호출의 합이다. 실측 지연은 체감되지 않는 수준(같은 초 내 연속 발사)이라 이번엔 넘겼다 — 47 §남은요청 3(방 상세에 참여자 동봉)이 해결되면 자연히 없어진다.
2. **45 §9-1(채팅방 자동 생성 부재)은 이 서버에서 해소돼 있다.** 파티 2/2 도달 시 방이 자동 생성되고 양쪽이 자동 참여됐다 — QA 가 `POST /chat-rooms` 수동 생성을 할 필요가 없었다. 다만 `../backend` 워킹트리에는 여전히 그 코드가 없어, 소스와 기동 서버가 어긋나 있다.
3. 45 §9-3(미인증 401 이 `USER005`)은 이 서버에서도 그대로다 — 이번 `/users` 경로도 토큰 없이 부르면 `401 USER005` 다.

---

## 9. 남은 백엔드 요청 (47 §남은요청 재확인)

1. **`ChatRoomMemberResult.userId` 패치의 develop 반영** — 이번 연동 전체가 이 필드에 걸려 있다. 반영 전 서버에서는 사전이 비고 학습 폴백만 남아 45 의 한계가 그대로 돌아온다. `../backend` 워킹트리에는 아직 엔드포인트 자체가 없다.
2. (장기·권장) **메시지에 `senderPublicId`·`senderNickname` 동봉** — 앱 매퍼가 이미 최우선으로 읽으므로(`ChatMappers.kt:95-98`) 백엔드 배포만으로 즉시 정확해지고, 그 시점에 참여자 캐시·학습 폴백·내부 PK 노출을 함께 걷어낼 수 있다.

---

## 10. 미검증

- **STOMP `/ws-chat`** — 45 와 동일하게 WebSocket 클라이언트가 환경에 없다. 앱은 폴링만 쓰므로 이번 판정에 영향 없음.
- **`searchMessages` 의 참여자 사전 반영** — 화면이 없어 실기 대상이 아니다. 코드상 `resolveIdentity` 를 같은 방식으로 통과하며 유닛 테스트가 덮는다.
- **`userId` 를 안 주는 구버전 서버에서의 폴백** — 패치본 서버뿐이라 실기로는 재현 불가. 유닛 테스트(`ChatMappersTest`·`RemoteChatRepositoryTest`)로만 확인.

---

## 11. 산출물 · 재현 메모

**스크린샷** `_workspace/screenshots/`
`chatmem_00_A_room_list.png` · `chatmem_01_B_nickname_before_any_send.png` · `chatmem_02_after_forcestop_mine_immediately.png` · `chatmem_03_A_send_right_bubble.png` · `chatmem_04_late_joiner_C_nickname.png` · `chatmem_05_left_member_nickname_kept.png` · `chatmem_06_account_switch_C_perspective.png` · `chatmem_07_C_room_list_no_stale.png` · `chatmem_08_regression_chat_list.png`

**로그** `_workspace/screenshots/chatmem_logcat.txt` (OkHttp 포함 전체 세션) · `chatmem_fcm_crash_trace.txt`

**서버 최종 상태(8080)**: 계정 `cmqaa`/`cmqab`/`cmqac`/`cmqad` (전부 `Passw0rd!`, 닉네임 큐에이에이/큐에이비/큐에이씨/큐에이디, 내부 userId 4/5/6/7) · 파티 2(부산대→부산역, 2/2, MATCHING) · **채팅방 2**(참여자 A active·B **inactive**·C active, 메시지 4건: id 3·4=B, 5=A, 6=C).

**재현 메모**
- 에뮬레이터는 떠 있지 않아 `Pixel_6` 을 `-no-snapshot-load` 로 직접 콜드부트(1대). 검증 후 종료했다. 8080 백엔드 프로세스·Redis 는 조작하지 않았다.
- `adb shell input text` 는 공백에서 문자열이 잘린다 — 메시지·아이디는 공백 없는 ASCII 로 넣었다.
- 한글 입력이 불가해 파티 생성·상대 계정 조작은 전부 curl 로 시드했다.
