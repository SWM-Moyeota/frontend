package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.AvatarCircle
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.SecondaryButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.RideRecord
import com.moyeota.domain.model.RideRecordMember

/**
 * 34 · 지난 탑승 기록 [S22 재도입]
 *
 * 진입: 35 마이페이지 「지난 탑승 기록」 행.
 *
 * 서버: `GET /api/v1/users/me/parties` — **하차까지 끝난 탑승만** 온다. 커서 페이징이고
 * `hasNext` 가 true 일 때만 `nextCursor` 로 다음 페이지를 받는다.
 *
 * ```
 * ← 지난 탑승 기록
 *   10월 31일 오후 4:15
 *   강남역 → 판교역
 *   (아바타들)  나 포함 1명
 *   1인 16,500원 · 총 16,500원     ← 요금이 없는 기록은 이 줄이 없다
 * ```
 *
 * ### 그리지 않는 것과 그 이유
 * - **상태 칩** — 서버가 상태를 주지 않는다. 이 목록은 전부 하차 완료라 상태 개념 자체가 없다.
 * - **방장 표시** — 응답 멤버에 `host` 필드가 없다(방 상세와 다르다).
 * - **평점·매너 점수** — 평가 API 가 없다. 지어낸 4.9 를 그리지 않는다.
 * - **「예정」 탭** — 예약 개념이 서버에 없다. 예전 34 가 이것 때문에 삭제됐다.
 * - **요금이 없는 기록의 요금 줄** — 1차 모드(기사 없이 합승 종료)로 끝난 방은 요금이 null 이다.
 *   0원으로 그리면 「공짜로 탔다」는 거짓말이 된다. 판정은 `RideHistoryDisplay.toHistoryItem`.
 *
 * ### 색은 토큰만 쓴다
 * 삭제됐던 `MyRidesScreen` 을 참고했지만 그 파일이 들고 있던 파일-로컬 그레이 5개
 * (`CanvasBg`·`GraySlate`·`GrayMute`·`GrayAsh`·`CardShadow`)는 **가져오지 않았다** — 토큰 밖의
 * 색이라 테마가 바뀌면 이 화면만 남는다. 더미 데이터와 「예정」 탭도 가져오지 않았다.
 */
@Composable
fun RideHistoryScreen(
    records: List<RideRecord> = emptyList(),
    /** 첫 페이지를 받는 중. 목록이 비어 있을 때만 전체 스피너를 그린다. */
    loading: Boolean = false,
    /** 다음 페이지를 받는 중. 목록 맨 아래 스피너다. */
    loadingMore: Boolean = false,
    /** true 면 끝까지 스크롤했을 때 [onLoadMore] 를 부른다. */
    hasNext: Boolean = false,
    /** 불러오기가 실패했는가. 문구는 하나다 — 사용자가 할 일은 「다시 시도」뿐이다. */
    failed: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val items = remember(records) { records.map { it.toHistoryItem() } }
    val listState = rememberLazyListState()

    // 마지막 카드가 보이면 다음 페이지. hasNext 가 false 면 트리거 자체가 서지 않는다
    // — 그래도 부르면 커서 없이 첫 페이지를 다시 받아 목록이 무한히 중복된다.
    // 키에 failed 가 **반드시** 들어가야 한다 — 빠뜨리면 실패로 loadingMore 가 내려가는 순간
    // 이 효과가 재시작해 같은 요청을 즉시 다시 쏘고, 그게 끝없이 돈다.
    LaunchedEffect(listState, hasNext, loadingMore, failed, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null &&
                    lastVisible >= items.lastIndex &&
                    shouldLoadMore(hasNext, loadingMore, failed)
                ) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "지난 탑승 기록", onBack = onBack)

        when {
            // 첫 조회가 끝나기 전에는 빈 상태 문구를 그리지 않는다 — 기록이 있는 사용자에게
            // 「아직 탑승 기록이 없어요」가 한 번 스쳐 보인다.
            loading && items.isEmpty() -> CenteredBox { CircularProgressIndicator() }

            failed && items.isEmpty() -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = RIDE_HISTORY_ERROR_TEXT,
                        style = MoyeotaType.BodyMd,
                        color = MoyeotaColor.TextMute,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton(text = "다시 시도", onClick = onRetry)
                }
            }

            items.isEmpty() -> CenteredBox {
                Text(
                    text = RIDE_HISTORY_EMPTY_TEXT,
                    style = MoyeotaType.BodyMd,
                    color = MoyeotaColor.TextMute,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // partyId 로 키를 준다 — 커서 페이징으로 뒤에 붙는 목록이라 위치 기반 재사용이
                // 일어나면 아바타 이미지가 다른 카드에 남는다(MemberDisplay KDoc 의 그 사고).
                items(items = items, key = { it.partyId }) { item -> RideHistoryCard(item) }

                if (loadingMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                // 2페이지 이후의 실패는 위의 `failed && items.isEmpty()` 분기에 걸리지 않는다
                // (이미 1페이지를 그리고 있으니 목록이 비어 있지 않다). 그 실패를 여기서 말하지
                // 않으면 사용자는 **왜 기록이 20건에서 멈췄는지 끝까지 알 수 없다.**
                if (failed && items.isNotEmpty() && !loadingMore) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "기록을 더 불러오지 못했어요",
                                style = MoyeotaType.BodySm,
                                color = MoyeotaColor.TextMute,
                            )
                            Spacer(Modifier.height(12.dp))
                            // 첫 페이지부터 다시 받지 않는다([onRetry] 는 그쪽이다) — 이미 보고 있던
                            // 기록과 스크롤 위치를 버리게 된다. 끊긴 지점부터 이어 받는다.
                            SecondaryButton(text = "다시 시도", onClick = onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 기록 한 장. **눌러도 갈 곳이 없어 탭 동작과 꺾쇠를 두지 않았다** — 끝난 방의 상세
 * (`GET /matching/rooms/{id}`)는 열리지만 그 화면(22)은 진행 중 방을 전제로 쓰여 있다.
 * 갈 곳이 없는데 꺾쇠를 달면 「눌러서 갈 곳이 있다」는 이 앱의 기호 규칙이 거짓이 된다.
 */
@Composable
private fun RideHistoryCard(item: RideHistoryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MoyeotaColor.SurfaceCard)
            .padding(16.dp),
    ) {
        if (item.dateLabel != null) {
            Text(text = item.dateLabel, style = MoyeotaType.CaptionMd, color = MoyeotaColor.TextMute)
            Spacer(Modifier.height(6.dp))
        }
        if (item.routeLabel != null) {
            Text(
                text = item.routeLabel,
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
        }

        if (item.members.isNotEmpty() || item.memberLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.members.forEach { member ->
                    RideHistoryAvatar(member)
                    Spacer(Modifier.width(6.dp))
                }
                if (item.memberLabel != null) {
                    Text(
                        text = item.memberLabel,
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextBody,
                    )
                }
            }
        }

        // 요금이 없는 기록(1차 모드)은 여기까지 아무것도 그리지 않는다. 「0원」을 쓰지 않는 자리다.
        val fare = item.fare
        if (fare != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MoyeotaColor.Hairline)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fare.perPersonLabel != null) {
                    Text(
                        text = fare.perPersonLabel,
                        style = MoyeotaType.HeadingMd,
                        color = MoyeotaColor.InkPrimary,
                    )
                }
                if (fare.perPersonLabel != null && fare.totalLabel != null) {
                    Text(
                        text = " · ",
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextAsh,
                    )
                }
                if (fare.totalLabel != null) {
                    Text(
                        text = fare.totalLabel,
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextMute,
                    )
                }
            }
        }
    }
}

