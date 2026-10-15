# 프로필 이미지 — 조사 결과와 구현 계획

작성: 2026-10-15 · 브랜치 `fix/place-roadname-null` · **계획만. 코드·의존성 변경 없음**

---

## 0. 한 줄 결론

**1차 배포(플레이 내부 테스트)에는 넣지 말 것.** 대신 `imageKey` 이름 바꾸기(1시간, 동작 변화 0)만
같이 올리고, 화면 작업은 v1.1 로 미룬다. 근거는 §6.

그리고 조사 중에 전제가 하나 뒤집혔다 — **이미지 로더(Coil)가 반드시 필요한 게 아니다.** 서버가
주는 이미지는 열린 집합이 아니라 **닫힌 16장**이고 업로드 경로가 없다. 16장을 앱에 넣으면
**96 KB** 로 끝나고, Coil 을 들이면 **약 1 MB + 신규 전이 의존성 6개**다. §1 을 먼저 읽을 것.

---

## 1. 이미지 로더를 어떻게 들일 것인가

### 1.1 먼저 확인한 사실 — 이건 "사진 업로드"가 아니다

운영 OpenAPI(`prod-openapi-2.json`)에 **업로드 엔드포인트가 없다.** 있는 건 세 개뿐이다.

| 경로 | 스키마 | 비고 |
|---|---|---|
| `GET /api/v1/users/profile-images` | `[{ key, url }]` (`ProfileImageResponse`) | 둘 다 그냥 `string`. 인증 필요 |
| `PATCH /api/v1/users/me` | `UpdateProfileRequest { nickname?, imageKey? }` | `imageKey` 는 "목록의 key 중 하나". 생략하면 유지 |
| `DELETE /api/v1/users/me/profile-image` | 본문 없음 → 204 | 멱등 |

그리고 CDN 을 직접 확인한 결과(읽기 요청만):

```
https://cdn.moyeota.co.kr/profile/presets/01.png → 200, 512x512 PNG(RGBA), 147 KB
                                      ... 16.png → 200, 512x512 PNG(RGBA), 202 KB
                                          17.png → 403   ← 16장이 전부다
cache-control: public, max-age=31536000, immutable · x-cache: Hit from cloudfront
16장 합계 2.65 MB
```

즉 **"프로필 이미지"는 서버가 가진 16장짜리 고정 일러스트 중 하나를 고르는 것**이지,
사용자가 임의의 사진을 올리는 기능이 아니다. 이미지가 무한히 늘어나지 않는다면 네트워크
이미지 로더는 **선택**이지 필수가 아니다.

### 1.2 안 A — 16장을 앱에 넣는다 (추천)

실제 CDN 에셋을 받아 변환해 측정했다(`cwebp`, 원본 512×512 PNG 기준):

| 변환 | 16장 합계 | 장당 |
|---|---|---|
| 원본 PNG 512px | 2,650 KB | 166 KB |
| WebP 256px q82 | **96 KB** | 6 KB |
| WebP 192px q82 | 71 KB | 4 KB |
| WebP 128px q82 | 43 KB | 2 KB |

그림이 **평면 일러스트 + 투명 배경의 원형**(고양이·동물 캐릭터 류)이라 손실 WebP 에 극단적으로 잘
눌린다. 쓰이는 최대 크기는 80 dp(`PartnerProfileScreen.kt:101`)이고 3x 밀도에서 240 px 이므로
**256 px 이면 충분하고 오히려 넉넉하다.**

- **비용: APK +96 KB (0.09 %).** 신규 Gradle 의존성 0, 신규 매니페스트 항목 0.
- 오프라인에서 즉시 그려진다. 로딩·실패 상태가 **아예 존재하지 않는다**.
- 치러야 할 값: 서버 소유 아트를 바이너리에 복제한다. 서버가 17번째를 추가하거나 그림을 바꾸면
  앱을 다시 올리기 전까지 반영되지 않는다(모르는 key 는 기존 이니셜 원으로 폴백 → 깨지진 않는다).
- key→로컬 리소스 매핑은 `profile/presets/NN.png` 의 `NN` 을 뽑아 16칸 표를 찾는 식.
  `imageUrl`(전체 URL)도 끝의 `NN.png` 로 같은 표를 탄다.

