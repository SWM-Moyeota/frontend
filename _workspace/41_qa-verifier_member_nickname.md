# 41 · qa-verifier — 동승자 표시 정보 + 닉네임 검증

작성 2026-09-07. 입력 `38_input_member_nickname.md` / `39_api-integrator_member_nickname.md` / `40_compose-builder_member_nickname.md`.
브랜치 `feature/mvp1-followup`. 백엔드 **localhost:8081** (신규 코드, `35d9203` 상당) — 8080·emulator-5558 미접촉.
빌드 `MOYEOTA_BASE_URL=http://10.0.2.2:8081/ ./gradlew :app:assembleDebug` → APK dex 에서 `http://10.0.2.2:8081/` 확인,
`BuildConfig.BASE_URL = "http://10.0.2.2:8081/"`. 실기 emulator-5554(Pixel_6) 콜드부트 상태에서 전 항목 구동 검증.

## 결과 요약

| 항목 | 결과 |
|---|---|
| A 정적 (빌드·유닛·DTO 교차) | **통과** |
| B 가입 10→12 (형식·중복·409·USER104·홈 인사말) | **통과** (결함 1건 발견 → 직접 수정 후 재검증 통과) |
| C 동승자 표시 20/21/22/23/25 | **통과** |
| D rideCount 실기 (배차→완주→탑승 1회) | **통과** (실기 전 구간 완주) |
| E 회귀 (로그인·로그아웃·폴링·오프라인) | **통과** |
| F 탈퇴 회원 null | **유닛으로 갈음** (서버 재현 경로 없음) |

크래시 0, 직렬화 예외 0 (`FATAL EXCEPTION|SerializationException|MissingFieldException|JsonConvertException` 전 구간 0건).
유닛 `:data` **153 tests / 0 failures**, `:presentation` **12 tests / 0 failures**.

---

## A. 정적 — 경계면 교차 비교 (통과)

서버 record 를 직접 열어 필드명·순서·타입을 대조했다.

| 서버 | 앱 | 결과 |
|---|---|---|
| `PartyDetailResult.MemberInfo(UUID publicId, String nickname, String imageUrl, String badgeId, Integer rideCount, Instant joinedAt)` | `PartyDtos.MemberInfo(publicId?, nickname?, imageUrl?, badgeId?, rideCount:Int=0, joinedAt?)` | 6/6 이름·순서 일치, 네 문자열 nullable 정확 |
| `UserRegisterRequest(loginId, password, nickname, name, birthDate, phoneNumber, gender, email)` | `RegisterRequestDto` 동일 순서 | 일치 — **실제 요청 본문으로도 확인**(아래) |
| `NicknameCheckRequest(@NotBlank String nickname)` / `NicknameCheckResponse(boolean exists)` | `NicknameCheckRequestDto` / `NicknameCheckResponse(exists=false)` | 일치 |
| `UserErrorCode` USER104/106/107/108 | `AuthMappers.toAuthException` 4개 분기 | 일치 |

실제 가입 요청 본문(로그캣, 02:07:54):
```
{"loginId":"qauser01","password":"Passw0rd!","nickname":"qauser01","name":"QaTester",
 "birthDate":"1999-03-15T00:00:00Z","phoneNumber":"010-3333-0001","gender":"MALE","email":"qauser01@test.com"}
```
서버 curl 실측: 1자 닉네임 → `400 {"code":"USER107"}`, 기존 닉네임 → `{"exists":true}`, 전화번호 중복 → `409 {"code":"USER104"}`.

---

## B. 가입 (통과 — 결함 1건 수정)

