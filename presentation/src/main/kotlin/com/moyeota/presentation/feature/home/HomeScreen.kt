package com.moyeota.presentation.feature.home

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naver.maps.map.NaverMap
import com.naver.maps.map.CameraUpdate
import com.moyeota.presentation.core.location.UserCoordinates
import com.moyeota.core.designsystem.component.latLngOrNull
import com.moyeota.core.designsystem.component.MyLocationOverlay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MapSheetScaffold
import com.moyeota.core.designsystem.component.MoyeotaBottomBar
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.core.designsystem.component.NaverMapView
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Ride
import com.moyeota.presentation.core.ActiveRideBanner
import com.moyeota.presentation.feature.auth.CheckMarkIcon
import java.util.Locale
import kotlin.math.roundToInt

// 와이어프레임 그레이 (core token 미정의 색 — 화면 재현용)
private val CanvasBg = Color(0xFFF5F7FA)
private val HeroBg = Color(0xFFF1F5FD)
private val GraySlate = Color(0xFF4B5563)
private val GrayDeep = Color(0xFF54637D)
private val GrayMute = Color(0xFF8A93A0)
private val GrayAsh = Color(0xFF9AA1AC)
private val RowDot = Color(0xFFC3CCDA)
private val CardShadow = Color(0x1A1B2A4A)

/** 첫 실위치로 옮길 때의 줌 — 동네 단위가 보이는 수준(26 운행 중과 같은 값) */
private const val HomeMyLocationZoom = 15.0

/** 펼친 시트 위로 남기는 지도 높이 — 예전 홈의 「화면 약 30%」 지도 노출과 비슷한 값 */
private val HomeMapRevealHeight = 200.dp

/**
 * 접힘 높이 추정치 = 핸들(36) + HeroSection(인사 18 + 제목·부제 ~70 + 검색 바 64 + 여백 40 ≈ 190).
 * 드래그 앵커·카메라 계산에만 쓰인다(실제 높이는 콘텐츠가 정한다 — MapSheetDefaults 설명 참고).
 */
private val HomeCollapsedSheetHeight = 226.dp

/**
 * 14 · 홈 — 어디로 갈까요 [S09]
 *
 * 이동(디스크립션):
 * - 상단 「{단계} · {목적지} 보기 ›」 배너 → 진행 중인 방의 단계 화면 21/25/26 (onActiveRideClick)
 *   — 진행 중인 방이 없으면([activeRide] null) 배너 자체가 없다
 * - 「목적지 검색」 바 탭 → 15 (onSearchClick)
 * - 「즐겨찾기」 행 탭 → 15, 도로명주소로 검색어 자동 입력 (onFavoritePlaceClick)
 * - 하단탭 합승/채팅/마이 → 17/24/35 (onTabSelect)
 *
 * 상태:
 * - 즐겨찾기는 서버 목록([favoritePlaces])을 sequence 순으로 전부 세로 카드에 그린다.
 *   행 = 이름 · 도로명주소 · 내 위치 기준 직선 거리([myLocation] 이 null 이면 거리 생략)
 * - 비어 있으면 카드 대신 「즐겨찾기를 등록하면…」 안내 문구 (등록은 15 의 ★)
 *
 * 레이아웃: 와이어프레임 B1「풀스크린 지도」 — 네이버 지도가 배경 레이어이고
 * 홈 UI 는 그 위 **드래그 시트**([MapSheetScaffold])다. 앵커가 **셋**이다:
 * - 펼침(기본) — 인사·검색 카드 + 즐겨찾기 목록
 * - 접힘 — 즐겨찾기가 접혀 지도가 검색 카드 위까지 드러난다
 * - **손잡이만**(2026-11, 오너 UX 피드백) — 한 번 더 끌어내리면 시트가 하단탭 뒤로 내려가고 손잡이 줄만
 *   남아 **지도가 전부** 보인다. 예전에는 인사·검색 카드가 늘 떠 있어 지도를 다 볼 길이 없었다.
 *   손잡이를 끌어올리거나 **누르면** 접힘으로 돌아온다(누르기는 끌 수 없는 사용자를 위한 길이다).
 *   검색 진입(「목적지 검색」 바 → 15)은 그대로다 — 손잡이만 남은 동안에는 바가 보이지 않을 뿐이다.
 */
