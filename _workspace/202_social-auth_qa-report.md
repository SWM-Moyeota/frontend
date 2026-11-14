# Android 소셜 인증 QA 결과

- debug 빌드 통과.
- data 테스트 619개, presentation 테스트 860개: 총 1,479개 통과. 실패·오류·건너뜀 0.
- release 빌드 및 lintVitalRelease 통과(56초). 산출물은 `app-release-unsigned.apk`, 서명 미설정.
- `git diff --check` 통과.
- 실제 backend 컨트롤러와 로그인 수단 조회·SMS 확인·Google 연결 DTO를 교차 확인했다.
- 신규 테스트는 가입 대기표/문자 증표 누락·만료, 로컬 자격증명 없는 소셜 가입, 연결 성공 시 목록 갱신·증표 소진, 중복 연결, 재발송 쿨다운, 소셜 전용 탈퇴, 연결 401 세션 보존과 HTTP 요청 계약을 검증했다.
- 기존 AccountRecoveryTest가 실시간 기반 타이머를 runTest 종료 시 계속 소진하던 문제를 테스트 finally의 ViewModel scope 취소로 보완했다.
- 실기 미검증: 실행 중 emulator-5554의 shell 무응답. 별도 read-only 콜드부팅은 기존 쓰기 가능 인스턴스와 충돌해 에뮬레이터가 거절했다. 기존 기기를 종료하지 않았다. 스크린샷 없음.
- 실서버·실계정 미검증: 서버 미기동을 유지했으며 운영 사용자의 가입·SMS·연결·탈퇴는 호출하지 않았다.
- 실제 iOS Apple 가입 → Google 연결 → Android 같은 UUID 로그인은 후속 실계정 검증이 필요하다.

전체 API·SDK·화면·콘솔 전제·재검증 순서·PR 초안은 `docs/SOCIAL-AUTH-2026-11-14.md`에 기록했다.
