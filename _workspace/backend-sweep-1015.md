# 백엔드 전수 점검 — 2026-10-15

작성: 2026-10-15 · 브랜치 `fix/place-roadname-null` · **조사·보고만. 코드 변경 0**

조사 범위: 백엔드 `origin/main`·`origin/develop` 의 2026-10-14 16:31 이후 전체 커밋,
운영 OpenAPI 스냅샷 3개의 기계 diff, 과거 제출 이슈 13건, 앱이 안 쓰는 서버 기능.

기준점(baseline)은 `284a950`(2026-10-14 16:31, PR #211 머지 = 운영 스냅샷 `prod-openapi.json`
채취 13분 전)이다. 그 뒤 `origin/main` 에 들어간 것이 「오늘의 변경」이다.

---

## 0. 한 줄 결론

**놓친 백엔드 변경: 없음.** 오늘 `main`·`develop` 에 들어간 커밋은 전부 이미 파악된
PR #233·#235·#237·#239·#241·#242·#243 안에 들어 있다. PR 없이 들어간 커밋 0건, 삭제된
엔드포인트 0건.

다만 **그 PR 들 안에** 별도로 짚지 않은 항목이 5개 있고(§1.3), 그중 **2개는 지금 조치 대상**이다
(§6). 그리고 과거 이슈 중 **#209 가 응답을 받은 사실이 눈에 안 띄게 지나갔다**(§4),
**#207 의 구현은 작성됐다가 버려졌다**(§7).

---

## 1. 오늘 백엔드에 들어간 것 — 커밋 전수

### 1.1 `origin/main` (운영 반영)

`git log origin/main --since="2026-10-14 15:00"` 전수. 괄호는 소속 PR.

| 시각 | 커밋 | 내용 | PR |
|---|---|---|---|
| 14:41 | `7f686fc` | Merge pull request #242 from develop | **#242 릴리스** |
| 14:20 | `de193d9` | Merge pull request #243 | #243 |
| 14:16 | `b81aa1a` | feat: SSE member-left 에 승계된 방장(hostPublicId) 포함 | #243 |
| 14:00 | `b4ba392` | Merge pull request #241 | #241 |
| 13:03 | `930fc6a` | refactor: 방장 판별을 Party.isHost 로 일원화 | #241 |
| 12:56 | `f9e66e5` | fix: 방 상세의 방장 노출을 내부 id 대신 멤버별 host 플래그로 | #241 |
| 12:41 | `418b29a` | Merge pull request #239 from develop | **#239 릴리스** |
| 12:21 | `b8e4750` | Merge pull request #237 | #237 |
| 12:05 | `5bb5957` | feat: 1차 모드 방장(파생) 합승 완료 권한 추가 | #237 |
| 12:02 | `5d8889a` | Merge pull request #236 from develop | **#236 릴리스** |
| 11:59 | `739b78d` | Merge pull request #235 | #235 |
| 11:55 | `2e1395c` | refactor: 프로필 이미지 목록 파일을 config 폴더로 옮김 | #235 |
| 10:12 | `4b5b969` | fix: 예전 URL 로 저장된 프로필 이미지를 비우고 CDN 주소 끝 슬래시를 보정 | #235 |
| 10:01 | `c95f9ac` | refactor: 프로필 이미지 목록을 별도 설정 파일로 분리 | #235 |
| 09:50 | `f130acd` | Merge pull request #233 | #233 |
| 09:20 | `c43c80c` | fix: 채팅 구독 큐가 버린 이벤트를 지표로 남기고 종료 때 큐를 비움 | #233 |

이 밖에 **10-14 작성이지만 오늘 운영에 처음 올라간** 커밋이 24건 있다(#233 의 채팅 순번 작업
`8d06409`~`d1da35c` 14건, #235 의 프로필 이미지 작업 `afe24c3`~`8b746ce` 7건 등). 전부 두 PR 안이다.

**`origin/develop` 은 `origin/main` 의 부분집합**이다(`de193d9` 이후 develop→main 릴리스 #242 로
동기). develop 에만 있고 main 에 없는 커밋 0건.

### 1.2 「PR 없이 들어간 커밋」 검사 결과 — 0건

main 의 오늘 커밋 16개 전부가 위 7개 PR 중 하나에 속한다. 직접 푸시 흔적 없음.

오늘 갱신된 PR 전수(`gh pr list --state all`, updatedAt ≥ 10-14T15:00Z): #233·#235·#236·#237·
#239·#241·#242·#243 **머지**, **#238 OPEN**. 이게 전부다.

열려 있는 다른 PR은 **#196 결제(포트원 빌링키 분할 결제, `mergeStateStatus=DIRTY` = 충돌)** 하나다.
오늘 손대지 않았고 머지도 안 됐으므로 앱 영향 없음. 머지되면 결제 도메인이 통째로 들어오므로
그때 별도 점검이 필요하다.

### 1.3 PR 안에 있었지만 따로 짚히지 않은 항목 5개 ★

#### (a) 마이그레이션 3개 — 그중 **V17 은 운영 데이터를 지운다**

```sql
-- V15__chat_message_seq.sql  (#233)
ALTER TABLE chat_room ADD COLUMN last_seq bigint NOT NULL DEFAULT 0;
ALTER TABLE chat_message ADD COLUMN seq bigint;
UPDATE chat_message ... row_number() OVER (PARTITION BY chat_room_id ORDER BY id)
ALTER TABLE chat_message ALTER COLUMN seq SET NOT NULL;
ALTER TABLE chat_message ADD CONSTRAINT uk_chat_message_room_seq UNIQUE (chat_room_id, seq);

-- V16__user_profile_gender_undisclosed.sql  (#235)
ALTER TABLE user_profile DROP CONSTRAINT user_profile_gender_check;
ALTER TABLE user_profile ADD CONSTRAINT user_profile_gender_check
      CHECK (gender IN ('MALE', 'FEMALE', 'UNDISCLOSED'));

-- V17__users_image_url_to_key.sql  (#235)   ← 데이터 삭제
UPDATE users SET image_url = NULL
WHERE image_url IS NOT NULL AND image_url NOT LIKE 'profile/%';
```

**V17 의 뜻:** 예전 `PATCH /users/me` 로 `imageUrl` 에 임의 URL 을 넣어 둔 계정은 **그 값이 전부
비워졌다**(= 기본 이미지로 되돌아갔다). 앱이 고칠 것은 없다 — 앱은 그 PATCH 로 이미지를 보낸 적이
없고(§5 (f)), 지금도 `imageKey = null` 로만 보낸다. 다만 **내부 테스트 계정 중 수동으로 이미지를
넣어 본 계정이 있으면 기본 이미지로 보인다**는 것을 QA 가 알고 있어야 한다. 되돌릴 수 없는 UPDATE 다.

V15 는 채팅 테이블에 `seq`·`last_seq` 를 추가하고 UNIQUE 를 건다. V16 은 `user_profile` 의
gender CHECK 제약을 교체한다. 둘 다 앱이 할 일 없음.

#### (b) `application.yaml` 변경 — 프로필 이미지 CDN 주소가 확정됐다

```diff
   config:
-    import: optional:classpath:application-local.yaml
+    import:
+      - classpath:config/profile-images.yaml   # 프로필 이미지 목록
+      - optional:classpath:application-local.yaml
 moyeota:
+  profile-image:
+    base-url: ${PROFILE_IMAGE_BASE_URL:https://cdn.moyeota.co.kr/}
```

- 신규 파일 `src/main/resources/config/profile-images.yaml` — `default-key: profile/presets/default.png`
  + `presets: profile/presets/01.png … 16.png` (**16장**).
- **도메인이 `cdn.moyeota.co.kr` 이다.** 어제 Swagger 예시의 `https://cdn.moyeota.app/p/1.png` 와
  다른 호스트다. 앱은 어느 쪽도 하드코딩하면 안 되고 서버가 주는 전체 URL 을 그대로 써야 한다.
- 읽기 요청으로 실측 확인(쓰기 없음):
  `default.png` 200 image/png 118,984 B · `01.png` 200 150,274 B · `16.png` 200 206,346 B.
  → **운영 CDN 은 실제로 서비스 중이다.**
- 서버 변환 규칙(`ProfileImages.toUrl`): `base-url + (key == null ? default-key : key)`.
  **따라서 응답의 `imageUrl` 은 고른 적 없는 사용자에게도 null 이 아니다** — 항상 전체 URL 이다.

#### (c) `UserResponse` 에 `imageUrl` 이 붙었다 → **이게 이슈 #209 의 (부분) 응답이다** ★

```java
// before
public record UserResponse(UUID uuid, String name) {}
// after (origin/main)
public record UserResponse(UUID uuid, String name, String imageUrl) {}
```

`UserResponse` 를 쓰는 경로는 운영 스펙 기준 **둘**이다:
`POST /api/v1/auth/register` 와 **`GET /api/v1/local/users/info`**.

즉 내 프로필 조회가 이제 프로필 이미지 URL 을 준다. 제출 이슈 **#209(「내 프로필 조회 응답 필드
확장 — imageUrl·연락처·가입일」)** 가 **imageUrl 한 항목만 조용히 충족된 상태**다. 이슈는 여전히
OPEN 이고 연락처·가입일은 안 왔다. 앱 `UserProfileResponse` 는 아직 `{uuid, name}` 두 필드만
선언하고 있어 이 값을 **버리고 있다**(`ignoreUnknownKeys` 가 흘린다 — 파싱은 안 깨진다).

