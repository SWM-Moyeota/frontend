# 백엔드 변경 전수 조사 — 커버리지 & 운영 대조 (2026-10-23)

조사 범위: `284a950..origin/develop` = **71 커밋** (백엔드 `/Users/sungyoon/Desktop/moyeota/backend`, 읽기 전용)
저장소: `https://github.com/SWM-Moyeota/Backend.git`
`origin` ref 갱신 시점: `.git/FETCH_HEAD` mtime **2026-10-23 21:24**

## 0. 한 줄 결론 세 개

1. **PR 없이 직접 푸시된 커밋은 0건이다.** 71개 전부가 머지 PR 14개(#233·#235·#237·#238·#241·#243·#244·#246·#249·#250·#251·#253·#256·#257) 중 하나를 통해 develop 에 들어왔다. 분해 합계가 정확히 71로 맞는다(§1 각주).
2. **`origin/main..origin/develop` = 0 커밋이고 두 브랜치의 트리가 바이트 단위로 같다.** 즉 "develop 에만 있고 main 에 없는 변경"은 **없다**. 조사 범위 71개 전부가 prod 에 가 있다.
3. **배포는 이미 끝났다.** `https://api.moyeota.co.kr/v3/api-docs` 가 200 으로 응답하고(1차 시도는 503), SSE 설명문이 `origin/develop` 의 `PartySseController.java` 와 **바이트 단위로 동일**하다 → 운영은 PR #257 까지 포함한 #245 빌드다. 운영 계약은 **실측**으로 확인했다(§4).

**미분류 커밋 33개**를 찾았다 — A·B·C 세 담당 어디에도 안 들어가는 PR **#233·#235·#237·#241·#243·#244**다. 다만 그중 대부분은 **이미 2026-10-15 스윕(`backend-sweep-1015.md`)이 다룬 것**이고, 앱 코드도 이미 흡수한 상태다. **실제로 아직 앱이 안 읽는 것은 3건**(§2.7).

---

## 1. 커밋 71개 전수표

담당 표기: **A** = SSE·매칭방 잠금·version(#249·#250·#251·#257) / **B** = 실시간 매칭(#256) / **C** = 즐겨찾기·채팅·결제(#238·#253·#246) / **미분류** = 셋 중 어디에도 지정되지 않음

`git log` 순(최신 → 과거).

| # | 해시 | 날짜 | 한 줄 요약 | PR | 담당 |
|---|---|---|---|---|---|
| 1 | `82f000f` | 10-22 | Merge PR #257 | #257 | A |
| 2 | `880b1fc` | 10-22 | Merge PR #253 | #253 | C |
| 3 | `8e7d54d` | 10-22 | develop → refactor/remove-sse-version-heartbeat 역머지 | #257 | A |
| 4 | `b8a308e` | 10-22 | SSE version heartbeat 제거, 배차 실패·운행 완료에 `closed` 발행 | #257 | A |
| 5 | `16319ae` | 10-22 | Merge PR #256 | #256 | B |
| 6 | `f931ac5` | 10-22 | develop → feature/real-time-matching 역머지 | #256 | B |
| 7 | `0f86d38` | 10-22 | Merge PR #251 | #251 | A |
| 8 | `8ec44c4` | 10-22 | Merge PR #250 | #250 | A |
| 9 | `ff4b2d5` | 10-22 | 한국 도보 OSRM 이미지 ECR 빌드 워크플로 추가 | #256 | B |
| 10 | `95059cb` | 10-22 | 실시간 매칭 후보를 출·도착 거리 합 상위 5개로 제한, 지도영역 인덱스 유지 | #256 | B |
| 11 | `355d6bd` | 10-21 | 깨진 단위테스트 + PostGIS 없는 DB 이미지 수정 | #256 | B |
| 12 | `426a527` | 10-21 | **OSRM 도보시간 기반 실시간 매칭 후보 조회 API 추가** | #256 | B |
| 13 | `c109b33` | 10-21 | Merge PR #246 | #246 | C |
| 14 | `1926932` | 10-20 | **V20 PostGIS 확장 + 300m 반경 매칭방 조회 쿼리** | #256 | B |
| 15 | `e2f7014` | 10-20 | `@Scheduled` 작업을 설정한 스레드풀 4개로 돌게 함 | #253 | C |
| 16 | `f758cbc` | 10-20 | 채팅방을 나가면 서버가 그 방 구독을 해제 | #253 | C |
| 17 | `5161326` | 10-17 | Merge PR #238 | #238 | C |
| 18 | `8e7a920` | 10-17 | 즐겨찾기 `placeName` → ERD 컬럼 `place_name` 명시 매핑 | #238 | C |
| 19 | `1b0b25e` | 10-17 | fix/party-lock-sse-race → feature/party-version 내부 머지 | #251 | A |
| 20 | `774deb9` | 10-17 | SSE `connected` 도 version 규칙 따름 + 이벤트 순서 Swagger 명시 | #251 | A |
| 21 | `ad69beb` | 10-17 | 잠금 동시성 테스트 대기에 시간 제한 | #250 | A |
| 22 | `019704f` | 10-17 | fix/party-lock-sse-race → feature/party-version 내부 머지 | #251 | A |
| 23 | `4a6ea57` | 10-17 | 부하테스트 결과 파일 `.gitignore` | #250 | A |
| 24 | `e7ff0fb` | 10-17 | 부하테스트 SSE 클라이언트를 version 규칙으로, 알림 정리 | #251 | A |
| 25 | `784bda5` | 10-17 | **매칭방 `version` 도입 + DB 기반 SSE heartbeat (V18)** | #251 | A |
| 26 | `3065da0` | 10-17 | 잠금 조회 동시성 테스트 + SSE 연결 닫기 마무리 | #250 | A |
| 27 | `2406342` | 10-17 | sse 선행 버그 수정 | #250 | A |
| 28 | `2c7a9c5` | 10-16 | 즐겨찾기 API 수동 확인용 `.http` 시나리오 | #238 | C |
| 29 | `8f54ad4` | 10-16 | **즐겨찾기 PK 를 복합키 → 자동채번 `place_id` 단일키 (V19)** | #238 | C |
| 30 | `4abc8e0` | 10-16 | Merge PR #249 | #249 | A |
| 31 | `582a6a2` | 10-16 | D7 에서 SSE 전파지연 측정, 알림을 k6 지표로 | #249 | A |
| 32 | `e76800e` | 10-16 | SSE 지표를 `PartySseMetrics` 로 분리, 서버 전파지연 Timer 제거 | #249 | A |
| 33 | `e7466b2` | 10-16 | SSE 알림 규칙 추가(`alerts.yml`) | #249 | A |
| 34 | `bc8c3a0` | 10-16 | 부하테스트에 SSE 정합성 검증 추가 | #249 | A |
| 35 | `230b41b` | 10-16 | 매칭방 SSE 서버 지표 추가 | #249 | A |
| 36 | `b818dcd` | 10-15 | 포트원 웹훅 시크릿 환경변수(`PORTONE_WEBHOOK_SECRET`) 설정 | #246 | C |
| 37 | `d9c17a4` | 10-15 | Merge PR #244 | #244 | **미분류** |
| 38 | `fc7637d` | 10-15 | 스모크 테스트의 합승 종료를 방장이 하도록 수정 | #244 | **미분류** |
| 39 | `3b1d6ed` | 10-15 | 즐겨찾기 삭제 리뷰 반영 + **전체 삭제 API** + **복합키 생성자 추가(#231 수정)** | #238 | C ※1 |
| 40 | `de193d9` | 10-15 | Merge PR #243 | #243 | **미분류** |
| 41 | `b81aa1a` | 10-15 | **SSE `member-left` 에 승계 방장 `hostPublicId` 포함** | #243 | **미분류** |
| 42 | `b4ba392` | 10-15 | Merge PR #241 | #241 | **미분류** |
| 43 | `930fc6a` | 10-15 | 방장 판별을 `Party.isHost` 로 일원화 | #241 | **미분류** |
| 44 | `f9e66e5` | 10-15 | **방 상세의 방장을 내부 `hostId` → 멤버별 `host` 플래그로 교체** | #241 | **미분류** |
| 45 | `b8e4750` | 10-15 | Merge PR #237 | #237 | **미분류** |
| 46 | `5bb5957` | 10-15 | **1차 모드 `/finish` 를 방장 전용으로 (403 `NOT_PARTY_HOST`)** | #237 | **미분류** |
| 47 | `739b78d` | 10-15 | Merge PR #235 | #235 | **미분류** |
| 48 | `cdc39da` | 10-15 | **즐겨찾기 장소 삭제 API 추가** | #238 | C ※1 |
| 49 | `2e1395c` | 10-15 | 프로필 이미지 목록 파일을 `config/` 로 이동 | #235 | **미분류** |
| 50 | `4b5b969` | 10-15 | **옛 URL 프로필 이미지를 비움 + CDN 끝 슬래시 보정 (V17)** | #235 | **미분류** |
| 51 | `c95f9ac` | 10-15 | 프로필 이미지 목록을 `profile-images.yaml` 로 분리 | #235 | **미분류** |
| 52 | `f130acd` | 10-15 | Merge PR #233 | #233 | **미분류** |
| 53 | `c43c80c` | 10-15 | 채팅 구독 큐가 버린 이벤트를 지표로, 종료 때 큐 비움 | #233 | **미분류** |
| 54 | `d1da35c` | 10-14 | 메시지 순번 마이그레이션을 V14 → **V15** 로 이동 | #233 | **미분류** |
| 55 | `2b4adf5` | 10-14 | 입장·퇴장 방송의 회원조회를 방별 큐 밖으로 | #233 | **미분류** |
| 56 | `f594c67` | 10-14 | 방별 실행기·메시지 순번 주석 정리 | #233 | **미분류** |
| 57 | `0b21226` | 10-14 | 안 쓰는 실시간 실행기 이름 상수 삭제 | #233 | **미분류** |
| 58 | `ebdb3ec` | 10-14 | **채팅방 목록·방 정보·푸시에 메시지 순번(`seq`/`lastSeq`) 추가** | #233 | **미분류** |
| 59 | `9e9557b` | 10-14 | **메시지 저장 시 채팅방 행 잠금 + 방별 순번 부여 (V14→V15)** | #233 | **미분류** |
| 60 | `ba186c2` | 10-14 | Redis 수신 실행기 연결, 안 쓰는 실시간 실행기 삭제 | #233 | **미분류** |
| 61 | `e6566c8` | 10-14 | 채팅 방송을 방별 단일 스레드 큐로 발행 | #233 | **미분류** |
| 62 | `b418ab9` | 10-14 | Redis 채팅 이벤트를 단일 스레드로 순서대로 처리 | #233 | **미분류** |
| 63 | `64be4fc` | 10-14 | 같은 세션의 STOMP 수신·송신 순서 보장 | #233 | **미분류** |
| 64 | `8d06409` | 10-14 | 채팅 순서 역전 지표 + D4 전송주기 옵션 | #233 | **미분류** |
| 65 | `8b746ce` | 10-14 | **채팅·합승 멤버 + 내 정보 응답에 프로필 이미지 URL 추가** | #235 | **미분류** |
| 66 | `b356719` | 10-14 | 유저 컨트롤러 Swagger 설명·메서드 순서 정리 | #235 | **미분류** |
| 67 | `a1694ee` | 10-14 | **회원가입 성별에 `UNDISCLOSED` 추가 (V16)** | #235 | **미분류** |
| 68 | `7e68e6f` | 10-14 | **프로필 이미지를 제공 목록 키로만 받고, 기본 이미지 복귀 API 추가** | #235 | **미분류** |
| 69 | `4dd7e29` | 10-14 | 공개 프로필 수정 서비스를 `UserService` 로 개명 | #235 | **미분류** |
| 70 | `a75b3df` | 10-14 | 프로필 수정·FCM 코드를 `users` 패키지로 이동 | #235 | **미분류** |
| 71 | `afe24c3` | 10-14 | **고를 수 있는 프로필 이미지 목록 API 추가** | #235 | **미분류** |

### 각주

**※1** — `3b1d6ed`·`cdc39da` 는 PR **#238** 소속이므로 담당 **C**(즐겨찾기)의 영역이지만, 오케스트레이터가 C 에게 명시한 커밋 목록(`8f54ad4`, `8e7a920`, `2c7a9c5`)에는 **없다**. 그런데 이 둘이 #238 의 **본체**다(삭제 API 3종 + #231 수정). C 가 이 둘을 읽지 않으면 #238 의 핵심을 놓친다 → **C 에게 명시 전달 필요**.

### 담당별 집계 (합계 = 71, 재현: `git log <merge>^1..<merge>^2`)

| 담당 | PR | 커밋 수 |
|---|---|---|
| A | #249(6) · #250(5) · #251(6) · #257(3) | **20** |
| B | #256(7) | **7** |
| C | #238(6) · #246(2) · #253(3) | **11** |
| **미분류** | #233(13) · #235(11) · #237(2) · #241(3) · #243(2) · #244(2) | **33** |
| | | **71** |

### PR 없이 직접 푸시된 커밋 — **0건**

14개 머지 PR 의 2번째 부모 쪽 커밋을 전부 더하면 71 이 된다. 누락·고아 커밋 없음.
`4a6ea57`(결과파일 ignore)·`2406342`(SSE 선행 버그) 처럼 커밋 메시지에 PR 번호가 안 적힌 것도 모두 PR 안에 들어 있다.

### 마이그레이션 — 범위 내 추가분 **5개** (V14 는 V15 로 개명되어 실체는 4개)

| 파일 | 내용 | PR | 담당 | 운영 영향 |
|---|---|---|---|---|
| `V15__chat_message_seq.sql` (←V14) | 메시지 방별 순번 컬럼 | #233 | 미분류 | `seq`/`lastSeq` 응답 필드가 생긴다 |
| `V16__user_profile_gender_undisclosed.sql` | Gender 에 `UNDISCLOSED` | #235 | 미분류 | 앱 이미 반영 |
| `V17__users_image_url_to_key.sql` | **옛 URL 로 저장된 프로필 이미지를 비운다** | #235 | 미분류 | **운영 데이터 삭제성**. 수동으로 `imageUrl` 넣은 테스트 계정은 기본 이미지로 보인다 (1015 스윕 A5 와 동일) |
| `V18__match_room_version.sql` | `match_room.version bigint NOT NULL DEFAULT 0` | #251 | A | SSE version 규칙의 저장소 |
| `V19__favorite_place_single_key.sql` | 즐겨찾기 PK 를 `(user_id, place_name)` → `place_id` 단일키, `user_id` 인덱스 추가 | #238 | C | **`FavoritePlaceResponse` 에 `placeId` 가 생겼다** (§2.6) |
| `V20__add_postgis_location_index_to_match_room.sql` | `CREATE EXTENSION postgis` + 생성열 2개 + GiST 부분 인덱스 2개 | #256 | B | PostGIS 확장이 **운영 DB 에 설치된다** |

### 설정 파일 변경 — `application.yaml` 전수

범위 내 `application.yaml` 변경은 **4곳**뿐이고, **기능 플래그를 켜거나 끈 변경은 없다**.

```
+ spring.config.import: classpath:config/profile-images.yaml   (#235)
+ portone.webhook.secret: ${PORTONE_WEBHOOK_SECRET:}            (#246)
+ moyeota.profile-image.base-url: ${PROFILE_IMAGE_BASE_URL:https://cdn.moyeota.co.kr/}  (#235)
+ osrm.base-url: ${OSRM_BASE_URL:http://localhost:5000}         (#256)
```

- **`application-prod.yaml` 과 `application-dev.yaml` 은 범위 내 변경 0건이다** (`git diff --stat 284a950..origin/develop -- src/main/resources/application-prod.yaml` 빈 출력).
- **`moyeota.taxi.enabled` 는 이 범위에서 안 바뀌었다.** 운영 기본값은 그 전부터 `${TAXI_ENABLED:false}` (`application-prod.yaml:48`), 로컬 기본값은 `true`. 이 플래그를 마지막으로 건드린 커밋은 범위 밖 `04f6be7` 이다.
- `@ConditionalOnProperty` 가 붙은 클래스 목록도 범위 내 변동 없음: `DispatchCallController`·`DispatchListener`·`MatchingSweeper`·`FcmCallNotifier`·`DriverLocationController`·`RideController`·`DriverController`(모두 `taxi.enabled=true` 필요), `CompletedPartySweeper`·`ChatOnlyCompletionPolicy`(`=false` 필요), `DispatchCompletionPolicy`(`=true`), `LoggingSmsSender`/`SolapiSmsSender`(`moyeota.sms.provider`).
- `spring.task.scheduling.pool.size: 4` 는 범위 전부터 있었고, `e2f7014` 가 그 설정이 실제로 먹게 만든 것이다(§2.5).

### `build.gradle` 의존성 변경 — **0건**

`git diff 284a950..origin/develop -- build.gradle settings.gradle gradle/` → 빈 출력. 의존성·플러그인·버전 변경 없음.
PostGIS·OSRM 은 자바 의존성이 아니라 **DB 확장 + 외부 HTTP 호출**로 붙었다.

### 삭제된 엔드포인트 — **0건** / 삭제된 DTO 필드 — **1건(범위 내 자기상쇄)**

- 컨트롤러 매핑 전후 비교에서 **제거된 매핑은 없다.** 추가만 5건: `POST /matching/rooms/realtime`, `DELETE /users/me/favorite-places/{placeId}`, `POST /users/me/favorite-places/batch-delete`, `DELETE /users/me/favorite-places/all`, `GET /profile-images`, `DELETE /me/profile-image`.
- DTO 필드 삭제: `PartyDetailResult.hostId`(Long) 가 `5bb5957`(#237)에서 **추가**됐다가 `f9e66e5`(#241)에서 **제거**됐다 — 그 자리를 `MemberInfo.host`(boolean) 가 대신한다. 284a950 기준으로는 순변화 0이므로 앱이 깨질 일은 없다. 운영 OpenAPI 실측에서도 `PartyDetailResult` 에 `hostId` 없음을 확인했다.
- 컨트롤러 파일 이동 1건: `user/profile/presentation/UserController.java` → `user/users/presentation/UserController.java` (경로 URL 은 그대로).

### 새 에러코드 — 5개 (전부 미분류/C 소속)

| 코드 | HTTP | PR | 담당 |
|---|---|---|---|
| `NOT_PARTY_HOST` | 403 | #237 | 미분류 |
| `FAVORITE_PLACE_NOT_FOUND` | 404 | #238 | C |
| `INVALID_PLACE_IDS` | 400 | #238 | C |
| `INVALID_PLACE_ID` | 400 | #238 | C |
| `INVALID_PROFILE_IMAGE` (`USER112`) | 400 | #235 | 미분류 |

---

## 2. 미분류 커밋 33개 — 무엇이고 앱 영향이 있는가

미분류 33개는 **PR 6개**로 묶인다. 전부 **10-14 ~ 10-15** 작업이고, 그 시점은 `_workspace/backend-sweep-1015.md` 가 다룬 창(窓)이다. 그래서 상당수는 **이미 조사됐고 앱에도 반영돼 있다**. 아래는 각 PR 을 다시 읽고 **현재 앱 코드로 실제 흡수 여부를 직접 확인한** 결과다.

### 2.1 PR #237 (2커밋) — `/finish` 가 방장 전용이 됐다 → **앱 반영 완료**

`5bb5957`:
```java
public void finishWithoutDriver(Long memberId) {
    if(!hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);
+   if(!hostId().equals(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_HOST);
```
방장 = **가장 먼저 합류한 현재 멤버**(저장 컬럼 없는 파생값, `joinedAt` → `memberId` tie-break). 방장이 나가면 자동 승계.

**앱 영향**: 비방장에게 「합승 완료」 버튼을 보여 주면 403 이 된다.
**확인 결과 — 반영됨.** `domain/.../PartyError.kt:147` 에 `NOT_PARTY_HOST` 상수, `:126` 에 `isNotHost`, `data/.../RemoteRideRepositoryTest.kt:276` 에 403 케이스 테스트가 있다. 주석에 `Backend #237` 로 출처까지 적혀 있다.

### 2.2 PR #241 (3커밋) — 방 상세의 방장이 `hostId` → `members[].host` 로 바뀌었다 → **앱 반영 완료**

`f9e66e5` 가 `PartyDetailResult.hostId`(내부 Long) 를 지우고 `MemberInfo` 에 `host`(boolean) 를 넣었다. `930fc6a` 가 판별을 `Party.isHost` 하나로 일원화.

**앱 영향**: 방장 표시·완료 버튼 게이트의 유일한 근거.
**확인 결과 — 반영됨.** `data/.../dto/PartyDtos.kt:129` 에 `val host: Boolean? = null`, `PartyMappers.kt:101` 에 `isHost = host ?: isMe` 폴백. DTO 주석(`PartyDtos.kt:119-121`)이 **"JSON 키는 `isHost` 가 아니라 `host` 다"** 라는 함정까지 못 박아 뒀다. 이게 이슈 #240 대응이고 1015 스윕 A1 의 "완주 확인" 항목 — **완주됐다**.

### 2.3 PR #243 (2커밋) — `member-left` 에 `hostPublicId` → **앱 반영 완료**

**확인 결과 — 반영됨.** `data/src/test/.../PartySseEventTest.kt:260-287` 이 세 경우(값 있음 / null / 필드 누락)를 모두 테스트한다. 운영 Swagger 가 지시한 승계 규칙("받을 때마다 host 표시를 재지정, null 이거나 목록에 없으면 방 상세 재조회")도 주석에 적혀 있다 — 1015 스윕 A2 항목.

### 2.4 PR #244 (2커밋) — **앱 영향 0**

`fc7637d` 는 k6 스모크 테스트 스크립트 하나만 고친다(합승 종료를 방장 계정으로 호출 — #237 때문에 테스트가 깨졌던 것). 서버 코드·계약 변경 없음. **담당 배정이 없는 게 맞다.**

### 2.5 PR #253 안의 `e2f7014` — 스케줄러 스레드풀 (담당 C 에 들어가 있으나 채팅/결제가 아니다)

```java
@Bean(name = "taskScheduler")
public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) { return builder.build(); }
```
`spring.task.scheduling.pool.size: 4` 가 이제 실제로 먹는다. 영향 받는 `@Scheduled` 작업 7개:
`ChatReadBuffer`(5초) · `EventResubmitter`(5초) · `MatchingSweeper`(30초, taxi 전용) · `DriverRetentionSweeper`(매일 04:10) · `CompletedPartySweeper`(10분, taxi=false 전용) · `UserDeletionSweeper`(매일 04:00) · `PartySseRegistry` heartbeat(별도 스케줄러).

**앱 영향**: 간접적이지만 실재한다. 이전에는 이 작업들이 **한 스레드에 직렬**로 몰릴 수 있었다 → 운영(taxi=false)에서 `CompletedPartySweeper`(10분마다 방 정리)와 `ChatReadBuffer`(5초마다 읽음 플러시)가 서로를 밀어낼 수 있었다. 지금은 4개 병렬. **읽음 처리·방 자동정리 지연이 줄어드는 방향**이다. 앱 코드 변경은 필요 없다.
**담당 C 에게**: 이 커밋은 즐겨찾기·채팅·결제가 아니다. 그냥 "앱 변경 불필요"로 닫으면 된다.

### 2.6 PR #238 안의 `cdc39da`·`3b1d6ed` — C 에게 명시되지 않은 #238 의 본체 ★

오케스트레이터가 C 에게 준 #238 커밋은 `8f54ad4`·`8e7a920`·`2c7a9c5` 뿐이다. 그런데 **삭제 API 3종과 #231 수정이 `cdc39da`·`3b1d6ed` 에 있다.**

운영에 실제로 떠 있는 것(§4 실측):
```
DELETE /api/v1/users/me/favorite-places/{placeId}
POST   /api/v1/users/me/favorite-places/batch-delete   (body: {"placeIds":[Long,...]}, 1~100개)
DELETE /api/v1/users/me/favorite-places/all
```
그리고 **응답 계약이 바뀌었다**:
```java
- public record FavoritePlaceResponse(String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence)
+ public record FavoritePlaceResponse(Long placeId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence)
```

**앱 영향 — 아직 안 읽는다.**
- `data/.../dto/PlaceDtos.kt` 의 `FavoritePlaceListResponse.FavoritePlaceItem` 에 **`placeId` 가 없다**. 추가 필드라 파싱은 안 깨지지만(`ignoreUnknownKeys`), **`placeId` 없이는 삭제 API 를 부를 수 없다.**
- `data/.../PlaceApi.kt` 에는 `POST`·`GET` 두 개뿐 — 삭제 3종이 선언돼 있지 않다.
- 부수 효과: `3b1d6ed` 의 "복합키 생성자 추가"가 **이슈 #231(즐겨찾기 등록 500)의 수정**이다. #231 은 2026-10-17 05:19 에 CLOSED 됐고 운영에 올라갔다 → **앱의 기존 즐겨찾기 등록 UI 가 이제 성공한다**(1015 스윕 C1 해소). 개인정보 처리방침 제11조 1·2항(1015 스윕 A3)도 이제 "삭제 수단 있음"이 사실이 됐다.

### 2.7 PR #233 (13커밋) — 채팅 메시지 순번 `seq`/`lastSeq` → **앱이 전혀 안 읽는다** ★

`ebdb3ec`·`9e9557b` 가 DTO 3개 + 푸시 1개에 순번을 넣었다:
```java
ChatRoomResult(Long id, Long partyId, long lastSeq, ...)                 // GET /chat-rooms/me
ChatMessageResult(Long id, Long chatRoomId, Long seq, ...)               // 메시지 조회·전송·소켓
ChatRoomUserResult.LastMessage(Long id, Long seq, ...)                   // GET /chat-rooms/{id}
ChatMessageNotification(Long chatRoomId, Long messageId, Long seq, ...)  // FCM 푸시
```
나머지 11커밋은 **순서 보장 인프라**다: STOMP 세션 수신·송신 순서(`64be4fc`), Redis 수신 단일 스레드(`b418ab9`), 방별 단일 스레드 큐 `RoomOrderedExecutor`(`e6566c8`), 메시지 저장 시 채팅방 행 잠금(`9e9557b`), 큐 드롭 지표(`c43c80c`).

**앱 영향**:
- (a) **서버 쪽 동작 개선은 앱 변경 없이 그대로 받는다** — 같은 방 메시지 순서 역전이 서버 단계에서 막힌다.
- (b) **`seq`/`lastSeq` 는 앱 코드에 0건이다.** `grep -rn 'messageSeq|lastSeq|\bseq\b'` → `data/src/main`·`domain/src/main` 모두 0. 운영 OpenAPI 에는 `ChatRoomResult.lastSeq`, `ChatMessageResult.seq`, `LastMessage.seq` 가 확실히 있다(§4 실측).
- 지금 앱은 `lastReadMessageId`/`unreadCount` 기반으로 동작하고 **그건 여전히 응답에 있다** → 깨지지 않는다. `lastSeq` 는 안읽음 계산 경량화용(1015 스윕 B4, "나중에") 이므로 **1차 배포 차단 요소는 아니다.**
- 남는 질문(미확인): 푸시 payload 에 `seq` 가 늘었다 — 앱 FCM 핸들러가 **알 수 없는 data 키를 그냥 무시하는지** 코드로는 확인했으나(관련 파서 없음) 실기 검증은 안 했다.

### 2.8 PR #235 (11커밋) — 프로필 이미지 + Gender UNDISCLOSED → **쓰기 경로는 반영, 읽기 경로 일부 미반영**

| 변경 | 운영 확인 | 앱 상태 |
|---|---|---|
| `UserRegisterRequest.gender` enum 에 `UNDISCLOSED` | **있음** (`['MALE','FEMALE','UNDISCLOSED']`) | **반영됨** — `AuthDtos.kt:10-14`, `AuthMappersTest.kt:106/124` |
| `UpdateProfileRequest(nickname, imageKey)` — `imageUrl` 아님 | **있음** (`['nickname','imageKey']`) | **반영됨** — `UserDtos.kt:44`, `RemoteAuthRepository.kt:205` |
| `GET /api/v1/users/profile-images` → `{key,url}` | **있음** (`ProfileImageResponse`) | **미반영** — `UserDtos.kt:31` 주석에만 적혀 있고 **Retrofit 선언 없음** |
| `DELETE /api/v1/users/me/profile-image` (기본 이미지 복귀) | **있음** | **미반영** — Retrofit 선언 없음 |
| `MemberInfo.imageUrl` / `ChatRoomMemberResult.imageUrl` | **있음** | **반영됨** — `PartyMappers.kt:98`, `ChatMappers.kt:94` |
| `UserResponse(uuid, name, imageUrl)` — `GET /local/users/info` | **있음** | **미반영** — `UserProfileResponse` 는 `uuid`·`name` 2필드뿐. 추가 필드라 파싱은 안 깨짐 |
| `V17` 로 기존 `image_url` 비움 | 적용됨 | 코드 변경 불필요. **QA 공지 필요**(1015 스윕 A5) |
| `INVALID_PROFILE_IMAGE` = 400 `USER112` | 코드에 있음 | 주석에만 (`UserDtos.kt:31`) |

세 "미반영" 항목은 `_workspace/profile-image-plan.md` 가 **v1.1 로 미루라고 이미 결론**낸 것이다(1015 스윕 B1·B2). 의도적 연기이며 누락이 아니다.

### 2.9 미분류 33개 요약 — 실제로 아직 손이 안 간 것 **3건**

| | 항목 | PR | 담당 제안 | 1차 배포 차단? |
|---|---|---|---|---|
| **M1** | 즐겨찾기 **삭제 API 3종 미연동** + 목록 응답의 **`placeId` 미수용** | #238 | **C 에게 `cdc39da`·`3b1d6ed` 를 명시 추가** | 삭제 UI 를 1차에 넣을지의 제품 결정에 달림 |
| **M2** | 채팅 `seq`/`lastSeq` 미수용 (0건) | #233 | 미지정 → 신규 | **아니다** (현행 `lastReadMessageId` 방식 유효) |
| **M3** | `GET /users/profile-images` · `DELETE /me/profile-image` · `UserResponse.imageUrl` 미연동 | #235 | 미지정 → v1.1 로 이미 연기 | **아니다** (계획된 연기) |

나머지 30개(#237·#241·#243·#244 전부 + #233·#235 의 서버 내부 변경)는 **앱이 이미 흡수했거나 앱 변경이 필요 없다.**

---

## 3. develop vs main — prod 에 간 것 / 안 간 것

### 3.1 측정값

```
git rev-parse origin/main    71994e4  "Merge pull request #245 from SWM-Moyeota/develop"  (2026-10-23)
git rev-parse origin/develop 82f000f  "Merge pull request #257 ..."                       (2026-10-22)

git log --oneline origin/main..origin/develop | wc -l   →  0
git log --oneline origin/develop..origin/main | wc -l   →  59
git diff --stat origin/main origin/develop              →  (빈 출력)
```

### 3.2 표

| 구분 | 커밋 수 | 내용 | prod 반영 |
|---|---|---|---|
| **develop 에만 있음** | **0** | — | 해당 없음 |
| **main 에만 있음** | 59 | 전부 과거 릴리스 머지 커밋 (`Merge pull request #N from SWM-Moyeota/develop`, N=5·8·10·12·16·18·19·25·35·48·49·55·58·59·61·64·69·71·76·79·81·85·90·92·94·99·102·127·130·132·134·137·140·145·148·154·156·158·161·164·166·168·173·179·182·185·189·191·194·199·202·205·211·236·239·242·245) + 비-develop 2건(`af5a2d0` PR #14, `8324f01` Slack 알림 워크플로, 2026-08) | 머지 커밋 자체는 트리를 안 바꾼다 |
| **두 브랜치 트리** | — | **바이트 단위 동일** | — |

### 3.3 해석 — "앱이 지금 맞추면 안 되는 것"은 **없다**

main 이 `#245` 로 develop 을 통째로 흡수했기 때문에 `main ⊇ develop` 이고 트리까지 같다.
→ **조사 범위 71 커밋 전부가 prod 에 가는(= 이미 간) 코드다.**
→ "develop 에만 있어서 앱이 아직 맞추면 안 되는 변경"은 **0건**. A·B·C 가 자기 PR 을 앱에 반영하는 걸 미룰 이유는 **계약 미배포가 아니다**.

유일한 예외는 main 에 **없는** 것, 즉 **열린 PR** 들이다 (§5): 결제(#196·#252·#254·#255)·graceful shutdown(#258)·기사 도메인 리팩터(#259)·포트원 테스트(#247·#248). 이것들은 main 에도 develop 에도 없다 → **앱이 지금 맞추면 안 된다.**

---

## 4. 운영 계약 대조 — **실측 완료**

### 4.1 획득 경위

| 시도 | 결과 |
|---|---|
| 1차 `curl -s https://api.moyeota.co.kr/v3/api-docs` | **HTTP 503**, 162바이트 — 배포 중 |
| 2차 (수 분 후) | **HTTP 200**, **55,735바이트** — 획득 |

**배포 완료 증거 (추측 아님)**: 운영 api-docs 의 `GET /api/v1/matching/rooms/{partyId}/events` description 이 `origin/develop:src/main/java/.../matching/sse/PartySseController.java` 의 `@Operation(description=...)` 문자열과 **바이트 단위로 동일**하다. 그 문자열은 PR #257(`b8a308e`, develop 의 최신 커밋)이 만든 것이다 → **운영은 #245 = #257 포함 빌드다.**

보조 실측:
- `GET https://api.moyeota.co.kr/api/v1/config` → `{"taxiEnabled":false}` (인증 불필요, 실측)
- `GET /actuator/health` → 401 (외부 비공개)

### 4.2 운영 인벤토리

**43 경로 / 52 오퍼레이션 / 50 스키마.**

### 4.3 범위 내 신규 엔드포인트 — 6개 전부 운영에 **있다**

| 엔드포인트 | PR | 담당 | 운영 |
|---|---|---|---|
| `POST /api/v1/matching/rooms/realtime` | #256 | B | **있음** |
| `DELETE /api/v1/users/me/favorite-places/{placeId}` | #238 | C | **있음** |
| `POST /api/v1/users/me/favorite-places/batch-delete` | #238 | C | **있음** |
| `DELETE /api/v1/users/me/favorite-places/all` | #238 | C | **있음** |
| `GET /api/v1/users/profile-images` | #235 | 미분류 | **있음** |
| `DELETE /api/v1/users/me/profile-image` | #235 | 미분류 | **있음** |

### 4.4 범위 내 신규/변경 필드 — 운영 실측 대조

| 스키마 | 운영 실측 필드 | PR | 앱 수용 |
|---|---|---|---|
| `PartyDetailResult` | `..., taxiDriverId, `**`version`** · `hostId` **없음** | #251, #241 | `version` **0건** (A 담당) |
| `MemberInfo` | `publicId, nickname, imageUrl, badgeId, rideCount, `**`host`** | #241, #235 | **수용됨** |
| `ChatRoomResult` | `id, partyId, `**`lastSeq`**`, departure, destination, createdAt, status` | #233 | **0건** |
| `ChatMessageResult` | `id, chatRoomId, `**`seq`**`, publicId, content, type, createdAt, deleted` | #233 | **0건** |
| `LastMessage` | `id, `**`seq`**`, senderPublicId, content, type, createdAt` | #233 | **0건** |
| `ChatRoomMemberResult` | `imageUrl` 포함 | #235 | **수용됨** |
| `UserResponse` | `uuid, name, `**`imageUrl`** | #235 | 미수용 (파싱 안전) |
| `UpdateProfileRequest` | `nickname, `**`imageKey`** | #235 | **수용됨** |
| `FavoritePlaceResponse` | **`placeId`**`, placeName, roadName, latitude, longitude, placeSequence` | #238 | **미수용** → 삭제 불가 |
| `UserRegisterRequest.gender` | `['MALE','FEMALE','`**`UNDISCLOSED`**`']` | #235 | **수용됨** |
| `ProfileImageResponse` | `key, url` (신규 스키마) | #235 | 미수용 |
| `FavoritePlaceDeleteRequest` | `placeIds` (신규 스키마) | #238 | 미수용 |
| `RealTimeMatchingRequest` / `MatchCandidate` | 신규 스키마 2개 | #256 | 미수용 (B 담당) |

### 4.5 앱 ↔ 운영 엔드포인트 차집합

**앱이 부르는데 운영에 없는 것 — 1건 (변동 없음, 1015 스윕과 동일)**

| 호출 | 사실 |
|---|---|
| `GET api/v1/dispatch/rides/{partyId}` (`DispatchApi.getDriverLocation`) | 운영 43경로에 **없다**. `taxi.enabled=false` 라 `RideController` 등 `@ConditionalOnProperty` 빈이 아예 생성되지 않는다. `GET /api/v1/config` → `taxiEnabled:false` **실측 확인**. 택시 모드를 켜는 순간 404 가 된다. (외부에서 `GET /api/v1/dispatch/rides/1` 을 쳐 보면 401 이 온다 — 시큐리티 필터가 라우팅 전에 막아 404/401 구별이 안 된다. 그래서 **api-docs 를 근거로 삼았다**: api-docs 는 실행 중인 인스턴스에 등록된 컨트롤러만 담는다.) |

**운영에 있는데 앱이 안 부르는 것 — 11건**

범위 내 신규 6개(§4.3) 중 `realtime`·즐겨찾기 삭제 3종·프로필 이미지 2종 = **6건 전부**, 그리고 범위 밖에서부터 안 쓰던 5건:
`POST /auth/phone/check` · `POST /chat-rooms/{id}/location` · `POST,DELETE /chat-rooms/{id}/location/sharing` · `POST /chat-rooms/{id}/reports`.
(`DELETE /api/v1/users/me` 는 `@HTTP(method="DELETE", hasBody=true)` 로 선언돼 있어 **부르고 있다** — 단순 grep 으로는 안 잡힌다.)

### 4.6 과거 스냅샷 비교

`_workspace/` 에 `prod-contract-diff.md`·`prod-contract-diff-2.md`·`backend-sweep-1015.md` 가 있으나 **원본 `prod-openapi-*.json` 스냅샷 파일은 남아 있지 않다**(1015 스윕 부록이 "스크래치패드는 세션마다 사라진다"고 적어 둔 그대로다). 따라서 기계 diff 는 재현할 수 없고, 위 대조는 **오늘 실측 api-docs ↔ `origin/develop` 소스** 2자 비교다. 1015 스윕의 서술(§1.3·§2.1·§5)과는 모순이 없다.

### 4.7 미확인으로 남기는 것

| | 항목 | 왜 미확인인가 |
|---|---|---|
| **U1** | 운영에 `OSRM_BASE_URL` 이 설정돼 있는가 | 기본값이 `http://localhost:5000` 이다. 환경변수는 EC2 systemd 유닛에서 주입되고 `deploy.yml`·`scripts/start.sh`·`appspec.yml`·`infra/` 에 `OSRM` grep **0건** → 저장소만으로는 알 수 없다. 설정이 없으면 `POST /matching/rooms/realtime` 이 운영에서 실패한다. 엔드포인트 **존재**는 확인됐지만 **동작**은 미확인(인증 없이 치면 401). → **B 가 실기 검증 필요** |
| **U2** | 운영에 `PORTONE_WEBHOOK_SECRET` 이 설정돼 있는가 | 같은 이유. 기본값은 빈 문자열. 다만 결제 엔드포인트가 운영에 **하나도 없으므로**(`/payments*` 0건) 지금 영향은 없다 |
| **U3** | V20 `CREATE EXTENSION postgis` 가 운영 DB 에서 성공했는가 | Flyway 로그 접근 없음. 성공했다고 **추정할 근거**는 있다 — 실패하면 앱 기동이 멈추고 api-docs 가 200 을 못 줬을 것이다. 그래도 **직접 확인 못 했으므로 미확인** |
| **U4** | FCM 푸시 payload 에 늘어난 `seq` 키를 앱 핸들러가 무시하는가 | 코드상 `seq` 파서는 없다(0건). 실기 검증은 안 했다 |

---

## 5. 백엔드 열린 PR·이슈 중 프론트 관련

### 5.1 열린 PR 8건 — 전부 **아직 main·develop 양쪽에 없다** (= 앱이 지금 맞추면 안 된다)

| PR | 날짜 | 브랜치 | 제목 | 프론트 영향 |
|---|---|---|---|---|
| **#259** | 10-22 | `refactor/driver-review` | 기사 도메인 코드리뷰 반영, `restore`→`load` 개명 | 서버 내부 리팩터. 기사 API 는 `taxi.enabled=false` 로 운영에 없음 → **영향 없음** |
| **#258** | 10-22 | `fix/graceful-shutdown` | 단일 서버 배포에서 종료 시 남은 채팅 작업 마무리 | **간접 영향 — 눈여겨볼 것.** #233 이 채팅을 방별 단일 스레드 큐에 태웠으므로 배포 중 종료되면 큐에 남은 메시지가 유실될 수 있다. 머지 전까지 **배포 순간에 보낸 메시지가 사라질 수 있다** → 배포 직후 QA 에서 채팅 유실을 "앱 버그"로 오인하지 말 것 |
| **#255** | 10-21 | `feature/payment-remove-card` | 카드 삭제 API | 결제 4종(#196·#252·#254·#255)은 **운영 0건**. 앱 결제 화면 착수 금지 |
| **#254** | 10-21 | `feature/payment-refactor` | payment 패키지를 계층형으로 재배치 | 상동 |
| **#252** | 10-20 | `feature/payment-register-card` | 포트원 빌링키 카드 등록·기본 결제수단·내 카드 목록 | 상동. **담당 C("결제")가 기다리는 것이 바로 이것** — 아직 머지 안 됨 |
| **#248** | 10-16 | `feature/portone-sdk-test-setup` | 포트원 SDK 카드 등록 테스트 환경 | 테스트 환경. 영향 없음 |
| **#247** | 10-15 | `feature/portone-integration-test` | 포트원 API·웹훅 시크릿 검증 도구 | U2 와 연결. 영향 없음 |
| **#196** | 10-08 | `feature/payments` | 포트원 빌링키 기반 분할 결제 (2주 넘게 열림) | 상동 |

**정리**: 열린 PR 중 **프론트가 "기다리는" 것은 결제(#252 계열) 하나**이고, **프론트가 "주의해야 할" 것은 #258**(배포 중 채팅 유실)이다. 그 외에 우리가 모르던 프론트 영향 PR 은 **없다**.

### 5.2 열린 이슈 7건 — 전부 **우리가 올린 것**이고, 새로 발견된 것은 없다

| 이슈 | 날짜 | 제목 | 현재 상태 |
|---|---|---|---|
| **#232** | 10-14 | 동승자 프로필 `badgeId` 가 항상 null — 채울지 뺄지 결정 필요 | 열림. 운영 `MemberInfo.badgeId` 존재하나 값 null. 앱은 nullable 로 받고 있어 안 깨짐 |
| **#226** | 10-13 | 가입 시 만 14세 미만 서버 차단 — 앱만 막고 있어 API 직접 호출로 우회 | 열림. 1015 스윕 C5 와 동일 |
| **#222** | 10-12 | 같은 방 재합류가 `ALREADY_JOINED_OTHER_PARTY` 로 와서 구별 불가 | 열림. 범위 내 수정 없음 |
| **#220** | 10-12 | SSE heartbeat 30초가 CloudFront 오리진 타임아웃 30초와 겹침 | 열림. **단, #257(`b8a308e`)이 version heartbeat 를 제거하고 `:ping` 주석 30초로 정리했다** → A 가 이 이슈와 #257 의 관계를 확인할 필요가 있다(해소됐는지 / 여전한지). **미확인** |
| **#219** | 10-12 | 배차·운행 상태 전이에 SSE 이벤트 추가 — 25·26 화면이 폴링을 벗어날 수 없다 | 열림. **단, #257 이 "배차 실패·운행 완료에 `closed` 발행"을 넣었다** → 부분 해소 가능. 운영 Swagger 에 `closed: {partyId, status, version}` 확인됨. **A 영역, 미확인** |
| **#209** | 10-10 | `GET /local/users/info` 응답 확장 — imageUrl·연락처·가입일 | 열림. **`imageUrl` 만 들어왔다**(#235, 운영 `UserResponse` 실측 확인). 연락처·가입일은 여전히 없음 → 1015 스윕 C4(이슈에 코멘트 남기기) 아직 유효 |
| **#207** | 10-10 | 완료된 방 목록(내 탑승 기록) API — `GET /users/me/parties` | 열림. 운영 43경로에 없음. 1015 스윕 §7 이 "구현이 로컬 커밋 `9e1be5e` 에 있었고 reset 으로 버려졌다"고 기록 |

### 5.3 우리가 올린 이슈 중 이 범위에서 **닫힌** 것 5건 (gh 실측)

| 이슈 | 닫힌 시각 | 무엇이 닫았나 |
|---|---|---|
| **#234** Gender 에 NONE | 10-15 01:28 | `a1694ee` → `UNDISCLOSED` (NONE 이 아님) |
| **#227** 가입 성별을 선택 항목으로 | 10-15 03:56 | 상동 |
| **#240** `MemberInfo.isHost` (hostId 가 내부 PK) | 10-15 05:54 | `f9e66e5`+`930fc6a` (#241) → JSON 키는 **`host`** |
| **#231** 즐겨찾기 등록 500 | **10-17 05:19** | **`3b1d6ed`**(#238, 미명시 커밋) → 복합키 생성자. **1015 스윕 C1 이 해소됐다** |
| **#208** 즐겨찾기 삭제 API(+별칭 수정) | **10-17 05:19** | **`cdc39da`+`3b1d6ed`**(#238, 미명시 커밋). 단 **별칭(placeName) 수정 API 는 들어오지 않았다** — 운영에 `PATCH/PUT favorite-places/*` 0건. 이슈가 닫혔지만 **범위의 절반만 구현됐다** ★ |

---

## 6. A·B·C 에게 넘겨야 할 것 (중복 착수 방지)

| | 대상 | 내용 |
|---|---|---|
| **H1** | **C** | **#238 커밋 `cdc39da`·`3b1d6ed` 를 명시 추가.** 삭제 API 3종 + `FavoritePlaceResponse.placeId` + 새 에러코드 3개 + `FavoritePlaceDeleteRequest`가 여기 있다. 받은 3개 커밋만 보면 #238 의 본체를 놓친다 (§2.6) |
| **H2** | **C** | `e2f7014`(스케줄러 스레드풀)는 즐겨찾기·채팅·결제가 아니다 → **앱 변경 불필요**로 닫으면 된다 (§2.5) |
| **H3** | **A** | 열린 이슈 **#219·#220 이 #257 로 해소됐는지** 확인. `closed` 이벤트와 `:ping` 30초 정리가 각각에 대응한다. **미확인** (§5.2) |
| **H4** | **B** | **U1: 운영 `OSRM_BASE_URL` 미확인.** 기본값 `localhost:5000`. 저장소에 주입 흔적 0건. 엔드포인트는 운영에 있지만 **동작은 검증되지 않았다** (§4.7) |
| **H5** | 신규 | **M2: 채팅 `seq`/`lastSeq` 가 앱에 0건** (§2.7). 1차 배포 차단은 아님 |
| **H6** | 전원 | **#258(graceful shutdown) 미머지** — 배포 순간 채팅 메시지 유실 가능. QA 때 앱 버그로 오인 금지 (§5.1) |
| **H7** | 문서 | **#208 이 닫혔지만 "별칭 수정"은 구현 안 됐다** (§5.3). 필요하면 별도 이슈로 다시 올릴 것 |
| **H8** | QA | **V17 로 기존 프로필 이미지가 비워졌다** — 수동으로 `imageUrl` 넣은 테스트 계정은 기본 이미지로 보인다 (1015 스윕 A5, 여전히 유효) |

---

## 부록 — 재현 명령 (전부 읽기 전용, 백엔드 작업 트리 미변경)

```bash
B=/Users/sungyoon/Desktop/moyeota/backend

# 1. 전수 목록
git -C $B log --date=short --pretty=format:'%h|%ad|%s' 284a950..origin/develop   # 71줄

# 2. PR 분해 (각 머지의 2번째 부모 쪽) — 합계 71 검증
for m in 82f000f 880b1fc 16319ae 0f86d38 8ec44c4 c109b33 5161326 \
         4abc8e0 d9c17a4 de193d9 b4ba392 b8e4750 739b78d f130acd; do
  echo "--- $m $(git -C $B log -1 --pretty=%s $m)"
  git -C $B log --oneline ${m}^1..${m}^2
done

# 3. develop vs main
git -C $B log --oneline origin/main..origin/develop | wc -l    # 0
git -C $B log --oneline origin/develop..origin/main | wc -l    # 59
git -C $B diff --stat origin/main origin/develop               # 빈 출력

# 4. 설정·마이그레이션·의존성
git -C $B diff 284a950..origin/develop -- src/main/resources/application.yaml
git -C $B diff --stat 284a950..origin/develop -- src/main/resources/application-prod.yaml  # 빈 출력
git -C $B diff 284a950..origin/develop -- build.gradle settings.gradle gradle/              # 빈 출력
git -C $B log --name-status 284a950..origin/develop -- 'src/main/resources/db/migration/*'
git -C $B grep -n ConditionalOnProperty origin/develop -- 'src/main/**'

# 5. 삭제된 엔드포인트 검출
git -C $B diff -U0 284a950..origin/develop -- 'src/main/java/**' \
  | grep -E '^[+-].*(Get|Post|Put|Patch|Delete|Request)Mapping'

# 6. 운영 실측
curl -s https://api.moyeota.co.kr/v3/api-docs -o /tmp/apidocs.json -w '%{http_code}\n'
curl -s https://api.moyeota.co.kr/api/v1/config      # {"taxiEnabled":false}
python3 -I -c "import json;d=json.load(open('/tmp/apidocs.json'));print(len(d['paths']),len(d['components']['schemas']))"
# 배포 동일성 증거: /matching/rooms/{partyId}/events 의 description 을
#   git show origin/develop:src/main/java/team/codingforest/moyeota/matching/sse/PartySseController.java
# 의 @Operation description 과 대조

# 7. 앱 수용 여부
F=/Users/sungyoon/Desktop/moyeota/frontend
grep -rn --include='*.kt' -E 'lastSeq|messageSeq' $F/data/src/main $F/domain/src/main   # 0건
grep -rn --include='*.kt' 'placeId' $F/data/src/main $F/domain/src/main                 # 0건
grep -rn --include='*.kt' 'version' $F/data/src/main $F/domain/src/main                 # 0건
grep -rhn --include='*.kt' -E '@(GET|POST|PUT|PATCH|DELETE)\(' $F/data/src/main \
  | sed -E 's/.*@(GET|POST|PUT|PATCH|DELETE)\("([^"]*)".*/\1 \2/' | sort -u            # 앱 엔드포인트 40

# 8. gh
gh pr list    --repo SWM-Moyeota/Backend --state open  --limit 60
gh issue list --repo SWM-Moyeota/Backend --state open  --limit 80
for n in 231 208 234 240 227; do gh issue view $n --repo SWM-Moyeota/Backend --json state,closedAt; done
```
