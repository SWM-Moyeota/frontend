# config-change-qa — 구성 변경(회전 등) 수정 실기 검증

대상: `fix/place-roadname-null` @ `4caaba9` (fix: 회전하면 가짜 방이 뜨던 문제 …). **코드 무수정 · 검증만.**
검증자: qa-verifier · 2026-10-15

## 검증 환경 (중요 — 운영 대신 로컬 백엔드를 썼다)

| 항목 | 값 |
|---|---|
| 에뮬레이터 | `Pixel_6` 콜드부트(`-no-snapshot-load -wipe-data`), `emulator-5554`, 1080x2400 |
| 백엔드 | **로컬 `localhost:8080`** (사용자가 띄워 둔 프로세스, profile=`local`, H2 인메모리, PID 15919). 죽이거나 재기동하지 않음 |
| APK | `MOYEOTA_BASE_URL=http://10.0.2.2:8080/ ./gradlew :app:assembleDebug` → DEX 문자열로 base URL 확인(`http://10.0.2.2:8080/`) · `adb install -r -t` |
| 계정 | 로컬에 새로 만듦: `cfgqa1`(회전테스트)·`cfgqa2`(합류이)·`cfgqa3`(동승삼), 비번 `Passw0rd!`, 문자 고정코드 `123456` |
| GPS | 서면역(35.1579,129.0596) `adb emu geo fix` |
| 입력 | uiautomator dump → `input tap`. 한글은 로컬 지오코더가 영문 질의도 받으므로 `haeundae` 등 ASCII 로 검색 |

**왜 운영이 아니라 로컬인가:** 이번 결함은 전적으로 **클라이언트 측 상태 복원**(구성 변경 시 `rememberSaveable`) 문제라
서버는 "진짜 방"을 내려 주기만 하면 된다. 지시서가 경고한 **문자 인증 하루 한도**와 실제 사용자 영향을 피하려고
이미 떠 있던 로컬 백엔드를 썼다. **운영에는 쓰기 요청을 하지 않았다**(운영 `/config` 200 생존만 확인). 앱 기본 base URL 은
운영(`api.moyeota.p-e.kr`)이지만 런타임 전환 수단이 없어 로컬용 APK 를 따로 빌드했다.

**Activity 재생성 확인:** `app/src/main/AndroidManifest.xml:33` 의 `.MainActivity` 에 `android:configChanges` 가 **없다** →
회전·글꼴·다크모드 모두 Activity 를 재생성한다. 아래 각 항목에서 logcat 으로 재생성을 실측했다(프로세스 PID 4887 유지,
Window 토큰 교체, `relaunch`/`Config changes` 로그).

빌드: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**. 전 세션 FATAL·ANR **0건**(logcat crash/main 버퍼).

---

## 결과 요약

| # | 항목 | 결과 |
|---|---|---|
| 1 | 회전해도 내 방 유지 (21 매칭 대기) | **통과** |
| 2a | 글꼴 크기 1.3 (21) | **통과** |
| 2b | 다크 모드 (21) | **통과** |
| 3 | 16 도착지 확인 모달 회전 | **통과** |
| 4 | 가짜 데이터 전수(홈→탐색→합류확인→21→채팅) | **통과** |
| 5 | 방장 전용 「합승 완료」 게이팅 | **미확인(로컬 백엔드 한계)** — §5 참조 |
| — | 부수 발견: 15 장소검색 stale 에러 | **별건 버그**(이번 수정과 무관) — §6 |

---

## 1. 회전해도 내 방이 유지되는가 — 핵심 · 통과

진짜 방을 **앱에서 직접 생성**해 방장으로 21 매칭 대기에 진입했다(가장 충실한 재현 — 결함 사슬의 `createdPartyId` 를 탄다).
- 방 37, `cfgqa1`(회전테스트) 호스트, **출발지 「부산광역시 부산진구 중앙대로 730 서면역」 → 도착지 「해운대해수욕장」**, 3인.
- 회전 전(세로) → **가로**(`accelerometer_rotation 0` + `user_rotation 1`) → 다시 **세로**.

