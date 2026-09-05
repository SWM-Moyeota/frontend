# 91. api-integrator — 긴급 신고 API 연동 (data·domain)

계약: `_workspace/90_emergency_contract.md` | 검증: `:data:testDebugUnitTest` + `:domain:assembleDebug` 통과 (2026-09-06)

## 연동 엔드포인트 (스펙 출처: 백엔드 컨트롤러 소스 직접 확인)

| 메서드 + 경로 | 요청 body | 응답 | 비고 |
|---|---|---|---|
| `POST /api/v1/reports` | `{partyId: Long?, latitude: Double?, longitude: Double?}` | 200 `{reportId: Long}` | `@CurrentUser` Bearer 필수 |
| `PATCH /api/v1/reports/call-result` | `{called: Boolean}` | 204 (본문 없음) | `@CurrentUser` Bearer 필수 |

- 스펙 근거: `backend/.../report/interfaces/ReportController.java` + `report/application/dto/{ReportRequest,ReportResponse,CallResultRequest}.java` (컨트롤러 기준)
- 실서버: 백엔드 기검증 완료(계약 문서, 200/204 확인됨). **이 세션에서는 실서버 미검증** — 승객 앱은 토큰이 없어 어차피 401 (알려진 한계)

## Repository 시그니처 (compose-builder 공유용 — `RideRepository` 추가분)

```kotlin
/** 저장 실패 시 한국어 IllegalStateException. 화면은 실패와 무관하게 112 다이얼 우선 */
suspend fun reportEmergency(partyId: Long?): Long   // 반환 = 서버 reportId
suspend fun confirmEmergencyCall(called: Boolean)   // 다이얼 복귀 후 통화 여부 저장
```

## 변경/추가 파일

- `domain/.../repository/RideRepository.kt` — 메서드 2개 + KDoc
- `data/.../remote/dto/ReportDtos.kt` — `ReportRequestDto` / `ReportResponse` / `CallResultRequestDto` (서버 필드명 1:1, 사유 필드 없음)
- `data/.../remote/ReportApi.kt` — Retrofit 인터페이스 (POST/PATCH)
- `data/.../remote/NetworkModule.kt` — `Apis.report` 추가 (같은 Retrofit 재사용)
- `data/.../repository/RemoteRideRepository.kt` — 구현. 위치는 고정 좌표 상수(서울시청 37.5665/126.9780) + `TODO(측위 연동)`. 모든 실패(HTTP/네트워크/직렬화)를 한국어 `IllegalStateException` 하나로 래핑, `CancellationException` 은 그대로 전파
- `data/.../repository/DummyRideRepository.kt` — `1L` 반환 / no-op
- `data/src/test/.../remote/ReportDtosTest.kt` — 직렬화 테스트 4개 (필드명, partyId null 생략, reportId 역직렬화, called true/false)

## 리더 후속 필요 (범위 밖이라 미수정)

1. **AppContainer 배선 1줄** (app 모듈): `RemoteRideRepository(apis.matching)` → `RemoteRideRepository(apis.matching, reportApi = apis.report)`
   - 기존 호출을 깨지 않으려 `reportApi` 를 기본값 null 로 뒀음. 미배선 상태에서 신고 호출 시 "신고 API가 아직 연결되지 않았어요" IllegalStateException (화면은 다이얼 우선이라 흐름은 완주됨)
2. presentation(27 긴급 신고 화면) 연결 — compose-builder. 사유 선택 UI 없음(서버에 필드 없음)

## 알려진 한계 (PR 명시용)

- 인증(Bearer) 미연동 → 실서버 저장은 401. 인증 계층 도입 시 수정 없이 저장까지 동작 (범위 밖, 계약 문서 명시)
- 위치 고정 좌표(서울시청) — 측위 소스 도입 시 `RemoteRideRepository` 상수만 교체
- partyId null 시 서버가 REPORT_NOT_ALLOWED → IllegalStateException 으로 전파 (저장 실패 플래그 처리)