| 케이스 | 기대 | 결과 | 근거 |
|---|---|---|---|
| 닉네임 1자 | 형식 문구·서버 미호출 | 통과 — 「닉네임은 2~10자로 입력해 주세요」, 붉은 테두리, `nickname/check` 미발생 | `member_04_nickname_1char.png` |
| 이미 있는 닉네임 | 디바운스 후 「이미 사용 중」 | 통과 | `member_05_nickname_taken.png` |
| 정상 닉네임 | 「사용 가능」 | 통과 (초록) | `member_06_nickname_available.png` |
| 디바운스 | 타자당 1회 아님 | 통과 — 8타에 요청 2건(`qata`, `qataken1`) | 로그캣 02:00:48 |
| 10자 상한 | 초과 입력 차단 | 통과 — 카운터 `10 / 10` 에서 절삭 | — |
| 가입 409 USER108 | 배너 + 「닉네임 바꾸기」 | 통과 | `member_08_signup_409_nickname.png`, `member_11_409_banner_again.png` |
| 409 후 10 복귀 · 입력값 보존 | 8개 필드 유지 | **결함 → 수정 후 통과** | 아래 D-1 |
| 전화번호 중복 USER104 | 「이미 가입된 전화번호예요」 | 통과, 「닉네임 바꾸기」 미표시(정상) | `member_14_phone_duplicated.png` |
| 홈 인사말 | 닉네임 | 통과 — `qauser03님, 좋은 저녁이에요` / 한글 닉네임 `해운대곰님, …` | `member_16_home_greeting.png` |

409 경쟁 상황은 지시대로 **제출 직전 curl 로 같은 닉네임을 선점**해 재현했다(`racewinner1`/`racewinner2`).

### D-1 (수정 완료) · 409 후 「닉네임 바꾸기」로 돌아가면 가입 폼 8개 값이 전부 사라진다

- **파일** `presentation/feature/auth/ProfileSetupScreen.kt:94-105`
- **증상** 12 에서 「닉네임 바꾸기」 → 10 복귀 시 닉네임·이름·생년월일·성별·휴대폰·아이디·비밀번호·이메일이 **전부 초기화**. 「다음」도 비활성. 게다가 **빈 입력 위에 초록 「사용 가능한 닉네임이에요」가 남아 있었다**(선점당한 그 닉네임인데도).
- **재현** 10 전체 입력 → 11 → 12 → 제출 직전 curl 로 같은 닉네임 선점 → 「동의하고 가입 완료」 → 409 배너 → 「닉네임 바꾸기」. 근거 `member_09_back_to_10_preserved.png`, `member_10_draft_lost_top.png`.
- **원인** 입력 9개가 전부 `remember` 였다. 10 은 12 로 넘어가는 동안 컴포지션에서 빠지므로 `remember` 는 폐기되고, `popBackStack` 으로 돌아오면 처음부터 다시 구성된다. 반면 `NicknameCheckViewModel` 은 백스택 엔트리에 붙어 살아남아 직전 결과(`Available`)를 그대로 들고 있었다 — 빈 필드 + 초록 문구의 불일치가 정확히 이 조합이다. `MainNavGraph.kt:281-283` 주석("이미 채운 나머지 7개 필드는 그대로 있고")이 실제 동작과 어긋나 있었다.
- **직접 수정** 입력 9개를 `rememberSaveable` 로 바꾸고(`passwordVisible` 은 일시적 토글이라 `remember` 유지), 복원된 닉네임을 다시 조회하도록 `LaunchedEffect(Unit)` 한 블록을 더했다. 구조 변경 없음 — 상태 보존 방식만 교체.
- **재검증** 8개 값 전부 보존(`member_12_draft_preserved_top.png`, `member_13_draft_preserved_bottom.png`), 그리고 재조회가 돌아 초록 「사용 가능」이 **「이미 사용 중인 닉네임이에요」로 정정**된다. `:app:assembleDebug` + `:presentation:testDebugUnitTest` 통과.

---

## C. 동승자 표시 (통과)

시나리오: 앱(`qauser03`)이 부산대→부산역광장 방 생성 → `qapartner1`(닉네임 `부산불곰`) curl 합류 → `qapartner2`(`해운대곰`) 관점으로 20 확인.

