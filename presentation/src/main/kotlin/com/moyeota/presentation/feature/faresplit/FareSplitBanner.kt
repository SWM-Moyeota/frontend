package com.moyeota.presentation.feature.faresplit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.FareSplitParticipant
import com.moyeota.domain.model.MyFareSplit
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.repository.FareSplitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 14 홈의 **「아직 보내지 않은 택시비」 배너** — 서버 Swagger 가 이 용도를 직접 적어 둔 목록이다
 * (`GET /users/me/fare-splits?status=NOT_SENT`: 「홈 배너(아직 보내지 않은 택시비)에 쓴다.
 * 항목을 누르면 partyId 로 정산 상세를 연다」).
 *
 * ### 비어 있으면 **아무것도 그리지 않는다** ★
 * 「보낼 택시비가 없어요」라는 빈 상태를 두지 않는다. 이 배너가 서는 자리는 홈 **지도 위**이고,
 * 평상시(보낼 돈이 없는 날)가 압도적으로 많다 — 그때마다 한 칸을 차지하면 **지도를 가리는 상설
 * 안내문**이 된다. 진행 중인 방 배너(`ActiveRideBanner`)가 같은 규칙으로 서 있다.
 *
 * ### 조회 실패도 **아무것도 그리지 않는다**
 * 배너에 「불러오지 못했어요」를 띄우지 않는다. 사용자가 그 자리에서 할 수 있는 일이 없고
 * (재시도 버튼은 배너를 두 줄로 만든다), 보낼 돈이 실제로 있으면 **다음 복귀에서 다시 나타난다**
 * ([FareSplitBannerViewModel.onScreenStart]). 34 기록이 반대로 하는 것(실패를 말한다)과 어긋나 보이지만
 * 사정이 다르다 — 그쪽은 **사용자가 보러 간 화면**이라 빈 목록이 「기록이 사라졌다」로 읽히고,
 * 이쪽은 **불러 주지 않은 알림**이라 없을 때가 정상이다.
 *
 * ### 「받는 사람」 배너는 이 목록으로 만들 수 없다
 * 서버가 **내가 보내는 사람인 정산만** 준다. 방장의 「정산을 시작하세요」 배너를 이 목록으로 만들면
 * **영구히 비어 있다**([com.moyeota.domain.repository.FareSplitRepository.getMyFareSplits] KDoc).
 * 그 진입점은 끝난 방 상세·탑승 기록 쪽이다.
 */
class FareSplitBannerViewModel(private val repository: FareSplitRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<MyFareSplit>>(emptyList())
    val items: StateFlow<List<MyFareSplit>> = _items.asStateFlow()

    init {
        refresh()
    }

    /**
     * 홈이 보일 때마다 다시 읽는다 — **이 목록을 갱신하는 다른 수단이 없다.**
     *
     * 「보냈어요」가 성공하면 그 방은 `NOT_SENT` 목록에서 빠져야 하는데, 그 사실을 알려 주는
     * 신호(SSE·푸시)가 없다. 39 에서 보낸 뒤 홈으로 돌아오면 여기서 다시 읽혀 배너가 줄어든다.
     * 주기 폴링을 두지 않는 이유: 홈은 이미 지도·즐겨찾기로 요청이 많고, 이 목록은 **사용자가
     * 움직였을 때만** 바뀐다(남이 바꿀 수 있는 값이 아니다).
     */
    fun onScreenStart() = refresh()

    /**
     * 실패를 **삼킨다**. 들고 있던 목록을 지우지도 않는다 — 한 번 실패했다고 보낼 돈이 사라진 것은
     * 아니고, 배너가 깜빡이며 사라졌다 나타나면 홈이 불안해 보인다(17 탐색의 `markStale` 과 같은 결의
     * 판단이지만, 배너에는 「낡음」을 말할 자리가 없어 그냥 유지한다).
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                _items.value = repository.getMyFareSplits(TransferStatus.NOT_SENT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 아무것도 하지 않는다 — 위 KDoc 참고. 로그도 남기지 않는다(계좌·금액이 섞인 응답이다).
            }
        }
    }

    companion object {
        fun factory(repository: FareSplitRepository) = viewModelFactory {
            initializer { FareSplitBannerViewModel(repository) }
        }
    }
}

/**
 * 홈에 **꽂는 한 줄** — ViewModel 배선과 복귀 재조회까지 안에서 끝낸다.
 *
 * 호출부(14 홈)가 저장소만 넘기면 되도록 만든 이유: 홈은 이미 ViewModel 셋(지도·즐겨찾기·진행 중
 * 방)을 들고 있어, 배너용 상태를 그쪽으로 끌어올리면 **홈이 정산을 알아야** 한다. 배너가 비어 있을 때
 * 아무것도 그리지 않는 규칙도 여기 안에 있으므로, 호출부에 `if (items.isNotEmpty())` 가 생기지 않는다.
 *
 * @param onOpen 항목을 눌렀다 — `partyId` 로 39 정산 상세를 연다.
 */
@Composable
fun FareSplitBannerHost(
    repository: FareSplitRepository,
    onOpen: (partyId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FareSplitBannerViewModel = viewModel(factory = FareSplitBannerViewModel.factory(repository))
    val items by viewModel.items.collectAsState()

    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStart()
        onStopOrDispose { }
    }

    FareSplitBanner(items = items, onItemClick = onOpen, modifier = modifier)
}

/**
 * 배너 본체(스테이트리스). **[items] 가 비면 아무것도 그리지 않는다** — Composable 자체가 빈 람다가
 * 되므로 바깥의 `Column` 간격도 생기지 않는다.
 */
@Composable
fun FareSplitBanner(
    items: List<MyFareSplit>,
    onItemClick: (partyId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = MoyeotaColor.Primary500.copy(alpha = 0.16f))
            .clip(RoundedCornerShape(18.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "아직 보내지 않은 택시비",
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
                modifier = Modifier.weight(1f),
            )
            // 건수를 적는다 — 세 건이 쌓였을 때 한 건만 보내고 끝난 줄 알기 쉽다.
            Text(text = "${items.size}건", style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
        }

        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MoyeotaColor.Hairline)
            FareSplitBannerRow(item = item, onClick = { onItemClick(item.partyId) })
        }
    }
}

