# 긴급 신고(새 흐름) 계약 — 승객 앱 (리더 확정)

## 흐름 (사용자 확정 흐름도)
26 운행 중 「신고」 탭 → 27 긴급 신고 화면 → **3초 홀드** 완료 시:
①`POST /api/v1/reports {partyId, latitude, longitude}` 저장 발사 ②동시에 112 다이얼러(ACTION_DIAL — 번호만 입력, 발신은 사용자)
→ 앱 복귀(ON_RESUME) → "112와 실제로 통화하셨나요?" 다이얼로그 → `PATCH /api/v1/reports/call-result {called}` → 26 복귀
- 신고 저장 실패해도 다이얼은 반드시 열림 (통화 최우선)
- **사유 선택 UI 제거** — 서버 ReportRequest 에 사유 필드가 없음 (partyId/latitude/longitude 뿐)

## 도메인 계약 (RideRepository 추가)
```kotlin
/** 긴급 신고 접수 — 서버가 파티·위치 저장, 신고 id 반환. 위치는 데이터 계층이 채운다(현재 고정 좌표 TODO) */
suspend fun reportEmergency(partyId: Long?): Long
/** 다이얼 복귀 후 실제 통화 여부 저장 */
suspend fun confirmEmergencyCall(called: Boolean)
```
- partyId: 화면에서 알 수 없으면 null 허용 (서버는 REPORT_NOT_ALLOWED 응답 — 저장 실패 플래그로 처리)

## 알려진 한계 (PR 에 명시)
- 승객 앱에 인증(Bearer) 미연동 → 실서버 저장은 현재 401. UI 흐름은 완주되며 인증 연동 시 수정 없이 저장까지 동작
- 위치: 승객 앱에 측위 소스 없음 → 데이터 계층 고정 좌표 + TODO (기사 앱과 같은 단계적 접근)

## 검증 (백엔드 기검증 완료 — 참고)
실서버에서 승객 토큰 신고 200 {reportId} → call-result 204 확인됨 (2026-09-06, 드라이버 세션)