서버 실응답(합류 200) — 계약 그대로다:
```json
"members":[{"publicId":"01a077ba-…","nickname":"qauser03","imageUrl":null,"badgeId":null,"rideCount":0,…},
           {"publicId":"01a077a8-…","nickname":"부산불곰","imageUrl":null,"badgeId":null,"rideCount":0,…}]
```

| 화면 | 확인 | 결과 |
|---|---|---|
| **21** | 헤드라인 `지금 같은 방향 2명(나 외 1명) · 목표 3명` | 통과 — 나 외 카운트가 `isMe` 기준으로 정확 |
| **21** | 두 멤버 실닉네임 + 이니셜 아바타(`q`/`부`) | 통과 |
| **21** | 「나」 배지가 **내 항목에만** | 통과 (`qauser03` 행에만) |
| **21** | 탑승 0회 → 「첫 탑승」 | 통과 (`member_19_match_waiting_alone.png`) |
| **22** | `함께 타는 사람 · 1명`(나 제외) + 목록은 나 포함 전원 | 통과 |
| **22** | 「매너 98%」 제거 | 통과 — 매너 표기 없음 |
| **22** | 내 행 탭 차단 | 통과 — 탭해도 23 으로 안 넘어감 |
| **23** | 실닉네임·탑승 횟수·「평가 준비 중」·「인증 정보 없음」 | **전부 통과** |
| **23** | 데모 김OO / 4.9 / 12회 없음 | 통과 — 「집계 전」, 「아직 확인된 인증 정보가 없어요」, 「아직 남은 후기가 없어요」 |
| **20** | 기존 멤버 실데이터, 매너 % 없음 | 통과 — `이미 참여한 사람 · 2명`, `qauser03 · 동승자 · 탑승 1회 · 외 1명` |
| **25** | 동승자 실닉네임 | 통과 — `부산불곰 · 나 포함 2명` |

근거: `member_19` `member_20` `member_23` `member_24` `member_25` `member_28`.

---

## D. rideCount (통과 — 실기 완주)

기사 `qadriver1` 시드(가입→로그인→`POST /api/v1/drivers`→`/drivers/verify`→`/drivers/call`→`/dispatch/online`) 후,
파티 1(2인)이 정원 도달 → 앱이 25 로 자동 전이 → 기사 콜 수락 → `arrive` → `board` → `complete{fare:24340}` (모두 204) → 파티 `FINISHED`.

- 서버 확인: 두 멤버 모두 `rideCount=1`
- **앱 확인**: 이후 새 방(파티 2)에서 21 이 「첫 탑승」 → **「탑승 1회」** 로 바뀜. 22·23 에도 「탑승 1회」/「1회」 반영.

즉 지시의 "curl 응답으로 대체" 없이 **화면까지 실기로 확인**했다.

**주의(다음 QA 용):** 기사 하트비트 TTL 이 30초라 `POST /api/v1/dispatch/location` 을 계속 쏘지 않으면 후보에서 빠져 `accept` 가 `409 CALL_CLOSED` 로 떨어진다. 처음 수락이 실패한 것도 이 때문이었고, 하트비트 루프를 돌리자 `MatchingSweeper` 재시도에서 콜이 열려 수락됐다.

---

## E. 회귀 (통과)

| 항목 | 결과 |
|---|---|
| 로그인 | 통과 — `해운대곰님, 좋은 저녁이에요` |
| 로그아웃 | 통과 — `POST /auth/logout` 204 → 로그인 화면 |
| 25 배차 폴링 | 통과 — members 파싱 실패·크래시 0. 기사 배정 후 `12가3456 / SEDAN · 4인승` 정상 갱신 |
| 홈·합승 탭 지도 | 통과 — 합승 목록에 방 노출(`2/3명`), 지도 렌더 정상 |
| **서버 다운 시 10 닉네임 확인** | **통과** — 비행기 모드에서 유효 닉네임 입력 시 **아무 문구도 뜨지 않고**(초록·붉은 표시 모두 없음) 다음 단계도 막지 않는다. 40 리포트 §70 의 확인 요청 사항이 설계대로 동작 |
| 네트워크 복구 | 통과 — 재입력하니 곧바로 「사용 가능한 닉네임이에요」 |