#### (d) 신규 에러 코드 2개

| 코드 | HTTP | 어디서 | 앱 대응 |
|---|---|---|---|
| `USER112 INVALID_PROFILE_IMAGE` | 400 | `PATCH /users/me` 의 `imageKey` 가 제공 목록 밖 | 앱은 `imageKey` 를 보내지 않으므로 지금은 발생 불가 |
| `NOT_PARTY_HOST` | 403 | `POST /matching/rooms/{partyId}/finish` | **대응 완료** (`PartyError.NOT_PARTY_HOST`, 커밋 `f78bee3`) |

#### (e) `member-joined` 의 `host` 는 **항상 false** 다

`PartyService.join` 이 `MemberInfo.of(summary, rideCount, saved.isHost(memberId))` 로 만들지만,
`Party.hostId()` = `min(joinedAt, memberId)` 이므로 **방금 들어온 사람은 구조적으로 방장이 될 수
없다**. 서버 Swagger 도 그렇게 적혀 있다(「host 는 항상 false」). 이 이벤트의 `host` 를 믿고
목록을 덮어쓰면 안 된다 — 승계는 `member-left` 의 `hostPublicId` 로만 온다.

---

## 2. 운영 스펙 3개 기계 diff — 전문

스크립트: `/private/tmp/claude-501/.../scratchpad/specdiff3.py`
(기존 `specdiff.py` 를 3-way 로 확장 + 경로/스키마/enum/required 인벤토리 추가).
입력: `prod-openapi.json`(S1, 10-14 16:44) → `prod-openapi-2.json`(S2, 10-15 12:22) →
`prod-openapi-3.json`(S3, 10-15 14:54, 현재).

> **스펙은 nullability 를 표현하지 않는다.** 아래 diff 의 `type: 'string'` 은 "널이 아니다"가
> 아니다. 널 판정은 §3 의 소스 근거로만 했다.

```
==============================================================================
PATH + OPERATION INVENTORY
S1 (10-14 16:44) : 37 paths, 46 operations
S2 (10-15 12:22) : 39 paths, 48 operations
S3 (10-15 14:54) : 39 paths, 48 operations

### S1 -> S2
   + DELETE /api/v1/users/me/profile-image
   + GET    /api/v1/users/profile-images

### S2 -> S3
   (identical operation set)

### S1 -> S3 (net)
   + DELETE /api/v1/users/me/profile-image
   + GET    /api/v1/users/profile-images

==============================================================================
SCHEMA INVENTORY
S1 : 45 schemas · S2 : 46 · S3 : 46

### S1 -> S2
   + ProfileImageResponse
### S2 -> S3
   (identical schema set)

==============================================================================
ENUM VALUES (all snapshots)
/ChatMessageResult/properties/type        S1=S2=S3: ['LOCATION','TEXT']
/ChatReportRequest/properties/reason      S1=S2=S3: ['ABUSE','ETC','FRAUD','SEXUAL','SPAM']
/ChatRoomResult/properties/status         S1=S2=S3: ['ACTIVE','ARCHIVED','CLOSED']
/ChatRoomUserResult/properties/status     S1=S2=S3: ['ACTIVE','ARCHIVED','CLOSED']
/LastMessage/properties/type              S1=S2=S3: ['LOCATION','TEXT']
/UserRegisterRequest/properties/gender  <<< CHANGED
    S1: ['FEMALE','MALE']
    S2: ['FEMALE','MALE','UNDISCLOSED']
    S3: ['FEMALE','MALE','UNDISCLOSED']
```

### 2.1 PATHS — 필드 단위

