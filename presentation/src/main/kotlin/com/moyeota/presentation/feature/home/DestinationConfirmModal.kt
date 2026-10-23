package com.moyeota.presentation.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.domain.model.MatchCandidate
import com.moyeota.core.designsystem.component.BackArrowIcon
import com.moyeota.core.designsystem.component.MapPlaceholder
import com.moyeota.core.designsystem.component.MapSheetScaffold
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.component.fitMapCamera
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.naver.maps.geometry.LatLng
import kotlin.math.roundToInt

private val ChipBlueBg = Color(0xFFF1F5FD)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)

/**
 * 16 지도·요약 칩·요금 문구에 쓰이는 경로 미리보기 값 (POST /matching/routes 응답).
 *
 * 방을 만들기 **전**이라 서버가 확정한 값이 아니다 — 좌표만으로 뽑은 추정치이며,
 * 실패해도 방 생성은 막지 않는다(서버가 생성 시 어차피 다시 계산한다).
 * 셋 다 null 이고 [loading] 이 false 면 「값 없음」 — 칩·요금 문구를 감춘다.
 */
data class RoutePreviewUi(
    val loading: Boolean = false,
    val estimatedMinutes: Int? = null,
    val estimatedFare: Int? = null, // 미터기 추정 총액(1인 요금 아님)
    val encodedPath: String? = null, // Google Encoded Polyline — 25 배차 화면과 같은 형식
)

/**
 * 16 에서 고른 매칭 조건. 지금은 **정원 하나뿐**이다.
 *
 * 「출발지 반경」·「도착지 반경」 칩은 지웠다(2026-10-14). 서버가 방 생성 요청에서 반경을 빼고
 * 고정값(500m)을 쓰기로 했다(Backend `a668aee`) — 보내도 버려진다. 그전에도 어떤 조회·판정도
 * 그 값을 읽지 않아(합류에 거리 검사가 없고 목록은 지도 뷰포트만 본다) 사용자는 **아무 영향 없는
 * 값**을 「매칭 조건」이라는 이름으로 고르고 있었다. 「준비 중」 자리표시를 두지 않고 요소째 내렸다.
 *
 * 「동성만」 토글도 없다 — 성별은 매칭에 쓰이지 않는다(백엔드 `matching` 패키지에 성별 필드 0건).
 * 고를 수는 있는데 아무 일도 일어나지 않는 조건은 두지 않는다.
 */
data class MatchConditions(
    val capacity: Int,
)

/**
 * 16 · 도착지 확인 · 매칭 조건 (모달) [신규]
 *
 * 이동(디스크립션):
 * - 닫기·배경 탭 → 15 (onDismiss) — 공통 규칙: 모달은 배경 탭·닫기로만 종료
 * - 「같이 탈 사람 찾기」 → 방 생성(POST /matching/rooms) 성공 시 21 매칭 대기 (onFindCompanions)
 *   선택한 매칭 조건(인원)을 그대로 넘긴다.
 *
 * 선택 규칙(디스크립션):
 * - 인원 1인/2인/3인 → 예상 1인 요금 재계산 ([RoutePreviewUi.estimatedFare] ÷ 인원, 10원 단위 반올림)
 *   기본값 3인. **고를 수 있는 조건은 이것뿐이다** — 반경 칩과 「매칭 방식」 행은 지웠다
 *   ([MatchConditions] KDoc: 서버가 반경을 고정값으로 정하고, 승차지점 개념 자체가 없다)
 *
 * 레이아웃(디스크립션):
 * - 지도가 헤더 아래를 전부 채우고, 그 위에 **드래그로 접었다 펴는 시트**가 얹힌다
 * - 펼침 = 매칭 조건까지 전부 보임(지도 160dp 노출) / 접힘 = 도착지·요금·CTA 만 보이고 지도가 넓어짐
 * - CTA 는 시트 하단에 고정이라 두 상태 모두에서 보인다
 */