@Composable
fun HomeScreen(
    // 로그인 사용자의 실명. 아직 못 받았거나 서버에 이름이 없으면 null — 인사말에서 이름을 뺀다.
    userName: String? = null,
    /** 지금 진행 중인 내 방. null 이면 지도 위 배너를 그리지 않는다 */
    activeRide: Ride? = null,
    /** 기기 실위치. null(권한 없음·아직 fix 없음)이면 파란 점을 그리지 않고 카메라도 옮기지 않는다 */
    myLocation: UserCoordinates? = null,
    /** 서버 즐겨찾기(GET /users/me/favorite-places). 호출부가 sequence 순으로 정렬해 넘긴다 */
    favoritePlaces: List<FavoritePlace> = emptyList(),
    /** 즐겨찾기 편집 모드. 행 탭이 15 이동 대신 선택 토글이 되고 삭제 손잡이가 나온다 */
    editingFavorites: Boolean = false,
    /** 편집 모드에서 고른 [FavoritePlace.id] 들 */
    selectedFavoriteIds: Set<Long> = emptySet(),
    /** 삭제 요청이 날아간 동안 true — 손잡이를 모두 잠근다(아래 [onDeleteSelected] 주석 참고) */
    deletingFavorites: Boolean = false,
    /** 삭제 실패 문구. 성공하면 null 로 돌아온다 */
    favoriteErrorMessage: String? = null,
    /**
     * 목록 조회가 **한 번이라도 성공**했는가. 「전체 삭제」를 그릴지의 근거다 —
     * 못 받은 상태에서 그리면 서버에 멀쩡히 있는 즐겨찾기를 지우는 버튼이 된다([onDeleteAll] 참고).
     */
    favoritesLoaded: Boolean = false,
    /** 목록 조회가 실패했다. **빈 목록과 다르게** 그려야 한다 — 섞으면 위 버튼의 근거가 거짓이 된다 */
    favoritesLoadFailed: Boolean = false,
    onRetryFavorites: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onActiveRideClick: () -> Unit = {},
    onFavoritePlaceClick: (FavoritePlace) -> Unit = {},
    onEditFavoritesToggle: () -> Unit = {},
    onFavoriteSelectToggle: (FavoritePlace) -> Unit = {},
    /**
     * 「선택한 N곳 삭제」. 1곳이어도 다건 삭제(batch-delete)로 보낸다 — 서버가 전부-아니면-전무라
     * 3개 중 1개만 지워진 어중간한 상태가 생기지 않는다(단건 삭제를 N번 돌리면 중간 404 에서
     * 목록과 서버가 어긋난다).
     */
    onDeleteSelected: () -> Unit = {},
    /**
     * 「전체 삭제」. **목록이 비어 보일 때도 쓸 수 있어야 한다** — placeId·좌표가 없는 레거시 행은
     * 매퍼가 버려 화면에 안 나오는데 서버에서는 10칸 중 하나를 계속 차지하고, 고를 수 없으니
     * 이 경로가 유일한 탈출구다.
     *
     * **단 [favoritesLoaded] 가 true 일 때만 그린다.** 「비어 보인다」가 조회 실패 때문일 수도 있고,
     * 그때는 서버에 멀쩡한 즐겨찾기가 있다 — 탈출구가 아니라 파괴 버튼이 된다.
     */
    onDeleteAll: () -> Unit = {},
    onTabSelect: (MoyeotaTab) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(CanvasBg)) {
        // 배경 = 풀스크린 지도, 그 위에 **드래그로 접었다 펴는** 시트(16·21 과 같은 MapSheetScaffold).
        // 펼침(기본)에서는 예전 홈처럼 지도 약 200dp + 검색·즐겨찾기,
        // 핸들을 내리면 상세(즐겨찾기)가 접혀 **지도가 검색 카드 위까지 전부** 보인다.
        MapSheetScaffold(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            mapRevealHeight = HomeMapRevealHeight,
            collapsedSheetHeight = HomeCollapsedSheetHeight,
            // 손잡이 줄만 남기는 세 번째 앵커 — 지도를 다 보고 싶은 사람의 길(위 KDoc). 지도의
            // contentPadding 은 시트가 손잡이 줄 높이를 알려 주므로(settledSheetHeight) 따로 손대지 않는다.
            hideable = true,
            hiddenHandleLabel = "검색 패널 펼치기",
            background = { sheet ->
                // 진행 배너(상태바 포함) 높이 — 지도 top contentPadding 으로 넘겨 줌 컨트롤(+/−)이
                // 배너 아래로 내려가게 한다(작은 폰에서 배너와 줌 버튼이 겹치던 문제)
                var bannerHeightPx by remember { mutableIntStateOf(0) }
                val bannerHeight = with(LocalDensity.current) { bannerHeightPx.toDp() }
                var map by remember { mutableStateOf<NaverMap?>(null) }
                val myPosition = latLngOrNull(myLocation?.latitude, myLocation?.longitude)
                NaverMapView(
                    modifier = Modifier.fillMaxSize(),
                    // 앵커 기준 시트 높이를 빼 카메라 중심이 시트 뒤가 아니라 실제 보이는 영역에 잡히게 한다
                    contentPadding = PaddingValues(top = bannerHeight, bottom = sheet.settledSheetHeight),
                    onMapReady = { map = it },
                )

                // 첫 실위치로 **한 번만** 옮긴다. 주기 갱신(1초)마다 카메라를 되돌리면 지도를 팬·줌할 수
                // 없다(합승 탭·26 과 같은 규칙). 그 뒤로는 파란 점만 최신 좌표를 따라간다.
                // NaverMapView 의 center 는 기본값(상수)이라 이 이동을 되돌리지 않는다.
                var centeredOnMe by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(map, myPosition) {
                    val naverMap = map ?: return@LaunchedEffect
                    val fix = myPosition ?: return@LaunchedEffect
                    if (centeredOnMe) return@LaunchedEffect
                    naverMap.moveCamera(CameraUpdate.scrollAndZoomTo(fix, HomeMyLocationZoom))
                    centeredOnMe = true
                }
                if (myPosition != null) {
                    MyLocationOverlay(
                        map = map,
                        position = myPosition,
                        accuracyMeters = myLocation?.accuracyMeters,
                        bearingDegrees = myLocation?.bearingDegrees,
                    )
                }
                // 진행 중인 방이 있으면 지도 위에 복귀 배너 하나만 얹는다 — 배너 밖은 여전히 지도의 것이다
                if (activeRide != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bannerHeightPx = it.height },
                    ) {
                        StatusBarSpacer()
                        ActiveRideBanner(
                            ride = activeRide,
                            onClick = onActiveRideClick,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            },
            // 항상 보이는 줄 = 인사 + 「어디로 갈까요?」 + 검색 바 (핸들과 함께 드래그 타깃)
            sheetTop = {
                HeroSection(userName = userName, onSearchClick = onSearchClick)
            },
            // 접히면 사라지는 상세 = 즐겨찾기 목록
            sheetDetail = {
                // 확인 다이얼로그 대상. null 이면 다이얼로그 없음 — 탈퇴·로그아웃과 같은 방식으로
                // 화면 안에 들고 있는다(ViewModel 까지 올릴 상태가 아니다).
                var confirming by remember { mutableStateOf<FavoriteDeleteKind?>(null) }
                // 편집 모드가 바뀌면 열려 있던 다이얼로그를 닫는다 — 안 닫으면 「완료」를 누른 뒤에
                // 확인 창만 떠 있어 무엇에 대한 확인인지 알 수 없다.
                // 조건부 대입이 아니라 LaunchedEffect 인 이유: 합성 중에 상태를 쓰면 안 된다.
                LaunchedEffect(editingFavorites) { confirming = null }

                Column(modifier = Modifier.fillMaxWidth().background(CanvasBg)) {
                    Spacer(Modifier.height(24.dp))

                    // 즐겨찾기 — 서버 등록 순서(sequence) 그대로 전부
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "즐겨찾기",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayMute,
                        )
                        Spacer(Modifier.weight(1f))
                        // 아이콘 자산이 없어 글자로 둔다(마이페이지의 ↗/› 방침과 같다).
                        // **목록이 비어 보여도 숨기지 않는다** — 그때가 「전체 삭제」가 유일한
                        // 탈출구인 경우(매퍼가 버린 레거시 행)라서 숨기면 탈출구가 함께 사라진다.
                        Text(
                            text = if (editingFavorites) "완료" else "편집",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deletingFavorites) GrayAsh else MoyeotaColor.Primary500,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !deletingFavorites) { onEditFavoritesToggle() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (favoritePlaces.isEmpty()) {
                        // ★ 「못 받았다」와 「없다」를 절대 같게 그리지 않는다.
                        // 둘을 섞으면 조회가 끊긴 사용자에게 화면이 「등록한 곳이 없어요」라고 말하고,
                        // 편집 중이면 거기에 더해 「전체 삭제로 비울 수 있어요」까지 권한다 —
                        // 서버에는 멀쩡한 즐겨찾기가 있고, 통신이 돌아오는 순간 그게 전부 지워진다.
                        if (favoritesLoadFailed) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = "즐겨찾기를 불러오지 못했어요",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GrayAsh,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "다시 시도",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MoyeotaColor.Primary500,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onRetryFavorites() }
                                        .padding(horizontal = 10.dp)
                                        .wrapContentHeight(),
                                )
                            }
                        } else {
                            Text(
                                // 편집 중에 비어 보이는 목록은 「등록해 보세요」로 끝낼 수 없다 —
                                // 서버에 남은 안 보이는 행을 치우려고 편집에 들어온 경우가 그 자리다.
                                // 단 **조회에 성공한 뒤**에만 그렇게 말할 수 있다(위 분기가 그걸 보장한다).
                                text = if (editingFavorites) {
                                    "목록에 안 보이는 장소가 서버에 남아 있을 수 있어요. 「전체 삭제」로 비울 수 있어요"
                                } else {
                                    // 등록은 15 목적지 화면에서 ★ 로 한다
                                    "즐겨찾기를 등록하면 여기서 바로 부를 수 있어요"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayAsh,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadow)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MoyeotaColor.SurfaceCanvas),
                        ) {
                            favoritePlaces.forEachIndexed { index, place ->
                                FavoritePlaceRow(
                                    place = place,
                                    distanceLabel = distanceLabel(myLocation, place),
                                    editing = editingFavorites,
                                    selected = place.id in selectedFavoriteIds,
                                    enabled = !deletingFavorites,
                                    // 편집 중에는 15 로 튀어나가지 않는다 — 화면이 바뀌면 고른 것이 날아간다
                                    onClick = {
                                        if (editingFavorites) {
                                            onFavoriteSelectToggle(place)
                                        } else {
                                            onFavoritePlaceClick(place)
                                        }
                                    },
                                )
                                if (index != favoritePlaces.lastIndex) {
                                    HorizontalDivider(
                                        color = MoyeotaColor.Hairline,
                                        modifier = Modifier.padding(start = 42.dp, end = 18.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ★ 「전체 삭제」는 **조회에 한 번이라도 성공했을 때만** 그린다.
                    // 레거시 행 탈출구라는 명분은 「목록을 받아 왔고 그 결과가 비었다」일 때만 선다 —
                    // 못 받은 상태에서 그리면 멀쩡한 즐겨찾기를 지우는 버튼이 된다.
                    if (editingFavorites && favoritesLoaded) {
                        Spacer(Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            // 파괴적 동작 두 줄이 맞닿아 있으면 「선택한 N곳」을 노린 손가락이
                            // 「전체 삭제」를 집는다. 둘 사이를 벌려 둔다.
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // TextButton 을 쓰지 않는다 — 이 앱에서 TextButton 은 다이얼로그 안에만 있다.
                            if (favoritePlaces.isNotEmpty()) {
                                DangerTextRow(
                                    text = "선택한 ${selectedFavoriteIds.size}곳 삭제",
                                    // 0곳이면 서버가 400 INVALID_PLACE_IDS 를 준다 — 누를 수 없게 둔다
                                    enabled = selectedFavoriteIds.isNotEmpty() && !deletingFavorites,
                                    onClick = { confirming = FavoriteDeleteKind.SELECTED },
                                )
                            }
                            DangerTextRow(
                                text = "전체 삭제",
                                enabled = !deletingFavorites,
                                onClick = { confirming = FavoriteDeleteKind.ALL },
                            )
                        }
                    }

                    if (favoriteErrorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = favoriteErrorMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MoyeotaColor.Danger600,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // 되돌릴 수 없는 삭제라 확인을 받는다(탈퇴·로그아웃과 같은 모양).
                // 다이얼로그는 목록을 되풀이하지 않는다 — 「되돌릴 수 없다」 한 가지만 말한다.
                val pending = confirming
                if (pending != null) {
                    AlertDialog(
                        onDismissRequest = { confirming = null },
                        title = {
                            Text(
                                text = when (pending) {
                                    FavoriteDeleteKind.SELECTED -> "선택한 곳을 지울까요?"
                                    FavoriteDeleteKind.ALL -> "즐겨찾기를 모두 지울까요?"
                                },
                                style = MoyeotaType.HeadingMd,
                            )
                        },
                        text = {
                            Text(
                                text = when (pending) {
                                    FavoriteDeleteKind.SELECTED -> "지우면 되돌릴 수 없어요"
                                    FavoriteDeleteKind.ALL -> "등록해 둔 곳이 모두 사라지고 되돌릴 수 없어요"
                                },
                                style = MoyeotaType.BodySm,
                                color = MoyeotaColor.TextBody,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirming = null
                                when (pending) {
                                    FavoriteDeleteKind.SELECTED -> onDeleteSelected()
                                    FavoriteDeleteKind.ALL -> onDeleteAll()
                                }
                            }) {
                                Text(text = "삭제", fontWeight = FontWeight.Bold, color = MoyeotaColor.Danger500)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirming = null }) {
                                Text(text = "취소", color = MoyeotaColor.TextMute)
                            }
                        },
                        containerColor = MoyeotaColor.SurfaceCanvas,
                    )
                }
            },
        )

        MoyeotaBottomBar(selected = MoyeotaTab.HOME, onSelect = onTabSelect)
    }
}

@Composable
private fun HeroSection(
    userName: String?,
    onSearchClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(HeroBg)) {
        // 시트 핸들(하단 8dp 여백)과 합쳐 인사말 위 여백을 만든다
        Spacer(Modifier.height(18.dp))
        Text(
            // 이름을 모르면 "OO님" 자리를 통째로 빼고 인사만 남긴다 — 빈 이름이 드러나지 않는다.
            text = if (userName != null) "${userName}님, 반가워요" else "반가워요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = GraySlate,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = "어디로 갈까요?",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.InkPrimary,
            lineHeight = 34.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "목적지를 넣으면 같은 방향 사람을 찾아드려요",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GrayDeep,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(20.dp))
        // 목적지 검색 바 → 15
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color(0x29085AF5))
                .clip(RoundedCornerShape(18.dp))
                .background(MoyeotaColor.SurfaceCanvas)
                .clickable { onSearchClick() }
                .padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon()
            Spacer(Modifier.size(10.dp))
            Text(
                text = "목적지 검색",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayMute,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MoyeotaColor.Primary500, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ArrowRightIcon(color = MoyeotaColor.TextOnDark)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/** 어떤 삭제의 확인을 받는 중인지. 다이얼로그 제목·본문·동작이 전부 이 값으로 갈린다 */
private enum class FavoriteDeleteKind { SELECTED, ALL }

/**
 * 즐겨찾기 한 행 — 점 · 이름/도로명주소 · (실위치가 있을 때만) 직선 거리.
 *
 * 편집 모드([editing])에서는 거리 라벨 자리를 체크박스가 대신한다. **제스처는 쓰지 않는다** —
 * 이 목록은 세로로 드래그되는 시트 안이고 그 배경은 패닝되는 지도라, 행 위 가로 스와이프가
 * 시트 드래그와 지도 제스처 둘 다와 싸운다. 편집 모드는 탭만 쓴다.
 */
@Composable
private fun FavoritePlaceRow(
    place: FavoritePlace,
    distanceLabel: String?,
    editing: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 삭제가 날아간 동안 탭을 막는다 — 단건·다건 삭제가 멱등이 아니라서(두 번째가 404)
            // 연타가 「지우지 못했어요」로 보인다
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(RowDot, CircleShape),
        )
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Text(
                text = place.roadName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GrayMute,
            )
        }
        if (editing) {
            Spacer(Modifier.size(12.dp))
            SelectBox(selected = selected)
        } else if (distanceLabel != null) {
            Spacer(Modifier.size(12.dp))
            Text(
                text = distanceLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GrayMute,
            )
        }
    }
}

