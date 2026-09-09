# 40 · compose-builder — 동승자 표시 정보 + 닉네임 화면 반영

작성 2026-09-07. 입력 `38_input_member_nickname.md`. 브랜치 `feature/mvp1-followup`.
빌드: `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**, `:presentation:testDebugUnitTest` 통과.
api-integrator 의 domain/data 변경(User.isMe/imageUrl, NewUser.nickname, AuthPolicy.isValidNickname,
AuthError.INVALID_NICKNAME/NICKNAME_DUPLICATED, AuthRepository.isNicknameTaken)이 이미 들어와 있어
38 계약 그대로 컴파일된다. **domain/data 는 한 줄도 건드리지 않았다.**

## 변경 파일

| 파일 | 변경 |
|---|---|
| `presentation/core/MemberDisplay.kt` | **신규** — 멤버 표기 공용 규칙(`isWithdrawn` / `displayNickname` / `rideCountLabel` / `verifiedLabelOrNone`) + `MemberAvatar`(이니셜 원) + `MeBadge` |
| `presentation/feature/auth/ProfileSetupRoute.kt` | **신규** — `NicknameCheckState` enum + `NicknameCheckViewModel`(500ms 디바운스, 이전 조회 취소) + `ProfileSetupRoute` |
| `presentation/feature/auth/ProfileSetupScreen.kt` | 표시 이름 → 닉네임(2~10자, 카운터 n/10), 중복 확인 상태 표시, `onNext(SignupDraft)` 로 시그니처 정리 |
| `presentation/feature/auth/SignupFieldSupport.kt` | `NicknamePolicy` 추가 — 규칙은 도메인 `AuthPolicy.isValidNickname` 위임, 사유별 한국어 문구 + 금칙어만 앱 정책 |
| `presentation/feature/auth/SignupDraft.kt` | `nickname` 필드 + `toNewUser()` 에 포함(공백이면 null), `authUserMessage()` 에 USER104/107/108 문구 |
| `presentation/feature/auth/SignupSubmitRoute.kt` | `UiState.nicknameDuplicated`(409 판정), `MannerPledgeRoute(onEditNickname)` |
| `presentation/feature/auth/MannerPledgeScreen.kt` | 오류 배너 아래 「닉네임 바꾸기」(닉네임 중복일 때만) |
| `presentation/core/MainNavGraph.kt` | `ProfileSetupRoute` 등록 + **255행 "표시 이름은 전송되지 않는다" 주석·우회 제거**, 409 → 10 복귀 배선, `selectedPartner` 상태로 23 에 실 User 전달, `RideDetailRoute` 의 userSession 인자 제거 |
| `presentation/feature/matching/MatchWaitingScreen.kt` | 「지금 모인 사람」 목록(이니셜 아바타·실닉네임·나 배지·탑승 N회) 추가, 나 제외 인원 문구 |
| `presentation/feature/matching/RideDetailRoute.kt` | `userSession`/`currentUserId` 우회 **삭제**(D-5 주석 포함) |
| `presentation/feature/matching/RideDetailScreen.kt` | `currentUserId` 파라미터 제거 → `isMe` 기준, 「매너 98%」 제거, 나 배지 + 나/탈퇴 회원 탭 차단 |
| `presentation/feature/explore/JoinConfirmScreen.kt` | `매너 N%`(rating×20) 제거 → 탑승 N회/첫 탑승, 탈퇴 회원 탭 차단, 대표 멤버는 실재 멤버 우선 |
| `presentation/feature/matching/PartnerProfileScreen.kt` | `user` 필수 파라미터화(데모 기본값 제거), 매너 셀 「평가 준비 중」, 노쇼 「집계 전」, `verifiedLabel` 빈값 → 「인증 정보 없음」, 인증/후기 기본값 빈 목록 + 빈 상태 문구, 인증 체크 배지는 라벨 있을 때만 |
| `presentation/feature/matching/DispatchStatusScreen.kt` | 동승자 행이 실닉네임 나열(`A, B · 나 포함 N명`, 혼자면 「나 혼자 탑승」), `DispatchInfoRow` 값 말줄임 |

## 화면별 UI 결정

**10 프로필 만들기**
- 라벨/문구 전부 「닉네임」. 규칙 2~10자 한글·영문·숫자, 카운터 `n / 10`, 공백은 입력 단계에서 조용히 제거(오류로 튕기지 않음). 금칙어·운영자 사칭어 필터는 유지.
- 입력 변경 → Route 가 500ms 디바운스 후 `isNicknameTaken`. 이전 조회 job 을 취소하므로 늦게 온 옛 응답이 최신 입력을 덮지 않는다. 형식이 안 맞으면 서버를 부르지 않는다.
- 표시: `Checking` → helper 「사용할 수 있는지 확인하고 있어요」, `Available` → 초록 「사용 가능한 닉네임이에요」, `Taken` → 필드 오류 「이미 사용 중인 닉네임이에요」, `InvalidFormat`(400 USER107) → 형식 문구. **네트워크 실패(`Unknown`)는 무표시·무차단** — 사용자가 고칠 수 있는 게 없고 서버가 가입 때 최종 판정한다.
- 「다음」 활성 조건은 형식 유효 + Taken/InvalidFormat 아님. Checking·Unknown 은 통과시킨다.
- 값은 `SignupDraft.nickname` → `NewUser.nickname` 으로 실제 전송된다.

**가입 에러 문구 매핑**(`authUserMessage`, 39 리포트 반영) — `PHONE_NUMBER_DUPLICATED`(409 USER104) → 「이미 가입된 전화번호예요. 로그인해 주세요」, `NICKNAME_DUPLICATED`(409 USER108) → 「이미 사용 중인 닉네임이에요」, `INVALID_NICKNAME`(400 USER107) → 형식 문구. 39 리포트 §76 대로 **닉네임 형식 오류가 `INVALID_REQUEST`(serverMessage `"nickname: …"`)로 오는 경로도 같은 형식 문구로 수렴**시켰다 — 가입 제출 문구와 10 화면의 중복 확인 양쪽에 동일 처리.

**12 매너 서약** — 가입 409 USER108 이면 배너 「이미 사용 중인 닉네임이에요」 + 그 아래 「닉네임 바꾸기」. 누르면 `popBackStack(PROFILE_SETUP)` 으로 10 복귀 — 스택에 남은 엔트리라 나머지 7개 입력값이 그대로 살아 있다. 다른 실패에는 이 링크가 뜨지 않는다(여기서 재시도가 맞다).

**21 매칭 대기** — 바텀시트에 「지금 모인 사람」 목록 신설(조건 카드 위). 각 줄: 이니셜 아바타 · 실닉네임(+ 나 배지) · 탑승 N회/첫 탑승. 헤드라인은 `지금 같은 방향 N명(나 외 M명) · 목표 K명` — M 은 `isMe` 기준. 21 에서는 프로필 진입을 열지 않았다(대기 흐름을 끊지 않기 위해; 23 진입은 20·22).

**22 탑승 상세** — 「함께 타는 사람 · N명」(N = 나 제외)이되 목록은 **나 포함 전원**을 그린다. 내 줄은 나 배지 + 탭 불가 + 셰브런 없음. 「매너 98%」 삭제, 탑승 N회/첫 탑승만. 탈퇴 회원은 「탈퇴한 회원」·탭 불가.

**20 합류 확인** — `매너 N%`(rating×20 환산) 제거. 대표 멤버 한 명 노출은 유지하되 탈퇴 회원이 앞에 있으면 실재 멤버를 앞세운다. 부제는 `탑승 N회 · 외 M명`.

**23 동승자 프로필** — 진입 시 20/22 에서 탭한 `User` 를 `selectedPartner` 로 넘긴다(라우트 인자 대신 NavHost 상태: 서버에 "멤버 1명" 조회 API 가 없어 id 만 넘기면 방을 다시 조회해야 한다). null 이면 데모 대신 `ErrorBox`. 매너 셀 「평가 준비 중」, 탑승 0회 「첫 탑승」, 노쇼 「집계 전」, 인증 라벨 없으면 「인증 정보 없음」 + 아바타 체크 배지도 숨김, 인증 항목/후기 태그는 빈 목록 기본값 + 빈 상태 문구. 데모 김OO/12회/4.9 는 Preview 안으로만 남았다.

**25 배차 현황** — 동승자 행이 「부산불곰, 해운대곰돌 · 나 포함 3명」 형태. 값이 길어질 수 있어 행 값에 weight+말줄임을 넣었다.

**아바타** — 프로젝트에 이미지 로더 의존성(Coil/Glide)이 **없다**. `imageUrl` 은 도메인에 담기지만 화면은 쓰지 않고 전원 이니셜 원(닉네임 첫 글자, 내 줄은 Primary 톤)으로 통일했다. 일부만 사진이 뜨는 상태가 더 어색하고, 새 의존성 추가는 이번 범위 밖이라 판단했다. 로더가 붙으면 `MemberAvatar` 한 곳만 고치면 된다.

**색상** — 새 하드코딩 hex 없음. `MemberDisplay.kt` 는 `MoyeotaColor` 토큰만 쓴다(기존 화면의 private 그레이는 그대로 재사용).

## 화면 진입 경로 (QA 용)

- 10: 04 로그인 → 「이메일로 시작하기」 (또는 04a 「가입하기」). 닉네임 입력 → 멈추면 500ms 뒤 확인 문구.
- 12: 10 → 11 안심 설정 → 12. 이미 있는 닉네임으로 가입하면 배너 + 「닉네임 바꾸기」 → 10.
- 21: 14 홈 → 15/16 방 생성 → 21. 다른 계정이 합류하면 목록에 두 줄, 내 줄에 「나」.
- 22: 21 조건 카드 탭. 23: 22 또는 20 의 동승자 행 탭.
- 20: 17 합승 목록 → 「합류」. 25: 21 에서 정원 도달 후 자동 전이.

## 미해결 · 남는 제약

1. **이미지 로더 부재** — `User.imageUrl` 이 화면에 반영되지 않는다. 프로필 사진을 실제로 띄우려면 Coil 의존성 추가가 필요(별도 결정 필요).
2. **평가·노쇼·인증 항목 API 없음** — 23 의 세 칸 중 두 칸이 「평가 준비 중」·「집계 전」, 인증/후기 섹션은 빈 상태다. 백엔드에 지표 API 가 생기기 전까지 유지.
3. **`badgeId` 항상 null** — `verifiedLabel` 이 사실상 항상 빈 값이라 20/21/22 에는 인증 라벨을 아예 그리지 않고 23 에서만 「인증 정보 없음」으로 명시한다.
4. **Explore(17/18/19) 목록 멤버는 여전히 자리표시** — 목록 API 에 members 가 없다(38 지시대로 유지). 목록 화면 더미에는 아직 김OO·4.9 가 남아 있으나 화면이 별점을 그리지 않는다.
5. **JoinConfirm/RideDetail 의 `partyId == null` 폴백** — 여전히 데모 Ride 를 그린다(기존 동작 유지). 다만 데모 닉네임을 중립값으로 바꾸고 매너·별점 표기를 전부 제거해, 이 경로로 들어가도 가짜 4.9·98%·김OO 는 보이지 않는다.
6. **실기 미확인** — 에뮬레이터가 떠 있지 않아(adb devices 비어 있음) 10 화면 닉네임 UX 를 직접 눌러 보지는 못했다. 콜드부트 실기 확인은 qa-verifier 몫이며, 서버는 **localhost:8081**(에뮬레이터에서 `10.0.2.2:8081`)이므로 `MOYEOTA_BASE_URL=http://10.0.2.2:8081/` 로 빌드해야 한다(8080 은 구버전). 특히 확인 요청: 백엔드가 죽어 있을 때 `isNicknameTaken` 실패가 **아무 문구도 남기지 않고 「다음」도 막지 않는지**.
