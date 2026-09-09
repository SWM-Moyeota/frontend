# 45 · qa-verifier — 채팅 인증(@CurrentUser) 전환 검증

작성 2026-09-07. 입력: `_workspace/42_input_chat_auth.md`, `43_api-integrator_chat_auth.md`, `44_compose-builder_chat_auth.md`(§결함 수정 포함).
환경: 백엔드 **localhost:8081**(워킹트리), 에뮬레이터 **Pixel_6_QA(emulator-5554, 콜드부트)**, 앱 `MOYEOTA_BASE_URL=http://10.0.2.2:8081/`.

> 검증은 2회 수행했다. **1차**(수정 전)에서 결함 3건을 보고했고, compose-builder 수정 후 **2차 재검증**에서 3건 모두 해소를 확인했다.

---

## 1. 종합 판정

| 항목 | 1차 | 재검증 |
|---|---|---|
| A 정적(테스트·DTO 교차·헤더) | 통과 | 통과 |
| B 채팅 목록 | 통과 | 통과 |
| C 메시지·내 메시지 판정 | 통과(한계 재현) | 통과 |
| D 읽음·나가기·403/409 | 통과 | 통과 |
| E 회귀 | **결함 1건** | 통과 |
| F STOMP | 미검증 | 미검증 |
| 결함-1 로그아웃 폴링 잔존 | **발생(19회 403)** | **해소(0건)** |
| 결함-2 방별 VM·중복 폴링 | **발생(2개 동시)** | **해소(0건)** |
| 결함-3 시각 UTC 표시 | **발생(08:46)** | **해소(17:46 KST)** |

**결론: 채팅 `@CurrentUser` 전환 연동은 합격.** data/domain(43 산출물)에서는 처음부터 결함이 없었고, presentation(44 산출물)의 결함 3건은 재검증에서 전부 해소됐다. 남은 이슈는 백엔드 3건과 채팅과 무관한 지도 ANR 1건이다.

---

## 2. A. 정적 검증 — 통과

| 검사 | 결과 |
|---|---|
| `:data:testDebugUnitTest` | **172 tests / 0 failures** (15개 클래스) |
| `:presentation:testDebugUnitTest` | 1차 12 → 재검증 **17 tests / 0 failures** (`ChatTimeLabelTest` 5건 추가) |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| APK BASE_URL | dex 문자열에 `http://10.0.2.2:8081/` 단일 확인 (8080 없음) |
| `FIXED_MEMBER_ID` · `currentUserId` grep | **0건** |
| `X-User-Id` grep | 프로덕션 코드 **0건** (제거 경위를 적은 KDoc/테스트 주석만 잔존) |

### DTO ↔ 서버 record 교차 (필드 1:1 일치, 불일치 0)

`backend/src/main/java/team/codingforest/moyeota/chat/` 실소스 대조.

| 서버 record | 앱 DTO | 결과 |
|---|---|---|
| `ChatMessageResult(id, chatRoomId, userId, content, type, createdAt, deleted)` | `ChatMessageResponse` | 일치 (+ `senderPublicId`/`senderNickname` 관용 필드는 서버 미제공 → null 역직렬화 정상) |
| `ChatMessageSlice(messages, nextCursor, hasNext)` | `ChatMessageSliceResponse` | 일치 |
| `ChatRoomResult(id, partyId, departure, destination, createdAt, status)` | `ChatRoomResponse` | 일치 |
| `ChatRoomUserResult(chatRoomId, lastReadMessageId, notificationMuted, joinedAt)` | `ChatRoomUserResponse` | 일치 (`lastReadMessageId` null 수신 확인) |
| `ChatRoomRequest(partyId, departure, destination)` | `CreateChatRoomRequestDto` | 일치 |
| `SendMessageRequest(content)` | `SendMessageRequestDto` | 일치 |

- `search` 파라미터 `keyword`/`cursor`/`size` — `ChatMessageController` 실소스와 일치.
- 서버 채팅 컨트롤러 4종(`ChatRoom`·`ChatRoomUser`·`ChatMessage`·`Stomp`)에 `@RequestHeader` **0건** — 서버 쪽도 `X-User-Id` 완전 제거.
- `createdAt` 은 `Instant` → **ISO-8601 문자열**로 직렬화(`"2026-09-07T08:35:54.400505Z"`) → DTO 의 `String?` 매핑 안전.
- `GET /messages` 는 **id 내림차순**(3,2,1)으로 내려온다. ViewModel 의 `sortedBy { it.id }` 가 필요하며 실제로 적용돼 있다.

