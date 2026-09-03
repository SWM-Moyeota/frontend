# 32. 화면 연결 — 하드코딩 "김OO" → 실명 (34 마이페이지 · 14 홈)

작업일: 2026-09-02 · 범위: `presentation/` 만 (data/·domain/ 무수정) · **에뮬레이터 실기 검증 완료**

계약: `_workspace/31_api-integrator_profile.md` — `AuthRepository.getMyProfile(): UserProfileInfo(uuid, name: String?)`

---

## 1. 변경 파일

**신규**

| 파일 | 내용 |
|---|---|
| `presentation/core/UserProfile.kt` | `UserNameState` + `UserProfileViewModel` (세션 캐시 홀더) |

**수정**

| 파일 | 라인 | 내용 |
|---|---|---|
| `presentation/core/MainNavGraph.kt` | 12, 113–116 | `UserProfileViewModel` 을 NavHost 바깥에서 생성·구독 |
| | 287 | `HomeRoute(userName = ...)` |
| | 504 | `MyPageScreen(userName = ...)` |
| `presentation/feature/home/HomeRoute.kt` | 49, 61 | `userName: String?` 파라미터 통과 |
| `presentation/feature/home/HomeScreen.kt` | 85, 239, 247 | `userName: String?` + 이름 없는 인사말 분기 |
| | 491 | Preview 에 실명 주입 |
| `presentation/feature/mypage/MyPageScreen.kt` | 79, 126–160 | 이름 3상태 렌더링 + 인증 배지 중립화 |
| | 487 | Preview |

진입 경로: **로그인 → 14 홈**(인사말) · **하단탭 「마이」 → 35 마이페이지**(프로필 카드).

## 2. 캐시를 화면이 아니라 NavHost 에 둔 이유

`getMyProfile()` 은 계약상 **캐시하지 않는다**(31 보고서 §3) — 보관은 호출부 몫이다.
그런데 이 값이 필요한 화면이 홈·마이 **둘 다 하단탭**이라, 화면별 ViewModel 로 두면
탭을 오갈 때마다 같은 응답을 반복해서 받는다. 그래서 `MainNavHost` 최상단에서
`viewModel(...)` 로 만든다 — 이 지점의 `ViewModelStoreOwner` 는 NavBackStackEntry 가 아니라
**액티비티**라, 네비게이션이 아무리 오가도 인스턴스가 유지된다.

갱신 트리거는 화면 진입이 아니라 **`authState` 의 uuid 변화**다:

```kotlin
repository.authState
    .map { (it as? AuthState.Authenticated)?.userUuid }
    .distinctUntilChanged()
    .collect { uuid -> ... }
```

로그아웃하면 `Loading` 으로 되돌려 다음 사용자 화면에 이전 사용자의 이름이 남지 않게 하고,
uuid 가 null 인 동안에는 **아예 호출하지 않는다** — 토큰이 없으면 401 이 확정이고
그 401 은 재발급으로 풀리지 않는다(31 §3).

## 3. `Loading` 과 `Resolved(null)` 을 나눈 이유

한 상태로 뭉치면 조회가 끝나기 전에 "이름 미설정" 폴백이 **스쳐 보인다** — 로그인 직후
자기 이름이 잠깐 "이름 미설정"으로 보이는 건 버그로 읽힌다. 그래서:

| 상태 | 34 마이페이지 | 14 홈 인사말 |
|---|---|---|
| `Loading` | 88×18 스켈레톤 박스 | "좋은 저녁이에요" (이름 생략) |
| `Resolved("김성윤")` | **김성윤** | "김성윤님, 좋은 저녁이에요" |
| `Resolved(null)` | "이름 미설정" (GrayMute) | "좋은 저녁이에요" |

홈은 인사말 한 줄이라 스켈레톤이 오히려 산만해서 이름만 뺀 형태로 떨어뜨린다.
조회 실패도 `Resolved(null)` 로 흡수한다(`runCatching { ... }.getOrNull()`) —
**이름 하나 때문에 화면 전체를 에러로 막지 않는다.** 시간대별 인사("좋은 저녁이에요")는 기존 그대로다.

## 4. 인증 배지 중립화 — 범위 밖이지만 그냥 둘 수 없던 것

이름이 실제 계정 값으로 바뀌는 순간, 그 옆의 인증 표시는 성격이 달라진다.
"김OO · 부산대학교 · 2026년 3월 인증 · 학생 인증 완료"는 더미 이름 옆에서는 목업이지만,
**"김성윤" 옆에서는 그 계정에 대한 사실 주장**이다. 서버는 인증 정보를 주지 않으므로
가입만 한 계정에도 인증 완료가 뜬다 — 앱이 사용자에게 거짓말을 하는 상태다. 그래서:

- `verifiedLine`(「부산대학교 · 2026년 3월 인증」) 파라미터 **삭제**
- 「학생 인증 완료」 Primary 배지 → 중립 회색 「**인증 정보 준비 중**」
- 아바타 우하단 **체크 배지 제거** (`SmallCheckIcon` 및 미사용 import `border`·`offset` 정리)
  — 회색으로 낮춰도 체크 표시 자체가 "인증됨" 신호라 색만 바꾸는 걸로는 부족했다

매너 98% · 탑승 42회 · 마일리지 0P 는 지시대로 **이번 범위 밖**이라 그대로 뒀다.
다만 같은 종류의 거짓 주장이므로, 서버에 데이터가 생기기 전까지는 남는 부채다(§7).

## 5. 검증

```
./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain
→ BUILD SUCCESSFUL in 8s
```

에뮬레이터 `emulator-5554`, 서버 localhost:8080 (죽이지 않음 — 시작·종료 모두 401 응답 확인).

**작업 전 상태 확인**: 에뮬레이터에 **기사 앱**(`com.moyeota.driver`)이 로그인 화면에서 유휴 상태로
떠 있었다(`profile_00_before.png`). 사용자 작업 중일 수 있어 기사 앱은 건드리지 않고
승객 앱(`com.moyeota`)만 `install -r` 후 `pm clear` 로 로그아웃 상태에서 시작했다.

| # | 스크린샷 | 확인 |
|---|---|---|
| 00 | `profile_00_before.png` | 작업 전 — 기사 앱 로그인 화면(유휴) |
| 01–04 | `profile_01_login` ~ `profile_04_filled` | 온보딩 건너뛰기 → 로그인 폼 → testuser1 입력 |
| 05 | `profile_05_home.png` | **홈 인사말 「김성윤님, 좋은 저녁이에요」** |
| 06 | `profile_06_mypage.png` | **마이페이지 「김성윤」 + 「인증 정보 준비 중」** (체크 배지 없음) |
| 07–08 | `profile_07_logout_confirm`, `profile_08_after_logout` | 로그아웃 → 로그인 화면 |
| 09 | `profile_09_relogin_home.png` | 재로그인 후 이름 복원 |
| 10 | `profile_10_cleanup_login.png` | **정리 완료 — 로그인 화면** |

### 캐시 실측 (logcat)

세션당 1회 호출이라는 주장은 눈으로 확인할 수 없어 HTTP 로그로 셌다.

| 구간 | `local/users/info` 로그 줄 | 해석 |
|---|---|---|
| 로그인 직후 | 2 (`-->` + `<--`) | **1회 호출** |
| 홈↔마이 왕복 2회 후 | 2 (변화 없음) | **재호출 없음** ✅ |
| 로그아웃 중 | 0 | 미로그인 상태에서 호출 안 함 ✅ |
| 재로그인 후 | 2 | **세션 바뀌면 다시 조회** ✅ (일회성 아님) |

마지막 줄이 중요하다 — `init` 에서 한 번만 받는 구조였다면 재로그인 후 0 이 나왔을 것이고,
계정 전환 시 **이전 사용자의 이름이 남는 버그**로 이어졌을 것이다.

## 6. 남은 제약

- 계정 전환(다른 uuid) 갱신은 **논리적으로만 보장**된다 — 테스트 계정이 testuser1 하나뿐이라
  로그아웃→재로그인(같은 uuid)까지만 실측했다. 트리거가 `distinctUntilChanged` 된 uuid 라
  다른 계정이면 당연히 발화하지만, 실측은 아니다.
- `name` 이 null 인 계정(프로필 미작성)의 폴백 「이름 미설정」은 **미실측** — 서버에
  프로필 없는 계정을 만들지 않았다. 매퍼가 빈 문자열·공백까지 null 로 정규화하므로(31 §3)
  화면은 null 한 가지만 본다.
- 닉네임 설정 엔드포인트가 생기면 이 값이 조용히 실명 → 닉네임으로 바뀐다(31 §7).
  현재 문구는 "OO님"이라 닉네임이 와도 어색하지 않다.

## 7. 백엔드 요청 (신규 1건)

**R-6. 마이페이지 프로필 필드** — `GET /api/v1/local/users/info` 가 uuid·name 만 준다.
매너 점수·탑승 횟수·인증 여부(학교/인증일)가 없어 화면이 더미를 그리거나 중립 문구로
비워 두는 수밖에 없다. 인증 배지는 이번에 중립화했지만 매너 98%·탑승 42회는 아직
하드코딩이 남아 있다 — 응답에 필드가 생기면 바로 연결한다.