@Composable
fun DestinationConfirmModal(
    destinationName: String,
    destinationAddress: String,
    originStopName: String,
    departureTimeLabel: String = "지금",
    arrivalTimeLabel: String = "도착 계산 중",
    // 이름 자리에는 검색 장소명 대신 **역지오코딩 실주소**가 들어올 수 있다(핀 조정·GPS 출발지).
    // 「부산광역시 금정구 부산대학로63번길 2 부산대학교」처럼 길어지므로 표기는 전부 1줄 말줄임이다 —
    // 시/도 접두 제거 같은 가공은 하지 않는다(서버가 조합해 준 값을 그대로 보여준다).
    // 지도 좌표 — 도착지가 있으면 실지도, 없으면 placeholder (15 를 거치지 않은 진입)
    destinationPosition: LatLng? = null,
    originPosition: LatLng? = null,
    routePreview: RoutePreviewUi = RoutePreviewUi(),
    creating: Boolean = false, // 방 생성 요청 중 — 중복 제출 차단
    errorMessage: String? = null,
    onDismiss: () -> Unit = {},
    // 핀 조정 확정 — (출발지, 도착지) 좌표. 조정하지 않은 쪽은 들어온 값 그대로 돌아온다
    onAdjustPositions: (LatLng?, LatLng?) -> Unit = { _, _ -> },
    onFindCompanions: (MatchConditions) -> Unit = {},
    /**
     * 「걸어서 가까운 방」 추천. **빈 목록이 기본값이자 기본 상태**다 — 서버가 출발지·도착지
     * 각각 300m 안(AND)에서만 고르기 때문에 초기에는 거의 항상 비어 있다. 비면 섹션을 그리지 않는다.
     *
     * 서버가 도보 시간 순으로 정렬해 준 순서 그대로 받는다 — **여기서 다시 정렬하지 않는다.**
     */
    nearbyCandidates: List<MatchCandidate> = emptyList(),
    /** 추천 카드를 누르면 그 방의 합류 확인(19)으로 간다 — 합류 흐름은 이미 있는 걸 그대로 쓴다. */
    onCandidateClick: (MatchCandidate) -> Unit = {},
) {
    // 기본값: 3인 (디스크립션 유효값 규칙)
    var peopleCount by rememberSaveable { mutableIntStateOf(3) }
    // 핀 조정 화면(모달 내 확장 상태). null 이면 닫혀 있다.
    var adjustTarget by remember { mutableStateOf<PinTarget?>(null) }
    val canAdjust = destinationPosition != null || originPosition != null

    // 1인 요금 = 미터기 추정 총액 ÷ 인원, 10원 단위 반올림 (25 요금 화면과 같은 컨벤션).
    // 추정이 아직 없거나 실패했으면 null — 문구를 감추되 CTA 는 살려둔다.
    val farePerPersonText = routePreview.estimatedFare?.let { total ->
        val perPerson = ((total.toDouble() / peopleCount) / 10.0).roundToInt() * 10
        "%,d".format(perPerson)
    }
    // 요약 칩 — 거리는 서버 응답에 없어 시간·총요금으로 대체한다(와이어프레임의 2값 구조 유지)
    val routeSummaryLabel = when {
        routePreview.loading -> "예상 경로 계산 중"
        routePreview.estimatedMinutes != null && routePreview.estimatedFare != null ->
            "예상 ${routePreview.estimatedMinutes}분 · 총 ${"%,d".format(routePreview.estimatedFare)}원"
        else -> null // 실패 — 칩 자체를 감춘다
    }
    // 폴리라인 디코딩은 25 배차 화면과 같은 decodePolyline 을 쓴다
    val routePath = remember(routePreview.encodedPath) {
        routePreview.encodedPath?.let(::decodePolyline).orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim 배경 — 탭 시 닫기
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MoyeotaColor.Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        // 모달 카드 (풀스크린 라우트 겸용) — 내부 탭은 소비
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            // 상단 헤더
            Column(modifier = Modifier.fillMaxWidth().background(MoyeotaColor.SurfaceCanvas)) {
                StatusBarSpacer()
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        BackArrowIcon()
                    }
                    Text(
                        text = "도착지 확인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    ShieldIcon()
                }
            }

            // 지도(헤더 아래 전부) 위에 드래그 시트를 얹는다 — 시트 메커니즘은 21 과 공유한다
            MapSheetScaffold(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // 접힘 = 핸들 + 도착지 배지 + 요금 문구 + CTA (기본값과 같은 구성)
                collapsedSheetHeight = 176.dp,
                background = { sheet ->
                    val widthDp = sheet.containerWidth.value
                    // fit 기준은 **시트에 가리지 않고 실제로 보이는 지도 높이**다.
                    // 앵커가 바뀔 때만 값이 변하므로 드래그·정착 중 카메라가 매 프레임 튀지 않는다.
                    val visibleMapHeightDp = sheet.visibleMapHeight.value
                    val fitted = remember(routePath, originPosition, destinationPosition, widthDp, visibleMapHeightDp) {
                        fitMapCamera(
                            points = routePath + listOfNotNull(originPosition, destinationPosition),
                            widthDp = widthDp,
                            heightDp = visibleMapHeightDp,
                        )
                    }
                    // 가려지는 영역을 지도에 알려 카메라 중심이 보이는 영역 기준으로 잡히게 한다
                    val mapContentPadding = remember(sheet.settledSheetHeight) {
                        PaddingValues(bottom = sheet.settledSheetHeight)
                    }
                    // 「위치 조정」 버튼은 **실측** 시트 높이를 써서 시트 바로 위에 붙인다 —
                    // 앵커 추정치를 쓰면 에러 배너가 떠 시트가 커졌을 때 버튼이 가린다.
                    val sheetHeight = sheet.sheetHeight

                    if (destinationPosition != null) {
                        RouteMapView(
                            modifier = Modifier.fillMaxSize(),
                            routePath = routePath, // previewRoute 의 encodedPath — 2점 미만이면 그려지지 않는다
                            originPosition = originPosition,
                            destinationPosition = destinationPosition,
                            center = fitted?.first ?: destinationPosition,
                            // 경로가 없을 땐 도착지 주변 블록이 식별될 정도로 당겨 본다
                            zoom = fitted?.second ?: 15.0,
                            contentPadding = mapContentPadding,
                            // 다이얼로그 목적지라 SurfaceView 대신 TextureView 로 그린다
                            useTextureView = true,
                        )
                    } else {
                        MapPlaceholder(modifier = Modifier.fillMaxSize())
                    }

                    // 지도를 덮는 투명 탭 레이어는 두지 않는다 — 그 레이어가 터치를 전부 먹어
                    // SDK 줌 컨트롤(+/-)·핀치·팬이 통째로 죽어 있었다. 핀 조정 진입은
                    // 아래 「위치 조정」 버튼 하나로 충분하다.

                    // 경로 미리보기 실패 시엔 칩을 통째로 감춘다 — 틀린 값보다 없는 편이 낫다
                    if (routeSummaryLabel != null) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .height(30.dp)
                                .shadow(3.dp, RoundedCornerShape(15.dp), spotColor = Color(0x1A1B2A4A))
                                .clip(RoundedCornerShape(15.dp))
                                .background(MoyeotaColor.SurfaceCanvas)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = routeSummaryLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.InkPrimary,
                            )
                        }
                    }

                    // 지도 조정 진입점. 오른쪽 아래는 SDK 줌 컨트롤(+/-) 자리라 왼쪽에 둔다.
                    if (canAdjust) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = sheetHeight + 12.dp)
                                .height(30.dp)
                                .shadow(3.dp, RoundedCornerShape(15.dp), spotColor = Color(0x1A1B2A4A))
                                .clip(RoundedCornerShape(15.dp))
                                .background(MoyeotaColor.SurfaceCanvas)
                                .clickable { adjustTarget = PinTarget.DESTINATION }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "위치 조정",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MoyeotaColor.Primary500,
                            )
                        }
                    }
                },
                sheetTop = {
                    // 도착지 배지 — 접힌 상태에서도 보이는 최소 정보
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge(kind = NoticeKind.SUCCESS, text = "도착지")
                        Spacer(Modifier.size(10.dp))
                        Text(
                            // 이름이 이미 실주소면 호출부가 주소 칸을 비워 보낸다 — 구분자만 남지 않게 합친다
                            text = listOf(destinationName, destinationAddress)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                sheetDetail = {
                    // 상세 영역은 시트가 접히면 높이 0 이 된다 — 좌우 여백은 여기서 준다
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        // 경로 카드
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ChipBlueBg)
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(11.dp).background(MoyeotaColor.Primary500, CircleShape))
                                Spacer(Modifier.size(13.dp))
                                Text(
                                    text = originStopName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.InkPrimary,
                                    // 실주소가 들어오면 시각·「조정」과 같은 줄에 못 담긴다 — 줄바꿈 대신 말줄임
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                // 이름이 남는 폭을 전부 쓰므로(weight) 시각과 붙지 않게 최소 간격을 둔다
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = departureTimeLabel,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.InkPrimary,
                                )
                                // 무엇을 조정하는지 애매하지 않도록 출발지·도착지 각각에 진입점을 둔다
                                if (originPosition != null) {
                                    AdjustLink(onClick = { adjustTarget = PinTarget.ORIGIN })
                                }
                            }
                            Row {
                                Box(modifier = Modifier.size(width = 11.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                                    DashedVerticalLine()
                                }
                                // 출발지 → 도착지 연결선만 그린다. 도보 거리·시간은 서버가 주지 않아 표기하지 않는다.
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(11.dp).background(MoyeotaColor.MarkerDestination, CircleShape))
                                Spacer(Modifier.size(13.dp))
                                Text(
                                    text = destinationName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = arrivalTimeLabel,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.InkPrimary,
                                )
                                if (destinationPosition != null) {
                                    AdjustLink(onClick = { adjustTarget = PinTarget.DESTINATION })
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "매칭 조건",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayMute,
                        )
                        Spacer(Modifier.height(10.dp))

                        // 매칭 조건 카드
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
                                .clip(RoundedCornerShape(18.dp))
                                .background(MoyeotaColor.SurfaceCanvas)
                                .padding(horizontal = 20.dp),
                        ) {
                            ConditionRow(label = "인원") {
                                listOf(1, 2, 3).forEach { count ->
                                    ConditionChip(
                                        text = "${count}인",
                                        selected = peopleCount == count,
                                        onClick = { peopleCount = count },
                                    )
                                }
                            }
                            // 「출발지 반경」·「도착지 반경」 행은 지웠다(2026-10-14) — 서버가 방 생성
                            // 요청에서 반경을 빼고 고정값(500m)을 쓴다(Backend `a668aee`). 고른 값이
                            // 전송조차 되지 않고, 전송됐을 때도 매칭이 읽지 않던 값이다([MatchConditions]).
                            //
                            // 「매칭 방식 · 주요 승차지점」 행은 지웠다(2026-10-13) — 그런 매칭 방식이
                            // 없다. 서버에 승차지점 개념이 없고(방은 사용자가 찍은 좌표 그대로 만들어진다)
                            // 칩도 항상 선택된 채 무동작이었다. 고를 것도, 가리킬 기능도 없는 줄이었다.
                            //
                            // 남은 행이 「인원」 하나라 구분선도 없다 — 카드는 그대로 둔다. 실제 매칭
                            // 조건이 하나뿐이라는 사실을 그대로 보여 주는 모양이다.
                        }

                        // 「걸어서 가까운 방」 — 후보가 있을 때만 그린다. 없는 게 기본 상태라
                        // (서버가 출발지·도착지 **각각** 300m 안에서만 고른다) 「없음」을 강조하면
                        // 방을 만들러 온 사람에게 실패처럼 읽힌다.
                        if (nearbyCandidates.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            NearbyCandidateSection(
                                candidates = nearbyCandidates,
                                onCandidateClick = onCandidateClick,
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                    }
                },
                sheetFooter = {
                    // 요금 안내 + CTA — 접힘·펼침 모두에서 보이도록 시트 하단에 고정한다.
                    // 추정 실패 시에도 CTA 는 막지 않는다 — 방 생성 요청을 받으면 서버가 요금을 다시 계산한다.
                    Text(
                        text = when {
                            routePreview.loading -> "예상 요금 계산 중이에요"
                            // 뒤에 붙던 설명 문구(「인원이 확정되면…」)는 뺐다 — 한 줄 안내는 금액만으로 충분하다
                            farePerPersonText != null -> "예상 요금 1인 ${farePerPersonText}원"
                            else -> "예상 요금 —"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                        // 접힘 높이가 문구 길이에 따라 흔들리지 않게 한 줄로 고정한다
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp),
                    )
                    if (errorMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        NoticeBanner(
                            kind = NoticeKind.ERROR,
                            text = errorMessage,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryCtaButton(
                        text = "같이 탈 사람 찾기",
                        onClick = {
                            onFindCompanions(MatchConditions(capacity = peopleCount))
                        },
                        loading = creating,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    // dialog() 윈도우라 인셋은 0 — CTA 아래 여백만 남는다
                    Spacer(Modifier.height(20.dp))
                    NavigationBarSpacer()
                },
            )
        }

        // 핀 조정 — 같은 다이얼로그 윈도우의 최상단 레이어로 덮는다(신규 목적지 아님)
        adjustTarget?.let { target ->
            PinAdjustOverlay(
                originName = originStopName,
                destinationName = destinationName,
                originPosition = originPosition,
                destinationPosition = destinationPosition,
                initialTarget = target,
                onCancel = { adjustTarget = null },
                onConfirm = { adjustedOrigin, adjustedDestination ->
                    adjustTarget = null
                    onAdjustPositions(adjustedOrigin, adjustedDestination)
                },
            )
        }
    }
}