/**
 * 파괴적 동작 한 줄. 눌러서 **다이얼로그를 여는 것**까지가 역할이고 삭제는 확인 뒤에 일어난다.
 *
 * `TextButton` 을 쓰지 않는 이유: 이 앱은 화면 본문에 TextButton 을 두지 않는다 —
 * 쓰인 자리가 전부 AlertDialog 안이다(프로필 수정 화면 주석의 방침).
 */
@Composable
private fun DangerTextRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (enabled) MoyeotaColor.Danger500 else GrayAsh,
        modifier = Modifier
            .fillMaxWidth()
            // 되돌릴 수 없는 동작이라 터치 타깃을 48dp 로 채운다. 세로 패딩만으로는 38dp 였고,
            // 바로 아래 「전체 삭제」와 맞닿아 손가락이 옆 줄을 집었다.
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp)
            .wrapContentHeight(),
    )
}

/** 편집 모드의 선택 상자 — material-icons 가 없어 테두리는 Canvas, 체크는 로그인 화면 것을 쓴다 */
@Composable
private fun SelectBox(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MoyeotaColor.Primary500 else MoyeotaColor.SurfaceCanvas)
            .border(
                width = 1.5.dp,
                color = if (selected) MoyeotaColor.Primary500 else RowDot,
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) CheckMarkIcon(iconSize = 14.dp, color = MoyeotaColor.TextOnDark)
    }
}

