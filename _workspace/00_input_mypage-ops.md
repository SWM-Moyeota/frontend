# 입력 — 운영용 마이페이지 정비 (feat/mypage-ops, #48 위에 쌓음)

사용자 요청(2026-10-10): "실운영 마이페이지에 있어야 할 것 중 앱에서 가능한 것 먼저 만들고, 백엔드 요청은 이슈로 정리".

## 앱에서 지금 가능한 것 (이번 작업)
- 35 마이페이지 메뉴 재구성: 프로필 수정 진입, 알림 설정(OS 설정), 문의하기(메일), 이용약관, 개인정보 처리방침, 로그아웃, 버전, (디버그) 개발자 옵션 유지
- 36 프로필 수정(닉네임) 신규 — `PATCH /users/me`(nickname) + 닉네임 중복 확인 API 는 이미 연동돼 있음(AuthRepository.updateProfile / isNicknameTaken)
- 약관·개인정보·문의 주소는 아직 없음 → `SupportLinks` 한 곳에 nullable 상수, null 이면 행 숨김

## 백엔드 요청(이슈)
- 계정 탈퇴 API (+ 웹 탈퇴 안내 페이지) — Google Play 요건
- 완료된 방 목록(탑승 기록) API
- 즐겨찾기 삭제/수정 API (현재 POST/GET 만)
- `GET /local/users/info` 응답에 imageUrl·phone 등 프로필 필드 확장(선택)

## 사실 확인
- `/local/users/info` 의 name = 닉네임(없으면 실명) → 36 프리필 가능
- `PATCH /users/me` 는 nickname(2~10자)·imageUrl 만 받음, 204
- 즐겨찾기: POST/GET 만 존재, DELETE 없음
- 백엔드에 탈퇴·완료 방 목록 엔드포인트 없음