// 경로 카드 행에 붙는 좌표 조정 진입점
@Composable
private fun AdjustLink(onClick: () -> Unit) {
    Text(
        text = "조정",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MoyeotaColor.Primary500,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * 「걸어서 가까운 방」 추천 목록. **후보가 있을 때만** 불린다(빈 목록은 호출부가 거른다).
 *
 * 기존 17 탐색 카드보다 **정보가 적다** — 서버 응답에 인원·요금이 없어서다. 그래서 숫자를
 * 지어내 채우지 않고, 가진 것(출발·도착·도보 시간·정원)만 보여 준다. 인원 현황은 탭해서 들어간
 * 합류 확인(19)이 방 상세를 읽어 채운다.
 */
@Composable
private fun NearbyCandidateSection(
    candidates: List<MatchCandidate>,
    onCandidateClick: (MatchCandidate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "걸어서 가까운 방",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            // 「N분 소요」라고 쓰면 목적지까지 N분으로 읽힌다 — 걷는 시간이라는 걸 못 박는다.
            text = "타러 가고, 내려서 걷는 시간을 더한 값이에요",
            fontSize = 12.sp,
            color = GrayAsh,
        )
        Spacer(Modifier.height(10.dp))
        candidates.forEach { candidate ->
            NearbyCandidateCard(candidate = candidate, onClick = { onCandidateClick(candidate) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NearbyCandidateCard(candidate: MatchCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = Color(0x1A1B2A4A))
            .clip(RoundedCornerShape(14.dp))
            .background(MoyeotaColor.SurfaceCanvas)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${candidate.ride.origin} → ${candidate.ride.destination}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val headcount = headcountLabel(joined = candidate.ride.members.size, capacity = candidate.ride.capacity)
            if (headcount.isNotEmpty()) {
                Text(
                    text = headcount,
                    fontSize = 12.sp,
                    color = GrayAsh,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = walkMinutesLabel(candidate.walkSeconds),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.Primary500,
            maxLines = 1,
        )
    }
}

/**
 * 추천 카드의 인원 문구 — 17 탐색 카드와 같은 「2/3명」 모양으로 맞춘다.
 *
 * 이 숫자가 카드에서 가장 중요한 정보다. **2/3 방은 내가 들어가면 바로 출발하고 1/3 방은 한 명을
 * 더 기다려야 한다** — 걸어서 3분이 1/3 이고 6분이 2/3 이면 6분 쪽을 골라야 하는 상황인데,
 * 정원만 보여 주면 두 방이 똑같아 보인다.
 *
 * [joined] 는 [com.moyeota.domain.model.Ride.members] 의 크기이고 **0 은 「빈 방」이 아니라
 * 「모른다」**는 뜻이다(서버가 `currentMembers` 를 안 주는 옛 배포). 후보는 모집 중인 방만 오고
 * 거기엔 최소 방장 1명이 있으므로 0 은 나올 수 없는 값이다. 모를 때는 지어내지 않고 정원만 쓴다.
 *
 * [capacity] 까지 0 이면 **빈 문자열을 돌려주고 호출부가 줄 자체를 지운다** — 「최대 0명」은
 * 사용자에게 아무 뜻도 없다.
 */
internal fun headcountLabel(joined: Int, capacity: Int): String = when {
    capacity <= 0 -> ""
    joined <= 0 -> "최대 ${capacity}명"
    else -> "$joined/${capacity}명"
}

/**
 * 도보 시간(초) → 표시 문구. **「걸어서」를 반드시 붙인다** — 서버가 주는 값은 탑승지까지 걷는
 * 시간과 내려서 걷는 시간의 **합**이고 차 타는 시간은 안 들어 있다. 「7분」만 쓰면 목적지까지
 * 7분으로 읽힌다.
 *
 * 1분 미만도 「걸어서 1분」으로 올린다 — 「0분」은 걸을 필요가 없다는 뜻으로 읽히는데 그런 경우가
 * 아니고(좌표가 다르니 거리는 있다), 반올림해 0 이 되는 값일 뿐이다.
 */
internal fun walkMinutesLabel(walkSeconds: Int): String {
    val minutes = ((walkSeconds + 59) / 60).coerceAtLeast(1)
    return "걸어서 ${minutes}분"
}

@Composable
private fun ConditionRow(label: String, chips: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
        )
        Spacer(Modifier.width(12.dp))
        // 칩 행이 **남는 폭 안에서** 오른쪽 정렬된다. 예전엔 라벨 뒤 Spacer(weight) + 고정폭 칩이라
        // 작은 폰(360dp)에서 칩 3개가 카드 밖으로 밀려 「500m」이 잘리고 선택 칩의 파란 배경이 카드
        // 모서리를 넘어 보였다(실기 QA). 칩 자체도 좁아졌다(ConditionChip 패딩 20 → 14dp).
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
        }
    }
}

// 와이어프레임 스타일 칩 — 선택 시 Primary 솔리드, 미선택 시 흰색 + 그림자
// (핀 조정 화면의 출발지/도착지 토글도 같은 칩을 쓴다 — 모듈 내부라 internal)
@Composable
internal fun ConditionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    /** 넓은 패딩 — 핀 조정 화면(16a)의 출발지/도착지 토글이 쓴다 */
    wide: Boolean = false,
) {
    val bg = if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas
    val fg = if (selected) MoyeotaColor.TextOnDark else GraySlate
    Box(
        modifier = Modifier
            .height(32.dp)
            .shadow(if (selected) 0.dp else 3.dp, RoundedCornerShape(18.dp), spotColor = Color(0x1A1B2A4A))
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = if (wide) 28.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DashedVerticalLine(modifier: Modifier = Modifier, color: Color = Color(0xFFB9C6DE)) {
    Canvas(modifier = modifier.size(width = 3.dp, height = 30.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun ShieldIcon(modifier: Modifier = Modifier, color: Color = GrayDeep) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.85f, h * 0.22f)
            quadraticTo(w * 0.85f, h * 0.62f, w * 0.5f, h * 0.92f)
            quadraticTo(w * 0.15f, h * 0.62f, w * 0.15f, h * 0.22f)
            close()
        }
        drawPath(path, color, style = Stroke(strokeWidth, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.35f, h * 0.48f), Offset(w * 0.46f, h * 0.6f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.46f, h * 0.6f), Offset(w * 0.66f, h * 0.36f), strokeWidth, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DestinationConfirmModalPreview() {
    DestinationConfirmModal(
        destinationName = "도착지",
        destinationAddress = "",
        originStopName = "출발지",
    )
}
