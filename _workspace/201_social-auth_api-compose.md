# API·화면 구현 산출물

상세 계약, 변경 동작과 화면 진입 경로는 `docs/SOCIAL-AUTH-2026-11-14.md`에 기록했다.

- API 계층: AuthRepository/UserApi/RemoteAuthRepository 확장, LoginMethodDtos·매퍼 추가, USER405~407 오류 매핑.
- SDK: app/GoogleSignIn.kt, domain/GoogleCredentialProvider. Credential Manager 버튼 흐름을 사용하고 취소는 null로 전달한다.
- 가입: LoginStartRoute → SocialSignupState → ProfileSetupRoute/Screen → MannerPledgeRoute → 기존 세션/가입 완료. 소셜 분기는 계정 자격증명 입력을 생략한다.
- 계정 관리: 마이페이지 → LoginMethodsRoute → 등록번호 SMS 재인증 → Google 연결 → 목록 재조회. 비밀번호 보유 회원에만 재설정 메뉴를 표시한다.
- 탈퇴: AccountDeleteRoute가 로그인 수단을 먼저 조회하고 hasPassword로 비밀번호 폼과 API를 결정한다. 조회 실패는 재시도 화면이며 탈퇴를 보내지 않는다.
- 액션 갱신: 연결 성공 시 getLoginMethods 재조회. 가입·탈퇴는 기존 authState 세션 전이 사용.
- 실서버 미검증: 서버를 임의 기동하거나 운영 사용자 정보를 변경하지 않았다.
