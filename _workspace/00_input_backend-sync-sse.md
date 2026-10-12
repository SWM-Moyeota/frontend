# 입력 — 백엔드 변경 연동 + 매칭방 SSE 전환 (feat/backend-sync-sse)

사용자 요청(2026-10-13): "백엔드 변경사항쪽 API 연결해줘. **방 상태는 polling 안 쓰고 SSE 쓴다.**"

기준: 백엔드 `origin/develop` = `53b8cf1` (PR #218 머지). 프론트는 `feat/mypage-ops`(PR #49) 위에 쌓는다.
읽기 전용 백엔드 체크아웃: `/private/tmp/claude-501/-Users-sungyoon-Desktop-moyeota-frontend/1b791af6-8c40-4510-9b30-44b1700ea413/scratchpad/backend-dev`

## 1. SSE 재설계 (Backend #218 `feature/sse-member-delta`) — 이번 작업의 핵심

`GET /api/v1/matching/rooms/{partyId}/events` (text/event-stream, 인증 필요)

| 이벤트 | data | 의미 |
|---|---|---|
| `connected` | **방 상세 전체 JSON**(`PartyDetailResult` = 프론트 `PartyDetailResponse` 와 같은 모양) | 구독 직후 스냅샷 |
| `member-joined` | `{"member": {publicId, nickname, imageUrl, badgeId, rideCount}, "status": "ACTIVE"}` | 들어온 멤버 1명 + 변경 후 방 상태 |
| `member-left` | `{"publicId": "uuid", "status": "ACTIVE"}` | 나간 멤버 publicId + 변경 후 방 상태. publicId 는 null 일 수 있다(탈퇴 등으로 요약 없음) |
| `closed` | `partyId` **숫자 문자열**(JSON 아님) | 방 종료. 서버가 모든 구독을 닫는다 |
| (주석) | `:ping` | **30초** 간격 heartbeat (이전 5초에서 바뀜) |

- **`changed` 이벤트는 사라졌다.** 현재 앱은 `changed` 만 듣고 상세를 재조회하므로 **지금 SSE 로는 아무 갱신도 못 받는다**(20초 안전망 폴링에만 의존 중). 이게 이번 작업의 출발점이다.
- 방을 나간 당사자의 스트림은 서버가 **이벤트 없이** 끊는다(`closeMember`).
- 403 `NOT_PARTY_MEMBER` / 404 `PARTY_NOT_FOUND` 는 종결(재연결 금지) — 기존 동작 유지.
- 배차·운행 전이(MATCHING→DRIVER_ASSIGNED→IN_RIDE→FINISHED)는 **SSE 이벤트가 없다**. 발행처는 `PartyService` 의 join/leave/finish/expire 뿐이다. → 25·26 화면은 폴링 유지(별도 백엔드 이슈로 요청 예정).

### 폴링 정책 (사용자 지시)
- **21 매칭 대기: 평상시 폴링 0회.** SSE 가 살아 있으면 status 폴링을 아예 돌리지 않는다.
- 끊겼을 때만 폴백 폴링(채팅과 같은 정책: 소켓 살아 있으면 0회, 죽었을 때만). 재연결되면 `connected` 스냅샷이 전체 상태를 보정하므로 catch-up 호출도 불필요하다.

## 2. join 응답 변경 (Backend #213) — **현재 앱이 깨지는 부분**

`POST /matching/rooms/{partyId}/join` 응답이 방 상세 → **`PartyStatusResponse{status, currentMembers, fingerprint}`** 로 바뀌었다.
프론트 `MatchingApi.joinParty` 는 아직 `PartyDetailResponse` 를 기대한다 → 역직렬화 실패 또는 빈 Ride.
동승자 목록은 `GET /matching/rooms/{partyId}` 로 따로 받아야 한다.

## 3. `MemberInfo.joinedAt` 제거 (#218)

백엔드 `MemberInfo` 는 이제 `(publicId, nickname, imageUrl, badgeId, rideCount)` 다. 프론트 DTO 의 `joinedAt` 필드는 **항상 null** 이므로 지운다(입장 시각 표시 계획도 근거가 사라졌다).

## 4. 가입에 문자 인증 필수 (Backend #212)

- `POST /api/v1/auth/sms/send` body `{phoneNumber}` → **204**. (로컬 `provider=log` 는 서버 로그에 코드 출력, `moyeota.sms.fixed-code` 로 고정 가능)
- `POST /api/v1/auth/sms/verify` body `{phoneNumber, code(6자리)}` → `{verificationToken}`
- `POST /api/v1/auth/register` 에 **`verificationToken` 필수**(@NotBlank). 지금 앱은 안 보내므로 **가입이 400 으로 실패한다.**
- 전화번호 형식 `^01[016-9]-?\d{3,4}-?\d{4}$` (하이픈 무관), 비밀번호 영문·숫자·특수문자 각 1자 이상 8~64자, 아이디 `^[a-z][a-z0-9_]{3,19}$`.
- 삭제된 건 옛 `POST /api/v1/local/users` 다. `POST /api/v1/auth/register` 는 그대로 있다.

## 작업 분할
- **Track A (매칭방 실시간)**: 1·2·3 — data/domain + 21 화면
- **Track B (문자 인증 가입)**: 4 — data/domain + 가입 화면
- 두 트랙은 건드리는 파일이 거의 겹치지 않는다. A = PartyEventSource·PartyDtos·MatchingApi·RideRepository·MatchWaiting*. B = AuthApi·AuthDtos·AuthRepository·ProfileSetup·SignupDraft·MainNavGraph(가입 그래프).
