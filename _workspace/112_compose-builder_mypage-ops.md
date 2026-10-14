# 112 compose-builder — 운영용 마이페이지 정비 (35 재구성 + 36 프로필 수정)

브랜치 `feat/mypage-ops` (#48 위). presentation 모듈만 — data/domain 무변경.
입력: `_workspace/00_input_mypage-ops.md`. 원칙: **백엔드 API 가 없는 기능은 만들지 않는다, 「준비 중」 금지.**

## 변경 파일

### 신규
| 파일 | 내용 |
|---|---|
| `presentation/core/SupportLinks.kt` | 약관·개인정보·문의 주소 한 곳. **지금은 셋 다 null** → 해당 행 숨김 |
| `presentation/core/ExternalIntents.kt` | 앱 밖 이동(OS 알림 설정·메일·브라우저) + `appVersionName()` + `supportMailBody()` |
| `presentation/feature/mypage/ProfileEditScreen.kt` | 36 화면(스테이트리스) |
| `presentation/feature/mypage/ProfileEditRoute.kt` | 36 ViewModel·순수 규칙 함수 |
| `presentation/src/test/.../feature/mypage/NicknameRulesTest.kt` | 저장 조건·오류 문구 11건 |
| `presentation/src/test/.../core/SupportMailBodyTest.kt` | 문의 메일 본문 3건 |

### 수정
| 파일 | 내용 |
|---|---|
| `feature/mypage/MyPageScreen.kt` | 프로필 카드 탭 가능(→36) + 메뉴 카드 2그룹 삽입. KDoc·프리뷰 갱신 |
| `core/UserProfile.kt` | `UserProfileViewModel.refresh()` 추가(+`loadJob` 단일화) |
| `core/Routes.kt` | `PROFILE_EDIT = "mypage/profile-edit"` // 36 |
| `core/MainNavGraph.kt` | `composable(Routes.PROFILE_EDIT)`, 35 의 `onEditProfile`, 섹션 주석에 36 |
| `docs/MODE-ROUTES.md` | 36 행 추가(공통/공통), 35 행 설명 갱신 |
| `docs/BACKEND-GAP-CLEANUP.md` | "프로필 수정 = 화면만 만들면 됨" → 완료 표시, 알림 설정·탈퇴 항목 사실 갱신 |

`ModeDebugOptions`·`ModeDebugSection` 은 손대지 않았고 맨 아래 위치 그대로다. 다른 화면 무변경.

## QA 진입 경로

1. **35 재구성** — 앱 실행 → 하단탭 「마이페이지」
   - 프로필 카드(이름 + 「닉네임 바꾸기」 + 꺾쇠) 탭 → **36 프로필 수정**
   - 「설정」 카드 → 「알림 설정」 탭 → **OS 앱 알림 설정 화면**이 떠야 한다(API 26+. 그 아래면 앱 상세 설정)
   - 「지원」 카드는 **지금 안 보이는 게 정상** (아래 참조)
   - 로그아웃(확인 다이얼로그) · 우측 버전 표기 · 디버그 빌드의 개발자 옵션은 전과 같다
2. **36 프로필 수정** — 35 프로필 카드 → 닉네임이 현재 이름으로 채워져 있음
   - 뒤로가기(←) → 35
   - 1자로 지우기 → 「저장」 꺼짐 / 특수문자 → 형식 오류 / 「모여타」 → 금칙어 오류
   - 2~10자 새 값 입력 후 0.5초 멈추면 자동 중복 확인 → 「사용 가능한 닉네임이에요」(초록) 또는 「이미 사용 중인 닉네임이에요」(필드 빨강)
   - 현재 이름으로 되돌리면 「지금 쓰고 있는 닉네임이에요」 + 「저장」 꺼짐 (**서버에 묻지 않는다**)
   - 「저장」 → 35 로 복귀하고 **프로필 카드와 14 홈 인사말이 새 닉네임으로 바뀌어야 한다**
   - 다른 기기/계정으로 같은 닉네임을 선점한 뒤 저장 → 필드가 중복 오류로 바뀌고 「저장」 꺼짐
3. **백엔드 교차 확인** — 저장 후 `GET /api/v1/local/users/info` 의 `name` 이 새 닉네임이어야 한다.

## ⚠️ QA 가 반드시 알아야 할 것 — SupportLinks 가 전부 null

운영 도메인·문의 메일이 아직 없어 `presentation/core/SupportLinks.kt` 의 세 값이 **모두 null** 이고,
그래서 **「지원」 카드(문의하기·이용약관·개인정보 처리방침)가 화면에 아예 그려지지 않는다.**
「준비 중」 자리표시를 두지 않기로 한 결과이며 **결함이 아니다.**

그 세 행을 확인하려면 임시 값을 넣고 빌드한다(커밋하지 말 것):

```kotlin
val TERMS_URL: String? = "https://example.com/terms"
val PRIVACY_URL: String? = "https://example.com/privacy"
val CONTACT_EMAIL: String? = "test@example.com"
```

확인할 것
- 세 행이 「지원」 카드로 묶여 보이고, 각 행 오른쪽에 외부 표시(↗)가 있다
- 「이용약관」·「개인정보 처리방침」 → 기본 브라우저로 그 주소가 열린다
- 「문의하기」 → 메일 앱 작성 화면, 받는 사람 = 그 주소, 제목 `[모여타 문의]`,
  본문 아래에 `앱 버전 / Android / 기기` 세 줄이 채워져 있다
- 값 하나만 null 로 두면 그 행만 빠지고 나머지는 남는다
- 메일 앱이 없는 이미지(일부 에뮬레이터)에서 「문의하기」 → **크래시 없이** 주소를 알려주는 토스트

## 설계 결정

1. **「준비 중」 대신 행 삭제.** 주소가 null 이면 행을, 그룹이 비면 카드·머리말까지 그리지 않는다
   (`MyPageMenuCard` 가 `items.isEmpty()` 에 early return). 눌러도 아무 일 없는 행은 고장으로 읽히고
   스토어 심사에서도 죽은 링크로 잡힌다.
2. **알림 설정은 앱 토글이 아니라 OS 설정으로 넘겼다.** 서버에 알림 설정 저장 API 가 없어, 앱이 자체
   토글을 들면 기기를 바꾸는 순간 되살아나고 서버는 그대로 푸시를 보낸다 — 지키지 못할 약속이 된다.
   OS 설정은 실제로 적용되는 유일한 스위치다. API 26 미만은 앱 상세 설정으로 폴백.
3. **외부 표시(↗) vs 꺾쇠(›).** 입력 지시는 약관·개인정보·문의에 ↗ 였지만, 알림 설정도 앱을 벗어나므로
   **메뉴 네 행 모두 ↗** 로 통일했다. 꺾쇠는 앱 안에서 이동하는 유일한 지점인 프로필 카드만 쓴다
   — 두 기호가 "여기서 앱을 벗어나는가"를 가른다. 유니코드 화살표 대신 Canvas 로 그렸다(글꼴 의존 제거).
4. **Intent 는 화면이 아니라 `core/ExternalIntents.kt`.** Context 확장 3개 + `ActivityNotFoundException`
   일괄 처리. 스낵바가 아니라 **토스트**를 쓴 이유: 35 에 Scaffold·SnackbarHost 가 없고, 이 안내는
   화면이 보관할 상태가 아니라 일회성 사실이다. 호스트를 세우려면 화면 골격을 바꿔야 한다.
   `appVersionName()` 도 여기 뒀다 — 버전 표기와 메일 본문이 같은 값을 써야 한다.
5. **35 는 Route 를 만들지 않았다.** 서버 데이터는 여전히 이름 하나뿐이고(NavGraph 가 주입),
   Intent 발사는 `LocalContext` 로 화면에서 바로 한다(기존에도 versionName 을 그렇게 읽었다).
   NavGraph 로 빼면 Context 만 전달하는 콜백 4개가 늘어난다.
6. **36 의 닉네임 규칙·문구·확인 방식은 10 프로필 만들기에서 그대로 가져왔다.**
   `NicknamePolicy`(2~10자 한글·영문·숫자 + 금칙어), `NicknameCheckState`, 500ms 디바운스,
   `authUserMessage()`. 같은 값을 두 화면이 다른 기준으로 받으면 둘 중 하나는 거짓말이 된다.
7. **"현재 닉네임과 같으면 서버에 묻지 않는다"** — 내 닉네임을 `isNicknameTaken` 에 물으면 당연히
   `true` 가 돌아온다. 그걸 그대로 띄우면 자기 이름이 남의 것처럼 보인다. `isNicknameUnchanged` 가
   확인·오류·저장 가능 판단 모두에서 먼저 걸러낸다.
8. **확인 중·확인 실패에는 「저장」 을 막지 않는다.** 10 과 같은 판단으로, 최종 판정은 `PATCH /users/me`
   의 409 다(확인과 저장 사이에 누가 선점할 수 있다). 네트워크가 나쁠 때 버튼이 영영 꺼져 있는 쪽이 손해다.
9. **저장 실패 중 중복·형식 오류는 배너가 아니라 필드로 되돌린다.** `check` 를 Taken/InvalidFormat 으로
   바꿔 필드가 빨갛게 말하고 「저장」 이 함께 꺼진다 — 같은 문장을 버튼 아래에 한 번 더 띄우지 않는다.
   그 외(401·네트워크 등)만 `NoticeBanner(ERROR)`(04a 로그인과 같은 표기)로 입력 아래에 남긴다.
10. **입력값을 화면이 아니라 ViewModel 이 들고 있다.** 프리필 값이 서버 조회 결과로 **늦게 도착**하므로,
    화면이 들면 "아직 못 받은 이름"과 "사용자가 지운 이름"을 구분할 수 없다. `prefill()` 은 한 번만 먹고,
    조회가 늦은 사이 사용자가 이미 입력했다면 그 입력을 지키고 비교 기준만 세운다.
11. **`UserProfileViewModel.refresh()`** — init 의 collect 는 `distinctUntilChanged` 라 같은 사용자에게는
    깨어나지 않으므로 별도 입구가 필요했다. 두 경로를 `loadJob` 하나로 모아 항상 마지막 요청만 살린다
    (겹치면 늦게 온 쪽이 이기고, 그게 이전 사용자의 이름일 수 있다). 재조회는 **스켈레톤을 띄우지 않고**,
    조회가 실패하면 이미 보여 주던 이름을 지우지 않는다 — 저장은 서버에서 끝났는데 조회만 실패한 상황에서
    「이름 미설정」으로 되돌리면 저장이 날아간 것처럼 보인다.
12. 프로필 카드 부제를 「프로필 수정」 이 아니라 **「닉네임 바꾸기」** 로 적었다. 36 에서 바꿀 수 있는 건
    닉네임 하나뿐이라, 「프로필 수정」 은 사진·전화번호까지 기대하게 만든다(화면 제목은 지시대로 「프로필 수정」).

## 만들지 않은 것 (백엔드 요청 = 이슈)

| 기능 | 필요한 것 | 비고 |
|---|---|---|
| 계정 탈퇴 | 탈퇴 API + 웹 탈퇴 안내 페이지 | **Google Play 필수 요건** — 이게 없으면 스토어 배포가 막힐 수 있다. 가장 급하다 |
| 탑승 기록 | 완료 방 목록(`GET /users/me/parties?status=FINISHED`) | 34 화면은 이미 삭제됨 |
| 즐겨찾기 삭제/수정 | `DELETE/PATCH /users/me/favorite-places` | 현재 POST/GET 만 |
| 프로필 사진 | 이미지 업로드(스토리지) API | `PATCH /users/me` 가 `imageUrl` 을 받지만 **올릴 곳이 없어** 보낼 값을 만들 수 없다. 36 은 항상 `imageUrl = null` 로 보낸다 |
| 알림 on/off | 알림 설정 저장 API | 생기면 OS 설정 이동을 앱 토글로 바꿀 수 있다 |
| 약관·개인정보 페이지 | 운영 도메인에 공개 페이지 2개 | 주소가 정해지면 `SupportLinks` 한 곳만 채우면 끝 |
| 문의 메일 계정 | 운영 메일 주소 | 같음 |

## 빌드·테스트

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew :app:assembleDebug :presentation:testDebugUnitTest --console=plain -q
```
통과(출력 없음 = 경고 0). `presentation` 단위 테스트 **101건 0 실패**(이번에 +14: NicknameRulesTest 11,
SupportMailBodyTest 3). `app-debug.apk` 생성 확인. 에뮬레이터 실기 확인은 하지 않았다 — qa-verifier 몫.

## 남은 불확실

- **에뮬레이터 실기 미확인.** 특히 ① `ACTION_APP_NOTIFICATION_SETTINGS` 가 이미지마다 다르게 뜰 수 있음
  ② 메일 앱 없는 이미지의 토스트 폴백 ③ 36 저장 후 35·14 의 이름 갱신 타이밍.
- 36 을 저장하고 **바로 다시** 들어가면(재조회 끝나기 전, ~수백 ms) 입력이 옛 이름으로 채워질 수 있다.
  그때는 「저장」 이 꺼진 상태라 잘못 저장되지는 않고, 재조회가 끝난 뒤 다시 들어가면 정상이다. 수정하지 않았다.
- `SupportLinks` 가 null 인 동안 릴리스 빌드의 마이페이지 메뉴는 「알림 설정」 한 줄뿐이다.
  스토어 제출 전에 약관·개인정보 주소를 채우는 것이 사실상 전제다(Play 콘솔에도 같은 주소가 필요하다).
