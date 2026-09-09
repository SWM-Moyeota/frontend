package com.moyeota.presentation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.domain.model.User

/**
 * 파티 멤버(동승자)를 화면에 그릴 때 쓰는 공용 표기 규칙.
 *
 * 20 합류 확인 · 21 매칭 대기 · 22 탑승 상세 · 23 동승자 프로필 · 25 배차 현황이 같은 데이터를
 * 조금씩 다른 레이아웃으로 보여준다. **문구 규칙만은 한 곳에 둔다** — "탑승 0회"를 한 화면은
 * 「첫 탑승」으로, 다른 화면은 「탑승 0회」로 쓰면 같은 사람이 화면마다 달라 보인다.
 *
 * 서버 계약(2026-09): 방 상세 `members[]` 는 유저 요약이 없으면(탈퇴 등) publicId·nickname·
 * imageUrl 이 전부 null 로 온다. 매퍼가 그걸 `id = ""` · `nickname = ""` 으로 접어 주므로
 * 화면은 [isWithdrawn] 하나로 판정한다.
 */

/** 유저 요약이 없는 멤버(탈퇴 등). 프로필로 들어갈 대상이 없다 — 탭을 막아야 한다. */
internal val User.isWithdrawn: Boolean
    get() = id.isBlank()

/** 목록·프로필에 찍는 이름. 탈퇴 회원은 닉네임이 없다. */
internal val User.displayNickname: String
    get() = if (isWithdrawn || nickname.isBlank()) "탈퇴한 회원" else nickname

/**
 * 탑승 횟수 문구. 0회는 "탑승 0회"(빈 실적처럼 읽힌다)가 아니라 「첫 탑승」으로 쓴다
 * — 서버 rideCount 는 FINISHED 파티 수라 신규 가입자는 항상 0 이다.
 */
internal val User.rideCountLabel: String
    get() = if (rideCount <= 0) "첫 탑승" else "탑승 ${rideCount}회"

/**
 * 인증 라벨. 서버 `badgeId` 가 아직 항상 null 이라 대부분 빈 값으로 온다 —
 * 빈 값에 아무 것도 안 쓰면 "인증된 사람"으로 오해될 여지가 있어 중립 문구를 명시한다.
 */
internal val User.verifiedLabelOrNone: String
    get() = if (verifiedLabel.isBlank()) "인증 정보 없음" else verifiedLabel

/**
 * 아바타 이니셜 원.
 *
 * [User.imageUrl] 이 있어도 **지금은 쓰지 않는다** — 앱에 이미지 로더(Coil 등) 의존성이 없다.
 * 네트워크 이미지를 직접 내려받아 그리는 코드를 화면에 심느니, 로더가 붙을 때까지 모든 멤버를
 * 같은 이니셜 원으로 통일한다(일부만 사진이 보이는 상태가 더 어색하다).
 */
@Composable
internal fun MemberAvatar(user: User, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initial = if (user.isWithdrawn || user.nickname.isBlank()) {
        "?"
    } else {
        user.nickname.take(1)
    }
    Box(
        modifier = modifier
            .size(size)
            .background(if (user.isMe) MoyeotaColor.Primary50 else MoyeotaColor.SurfaceSoft, CircleShape)
            .border(1.dp, MoyeotaColor.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
            color = if (user.isMe) MoyeotaColor.Primary600 else MoyeotaColor.TextMute,
        )
    }
}

/** 「나」 배지 — 멤버 목록에서 내 줄을 한눈에 찾게 한다(isMe = publicId == 세션 uuid). */
@Composable
internal fun MeBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MoyeotaColor.Primary50, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = "나", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.Primary600)
    }
}
