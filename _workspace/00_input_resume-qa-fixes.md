# 입력 — QA 결함 수정 + 회원 탈퇴 연동 (feat/backend-sync-sse 이어서)

재개(2026-10-13). 기준 커밋 `dabadff`. 백엔드 `origin/develop` = `88d586d`(회원 탈퇴 머지됨).
읽기 전용 백엔드: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/backend-dev`

## A. QA 리포트(`119_qa-verifier_report.md`) 결함 4건
- **D-1 (중)** 17 탐색의 4초 폴링이 21·20·채팅 등 **위에 쌓인 모든 화면에서 계속 돈다**. 21 체류 135초에 34회.
  `feature/explore/ExploreRoute.kt:39` `NEARBY_POLL_INTERVAL_MS`, 루프 `:85~97` `observeRequests()`.
  원인: ViewModel 이 NavBackStackEntry 에 묶여 있고 `viewModelScope` 가 lifecycle 을 보지 않는다.
  → **이번 작업의 목표(평상시 네트워크 0)를 상쇄한다.** 화면 가시성에 묶어야 한다.
- **D-2 (하)** 21 진입 시 방 상세를 2회(합류 경로 3회) 읽는다.
  `MatchWaitingRoute.kt:224` `refresh()` + `MainNavGraph.kt:554` `rememberParty()` → `ActiveParty.kt:144~149` `reload()`.
- **D-3 (하)** 방을 떠난 뒤 `GET /matching/rooms/0` 404. `RemoteActivePartyRepository.verify()` 가 0·음수를 안 거른다.
- **D-4 (하·문서)** `USER207` 의 백엔드 enum 이름은 `SMS_NOT_VERIFIED` 다(문서에 `PHONE_NOT_VERIFIED` 로 적힘). 동작 영향 없음.

## B. 회원 탈퇴 연동 (Backend 가 내 이슈 #206 을 구현했다 — PR #216, `88d586d`)
- `DELETE /api/v1/users/me` body `{"password": "..."}` → **204**. 인증 필요.
- 성공하면 **기존 토큰이 모두 무효** → 앱은 저장한 토큰을 지우고 로그인 화면으로.
- 신규 에러: `USER110 ONGOING_PARTY_EXISTS`(409, 진행 중 합승이 있으면 탈퇴 불가),
  `USER111 PASSWORD_MISMATCH`(400, 비밀번호 불일치), `USER109 REJOIN_RESTRICTED`(409, **가입** 시 — 탈퇴 후 30일 같은 번호 재가입 금지).
- 탈퇴하면 채팅방에서 나가고 즐겨찾기가 지워지며, 탈퇴자는 채팅·합승 화면에 「탈퇴한 사용자」로 표시된다.
- 프론트는 35 마이페이지에 「탈퇴하기」가 없다(API 가 없어 뺐었다 — `docs/BACKEND-GAP-CLEANUP.md`). 이제 되살린다.

## C. 미검증 1건
인증번호 3분 만료 경로(QA D13) — 시간이 걸려 중단됨.

## 배포 전제 (바뀌지 않음)
백엔드 변경은 **develop 에만** 있다. 운영 `api.moyeota.p-e.kr` 는 main(옛 계약)이다.
이 브랜치는 백엔드 develop→main 릴리스와 **함께** 나가야 한다.
