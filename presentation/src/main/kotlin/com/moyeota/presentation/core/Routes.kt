package com.moyeota.presentation.core

// v15 와이어프레임 화면 번호 · 화면 ID 매핑
object Routes {
    const val LOGIN_METHODS = "mypage/login-methods" // 로그인 수단 관리
    // A · 온보딩
    const val ONBOARDING_SAVING = "onboarding/saving"        // 01 O01
    const val ONBOARDING_TRUST = "onboarding/trust"          // 02 O02
    const val ONBOARDING_SAFETY = "onboarding/safety"        // 03 O03

    // B · 로그인
    const val LOGIN = "auth/login"                           // 04 S01
    const val LOGIN_FORM = "auth/login-form"                 // 04a 신규 (아이디 로그인)

    // 04b 아이디 찾기 · 04c 비밀번호 재설정 (신규, 2026-11) — 백엔드 #314 · #331.
    // 둘 다 04a 위에 쌓이고, 끝나면 04a 로 되돌아간다. iOS 가 먼저 붙인 화면이다(frontend-ios #47).
    const val FIND_ID = "auth/find-id"                       // 04b 신규 (아이디 찾기)
    const val PASSWORD_RESET = "auth/password-reset"         // 04c 신규 (비밀번호 재설정)

    // B' · 부가 인증 05~09 — **가입 그래프에 등록하지 않는다**
    //
    // 서버 가입(POST /api/v1/users)에는 계정 유형 개념이 없고(loginId·password·name·birthDate·
    // phone·gender·email 뿐), 학교/재직 인증은 「마이페이지에서 나중에 추가」하는 컨셉이다.
    // 09 휴대폰 본인 인증은 MVP 범위 밖이라 인증 없이 값만 묻는 화면이 되어 있었다
    // — 받던 값(실명·생년월일·성별·휴대폰)은 10 프로필의 「기본 정보」 절이 이어받았다.
    // 가입 필수 경로에 세워두면 아무 데도 쓰이지 않는 값을 4~5화면 더 묻게 된다.
    // 화면 파일과 라우트 상수는 남겨둔다 — 인증을 붙일 때 그대로 재사용한다.
    const val ACCOUNT_TYPE = "auth/account-type"             // 05 S25 (미연결)
    const val SCHOOL_EMAIL = "auth/school-email"             // 06 S02 (미연결)
    const val EMAIL_CODE = "auth/email-code"                 // 07 S03 (미연결)
    const val WORK_VERIFY = "auth/work-verify"               // 08 S26 (미연결)
    const val IDENTITY_VERIFY = "auth/identity"              // 09 S04 (미연결)

    // C · 가입 (2단계: 10 → 12 → 13 완료)
    const val PROFILE_SETUP = "auth/profile"                 // 10 S05
    const val MANNER_PLEDGE = "auth/manner-pledge"           // 12 S07
    const val SIGNUP_COMPLETE = "auth/signup-complete"       // 13 S08

    // D · 홈 · 목적지
    const val HOME = "home"                                  // 14 S09
    const val DESTINATION = "home/destination"               // 15 S10
    const val DESTINATION_CONFIRM = "home/destination-confirm" // 16 신규 (모달)

    // E · 합승 탐색 (17–19 는 한 화면의 시트 상태)
    const val EXPLORE = "explore"                            // 17–19 V07·V07b·V07c
    const val JOIN_CONFIRM = "explore/join-confirm"          // 20 신규

    // F · 매칭 · 탑승
    const val MATCH_WAITING = "matching/waiting"             // 21 S11
    const val RIDE_DETAIL = "ride/detail"                    // 22 S12
    const val PARTNER_PROFILE = "ride/partner-profile"       // 23 S13

    // G · 채팅 · 안심
    const val CHAT = "chat"                                  // 24 S16 (+24a 메뉴 · 24b 공유 시트)

    /**
     * 24 S16 — **채팅방 단독 목적지**. 21 대기 화면의 「채팅 열기」가 이리로 push 한다.
     *
     * 채팅 탭([CHAT])으로 보내면 매칭 화면이 스택에서 빠져 뒤로가기로 돌아올 곳이 없어진다
     * (사용자 신고: "채팅방에서 매칭 진행 화면으로 돌아갈 수 없다"). 방을 **쌓아** 열면
     * 뒤로가기가 곧 원래 화면 복귀다. 채팅 탭 목록에서 여는 경로는 예전 그대로 탭 안에서 전환한다.
     */
    const val CHAT_ROOM = "chat/room/{roomId}"               // 24 S16 (단독 진입)

    fun chatRoom(roomId: Long) = "chat/room/$roomId"

    // I · 마이페이지
    const val MYPAGE = "mypage"                              // 35 S24
    const val PROFILE_EDIT = "mypage/profile-edit"           // 36 신규 (닉네임 수정)

