# 26. D-8 계정 유형 분기 + 09 개인정보 카피 수정 — compose-builder

작업일: 2026-09-02 / 브랜치: `feature/naver-map` / 선행: `23_compose-builder_auth.md`, `24_qa-verifier_auth.md`(D-8)
빌드: `./gradlew :presentation:compileDebugKotlin :app:assembleDebug --console=plain` → **BUILD SUCCESSFUL**
실기: **emulator-5554**(install -r, 데이터 보존) — 3경로 전부 통과. 스크린샷 `_workspace/screenshots/d8_00~07_*.png`

---

## 1. D-8 — 05 선택값이 라우팅에 반영되지 않던 문제

`AccountTypeScreen` 은 이미 `onNext: (AccountType) -> Unit` 으로 선택값을 올려보내고 있었다. **결함은 화면이 아니라 그래프 쪽**이었다 — `MainNavGraph` 가 WORKER 와 GENERAL 을 **둘 다 `WORK_VERIFY`(08) 로 보내고**, 차이를 `workVerifyIsWorker` 라는 화면 내 표시 플래그로만 처리했다. 와이어프레임의 고정 흐름(05 → 08)을 그대로 옮긴 잔재다.

**MainNavGraph.kt:246~252** — 목적지 자체를 선택값이 정하도록 바꿨다.

```
STUDENT -> SCHOOL_EMAIL   (06 → 07 → 09)
WORKER  -> WORK_VERIFY    (08 → 09)
GENERAL -> IDENTITY_VERIFY(09 직행)
```

일반 유형을 08 에 세울 이유가 없다: 08 이 받는 유일한 값이 **회사 메일**인데 일반 사용자에게는 그게 없다. 실제로 기존 코드도 일반일 때는 빈 입력으로 통과시키고 있었으므로, 화면 하나를 아무 값도 받지 않고 지나가게 두는 셈이었다.

### 파생 정리 — 08 은 직장인 전용이 됐다

`isWorker = false` 로 들어올 경로가 사라졌으므로 죽은 분기를 남기지 않았다(그대로 두면 다음 사람이 "일반도 여기 오나?"를 다시 물어야 한다).

| 위치 | 변경 |
|---|---|
| `MainNavGraph.kt:118` | `workVerifyIsWorker` 상태 삭제 |
| `MainNavGraph.kt:279~286` | `isWorker` 전달 제거, `onSubmit` 이 항상 유효한 메일을 받으므로 null 체크 삭제 |
| `WorkVerifyScreen.kt:65~67` | `isWorker` 파라미터 제거, `onSubmit: (String?) -> (String)` |
| `WorkVerifyScreen.kt:82~83` | `ctaEnabled = emailValid` (일반용 "비워도 통과" 분기 제거) |
| `WorkVerifyScreen.kt:111` | 부제 → 「재직 정보로 직장인 인증을 진행해요」 (일반 이용자 언급 제거) |

### 진행 표시 보정 (08: 2/5 → 1/5)

기존 배치는 학생 경로 06·07 = **1/5**, 09 = 2/5, 10 = 3/5, 11 = 4/5, 12 = 5/5 로 「인증」이 1단계다. 그런데 08 만 **2/5** 여서 직장인 경로는 08(2/5) → 09(2/5) 로 같은 숫자가 두 번 나왔다. 08 을 **1/5**(`WorkVerifyScreen.kt:92,95`)로 내려 세 경로의 2~5단계가 모두 일치한다.

일반 경로는 2/5 에서 시작한다 — 1단계(인증)를 건너뛴 것이 사실이고, 기존 「나중에 인증할게요」 경로가 이미 09(2/5) 로 직행하고 있어 선례와도 맞는다. 단계 수를 유형별로 다르게 세려면 09~12 네 화면에 진행도를 주입해야 해서 하지 않았다.

---

## 2. 09 본인 인증 부제 카피 (개인정보 고지 정확성)

`IdentityVerifyScreen.kt:141`

- 이전: 「실명과 성별은 매칭 안전에만 쓰고 **저장하지 않아요**」
- 이후: 「실명과 성별은 매칭 안전을 위해서만 사용해요」

실명·성별은 `POST /api/v1/users` 로 서버에 저장된다. 저장을 부정하는 표현만 걷어내고 용도 한정 문구는 유지했다. 근거를 코드 주석으로 남겼다.

---

## 3. 변경 파일

- `presentation/src/main/kotlin/com/moyeota/presentation/core/MainNavGraph.kt` — 118(삭제), 246~252, 279~286
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/WorkVerifyScreen.kt` — 49~50, 65~67, 82~83, 92, 95, 111, 194
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/IdentityVerifyScreen.kt` — 140~141
- `presentation/src/main/kotlin/com/moyeota/presentation/feature/auth/AccountTypeScreen.kt` — 60~64, 72 (주석만)

하드코딩 색상 추가 없음. Routes 상수 변경 없음(기존 3개 라우트를 다시 배선했을 뿐).

---

## 4. 화면 진입 경로 · 실기 결과 (emulator-5554)

진입: 온보딩 「건너뛰기」 → 04 「로그인」 → 04a → 「가입하기」 → **05 계정 유형**

| 선택 | 기대 | 실제 | 스크린샷 |
|---|---|---|---|
| 일반 | 09 본인 인증 (2/5) | ✅ 「휴대폰으로 본인 확인할게요」 2/5 — 08 을 거치지 않음 | `d8_04_general_to_identity.png` |
| 직장인 | 08 재직 인증 (1/5) | ✅ 「신원을 확인할게요」 1/5, 부제 「재직 정보로 직장인 인증을 진행해요」 | `d8_05_worker_to_workverify.png` |
| 학생 | 06 학교 이메일 (1/5) | ✅ 「학교 이메일을 알려주세요」 1/5 | `d8_06_student_to_schoolemail.png` |

09 부제 수정도 `d8_04` 에서 함께 확인된다 — 「실명과 성별은 매칭 안전을 위해서만 사용해요」.

각 경로 확인 후 시스템 back 으로 05 로 복귀 → 05 에서 두 번 더 back 하여 04a 로그인 화면으로 정리(`d8_07`). 서버(localhost:8080)는 건드리지 않았고 앱은 `install -r` 로만 갱신했다.

---

## 5. 남긴 관찰 (수정하지 않음 — 지시 범위 밖)

**06 학교 이메일 화면의 「이름과 학번은 저장하지 않아요」** 도 09 와 같은 종류의 문구다. 이름은 09 에서 받아 서버에 저장되므로, 가입 플로우 전체로 보면 이 고지도 사실과 어긋난다. D-8 범위를 넘어서 손대지 않았으니 카피 정리 시 함께 검토할 것.
