package com.moyeota.domain.model

data class User(
    val id: String,
    val nickname: String,
    val verifiedLabel: String, // 예: "성결대 인증", "직장 인증"
    val rating: Double,
    val rideCount: Int,
)

/**
 * 로그인한 본인의 서버 기준 신원. GET /api/v1/local/users/info 의 결과다.
 *
 * 같은 파일의 [User] 와는 쓰임이 다르다 — [User] 는 파티 참여자를 화면에 그리기 위한 표시용 모델이고,
 * 이쪽은 **서버가 확인해 준 "나"** 다. 별점·인증 라벨 같은 건 이 엔드포인트가 주지 않는다.
 *
 * [name] 이 nullable 인 이유: 서버가 "닉네임 → 프로필 실명 → null" 순으로 폴백하기 때문이다.
 * 현재 서버에는 닉네임을 설정하는 경로가 없어 사실상 항상 실명이 오지만, 프로필이 비어 있으면 null 이다.
 * **화면은 null 을 반드시 처리해야 한다** — "이름 없음" 폴백 문구든, 이름 영역 숨김이든.
 *
 * [uuid] 는 [AuthState.Authenticated.userUuid] 와 같은 값이다(액세스 토큰 `sub`). 즉 uuid 만 필요하면
 * 이 호출은 필요 없다 — 이 엔드포인트를 부르는 이유는 오직 [name] 이다.
 */
data class UserProfileInfo(
    val uuid: String,
    val name: String?,
)
