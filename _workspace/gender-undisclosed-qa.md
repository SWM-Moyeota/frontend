# 성별 「선택 안 함」 QA 리포트

- 브랜치 `fix/place-roadname-null` · 2026-10-15
- 기기: 에뮬레이터 Pixel_6 (콜드부트, `-no-snapshot-load`), 1080x2400
- APK: `app/build/outputs/apk/debug/app-debug.apk` · BASE_URL = `https://api.moyeota.p-e.kr/` (**운영**, 오버라이드 없음)
- 스크린샷: `/Users/sungyoon/Desktop/moyeota/frontend/_workspace/screenshots/gender-undisclosed/` (gitignore 대상)

## 결론

게이트 `false`(현재 상태) 기준 **통과**. 게이트 `true` 로 켰을 때의 3분할 레이아웃도
360dp·font_scale 1.3·2.0 전부에서 「선택 안 함」 네 글자가 온전했다. 접근성 속성도 실제로 나온다.

다만 font_scale 2.0 에서 **칩이 하단 CTA 에 가려 「선택 안」으로 읽히는 자리**가 있다 (A-1, 아래).
이건 칩이 아니라 화면 10 의 기존 하단 CTA 오버레이 문제이고 게이트가 꺼져 있는 동안은 드러나지 않지만,
**서버 배포 후 게이트를 켜기 전에 먼저 처리해야 한다.**

## 1. 빌드·유닛 테스트 (게이트 `false`)

```
./gradlew :app:assembleDebug :data:testDebugUnitTest :presentation:testDebugUnitTest --console=plain
→ BUILD SUCCESSFUL
```

첫 실행이 전부 UP-TO-DATE / FROM-CACHE 로 나와 **초록 바를 믿지 않고** 결과 디렉터리를 지우고
`--rerun --no-build-cache` 로 실제 실행시킨 뒤, JUnit XML 을 직접 파싱한 수치다.

| 모듈 | XML 파일 | tests | failures | errors | skipped |
|------|---------|-------|----------|--------|---------|
| `:data` | 20 | **283** | 0 | 0 | 0 |
| `:presentation` | 31 | **283** | 0 | 0 | 0 |

기대치(283 / 283 / 실패 0)와 일치.

이번 변경과 직접 닿는 클래스:

| 클래스 | tests | failures |
|--------|-------|----------|
| `com.moyeota.presentation.feature.auth.SignupGenderChoicesTest` | 4 | 0 |
| `com.moyeota.presentation.feature.auth.SignupDraftTest` | 12 | 0 |
| `com.moyeota.data.remote.AuthMappersTest` | 34 | 0 |

## 2. 실기 — 게이트 `false` 에서 선택지 2개

경로: 온보딩 「건너뛰기」 → 04 「이메일로 시작하기」 → **화면 10 「가입 정보를 알려주세요」** → 성별 행까지 스크롤.

- 칩 **2개**(남성 · 여성). 「선택 안 함」 없음. → **통과**
- 스크린샷 `03_gender_row_gate_false.png`
- 남성 선택 후 `04_gate_false_male_selected.png`
- 되돌린 빌드 재확인 `14_gate_false_restored.png` (다시 2개)

## 3. 실기 — 게이트 `true` 3분할 레이아웃

`ServerAcceptsUndisclosedGender = true` 로 **임시** 변경 → `:app:assembleDebug` → 재설치.

### 3-1. 기본 폭 411dp (1080px / 420dpi)

- 칩 **3개**, 「선택 안 함」 한 줄, 잘림 없음 → **통과**
- `05_gate_true_411dp.png`

### 3-2. 좁은 화면 360dp

`wm size 360x800` 은 이 기기에서 쓸 수 없다 — 밀도가 420 이라 폭이 137dp 가 되어 검증 의미가 사라진다.
대신 **`wm density 480`** 으로 1080px / 480dpi = **정확히 360dp × 800dp** 를 만들었다.

- 칩 3개, 각각 폭 312px = **104dp** (구현 KDoc 의 "360dp 폭에서 칩 하나가 104dp" 와 일치)
- 「선택 안 함」 한 줄, 잘림 없음 → **통과**
- `06_gate_true_360dp.png`

### 3-3. 큰 글꼴 (360dp 유지)

| font_scale | 칩 높이 | 「선택 안 함」 | 판정 |
|-----------|--------|--------------|------|
| 1.0 | 144px (48dp) | 1줄 「선택 안 함」 | 통과 |
| **1.3** | 144px (48dp) | 1줄 「선택 안 함」, 잘림 없음 | **통과** · `08_gate_true_360dp_fs1.3.png` |
| **2.0** | 246px (82dp) | **2줄 「선택 안」 / 「함」**, 네 글자 온전 | **통과** · `12_fs2.0_mid.png`, 확대 `12c_crop_fs2.0_mid.png` |

`heightIn(min = 44.dp)` + `maxLines = 2` 가 의도대로 동작한다. 2.0 에서 칩 Box 가 48dp → 82dp 로
실제로 자라고(UI 덤프 bounds 로 확인: `[720,816][1032,1062]`, 높이 246px), 둘째 줄 「함」이 그려진다.
**「선택」으로 잘려 뜻이 뒤집히는 일은 없었다.**

