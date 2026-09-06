package com.moyeota.presentation.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
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
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.RouteMapView
import com.moyeota.core.designsystem.component.SheetHandle
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.decodePolyline
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.naver.maps.geometry.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin

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
data class MatchConditions(
    val capacity: Int,
    val departureRadiusMeters: Int,
    val destinationRadiusMeters: Int,
    val sameGenderOnly: Boolean,
)

// 칩 라벨 → m. 서버 검증은 100~500m 라 1km·2km 는 그대로 보낼 수 없다(호출부에서 clamp).
private fun radiusLabelToMeters(label: String): Int = when (label) {
    "500m" -> 500
    "1km" -> 1_000
    else -> 2_000
}

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
 * - 출발지·도착지 반경 500m/1km/2km 각 1개 필수 선택, 기본값 3인 / 1km
 * - 매칭 방식 「주요 승차지점」 선택
 * - 「동성만」 토글 — 본인 인증 완료 계정만 사용 가능(미인증 시 비활성 + 안내)
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
    sameGenderAvailable: Boolean = true, // 09 본인 인증 완료 여부
    creating: Boolean = false, // 방 생성 요청 중 — 중복 제출 차단
    errorMessage: String? = null,
    onDismiss: () -> Unit = {},
    // 핀 조정 확정 — (출발지, 도착지) 좌표. 조정하지 않은 쪽은 들어온 값 그대로 돌아온다
    onAdjustPositions: (LatLng?, LatLng?) -> Unit = { _, _ -> },
    onFindCompanions: (MatchConditions) -> Unit = {},
) {
    // 기본값: 3인 / 1km (디스크립션 유효값 규칙)
    var peopleCount by rememberSaveable { mutableIntStateOf(3) }
    var originRadius by rememberSaveable { mutableStateOf("1km") }
    var destinationRadius by rememberSaveable { mutableStateOf("1km") }
    var sameGenderOnly by rememberSaveable { mutableStateOf(false) }
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

            // 지도 영역 + 예상 시간 칩
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val widthDp = maxWidth.value
                val heightDp = maxHeight.value
                // 경로 + 두 마커가 모두 들어오는 카메라. 경로가 아직(또는 끝내) 없으면
                // 마커 2개만으로 맞춘다 — 좌표가 하나뿐이면 null 이 되어 아래 기본값으로 떨어진다.
                val fitted = remember(routePath, originPosition, destinationPosition, widthDp, heightDp) {
                    fitCamera(
                        points = routePath + listOfNotNull(originPosition, destinationPosition),
                        widthDp = widthDp,
                        heightDp = heightDp,
                    )
                }
                if (destinationPosition != null) {
                    RouteMapView(
                        modifier = Modifier.fillMaxSize(),
                        routePath = routePath, // previewRoute 의 encodedPath — 2점 미만이면 그려지지 않는다
                        originPosition = originPosition,
                        destinationPosition = destinationPosition,
                        center = fitted?.first ?: destinationPosition,
                        // 160dp 스트립 — 경로가 없을 땐 도착지 주변 블록이 식별될 정도로 당겨 본다
                        zoom = fitted?.second ?: 15.0,
                        // 다이얼로그 목적지라 SurfaceView 대신 TextureView 로 그린다
                        useTextureView = true,
                    )
                } else {
                    MapPlaceholder(modifier = Modifier.fillMaxSize())
                }
                // 미리보기 지도를 덮는 투명 탭 레이어. MapView 는 AndroidView 라 터치를 스스로
                // 먹으므로 부모의 clickable 로는 탭을 못 받는다. 겸사겸사 160dp 스트립이
                // 제멋대로 팬·줌되는 것도 막는다 — 여기서의 제스처는 「조정 화면 열기」 하나뿐.
                if (canAdjust) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { adjustTarget = PinTarget.DESTINATION },
                    )
                }
                // 경로 미리보기 실패 시엔 칩을 통째로 감춘다 — 틀린 값보다 없는 편이 낫다
                if (routeSummaryLabel != null) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .height(30.dp)
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
                // 미리보기 지도 위 진입점. 지도를 직접 탭해도 같은 화면이 열리지만(160dp 스트립은
                // 팬 대상으로 오해하기 쉽다), 조정이 가능하다는 사실은 눈에 보여야 한다.
                if (canAdjust) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
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
            }

            // 하단 시트
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color(0x141B2A4A))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MoyeotaColor.SurfaceCanvas),
            ) {
                SheetHandle(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(16.dp))

                    // 도착지 배지 + 동성만 토글
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(Modifier.size(10.dp))
                        SameGenderToggle(
                            selected = sameGenderOnly,
                            enabled = sameGenderAvailable,
                            onToggle = { if (sameGenderAvailable) sameGenderOnly = !sameGenderOnly },
                        )
                    }
                    if (!sameGenderAvailable) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "동성만 필터는 본인 인증 후 사용할 수 있어요",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GrayAsh,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

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
                            listOf("500m", "1km", "2km").forEach { radius ->
                                ConditionChip(
                                    text = radius,
                                    selected = originRadius == radius,
                                    onClick = { originRadius = radius },
                                )
                            }
                        }
                        HorizontalDivider(color = MoyeotaColor.Hairline)
                        ConditionRow(label = "도착지 반경") {
                            listOf("500m", "1km", "2km").forEach { radius ->
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
                                selected = false,
                                onClick = {}, // 단일 방식 — 항상 「주요 승차지점」
                                wide = true,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "설정한 출발지 · 도착지 반경 안의 탑승만 18 합승 리스트에 보여요",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GrayAsh,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 요금 안내 + CTA. 추정 실패 시에도 CTA 는 막지 않는다 —
                // 방 생성 요청을 받으면 서버가 어차피 요금을 다시 계산한다.
                Text(
                    text = when {
                        routePreview.loading -> "예상 요금 계산 중이에요"
                        farePerPersonText != null ->
                            "예상 요금 1인 ${farePerPersonText}원 · 인원이 확정되면 요금도 확정돼요"
                        else -> "예상 요금 — · 방을 만들면 요금이 확정돼요"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrayAsh,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
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
                                sameGenderOnly = sameGenderOnly,
                            ),
                        )
                    },
                    loading = creating,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                // dialog() 윈도우라 인셋은 0 — CTA 아래 여백만 남는다
                Spacer(Modifier.height(20.dp))
                NavigationBarSpacer()
            }
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