```
### S1 -> S2   (added=16 removed=0 changed=2)
  -- CHANGED
     /api/v1/users/me  patch  description
         old='nickname·imageUrl 중 넘어온 것만 변경. 자기 닉네임 재전송은 중복 아님'
         new='nickname·imageKey 중 넘어온 것만 변경. imageKey 는 프로필 이미지 목록의 key 만
              허용. 자기 닉네임 재전송은 중복 아님'
     /api/v1/users/me  patch  responses/400/description
         old='INVALID_NICKNAME(USER107)'
         new='INVALID_NICKNAME(USER107), INVALID_PROFILE_IMAGE(USER112)'
  -- ADDED  (경로 2개가 통째로 생긴 것 — 16개 리프는 그 하위 필드)
     DELETE /api/v1/users/me/profile-image
        summary     '프로필 이미지 기본으로 되돌리기'
        description '고른 이미지를 지우고 기본 이미지로 표시. 멱등'
        operationId 'resetProfileImage'
        204 '되돌리기 완료'
        401 ErrorResponse '인증 필요 - 토큰 없음(USER005)/만료(USER002)/무효·탈퇴(USER001)'
        tags ['유저']
     GET /api/v1/users/profile-images
        summary     '프로필 이미지 목록'
        description '고를 수 있는 제공 이미지. 앱은 이 순서대로 보여주고, 고른 key 를
                     프로필 수정의 imageKey 로 보냄'
        operationId 'getProfileImages'
        200 array of ProfileImageResponse  '조회 성공'
        401 ErrorResponse
        tags ['유저']
  -- REMOVED
     (없음)

### S2 -> S3   (added=0 removed=0 changed=4)
  -- CHANGED
     /api/v1/matching/rooms/{partyId}/get/description
         old='members 에 동승자 publicId·닉네임·이미지·탑승 횟수. 본인 판별은 publicId 와
              토큰 sub 비교. SSE 구독(/events)의 connected 이벤트도 같은 모양이다'
         new='members 에 동승자 publicId·닉네임·이미지·탑승 횟수·host(방장 여부, 합류 순 정렬).
              방장 = 가장 먼저 합류한 현재 멤버이고 나가면 다음으로 일찍 합류한 멤버가 승계,
              항상 정확히 한 명. 본인 판별은 publicId 와 토큰 sub 비교. SSE 구독(/events)의
              connected 이벤트도 같은 모양이다'
     /api/v1/matching/rooms/{partyId}/events/get/description
         old=... '- `member-joined`: { member, status }. member 는 방 상세 members 원소와 같은 모양.
                  이미 있는 publicId 면 목록은 그대로 두고 상태만 반영
                  - `member-left`: { publicId, status }. 없는 publicId 면 목록은 그대로 두고
                  상태만 반영' ...
         new=... '- `member-joined`: { member, status }. member 는 방 상세 members 원소와 같은
                  모양(host 는 항상 false - 새로 들어온 사람은 방장이 아니다). 이미 있는 publicId
                  면 목록은 그대로 두고 상태만 반영
                  - `member-left`: { publicId, status, hostPublicId }. 없는 publicId 면 목록은
                  그대로 두고 상태만 반영. hostPublicId 는 이 이벤트를 반영한 뒤의 방장 - 받을
                  때마다 members 의 host 표시를 이 값으로 다시 지정한다. 단 null 이거나 목록에
                  없는 publicId 면 host 표시를 바꾸지 말고 방 상세를 다시 받는다(이벤트 순서
                  역전 대비)' ...
     /api/v1/matching/rooms/{partyId}/finish/post/description
         old='... 정원이 찬(COMPLETED) 방에서만 가능. 기사 기능이 켜진 모드에선 ...'
         new='... 정원이 찬(COMPLETED) 방에서만 가능. 방장(가장 먼저 합류한 멤버)만 호출 가능.
              기사 기능이 켜진 모드에선 ...'
     /api/v1/matching/rooms/{partyId}/finish/post/responses/403/description
         old='NOT_PARTY_MEMBER'
         new='NOT_PARTY_MEMBER / NOT_PARTY_HOST'
```

### 2.2 SCHEMAS — 필드 단위

```
### S1 -> S2   (added=13 removed=5 changed=1)
  -- CHANGED
     /UserRegisterRequest/properties/gender/enum
         old=['FEMALE','MALE']   new=['FEMALE','MALE','UNDISCLOSED']
  -- REMOVED
     /UpdateProfileRequest/properties/imageUrl/type        = 'string'
     /UpdateProfileRequest/properties/imageUrl/description = '생략하면 유지'
     /UpdateProfileRequest/properties/imageUrl/example     = 'https://cdn.moyeota.app/p/1.png'
     /UpdateProfileRequest/properties/imageUrl/maxLength   = 500
     /UpdateProfileRequest/properties/imageUrl/minLength   = 0
  -- ADDED
     /ChatMessageResult/properties/seq/type     = 'integer'   format='int64'
     /ChatRoomResult/properties/lastSeq/type    = 'integer'   format='int64'
     /LastMessage/properties/seq/type           = 'integer'   format='int64'
     /ProfileImageResponse/type                 = 'object'
     /ProfileImageResponse/properties/key/type  = 'string'
     /ProfileImageResponse/properties/url/type  = 'string'
     /UpdateProfileRequest/properties/imageKey/type        = 'string'
     /UpdateProfileRequest/properties/imageKey/description = '생략하면 유지.
                                GET /api/v1/users/profile-images 의 key 중 하나'
     /UpdateProfileRequest/properties/imageKey/example     = 'profile/presets/03.png'
     /UserResponse/properties/imageUrl/type     = 'string'

### S2 -> S3   (added=1 removed=0 changed=0)
  -- ADDED
     /MemberInfo/properties/host/type = 'boolean'

==============================================================================
REQUIRED ARRAYS (per schema, all snapshots)
S1 : 13 schemas declare required · S2 : 13 · S3 : 13
  ProfileImageResponse  <<< CHANGED
     S1: None   S2: []   S3: []       ← 스키마 신설에 따른 빈 배열. 의미 변화 아님
```

### 2.3 diff 가 말하는 것 / 말하지 못하는 것

**경로:** 3일 스냅샷 전체에서 **삭제된 경로·오퍼레이션 0건**. 추가는 2건(둘 다 프로필 이미지).
`GET /matching/rooms/{partyId}/status` 는 **S1 시점에 이미 없다** — #221(`04f6be7`)로 더 전에
사라졌고, 이슈 #219 에 그 영향을 이미 적어 뒀다.