> **권장 형태(하이브리드):** 그림은 로컬에서 그리되 **목록·순서는 `GET /users/profile-images` 를
> 그대로 따른다.** 어떤 key 를, 어떤 순서로 보여 줄지는 서버가 정하고, 픽셀만 로컬에서 꺼낸다.
> 조회가 실패하면 로컬 16장을 고정 순서로 보여 준다. 서버가 주인 자리를 유지하면서 오프라인도 된다.

### 1.3 안 B — Coil

굳이 쓴다면 **버전은 3.3.0 이다. 최신(3.6.3)이 아니다.** 이건 취향이 아니라 해석 결과다.

| Coil | kotlin-stdlib 요구 | CMP foundation → androidx compose | 이 프로젝트에서 |
|---|---|---|---|
| 3.6.3 (최신) | **2.4.10** | 1.12.0 → **androidx 1.12.0** | stdlib 이 컴파일러(2.2.10)보다 높고, Compose 를 BOM 위로 밀어 올림 |
| 3.5.0 | 2.4.0 | 1.11.1 → 1.11.1 | 같은 종류의 문제 |
| 3.4.0 | 2.3.10 | 1.9.3 → 1.9.3 | stdlib 여전히 높음 |
| **3.3.0** | **2.2.0** | 1.8.2 → androidx 1.8.2 | **둘 다 프로젝트 값 이하 → 아무것도 안 밀어 올림** |

- 프로젝트: Kotlin **2.2.10**, Compose BOM **2026.06.01**(= `androidx.compose.foundation` **1.11.4**,
  material3 1.4.0), AGP 9.2.1.
- Coil 3.6.3 을 넣으면 `org.jetbrains.compose.foundation:1.12.0` 이 `androidx.compose.foundation:1.12.0`
  을 요구한다. BOM 제약은 strict 가 아니라 **높은 쪽이 이긴다** → foundation/ui/runtime 만 1.12.0 으로
  올라가고 material3 는 BOM 의 1.4.0 에 남는다. **BOM 이 Compose 버전을 더 이상 통제하지 못하게 된다.**
  출시 2주 전에 만들 상태가 아니다.
- 다만 **Kotlin 메타데이터는 전부 mv=2.0** 이라(클래스 파일 직접 확인) 컴파일이 터지지는 않는다.
  위험은 "빌드 실패"가 아니라 "조용한 버전 드리프트"다.
- Coil 3.3.0 은 lifecycle 2.8.7(프로젝트 2.11.0), core-ktx 1.15.0(프로젝트 1.19.0), okio 3.15.0,
  okhttp 4.12.0(프로젝트 5.1.0) — 전부 프로젝트 값이 이긴다. 깨끗하다.

**크기(실측, AAR 기준):**

| 묶음 | 크기 |
|---|---|
| coil-compose / coil / coil-core / coil-compose-core / network-okhttp / network-core | 690 KB |
| **신규** androidx.appcompat:appcompat-resources | 62 KB |
| **신규** androidx.vectordrawable + vectordrawable-animated (appcompat-resources 가 끌고 옴) | 66 KB |
| **신규** androidx.exifinterface | 74 KB |
| **신규** androidx.profileinstaller (+ startup-runtime, concurrent-futures, listenablefuture) | 53 KB |
| **신규** accompanist-drawablepainter | 11 KB |
| 합계 | **≈ 0.95 MB** → dex 로 **APK +0.8~1.2 MB** |

**APK 크기 이야기는 결론을 바꾸지 않는다.** 현재 릴리스 APK(`moyeota-v2026.1014.1.apk`, 108.5 MB)를
뜯어 보면:

```
lib/x86_64   26.1M   lib/x86      25.5M
lib/arm64-v8a 24.8M  lib/armeabi-v7a 18.9M   ← 네이티브 95 MB = 88 %
classes*.dex 11.4M(압축)                      ← 코드는 전부 합쳐 11 MB
resources.arsc 0.8M
```

108 MB 는 **ABI 4종 네이티브 라이브러리**(네이버 지도 + play-services)와, `app/build.gradle.kts` 의
`release { optimization { enable = false } }`(R8 꺼짐) 때문이다. Coil 은 유니버설 APK 의 **+1 %**,
AAB 실다운로드(≈37 MB)의 **+3 %** 다. **크기를 줄이고 싶다면 레버는 Coil 이 아니라 ABI 분할과 R8 이다.**
그래도 안 A(96 KB)가 안 B(~1 MB)보다 10배 작은 건 사실이고, 그게 선택의 근거다.

