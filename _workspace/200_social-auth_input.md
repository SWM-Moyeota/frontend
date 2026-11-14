# 소셜 인증 작업 범위

2026-11-14 승인 계획과 `/private/tmp/moyeota-social-auth-contract.md`에 따라 Android Google 로그인·가입·기존 계정 연결·소셜 탈퇴를 구현한다.

- 기존 `2832abb`, `589343f` 위에서 구현한다. 원본 작업 폴더는 수정하지 않는다.
- 팀 전체 역할 분배에 따라 이 작업자는 Android API/domain/Compose/QA를 함께 담당한다. 하위 에이전트는 추가하지 않는다.
- 적용 스킬: `.claude/skills/moyeota-dev/SKILL.md`, `api-integration`, `compose-feature`, `verify`.
- 서버와 운영 계정은 변경하지 않는다. PR/push/store 제출은 하지 않는다.
- Google 웹 client ID는 공개 식별자이며 기존 콘솔 전달 문서 값을 사용한다. 비밀값은 출력하지 않는다.