/**
 * 동승자 아바타. 사진이 있으면 사진, 없거나 로드에 실패하면 **닉네임 첫 글자**다
 * (`AvatarCircle` 이 둘을 겹쳐 그리고 성공했을 때만 글자를 가린다).
 *
 * `MemberDisplay.MemberAvatar` 를 쓰지 않는다 — 그쪽은 `User` 를 받고 `isMe` 로 색이 갈리는데,
 * 기록의 멤버는 [RideRecordMember] 라 `User` 로 올리려면 없는 필드(`rideCount`·`host`·`badgeId`)를
 * 거짓값으로 채워야 한다. 그래서 디자인시스템의 `AvatarCircle` 을 직접 쓴다.
 */
@Composable
private fun RideHistoryAvatar(member: RideRecordMember) {
    AvatarCircle(
        size = 28.dp,
        label = member.nickname.firstOrNull()?.toString(),
        imageUrl = member.imageUrl,
    )
}

@Preview(showBackground = true)
@Composable
private fun RideHistoryScreenPreview() {
    RideHistoryScreen(
        records = listOf(
            RideRecord(
                partyId = 69,
                departure = "강남역",
                destination = "판교역",
                pickupTime = "2026-10-31T19:15:17.550211Z",
                dropoffTime = "2026-10-31T19:15:17.571486Z",
                totalFare = 16500,
                farePerPerson = 16500,
                memberCount = 1,
                members = listOf(
                    RideRecordMember(id = "u1", nickname = "프로브9911", imageUrl = null, isMe = true),
                ),
            ),
            // 1차 모드 기록 — 요금 줄이 없는 것이 정상이다
            RideRecord(
                partyId = 67,
                departure = "부산대 정문",
                destination = "서면역 1번 출구",
                pickupTime = null,
                dropoffTime = "2026-10-20T09:03:00Z",
                totalFare = null,
                farePerPerson = null,
                memberCount = 3,
                members = listOf(
                    RideRecordMember(id = "u1", nickname = "김OO", imageUrl = null, isMe = true),
                    RideRecordMember(id = "u2", nickname = "이OO", imageUrl = null, isMe = false),
                    RideRecordMember(id = "", nickname = "탈퇴한 회원", imageUrl = null, isMe = false),
                ),
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun RideHistoryScreenEmptyPreview() {
    RideHistoryScreen()
}

@Preview(showBackground = true)
@Composable
private fun RideHistoryScreenFailedPreview() {
    RideHistoryScreen(failed = true)
}