### 3-4. 선택 상태의 시각적 구분

「선택 안 함」을 고른 상태는 미선택과 분명히 다르다 — 테두리 1.5dp Primary500 + 배경 Primary50 +
글자 Bold/Primary500 (미선택은 흰 배경 + 그림자 + SlateGray Medium).

- 아무것도 안 고른 상태: `06_gate_true_360dp.png`
- 「선택 안 함」 고른 상태: `07_gate_true_360dp_undisclosed_selected.png` (360dp),
  `13_fs2.0_selected.png` / 확대 `13c_crop_fs2.0_selected.png` (360dp + fs 2.0, 2줄)
- → **통과**

### A-1. (결함) font_scale 2.0 에서 둘째 줄이 하단 CTA 에 가려 「선택 안」으로 읽히는 자리가 있다

- 재현: 360dp + `font_scale 2.0` + 게이트 `true` → 화면 10 에서 성별 행이 화면 **하단**에 오도록 스크롤
- 증상: 칩에 **「선택 안」**만 보인다. 둘째 줄 「함」이 하단 CTA(「다음」) 영역에 **덮여서** 안 보인다.
  스크린샷 `09_gate_true_360dp_fs2.0.png`, 확대 `09c_crop_fs2.0.png`
- 같은 칩을 화면 중앙으로 스크롤하면 「선택 안 / 함」이 정상적으로 다 보인다 (`12c_crop_fs2.0_mid.png`)
  → **글자 잘림(truncation)이 아니라 하단 CTA 겹침(occlusion)이다.**
- 원인: `ProfileSetupScreen` 의 하단 CTA 가 스크롤 콘텐츠 **위에 겹쳐** 그려진다(스크롤 Column 에
  CTA 높이만큼의 bottom padding 이 없다). 이번 변경과 무관한 **기존 레이아웃 문제**로,
  font_scale 1.0 에서도 「다음」이 아이디 입력란을 덮는 것이 보인다 (`03_gender_row_gate_false.png` 하단).
- 심각도: 게이트가 `false` 인 지금은 드러나지 않는다(두 칩은 어느 배율에서도 1줄). 다만
  **게이트를 켜는 순간 "선택 안" 으로 읽히는 상태가 실제로 도달 가능**해지고, 그게 바로 구현 KDoc 이
  막으려던 실패 양상이다.
- 요청(compose-builder): `ProfileSetupScreen` 의 스크롤 `Column` 에 하단 CTA 높이만큼
  `contentPadding`/`Spacer` 를 주어 마지막 콘텐츠가 CTA 아래로 숨지 않게 할 것.
  **서버 배포 후 게이트를 켜기 전**에 처리 필요.

## 4. 접근성 — 선택 상태가 실제로 노출되는가

`uiautomator dump` 로 확인 (TalkBack 대신 의미 트리를 직접 읽었다).

- 칩마다 `class="android.widget.RadioButton"` 노드가 나온다 → `Role.RadioButton` 이 실려 나간다
- 칩 노드에 `checkable="true" clickable="true" focusable="true"` 가 붙는다
- `checked` 가 선택에 따라 실제로 바뀐다:

```
# 게이트 false, 남성 탭 후
label='남성'      checked=true   role=RadioButton
label='여성'      checked=false  role=RadioButton

# 게이트 true, 「선택 안 함」 탭 후 (360dp)
label='남성'      checked=false  role=RadioButton
label='여성'      checked=false  role=RadioButton
label='선택 안 함'  checked=true   role=RadioButton

# 아무것도 안 고른 초기 상태 — 세 칩 모두 checked=false (「선택 안 함」이 기본 선택처럼 보이지 않는다)
```

→ **통과.** bare `clickable` 이던 이전 구현에서는 `checkable=false`/`checked=false` 로 고정이라
상태가 전혀 전달되지 않았다. Row 의 `selectableGroup()` 도 라디오 묶음으로 묶인다.

## 5. 제출 경로 — 미검증 (의도적)

**지시대로 12 에서 제출하지 않았다. 추가로 10 → 다음 화면 전이 자체도 실행하지 않았다.**

이유 — 운영 서버에 쓰기 요청이 나가기 때문이다:

- 화면 10 의 「다음」은 **휴대폰 문자 인증을 끝내야** 열린다
  (`ProfileSetupScreen.kt` 의 `phoneVerified` 게이트). 인증번호 발송은 운영 서버로 가는 쓰기 요청이고
  하루 한도(`AuthPolicy.SMS_DAILY_LIMIT`)를 태운다
- APK 의 `BuildConfig.BASE_URL` 이 `https://api.moyeota.p-e.kr/` (운영)로 빌드돼 있다
  (`app/build.gradle.kts:37` 기본값, `local.properties`·환경변수 오버라이드 없음)

대신 정적으로 확인한 사실:

- **브리핑의 "10 → 11" 은 코드상 "10 → 12" 다.** `MainNavGraph.kt:464` 에서 10 의 `onNext` 가
  곧장 `Routes.MANNER_PLEDGE` 로 간다. 가입 경로에 11 은 없다(09 가 경로에서 빠지면서 정리된 구조)