**Coil 을 쓸 때만 생기는 추가 비용:**
- `profileinstaller` 가 `androidx.startup` InitializationProvider + ProfileInstallReceiver 를
  머지된 매니페스트에 꽂는다 — 출시 직전에 늘릴 앱 시작 경로가 아니다.
- **CDN 요청에 Bearer 가 붙으면 안 된다.** 앱의 인증 OkHttp 클라이언트를 Coil 에 넘기면 토큰이
  CloudFront 로 나가고, CDN 의 403 이 토큰 재발급 인터셉터를 깨운다. 평문 클라이언트를 따로 줘야 한다.
- 36 을 처음 열면 **2.65 MB 를 내려받는다**(16 × 512 px PNG). 셀룰러에서 눈에 띄게 느리다.
- `size()` 힌트를 안 주면 16 × 512×512 ARGB = **비트맵 16 MB** 를 디코딩한다.

### 1.4 결정

**안 A(하이브리드).** 안 B 는 사용자가 **임의의 사진을 올릴 수 있게 되는 날** 필요해지고, 그 날은
서버 스펙상 아직 없다. 그때 Coil 3.3.0 을 넣으면 된다.

---

## 2. 어디에 무엇을 그릴 것인가 (파일:라인)

### 2.1 데이터 계층 — 지금 계약이 **이미 틀렸다**

| 파일:라인 | 지금 | 해야 할 일 |
|---|---|---|
| `data/.../dto/UserDtos.kt:36-39` | `UpdateProfileRequest(nickname, imageUrl)` | **`imageUrl` → `imageKey`**. 서버에 그 필드가 더는 없어 **조용히 버려진다**. KDoc(`:27-33`)도 전제가 틀렸다 |
| `data/.../dto/UserDtos.kt:21-24` | `UserProfileResponse(uuid, name)` | `imageUrl: String? = null` 추가 — 운영 `UserResponse` 스키마에 이제 `imageUrl` 이 있다 |
| `data/.../UserMappers.kt:13-16` | uuid·name 만 매핑 | `imageUrl` 전달 |
| `domain/.../model/User.kt:44-47` | `UserProfileInfo(uuid, name)` | `imageUrl` 추가 |
| `domain/.../repository/AuthRepository.kt:130` | `updateProfile(nickname, imageUrl)` | `updateProfile(nickname, imageKey)` |
| `data/.../repository/RemoteAuthRepository.kt:204-205` | 위 호출 | 시그니처 맞춤 |
| `data/.../remote/UserApi.kt` (신규) | — | `@GET("api/v1/users/profile-images")`, `@DELETE("api/v1/users/me/profile-image")`. **인증 클라이언트 쪽**이다(`/api/v1/users/**` 는 permitAll 아님 — UserApi KDoc `:90-95` 가 설명하는 그 이유) |
| `domain/.../model/` (신규) | — | `data class ProfileImage(val key: String, val url: String)` |
| `AuthError` | — | `INVALID_PROFILE_IMAGE`(USER112) + 한국어 문구 |
| `presentation/.../core/UserProfile.kt:29, 96-99` | `UserNameState.Resolved(name)` | 35·36 이 공유하는 유일한 홀더다. `imageUrl`(또는 key)도 여기에 실어야 한다 |

**깨질 테스트:** `RemoteAuthRepositoryTest.kt:408-420`(`UpdateProfileRequest(nickname, imageUrl=null)`
단언), `:781`·`:816`(가짜 구현), `FcmTokenRegistrarTest.kt:212`, `UserMappersTest.kt:58`,
`AuthenticatedPathContractTest.kt:82-84`(새 경로 2개 단언 추가 필요).

### 2.2 36 프로필 수정

| 파일:라인 | 할 일 |
|---|---|
| `ProfileEditScreen.kt:43-46` | **KDoc 전제가 죽었다.** "프로필 사진은 업로드할 곳이 없어 보낼 값을 만들 수 없다" → 이제 만들 수 있다. 다시 쓴다 |
| `ProfileEditScreen.kt:85` | "닉네임을 바꿀 수 있어요" → 이미지까지 포함하는 문구로 |
| `ProfileEditScreen.kt:82` 부근 | 닉네임 필드 **위**에 이미지 영역 신규: 현재 아바타(96 dp) + 16장 4×4 선택 + 「기본 이미지로 되돌리기」 |
| `ProfileEditScreen.kt:57-67` | 파라미터 추가: `images`, `selectedKey`, `onSelectImage`, `onResetImage` |
| `ProfileEditRoute.kt:174` | `updateProfile(nickname, imageUrl = null)` → `updateProfile(nickname, imageKey = selectedKey)` |
| `ProfileEditRoute.kt:66-76` | **`canSaveNickname` 이 닉네임만 본다.** 닉네임을 그대로 두고 **이미지만 바꾸면 「저장」이 영원히 꺼져 있다.** `nicknameChanged \|\| imageChanged` 로 넓혀야 한다 ← 가장 놓치기 쉬운 버그 |
| `ProfileEditRoute.kt:90-99` | `UiState` 에 `images`·`selectedKey`·`initialKey` 추가 |