/**
 * 내 위치 → 즐겨찾기 직선 거리 라벨. 1km 미만은 "850m", 이상은 소수 첫째 자리 "6.2km".
 * 실위치가 없거나 좌표가 유한하지 않으면 null — 행에서 거리 칸을 생략한다.
 */
private fun distanceLabel(from: UserCoordinates?, place: FavoritePlace): String? {
    if (from == null) return null
    if (!from.latitude.isFinite() || !from.longitude.isFinite()) return null
    if (!place.latitude.isFinite() || !place.longitude.isFinite()) return null
    val result = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, place.latitude, place.longitude, result)
    val meters = result[0]
    return if (meters < 1000f) {
        "${meters.roundToInt()}m"
    } else {
        String.format(Locale.US, "%.1fkm", meters / 1000f)
    }
}

// ─── 아이콘 (material-icons 미사용 — Canvas 직접 드로잉) ─────────────────────

@Composable
private fun SearchIcon(modifier: Modifier = Modifier, color: Color = Color(0xFF54637D)) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val strokeWidth = 2.dp.toPx()
        drawCircle(
            color = color,
            radius = w * 0.26f,
            center = Offset(w * 0.42f, w * 0.42f),
            style = Stroke(strokeWidth),
        )
        drawLine(color, Offset(w * 0.62f, w * 0.62f), Offset(w * 0.86f, w * 0.86f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun ArrowRightIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.52f, h * 0.2f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.52f, h * 0.8f), strokeWidth, StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        userName = "김성윤",
        myLocation = UserCoordinates(latitude = 35.2316, longitude = 129.0846),
        favoritePlaces = listOf(
            FavoritePlace(1, "집", "부산 부산진구 중앙대로 672", 35.1577, 129.0592, sequence = 1),
            FavoritePlace(2, "학교", "부산 금정구 부산대학로 63번길 2", 35.2334, 129.0798, sequence = 2),
        ),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenEmptyFavoritesPreview() {
    HomeScreen(userName = "김성윤")
}

/** 편집 모드 — 체크박스와 두 삭제 줄이 어떻게 보이는지 확인용 */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenEditingFavoritesPreview() {
    HomeScreen(
        userName = "김성윤",
        favoritePlaces = listOf(
            FavoritePlace(1, "집", "부산 부산진구 중앙대로 672", 35.1577, 129.0592, sequence = 1),
            FavoritePlace(2, "학교", "부산 금정구 부산대학로 63번길 2", 35.2334, 129.0798, sequence = 2),
        ),
        editingFavorites = true,
        selectedFavoriteIds = setOf(2L),
    )
}

/**
 * 목록이 비어 보이는데 편집 중인 경우 — **「전체 삭제」가 남아 있어야 한다.**
 * placeId·좌표 없는 레거시 행은 매퍼가 버려 화면에 안 나오지만 서버 10칸 중 하나를 차지하므로,
 * 이 화면이 그 행을 치울 유일한 자리다.
 */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenEditingEmptyFavoritesPreview() {
    HomeScreen(userName = "김성윤", editingFavorites = true)
}
