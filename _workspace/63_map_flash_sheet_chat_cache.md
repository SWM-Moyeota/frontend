# 63 · 지도 검은 깜빡임 · 홈/운행중 시트 접기 · 채팅방 캐시(403)

작성 2026-09-12. 실기 피드백 3건.

## 1. 화면 전환 시 지도가 검게 깜빡이며 딜레이
- 원인: `NaverMapView` 기본이 SurfaceView. 전환 애니메이션 동안 서피스가 준비되기 전까지 영역이 검게 빈다.
- 수정: `NaverMapView` / `RouteMapView` / `RouteStripMap` 의 `useTextureView` 기본값 false → **true**. TextureView 는 일반 뷰처럼 합성돼 검은 프레임이 없다. 지도가 화면 일부인 이 앱에서 렌더 비용 차이는 체감 없음.

## 2. 홈(14) · 운행 중(26) 시트를 내려도 지도가 전부 안 보임
- 원인: 두 화면은 시트가 고정 레이아웃(weight/height)이라 핸들이 장식일 뿐 드래그가 없었다.
- 수정: 16·21 과 같은 `MapSheetScaffold` 로 재구성.
  - 14 홈: 배경 = 풀스크린 지도(+진행 배너), sheetTop = 인사·「어디로 갈까요?」·검색 바, sheetDetail = 자주 가는 곳·최근 목적지. 접으면 지도가 검색 카드 위까지 전부. `mapRevealHeight 200dp`, `collapsed 226dp`.
  - 26 운행 중: 배경 = RideOngoingMap(fillMaxSize, `bottomInset = settledSheetHeight` → contentPadding 으로 fitBounds 가 보이는 영역 기준), sheetTop = 남은 시간·도착 예정, sheetDetail = 경유 순서·안심 공유, sheetFooter = 신고·채팅 열기(접힘에서도 항상). `mapRevealHeight 190dp`(예전 고정값), `collapsed 230dp`.

## 3. 26 「채팅 열기」 → "이 채팅방에 참여하고 있지 않아요"
- 앱은 `/chat-rooms/me`(활성 참여 방)에서 partyId 로 채팅방을 찾아 **캐시**한다. 나중에 403 이 나는 경로는 「채팅방 나가기」뿐 — 서버가 leftAt 을 찍고, 앱은 죽은 id 로 계속 연다.
- **서버 결함(백엔드 보고)**: `chat_room_user` PK 가 (userId, chatRoomId) 복합키라 나간 행이 남는다 → 재참여 시 409 `CHAT_ROOM_ALREADY_JOINED`(DataIntegrityViolation) + 활성 조회 403 `CHAT_NOT_PARTICIPANT`. 한 번 나가면 되돌릴 수 없다. 어제 QA 계정의 수동 생성 경로도 같은 증상.
- 앱 수정:
  - `ActivePartyViewModel.forgetChatRoom()` — 나가기 성공 뒤 / 403 수신 시 캐시를 비운다. 폴링이 다음 주기에 다시 찾는다.
  - `ChatRoomViewModel.UiState.Error.notParticipant` → `ChatRoomRoute.onNotParticipant` → NavGraph 가 `forgetChatRoom`.
  - **진행 중인 내 방의 채팅방(헤더에 「매칭 화면으로」가 있는 방)에서는 「채팅방 나가기」 메뉴를 숨긴다** (`ChatScreen.canLeave`). 되돌아올 길이 없는 행동을 막는다.

## 검증
- 빌드·presentation 단위 테스트 통과
- 에뮬레이터: 홈 펼침(지도 200dp) / 핸들 드래그 → 접힘(지도가 검색 카드 위까지) 확인
- 26 시트·채팅 403 복구·전환 깜빡임은 실기 재현 조건(운행 중 파티·나간 방)이 필요해 미확인 — 폰 설치 후 사용자 확인
