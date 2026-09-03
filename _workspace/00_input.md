# 작업 입력 — 백엔드(KII1ua 작성분) API 프론트 연결

날짜: 2026-08-30 · 브랜치: feature/naver-map (naver-map 미커밋 변경 위에 작업, 되돌리지 말 것)
백엔드 레포: /Users/sungyoon/Desktop/moyeota/backend (브랜치 feature/driver-report)

## 연동 대상 (승객 앱에서 소비 가능한 것만)

1. **매칭 계약 갱신** — 백엔드 매칭 도메인 변경 반영
   - 신규: `POST /api/v1/matching/rooms/{partyId}/{memberId}/join`
   - 제거: `POST/DELETE api/v1/matching/ready/{partyId}/{memberId}`, `POST api/v1/matching/start/{partyId}/{memberId}` — 백엔드에서 삭제됨 (정원 도달 시 서버가 자동 기사 매칭 시작, Party.matchStartedAt 추가)
   - 소스: `matching/interfaces/PartyController.java`
2. **승객→기사 정보 확인**: `GET /api/v1/matching/rooms/{partyId}/driver` → DispatchStatusScreen(배차 현황) 기사명·차량·별점
3. **기사 위치 조회**: `GET /api/v1/dispatch/rides/{partyId}/{memberId}` (DriverLocationResponse) → DispatchStatusScreen 지도에 기사 위치 폴링 표시
   - 소스: `dispatch/interfaces/RideController.java`
4. **경로 조회**: `GET /api/v1/matching/routes` (네이버 지도 polyline 인코딩) → 지도 경로 표시
5. **기사 신고**: `POST /api/v1/reports`, `PATCH /api/v1/reports/{reportId}/call-result` → EmergencyScreen/신고 플로우
   - 소스: `report/interfaces/ReportController.java`

## 제외 (연동하지 않음)

- 기사 앱 전용 (별도 레포 예정): 콜 수락/거절/상태(`/dispatch/calls/*`), 운행 상태 변경(`/dispatch/rides/*/arrive|board|complete`), 기사 위치 보고(`/dispatch/online|location`), 기사 등록·차량·콜토글·FCM(`/drivers/*`)
- FCM 푸시 수신: 승객측 토큰 등록 엔드포인트 부재 → 백엔드 요청 목록에 추가
- 채팅 Redis Pub/Sub: 서버 내부 변경, 클라이언트 영향 없음

## 팀 구성

api-integrator(계약 갱신+DTO/매퍼/Repository) → compose-builder(화면 연결) → qa-verifier(빌드+실기)