/**
 * [points] 가 모두 들어오는 카메라(중심·줌)를 구한다. 못 구하면 null.
 *
 * 네이버 지도에도 `CameraUpdate.fitBounds` 가 있지만, 그건 `NaverMap` 인스턴스를 직접 잡아
 * 명령형으로 호출해야 한다 — [RouteMapView] 는 center/zoom 을 선언형으로 받고 사용자의 카메라를
 * 보존하는 구조라, 거기에 명령형 이동을 섞으면 두 주인이 생긴다. 그래서 값만 계산해 넘긴다.
 *
 * 웹 메르카토르 기준 계산: 줌 z 에서 세계 지도 폭이 [WORLD_TILE_DP]·2^z **dp** 이므로,
 * 필요한 폭(높이)을 뷰포트 dp 로 나눈 비율의 로그가 곧 줌이다. 가로·세로 중 더 빡빡한 쪽을 쓴다.
 */
private fun fitCamera(points: List<LatLng>, widthDp: Float, heightDp: Float): Pair<LatLng, Double>? {
    if (points.size < 2 || widthDp <= 0f || heightDp <= 0f) return null

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }

    val minY = mercatorY(minLat)
    val maxY = mercatorY(maxLat)
    // 중심 위도는 메르카토르 y 의 중점을 되돌려 구한다(단순 평균은 화면에서 위아래로 치우친다)
    val center = LatLng(inverseMercatorY((minY + maxY) / 2.0), (minLng + maxLng) / 2.0)

    // 두 점이 사실상 같은 좌표면(핀을 겹쳐 찍은 경우) 폭이 0 이라 줌이 발산한다
    val latFraction = (maxY - minY) / (2 * PI)
    val lngFraction = (maxLng - minLng) / 360.0
    if (latFraction <= 0.0 && lngFraction <= 0.0) return null

    // 좌표는 마커의 **꼭짓점**이라 아이콘 몸통·캡션은 그 위아래로 더 뻗는다.
    // 가장자리에 딱 맞추면 끝점 마커가 잘리므로 사방으로 [MARKER_INSET_DP] 만큼 비워둔다.
    val usableHeight = (heightDp - 2 * MARKER_INSET_DP).coerceAtLeast(1f)
    val usableWidth = (widthDp - 2 * MARKER_INSET_DP).coerceAtLeast(1f)

    val zoomForLat = if (latFraction > 0.0) log2(usableHeight / WORLD_TILE_DP / latFraction) else Double.MAX_VALUE
    val zoomForLng = if (lngFraction > 0.0) log2(usableWidth / WORLD_TILE_DP / lngFraction) else Double.MAX_VALUE
    val zoom = minOf(zoomForLat, zoomForLng).coerceIn(MIN_FIT_ZOOM, MAX_FIT_ZOOM)
    return center to zoom
}

// 마커 아이콘은 좌표(꼭짓점)에서 위로 ~34dp 솟고 캡션은 아래로 ~14dp 내려간다.
// 사방 동일하게 잡으려면 둘 중 큰 쪽에 맞춰야 한다.
private const val MARKER_INSET_DP = 38f

/**
 * 네이버 지도의 줌 1 단계당 세계 지도 폭 (dp).
 *
 * 구글·OSM 계열의 통상값인 256px 이 **아니다** — 네이버는 타일이 두 배 크고 dp 기준이라,
 * 같은 축척을 구글 줌 z+1 로 표현한다. 256 으로 계산하면 두 배 넘게 당겨진 화면이 나온다.
 * 실측 근거: 이 화면에서 `NaverMap.contentBounds` 를 찍어보면 줌 12.11 · 뷰포트 411dp 폭이
 * 경도 0.065° 를 덮는다 → 세계 폭 = 411 / (0.065/360) / 2^12.11 ≈ 512dp.
 */
private const val WORLD_TILE_DP = 512.0
private const val MIN_FIT_ZOOM = 6.0
private const val MAX_FIT_ZOOM = 16.0

private fun mercatorY(latitude: Double): Double {
    val s = sin(latitude * PI / 180.0).coerceIn(-0.9999, 0.9999)
    return ln((1 + s) / (1 - s)) / 2.0
}

private fun inverseMercatorY(y: Double): Double = (2 * atan(exp(y)) - PI / 2) * 180.0 / PI

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

// 동성만 토글 칩
@Composable
private fun SameGenderToggle(
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val bg = if (selected) MoyeotaColor.Primary500 else ChipBlueBg
    val fg = if (selected) MoyeotaColor.TextOnDark else GrayDeep
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "동성만",
            fontSize = 12.sp,
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
