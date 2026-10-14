# Google Play 「데이터 보안(Data safety)」 양식 답안

> 조사일 2026-10-14 · 대상 `com.moyeota` (versionName `app/build.gradle.kts:80` · applicationId `:76` 기준)
> 근거: 앱 소스(`frontend`) · 백엔드(`backend`) · 배포된 법적 고지(`_workspace/legal-site/`)
> **이 문서는 조사·작성만 했다. 코드와 법적 고지는 한 줄도 고치지 않았다.**

## 이 문서를 읽는 법

- 표 1 이 콘솔에 그대로 넣을 답이다. 「근거」 칸의 `파일:라인` 은 **그 답이 참인 이유**이지
  참고 자료가 아니다. 서버나 앱이 바뀌면 그 줄부터 다시 본다.
- 확인하지 못한 것은 지어내지 않고 **「미확인 — 확인 방법」** 으로 남겼다(§5).
- 공개 산출물에 전화번호를 넣지 않았다. 공개 연락처는 `codingforest17@gmail.com` 하나다.

## 먼저 알아야 할 결론 네 가지

1. **애널리틱스·크래시 리포팅 SDK 는 앱에 없다.** 추측이 아니라 의존성 전수 확인 결과다 —
   Firebase 는 **messaging 하나만** 들어 있다(`app/build.gradle.kts:141-142`,
   `gradle/libs.versions.toml:51-52`). `crashlytics|analytics|sentry|amplitude|mixpanel|appsflyer|
   braze|bugsnag` 를 `*.kts *.toml *.kt *.xml *.json *.properties` 전수 grep 해서 **0건**이고,
   `app/google-services.json` 에도 analytics 블록이 없다. `MoyeotaApplication.onCreate`
   (`app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt:25-30`)가 초기화하는 것은
   `AppContainer` · 알림 채널 · FCM 토큰 조회뿐이다.
   → **「앱 정보 및 성능」 세 항목(비정상 종료 로그 · 진단 · 기타 앱 성능 데이터)은 전부 「수집 안 함」.**

