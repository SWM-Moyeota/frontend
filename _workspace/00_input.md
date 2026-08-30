# 작업 입력: 백엔드 완성 API 프론트 연동 (2026-08-24)

## 요청
backend 레포에서 완성된 API들을 frontend에 연동. 매칭 조회는 연동 완료 상태였으나 경로 확인 필요, 액션 API 등 미연동분 파악 후 연결.

## 백엔드 완성 기준
`origin/develop` 머지분. dispatch call(콜 수락/거절, DispatchCallController)은 `feature/driverCall-accept` 작업 중이라 **제외**.
백엔드 로컬 체크아웃은 feature 브랜치 + 미커밋 변경 상태이므로, 스펙 확인은 `git show origin/develop:<path>` 기준으로 할 것.

## 연동 범위 (origin/develop 완성 API × 프론트 화면 존재 여부로 산정)

### 1. 공통: API 경로 버전 수정
- 백엔드 전 컨트롤러가 `/api/v1` 프리픽스 사용. 프론트 `MatchingApi`는 `api/matching/...`(v1 없음) → **기존 조회 연동도 깨진 상태로 추정. 전면 v1 반영**

### 2. 매칭(Party) — PartyController
| 엔드포인트 | 프론트 연결처 |
|---|---|
| POST /api/v1/matching/rooms (방 생성) | DestinationConfirmModal → MatchWaiting |
| POST /api/v1/matching/rooms/{partyId}/{memberId}/join | JoinConfirmScreen |
| DELETE /api/v1/matching/leave/{partyId}/{memberId} | MatchWaiting/RideDetail 나가기 |
| POST /api/v1/matching/ready/{partyId}/{memberId} | MatchWaiting 준비 |
| DELETE /api/v1/matching/ready/{partyId}/{memberId} | MatchWaiting 준비 해제 |
| POST /api/v1/matching/start/{partyId}/{memberId} | MatchWaiting → DispatchStatus/RideOngoing |
| GET /api/v1/matching/rooms | ExploreScreen (기연동, 경로만 수정) |
| GET /api/v1/matching/rooms/{partyId} | RideDetail (기연동, 경로만 수정) |
| GET /api/v1/matching/routes | Explore/Home 경로 추천 |

### 3. 장소(Place) — PlaceSearchController, FavoritePlaceController
| 엔드포인트 | 프론트 연결처 |
|---|---|
| GET /api/v1/places (검색) | DestinationScreen 검색 |
| POST /api/v1/users/me/favorite-places | DestinationScreen/Home 즐겨찾기 등록 |
| GET /api/v1/users/me/favorite-places | DestinationScreen/Home 즐겨찾기 목록 |

### 4. 채팅(Chat) — ChatRoom/ChatRoomUser/ChatMessage(REST), StompChat(WS)
| 엔드포인트 | 프론트 연결처 |
|---|---|
| GET /api/v1/chat-rooms/me, GET /{id}, POST, DELETE | ChatScreen 진입/방 관리 |
| POST·DELETE /{id}/users, POST read | 합류/나가기 시 채팅방 연동 |
| GET·POST messages, GET /after, DELETE | ChatScreen 메시지 |
| STOMP 실시간 | 1차는 REST 우선, STOMP는 백엔드 스펙 확인 후 가능 범위 내 구현·불가 시 플래그 |

### 제외 (프론트 화면 없음 / 백엔드 미완성)
- Driver API 6종, DriverLocation 3종 — 기사용 화면이 프론트에 없음
- DispatchCall — 백엔드 feature 브랜치 작업 중

## 검증 기준
- `./gradlew :app:assembleDebug :data:testDebugUnitTest` 통과
- 에뮬레이터(콜드부트) 실기: 백엔드 로컬 기동 가능 시 실서버 연동 확인, 불가 시 "실서버 미검증" 플래그 + 에러 상태 UI 확인
- 경계면 교차 비교: 백엔드 DTO(origin/develop) ↔ 프론트 DTO/매퍼

## 브랜치
feature/api-integration (feature/front-setting에서 분기)