**구현 함정 2개 (미리 못 박아 둠):**

1. **`LazyVerticalGrid` 를 쓰면 안 된다.** 이 화면 본문은 `ProfileEditScreen.kt:79` 에서 이미
   `verticalScroll` 안이다. 세로 스크롤 안에 세로 Lazy 격자를 넣으면
   `Vertically scrollable component was measured with an infinity maximum height` 로 **런타임에 죽는다.**
   16장은 개수가 고정이므로 `Row` 4개(또는 `FlowRow`)로 고정 배치한다.
2. **「기본으로 되돌리기」를 언제 켤지 판단할 근거가 지금은 없다.** 서버는 `imageUrl` 을 절대 null 로
   주지 않는다(고른 적 없어도 기본 이미지 URL). 기본 이미지 URL 이 16장 중 하나와 같다면 앱은
   "고른 적 없음"과 "01 을 골랐음"을 구분할 수 없다 → §7 결정 2.
   당장은 **항상 켜 두는 쪽**이 안전하다(DELETE 가 멱등이라 눌러도 해롭지 않다).

### 2.3 35 마이페이지

| 파일:라인 | 할 일 |
|---|---|
| `MyPageScreen.kt:174` | `AvatarCircle(size = 56.dp)` → 고른 이미지 표시 |
| `MyPageScreen.kt:196-198` | 「닉네임 바꾸기」 → 「프로필 수정」. 바로 위 주석이 "36 에서 바꿀 수 있는 게 닉네임 하나뿐이라 그렇게 적는다"고 이유를 적어 뒀는데, **그 이유가 만료된다** |

### 2.4 멤버 표시 — 지금은 무엇을 그리고 있나

`MemberDisplay.kt:62-82` `MemberAvatar` 는 **닉네임 첫 글자 이니셜 원**을 그린다. 탈퇴·닉네임
없음이면 `"?"`(`:63-67`), 본인은 Primary50 배경 + Primary600 글자, 남은 SurfaceSoft + TextMute.
기본 아이콘이 아니라 **이니셜**이다.

`:54-60` 의 KDoc 이 "일부만 사진이 보이는 상태가 더 어색하다"며 **의도적으로** 전부 이니셜로 통일해
뒀다. 그리기 시작하면 이 전제가 바뀌는데, 지금은 서버가 살아 있는 사용자에게 `imageUrl` 을 항상 주므로
**실제로 폴백으로 떨어지는 건 탈퇴 회원뿐**이다 — 그건 원래 다르게 보여야 하는 대상이라 허용 가능하다.

**함수 본문 한 군데만 고치면 네 화면이 따라온다**(호출부 전부 이미 `User` 를 넘기고, `User.imageUrl`
은 `User.kt:21` 에 이미 있다 — 배선 작업 없음):

- `MatchWaitingScreen.kt:477` (36 dp) · `RideDetailScreen.kt:328` (40 dp)
- `PartnerProfileScreen.kt:101` (80 dp) · `JoinConfirmScreen.kt:473` (40 dp)

**이번에 안 되는 자리:**

| 파일:라인 | 왜 |
|---|---|
| `ChatListScreen.kt:153` | 방 단위 줄이라 **사용자 데이터가 없다** — 자리표시로 남긴다 |
| `ExploreScreen.kt:601` `AvatarStack` | 인원 **수**만 받는다. 사용자가 없다 — 남긴다 |
| `ChatScreen.kt:517` | `ChatUiMessage`(`:83`)에 `senderName` 만 있다. `ChatMember.imageUrl`(`domain/.../Chat.kt:86`)에서 **새 필드를 배선해야** 한다 — 유일하게 진짜 배선이 필요한 자리 |

---

## 3. 오프라인·실패 처리

