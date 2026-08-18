package com.moyeota.presentation.core

// v15 와이어프레임 화면 번호 · 화면 ID 매핑
object Routes {
    // A · 온보딩
    const val ONBOARDING_SAVING = "onboarding/saving"        // 01 O01
    const val ONBOARDING_TRUST = "onboarding/trust"          // 02 O02
    const val ONBOARDING_SAFETY = "onboarding/safety"        // 03 O03

    // B · 로그인 · 계정 유형
    const val LOGIN = "auth/login"                           // 04 S01
    const val ACCOUNT_TYPE = "auth/account-type"             // 05 S25

    // C · 인증 · 가입
    const val SCHOOL_EMAIL = "auth/school-email"             // 06 S02
    const val EMAIL_CODE = "auth/email-code"                 // 07 S03
    const val WORK_VERIFY = "auth/work-verify"               // 08 S26
    const val IDENTITY_VERIFY = "auth/identity"              // 09 S04
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
