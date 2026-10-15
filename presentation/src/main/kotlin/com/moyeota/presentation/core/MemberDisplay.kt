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
 * 서버 계약: 방 상세 `members[]` 는 유저 요약이 없으면(탈퇴 등) publicId·nickname·imageUrl 이
 * 전부 null 로 온다. 매퍼가 그걸 `id = ""` · `nickname = ""` 으로 접어 주므로 화면은
 * [isWithdrawn] 하나로 판정한다.
 *
 * **탈퇴 판정을 `imageUrl == null` 로 바꾸지 마라.** 2026-10-15 배포(Backend #235)부터 멀쩡한
 * 사용자도 기본 이미지의 전체 URL 을 받는다 — 그 둘을 바꾸면 판정이 정확히 뒤집혀 **전원이
 * 탈퇴 회원으로 표시된다.** 판정의 출처는 publicId 하나다.
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
 *
 * 서버는 2026-10-15 부터 제공 이미지 16장을 고르게 하고 `imageUrl` 로 전체 주소를 내려준다.
 * 그리기로 할 때의 선택지와 비용은 `_workspace/profile-image-plan.md` 에 정리해 뒀다.
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

/**
 * 「방장」 배지를 그릴 행인가 — **내 줄에는 그리지 않는다.**
 *
 * 방장 표시는 2026-10-15 에 되살렸다. 예전에 지웠던 것(`_workspace/33_api-integrator_no_host.md`·
 * `34_compose-builder_no_host.md`)과 **근거가 다르다**: 그때는 서버에 방장 개념이 아예 없어서 앱이
 * `joinedAt` 최소값으로 **추정**하던 것이었고, 지금 [User.isHost] 에 담긴 값은 서버가 멤버마다
 * 내려 주는 `MemberInfo.host` 다(Backend #240). 즉 **추정이 아니라 서버 값**이다 — 앱은 입장 순서를
 * 세지 않는다.
 *
 * 그런데 **서버가 `host` 를 빼먹었을 때의 폴백이 한쪽으로만 기울어 있다.** 매퍼가 `host ?: isMe` 로
 * 접기 때문에(`PartyMappers.toUser` 의 표) 옛 배포를 보고 있으면 **내 줄만** true 가 된다. 그건
 * 의도된 fail-open 이다 — 방장이 「합승 완료」를 못 보면 방을 끝낼 길이 없어 30분 만료를 기다려야
 * 하는 쪽이 복구 불가이고, 반대로 틀렸을 때는 서버 403 `NOT_PARTY_HOST` 가 바로잡아 준다.
 *
 * **그 폴백을 배지에 그대로 쓰면 안 된다.** 버튼의 거짓은 눌렀을 때 서버가 바로잡지만, 배지의
 * 거짓은 아무도 바로잡지 않는다 — 비방장이 자기 줄에서 「방장」을 읽고, 심지어 눌렀을 때 받는
 * 「방장만 합승을 완료할 수 있어요」와 **정면으로 모순**되는 화면이 된다. 사람에 대해 지어낸
 * 사실을 띄우는 건 이 저장소가 거짓 문구 19건을 들어내며 금지한 바로 그것이다(`ProductClaimsTest`).
 *
 * 그래서 내 줄을 뺀다. [User.isMe] 는 한 명뿐이라 폴백이 만들 수 있는 거짓 true 도 내 줄 하나뿐이고,
 * 내 줄을 빼면 이 값은 정확히 **「서버가 이 사람을 방장이라고 말했다」** 가 된다. 잃는 것은 「내가
 * 방장일 때 내 줄에 붙는 배지」 하나인데, 그건 21 대기 화면 하단이 이미 말한다 — 방장에게는
 * 「합승 완료」 버튼이 보이고, 비방장에게는 방장을 가리키는 안내문이 보인다.
 */
internal val User.showsHostBadge: Boolean
    get() = isHost && !isMe

/**
 * 안내문에서 **이름으로 부를 수 있는** 방장 — 부를 수 없으면 null.
 *
 * [showsHostBadge] 와 **같은 판정**을 쓴다(배지가 붙은 사람과 안내문이 부르는 사람이 어긋나면 안 된다).
 * 거기에 탈퇴 조건을 하나 더 얹는다: 요약이 없는 멤버는 닉네임이 "탈퇴한 회원"으로 채워져
 * ([displayNickname] 아님 — 매퍼가 이미 그 문자열을 넣는다) 공백 검사로는 걸러지지 않는데,
 * 「방장 탈퇴한 회원님이…」는 사람 이름이 아니다.
 *
 * null 이 나오는 경우는 둘이다: 방장이 탈퇴해 이름이 없거나, 내가 멤버 목록에 없어(식별자 없는
 * 응답) 누구도 방장으로 지목되지 않았거나. 그때 문구는 이름 대신 **조건**을 말한다
 * ([com.moyeota.presentation.feature.matching.finishHostNotice]).
 */
internal fun List<User>.hostDisplayNameOrNull(): String? =
    firstOrNull { it.showsHostBadge && !it.isWithdrawn }?.nickname?.takeIf { it.isNotBlank() }

/**
 * 「방장」 배지 — [showsHostBadge] 인 행에만 붙인다.
 *
 * 「나」([MeBadge])와 색을 달리한다. 둘은 같은 목록에 함께 뜨지만(내 줄에 「나」, 방장 줄에 「방장」)
 * 뜻이 다르다 — 하나는 신원, 하나는 **역할**이다. 같은 Primary 알약으로 그리면 같은 종류의
 * 표시로 읽힌다.
 */
@Composable
internal fun HostBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MoyeotaColor.SurfaceSoft, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = "방장", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MoyeotaColor.TextMute)
    }
}