### 헤더 미전송 실증 (logcat OkHttp BODY)

```
--> GET http://10.0.2.2:8081/api/v1/chat-rooms/1/messages?size=30
--> END GET                                    ← 요청 헤더 0개

--> POST http://10.0.2.2:8081/api/v1/chat-rooms/1/messages
Content-Type: application/json; charset=utf-8
Content-Length: 24
{"content":"hello from"}                       ← 본문에 사용자 id 없음
--> END POST (24-byte body)
<-- 201 ... {"id":1,"chatRoomId":1,"userId":1,...}
```

Retrofit 이 만든 요청에 `X-User-Id` 가 없음이 실측으로 확인된다(`Authorization` 은 로거 하위의 네트워크 인터셉터가 붙이며, 200/201 응답이 그 존재를 증명). 계약 고정은 `AuthenticatedPathContractTest` 의 리플렉션 검사가 담당.

---

## 3. B. 채팅 목록 — 통과

- 목록 제목이 서버 실값 `"{departure} → {destination}"` — 부산대 → 부산역 / 장전역 → 해운대 / 온천장 → 광안리.
- 상태 라벨: ACTIVE → `진행 중`, CLOSED → `종료된 방`.
- 빈 상태: `아직 참여 중인 채팅방이 없어요. 합승에 합류하면 채팅방이 열려요`.
- `GET /chat-rooms/me` 200, 방마다 `GET /chat-rooms/{id}` 상세 조회(N+1) 정상.

증거: `screenshots/chat_01_list_real_route.png`, `chat_11_list_status_labels.png`, `chat_r01_list_3rooms.png`

> **주의**: 파티 정원 도달 시 채팅방 자동 생성은 **8081 워킹트리에 없다**(아래 백엔드 이슈 1). QA 는 `POST /chat-rooms` 로 수동 생성 후 양쪽이 `POST /{id}/users` 로 참여해 진행했다.

---

## 4. C. 메시지 · 「내 메시지」 판정 — 통과

| 시나리오 | 결과 |
|---|---|
| A 앱에서 전송 | 오른쪽 파란 말풍선(`isMine=true`) |
| B curl 전송 → 3초 폴링 | 왼쪽 흰 말풍선 + 이름 `동승자`, 6초 내 수신 |
| 앱 재시작(force-stop) 후 재진입 | A의 과거 메시지가 `동승자`로 표시 — **42/43 에 명시된 인터림 한계, 결함 아님** |
| 1건 전송 후 재조회 | 과거 메시지까지 내 말풍선으로 **교정** |
| 다른 방(앱에서 보낸 적 없는 방) | 학습된 내부 id 가 저장소 스코프라 그대로 적용 → 정확 |
| **계정 전환(A→B) 캐시 오염** | **없음.** B 세션에서 A의 메시지가 전부 `동승자`. `learnedForUuid` 가드 정상 동작 |

증거: `chat_02_send_mine_bubble.png`, `chat_03_peer_bubble_polling.png`, `chat_04_before_learning_limitation.png`, `chat_06_corrected_after_refetch.png`, `chat_r09_B_cache_isolated.png`

**1차에서 지적한 뉘앙스(재조회 도달성)** 는 결함-2 수정으로 함께 해소됐다 — 아래 §6 참조.

---

## 5. D. 읽음 · 나가기 · 에러 문구 — 통과

| 항목 | 실측 |
|---|---|
| 읽음 처리 | 전송·수신 직후 `POST /api/v1/chat-rooms/{id}/users/read/{msgId}` → **200** (id 9, 10 확인) |
| 나가기 | `DELETE /chat-rooms/{id}/users` → **200** → 14 홈 복귀 → 목록에서 사라짐 |
| 나가기 후 채팅 탭 재진입 | 나간 방을 다시 열지 않고 **목록**으로 시작 (44 의 `openedRoom` 정리 확인) |
| 403 `CHAT_NOT_PARTICIPANT` | 앱 문구 **"이 채팅방에 참여하고 있지 않아요"** (입력 내용 보존, 전송 안 됨) |
| 409 `CHAT_ROOM_CLOSED` | 앱 문구 **"종료된 채팅방이에요"** |
| 401 (토큰 없음) | 서버가 `USER005` 로 응답 — `CHAT_UNAUTHORIZED` 아님(백엔드 이슈 3) |

