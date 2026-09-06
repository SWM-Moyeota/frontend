# 92 · compose-builder — 긴급 신고(27) 새 흐름 개조

날짜: 2026-09-06 · 브랜치: (워크트리) frontend-report · 계약: `_workspace/90_emergency_contract.md`

## 변경 파일
| 파일 | 변경 |
|------|------|
| `presentation/src/main/kotlin/com/moyeota/presentation/feature/chat/EmergencyScreen.kt` | 사유 선택 UI 전부 제거(enum·ReasonCard·사유 아이콘·상세 입력). `EmergencyViewModel`(UiState+factory) + `EmergencyRoute` 추가, `EmergencyScreen` 은 스테이트리스 유지. 112 다이얼 발사·복귀 다이얼로그(`CallConfirmDialog`) 추가 |
| `presentation/src/main/kotlin/com/moyeota/presentation/core/MainNavGraph.kt` | 27 등록부만: `EmergencyScreen` → `EmergencyRoute(repository = rideRepository, partyId = selectedPartyId ?: createdPartyId, onReportSubmitted = ::back)` + import 교체 |

- 26 `RideOngoingScreen.kt`: 수정 없음 (기존 「신고」 진입 문구 그대로 유효)
- `Routes.kt` / designsystem: 수정 없음 (지시 준수)
- 하드코딩 hex 신규 추가 없음 — 기존 로컬 상수(EmergencyBg 등)만 유지, ReasonIconBg·chevron hex 는 사유 UI 와 함께 제거됨

## 상태 전이 (EmergencyViewModel.UiState)
```
초기 { }                       ─ 3초 홀드 완료(onEmergencyHold + UI 가 ACTION_DIAL tel:112) →
{ dialLaunched=true }           · reportEmergency(partyId) 발사 — 실패 시 reportSaveFailed=true 플래그만(다이얼은 이미 열림)
  ─ ON_RESUME(onResumedFromDial) →
{ showCallConfirm=true }        · "112와 실제로 통화하셨나요?" (back·바깥탭으로 안 닫힘)
  ─ 통화했어요/안 했어요(onCallConfirm) →
{ confirmSaving=true }
  ├ confirmEmergencyCall 성공 → { done=true } → onReportSubmitted() → 26 복귀
  └ 실패 → { confirmError="통화 여부 저장에 실패했어요…" } → 다이얼로그 유지, 재선택 가능
```
- 홀드 버튼은 dialLaunched 후 비활성(중복 발사 방지, 라벨 "112 통화 화면으로 연결했어요")
- 3초 중도 해제 시 미전송(기존 오작동 방지 로직 유지)

## QA 재현 경로
1. 앱 실행 → 온보딩 「건너뛰기」 → 04 로그인 「로그인」 → 14 홈
2. 하단탭 「채팅」 → 24 채팅 → 상단 운행 배너/위치 공유로 26 운행 중 진입
   (또는 14 홈 → 합승 흐름 → 25 배차 → 26; 26 은 15초 후 28 로 자동 전환되므로 빠르게 진행)
3. 26 하단 「신고」 탭 → 27 긴급 신고
4. 빨간 버튼 3초 홀드 → 112 다이얼러가 열림(번호만 입력됨, 발신 안 됨) — 동시에 POST /reports 발사
5. back 으로 앱 복귀 → "112와 실제로 통화하셨나요?" 다이얼로그(바깥 탭·back 무시 확인)
6. 「통화했어요」 또는 「통화 안 했어요」 → PATCH /reports/call-result → 26 복귀
- 알려진 한계(계약 문서): 승객 앱 Bearer 미연동으로 실서버 저장은 401 → reportSaveFailed 플래그로 처리되고 UI 흐름은 완주됨. 에뮬레이터에서 ACTION_DIAL 은 기본 다이얼러로 열림

## 빌드
`./gradlew :presentation:compileDebugKotlin --console=plain` → BUILD SUCCESSFUL (신규 경고 0, 기존 경고 2건은 무관 파일)