- 가입 요청 실행은 12 의 `MannerPledgeRoute` 한 곳 (`MainNavGraph.kt:473`)
- 경계면 교차 읽기 — 성별 값이 서버까지 가는 길에 어긋나는 데가 없다:

| 단계 | 코드 | 값 |
|------|------|-----|
| 도메인 | `domain/model/Auth.kt` `enum Gender` | `MALE` · `FEMALE` · `UNDISCLOSED` |
| 매퍼 | `data/remote/AuthMappers.kt:38` `gender = gender.name` | 문자열 그대로 |
| DTO | `data/remote/dto/AuthDtos.kt` `RegisterRequestDto.gender: String` | non-null, 필드명 `gender` |
| 서버 | `UserRegisterRequest.gender` | `@NotNull` |

필드명 케이스 불일치 없음, nullability 비대칭 없음, 래핑 unwrap 없음. 코드 변경이 필요 없다는
브리핑의 판단이 맞다.

### 게이트 `false` 의 근거를 실측으로 재확인

운영 OpenAPI 스펙을 읽기 전용 GET 으로 직접 확인했다(쓰기 아님):

```
curl -s https://api.moyeota.p-e.kr/v3/api-docs   → http=200
UserRegisterRequest.gender -> {"type":"string","enum":["MALE","FEMALE"]}
```

**운영 서버는 2026-10-15 현재도 `UNDISCLOSED` 를 모른다.** 지금 보내면 400 이 맞고,
게이트 기본값 `false` 가 옳다. 백엔드 PR #235 배포 후 `true` 로 바꾸면 된다.

## 6. 게이트 원복 확인

```
$ grep -n '^internal const val ServerAcceptsUndisclosedGender' \
    presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/SignupFieldSupport.kt
175:internal const val ServerAcceptsUndisclosedGender = false

$ grep -rn 'ServerAcceptsUndisclosedGender *= *true' --include='*.kt' .
(없음)

$ git status --short
 M data/src/main/kotlin/com/moyeota/data/remote/dto/AuthDtos.kt
 M data/src/test/kotlin/com/moyeota/data/remote/AuthMappersTest.kt
 M domain/src/main/kotlin/com/moyeota/domain/model/Auth.kt
 M presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/ProfileSetupScreen.kt
 M presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/SignupFieldSupport.kt
 M presentation/src/test/kotlin/com/moyeota/presentation/feature/auth/SignupDraftTest.kt
?? presentation/src/test/kotlin/com/moyeota/presentation/feature/auth/SignupGenderChoicesTest.kt
```

- **게이트 `false` 로 되돌아갔다.** 변경 파일 목록이 검증 시작 시점과 **동일**하다(늘지도 줄지도 않음)
- `SignupFieldSupport.kt` 의 diff 에 남은 `+internal const val ... = false` 는 이번 기능으로 **새로 생긴**
  줄이라 당연히 추가로 잡히는 것이고, `= true` 는 레포 어디에도 없다
- 원복 상태로 `:app:assembleDebug` + 양 모듈 테스트 재실행 → BUILD SUCCESSFUL, 283/283 실패 0
- 원복 APK 를 에뮬레이터에 재설치해 **칩이 다시 2개**임을 확인 (`14_gate_false_restored.png`)
- 기기 설정도 원복: `font_scale=1.0`, `wm density reset`(420 복귀)
- 커밋·푸시·브랜치 변경 없음. `_workspace/` 기존 파일 손대지 않음

## 미검증 / 범위 밖

| 항목 | 사유 |
|------|------|
| 10 → 12 화면 전이, 12 의 제출 | 문자 인증이 운영 서버 쓰기 요청 + SMS 일일 한도 소모. 지시대로 하지 않음 |
| `UNDISCLOSED` 를 실제로 보냈을 때의 서버 응답 | 운영에 쓰기 금지. 스펙 조회(읽기)로 400 예상만 확인 |
| 실제 TalkBack 음성 출력 | UI 덤프의 `RadioButton` / `checkable` / `checked` 로 대체 확인 |
| 다크 모드, 가로 방향 | 요청 범위 밖 |
| font_scale 1.3·2.0 에서 화면 10 의 **다른** 필드 | 범위 밖이나 눈에 띈 것: fs 2.0 에서 비밀번호 placeholder 가 「보기」 버튼과 겹치고, 「인증번호를 받아…」 helper 가 2줄로 넘친다. 이번 변경과 무관한 기존 문제 |

## 담당자 요청

- **compose-builder** — A-1: `ProfileSetupScreen` 하단 CTA 겹침. 스크롤 Column 하단에 CTA 높이만큼
  여백을 둘 것. 게이트를 켜기 전까지가 기한
- **api-integrator** — 조치 없음. 경계면(도메인 → 매퍼 → DTO → 서버) 정합. 게이트는 백엔드 PR #235
  **배포 확인 후** `true` 로 바꾸면 된다(스펙 `gender` enum 에 `UNDISCLOSED` 가 보이는지로 판정)