    /**
     * 34 지난 탑승 기록 (2026-11-01 재도입) — `GET /api/v1/users/me/parties`.
     *
     * 예전의 34 「내 탑승」과 **보여 주는 것이 다르다.** 그쪽은 「진행 중 · 예정」 탭이었고
     * 서버에 완료 방 목록도 예약 개념도 없어 삭제됐다(`docs/BACKEND-GAP-CLEANUP.md` §1).
     * 지금 이 화면은 **하차까지 끝난 탑승만** 그린다 — 서버가 그것만 주기 때문이다.
     * 진행 중인 방은 홈·탐색 배너가 맡으므로 35 와의 순환 링크도 되살리지 않았다.
     */
    const val RIDE_HISTORY = "mypage/ride-history"           // 34 재도입 (지난 탑승 기록)

    // 38 노쇼 기록·이의 신청은 **없다** (2026-11-12 제거). 서버가 노쇼 기능을 통째로 지워
    // (`b16551b7`, V37 이 테이블 DROP) 그 화면이 붙을 엔드포인트가 둘 다 404 다. 되살리지 마라 —
    // 라우트만 있고 서버가 없으면 「불러오지 못했어요」로만 떨어지는 길이 하나 생길 뿐이다.

    /**
     * 37 회원 탈퇴 (신규, 2026-10) — `DELETE /api/v1/users/me`. Google Play 계정 삭제 요건.
     *
     * 다이얼로그가 아니라 **별 화면**인 이유는 [com.moyeota.presentation.feature.mypage.AccountDeleteScreen]
     * KDoc 에 있다(서버가 비밀번호를 본문으로 요구한다 → 입력란 + 두 가지 실패 표시 + 경고 네 줄).
     *
     * 성공 뒤의 이동을 이 라우트가 하지 않는다 — 세션이 사라지면 [MainNavGraph] 의 로그인 전이
     * 감지가 04a 로 보내면서 스택을 통째로 비운다(이 화면도 그때 사라진다).
     */
    const val ACCOUNT_DELETE = "mypage/account-delete"       // 37 신규 (회원 탈퇴)

    // J · 택시비 정산 39–40 (신규, 2026-11-12)
    //
    // **38 을 비워 두는 이유**: 그 번호는 노쇼 기록·이의 신청이 쓰다가 서버 기능이 통째로 사라져
    // 지운 자리다(바로 위 주석). 되쓰면 옛 문서·보고서의 「38」이 두 가지를 가리키게 된다.
    //
    // 두 라우트가 **같은 `{partyId}` 를 경로 인자로** 받는다. 화면 바깥에 방 id 를 들고 있는
    // 홀더(`selectedPartyId`·`createdPartyId`)를 쓰지 않는 이유는 진입점이 셋이고 서로 다른
    // 스택 위치에서 오기 때문이다(끝난 방 상세 · 탑승 기록 카드 · 홈 배너). 홀더에 기대면
    // 「홈 배너로 열었는데 직전에 보던 방의 정산이 뜨는」 혼동이 가능해지고, **돈이 걸린 화면에서
    // 그 혼동의 대가가 가장 크다.** 경로 인자는 구성 변경·프로세스 복원에도 그대로 살아남는다.

    /**
     * 39 택시비 정산 상세 — **역할에 따라 받는 사람/보내는 사람 화면이 갈린다**
     * (서버가 `myRole` 로 같은 엔드포인트에서 두 모양을 준다).
     *
     * 정산이 아직 없는 방도 이 라우트로 들어온다 — 404 는 **정상 상태**이고 화면이
     * 「정산 시작하기」로 그린다([com.moyeota.presentation.feature.faresplit.FareSplitAbsentScreen]).
     */
    const val FARE_SPLIT = "faresplit/{partyId}"             // 39 신규 (정산 상세)

    fun fareSplit(partyId: Long) = "faresplit/$partyId"

    /**
     * 40 택시비 정산 시작·수정 — **방장 전용**. 39 위에 쌓이므로 뒤로가기가 곧 상세 복귀다.
     *
     * 시작과 수정이 **같은 라우트**인 이유: 서버가 POST·PATCH 에 같은 본문을 쓰고
     * ([com.moyeota.domain.model.FareSplitDraft]) 화면도 같다. 어느 쪽인지는 `start` 인자가 정한다 —
     * 라우트를 둘로 나누면 같은 화면이 두 곳에 등록되고, 한쪽만 고치는 날이 온다.
     */
    const val FARE_SPLIT_EDIT = "faresplit/{partyId}/edit?start={start}" // 40 신규 (정산 시작·수정)

    /** @param start true 면 시작(POST), false 면 수정(PATCH). */
    fun fareSplitEdit(partyId: Long, start: Boolean) = "faresplit/$partyId/edit?start=$start"
}