curl 교차 확인:
```
B 방 나간 뒤 전송  → 403 {"code":"CHAT_NOT_PARTICIPANT","message":"채팅방 참여자가 아닙니다."}
C 미참여 방 조회   → 403 CHAT_NOT_PARTICIPANT
종료된 방에 전송   → 409 {"code":"CHAT_ROOM_CLOSED","message":"종료된 채팅방입니다."}
토큰 없이 조회     → 401 {"code":"USER005","message":"로그인이 필요합니다."}
```
> 종료된 방도 **조회(GET messages)는 200** 이고 `/chat-rooms/me` 에 계속 남는다. 전송만 409.

증거: `chat_07_403_not_participant.png`, `chat_08_409_room_closed.png`, `chat_09_leave_to_home.png`, `chat_10_list_empty_after_leave.png`, `chat_r11_403.png`, `chat_r12_409.png`, `chat_r13_leave_to_home.png`, `chat_r14_list_after_leave.png`

---

## 6. 1차 결함 3건 · 재검증 결과

### 결함-1 (높음) — 로그아웃 뒤 이전 계정 폴링 잔존 → **해소**

- **1차 증상**: A 로그인 → 방 진입 → **마이/합승 탭** 이동 → 로그아웃 → B 로그인 시, B 토큰으로 A의 방을 3초마다 폴링. 로그아웃(17:50:25) 이후 `GET /chat-rooms/1/messages/after` **19회, 전부 403**. 채팅 화면이 A 세션 UI 그대로 남고, 합승 탭도 A의 「내 탑승」 화면으로 복원됐다.
- **원인**: `MainNavGraph.kt` `navigateTab` 의 `popUpTo(HOME){ saveState = true }` 가 CHAT 엔트리의 ViewModelStore 를 **저장**(파기 아님) → `resetTo` 의 `popUpTo(0){inclusive}` 가 저장분을 지우지 못함.
- **수정**(44): `resetAfterSignOut()` 이 `TabRoutes` 4개에 `clearBackStack()` + `ChatRoomViewModel` 폴링을 `LifecycleStartEffect` 로 start/stop.
- **재검증 결과**: 로그아웃(18:14:10) 이후 **이전 방 `/messages` 요청 0건**. 채팅 탭은 B 자신의 방 목록만 새로 로드(A의 room 1 없음, 열려 있던 방으로 복원되지 않음), 합승 탭은 「합승 – 내 주변」 초기 화면. → **통과**

증거: `chat_r06_logout.png`, `chat_r07_B_own_list_no_restore.png`, `chat_r08_B_explore_no_restore.png`

### 결함-2 (중간) — 방별 VM 누적 · 중복 폴링 · 재진입 무갱신 → **해소**

- **1차 증상**: `viewModel(key="chat-room-${room.id}")` 로 방마다 VM 이 쌓여 방 1·방 2 폴링이 **동시** 발생. `init` 이 방당 1회만 돌아 뒤로→재진입 시 재조회가 없고, 학습 후에도 과거 메시지가 교정되지 않았다.
- **수정**(44): 고정 key 단일 VM + `onScreenStart(roomId)` 방 교체, 재진입마다 `load()`(조용한 갱신).
- **재검증 결과**:
  - 방2 → 목록 → 방3 이동 후 16초간 **방2 폴링 0건**, 방3만 3초 주기(5회).
  - 방3에서 전송 → 뒤로 → 재진입 시 `GET /chat-rooms/3/messages?size=30` **재호출 확인(+1)**, `room3 from A` 가 `동승자` → **내 파란 말풍선으로 교정**.
  - 폴링 중단: **홈 탭 0건 / 마이 탭 0건 / 앱 백그라운드 0건** (1차엔 마이·합승 경유 시 계속됐다).
  → **통과**

증거: `chat_r03_room_switch_no_dual_poll.png`, `chat_r04_after_send.png`, `chat_r05_reenter_refetch_corrected.png`

### 결함-3 (낮음, 기존) — 메시지 시각이 UTC → **해소**

- **1차 증상**: `ChatRoute.kt` `toTimeLabel()` 이 ISO 문자열을 잘라 쓰기만 해 KST 17:36 메시지가 `08:36` 으로 표시(헤더 "오후 6:38"과 9시간 불일치). `git show HEAD` 로 **이번 변경 이전부터 존재**함을 확인.
- **수정**(44): `toTimeLabel(zone = ZoneId.systemDefault())` — `Instant.parse` → `OffsetDateTime.parse` 순 파싱 후 기기 시간대 `HH:mm`.
- **재검증 결과**: 서버 `08:46Z` → 화면 **`17:46`**, `09:05Z` → **`18:05`**, 실시간 수신 메시지 **`18:17`**. `ChatTimeLabelTest` 5/5 통과. → **통과**

