---
name: moyeota-dev
description: "모여타 프론트엔드 개발 에이전트 팀 오케스트레이터. 화면 구현, 기능 추가, API 연동, 액션 API 연결, UI 수정, 버그 수정 등 코드 변경이 수반되는 모든 개발 요청 시 반드시 이 스킬을 사용할 것. 후속 작업도 포함: 다시 실행, 재실행, 수정, 보완, 업데이트, 이전 결과 개선, '화면만 다시', '연동만 다시', '검증만 다시' 요청 시에도 이 스킬을 사용. 단순 코드 질문/설명 요청은 직접 응답 가능."
---

# Moyeota Dev Orchestrator

모여타 프론트엔드 개발 작업을 「화면 구현(compose-builder) + API 연동(api-integrator) + 실기 검증(qa-verifier)」 에이전트 팀으로 수행하는 오케스트레이터.

## 실행 모드: 에이전트 팀

작업 범위가 단일 에이전트 영역에만 해당하면(예: 화면 1개 수정) 팀 없이 해당 에이전트 1명만 Agent 도구로 서브 실행해도 된다. 2개 이상 영역이 걸리면 팀을 구성한다 — 경계면(Repository 시그니처, DTO shape) 협의가 품질을 좌우하기 때문이다.

## 에이전트 구성

| 팀원 | 에이전트 타입 | 역할 | 스킬 | 출력 |
|------|-------------|------|------|------|
| compose-builder | compose-builder (커스텀) | 화면 구현·NavGraph | compose-feature | `_workspace/{NN}_compose-builder_*.md` + 코드 |
| api-integrator | api-integrator (커스텀) | API 연동·data/domain | api-integration | `_workspace/{NN}_api-integrator_*.md` + 코드 |
| qa-verifier | qa-verifier (커스텀) | 빌드·실기 검증·경계면 교차 비교 | verify | `_workspace/{NN}_qa-verifier_report.md` |

모든 Agent/TeamCreate 호출에 `model: "opus"`를 명시한다.

## 워크플로우

### Phase 0: 컨텍스트 확인 (후속 작업 지원)

1. `_workspace/` 존재 여부 확인
2. 실행 모드 결정:
   - **미존재** → 초기 실행, Phase 1로
   - **존재 + 부분 수정 요청** → 부분 재실행. 해당 에이전트만 재호출하고, 프롬프트에 이전 산출물 경로를 포함해 기존 결과를 읽고 피드백만 반영하게 한다
   - **존재 + 새 작업 입력** → 새 실행. 기존 `_workspace/`를 `_workspace_{YYYYMMDD_HHMMSS}/`로 이동 후 Phase 1로
3. git 상태를 확인해 진행 중 변경과 충돌하지 않는지 본다

### Phase 1: 준비

1. 요청 분석 — 대상 화면 번호(WIREFRAME-MVP1.md), 연동 엔드포인트, 검증 범위 도출
2. 프로젝트 루트에 `_workspace/` 생성, 요청 정리를 `_workspace/00_input.md`에 저장
3. 작업이 화면/연동/검증 중 어느 영역에 걸치는지 판단 → 팀 구성원 결정 (해당 없는 에이전트는 제외)

### Phase 2: 팀 구성

```
TeamCreate(
  team_name: "moyeota-dev-team",
  members: [
    { name: "api-integrator",  agent_type: "api-integrator",  model: "opus", prompt: "…연동 범위 + _workspace 경로…" },
    { name: "compose-builder", agent_type: "compose-builder", model: "opus", prompt: "…화면 범위 + _workspace 경로…" },
    { name: "qa-verifier",     agent_type: "qa-verifier",     model: "opus", prompt: "…검증 범위 + incremental QA 지시…" },
  ]
)
```

TaskCreate로 작업 등록. 의존 관계 원칙:
- Repository 시그니처 확정(api-integrator) → 화면 연결(compose-builder)은 `depends_on`으로 명시
- 각 구현 작업마다 대응하는 검증 작업을 만들고 qa-verifier에게 할당 (전체 완성 후 1회가 아니라 **모듈 완성 직후 검증** — incremental QA)