**안 A 를 고르면 처리할 게 거의 없다.** 그림이 로컬이라 로딩 상태도 실패 상태도 생기지 않는다.

| 상황 | 표시 |
|---|---|
| 모르는 key / 모르는 URL 형태 | 기존 이니셜 원(`MemberDisplay.kt:63-80`) 그대로 |
| 탈퇴 회원 (`isWithdrawn`, `MemberDisplay.kt:33`) | `"?"` 원 그대로 — **이미지가 생겨도 그대로 둔다** |
| `GET /users/profile-images` 실패 | 로컬 16장을 고정 순서로 보여 준다. 화면을 막지 않는다 |
| `PATCH` 400 `INVALID_PROFILE_IMAGE(USER112)` | 앱이 서버 목록의 key 만 보내므로 정상 경로에선 안 난다. 나면 "잠시 후 다시 시도해 주세요" 수준의 문구 + 선택 되돌리기 |
| `DELETE` 실패 | 멱등이라 그냥 재시도 가능. 화면 상태를 되돌린다 |

즉 **지금의 대체 표시를 그대로 쓰면 된다.** 새로 만들 폴백이 없다.

(안 B 였다면: 아바타마다 로딩/성공/실패 3상태 × 5개 호출부 + placeholder·error painter + 36 첫
진입 2.65 MB 다운로드 + 비트맵 메모리 관리. 이게 두 안의 실질적 작업량 차이다.)

---

## 4. 단계 분할

| 단계 | 내용 | 작업량 | 위험 |
|---|---|---|---|
| **0** | **`imageUrl` → `imageKey` 이름 바꾸기**(`UserDtos.kt:36-39`, `AuthRepository.kt:130`, `RemoteAuthRepository.kt:204`, `ProfileEditRoute.kt:174`, 테스트 4개). 앱은 지금도 null 만 보내므로 **동작 변화 0** | ~1 h | **없음**. 지금 올려도 됨 |
| **1** | **가장 작은 쓸모 있는 조각 — 36 에서 고르기 + 35 에 반영.** 16장 WebP 번들(96 KB), key→리소스 표, 36 이미지 격자 + 되돌리기, `MyPageScreen.kt:174`. 데이터: 목록 조회·PATCH imageKey·DELETE·`UserProfileResponse.imageUrl` | 1~1.5 d (QA 포함) | 중하. 35/36 안에 갇혀 있어 **다른 흐름에 닿지 않는다** |
| **2** | **멤버 아바타** — `MemberDisplay.kt:62` 본문 한 군데 → 4개 화면 | ~0.5 d | 하. 단 **매칭·탑승 핵심 흐름 4개 화면**을 건드린다. 회귀하면 아바타 없는 것보다 훨씬 손해 |
| **3** | **채팅 말풍선 아바타** — `ChatUiMessage` 에 `senderImageUrl` 배선(`ChatScreen.kt:83, 517`) | ~0.5 d | 중. 채팅이 앱에서 상태가 가장 복잡한 화면 |
| **4** | Coil 3.3.0 도입 | — | **임의 사진 업로드가 생기기 전까지 불필요** |

1 과 2 사이에 경계를 둔 게 핵심이다. **1 은 마이페이지 안에서 끝나고, 2 부터 매칭 흐름을 건드린다.**

---

## 5. 번들 에셋 만드는 법 (1단계용 메모)

```
# 원본은 CDN 에 공개돼 있다(읽기만)
for i in $(seq -w 1 16); do curl -sO https://cdn.moyeota.co.kr/profile/presets/$i.png; done
# 256 px 손실 WebP — 16장 96 KB
cwebp -resize 256 256 -q 82 -alpha_q 90 NN.png -o profile_preset_NN.webp
```

→ `core/designsystem/src/main/res/drawable-nodpi/profile_preset_01..16.webp`
→ key→리소스 16칸 표 + `fun profileImageRes(keyOrUrl: String?): Int?`(모르면 null → 이니셜 원)

단, **디자이너의 원본 에셋을 받는 쪽이 낫다**. 위는 CDN PNG 재인코딩이라 손실이 한 번 더 들어간다
(36~96 dp 에서는 육안으로 구분 안 됨을 확인했지만, 원본이 있으면 원본을 쓴다).

---

## 6. 1차 배포에 넣을 것인가 — 권고: **아니오**

### 미루는 쪽의 근거