/**
 * 한 줄: 금액이 가장 크고, 받는 사람과 경로가 그 아래 보조로 붙는다.
 *
 * 금액을 앞세우는 이유: 이 배너를 누르는 판단은 「얼마를 보내야 하나」이고, 경로는 **어느 날의
 * 탑승인지 떠올리는** 보조 정보다. 경로가 없는 줄도 있으므로(방이 지워진 뒤에도 정산은 남는다)
 * 경로를 앞세우면 **어떤 줄은 머리가 비어 보인다**([myFareSplitRouteLabel]).
 *
 * 마감을 적지 않는다 — 만료 경계가 「1일 + 최대 30분」이라 배너 한 줄로 약속할 수 있는 값이 아니다
 * ([EXPIRY_NOTICE]). 그 안내는 상세 화면이 한다.
 */
@Composable
private fun FareSplitBannerRow(item: MyFareSplit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fareWonLabel(item.myAmount),
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.payer.displayName}님에게",
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            myFareSplitRouteLabel(item)?.let { route ->
                Text(
                    text = route,
                    style = MoyeotaType.CaptionMd,
                    color = MoyeotaColor.TextAsh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(text = "보내기 ›", style = MoyeotaType.ButtonMd, color = MoyeotaColor.Primary600)
    }
}

// ─────────────────────────── 프리뷰 ───────────────────────────

@Preview(showBackground = true)
@Composable
private fun FareSplitBannerPreview() {
    FareSplitBanner(
        items = listOf(
            MyFareSplit(
                partyId = 42L,
                departure = "센텀시티역 3번 출구",
                destination = "서면역 1번 출구",
                payer = FareSplitParticipant("p-1", "모여타방장"),
                myAmount = 6_233,
                status = TransferStatus.NOT_SENT,
                createdAt = "2026-11-12T00:00:00Z",
            ),
            // 방이 지워진 정산 — 경로 줄이 **없는 것이 정상**이다
            MyFareSplit(
                partyId = 41L,
                departure = null,
                destination = null,
                payer = FareSplitParticipant("p-2", "부산가자"),
                myAmount = 4_100,
                status = TransferStatus.NOT_SENT,
                createdAt = "2026-11-11T00:00:00Z",
            ),
        ),
        onItemClick = {},
        modifier = Modifier.padding(16.dp),
    )
}

/** 보낼 돈이 없는 날 — **아무것도 그리지 않는다**(프리뷰가 빈 화면인 것이 정상이다). */
@Preview(showBackground = true)
@Composable
private fun FareSplitBannerEmptyPreview() {
    FareSplitBanner(items = emptyList(), onItemClick = {})
}