**스펙이 못 잡은 변화 3건** (OpenAPI 에 안 나타남 → 소스로만 확인 가능):
1. `member-left` SSE payload 에 `hostPublicId` 추가 (#243) — SSE 본문은 스키마가 없고
   오퍼레이션 description 변경으로만 드러난다.
2. `MemberLeft` record 구조 변경: `MemberLeft(UUID publicId, String status, UUID hostPublicId)`.
3. V15~V17 마이그레이션 (§1.3 (a)).

**nullability — 소스로 판정한 결과:**

| 필드 | 널 가능? | 근거(소스) |
|---|---|---|
| `MemberInfo.host` | **아니다** | `record MemberInfo(..., boolean host)` — 원시 boolean. Jackson 이 항상 true/false 를 싣는다 |
| `MemberInfo.publicId`·`nickname`·`imageUrl`·`badgeId` | **그렇다(넷 동시에)** | `MemberInfo.of`: `if(summary == null) return new MemberInfo(null, null, null, null, rideCount, host)` |
| `MemberLeft.hostPublicId` | **그렇다** | `PartyService.leave`: `hostSummary == null ? null : hostSummary.publicId()` |
| `UserResponse.imageUrl` | **아니다** | `ProfileImages.toUrl`: `baseUrl + (key == null ? defaultKey : key)` — 고른 적 없어도 기본 이미지 전체 URL |
| `UserResponse.name` | **그렇다** | `LocalUserService.getProfile`: `.orElse(null)` (기존과 동일) |
| `ChatMessageResult.seq` / `LastMessage.seq` | **아니다** | V15 가 `seq SET NOT NULL` + UNIQUE |
| `ChatRoomResult.lastSeq` | **아니다** | V15 `last_seq bigint NOT NULL DEFAULT 0` |
| `ProfileImageResponse.key`·`url` | **아니다** | `properties.presets()` 의 고정 문자열 + `toUrl` |

---

## 3. #238 즐겨찾기 삭제 — 현재 상태와 개인정보 처리방침 연결

### 3.1 상태: **OPEN, 머지 대기(차단됨)**

| 항목 | 값 |
|---|---|
| 번호·제목 | #238 `feat: 즐겨찾기 장소 삭제 API 추가` |
| 작성자 | erase-jeong |
| 브랜치 | `develop` ← `feature/favorite-place-delete` |
| 생성 / 최종 갱신 | 2026-10-15T03:01:45Z / 03:46:38Z |
| `mergeable` | `MERGEABLE` (충돌 없음) |
| `mergeStateStatus` | **`BLOCKED`** |
| 리뷰 | `COMMENTED` × 4 — **`APPROVED` 0건** |
| 체크 | `빌드 및 테스트` SUCCESS · `claude-review` SUCCESS · 나머지 SUCCESS/SKIPPED |

→ 차단 사유는 **승인 리뷰 부재**로 보인다(충돌·CI 실패 아님). 이슈 #208 과 `closes #` 로
연결돼 있지 않다(본문에 번호가 비어 있다) — 머지돼도 **#208 이 자동으로 닫히지 않는다.**

### 3.2 계약

```
DELETE /api/v1/users/me/favorite-places/{placeName}
  204  삭제 완료
  404  FAVORITE_PLACE_NOT_FOUND  "등록되지 않은 즐겨찾기입니다."  (PlaceErrorCode 신설)
```

- 즐겨찾기는 `(userId, placeName)` 복합키라 **별도 id 가 없다**. 그래서 **경로에 장소 이름**이 온다.
- 소유자 격리: 내 즐겨찾기에 없는 이름이면 404. 남의 같은 이름은 건드리지 않는다(테스트 있음).
- **별칭 수정(PATCH)은 들어 있지 않다** — #208 이 「+ 별칭 수정」까지 요청했는데 삭제만 왔다.

### 3.3 머지되면 앱의 어디가 달라지는가

| 자리 | 지금 | 머지·배포 후 |
|---|---|---|
| `data/.../remote/PlaceApi.kt` | `POST`·`GET /users/me/favorite-places` 둘뿐 | `@DELETE("api/v1/users/me/favorite-places/{placeName}")` 추가. **장소 이름이 경로 변수**이므로 `@Path(value="placeName", encoded=false)` 로 Retrofit 인코딩에 맡긴다(한글) |
| `domain/.../repository/PlaceRepository.kt` | `getFavoritePlaces`·`addFavoritePlace` | `removeFavoritePlace(placeName)` 추가 |
| `presentation/.../home/DestinationScreen.kt` | 「자주 가는 곳」 섹션은 `favoritePlaces.take(3)` 을 **읽기 전용**으로 그린다. ★ 버튼은 **등록 전용**(`onAddFavorite`) | 각 행에 삭제 어포던스(스와이프 또는 ×) + 확인. 404 는 「이미 지워졌어요」로 받아 목록 재조회 |
| `DestinationRoute.addFavorite` 의 실패 문구 | 「중복이거나 10개를 넘었을 수 있어요」 | 삭제 실패 문구를 따로 둔다 |

**단 전제가 하나 깨져 있다 — #231 이 아직 OPEN 이다.** `FavoritePlaceEntity` 는
`@IdClass(FavoritePlaceId.class)` 인데 `origin/main` 의 `FavoritePlaceId` 에는 **public 무인자
생성자가 없다**(`FavoritePlaceId(Long, String)` 하나뿐). JPA 가 IdClass 인스턴스화에 실패해
**`POST /users/me/favorite-places` 는 운영에서 500** 이다(앱 작업 기록 `22_api-integrator_authpaths.md`
`28_api-integrator_auth_v2.md` 에 이미 적혀 있다. 운영에 쓰기 요청을 보내지 않았으므로 라이브
재현은 **미확인 — 확인 방법: #231 수정 후 앱에서 ★ 탭**. 소스 근거는 위와 같다).

삭제 쪽은 `existsByUserIdAndPlace`·`deleteByUserIdAndPlaceName` 둘 다 `@Query`+`@Param` 이라
IdClass 를 인스턴스화하지 않는다 → **#238 자체는 #231 과 무관하게 동작할 것으로 보인다.**
그러나 **담을 수 없는 목록을 지우는 API** 이므로, 앱에 삭제 UI 를 붙이는 값은 **#231 이 먼저
고쳐진 뒤에** 생긴다. 두 이슈를 묶어 요청하는 편이 낫다.

### 3.4 개인정보 처리방침 연결 ★

`_workspace/legal-site/privacy/index.html` **제11조(이용자의 권리와 행사 방법)** 는 권리를
두 묶음으로 나눠 놓았다.

- **1항 「앱에서 지금 바로 할 수 있는 것」** — 닉네임 변경 / 회원 탈퇴 / 합승방별 채팅 알림 끄기 /
  권한 변경
- **2항 「아래 요구는 개인정보 보호책임자 이메일(제15조)로 접수합니다. 앱에 해당 기능이 아직
  없어서 이메일로 받습니다」** — 그 목록 안에 정확히 이 줄이 있다:

  > **「자주 가는 곳을 하나씩 삭제해 달라는 요구」**

- 같은 조 **5항**(마지막 문단)도 「없는 기능을 있다고 적지 않기 위해」 미제공 기능을 나열한다.

**#238 이 머지·배포되고 앱에 삭제 UI 가 붙으면 세 자리를 같이 고쳐야 한다:**

1. `privacy` 제11조 **1항에 「자주 가는 곳 삭제 — 목적지 검색 > 자주 가는 곳」을 추가**
2. `privacy` 제11조 **2항에서 「자주 가는 곳을 하나씩 삭제해 달라는 요구」 줄을 뺀다**
   — 안 빼면 「앱에 해당 기능이 아직 없어서」가 **허위 고지**가 된다
3. `account-deletion` 의 「계정은 그대로 두고 일부만」 절에 자주 가는 곳 항목을 넣는다
   (채팅 메시지 항목과 같은 자리)

그리고 **`_workspace/legal-site/README.md` 의 「서버 동작이 바뀌면 고쳐야 할 자리」 표에
이 줄이 아예 없다.** 메시지 삭제·위치 공유·채팅 신고 세 건은 행이 있는데 즐겨찾기 삭제는 없다.
**표에 행을 하나 추가해 두는 것이 지금 당장 할 수 있는 조치다**(§6-A3) — 그러면 #238 이 머지될 때
문서 수정이 누락되지 않는다.

제16조는 방침 변경 시 **시행일 7일 전 공지**(중요 변경은 30일)를 약속한다. 권리 행사 창구가
이메일 → 앱으로 **넓어지는** 변경이라 수집·목적 변경은 아니지만, 변경 자체는 공지 대상이다.
(이 판단은 법률 자문이 아니다. 표기 변경 시 운영자 확인 필요.)

---

## 4. 과거 제출 이슈 13건 — 현재 상태

`gh issue view` 실측(2026-10-15). 전부 `SWM-Moyeota/Backend`, 작성자 KII1ua.

| # | 제목 | 상태 | 닫힌 시각 | 해결 PR | 앱이 쓰고 있나 |
|---|---|---|---|---|---|
| **206** | 계정 탈퇴 API(`DELETE /users/me`) + 웹 탈퇴 페이지 | **CLOSED** completed | 10-15 03:25 | #230/#211 (10-14 16:28 머지) — **오늘 PR 이 아니다.** 닫기만 오늘 | ✅ 쓴다 (`UserApi.deleteAccount`, `@HTTP(hasBody=true)`) |
| **207** | 완료된 방 목록(내 탑승 기록) `GET /users/me/parties` | OPEN | — | 없음 | ❌ 서버에 경로 없음. 원격 브랜치 전수 grep `me/parties` 0건. **그러나 구현이 로컬 커밋 `9e1be5e` 에 한 번 있었고 버려졌다 — §7** |
| **208** | 즐겨찾기 삭제 API(+ 별칭 수정) | OPEN | — | **#238(OPEN, BLOCKED)** — 삭제만. 별칭 수정 없음. `closes` 미연결 | ❌ (§3) |
| **209** | 내 프로필 조회 응답 확장 — imageUrl·연락처·가입일 | OPEN | — | **imageUrl 만 #235 로 들어왔다**(`UserResponse.imageUrl`, §1.3 (c)). 연락처·가입일 없음 | ❌ **앱 DTO 가 아직 받지 않는다** |
| **219** | 배차·운행 상태 전이에 SSE 이벤트 추가 | OPEN | — | 없음 | ❌ 25·26 은 방 상세 **폴링 6~8초**로 버티는 중(이슈 코멘트에 기록) |
| **220** | SSE heartbeat 30초 ↔ CloudFront 오리진 타임아웃 30초 | OPEN | — | 없음 | ❌ 앱은 90초 무신호 시 재연결로 버틴다 |
| **222** | 같은 방 재합류가 `ALREADY_JOINED_OTHER_PARTY` 로 와서 구별 불가 | OPEN | — | 없음 | ❌ 합류 중단 복구가 여전히 어렵다 |
| **226** | 만 14세 미만 가입을 서버에서 막아야 | OPEN | — | 없음 | ❌ 앱만 막는다(`126_compose-builder_age-gate.md`). API 직접 호출 우회 가능 |
| **227** | 가입 시 성별을 선택 항목으로 | **CLOSED** completed | 10-15 03:56 | #235 | ⚠️ **"선택 항목"이 된 게 아니다** — `UserRegisterRequest.gender` 는 **여전히 `@NotNull`** 이다. `UNDISCLOSED` 라는 **값**이 생겼을 뿐. 앱은 이에 맞게 「선택 안 함」을 **값으로** 보낸다 (커밋 `249f33f`) ✅ |
| **231** | 즐겨찾기 등록 500 — `FavoritePlaceId` 생성자 | **OPEN** | — | 없음. **`origin/main` 의 `FavoritePlaceId` 에 무인자 생성자 여전히 없음(재확인)** | ⚠️ 앱에 ★ 등록 UI 가 **있다** → 운영에서 실패하는 전제 |
| **232** | 동승자 `badgeId` 가 항상 null | OPEN | — | 없음. `MemberInfo.of` 에 `// TODO badgeId 값 설정` 그대로 | ✅ 앱은 `verifiedLabel = ""` 로 고정(가짜 배지 안 만든다) |
| **234** | 성별에 「밝히지 않음」 추가 — Gender enum | **CLOSED** completed | 10-15 01:28 | #235 (`Gender.UNDISCLOSED`) | ✅ 대응 완료 |
| **240** | [#237] 앱이 방장을 식별할 수 없음 — `hostId` 가 내부 PK | **CLOSED** completed | 10-15 05:54 | **#241 (`members[].host`) + #243 (`hostPublicId`)** | 🔄 **작업 중(미커밋)** — §5 (g) |

요약: **CLOSED 4건**(#206·#227·#234·#240), **OPEN 9건**. OPEN 중 #208 만 PR 이 떠 있고,
#209 는 응답의 1/3 이 조용히 들어왔다.

### 4.1 #227 에 대한 주의 — 문서 표기가 아직 맞다

이슈는 「선택 항목으로」였고 CLOSED 지만, 서버는 **여전히 성별을 필수로 받는다**
(`@NotNull(message = "성별은 필수입니다.") Gender gender`). 따라서
`_workspace/legal-site/privacy/index.html` 제1조의 「아래 항목은 **모두 필수**입니다」와
「성별」 행에 (선택) 이 없는 상태는 **지금도 사실과 맞다.** `legal-site/README.md` 표 첫 행의
조건(「`@NotNull` 이 빠지고 … **둘 다** 바뀐 뒤에만 참이다」)은 **아직 충족되지 않았다** — 고치면
오히려 틀린다. 「선택 안 함」이라는 선택지가 생긴 사실을 처리방침 성별 행 설명에 한 줄
덧붙일지는 운영 판단(필수성 서술은 그대로 두어야 한다).

---

## 5. 앱이 쓰지 않는 서버 기능

운영 스펙 S3 의 48 오퍼레이션 ↔ 앱의 Retrofit/SSE 선언 42건을 기계적으로 집합 차집합한 결과
(경로 변수 이름 정규화). 스크립트 포함 조사: `data/src/main`·`domain/src/main`·
`presentation/src/main`·`core` 의 `@GET/@POST/@PUT/@PATCH/@DELETE/@HTTP`.

### 5.1 서버에 있는데 앱이 안 부르는 것 — **7건**

| 메서드·경로 | 왜 안 쓰는가 (앱 소스 근거) | 분류 |
|---|---|---|
| (a) `POST /api/v1/auth/phone/check` | **승객 앱용이 아니다.** 스펙 설명이 「기사 phone-first 온보딩 1단계」다. 승객 앱은 「이미 가입된 번호」를 `POST /auth/sms/send` 의 **409 `USER104`** 로 거른다(`AuthMappers`·`RemoteAuthRepositoryTest` 가 그 매핑을 못 박는다) | 의도적 — 불필요 |
| (b) `POST /api/v1/chat-rooms/{id}/location` | 채팅 도메인의 위치 메시지/공유 세트. 앱의 위치 공유는 **매칭 도메인** API(`/matching/rooms/{partyId}/location(s)`, 백엔드 PR #100)로 붙였다가, **그 엔드포인트가 서버에서 사라져** 앱 코드도 함께 제거됐다(`origin/main` grep 0건, 운영 스펙에도 없음). 채팅 쪽 세트는 처음부터 쓴 적이 없다 | 화면 없음 |
| (c) `POST /api/v1/chat-rooms/{id}/location/sharing` | 같음 | 화면 없음 |
| (d) `DELETE /api/v1/chat-rooms/{id}/location/sharing` | 같음 | 화면 없음 |
| (e) `POST /api/v1/chat-rooms/{id}/reports` | 채팅 신고. **서버와 `chat_report` 테이블은 이미 있고 앱에만 없다** — `legal-site/README.md` 가 이 상태를 표의 한 행으로 이미 추적하고 있다. 앱의 「신고」는 운행 중 긴급 신고(`POST /api/v1/reports`)로 다른 기능이다 | 화면 없음 — 문서 추적됨 |
| (f) `GET /api/v1/users/profile-images` | **의도적 보류.** `_workspace/profile-image-plan.md`(10-15) 결론: 「1차 배포(플레이 내부 테스트)에는 넣지 말 것. `imageKey` 이름 바꾸기만 올리고 화면 작업은 v1.1 로」. 업로드가 아니라 **닫힌 16장 고르기**라 로더(Coil) 없이 앱 내장 WebP 96 KB 로 가능하다는 측정까지 끝나 있다 | 의도적 — v1.1 |
| (g) `DELETE /api/v1/users/me/profile-image` | 같음 (프로필 이미지 화면이 없으니 되돌릴 대상도 없다) | 의도적 — v1.1 |

추가로 **스펙에는 필드가 왔는데 앱이 안 읽는 것** 3건:

| 필드 | 상태 |
|---|---|
| `ChatMessageResult.seq` · `LastMessage.seq` · `ChatRoomResult.lastSeq` (#233) | 앱에 선언 없음 → `ignoreUnknownKeys` 가 흘린다. **영향 없음**(파악한 대로). 앱의 메시지 커서는 `messageId` 기반이고 정렬도 거기에 의존한다. `lastSeq` 는 장래에 안읽음 계산을 가볍게 할 재료가 될 수 있다 |
| `UserResponse.imageUrl` (#235, `GET /local/users/info`) | 앱 `UserProfileResponse` 가 `{uuid, name}` 만 선언 → **버린다.** 마이페이지에 내 프로필 이미지를 그릴 때 필요한 값이다(§6-B1) |
| `MemberInfo.host` (#241) | **작업 중** — 아래 (g) |

### 5.2 앱이 부르는데 운영에 없는 것 — **1건** ⚠️

| 메서드·경로 | 사실 |
|---|---|
| `GET /api/v1/dispatch/rides/{partyId}` (`DispatchApi.getDriverLocation`) | **운영 스펙 S1·S2·S3 전부에 없다.** `origin/main` 에도 `dispatch/rides` grep 0건 — 백엔드 `feature/driver-report` 브랜치 기준으로 선언된 것이다(`DispatchApi.kt` 주석에 그렇게 적혀 있다). `RemoteDispatchRepository` 가 실제로 쓴다 |

이것은 **오늘 생긴 문제가 아니다**(어제 스냅샷에도 없었다). 택시 모드가 꺼져 있어
기사 위치 화면(26)에 도달하지 않으므로 지금 사용자에게 보이지는 않는다 —
**미확인 — 확인 방법: `GET /api/v1/config` 의 택시 플래그가 운영에서 false 인지와,
그 플래그가 26 진입을 실제로 막는지 앱에서 재확인.** 택시 모드를 켜는 순간 404 가 된다.

### 5.3 (g) 지금 다른 에이전트가 작업 중인 것 — 중복 착수 금지

`fix/place-roadname-null` 작업 트리에 **미커밋 수정 5개**가 있다(조사 시점):

```
 M data/src/main/kotlin/com/moyeota/data/remote/PartyMappers.kt
 M data/src/main/kotlin/com/moyeota/data/remote/dto/PartyDtos.kt
 M domain/src/main/kotlin/com/moyeota/domain/model/PartyEvent.kt
 M domain/src/main/kotlin/com/moyeota/domain/model/Ride.kt
 M domain/src/main/kotlin/com/moyeota/domain/model/User.kt
```

내용을 읽어 보니 **#241·#243 대응이 이미 들어가는 중**이다:
`PartyDetailResponse.MemberInfo.host: Boolean?` 추가, `MemberLeftPayload.hostPublicId: String?`
추가, `User.isHost`, `Ride.isHostedByMe`, `PartyMappers` 의 `isHost = host ?: isMe` 폴백
(누락 시 나에게만 true 로 기울이는 근거가 표로 적혀 있다).

→ **「`members[].host` 미적용」을 새 작업으로 올리지 말 것.** 진행 중이다.
커밋 전 확인할 점 하나: JSON 키는 **`host`** 다(`isHost` 로 적으면 `ignoreUnknownKeys` 가
조용히 흘려 늘 null 이 된다) — 해당 DTO 주석이 이미 그 함정을 적어 두었다.

---

## 6. 지금 당장 / 나중에

### A. 지금 당장 (1차 배포 전)

| | 항목 | 왜 지금 | 어디 |
|---|---|---|---|
| **A1** | **#241·#243 대응 완주**(`members[].host` + `hostPublicId`) | 운영에 05:41 배포 완료. 이걸 안 읽으면 비방장에게 「합승 완료」 버튼을 계속 보여 주고 403 안내로 때운다. 방장 승계 표시도 안 된다. **이미 작업 중이므로 완주·커밋만 확인** | `PartyDtos.kt`·`PartyMappers.kt`·`User.kt`·`Ride.kt`·`PartyEvent.kt` (타 에이전트) |
| **A2** | `member-left` 처리에 **승계 규칙** 반영 | 서버 지시가 명시적이다: `hostPublicId` 를 받을 때마다 host 표시를 재지정하고, **null 이거나 목록에 없는 publicId 면 표시를 바꾸지 말고 방 상세를 다시 받는다**(이벤트 역전 대비). `hostPublicId` 는 **널 가능**(§2.3) | A1 과 같은 작업 |
| **A3** | `legal-site/README.md` 표에 **「앱에 즐겨찾기 삭제 UI 가 붙는다」 행 추가** | #238 이 머지될 때 `privacy` 제11조 1·2항 수정이 누락되면 허위 고지가 된다. 코드 변경 0, 5분 | `_workspace/legal-site/README.md` |
| **A4** | `PartyDtos.kt` 의 **낡은 주석 확인** | 「서버 record 에 hostId 도, MemberInfo.isHost 도 **없다**」는 05:41 부로 거짓. A1 작업에서 이미 고쳐지는 중으로 보이므로 **커밋 내용 확인만** | 같음 |
| **A5** | **QA 공지: V17 로 기존 프로필 이미지가 비워졌다** | 수동으로 `imageUrl` 을 넣어 본 테스트 계정은 기본 이미지로 보인다. 앱 버그로 오인하지 않게 | 코드 변경 없음 |

### B. 나중에 해도 되는 것

| | 항목 | 왜 미뤄도 되나 |
|---|---|---|
| **B1** | `UserProfileResponse` 에 `imageUrl` 추가 (`GET /local/users/info`) | 마이페이지가 아직 프로필 이미지를 그리지 않는다(v1.1). 지금은 **버려도 안전**하다 — 파싱 안 깨진다. §1.3 (c) |
| **B2** | 프로필 이미지 고르기 화면 + `GET /users/profile-images` + `DELETE /users/me/profile-image` | `profile-image-plan.md` 가 **v1.1 로 미루라고 이미 결론**. CDN 16장 실측 완료 |
| **B3** | 즐겨찾기 삭제 UI | **#238 머지 + 배포 전까지 할 수 없다.** 게다가 **#231(등록 500)이 먼저** 고쳐져야 값이 생긴다 |
| **B4** | `lastSeq` 로 안읽음 계산 경량화 | 현재 방식이 동작한다. 성능 과제 |
| **B5** | `DispatchApi` 의 운영 미존재 정리 | 택시 모드가 꺼져 있어 도달 불가. 택시 모드를 켜기 **전에** 반드시 해소 (#219 와 같은 타이밍) |

### C. 백엔드에 다시 눌러야 할 것 (앱 작업 아님)

| | 이슈 | 메모 |
|---|---|---|
| C1 | **#231** 즐겨찾기 등록 500 | **앱에 UI 가 이미 있어 실패를 사용자에게 보여 준다.** `FavoritePlaceId` 에 public 무인자 생성자 한 줄. 1차 배포에 즐겨찾기를 노출하려면 이게 먼저다 |
| C2 | **#238** 머지 요청 + **#208 에 `closes` 연결** + 별칭 수정 누락 확인 | 승인 리뷰만 받으면 머지 가능(CI 전부 통과). 머지돼도 #208 은 수동으로 닫아야 한다 |
| C3 | **#219 / #220** | 택시 모드를 켜기 전 필수. 지금 25·26 은 무거운 상세를 6~8초로 폴링한다 |
| C4 | **#209** 의 남은 두 항목(연락처·가입일) | imageUrl 만 들어왔다는 사실을 이슈에 코멘트로 남겨 두면 범위가 흐려지지 않는다 |
| C5 | **#226** 만 14세 서버 차단 | 앱만 막고 있다 |
| C6 | **#207** 탑승 기록 API | origin 의 모든 브랜치에 0건. **단 구현이 로컬 커밋 `9e1be5e` 에 있었고 `reset` 으로 버려졌다 — §7.** 다시 요청할 때 그 커밋을 가리키면 재작성이 불필요하다 |

---

## 7. 조사 중 발견 — #207 구현이 로컬에 있었고 버려졌다 ★

백엔드 작업 트리의 reflog 를 확인하다 나온 것이다(origin 의 변경은 아니다. 그래서 §1 의
「놓친 변경 없음」 결론은 그대로다).

```
f130acd HEAD@{6}: reset: moving to origin/develop          ← 버린 자리
9e1be5e HEAD@{7}: commit: feat: 합승 이력 조회와 1차 모드 방장 합승 완료 권한 추가
```

`9e1be5e` (2026-10-15 11:48, author KII1ua)의 커밋 메시지와 파일 목록:

```
합승 이력
- match_room 에 finished_at·finish_reason 추가(V15), 종료 사유 기록
  (RIDE_COMPLETED / FINISHED_WITHOUT_DRIVER / EXPIRED)
- GET /users/me/parties: 내가 끝까지 남은 FINISHED 방을 종료 시각
  최신순 커서 페이지네이션으로 조회

파생 방장 (저장 컬럼 없음)
- 방장 = 가장 먼저 합류한 현재 멤버(Party.hostId() 파생) ...
- 방 상세 응답에 hostId 노출 (멤버가 없는 취소된 방은 null)

 matching/party/PartyHistoryController.java   | 37 +++   ← @GetMapping("/users/me/parties")
 matching/party/PartyHistoryService.java      | 52 +++
 matching/party/domain/FinishReason.java      |  5 +
 matching/party/domain/FinishedPartyRow.java  |  7 +
 matching/party/PartyHistoryIntegrationTest.java | 95 +++
 ...
```

즉 **이슈 #207(탑승 기록 API)의 구현이 한 번 작성됐다가 `reset` 으로 버려졌다.** 그 뒤
같은 브랜치에서 **방장 부분만** 다시 떼어 `5bb5957`(#237) → `f9e66e5`·`930fc6a`(#241)로
올라갔다. 그래서 origin 에는 `/users/me/parties` 가 **없다**(§4 의 전수 grep 과 일치).

`hostId` → `members[].host` 전환도 이 흐름에서 설명된다 — 버려진 커밋은 방 상세에
**`hostId`(내부 PK)** 를 노출했고, 그게 이슈 #240 을 부른 모양이며, 재작업에서
`members[].host` 로 바뀐 것이다.

- 커밋은 **푸시되지 않았고** reflog 에만 남아 있다. `git show 9e1be5e` 로 전문을 읽을 수 있고
  `git cherry-pick`·`git diff` 로 되살릴 수 있다. GC 대상이므로 **오래 두면 사라진다.**
- 앱이 지금 할 일은 **없다**(엔드포인트가 운영에 없다). 다만 #207 을 다시 요청할 때
  「구현이 로컬 `9e1be5e` 에 있었다」를 함께 알려 주면 처음부터 다시 쓰지 않아도 된다.
- **미확인 — 확인 방법:** 그 커밋의 응답 스키마(커서 모양·필드명)는 읽지 않았다.
  필요하면 `git show 9e1be5e -- '*PartyHistory*'`.

### 7.1 조사 중 외부에서 바뀐 것 (기록)

백엔드 작업 트리가 조사 **도중에** `feature/party-ride-history`(930fc6a, 수정 3개) →
`develop`(de193d9, 깨끗) 으로 옮겨졌다. reflog `HEAD@{1} checkout` + `HEAD@{0} pull` 이며
**이 조사가 한 일이 아니다**(실행한 git 명령은 `fetch --prune`·`log`·`show`·`diff`·`grep`
뿐이다. `fetch` 는 체크아웃된 브랜치를 옮기지 않는다). 다른 창에서 작업이 동시에 진행된 것으로
보이며, 그 WIP 3개는 `HEAD@{2}` 의 로컬 커밋 `5dcefc8`("커밋")에 담겨 있다.


---

## 부록 — 재현 방법

```bash
# 1. 오늘 들어간 커밋 전수
cd /Users/sungyoon/Desktop/moyeota/backend && git fetch origin --prune
git log origin/main    --since="2026-10-14 15:00" --date=iso --pretty='%h|%ad|%an|%s'
git log origin/develop --since="2026-10-14 15:00" --date=iso --pretty='%h|%ad|%an|%s'

# 2. 설정·마이그레이션·삭제 엔드포인트
git diff --name-status 284a950 origin/main -- '*.yaml' '*.yml' '*.sql' '*.properties'
git diff --name-status --find-renames 284a950 origin/main | grep -E '^(D|R)'
# 컨트롤러 매핑 전후 비교 (삭제된 엔드포인트 검출)
for r in 284a950 origin/main; do
  git grep -n -E '@(RequestMapping|Get|Post|Put|Patch|Delete)Mapping' $r \
    -- 'src/main/java/**/*Controller.java' | sed "s#^$r:##"
done   # → 추가 2건, 삭제 0건

# 3. 스펙 3-way 기계 diff
cd /private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/<세션>/scratchpad
python3 specdiff3.py        # 결과: specdiff3.txt (§2 전문)

# 4. 서버 ↔ 앱 엔드포인트 집합 차집합  (§5)
#    prod-openapi-3.json 의 48 오퍼레이션 vs 앱 @GET/@POST/... 선언

# 5. CDN 실측 (읽기 요청만)
curl -s -o /dev/null -w '%{http_code} %{content_type} %{size_download}\n' \
  https://cdn.moyeota.co.kr/profile/presets/default.png
```

**이 조사에서 운영 서버에 보낸 요청:** `cdn.moyeota.co.kr` 의 정적 이미지 GET 3건뿐.
쓰기 요청 0건. 백엔드·프론트엔드 양쪽 모두 커밋·푸시·브랜치 변경 0건, 빌드·테스트 실행 0건.

### 부록 B — `specdiff3.py` 전문 (스크래치패드는 세션마다 사라지므로 여기 남긴다)

```python
import json, sys

snaps = [('S1 (10-14 16:44)','prod-openapi.json'),
         ('S2 (10-15 12:22)','prod-openapi-2.json'),
         ('S3 (10-15 14:54)','prod-openapi-3.json')]
docs = [(n, json.load(open(f))) for n,f in snaps]

def flat(o, p=''):
    out={}
    if isinstance(o, dict):
        for k,v in o.items(): out.update(flat(v, p+'/'+str(k)))
    elif isinstance(o, list):
        if all(not isinstance(x,(dict,list)) for x in o): out[p]=sorted(map(str,o))
        else:
            for i,v in enumerate(o): out.update(flat(v, p+'/'+str(i)))
    else: out[p]=o
    return out

def section(title, get):
    print('='*78); print(title)
    for i in range(len(docs)-1):
        (na,a),(nb,b) = docs[i], docs[i+1]
        fa, fb = flat(get(a)), flat(get(b))
        ka, kb = set(fa), set(fb)
        added, removed = sorted(kb-ka), sorted(ka-kb)
        changed = sorted(k for k in ka&kb if fa[k]!=fb[k])
        print('\n### %s  ->  %s   (added=%d removed=%d changed=%d)' % (na,nb,len(added),len(removed),len(changed)))
        if changed:
            print('  -- CHANGED')
            for k in changed: print('     %s\n         old=%r\n         new=%r'%(k,fa[k],fb[k]))
        if removed:
            print('  -- REMOVED')
            for k in removed: print('     %s = %r'%(k,fa[k]))
        if added:
            print('  -- ADDED')
            for k in added: print('     %s = %r'%(k,fb[k]))
        if not (changed or removed or added): print('  (no difference)')

# top-level path inventory
print('='*78); print('PATH + OPERATION INVENTORY')
sets=[]
for n,d in docs:
    ops = set()
    for p, item in d['paths'].items():
        for m in item:
            if m in ('get','post','put','patch','delete','head','options'):
                ops.add('%-6s %s' % (m.upper(), p))
    sets.append((n, ops))
    print('%s : %d paths, %d operations' % (n, len(d['paths']), len(ops)))
for i in range(len(sets)-1):
    (na,a),(nb,b)=sets[i],sets[i+1]
    print('\n### %s -> %s'%(na,nb))
    for o in sorted(b-a): print('   + %s'%o)
    for o in sorted(a-b): print('   - %s'%o)
    if not (b-a or a-b): print('   (identical operation set)')
print('\n### S1 -> S3 (net)')
a,b = sets[0][1], sets[-1][1]
for o in sorted(b-a): print('   + %s'%o)
for o in sorted(a-b): print('   - %s'%o)

# schema inventory
print()
print('='*78); print('SCHEMA INVENTORY')
ssets=[]
for n,d in docs:
    s=set(d['components']['schemas']); ssets.append((n,s))
    print('%s : %d schemas'%(n,len(s)))
for i in range(len(ssets)-1):
    (na,a),(nb,b)=ssets[i],ssets[i+1]
    print('\n### %s -> %s'%(na,nb))
    for o in sorted(b-a): print('   + %s'%o)
    for o in sorted(a-b): print('   - %s'%o)
    if not (b-a or a-b): print('   (identical schema set)')

# enum inventory across all snapshots
print()
print('='*78); print('ENUM VALUES (all snapshots)')
def enums(d):
    out={}
    def walk(o,p):
        if isinstance(o,dict):
            if 'enum' in o and isinstance(o['enum'],list): out[p]=sorted(map(str,o['enum']))
            for k,v in o.items(): walk(v,p+'/'+str(k))
        elif isinstance(o,list):
            for i,v in enumerate(o): walk(v,p+'/'+str(i))
    walk(d,''); return out
emaps=[(n,enums(d)) for n,d in docs]
allk=sorted(set().union(*[set(e) for _,e in emaps]))
for k in allk:
    vals=[e.get(k) for _,e in emaps]
    mark='  <<< CHANGED' if len(set(map(str,vals)))>1 else ''
    print('%s%s'%(k,mark))
    for (n,_),v in zip(emaps,vals): print('    %s: %s'%(n,v))

section('PATHS (deep field-level)', lambda d: d['paths'])
section('SCHEMAS (deep field-level)', lambda d: d['components']['schemas'])

# required / property inventory per schema, for nullability-adjacent info
print()
print('='*78); print('REQUIRED ARRAYS (per schema, all snapshots)')
for n,d in docs:
    req = {k:v.get('required') for k,v in d['components']['schemas'].items() if v.get('required')}
    print('%s : %d schemas declare required'%(n,len(req)))
reqs=[{k:sorted(v.get('required',[])) for k,v in d['components']['schemas'].items()} for _,d in docs]
keys=sorted(set().union(*[set(r) for r in reqs]))
for k in keys:
    vals=[r.get(k) for r in reqs]
    if len(set(map(str,vals)))>1:
        print('  %s  <<< CHANGED'%k)
        for (n,_),v in zip(docs,vals): print('     %s: %s'%(n,v))
```