1. **아무것도 막고 있지 않다.** 아바타가 이니셜 원이라서 앱을 못 쓰는 사람은 없다. 그리고 이니셜 원은
   미완성 상태가 아니라 **문서화된 설계 결정**이다(`MemberDisplay.kt:54-60`). 의도적으로 보이는 이유는
   실제로 의도적이기 때문이다.
2. **하필 가장 중요한 배선 두 곳을 가장 나쁜 시점에 건드린다.** `AuthRepository.updateProfile` 시그니처
   (프로필 쓰기 유일 경로)와 `MemberAvatar`(핵심 매칭 흐름 4개 화면). 내부 테스트 빌드의 임무는
   **매칭·채팅·배차가 도는지 확인하는 것**인데, 그 직전에 저기를 흔들면 올라오는 버그 리포트마다
   "원래 있던 건가 방금 넣은 건가"를 먼저 따져야 한다.
3. **출시 체크리스트가 이미 빡빡하다.** 백엔드 결함 2건(D-1 좌표 전치, D-2 startRide 미저장)이 열려
   있고 요청 5건이 대기 중이다. 출시 전 남은 시간을 아바타에 쓴다는 건 저기에 안 쓴다는 뜻이다.
4. **미뤄도 나중에 더 비싸지지 않는다.** 1단계는 35/36 안에 갇혀 있고 다른 흐름이 여기에 의존하지
   않는다. v1.1 feature 로 깔끔하게 떨어진다.
5. **지금 할 가치가 있는 건 0단계뿐이다.** `UpdateProfileRequest.imageUrl` 은 서버가 더는 읽지 않는
   필드다. 앱은 항상 null 을 보내니 동작엔 영향이 없지만, 그대로 두면 **다음 사람이 이미지 기능을
   붙일 때 조용히 버려지는 필드로 반나절을 태운다.** 1시간, 런타임 위험 0 — 이건 릴리스에 같이 올린다.

### 정직하게 말하는 반대 근거

백엔드가 2026-10-15 에 이걸 **이미 운영에 배포했다.** 지금 CDN 에 16장이 올라가 있고 아무도 안 쓴다.
내부 테스트의 목적이 이해관계자에게 제품을 **보여 주는** 것이라면, 1.5일로 살 수 있는 "완성돼 보임"
효과가 이만한 작업은 지금 없다. **일정에 여유가 있다면 1단계만(2단계는 빼고)** 넣는 건 방어 가능하다 —
1단계는 매칭 흐름에서 격리돼 있기 때문이다. 그래도 내 권고는 아니오지만, 받아들일 수 있는 "예"는 이 모양이다.

**절대 1차에 넣지 말 것: 2단계(멤버 아바타)와 3단계(채팅).**

---

## 7. 사용자가 결정해야 할 것

1. **번들 vs 로더** — 16장을 APK 에 넣을까(96 KB, 오프라인, 의존성 0), Coil 3.3.0 을 넣을까(~1 MB,
   아트의 주인이 서버로 유지). → 내 권고: **번들 + 목록은 서버에서**(§1.2 하이브리드).
2. **기본 이미지를 구분할 수 있나** — `GET /local/users/info` 의 `imageUrl` 이, 고른 적 없는 사용자에게
   **16장과 구분되는 별도 URL**(예: `default.png`)을 주는가, 아니면 16장 중 하나인가? 구분이 안 되면
   36 은 "고른 적 없음"과 "01 을 골랐음"을 구별할 수 없고 「되돌리기」의 활성/비활성을 정할 수 없다.
   → 백엔드 확인 필요. 답이 늦으면 **항상 활성**(멱등이라 안전)으로 간다.
3. **아트의 주인** — 16장 일러스트는 누가 소유하고, 바뀔 예정이 있나? 번들한다면 디자이너 원본이
   필요하다(없으면 CDN PNG 재인코딩으로 진행 가능 — 품질은 확인했다).
4. **1차에 넣을지** — 내 권고는 **0단계만 v1, 1~3단계는 v1.1**. 다르게 가려면 "1단계까지만" 이
   유일하게 방어 가능한 선이다.
5. (별건) **APK 108 MB 를 줄일 생각이 있나** — 88 %가 ABI 4종 네이티브다. ABI 분할이나 R8 활성화
   (`app/build.gradle.kts` 의 `optimization { enable = false }`)가 진짜 레버다. 프로필 이미지와는
   무관하지만 크기가 걱정이라면 여기다.
