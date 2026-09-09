package com.moyeota.presentation.core

// v15 와이어프레임 화면 번호 · 화면 ID 매핑
object Routes {
    // A · 온보딩
    const val ONBOARDING_SAVING = "onboarding/saving"        // 01 O01
    const val ONBOARDING_TRUST = "onboarding/trust"          // 02 O02
    const val ONBOARDING_SAFETY = "onboarding/safety"        // 03 O03

    // B · 로그인
    const val LOGIN = "auth/login"                           // 04 S01
    const val LOGIN_FORM = "auth/login-form"                 // 04a 신규 (아이디 로그인)

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

    // C · 가입 (3단계: 10 → 11 → 12 → 13 완료)
    const val PROFILE_SETUP = "auth/profile"                 // 10 S05
    const val SAFETY_SETTINGS = "auth/safety-settings"       // 11 S06
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
    const val DISPATCH_STATUS = "ride/dispatch"              // 25 S14

    // G · 채팅 · 안심
    const val CHAT = "chat"                                  // 24 S16 (+24a 메뉴 · 24b 공유 시트)

    /**
     * 24 S16 — **채팅방 단독 목적지**. 21·25·26 의 「채팅 열기」가 이리로 push 한다.
     *
     * 채팅 탭([CHAT])으로 보내면 매칭 화면이 스택에서 빠져 뒤로가기로 돌아올 곳이 없어진다
     * (사용자 신고: "채팅방에서 매칭 진행 화면으로 돌아갈 수 없다"). 방을 **쌓아** 열면
     * 뒤로가기가 곧 원래 화면 복귀다. 채팅 탭 목록에서 여는 경로는 예전 그대로 탭 안에서 전환한다.
     */
    const val CHAT_ROOM = "chat/room/{roomId}"               // 24 S16 (단독 진입)

    fun chatRoom(roomId: Long) = "chat/room/$roomId"

    const val RIDE_ONGOING = "ride/ongoing"                  // 26 S15
    const val EMERGENCY = "ride/emergency"                   // 27 S17

    // H · 요금 · 정산 · 결제
    const val FARE_FINAL = "fare/final"                      // 28 S19a
    const val SETTLEMENT = "fare/settlement"                 // 29 S19
    const val PAYMENT_METHODS = "payment/methods"            // 30 S20
    const val PAYMENT_ADD = "payment/add"                    // 31 신규
    const val PAYMENT_RESULT = "payment/result"              // 32 S21

    // I · 완료 · 평가 · 기록
    const val RIDE_COMPLETE = "ride/complete"                // 33 S18
    const val MY_RIDES = "my-rides"                          // 34 S22
    const val MYPAGE = "mypage"                              // 35 S24
}
