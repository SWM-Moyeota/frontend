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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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

// 16 에서 고른 매칭 조건. 반경은 m 단위(서버 OpenPartyRequest 와 같은 단위).
//
// 「동성만」은 조건이 아니다 — 모여타는 같은 성별끼리만 매칭되는 서비스라 항상 참이고,
// 서버 요청에도 실리지 않는다. 사용자가 끌 수 있는 것처럼 보이는 토글은 두지 않는다.
data class MatchConditions(
    val capacity: Int,
    val departureRadiusMeters: Int,
    val destinationRadiusMeters: Int,
)

/**
 * 칩 라벨 → m.
 *
 * 칩 값은 **서버 검증 범위(100~500m)와 같은 집합**이다. 예전에는 500m/1km/2km 를 보여 주고
 * 호출부가 1km·2km 를 조용히 500m 로 깎았는데(QA D-2), 사용자가 고른 값이 아무 표시 없이
 * 버려지는 셈이었다. UI 가 서버 계약보다 넓은 선택지를 내지 않는 쪽으로 맞춘다.
 */
private fun radiusLabelToMeters(label: String): Int = when (label) {
    "100m" -> 100
    "300m" -> 300
    else -> 500
}

/** 반경 칩 목록·기본값. 기본 300m 는 가운데 값이자 서버 기본 동작에 가장 가깝다. */
private val RadiusOptions = listOf("100m", "300m", "500m")
private const val DefaultRadiusLabel = "300m"

/**
 * 16 · 도착지 확인 · 매칭 조건 (모달) [신규]
 *
 * 이동(디스크립션):
 * - 닫기·배경 탭 → 15 (onDismiss) — 공통 규칙: 모달은 배경 탭·닫기로만 종료
 * - 「같이 탈 사람 찾기」 → 방 생성(POST /matching/rooms) 성공 시 21 매칭 대기 (onFindCompanions)
 *   선택한 매칭 조건(인원·반경)을 그대로 넘긴다. 반경은 m 단위.
 *
 * 선택 규칙(디스크립션):
 * - 인원 1인/2인/3인 → 예상 1인 요금 재계산 ([RoutePreviewUi.estimatedFare] ÷ 인원, 10원 단위 반올림)
 * - 출발지·도착지 반경 100m/300m/500m 각 1개 필수 선택, 기본값 3인 / 300m
 *   (서버 검증 범위와 같은 집합이라 고른 값이 그대로 POST 본문에 실린다 — QA D-2)
 * - 매칭 방식은 「주요 승차지점」 하나뿐 — 선택된 상태로 고정한다
 *
 * 레이아웃(디스크립션):
 * - 지도가 헤더 아래를 전부 채우고, 그 위에 **드래그로 접었다 펴는 시트**가 얹힌다
 * - 펼침 = 매칭 조건까지 전부 보임(지도 160dp 노출) / 접힘 = 도착지·요금·CTA 만 보이고 지도가 넓어짐
 * - CTA 는 시트 하단에 고정이라 두 상태 모두에서 보인다
 */
@Composable
fun DestinationConfirmModal(
    destinationName: String = "서면역 1번 출구",
    destinationAddress: String = "부산진구 부전동",
    originStopName: String = "부산대학교 정문 버스정류장",
    departureTimeLabel: String = "지금",
    arrivalTimeLabel: String = "도착 계산 중",
    walkLabel: String = "도보 2분 · 180m",
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
) {
    // 기본값: 3인 / 300m (디스크립션 유효값 규칙)
    var peopleCount by rememberSaveable { mutableIntStateOf(3) }
    var originRadius by rememberSaveable { mutableStateOf(DefaultRadiusLabel) }
    var destinationRadius by rememberSaveable { mutableStateOf(DefaultRadiusLabel) }
    // 핀 조정 화면(모달 내 확장 상태). null 이면 닫혀 있다.
    var adjustTarget by remember { mutableStateOf<PinTarget?>(null) }
    val canAdjust = destinationPosition != null || originPosition != null

    // 1인 요금 = 미터기 추정 총액 ÷ 인원, 10원 단위 반올림 (25/32 요금 화면과 같은 컨벤션).
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
                                Spacer(Modifier.size(13.dp))
                                Text(
                                    text = walkLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GrayMute,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
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
                            HorizontalDivider(color = MoyeotaColor.Hairline)
                            ConditionRow(label = "출발지 반경") {
                                RadiusOptions.forEach { radius ->
                                    ConditionChip(
                                        text = radius,
                                        selected = originRadius == radius,
                                        onClick = { originRadius = radius },
                                    )
                                }
                            }
                            HorizontalDivider(color = MoyeotaColor.Hairline)
                            ConditionRow(label = "도착지 반경") {
                                RadiusOptions.forEach { radius ->
                                    ConditionChip(
                                        text = radius,
                                        selected = destinationRadius == radius,
                                        onClick = { destinationRadius = radius },
                                    )
                                }
                            }
                            HorizontalDivider(color = MoyeotaColor.Hairline)
                            ConditionRow(label = "매칭 방식") {
                                ConditionChip(
                                    text = "주요 승차지점",
                                    // 단일 방식이라 항상 선택된 상태다 — 고를 게 없으니 탭도 무동작
                                    selected = true,
                                    onClick = {},
                                    wide = true,
                                )
                            }
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
                            onFindCompanions(
                                MatchConditions(
                                    capacity = peopleCount,
                                    departureRadiusMeters = radiusLabelToMeters(originRadius),
                                    destinationRadiusMeters = radiusLabelToMeters(destinationRadius),
                                ),
                            )
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
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            .padding(horizontal = if (wide) 28.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
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
    DestinationConfirmModal()
}