근거: `member_29_offline_nickname.png`, `member_30_online_recovered.png`.

---

## F. 탈퇴 회원 null (유닛으로 갈음)

서버에 탈퇴 API 가 없어 `summary == null` 분기를 실서버로 만들 수 없다. 지시대로 유닛으로 갈음한다 —
`PartyMappersTest` 의 **`요약이 없는 멤버는 탈퇴한 회원으로 표시하고 id 를 비운다`**, `자리 표시용 멤버는 나로 표시되지 않는다`,
`세션 uuid 를 모르면 아무도 나로 표시하지 않는다`, `멤버 항목에 모르는 필드가 있거나 필드가 빠져도 파싱이 깨지지 않는다` 통과.
**실기 미검증**임을 명시한다.

---

## 남은 결함 · 후속 (수정하지 않음)

### R-1 · 마이(26)에 데모 지표가 그대로 남아 있다 — 이번 작업의 취지와 정면으로 어긋남

- **파일** `presentation/feature/mypage/…` (마이 헤더 지표 3칸)
- **증상** 닉네임은 실데이터(`qauser03`)인데 그 아래가 **「98% 매너 점수」·「42회 탑승」** 하드코딩이다. 근거 `member_26_mypage.png`.
- 이번 작업이 20~25 에서 지운 바로 그 가짜 수치(매너 %·가짜 탑승 횟수)가 마이에는 남아 있다. 38 범위 밖이라 손대지 않았지만, **사용자가 가장 자주 보는 화면이라 우선순위가 높다.**
- 다만 1~2줄 수정이 아니다: 매너 지표 API 가 없고, 내 `rideCount` 도 단독 조회 API 가 없다(파티 members 에만 들어 있다). 23 처럼 「평가 준비 중」·「집계 전」으로 내릴지, 서버에 `GET /users/me/stats` 를 요청할지 **결정이 필요**하다 → 리더 판단 요청.

### R-2 · 백엔드: `badgeId` 타입 불일치 (39 리포트 §5-1 재확인)

`MemberSummary.badgeId` 는 `Long`, `PartyDetailResult.MemberInfo.badgeId` 는 `String`. 지금은 `toMemberInfo` 가 항상 `null` 을 넣어 드러나지 않지만, 서버가 실제 값을 채우는 순간 앱의 `String?` 파싱이 깨진다(kotlinx 는 숫자→String 을 허용하지 않는다). **서버가 배지를 채우기 전에 반드시 프론트에 알릴 것.**

### R-3 · 참고: 좌표 전치 없음

기존에 기록된 백엔드 결함 D-1(좌표 전치) 관련해, 이번 기사 위치 등록 후 Redis `GEOPOS drivers:location 1` 이 `129.0838…`(경도) → `35.2313…`(위도) 순으로 정상 반환됐다. 이 경로에서는 전치가 관측되지 않는다.

---

## 검증에 쓴 계정 (H2 인메모리 — 서버 재기동 시 소멸)

| 아이디 | 닉네임 | 용도 |
|---|---|---|
| `qauser02` | `qauser03` | 앱 로그인 계정(방 생성자) |
| `qapartner1` | `부산불곰` | 동승자 |
| `qapartner2` | `해운대곰` | 20 합류 확인 관점 |
| `qataken001` | `qataken1` | 닉네임 중복 확인용 선점 |
| `racewinner1`/`racewinner2` | `qauser01`/`qauser02` | 409 경쟁 상황 재현 |
| `qadriver1` | `기사하나` | 배차 기사(VERIFIED, 온라인) |

스크린샷: `_workspace/screenshots/member_01_*` ~ `member_30_*`.
