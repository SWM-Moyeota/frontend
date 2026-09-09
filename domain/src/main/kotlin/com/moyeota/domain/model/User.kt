package com.moyeota.domain.model

/**
 * 파티 참여자를 화면에 그리기 위한 표시용 모델. 서버 `PartyDetailResult.MemberInfo` 가 출처다.
 *
 * [id] 는 서버 `publicId`(UUID v7, JWT `sub` 와 같은 값)다. **탈퇴 등으로 유저 요약이 없으면
 * 서버가 publicId·nickname·imageUrl 을 전부 null 로 내리므로 그때는 빈 문자열이다**
 * — 화면은 빈 id 를 "탭 불가"로 다뤄야 한다(프로필로 넘어갈 대상이 없다).
 *
 * [rating] 은 지금 항상 0.0 이다. 평가 API 자체가 서버에 없다 —
 * 화면은 0.0 을 "평가 없음"으로 표시해야 하며, 4.9·매너 98% 같은 가짜 값을 지어내면 안 된다.
 * [verifiedLabel] 도 같은 처지다: 서버 `badgeId` 가 TODO 라 항상 null 이므로 빈 문자열이 온다.
 */
data class User(
    val id: String,
    val nickname: String,
    val verifiedLabel: String, // 예: "성결대 인증", "직장 인증"
    val rating: Double,
    val rideCount: Int,
    /** 프로필 이미지 URL. 서버가 주지 않으면 null — 화면은 이니셜 원 같은 대체 표현을 쓴다. */
    val imageUrl: String? = null,
    /**
     * 이 멤버가 로그인한 본인인가. `publicId == UserSession.currentUserUuid` 로 판정한다.
     *
     * 기본값이 false 인 건 **식별자가 없는 응답**(목록·방 생성) 때문이다 — 그쪽 자리 표시용 멤버는
     * 누가 나인지 알 방법이 없다. 상세/합류 응답에서만 실제로 채워진다.
     */
    val isMe: Boolean = false,
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