**Activity 재생성 실측**: `ActivityTaskManager: Config changes=20000480 {… mDisplayRotation=ROTATION_90 … land}`,
프로세스 PID **4887 유지**, MainActivity Window 토큰 교체(`1c98c22`#181 → `382948a`#193), `finishDrawing of relaunch: …MainActivity`.

**결과**: 회전 전후 모두 **서면역 → 해운대해수욕장 / 회전테스트 / 3인** 그대로. 더미 키워드(`센텀시티`·`서면역 1번 출구`·`부산가자`·`3,600`) **0건**.
- 세로→가로→세로 왕복 뒤 uiautomator 텍스트에 출발지·도착지·멤버·매칭 조건 전부 복원.
- 스크린샷: `21_waiting_portrait.png`, `21_waiting_landscape_rotated.png`, `21_waiting_after_rotate_back.png`

## 2. 다른 구성 변경 — 통과

같은 방(21)에서:

**2a 글꼴 크기 1.3** (`settings put system font_scale 1.3`):
- 재생성 실측 `Config changes=40000000 {1.3 …}`, PID 유지.
- 서면역→해운대, 회전테스트, 3인 유지. 더미 0건. 스크린샷 `21_waiting_font_scale_1.3.png`. (되돌림 `1.0`)

**2b 다크 모드** (`cmd uimode night yes`):
- 재생성 실측 `Config changes=200 {… night …}`, PID 유지.
- 상세 카드(회전테스트/나, 서면역→해운대, 매칭 조건 3인) 그대로, 레이아웃 깨짐 없음. 더미 0건.
  (지도 타일은 어두워지나 바텀시트는 라이트 서피스 — 이 화면 다크 테마 미완성이지만 **상태 유실은 아님**.)
- 스크린샷 `21_waiting_dark_mode.png`. (되돌림 `night no`)

## 3. 16 도착지 확인 모달 회전 — 통과

15 목적지 입력 → 「해운대해수욕장」 선택 → 「경로 확인하기」 → **16 모달**(`dialog`, 회전 시 재생성 대상)에서 가로 회전.
- 회전 전: 출발 서면역 → 도착 해운대해수욕장, 예상 42분·총 17,700원, 1인 5,900원.
- **회전 후(가로)**: 제목 「도착지 확인」, 경로 지도(서면→해운대), 도착지 해운대해수욕장, 42분·17,700원, 「같이 탈 사람 찾기」 **모두 유지**.
- **「출발지가 없어요…」 문구 0건.** 스크린샷 `16_modal_portrait.png`, `16_modal_landscape_rotated.png`
- 근거 코드: `MainNavGraph.kt:215-216` `confirmedOrigin/Destination` 를 `rememberSaveable(stateSaver = PlaceSaver)` 로, `PlaceSaver`(:877~) 가 `Place` 4필드를 Bundle 로 저장.

## 4. 가짜 데이터 전수 — 통과

홈 → 동승(탐색) → 합류 확인 → 21 → 채팅을 돌며 **서버에 없는 데이터**를 찾았다. **발견 0건.**
- **탐색(17) 목록**: 지도 뷰포트 질의(`GET /matching/rooms?swLat..neLat..`) 결과만 렌더 — 「주변 동승 1개」 = cfgqa2 의 실제 방 38(서면역 7번 출구→부산시민공원, 1/2명, 합류). 옛 `DefaultParties` 더미 5개(부산대 정문→서면역·장전역·온천장역·김OO·이OO…) **안 뜸**. 스크린샷 `explore_list_no_phantom.png`
- **합류 확인(20)**: 방 38 실데이터(예상 14분·3.9km, 6,500원/3,250원, 호스트 합류이).
- **채팅**: 방 38 실데이터(합류이, 서면역 7번 출구→부산시민공원), 메시지 없음(더미 메시지 0). 스크린샷 `chat_room.png`
- 근거: 이번 커밋이 `ExploreScreen`/`MatchWaiting`/`DispatchStatus` 등 5곳의 `ride`/`parties` 더미 **기본 인자 제거**, 21·25 의 `partyId==null` 분기를 **ErrorBox** 로 교체(`MatchWaitingRoute.kt:858`, `DispatchStatusRoute.kt:231`) — 더미 화면을 그리던 경로가 사라졌고 더미는 `@Preview` 전용으로만 남음(`MatchWaitingScreen.kt:93 waitingRideDummy`).

## 5. 방장 전용 「합승 완료」 게이팅 — 미확인(환경 한계)

- **방장 쪽(확인됨)**: 방 37 정원 충족(3/3) 시 호스트 cfgqa1 화면에 **「합승 완료」 버튼**과 멤버 3명(회전테스트/합류이/동승삼) 정상 표시,
  「다 타면 「합승 완료」를 눌러 주세요」. 회전해도 버튼·멤버 유지(스크린샷 `21_full_host_finish_button.png`, `21_full_host_after_rotate.png`).
  앱에서 「합승 완료」 실제 탭 → `POST /matching/rooms/37/finish` **204** → 홈 복귀(동작 확인).
- **비방장 쪽(핵심 — 로컬 백엔드에서 검증 불가)**: cfgqa2 가 만든 방 38 에 cfgqa1 이 합류(비방장)해 정원 충족(2/2)시켜도
  **비방장 cfgqa1 에게도 「합승 완료」가 보였다**. 원인은 앱 버그가 아니라 **로컬 백엔드(de193d9)가 방장 정보를 아예 안 내려 준다**는 것:
  방 상세 응답에 `hostPublicId` 없음, 멤버마다 `host`/`isHost` 필드 없음(멤버 키 = `publicId,nickname,imageUrl,badgeId,rideCount`).
  앱은 `PartyMappers.kt:101` `isHost = host ?: isMe` — 서버가 host 를 빼면 **현재 사용자를 방장으로 낙관 표시**하고 서버 403 에 맡기는
  **의도된 하위호환 동작**(같은 파일 71–85 주석에 명시). 운영 서버는 `host` 를 원시 boolean 으로 늘 실으므로 거기선 게이팅이 먹는다.
- **결론**: 호스트에게 버튼이 보이는 것은 확인. **비방장에게 안내문만 보이는지는 이 백엔드로는 재현 불가** → **미확인**.
  지시서가 「어려우면 미확인으로」라고 한 범위다. 운영 백엔드(또는 host 를 내려 주는 로컬 빌드)에서 재확인 필요.

## 6. 부수 발견 — 15 장소검색 stale 에러 (이번 수정과 무관, 별건)

빠르게 타이핑해 목적지를 검색하면 서버가 **200 + 정상 목록**을 줘도 「장소를 검색하지 못했어요」가 **지워지지 않고 남아** 결과가 안 보인다.
- 재현: 15 에서 `PNU`/`busan` 등을 보통 속도로 입력 → 에러 문구. **한 글자씩 천천히(>1s)** 입력하면 정상(결과 렌더). 스크린샷 `15_search_stale_error_bug.png`(에러), `15_search_bug_resolved.png`(정상).
- 원인: `DestinationRoute.kt:116 runSearch` 의 `searchJob?.cancel()` 로 취소된 요청의 `CancellationException` 이 `catch (e: Exception)`(:128)에 걸려 `searchErrorMessage` 를 세팅하는데, **성공 분기(:127)가 `searchErrorMessage` 를 지우지 않는다.** 그리고 `DestinationScreen.kt:330` `when` 의 첫 가지가 `searchErrorMessage != null` 이라, 결과가 있어도 **에러 문구가 결과를 가린다.**
- **이번 구성 변경 수정과 무관**하나 `fix/place-roadname-null` 브랜치 주제(장소 검색) 안이라 함께 보고한다. 수정은 리더 판단 — 제안: 성공 분기에서 `searchErrorMessage = null` 클리어 + `catch` 에서 `CancellationException` 재던지기.

---

## 뒷정리 · 원복 확인

- **만든 방 전부 정리**: 36(leave→CANCELED)·37(합승 완료→FINISHED)·38(host finish→FINISHED)·39/40/41(probe, leave→CANCELED).
  정리 후 `GET /matching/rooms` 에 내 방 **0건**(남은 9개는 다른 세션 시드, 안 건드림). 세 계정 모두 활성 파티 없음.
- **디바이스 설정 전부 원복**: `accelerometer_rotation=1`, `user_rotation=0`, `font_scale=1.0`, `night=no`.
- **원복 안 한 것(다음 세션 무영향)**: 로컬 H2 에 테스트 계정 cfgqa1/2/3 잔존(서버 재기동 시 소멸). 에뮬레이터는 cfgqa1 로그인 상태로 홈에 둠. 로컬 백엔드(사용자 프로세스)는 안 건드림.
- 브랜치·커밋·stash 변경 없음. 운영 서버 쓰기 없음.

## 스크린샷 경로

`/Users/sungyoon/Desktop/moyeota/frontend/_workspace/screenshots/config-change/` (원본은 세션 scratchpad `…/scratchpad/cfgqa/`):
`16_modal_portrait.png`, `16_modal_landscape_rotated.png`, `21_waiting_portrait.png`, `21_waiting_landscape_rotated.png`,
`21_waiting_after_rotate_back.png`, `21_waiting_font_scale_1.3.png`, `21_waiting_dark_mode.png`,
`21_full_host_finish_button.png`, `21_full_host_after_rotate.png`, `21_nonhost_full.png`,
`explore_list_no_phantom.png`, `chat_room.png`, `15_search_stale_error_bug.png`, `15_search_bug_resolved.png`