2. **성별은 「선택」이 아니라 「필수」다.** 작업 지시서에는 "통계 목적의 선택 항목"이라고 적혀 있지만
   코드는 그렇지 않다 — `SignupDraft.toNewUser()` 가 `val genderValue = gender ?: return null`
   (`presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/SignupDraft.kt:62`)로
   **성별이 없으면 가입 요청을 만들지도 않고**, 서버 `UserRegisterRequest` 의 `gender` 에
   `@NotNull` 이 걸려 있다(백엔드 #227 미반영). 처리방침도 2026-10-14 에 「필수」로 되돌렸다
   (`_workspace/legal-site/_changes.md` §2).
   **「매칭에 쓰지 않는다」는 참이다**(백엔드 `matching` 패키지에 `gender` 0건) — 그러나
   「선택 항목이다」는 거짓이므로 양식에도 **필수**로 적어야 한다.

3. **제3자 「공유(shared)」는 전 항목 「아니오」로 답한다.** Google/네이버/카카오/SOLAPI/AWS 는
   모두 모여타의 지시로 처리하는 **서비스 제공업체(처리위탁)** 이고, Play 의 「공유」 정의는
   서비스 제공업체로의 전송을 명시적으로 제외한다. 처리방침 제7조·제8조가 이미 그 구조로 적혀 있다.
   다만 제7조의 **제목**이 「제3자 제공과 국외 이전」이라 심사자가 「공유=예」를 기대할 수 있다 — 표 2 의 M-1 참고.

4. **택시 관련 데이터는 현재 생성되지 않는다.** 운영 서버가 동승 모드(`TAXI_ENABLED:false`,
   `backend/src/main/resources/application-prod.yaml:46-48`)라 26 운행 중 화면에 닿을 수 없고,
   27 긴급 신고는 **26 에서만** 들어간다(`presentation/.../core/MainNavGraph.kt:754` 가 유일한
   진입점, `docs/MODE-ROUTES.md` 화면별 표의 26·27 행). 그래서 **긴급 신고 위치는 지금 서버로 가지 않는다.**
   → 양식의 「정확한 위치」는 긴급 신고 때문이 아니라 **지도/합승방 생성** 때문에 「수집함」이다.

---

## 표 1 — 데이터 보안 양식 답안

「수집」 = 앱이 기기 밖(모여타 서버 또는 SDK가 그 제공자)으로 전송함.
「공유」 = Play 정의상 제3자 전송(= 서비스 제공업체 전송은 제외).
「필수·선택」 = Play 의 「데이터 수집이 필수인가 / 사용자가 선택할 수 있는가」.

### 개인 정보 (Personal info)

| 범주(콘솔 표기) | 실제 항목 | 수집 | 공유 | 필수·선택 | 목적 | 근거 코드 |
|---|---|---|---|---|---|---|
| **이름** | 실명 (`name`) | **예** | 아니오 | **필수** | 계정 관리 · 사기 방지·보안 및 규정 준수 | `data/src/main/kotlin/com/moyeota/data/remote/dto/AuthDtos.kt:24` (`RegisterRequestDto.name`) · `data/src/main/kotlin/com/moyeota/data/remote/AuthApi.kt:65-66` (`POST api/v1/auth/register`) · `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/SignupDraft.kt:64` (비어 있으면 가입 차단) |
| **이메일 주소** | 가입 이메일 (`email`) | **예** | 아니오 | **필수** | 계정 관리 | `data/.../dto/AuthDtos.kt:28` · `SignupDraft.kt:64` |
| **전화번호** | 휴대폰번호 (`phoneNumber`) | **예** | 아니오 | **필수** | 계정 관리 · 사기 방지·보안 및 규정 준수 | `data/.../dto/AuthDtos.kt:26` (가입) · `data/.../dto/AuthDtos.kt:42-44` (`SmsSendRequestDto`) · `data/.../remote/AuthApi.kt:46-47`, `55-56` (인증번호 발송·확인) |
| **사용자 ID** | 로그인 아이디(`loginId`) · 닉네임(`nickname`) · 서버 공개 식별자(`publicId`) | **예** | 아니오 | **필수** | 앱 기능 · 계정 관리 | `data/.../dto/AuthDtos.kt:21,23` · `data/.../dto/ChatDtos.kt:104-109` (`ChatMemberResponse.publicId/nickname`) · `data/.../remote/UserApi.kt:48-49` + `data/.../dto/UserDtos.kt:36-39` (닉네임 변경) |
| **기타 정보** | 생년월일(`birthDate`) · 성별(`gender`) | **예** | 아니오 | **필수** | 사기 방지·보안 및 규정 준수(만 14세 미만 차단) · 계정 관리 · **분석**(성별 통계) | `data/.../dto/AuthDtos.kt:25,27` · `SignupDraft.kt:61-62` (둘 다 없으면 가입 요청 미생성) · `domain/src/main/kotlin/com/moyeota/domain/model/Auth.kt:121` (`MIN_AGE = 14`) |
| 주소 / 인종·민족 / 정치·종교 신념 / 성적 지향 | — | **아니오** | — | — | — | 매니페스트 권한이 `INTERNET`·`POST_NOTIFICATIONS`·`ACCESS_FINE_LOCATION`·`ACCESS_COARSE_LOCATION` 넷뿐 (`app/src/main/AndroidManifest.xml:5-11`), 가입 요청 필드도 9개로 고정 (`AuthDtos.kt:20-30`) |

> **「기타 정보」의 목적에 「분석」을 넣는 이유**: 성별은 매칭에 쓰이지 않고 이용자 구성 집계에만 쓴다
> (처리방침 제2조 제7호). 백엔드 `matching` 패키지에 `gender` 참조가 **0건**이다.
> 처리방침 제9조의 「분석 도구를 사용하지 않습니다」는 **SDK/도구** 이야기이지 **목적** 이야기가 아니다 —
> 표 2 의 M-2 에서 이 어긋나 보이는 지점을 다룬다.

### 위치 (Location)

| 범주 | 수집 | 공유 | 필수·선택 | 목적 | 근거 코드 |
|---|---|---|---|---|---|
| **정확한 위치** | **예** | 아니오 | **선택** | 앱 기능 | **취득**: `presentation/src/main/kotlin/com/moyeota/presentation/core/location/MyLocationState.kt:73-76` (두 권한), `:85` (지도 1초 주기), `:88` (지도 없는 15 화면 5초 주기), `:126-128` (화면이 보이는 동안에만 구독 — 백그라운드 위치 없음)<br>**서버 전송 ①** 지도 지점 → 주소: `data/.../remote/PlaceApi.kt:27-31` (`GET api/v1/places/reverse?latitude&longitude`), 호출처 `presentation/.../feature/home/DestinationConfirmRoute.kt:199`<br>**서버 전송 ②** 주변 합승방: `data/.../remote/MatchingApi.kt:38-45` (`swLat/swLng/neLat/neLng`)<br>**서버 전송 ③** 합승방 생성: `data/.../dto/PartyDtos.kt:167-175` (`OpenPartyRequestDto.departureLat/Lng, destinationLat/Lng`) — 출발지 기본값이 **기기 실좌표**다 (`presentation/.../feature/home/DestinationRoute.kt:31-37`, `:208`)<br>**서버 전송 ④** 자주 가는 곳: `data/.../remote/PlaceApi.kt:34-35` + `data/.../dto/PlaceDtos.kt:35-41`<br>**(비활성) 긴급 신고**: `app/src/main/kotlin/com/moyeota/app/ReportLocationProvider.kt:27-31` → `data/.../dto/ReportDtos.kt:14-18` — 현재 도달 불가(위 결론 4) |
| **대략적인 위치** | **예** | 아니오 | **선택** | 앱 기능 | `ACCESS_COARSE_LOCATION` 만 허용돼도 동작한다 — `MyLocationState.kt:73-76` 가 두 권한을 함께 요청하고 `:62` 주석대로 **하나라도** 허용되면 `isGranted` 가 참이다. `ReportLocationProvider.kt:34-39` 도 동일 |

> **「선택」으로 답하는 근거**: 권한을 거부해도 가입·로그인·채팅·목적지 검색이 모두 된다.
> `MyLocationState.kt:127-128` — 「권한이 없거나 좌표를 한 번도 못 받으면 `coordinates` 가 null 로
> 남고 호출부가 각자 처리한다 … 어느 경로에서도 예외를 던지지 않는다」. 처리방침 제3조 제6항과 일치.
>
> **네이버 지도 SDK 는 스스로 위치를 추적하지 않는다.** `locationSource`·`LocationTrackingMode` 사용처가
> **0건**이고, `locationOverlay` 는 **우리가 구한 좌표를 그려 넣는 용도로만** 쓴다
> (`core/designsystem/.../MyLocationOverlay.kt:75-91`, `core/designsystem/.../RouteMapView.kt:160-165`).
> 기기에서 네이버로 나가는 것은 **지도 타일/카메라 영역**이다.

### 메시지 (Messages)

| 범주 | 수집 | 공유 | 필수·선택 | 목적 | 근거 코드 |
|---|---|---|---|---|---|
| **기타 인앱 메시지** | **예** | 아니오 | **선택** | 앱 기능 | `data/.../remote/ChatApi.kt:88-89` (`POST api/v1/chat-rooms/{id}/messages`) + `data/.../dto/ChatDtos.kt:53-55` (`SendMessageRequestDto.content`) · 수신 `ChatApi.kt:72-79` · 실시간 `data/.../remote/chat/ChatSocket.kt:176-178` (WSS) · 서버 저장이 전제인 DTO `ChatDtos.kt:63-88` (`createdAt`, `deleted`) |
| 이메일 / SMS·MMS | **아니오** | — | — | — | 앱이 기기의 메일·문자함을 읽는 권한이 없다 (`AndroidManifest.xml:5-11`). 인증 문자는 **서버가 보내는 것**이지 앱이 읽는 것이 아니다 |

> **「선택」인 이유**: 채팅은 합승방에 들어간 뒤 사용자가 보낼 때만 생성된다.
> **푸시 미리보기 주의**: 새 메시지 알림이 **발신자 닉네임 + 본문 앞 30자** 를 Google(FCM)을 거쳐
> 수신 기기로 보낸다(`app/src/main/kotlin/com/moyeota/app/MoyeotaFirebaseMessagingService.kt:88-90`
> 의 서버 페이로드 설명, 처리방침 제4조 제4항). 그래도 **공유=아니오** — Google 은 서비스 제공업체다.

### 앱 활동 (App activity)

| 범주 | 수집 | 공유 | 필수·선택 | 목적 | 근거 코드 |
|---|---|---|---|---|---|
| **앱 상호작용** | **예** | 아니오 | **필수** | 앱 기능 · 사기 방지·보안 및 규정 준수 | 서버가 사용자 단위로 행동을 기록한다 — 합승방 참여/이탈 `data/.../remote/MatchingApi.kt:93-104`, 합승 완료 `:110-111`, 채팅방 입·퇴장 `data/.../remote/ChatApi.kt:44-49`, 읽음 처리 `:64-70`, 알림 음소거 `:58-62`. 요청마다 Bearer 로 사용자가 특정된다 (`data/.../remote/auth/AuthHeaderInterceptor.kt`) |
| **인앱 검색 기록** | **예** (보수적 답변) | 아니오 | **선택** | 앱 기능 | `data/.../remote/PlaceApi.kt:19-20` (`GET api/v1/places?query=`) · `ChatApi.kt:103-110` (채팅 내 검색). **검색 기록으로 저장하지는 않지만**(백엔드 `backend/.../KakaoPlaceClient.java:23-46` — 결과만 반환하고 저장하지 않는다) 실패 시 질의어가 로그에 남는다(`KakaoPlaceClient.java:36` `log.warn("카카오 장소 검색 실패 query={}", query, e)`) → 「일시적 처리」 면제를 주장하지 않고 **수집함**으로 답한다 |
| **기타 사용자 생성 콘텐츠** | **예** | 아니오 | **선택** | 앱 기능 | 자주 가는 곳(장소 이름·도로명 주소) `data/.../dto/PlaceDtos.kt:35-41` · 합승방의 출발·도착 **주소 문자열** `data/.../dto/PartyDtos.kt:172-173`. (좌표는 「정확한 위치」로 이미 신고) |
| 설치된 앱 / 기타 작업 | **아니오** | — | — | — | `QUERY_ALL_PACKAGES`·`<queries>` 선언 없음 (`AndroidManifest.xml` 전체 확인) |
| **웹 검색 기록** | **아니오** | — | — | — | 앱에 WebView·Custom Tabs 가 **0건**이다. 외부 링크는 `ACTION_VIEW` 로 브라우저에 넘긴다 |

> 「인앱 검색 기록」과 「기타 사용자 생성 콘텐츠」는 **판단이 갈릴 수 있는 칸**이다 — §5 의 Q3 참고.

### 기기 또는 기타 ID (Device or other IDs)

| 범주 | 수집 | 공유 | 필수·선택 | 목적 | 근거 코드 |
|---|---|---|---|---|---|
| **기기 또는 기타 ID** | **예** | 아니오 | **필수** | 앱 기능 | **FCM 등록 토큰**: `app/src/main/kotlin/com/moyeota/app/MoyeotaApplication.kt:46-61` (앱 시작마다 조회) → `:71-74` → `data/src/main/kotlin/com/moyeota/data/push/FcmTokenRegistrar.kt:92-94` → `data/.../remote/UserApi.kt:59-60` (`PUT api/v1/users/me/fcm-token`) + `data/.../dto/UserDtos.kt:73-75`. 로그아웃 시 삭제 `FcmTokenRegistrar.kt:77-78` → `UserApi.kt:70-71`, 호출처 `data/.../repository/RemoteAuthRepository.kt:137`.<br>**Firebase 설치 ID**: `firebase-messaging` 이 끌고 오는 Installations 구성요소가 기기마다 만들어 Google 로 보낸다 (`app/build.gradle.kts:141-142`) |

> **「필수」인 이유**: 토큰 조회가 `Application.onCreate` 에서 **무조건** 돌고
> (`MoyeotaApplication.kt:29`), 알림 권한을 거부해도 발급·등록을 막는 분기가 없다.
> **광고 ID(ADID)는 쓰지 않는다** — `play-services-ads*` 의존성 0건, `AdvertisingIdClient` 참조 0건.

### 수집하지 않는 나머지 범주 — 전부 「아니오」

| 콘솔 범주 | 「아니오」인 근거 |
|---|---|
| 재무 정보 (결제 정보·구매 내역·신용 점수 등) | 결제 SDK 0건 (`billing|admob|payment|iap` grep 0건). 승객용 앱에 결제 기능이 없다 |
| 건강 및 피트니스 | 관련 권한·API 0건 |
| 사진 및 동영상 | `READ_MEDIA_*`·카메라 권한 없음 (`AndroidManifest.xml:5-11`). 프로필 이미지 **업로드 코드 0건** — `UpdateProfileRequest.imageUrl` (`data/.../dto/UserDtos.kt:38`)은 서버 계약에만 있고 앱이 채우지 않는다 |
| 오디오 파일 | `RECORD_AUDIO` 없음 |
| 파일 및 문서 | 저장소 권한 없음 |
| 캘린더 | 권한 없음 |
| 연락처 | `READ_CONTACTS` 없음 |
| **앱 정보 및 성능 — 비정상 종료 로그 / 진단 / 기타 앱 성능 데이터** | **크래시·성능 SDK 가 하나도 없다** — 위 「결론 1」의 전수 확인 결과. `firebase-crashlytics`·`firebase-perf` 미도입, `app/google-services.json` 에 analytics 설정 없음 |

### 보안 관행 (Security practices) — 양식 뒷부분

| 질문 | 답 | 근거 |
|---|---|---|
| 수집하는 모든 사용자 데이터가 **전송 중에 암호화**되나요? | **예** | 기본 baseUrl 이 HTTPS (`app/build.gradle.kts:36-38` → `https://api.moyeota.p-e.kr/`), 소켓은 WSS (`data/.../remote/chat/ChatSocket.kt:176-178`). 평문 허용은 **디버그 전용 매니페스트에만** 있다 (`app/src/debug/AndroidManifest.xml` — `src/main` 에는 `usesCleartextTraffic` 선언 자체가 없어 targetSdk 36 기본값 false 가 적용된다, `app/src/main/AndroidManifest.xml:13-16` 주석) |
| 사용자가 **데이터 삭제를 요청**할 수 있나요? | **예 — 앱 내 삭제 + 웹 요청 모두** | 앱 내: `data/.../remote/UserApi.kt:104-105` (`DELETE api/v1/users/me`, 비밀번호 본인확인) + `data/.../dto/UserDtos.kt:56-58`, 화면 `presentation/.../feature/mypage/AccountDeleteScreen.kt`. 서버: `backend/.../user/deletion/application/UserDeletionService.java` `delete()`. 웹: `https://legal.moyeota.p-e.kr/account-deletion/` (앱 미설치자도 이메일로 요청 가능) |
| **계정 삭제 URL** | `https://legal.moyeota.p-e.kr/account-deletion/` | `presentation/src/main/kotlin/com/moyeota/presentation/core/SupportLinks.kt:53` + 같은 파일 `:25-26` 의 요건 메모. 페이지가 Play 요건을 모두 담는다 — 앱 내 절차 · 앱 없이 이메일 요청 · 삭제되는 데이터 · 보관되는 데이터와 기간 · 처리 기간 |
| **독립적인 보안 검토**를 받았나요 | **아니오** (해당 없음) | 외부 감사 기록 없음. 과장하지 않는다 |
| Play **Families 정책** 대상인가 | **아니오** | 만 14세 미만 가입 차단 (`domain/.../model/Auth.kt:121`, 처리방침 제13조) |

---

## 표 2 — 처리방침과 코드의 불일치

> 「반려」 칸은 Play 심사에서 **데이터 보안 양식 ↔ 처리방침 불일치**로 잡힐 가능성이다.

| # | 처리방침이 말하는 것 | 코드가 하는 것 | 반려 | 어느 쪽을 고치나 |
|---|---|---|---|---|
| **M-1** | 제7조 **제목**이 「개인정보의 **제3자 제공**과 국외 이전」이고, Google·네이버·카카오·SOLAPI 를 「개인정보가 전달되는 회사」 표로 싣는다 | 넷 다 모여타의 지시로 처리하는 **서비스 제공업체**다 — FCM 전달(`MoyeotaApplication.kt:46-74`), 네이버 지도/역지오코딩/경로(`backend/.../NaverApiConfig.java:15` `maps.apigw.ntruss.com`), 카카오 장소 검색(`backend/.../KakaoApiConfig.java:14` `dapi.kakao.com`), SOLAPI 문자 발송(`backend/.../SolapiSmsSender.java:37-40`) | **중** — 양식은 「공유=아니오」인데 방침 제목은 「제3자 제공」이라 심사자가 모순으로 읽을 수 있다 | **방침을 다듬는다.** 제7조 제1항 끝의 「법적으로는 처리위탁에 해당할 수 있습니다」를 **앞으로** 올리거나, 제목을 「외부 서비스로의 전달과 국외 이전」류로 바꾼다. **양식은 「공유=아니오」를 유지한다** — Play 정의상 서비스 제공업체 전송은 공유가 아니다 |
| **M-2** | 제9조 「광고·**분석** 도구를 사용하지 않습니다 … Firebase Analytics, Crashlytics … 모두 사용하지 않습니다」 | 사실이다(SDK 0건). 그런데 **양식에서는 성별의 목적으로 「분석」을 골라야 한다** — 제2조 제7호가 「이용자 구성에 관한 통계 작성(성별)」이라고 적고 있고, Play 의 목적 목록에 「통계」가 따로 없다 | **하** — 조문을 읽으면 모순이 아니다(도구 vs 목적) | **둘 다 그대로 두고 양식에 「분석」을 고른다.** 불안하면 제9조 첫 줄에 「(도구를 쓰지 않는다는 뜻이며, 제2조 제7호의 성별 통계는 모여타가 직접 집계합니다)」 한 구절을 더한다 |
| **M-3** | 제3조 제3항 「위치가 모여타 서버로 전송되는 경우는 **아래 네 가지뿐**입니다」 — ①주소 확인 ②주변 합승방 ③합승방 생성 ④긴급 신고 | **다섯 번째가 있다.** 자주 가는 곳 저장이 좌표를 보내고 **서버에 저장된다** — `data/.../remote/PlaceApi.kt:34-35` + `data/.../dto/PlaceDtos.kt:35-41` (`latitude`, `longitude`). 제1조 표2·제6조·제10조는 자주 가는 곳을 제대로 싣고 있어 **제3조만 빠졌다** | **중** — 「뿐」이라는 단정이 거짓이 된다. 심사와 무관하게 **고지 정확성 문제** | **방침을 고친다.** 제3조 제3항 표에 다섯 번째 행을 넣는다 — 「자주 가는 곳을 저장할 때 / 그 장소의 좌표와 도로명 주소 / 저장됩니다(제6조, 탈퇴 시 삭제)」. ※ 저장되는 좌표는 **검색 결과 장소**의 좌표이지 기기 실좌표가 아니다(`presentation/.../feature/home/DestinationScreen.kt:377` — 검색 결과 행의 ★ 에서만 등록) |
| **M-4** | 제1조 표2 현재 위치 「언제 만들어지는지 = **지도가 있는 화면**을 보고 있을 때, 지도에서 지점을 찍어 주소를 확인할 때 …」 | 지도가 **없는** 15 목적지 입력 화면도 위치를 구독한다 — `MyLocationState.kt:87-88` (`FormLocationIntervalMs` = 「지도가 없는 화면(15 목적지 입력)용」), 호출처 `presentation/.../feature/home/DestinationRoute.kt:203-209` | **하** | **방침을 고친다.** 「지도가 있는 화면을 보고 있을 때」 → 「지도가 있는 화면을 보고 있을 때와, 목적지 입력 화면에서 출발지를 자동으로 채울 때」 |
| **M-5** | 제1조 표2 장소 검색어 「모여타 서버에 **검색 기록으로 저장하지 않습니다**」 | 참이다(저장 코드 없음). 다만 **실패 시 질의어가 서버 로그에 남는다** — `backend/.../KakaoPlaceClient.java:36`. 제1조 표3 이 이미 「서비스 운영 로그 … 장소 검색어 같은 값이 포함될 수 있습니다」로 밝혀 두었다 | **하** — 방침 안에서 이미 해소돼 있다 | **방침은 그대로.** 대신 **양식에서 「인앱 검색 기록 = 수집함」** 으로 답한다(일시적 처리 면제를 주장하지 않는다). 면제를 주장하려면 `KakaoPlaceClient.java:36` 의 질의어 로깅부터 지워야 한다 |
| **M-6** | 제1조 「**성별**」이 필수 항목 표에 들어 있다(2026-10-14 환원) | 일치한다 — `SignupDraft.kt:62` · 서버 `@NotNull` | — | **고칠 것 없음.** 단, **작업 지시서의 「성별은 선택」 전제가 코드와 다르다** — 양식에 「선택」으로 적으면 그게 불일치가 된다 |
| **M-7** | 제12조 제4항 「앱과 서버 사이의 통신은 암호화된 주소(HTTPS·WSS)로 이루어집니다」 | 릴리스에서는 참 — 평문 허용이 디버그 매니페스트에만 있다(`app/src/debug/AndroidManifest.xml`) | — | **고칠 것 없음.** 양식 「전송 중 암호화 = 예」와 정합 |
| **M-8** | 제10조 제2항 「회원 탈퇴는 **앱의 마이페이지에서 직접** 할 수 있습니다」 | 일치 — `UserApi.kt:104-105`, `AccountDeleteScreen.kt`, 서버 `UserDeletionService.delete()`(비밀번호 확인 + `ONGOING_PARTY_EXISTS` 차단) | — | **고칠 것 없음.** 양식 「삭제 요청 가능 = 예」와 정합 |
| **M-9** | 제5조 제1항 「같은 합승방의 이용자에게 보이는 정보는 닉네임, 누적 합승 완료 횟수, **프로필 이미지**」 | 프로필 이미지 **업로드 경로가 앱에 없다** — `UpdateProfileRequest.imageUrl` 를 채우는 호출처 0건. 방침도 「현재 앱에는 프로필 사진을 올리는 기능이 없어 기본 이미지로 표시됩니다」로 이미 단서를 달았다 | — | **고칠 것 없음.** 양식에서도 「사진 및 동영상 = 수집 안 함」이 맞다 |

---

## 콘솔에 그대로 입력할 값

| 입력란 | 값 |
|---|---|
| **개인정보처리방침 URL** (앱 콘텐츠 · 데이터 보안 양쪽) | `https://legal.moyeota.p-e.kr/privacy/` |
| **계정 삭제 URL** (데이터 보안 → 데이터 삭제) | `https://legal.moyeota.p-e.kr/account-deletion/` |
| 이용약관 URL (스토어 등록정보에 넣는다면) | `https://legal.moyeota.p-e.kr/terms/` |
| 개발자 연락처 이메일 | `codingforest17@gmail.com` |
| 패키지명 | `com.moyeota` (`app/build.gradle.kts:76`) |
| 데이터 삭제 방식 | **앱 내 삭제 + 웹 요청 둘 다 제공** — 「계정 삭제를 요청할 수 있는 방법을 앱 안에 제공합니다」 체크 |

세 URL 모두 앱 안의 **한 파일**에서 나온다 — `presentation/src/main/kotlin/com/moyeota/presentation/core/SupportLinks.kt:32,41,44,53`.
**콘솔 값을 바꾸면 이 파일도 같이 고쳐야 한다.** 로그인 없이 열리고 분석·광고 스크립트와 쿠키가 없다
(`_workspace/legal-site/assets/style.css` 외 외부 요청 0, 처리방침 제9조 제4항).

### 양식에서 「아니오」로 답하는 나머지 질문

- 독립적인 보안 검토: **아니오**
- 데이터가 전송 중 암호화됨: **예**
- 광고 ID 수집: **아니오**
- Families 정책 적용: **아니오** (만 14세 미만 가입 차단)

---

## 내가 코드만 보고 결정할 수 없는 것 — 물어봐야 할 미결 항목

### Q1. 「성별 선택 항목화」가 이번 배포에 들어가나

지시서는 성별을 「선택」으로 전제했지만 **코드는 필수**다(앱 `SignupDraft.kt:62` · 서버 `@NotNull`).
- **지금 코드 그대로 배포한다면** → 양식에 **필수**로 적는다(표 1 이 그렇게 적혀 있다).
- **배포 전에 선택으로 바꾼다면** → 백엔드 #227 의 `@NotNull` 제거와 앱 `SignupDraft.toNewUser()`
  수정이 **둘 다** 끝난 뒤에만 「선택」으로 적을 수 있고, 처리방침 제1조도 함께 되돌려야 한다
  (되돌리는 자리는 `_workspace/legal-site/README.md` 의 「서버 동작이 바뀌면 고쳐야 할 자리」 1행에 이미 적혀 있다).

**→ 어느 쪽인지 알려 주면 표 1 의 해당 행만 고치면 된다.**

### Q2. 제7조를 「처리위탁」 쪽으로 다듬을 것인가 (M-1)

양식은 **공유=아니오**가 맞다. 다만 방침 제7조 제목이 「제3자 제공」이라 심사자가 교차 확인할 때
걸릴 수 있다. ①제목·문구를 다듬을지 ②그대로 두고 양식만 「아니오」로 낼지 결정이 필요하다.
(법적 고지 수정은 `_check.py` 통과 → S3 sync → CloudFront 무효화 절차를 탄다 — `legal-site/README.md`.)

### Q3. 「인앱 검색 기록」과 「기타 사용자 생성 콘텐츠」를 신고할 것인가

둘 다 **과소 신고보다 과다 신고가 안전한** 회색지대다.
- **인앱 검색 기록**: 서버가 검색 기록을 만들지 않지만 실패 시 질의어가 로그에 남는다
  (`backend/.../KakaoPlaceClient.java:36`). 표 1 은 **수집함**으로 적었다.
- **기타 사용자 생성 콘텐츠**: 자주 가는 곳의 장소 이름과 합승방의 출발·도착 주소 문자열.
  「위치」로만 신고하고 이 칸을 비우는 선택지도 있다. 표 1 은 **수집함**으로 적었다.

**→ 보수적으로 둘 다 신고하는 현재 안으로 갈지 확인 필요.**

### Q4. 택시 기능을 언제 켜는가

지금은 꺼져 있어(`application-prod.yaml:46-48`) 긴급 신고 위치·기사 위치가 생성되지 않는다.
**켜는 순간 양식도 같이 고쳐야 한다** — 위치의 목적에 「사기 방지·보안 및 규정 준수」가 추가되고,
처리방침의 조건부 단서 18곳을 지워야 한다(`legal-site/README.md` 의 해당 행).
**→ 이번 배포 중에 켤 계획이 있는지.**

### Q5. 「개인정보 보호책임자」 실명 공개 범위

처리방침 제15조에 보호책임자 실명이 들어가 있다(이미 배포된 상태). Play 양식에는 입력란이 없어
이번 작업의 대상은 아니지만, 공개 범위를 재검토할 생각이 있는지만 확인해 두면 좋겠다.
**전화번호는 네 문서 어디에도 없고 이 문서에도 넣지 않았다.**

---

## 미확인 — 확인 방법

| 항목 | 왜 확인 못 했나 | 확인 방법 |
|---|---|---|
| **네이버 지도 SDK 가 기기에서 네이버로 실제로 무엇을 보내는지** | 바이너리 SDK 라 소스가 없다. 앱 코드 쪽은 다 봤다 — 위치 추적 기능(`locationSource`)을 **켜지 않았고** 카메라 좌표만 넘긴다 | 릴리스 빌드에 프록시(mitmproxy)를 물려 `*.ntruss.com`·`*.map.naver.com` 요청을 캡처한다. 또는 네이버 클라우드 플랫폼의 Maps SDK 개인정보 처리 안내를 근거로 삼는다 |
| **Firebase Installations 가 보내는 값의 정확한 범위** | `firebase-messaging` 이 전이 의존성으로 끌고 오는 구성요소라 우리 코드에 호출처가 없다 | Google 의 「Firebase 및 Google Play 데이터 공개」 문서(Firebase 제품별 데이터 수집 표)를 근거로 삼는다. 현재 표 1 은 처리방침 제1조 표3·제9조 제3항과 같은 범위(설치 식별자)로 적었다 |
| **서비스 운영 로그에 실제로 어떤 필드가 남는지** | 운영 로그 설정이 `logging.pattern.correlation` 한 줄뿐이고(`backend/src/main/resources/application-prod.yaml:58-60`) 실물 로그를 보지 않았다 | 운영 Loki/Grafana 에서 하루치 로그를 표본으로 떠서 좌표·검색어·전화번호가 평문으로 남는지 본다. 전화번호는 `Masking.phone()` 으로 가려지는 것을 코드에서 확인했다(`SolapiSmsSender.java:42`, `LoggingSmsSender.java:16`) |
| **AWS 리전·보관 위치** | 인프라 설정이 이 저장소에 없다 | 처리방침 제8조가 「서울 리전(ap-northeast-2)」이라고 적고 있고 `legal-site/README.md` 의 S3 버킷도 ap-northeast-2 다. 운영 콘솔에서 RDS/EC2 리전을 한 번 더 확인하면 끝난다 |

---

## 부록 — 제3자별 전달 범위 (양식의 「공유」 판단 근거)

| 회사 | 어디서 호출하나 | 전달되는 것 | Play 분류 |
|---|---|---|---|
| **Google (FCM · Firebase Installations)** | **앱에서** — `app/build.gradle.kts:141-142`, `MoyeotaApplication.kt:46-74` | 푸시 토큰, 설치 ID, 알림 페이로드(알림 종류·합승방/메시지 번호·발신자 공개 식별자, 채팅은 **닉네임 + 본문 앞 30자**) | 서비스 제공업체 → **공유 아님** |
| **네이버 클라우드 플랫폼** | **앱에서** 지도 타일(`core/designsystem/.../NaverMapView.kt`, 키는 `AndroidManifest.xml:29-31`) · **서버에서** 역지오코딩(`backend/.../NaverReverseGeoCodingClient.java:30-34`)과 경로(`backend/.../NaverDirectionsClient.java:26-34`), 공통 클라이언트 `NaverApiConfig.java:15` | 지도 영역·중심 좌표, 주소를 구할 지점 좌표, 출발·도착 좌표. **이름·연락처·계정 식별자는 보내지 않는다** | 서비스 제공업체 → **공유 아님** |
| **카카오** | **서버에서만** — `backend/.../KakaoPlaceClient.java:26-33`, `KakaoApiConfig.java:14` (`https://dapi.kakao.com/v2/local/search/keyword.json`) | 이용자가 입력한 **장소 검색어**만. 좌표·계정 식별자 없음 | 서비스 제공업체 → **공유 아님** |
| **SOLAPI** | **서버에서만** — `backend/.../SolapiSmsSender.java:37-40`, 활성 조건 `application-prod.yaml:52-53` (`SMS_PROVIDER:solapi`) | **수신 휴대폰번호**, 인증번호가 담긴 문자 본문 | 서비스 제공업체 → **공유 아님** |
| **AWS** | 인프라 전체 | 서버·DB·캐시에 저장되는 모든 데이터 (서울 리전) | 서비스 제공업체(수탁자) → **공유 아님** |

**앱이 직접 호출하는 외부 서비스는 Google(FCM)과 네이버(지도)뿐이다.** 카카오와 SOLAPI 는
서버가 호출하므로 **앱의 SDK 목록에는 나타나지 않는다** — 그래도 Play 양식은 「앱이 수집하는 데이터」
기준이므로 전화번호·검색어는 앱이 서버로 보낸 시점에 이미 「수집」이고, 그 뒤 경로는 위 표대로다.