증거: `chat_r02_time_kst_fixed.png`, `chat_r10_send_recv_read.png`

---

## 7. E. 회귀 — 통과

로그인 / 14 홈(네이버 실지도·닉네임 `QaA님`·`QaB님`) / 17 합승 탐색(실지도·주변 목록) / 20 합류확인(서버 실값 + 기존 참여자 `QaC 동승자`) / **21 대기**(「같이 탈 사람 찾는 중」 — `지금 같은 방향 2명(나 외 1명) · 목표 3명`, 모인 사람 `QaC` / `QaD` + **「나」 배지**) / 24 채팅 / 35 마이 — 전부 정상.

21 대기의 「나」 배지는 `UserSession` 제거 후 **publicId 기반 매퍼 판정**으로 대체된 경로이며, 새로 로그인한 계정(QaD)에서 정확히 동작했다.

**크래시(FATAL EXCEPTION) 0건** — 1차·재검증 모두.

증거: `chat_14_regression_waiting21.png`

---

## 8. 신규 발견 — 지도 ANR 1건 (채팅과 무관 · 환경 요인)

재검증 중 로그인 직후 하단탭 탭 시 ANR 1회 발생. **채팅 수정과 무관**하며 원인이 스택으로 확정됐다.

```
"main" prio=5 tid=1 Waiting
  at java.lang.Object.wait(...)
  - waiting on <0x0cfc74b3> (a android.opengl.GLSurfaceView$GLThreadManager)
  at android.opengl.GLSurfaceView$GLThread.onPause(GLSurfaceView.java:1733)
  at com.naver.maps.map.MapView.onStop(SourceFile:292)
  at ...NaverMapViewKt.NaverMapView$lambda$19$lambda$18$sync(NaverMapView.kt:117)
  ... LifecycleRegistry.backwardPass ...
  at ...MainNavGraphKt.MainNavHost$navigateTab(MainNavGraph.kt:178)

"GLThread 134" prio=5 tid=51 Native
  native: eglMakeCurrent → libEGL_emulation.so → qemu_pipe_read   ← 에뮬레이터 GL 파이프 정지
```

- 메인 스레드가 `MapView.onStop()` → `GLSurfaceView.onPause()` 에서 GL 스레드를 기다리는데, GL 스레드가 **에뮬레이터의 소프트웨어 EGL(`libEGL_emulation`) 파이프 읽기에서 멈춰** 있다.
- 즉 **에뮬레이터 GPU(swiftshader) 문제**이며 앱 코드 결함이 아니다. 다만 `NaverMapView.kt:117` 의 `mapView.onStop()` 이 메인 스레드 동기 호출이라 GL 이 느린 환경에서 ANR 로 번진다는 위험은 실재한다(실기기 확인 권장, 별도 과제).
- 앱 force-stop 후 정상 복구, 이후 동일 조작 반복에서 재현되지 않음.

증거: `chat_r15_anr_navermap.png`, `chat_r_anr_trace_main_thread.txt`

---

## 9. 백엔드 이슈 · 요청

1. **파티 정원 도달 시 채팅방 자동 생성이 8081 워킹트리에 없다.** 파티가 2/2 로 차고 `status=MATCHING` 이 돼도 양쪽 `GET /chat-rooms/me` 가 `[]`. `createChatRoom` 호출부는 `ChatRoomController` 하나뿐이고 matching 도메인 어디에도 없다 — `feat/chat-room-auto-create` 가 이 브랜치에 없거나 미머지. QA 는 `POST /chat-rooms {partyId, departure, destination}` 수동 생성으로 대체했다.
   - 부수 확인: **파티 1개당 채팅방 1개** 제약이 있다(같은 partyId 재생성 시 `CHAT_ROOM_ALREADY_EXISTS`).
   - 방 생성자가 자동 참여되지는 않는다 — 생성 후 별도로 `POST /{id}/users` 가 필요하다.