### Phase 3: 구현 + 점진 검증

**실행 방식:** 팀원 자체 조율

팀원 간 통신 규칙:
- api-integrator는 Repository 시그니처 확정·변경 시 즉시 compose-builder에게 SendMessage
- 구현 완료 시 담당자가 qa-verifier에게 검증 요청 (변경 파일 + 진입 경로 포함)
- qa-verifier는 결함 발견 시 담당 에이전트에게 파일:라인 + 수정 방법으로 요청. 경계면 이슈는 양쪽 모두에게 알림. 재검증 루프 최대 2회, 이후 리더 에스컬레이션

리더 모니터링: 유휴 알림 수신 시 TaskGet으로 진행률 확인, 막힌 팀원에게 SendMessage로 개입.

### Phase 4: 최종 검증 및 통합

1. 모든 작업 완료 확인 (TaskGet)
2. qa-verifier의 최종 리포트(`_workspace/*_qa-verifier_report.md`) 수집 — 통과/실패/미검증 구분 확인
3. 리더가 직접 최종 빌드 1회 실행: `./gradlew :app:assembleDebug :data:testDebugUnitTest --console=plain`
4. 실패 항목이 남았으면 해당 에이전트 재호출 (1회), 재실패 시 미해결로 명시

### Phase 5: 정리

1. 팀원 종료 요청(SendMessage) 후 TeamDelete
2. `_workspace/` 보존 (감사 추적용)
3. 사용자에게 보고: 변경 파일, 검증 결과(스크린샷 경로 포함), 미해결·미검증 항목, 남은 작업
4. 피드백 기회 제공 — 개선 요청이 있으면 하네스 진화(CLAUDE.md 변경 이력 갱신)로 연결

## 데이터 흐름

```
[리더] → TeamCreate
   api-integrator ──시그니처 확정──→ compose-builder
        │                              │
        └──완료 알림──→ qa-verifier ←──완료 알림──┘
        ↓                   ↓                  ↓
  _workspace/NN_api…   NN_qa…report.md   NN_compose…
                            ↓
                   [리더: 최종 빌드 + 보고]
```

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| 팀원 1명 실패/중지 | SendMessage로 상태 확인 → 재시작 1회 → 실패 시 작업을 리더가 직접 수행하거나 미해결 명시 |
| 빌드 실패 반복(2회+) | 해당 영역 롤백 여부를 사용자에게 확인 |
| 에뮬레이터 부팅 실패 | qa-verifier가 정적 검증만 수행, 보고서에 "실기 미검증" 명시 |
| 백엔드 미기동 | 함부로 띄우지/죽이지 않음. 컨트롤러 소스 기준 스펙 확정 + "실서버 미검증" 플래그 |
| 경계면 합의 불발 | 리더가 domain 계약(Repository 인터페이스) 기준으로 중재 |

## 테스트 시나리오

### 정상 흐름 (액션 API 연동)
1. 사용자: "합류 액션 API 연동해줘"
2. Phase 1: 대상 = POST 합류 엔드포인트 + JoinConfirm 화면(20) + Explore 갱신
3. Phase 2: 3명 팀 구성, 시그니처 확정 → 화면 연결 의존성 등록
4. Phase 3: api-integrator가 DTO/매퍼/Repository 구현·테스트 → 시그니처 공유 → compose-builder가 ViewModel 연결 → qa-verifier가 경계면 교차 비교 + 에뮬레이터 실기 합류 플로우 확인
5. Phase 4~5: 최종 빌드 통과, 스크린샷과 함께 보고

### 에러 흐름
1. Phase 3에서 백엔드 미기동 발견
2. api-integrator가 컨트롤러 소스 기준으로 스펙 확정, "실서버 미검증" 플래그
3. qa-verifier는 airplane-mode 프로브로 에러 상태 UI만 실기 검증
4. 최종 보고에 "실서버 연동 미검증 — 백엔드 기동 후 재검증 필요" 명시
