# 방장 전용 「합승 완료」 게이팅 — 실기 검증 (2026-10-23)

`config-change-qa.md` §5 가 **미확인**으로 남긴 항목을 닫는다.

## 왜 지금까지 못 봤나

지난 QA 때 비방장에게도 버튼이 보였다. 앱 버그가 아니라 **그때 로컬 백엔드가 `host` 를 아예 안
내려 줬기** 때문이고, 앱은 그 경우 `isHost = host ?: isMe` 로 **나만** 방장으로 보는 하위호환
동작을 한다(`PartyMappers.kt`). 즉 **게이팅이 도는 모습을 볼 수 있는 서버가 없었다.**

## 환경

| | |
|---|---|
| 백엔드 | 로컬, 작업트리 `refactor/driver-review`(= `host`·`hostPublicId`·`version` 모두 포함) |
| DB · 캐시 | H2 인메모리 + Redis(docker `moyeota-qa-redis`) |
| **`TAXI_ENABLED`** | **`false`** — 운영과 같게 맞춤 |
| 앱 | `MOYEOTA_BASE_URL=http://10.0.2.2:8080/` 로 빌드한 debug, 에뮬 `Pixel_6` API 37 |
| 계정 | `hostqa1023`(방장큐에이) · `guestqa1023`(손님큐에이), 정원 2명 방 |

### ★ `TAXI_ENABLED=false` 가 이 검증의 전제다

로컬 기본값은 `true` 다. 그대로 두면

```
isFinishStage(status, taxiEnabled) = !taxiEnabled && status == MATCHED
```

의 앞항이 false 라 **방장에게도 비방장에게도 버튼이 안 뜬다.** 그 상태로 "비방장에게 버튼 없음"을
확인하면 아무것도 검증하지 못한 채 통과 도장만 찍는다. 상태 전이도 갈린다:

| 서버 | 정원 충족 시 status | 앱 `RideStatus` | 완료 버튼 |
|---|---|---|---|
| `TAXI_ENABLED=true` | `MATCHING` | `DISPATCHING` | 안 뜸(배차로 감) |
| `TAXI_ENABLED=false` | `COMPLETED` | **`MATCHED`** | **방장만** |

**다음에 이 검증을 다시 할 때도 반드시 `TAXI_ENABLED=false` 로 띄울 것.**

## 결과 — 둘 다 통과

### ① 비방장에게는 버튼이 없고 안내문이 뜬다

`손님큐에이` 로 앱에서 직접 합류 → 2/2 충족(`COMPLETED`/`MATCHED`).

- 「합승 완료」 **없음**(uiautomator 질의 `NO`)
- 대신 **「다 타면 방장 방장큐에이님이 「합승 완료」를 눌러 방을 끝내요」** — 실제 방장 닉네임이 들어간다
- `방장큐에이` 에 **「방장」 배지** 표시

서버 응답도 같이 확인: `방장큐에이 host=true` / `손님큐에이 host=false`, `version=1`.

### ② 방장이 나가면 버튼이 승계자에게 넘어간다

방장큐에이 퇴장 → 1/2 로 떨어져 **모집 중**으로 돌아간다. 이 단계에선 **아무에게도 버튼이 없는 게
맞다**(정원 미충족). 그래서 승계를 보려면 **방을 다시 채워야 한다** — 방장큐에이를 재합류시켰다.

재합류 후 서버: `손님큐에이 host=true` / `방장큐에이 host=false`, `version=5` — 승계 확정.

앱 화면(`손님큐에이`):
- 「합승 완료」 **있음**
- 안내문이 방장용으로 바뀜 — **「채팅으로 만날 곳을 정해요. 다 타면 「합승 완료」를 눌러 주세요」**
- **「방장」 배지는 아무에게도 안 보인다** — 정상이다. `showsHostBadge = isHost && !isMe` 라
  내가 방장이면 나에게 배지를 달지 않는다(스스로에게 알릴 필요가 없다).

### ③ 끝까지 동작

「합승 완료」 탭 → 확인 다이얼로그(「같이 탄 사람 모두의 방이 종료돼요」) → 승인
→ `POST /api/v1/matching/rooms/1/finish` **204** → 홈 복귀.

## 부수 발견 — 앱 데이터를 지우면 진행 중인 방으로 못 돌아간다

검증 중, curl 로 합류시킨 뒤 앱을 열었더니 **진행 중인 방 배너도 자동 이동도 없었다.**

`RemoteActivePartyRepository` 는 기억(DataStore)이 없을 때 **채팅방 목록으로 폴백**하는데,
후보를 `it.room.partyId.takeIf { it > 0 }` 로 거른다. 그런데 **운영·로컬 모두 `GET /chat-rooms/me`
응답에 `partyId` 가 없다**(실측: `chatRoomId`·`departure`·`destination`·`status`·`members`…).
매퍼가 채운 자리값 0 이 그대로 걸러져 **폴백이 항상 빈손**이다. 앱 KDoc 도 이미 그렇게 적고 있다.

평소엔 앱에서 만들거나 합류하면서 기억이 생기니 드러나지 않는다. 드러나는 경우는
**앱 재설치·데이터 삭제·기기 변경 뒤 진행 중인 방이 있을 때**고, 그때는 방으로 돌아갈 길이 없다
(30분 만료를 기다려야 한다). 이번 변경과 무관한 기존 사안이라 여기 기록만 한다 —
고치려면 백엔드에 `GET /chat-rooms/me` 응답의 `partyId` 추가를 요청해야 한다.

## 재현 방법

```bash
docker run -d --name moyeota-qa-redis -p 6379:6379 redis:7-alpine
cd ../backend && TAXI_ENABLED=false ./gradlew bootRun          # ★ false 필수
cd ../frontend && MOYEOTA_BASE_URL=http://10.0.2.2:8080/ ./gradlew :app:assembleDebug
adb emu geo fix 127.0276 37.4979                               # 방 출발지(강남역) 근처로
```

계정은 `_workspace/sweep-1023/` 의 `signup.sh` 흐름(SMS 발송 → 로그에서 인증번호 → verify →
register → login)을 따른다. **로컬 access token 유효기간이 2분**이라 curl 로 길게 작업하면
중간에 재로그인해야 한다.

**좌표를 하드코딩하지 말 것.** 「로그인」처럼 같은 문자열이 제목과 버튼에 둘 다 있고, 에러 박스가
뜨면 버튼이 아래로 밀린다. `uiautomator dump` 로 매번 `bounds` 를 읽어 눌러야 한다.
