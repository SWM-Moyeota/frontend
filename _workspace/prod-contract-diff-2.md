# 운영 재배포(#236) ↔ 앱 계약 재대조 — 응답 모양 변화 중심

- 일시: 2026-10-15
- 앱: `/Users/sungyoon/Desktop/moyeota/frontend` (branch `fix/place-roadname-null`, **HEAD `249f33f`**)
  - 세션 시작 스냅샷의 `5fc8da4` 보다 한 커밋 앞서 있다. `249f33f` 가 성별 노출 게이트를 켠 커밋이다(§3-6).
- 운영: `https://api.moyeota.p-e.kr/` — 배포본 OpenAPI `prod-openapi-2.json`(39 경로) vs 직전 `prod-openapi.json`(37 경로)
- 서버 근거: 백엔드 `origin/main` = `5d8889a`. 직전 운영본은 `284a950`(#211). **대조 범위 = `284a950..5d8889a`, 25 커밋.**
- 운영 서버에 쓰기 요청 0건. 코드 수정 0건. 커밋·브랜치 변경 0건.

## 0. 한 줄 결론

**출시 차단 0건.** 이번 배포로 바뀐 응답 필드는 전부 **추가(additive)** 이고, 앱의 **모든** 디코더가
`ignoreUnknownKeys = true` 라 흘려보낸다. `imageUrl` 이 non-null 로 바뀐 것에 의존하는 앱 코드는
**프로덕션 코드에 한 줄도 없다**(반증을 찾지 못했다 — 근거는 §2). 고쳐야 할 것은 **서버 사실과 어긋나게
된 앱 쪽 주석·DTO 필드명 2건**뿐이다.

### ⚠ 전제 1건 수정: 이번 배포는 #235 **하나가 아니라 둘**이다

지시문은 #235(프로필 이미지 + 성별)만 예고했으나, `284a950..5d8889a` 에는 **PR #233
`feature/chat-message-order`(채팅 메시지 방별 순번)** 도 함께 들어왔다. 직전 스펙에 `seq` 가 없고
지금 있으므로 **#233 은 이번 배포로 처음 운영에 올라간 것**이다. 지시문이 예고하지 않은 변경이라
§3-3 에 따로 적었다.

```
$ git log --oneline --merges 284a950..5d8889a
5d8889a Merge pull request #236 from SWM-Moyeota/develop
739b78d Merge pull request #235 from SWM-Moyeota/feature/profile-image
f130acd Merge pull request #233 from SWM-Moyeota/feature/chat-message-order   ← 예고 밖
```

---

## 1. 두 스펙 JSON 기계 대조 (사람 눈 아님)

스크립트: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/specdiff.py` — `components.schemas` 와 `paths` 를 **말단 키까지 전개**해
(`/Schema/properties/x/type` 꼴) 세 집합(CHANGED / REMOVED / ADDED)으로 나눈다. 원시값 리스트
(`enum`, `required`, `tags`)는 정렬해 순서 차이를 무시하고, 딕셔너리 리스트(`parameters` 등)는
인덱스로 전개해 순서 변화도 잡는다. 출력 전문은 `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/specdiff.txt`.

**스키마 변경 총계: CHANGED 1 · REMOVED 5 · ADDED 13. 경로 변경 총계: CHANGED 2 · REMOVED 0 · ADDED 16.**
아래가 **전부**다 — 나열에서 빠진 필드는 바이트 단위로 동일하다.

### 1-1. 스키마

| # | 스키마 | 변화 | 비고 |
|---|---|---|---|
| S1 | `UserRegisterRequest.gender.enum` | `[FEMALE,MALE]` → `[FEMALE,MALE,UNDISCLOSED]` | 기지(旣知). §3-6 |
| S2 | `UserResponse.imageUrl` | **추가** (`string`) | 예고 ①② → §3-1 |
| S3 | `UpdateProfileRequest.imageUrl` | **삭제** (5개 말단 키) | 예고 ④ → §3-4 / **F-1** |
| S4 | `UpdateProfileRequest.imageKey` | **추가** (`string`) | 예고 ④ |
| S5 | `ProfileImageResponse` | **신규 스키마** `{key, url}` | §4 |
| S6 | `ChatMessageResult.seq` | **추가** (`int64`) | **예고 밖** → §3-3 |
| S7 | `ChatRoomResult.lastSeq` | **추가** (`int64`) | **예고 밖** → §3-3 |
| S8 | `LastMessage.seq` | **추가** (`int64`) | **예고 밖** → §3-3 |

스키마 집합: 45 → 46 (`ProfileImageResponse` 1개 추가, 삭제 0).

### 1-2. 경로

- **CHANGED 2건 — 둘 다 `PATCH /api/v1/users/me` 의 설명 문자열뿐이다**(요청/응답 구조 아님):
  - `description`: `nickname·imageUrl 중 …` → `nickname·imageKey 중 … imageKey 는 프로필 이미지 목록의 key 만 허용 …`
  - `responses.400.description`: `INVALID_NICKNAME(USER107)` → `… , INVALID_PROFILE_IMAGE(USER112)`
- **ADDED 16건 = 신규 경로 2개의 전체 말단 키.** 기존 37 경로의 **파라미터·요청 본문·응답 스키마 참조·상태코드 집합은 전부 불변.**
- **REMOVED 0건.** 사라진 경로·사라진 응답 필드·사라진 파라미터 없음.

### 1-3. 이 대조의 한계 (중요)

**스펙은 nullability 를 전혀 표현하지 않는다.** springdoc 이 Java `record` 의 `String` 필드에
`nullable` 을 붙이지 않아, 두 스펙 모두 `nullable` 키가 0개다. 그래서 **예고 ③(`imageUrl` 이 null 로
안 나감)은 스펙 diff 에 나타날 수 없다** — `MemberInfo` 스키마는 바이트 단위로 동일하다. ③의 판정은
전부 `origin/main` 소스 기준이다(§2).

또한 **지시문의 `MemberSummary` 는 운영 스펙에 존재하지 않는 이름이다.** 서버 내부 모듈 간 DTO
(`user/api/MemberSummary.java:5`)이고, 밖으로 나가는 이름은 방 상세의 **`MemberInfo`** 와 채팅의
**`ChatRoomMemberResult`** 다. ③을 확인할 때 이 두 개를 봤다.

---

## 2. 예고 ③ 반증 추적 — `imageUrl` null 의존 코드를 찾았는가: **없다**

### 2-1. 서버가 실제로 그렇게 바뀌었나: **그렇다. 단 한 줄이다**

```
# git diff 284a950 origin/main -- .../user/users/application/UserAccessService.java
-  new MemberSummary(user.getPublicId(), user.displayNickname(), user.getImageUrl(),              badgeId)
+  new MemberSummary(user.getPublicId(), user.displayNickname(), profileImages.toUrl(user.getImageUrl()), badgeId)
```
`UserAccessService.java:36`. 그리고 `ProfileImages.java:24-25`

```java
public String toUrl(String key) {
    return properties.baseUrl() + (key == null ? properties.defaultKey() : key);
}
```

→ **`toUrl` 은 구조적으로 null 을 반환할 수 없다.** 저장값이 null 이면 `defaultKey` 가 붙는다.
`MemberSummary` 를 소비하는 곳이 정확히 둘이고, 둘 다 이 값을 그대로 흘린다:

| 경유 | 나가는 필드 | 영향 받는 응답 |
|---|---|---|
| `matching/party/dto/PartyDetailResult.java:26` | `MemberInfo.imageUrl` | `GET /matching/rooms/{id}`, `POST .../join`, SSE `connected`·`member-joined` |
| `chat/member/infrastructure/UserMemberProvider.java:31` | `ChatRoomMemberResult.imageUrl` | `GET /chat-rooms/{id}/users`, WS `/members` 목적지 |

### 2-2. 그러나 "null 이 절대 안 나온다"는 **과장이다** — 접기 경로는 그대로 살아 있다

`PartyDetailResult.java:23-27` 의 접기 로직은 **이번 배포에서 손대지 않았다**:

```java
public static MemberInfo of(MemberSummary summary, int rideCount) {
    if(summary == null) return new MemberInfo(null, null, null, null, rideCount);   // :24
    return new MemberInfo(summary.publicId(), summary.nickname(), summary.imageUrl(), null, rideCount);
}
```

`findMemberSummaries` 는 `findAllByIds` 가 돌려준 행에만 엔트리를 넣으므로, 행이 없으면
`summaries.get(memberId)` 가 null 이고 **`imageUrl` 을 포함해 4개 필드가 여전히 전부 null 로 나간다.**
즉 정확한 서버 사실은 "**null 이 안 나간다**"가 아니라 "**유저 행이 있으면 non-null 전체 URL, 없으면
(접기) 여전히 null**"이다. 이 구분이 §2-3 의 판정을 좌우한다.

### 2-3. 앱이 `imageUrl == null` 에 의존하는가: **의존하지 않는다**

지시문이 지목한 두 지점을 직접 읽었다.

**(a) 접기 판정은 `publicId` 로 한다 — `imageUrl` 과 무관하다.**
- `data/.../remote/PartyMappers.kt:67-75` — `id = publicId ?: ""`, `nickname = nickname ?: "탈퇴한 회원"`,
  `imageUrl = imageUrl`(**가공 없는 통과**), `isMe = publicId != null && publicId == currentUuid`
- `presentation/.../core/MemberDisplay.kt:33-34` — `val User.isWithdrawn get() = id.isBlank()`
  → 트리거는 **`publicId == null`** 이고, 그 경우 서버는 지금도 `imageUrl` 도 null 로 보낸다(§2-2).
  **imageUrl 이 non-null 로 바뀌어도 `isWithdrawn` 판정은 바뀌지 않는다.**
- `MemberDisplay.kt:38` `displayNickname` 도 `isWithdrawn || nickname.isBlank()` 만 본다.

**(b) 아바타는 `imageUrl` 을 읽지조차 않는다.**
- `MemberDisplay.kt:62-67` `MemberAvatar(user, …)` 의 분기는 `user.isWithdrawn || user.nickname.isBlank()`
  뿐이고 본문에 `user.imageUrl` 참조가 없다(`:57` 주석이 이유를 적어 뒀다 — Coil 미도입).

**(c) 전수 grep — 프로덕션 코드에서 `imageUrl` 을 **읽는** 곳은 통과 매퍼 2개뿐이다.**
```
$ grep -rn --include="*.kt" imageUrl presentation/src/main app/src/main
presentation/.../core/MemberDisplay.kt:28           ← 주석
presentation/.../core/MemberDisplay.kt:57           ← 주석
presentation/.../mypage/ProfileEditScreen.kt:43     ← 주석
presentation/.../mypage/ProfileEditRoute.kt:162     ← 주석
presentation/.../mypage/ProfileEditRoute.kt:174     ← 요청 측(항상 null)
```
→ **presentation·app 모듈에 `imageUrl` 을 읽는 실행 코드가 0줄.** data 모듈의 두 지점
(`PartyMappers.kt:73`, `ChatMappers.kt:94`)은 가공 없는 통과다. 비교·null 병합(`?:`)·`takeIf`·
`isNullOrBlank` 같은 분기가 **한 곳도 없다.**

**(d) 동일성 비교로 새는 경로도 없다.** 멤버 리스트를 `distinctBy`/`associateBy` 하는 곳은
`RemoteChatRepository.kt:259`(`associateBy { it.publicId }`)와 `ChatRoute.kt:423,471,535,622`
(`distinctBy { it.id }` — **메시지** 대상)뿐이다. `User`/`ChatMember` 의 data class 전체 동일성으로
비교하는 코드는 없으므로, `imageUrl` 값이 바뀌어도 중복 제거·병합 결과가 달라지지 않는다.

**결론: 예고 ③은 서버 쪽 사실로는 맞고(단 §2-2 의 단서 필요), 앱에는 영향이 없다. 반증 없음.**

---

## 3. 예고 4건 + 예고 밖 1건 개별 판정

### 3-1. 예고 ① `GET /api/v1/local/users/info` 에 `imageUrl` 추가 — **사실 · 무해**

- 스펙: `UserResponse.imageUrl: string` 추가(S2). 해당 경로의 200 이 `$ref: UserResponse`.
- 서버: `user/local/dto/UserResponse.java:5` `record UserResponse(UUID uuid, String name, String imageUrl)`,
  주입은 `user/local/application/LocalUserService.java:85` `profileImages.toUrl(user.getImageUrl())` — non-null.
- 앱: `data/.../remote/dto/UserDtos.kt:20-24` `UserProfileResponse(uuid, name)` — `imageUrl` 미선언.
  `NetworkModule.kt:24` 의 `Json { ignoreUnknownKeys = true }` 가 흘린다.
- 소비 경로도 영향 없음: `RemoteAuthRepository.kt:213-219` 가 `uuid` 공백만 검사하고 `name` 만 쓴다.

### 3-2. 예고 ② 회원가입 응답에 `imageUrl` 추가 — **사실 · 무해**

- 스펙: `POST /api/v1/auth/register` 의 201 이 **같은 `UserResponse`** 를 참조한다(경로 쪽 diff 0건이라
  변화가 S2 하나로 공유된다). 서버 `LocalUserService.java:66` 가 같은 `toUrl` 을 쓴다.
- 앱: `data/.../remote/dto/AuthDtos.kt:78-80` `RegisterResponse(uuid)` — `name` 도 `imageUrl` 도 미선언.
  둘 다 `ignoreUnknownKeys` 로 흘러간다.

### 3-3. 🔎 **예고 밖** — PR #233: 채팅 응답 3곳에 순번 필드 추가 — **무해(검증 완료)**

이번 배포의 숨은 절반이다. 지시문에 없었으므로 특히 깐깐히 봤다.

| 응답 | 추가 필드 | 서버 근거 | 앱 DTO | 결과 |
|---|---|---|---|---|
| 채팅 메시지(목록·조회·**WS 브로드캐스트**) | `seq: Long` | `chat/message/dto/ChatMessageResult.java:12` | `ChatDtos.kt` `ChatMessageResponse` 미선언 | 흘림 |
| 채팅방 정보 | `lastSeq: long`(**primitive → 항상 직렬화**) | `chat/room/dto/ChatRoomResult.java:11` | `ChatDtos.kt` `ChatRoomResponse` 미선언 | 흘림 |
| 채팅방 목록의 마지막 메시지 | `seq: Long` | `chat/member/dto/ChatRoomUserResult.java:27` | `ChatLastMessageResponse` 미선언 | 흘림 |
| FCM `CHAT_MESSAGE` data | `seq` 키 | `chat/notification/infrastructure/FcmChatNotifier.java:50` | — | 흘림 |

**이 변경이 차단 등급이 될 수 있었던 유일한 경로를 직접 확인했다.** `seq` 는 REST 응답만이 아니라
**WebSocket 브로드캐스트 본문**(`ChatRedisPublisher.java:26-28` 이 `ChatMessageResult` 를 그대로 직렬화)에도
실린다. 앱의 STOMP 수신은 Retrofit 과 **다른 디코더**를 쓰므로, 그 디코더에 `ignoreUnknownKeys` 가
없으면 **배포 즉시 실시간 채팅 메시지가 전부 파싱 실패**했을 것이다. 전수 확인 결과 앱의 `Json` 인스턴스
**8개가 전부** 해당 옵션을 켜 두고 있다:

```
data/.../remote/NetworkModule.kt:24          (Retrofit 본체)
data/.../remote/chat/ChatSocket.kt:51        (WebSocket/STOMP)   ← 결정적
data/.../remote/matching/PartySseEvent.kt:54 (SSE)
data/.../remote/{Party,Chat,Auth,Place}Mappers.kt (errorJson ×4)
data/.../remote/auth/JwtSubject.kt:14
```

**커서·정렬 의미는 바뀌지 않았다**(가장 중요한 확인). 서버 페이지네이션은 여전히 **`id` 기준**이다 —
`chat/message/infrastructure/ChatMessageJpaRepository.java:15` `findByChatRoomIdAndIdLessThanOrderByIdDesc`,
`:17` `…AndIdGreaterThanOrderByIdAsc`, `:24-25` `AND (:cursor IS NULL OR m.id < :cursor) ORDER BY m.id DESC`.
`seq` 는 저장·표시용이고 조회 조건에 쓰이지 않는다. 앱도 `ChatRoute.kt:423,471,535` 에서
`distinctBy { it.id }.sortedBy { it.id }` 로 **id 기준 병합**을 유지하므로 양쪽이 일치한다.

부수 변경 2건도 앱에 닿지 않는다:
- `ChatMessageBroadcaster.java:34-62` 가 방별 단일 스레드 큐(`RoomOrderedExecutor`)로 발행 순서를 보장하도록
  바뀌었다 — 와이어 포맷 변화 없음(순서 품질 개선이라 앱에 이득).
- `ChatRedisPublisher.java:30-41` 의 `memberJoined/memberLeft` 시그니처가 바뀌었으나 전송되는
  `MemberChangedPayload` 필드 집합은 동일(`:56-62`)하고, 목적지는 앱이 구독하지 않는
  `/sub/chat-rooms/{id}/members` 다(직전 보고서 §H-7 에 기록된 기능 공백 그대로).

### 3-4. 예고 ④ `PATCH /api/v1/users/me` 요청이 `imageUrl` → `imageKey` — **사실 · 지금은 무해, 그러나 F-1**

- 스펙: S3(삭제) + S4(추가). 서버 `user/users/dto/UpdateProfileRequest.java:13` 에 `imageKey` 만 있다.
- 서버는 **키 화이트리스트 검증**을 추가했다: `UserService.java:33-34`
  `if(imageKey != null && !profileImages.isPreset(imageKey)) throw INVALID_PROFILE_IMAGE` → 400 `USER112`
  (`UserErrorCode.java:24`). 즉 임의 URL·외부 주소는 저장되지 않는다.
- 앱은 아직 `imageUrl` 을 보낸다 → **F-1**(§5).

### 3-5. V16/V17 마이그레이션 — **무해**

- `V16__user_profile_gender_undisclosed.sql` — `user_profile.gender` CHECK 를 3값으로 교체. §3-6 의 전제.
- `V17__users_image_url_to_key.sql` — `users.image_url` 이 `profile/%` 로 시작하지 않으면 NULL 로 비운다.
  **이 앱 사용자에게는 no-op 이다**: 앱은 `PATCH /users/me` 에 `imageUrl` 을 한 번도 실어 보낸 적이 없고
  (`ProfileEditRoute.kt:174` 가 항상 `null`), 그 외에 이 컬럼을 채우는 경로가 없어 전부 이미 NULL 이다.
  NULL 행은 `toUrl` 이 기본 이미지로 바꿔 주므로 응답도 정상이다.

### 3-6. 성별 `UNDISCLOSED` — **이미 반영 완료 · 조치 불필요**

세션 스냅샷 이후 커밋 **`249f33f`**("성별 「선택 안 함」 노출을 켠다")가 들어와 있다.
`presentation/.../auth/SignupFieldSupport.kt:177` 이 현재 `ServerAcceptsUndisclosedGender = true` 이고
(`5fc8da4` 시점에는 `false` 였다), `SignupGenderChoices`(`:194-198`)가 세 번째 칩을 노출한다.
운영 스펙의 `UserRegisterRequest.gender` 가 `[FEMALE,MALE,UNDISCLOSED]`(S1)이고 서버
`Gender.java` 가 3상수이므로 **앱 상태가 운영과 일치**한다. `git status` 클린.

---

## 4. 신규 엔드포인트 2개 — 요청/응답 모양 (프로필 이미지 검토 담당에게 넘길 재료)

앱은 아직 둘 다 호출하지 않는다. 아래는 스펙 + `origin/main` 소스 + **CDN 실측**으로 확정한 것이다.

### 4-1. `GET /api/v1/users/profile-images` — 고를 수 있는 이미지 목록

- 인증: **Bearer 필수**(`SecurityConfig` permitAll 에 `/api/v1/users/**` 없음; 이번 배포에서 `SecurityConfig` 무변경).
- 요청: 파라미터·본문 없음.
- 200: **배열**(래퍼 객체 아님) `List<ProfileImageResponse>` — `ProfileImageResponse.java:3` `record (String key, String url)`
  ```json
  [{"key":"profile/presets/01.png","url":"https://cdn.moyeota.co.kr/profile/presets/01.png"},
   {"key":"profile/presets/02.png","url":"https://cdn.moyeota.co.kr/profile/presets/02.png"}]
  ```
  - 스펙의 content-type 이 `*/*` 인 것은 springdoc 기본값이고 실제는 `application/json` 이다.
  - **항목 수 16개, `01.png`~`16.png`**, `config/profile-images.yaml` 의 **yaml 순서가 곧 표시 순서**다
    (`UserController.java:48` 설명이 "앱은 이 순서대로 보여주고"라고 못박았다).
  - `key`·`url` 모두 non-null (`ProfileImages.java:28-31` 이 presets 목록에서 직접 만든다).
  - **`profile/presets/default.png` 는 이 목록에 없다** — 고를 수 없고, 저장값이 null 인 사용자에게
    `toUrl` 이 대신 끼워 주는 값일 뿐이다(`ProfileImages.java:25`, `profile-images.yaml` `default-key`).
- 401: `ErrorResponse{code,message}` — USER005 / USER002 / USER001.
- base URL: `application.yaml:115` `base-url: ${PROFILE_IMAGE_BASE_URL:https://cdn.moyeota.co.kr/}`.
  `application-prod.yaml` 에 오버라이드가 **없다**(grep 0건) → 환경변수 미설정이면 기본값이 쓰인다.
- **실측(읽기 전용 HEAD, 운영 API 아님 · CDN)**: `cdn.moyeota.co.kr` 가 4개 A 레코드로 해석되고,
  `profile/presets/01.png`~`16.png` + `default.png` 전부 `200 image/png`. 목록 밖 키
  (`99.png`, `nonexistent.png`)는 `403` — 캐치올 200 이 아닌 **실제 오브젝트 스토어**임이 확인된다.
  즉 **16개 이미지가 실제로 존재한다.**

### 4-2. `DELETE /api/v1/users/me/profile-image` — 기본 이미지로 되돌리기

- 인증: Bearer 필수. **요청 본문 없음**(`UserController.java:57-61` — `DELETE /users/me`(회원 탈퇴)와 달리
  `@HTTP(hasBody=true)` 가 **필요 없다**. Retrofit 의 평범한 `@DELETE` 로 선언하면 된다).
- 204, 본문 없음. **멱등**(`UserService.java:59-61` → `User.resetImage()` 가 무조건 null 로 덮는다).
- 400/409 없음. 401 만 문서화돼 있다.
- 효과: 이후 모든 응답의 `imageUrl` 이 기본 이미지 전체 URL 이 된다(null 이 아니다).

### 4-3. 앱이 프로필 이미지를 붙일 때의 계약 요점 (3줄)

1. 목록의 **`key`** 를 그대로 `PATCH /api/v1/users/me` 의 **`imageKey`** 로 보낸다. 목록 밖 값은 400 `USER112`.
2. 표시용 전체 URL 은 **직접 조립하지 말고** 서버가 주는 `url`/`imageUrl` 을 쓴다(baseUrl 이 환경변수라 앱이 추측하면 깨진다).
3. 해제는 `imageKey: null` 이 아니라 **`DELETE /users/me/profile-image`** 다. `null` 은 "유지"를 뜻한다.

---

## 5. 등급별 불일치 목록

### 🔴 출시 차단 — **0건**

### 🟡 고쳐야 함 — 2건 (둘 다 현재 동작 영향 0, 다음 변경에서 함정이 되는 것)

#### [F-1] 앱 요청 DTO 의 `imageUrl` 이 서버에서 **사라진 필드**다 — 프로필 이미지를 붙이는 순간 "조용한 성공"이 된다

- **앱**
  - `data/src/main/kotlin/com/moyeota/data/remote/dto/UserDtos.kt:38` — `val imageUrl: String? = null`
  - `data/src/main/kotlin/com/moyeota/data/remote/dto/UserDtos.kt:27` — KDoc 이 **지금 틀린 사실**을 명시:
    "백엔드 `UpdateProfileRequest(String nickname, String imageUrl)`"
  - `domain/src/main/kotlin/com/moyeota/domain/repository/AuthRepository.kt:130` — `updateProfile(nickname, imageUrl)`
  - `data/src/main/kotlin/com/moyeota/data/repository/RemoteAuthRepository.kt:204-205`
  - `presentation/src/main/kotlin/com/moyeota/presentation/feature/mypage/ProfileEditRoute.kt:174` — 항상 `imageUrl = null`
- **서버 근거**: `user/users/dto/UpdateProfileRequest.java:13` 에 `imageKey` 만 존재(`imageUrl` 없음).
  `UserController.java:45` → `userService.update(userId, request.nickname(), request.imageKey())`.
  스펙 diff S3(`imageUrl` 말단 키 5개 REMOVED) + S4(`imageKey` ADDED).
- **왜 지금 안 터지나(2중)**: ① 앱이 항상 `null` 을 넘기고, kotlinx `Json` 기본값 `encodeDefaults = false` +
  필드 기본값 `null` 이라 **키 자체가 본문에서 빠진다**(`UserDtos.kt:29` 가 이 메커니즘을 정확히 적어 뒀다).
  ② 설령 실려 나가도 Spring 기본 `FAIL_ON_UNKNOWN_PROPERTIES=false` 가 무시한다.
- **재현 경로**: 17 프로필 수정 화면에 이미지 선택을 붙이고 `repository.updateProfile(nickname = null,
  imageUrl = "profile/presets/03.png")` 를 호출 → 서버는 `imageKey` 를 못 받으므로 아무것도 바꾸지 않고
  **204 를 돌려준다.** 앱은 성공으로 처리하고 화면을 닫는데 이미지는 그대로다. 400 이 아니라 성공 코드라
  디버깅이 특히 비싸다(화이트리스트 검증 `UserService.java:33` 도 `imageKey == null` 이라 건너뛴다).
- **고치는 범위**: `UserDtos.kt:38` 필드명 + `:27,30` KDoc, `AuthRepository.kt:130` 파라미터명,
  `RemoteAuthRepository.kt:204-205`, `ProfileEditRoute.kt:174`, 테스트
  `RemoteAuthRepositoryTest.kt:408,410`. **해제는 필드가 아니라 신규 DELETE 라는 점**(§4-2)도 같이 적어야 한다.

#### [F-2] "멤버 `imageUrl` 은 null 로 온다"를 **서버 계약으로 못박은 주석**이 더 이상 사실이 아니다

- **앱(주석)**
  - `presentation/src/main/kotlin/com/moyeota/presentation/core/MemberDisplay.kt:27-28` —
    "서버 계약(2026-09): 방 상세 `members[]` 는 유저 요약이 없으면(탈퇴 등) publicId·nickname·**imageUrl 이 전부 null 로 온다**"
    → 접기 경우에는 **여전히 맞다**(§2-2). 틀린 쪽은 아래 둘이다.
  - `domain/src/main/kotlin/com/moyeota/domain/model/User.kt:20` —
    "프로필 이미지 URL. **서버가 주지 않으면 null**" → 요약이 있으면 서버는 **항상 준다.**
  - `data/src/main/kotlin/com/moyeota/data/remote/dto/UserDtos.kt:6` —
    "백엔드 `UserResponse(UUID uuid, String name)`" → 이제 3필드(S2).
  - `data/src/main/kotlin/com/moyeota/data/remote/dto/AuthDtos.kt:76` —
    "백엔드 `UserResponse(UUID uuid)`" → 이제 3필드.
- **테스트 픽스처(통과하지만 사실과 어긋남)**: 정상 멤버에 `"imageUrl":null` 을 쓰는 픽스처가
  `data/src/test/kotlin/com/moyeota/data/remote/PartyMappersTest.kt:353`,
  `data/src/test/kotlin/com/moyeota/data/remote/matching/PartySseEventTest.kt:28-29,65,79` 에 있다.
  운영은 이제 이 모양을 **보내지 않는다**(요약이 있는 멤버는 전체 URL). 반면
  `PartyMappersTest.kt:191-204`(`assertNull(user.imageUrl)` — 접기 경우)와 `PartySseEventTest.kt:91`
  (`publicId:null` 과 함께)은 **여전히 유효한 서버 모양**이다.
- **서버 근거**: `UserAccessService.java:36` + `ProfileImages.java:24-25`(§2-1).
- **재현 경로**: 런타임 재현 경로 **없음**(이게 차단이 아닌 이유다). 비용은 다음 사람에게 발생한다 —
  Coil 을 붙이는 작업자가 `User.kt:20` 을 읽고 "null 이 흔하다"고 믿어 플레이스홀더 분기를 짜거나,
  반대로 `MemberDisplay.kt:27` 을 읽고 "imageUrl 이 null 인가"로 탈퇴 판정을 바꿔 쓰면
  **멀쩡한 사용자가 전원 탈퇴 회원으로 표시된다**(이제 `imageUrl` 이 null 이 아니니 정반대로 깨진다).
  `249f33f` 커밋 메시지의 "틀린 주석은 없는 주석보다 나쁘다"와 같은 종류의 부채다.

### 🟢 무해 — 6건

| # | 항목 | 근거 요약 |
|---|---|---|
| H-1 | `UserResponse.imageUrl` 추가 (`/local/users/info` + 가입 201) | 앱 DTO 미선언(`UserDtos.kt:20-24`, `AuthDtos.kt:78-80`) + `NetworkModule.kt:24` `ignoreUnknownKeys`. 소비부(`RemoteAuthRepository.kt:213-219`)는 `uuid`·`name` 만 본다 |
| H-2 | `MemberInfo`·`ChatRoomMemberResult` 의 `imageUrl` 이 non-null 전체 URL | §2 전체. 앱 프로덕션 코드에 `imageUrl` 읽는 분기 0줄, 아바타는 `MemberDisplay.kt:62-67` 이니셜 원 고정 |
| H-3 | **(예고 밖)** 채팅 `seq`/`lastSeq`/FCM `seq` 추가 | §3-3. 디코더 8개 전부 `ignoreUnknownKeys`(특히 `ChatSocket.kt:51`), 커서·정렬 여전히 `id` 기준, FCM 은 `message.data[KEY_TYPE]` 맵 조회(`MoyeotaFirebaseMessagingService.kt:56`) |
| H-4 | 신규 400 `USER112 INVALID_PROFILE_IMAGE` | `UserService.java:33-34` 는 `imageKey != null` 일 때만 던진다. 앱은 `imageKey` 를 보내지 않으므로 도달 불가 |
| H-5 | V17 `users.image_url` 정리 마이그레이션 | §3-5. 앱이 이 컬럼을 채운 적이 없어 no-op |
| H-6 | `ChatRedisPublisher` 멤버 이벤트 시그니처 변경 | 와이어 필드 집합 동일(`:56-62`), 목적지 `/sub/.../members` 는 앱 미구독(직전 보고서 §H-7) |

### 참고 — 직전 보고서에서 끌고 온 미해결 항목의 현재 상태

- **M-1(장소 검색 `roadName` null)**: 커밋 `01365b1` 로 **해소됨**. `PlaceDtos.kt:24,62` 가 nullable,
  `PlaceMappers.kt:23,31` 이 `.orEmpty()`. 이번 배포와 무관하며 재조사하지 않았다.
- **H-1(`/api/v1/dispatch/**` 기능 플래그)**: 이번 배포에 dispatch 모듈 변경 0건(파일 목록 확인). 상태 불변.

---

## 6. 조사 한계 (미확인으로 남은 것)

1. **인증이 필요한 운영 응답 바디를 한 건도 실측하지 못했다.** 로그인 계정 미보유 + 쓰기 금지 제약.
   응답 모양 판정은 전부 `origin/main` 소스(record 정의 + `toUrl` 구현) + OpenAPI 스펙 기준이다.
   지시대로 상태코드로 경로 존재를 판정하지 않았다.
2. **`PROFILE_IMAGE_BASE_URL` 환경변수의 운영 실제값을 확인할 수 없다.** `application-prod.yaml` 에
   오버라이드가 없어 기본값 `https://cdn.moyeota.co.kr/` 로 판단했고, 그 CDN 에 16개 프리셋과
   기본 이미지가 실제로 존재함을 HEAD 로 확인했다(§4-1). 환경변수가 다른 값으로 설정돼 있다면
   `url` 의 호스트가 달라질 수 있다 — **그래서 §4-3 ②(앱이 URL 을 조립하지 말 것)가 중요하다.**
3. **`PartyDetailResult.java:24` 접기가 운영에서 실제로 발생하는 빈도**를 확인하지 못했다
   (`findAllByIds` 가 소프트 삭제 행을 포함하는지는 이번 배포 변경분이 아니라 보지 않았다).
   이 값은 F-2 의 등급을 바꾸지 않는다 — 어느 쪽이든 `imageUrl` 이 접기 판정의 트리거가 아니기 때문이다.
4. **빌드·테스트를 돌리지 않았다.** 코드를 한 줄도 바꾸지 않았으므로 검증 대상이 없다
   (직전 커밋 `249f33f` 기록: `:data` 283 · `:presentation` 283, 실패 0).
