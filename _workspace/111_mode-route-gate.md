# 111 · 동승/택시 모드 분기 정리 (feat/mode-route-gate)

- 배경: 실배포(동승)와 개발 중(택시) 화면이 달라 조율 기준이 필요했다. PR #47 머지 직후 작업.
- 결정: 브랜치·플레이버 분리 대신 **코드 하나 + 서버 플래그**. 모드는 문구·버튼만, 이동은 status. `docs/MODE-ROUTES.md`.
- 변경
  - `AppConfig.Default`·`AppConfigResponse.taxiEnabled` 기본값 true → **false**(동승). 서버 장애 시 택시 문구가 뜨던 문제.
  - `AppConfigViewModel`: 실패 시 2·5·15초 재시도, onResume 재시도, 디버그 강제값(`setDebugOverride`), `config = 서버 값 ⊕ 강제값`.
  - `core/AppMode.kt`: `AppMode`, `effectiveTaxiEnabled`, `isStageOutsideMode` (+ `AppModeTest`).
  - `MainNavGraph`: 동승 모드에 배차·운행 단계가 오면 status 를 따르되 설정 재조회. 디버그 빌드에서 35 에 개발자 옵션 전달.
  - `MyPageScreen`: `ModeDebugOptions` — 서버 모드 표시 + 서버 값/동승 강제/택시 강제 칩. 릴리스엔 안 그림.
  - 21 `MatchWaitingRoute/Screen` 의 `taxiEnabled` 파라미터 기본값 false.
- 검증: 아래 빌드·테스트 로그 참조.