2. **`ChatMessageResult` 에 `senderPublicId`(UUID)·`senderNickname` 추가 요청** (43 리포트 요청 1·2 유효). `chat/app/dto/ChatMessageResult.java` 실소스에 여전히 없다. 앱은 관용 필드를 이미 열어 뒀으므로 **서버 배포만으로**
   - 세션 첫 전송 전까지 내 과거 메시지가 상대로 보이는 인터림 한계가 사라지고,
   - 상대 이름이 전부 `동승자` 로 폴백되는 문제(3인 이상 방에서 발화자 구분 불가)가 해소된다.
   내부 `userId` 노출 제거도 함께 권장.
3. **미인증 401 이 `USER005`("로그인이 필요합니다")로 내려온다** — `CHAT_UNAUTHORIZED` 가 아니다. Spring Security 엔트리포인트가 채팅 컨트롤러보다 먼저 잡기 때문. 앱의 `ChatException.isUnauthorized` 는 이 경로에서 매칭되지 않고 기본 문구로 떨어진다. 현재 동작상 문제는 없으나 42 의 계약 표와 어긋나므로 문서/코드 중 한쪽 정정 필요.
4. (참고) `matching` 의 `Radius` 는 **100~500m** 만 허용(`matching/domain/Radius.java`). 1000 은 400 `INVALID_RADIUS` — 시드 스크립트 작성 시 주의.

---

## 10. 미검증

- **F. STOMP `/ws-chat`** — websocat/wscat 등 WebSocket 클라이언트가 환경에 없어 CONNECT(네이티브 헤더 `Authorization: Bearer`) 를 검증할 수단이 없다. 앱은 폴링만 사용하므로 이번 범위의 판정에는 영향 없음.
- **`searchMessages`** — 화면이 없어 실기 대상 아님. 계약(경로·파라미터명)만 서버 소스와 대조해 일치 확인.

---

## 11. 재현 환경 메모

- **emulator-5554 는 처음에 떠 있지 않았다.** `Pixel_6_QA` AVD 를 `-no-snapshot-load`(콜드부트)로 직접 기동했다. emulator-5558 은 존재하지 않아 건드리지 않았고, 8081 백엔드 프로세스도 조작하지 않았다.
- 시드 계정(8081 H2, 비밀번호 전부 `Qa!12345`): `qachata`(QaA) · `qachatb`(QaB) · `qachatc`(QaC) · `qachatd`(QaD) · `qachate`(QaE).
- 최종 서버 상태: 파티 1(부산대→부산역, 2인, MATCHING) · 파티 2(부산대→부산역, 3인, C·D) · 파티 3(온천장→광안리, 3인, E) / 채팅방 1(CLOSED) · 2(ACTIVE) · 3(CLOSED).
- 한글 입력 불가로 메시지·계정은 전부 영문/숫자. 파티 생성은 장소검색 UI 대신 curl 로 시드했다(출발/도착 한글 필요).

## 12. 산출물 경로

**1차 스크린샷** `_workspace/screenshots/`
`chat_01_list_real_route.png` · `chat_02_send_mine_bubble.png` · `chat_03_peer_bubble_polling.png` · `chat_04_before_learning_limitation.png` · `chat_05_after_send_not_corrected_inplace.png` · `chat_06_corrected_after_refetch.png` · `chat_07_403_not_participant.png` · `chat_08_409_room_closed.png` · `chat_09_leave_to_home.png` · `chat_10_list_empty_after_leave.png` · `chat_11_list_status_labels.png` · `chat_12_stale_ui_after_account_switch.png` · `chat_13_fresh_load_cache_isolated_ok.png` · `chat_14_regression_waiting21.png`

**재검증 스크린샷** `_workspace/screenshots/`
`chat_r01_list_3rooms.png` · `chat_r02_time_kst_fixed.png` · `chat_r03_room_switch_no_dual_poll.png` · `chat_r04_after_send.png` · `chat_r05_reenter_refetch_corrected.png` · `chat_r06_logout.png` · `chat_r07_B_own_list_no_restore.png` · `chat_r08_B_explore_no_restore.png` · `chat_r09_B_cache_isolated.png` · `chat_r10_send_recv_read.png` · `chat_r11_403.png` · `chat_r12_409.png` · `chat_r13_leave_to_home.png` · `chat_r14_list_after_leave.png` · `chat_r15_anr_navermap.png`

**로그**
`_workspace/screenshots/chat_auth_logcat.txt` (1차, 41,636줄 · OkHttp BODY 포함)
`_workspace/screenshots/chat_auth_logcat_reverify.txt` (재검증)
`_workspace/screenshots/chat_r_anr_trace_main_thread.txt` (ANR 스레드 덤프)
