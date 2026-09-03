package com.moyeota.presentation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.Place
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.DispatchRepository
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.domain.repository.ReportRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import com.moyeota.presentation.feature.auth.LoginFormRoute
import com.moyeota.presentation.feature.auth.LoginScreen
import com.moyeota.presentation.feature.auth.MannerPledgeRoute
import com.moyeota.presentation.feature.auth.ProfileSetupScreen
import com.moyeota.presentation.feature.auth.SafetySettingsScreen
import com.moyeota.presentation.feature.auth.SignupCompleteScreen
import com.moyeota.presentation.feature.auth.SignupDraft
import com.moyeota.presentation.feature.chat.ChatRoute
import com.moyeota.presentation.feature.chat.EmergencyRoute
import com.moyeota.presentation.feature.chat.RideOngoingScreen
import com.moyeota.presentation.feature.explore.ExploreRoute
import com.moyeota.presentation.feature.explore.JoinConfirmRoute
import com.moyeota.presentation.feature.home.DemoOrigin
import com.moyeota.presentation.feature.home.DestinationConfirmRoute
import com.moyeota.presentation.feature.home.DestinationRoute
import com.moyeota.presentation.feature.home.HomeRoute
import com.moyeota.presentation.feature.matching.DispatchStatusRoute
import com.moyeota.presentation.feature.matching.MatchWaitingRoute
import com.moyeota.presentation.feature.matching.PartnerProfileScreen
import com.moyeota.presentation.feature.matching.RideDetailRoute
import com.moyeota.presentation.feature.mypage.MyPageScreen
import com.moyeota.presentation.feature.mypage.MyRidesScreen
import com.moyeota.presentation.feature.mypage.RideCompleteScreen
import com.moyeota.presentation.feature.onboarding.OnboardingSafetyScreen
import com.moyeota.presentation.feature.onboarding.OnboardingSavingScreen
import com.moyeota.presentation.feature.onboarding.OnboardingTrustScreen
import com.moyeota.presentation.feature.payment.FareFinalScreen
import com.moyeota.presentation.feature.payment.PaymentAddScreen
import com.moyeota.presentation.feature.payment.PaymentMethodsScreen
import com.moyeota.presentation.feature.payment.PaymentResultScreen
import com.moyeota.presentation.feature.payment.SettlementScreen
import kotlinx.coroutines.launch

/**
 * 앱의 진입 라우팅. 세션이 확정되기 전에는 어떤 화면도 열지 않는다.
 *
 * [AuthState.Unknown] 은 "아직 디스크에서 토큰을 못 읽었다"는 뜻이지 미로그인이 아니다.
 * 이걸 미로그인으로 취급하면 이미 로그인한 사용자가 앱을 켤 때마다 온보딩을 스쳐 본다.
 * 확정된 뒤에야 [MainNavHost] 를 만들고, 그 시점의 상태가 시작 화면을 정한다.
 */
@Composable
fun MainNavGraph(
    authRepository: AuthRepository,
    rideRepository: RideRepository,
    placeRepository: PlaceRepository,
    chatRepository: ChatRepository,
    dispatchRepository: DispatchRepository,
    reportRepository: ReportRepository,
    userSession: UserSession,
) {
    val authState by authRepository.authState.collectAsState()

    if (authState is AuthState.Unknown) {
        SplashScreen()
        return
    }

    MainNavHost(
        loggedIn = authState is AuthState.Authenticated,
        authRepository = authRepository,
        rideRepository = rideRepository,
        placeRepository = placeRepository,
        chatRepository = chatRepository,
        dispatchRepository = dispatchRepository,
        reportRepository = reportRepository,
        userSession = userSession,
    )
}

// v15 와이어프레임 35화면 이동 규칙을 한곳에서 배선한다.
// 각 화면은 콜백만 노출하는 순수 컴포저블 — 화면 안에는 네비게이션 코드가 없다.
@Composable
private fun MainNavHost(
    loggedIn: Boolean,
    authRepository: AuthRepository,
    rideRepository: RideRepository,
    placeRepository: PlaceRepository,
    chatRepository: ChatRepository,
    dispatchRepository: DispatchRepository,
    reportRepository: ReportRepository,
    userSession: UserSession,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // 로그인 사용자의 이름 — NavHost 바깥(액티비티 스코프)에 두어 홈·마이가 한 인스턴스를 공유한다.
    // 화면별 ViewModel 로 두면 탭을 오갈 때마다 같은 조회가 반복된다.
    val profileViewModel: UserProfileViewModel =
        viewModel(factory = UserProfileViewModel.factory(authRepository))
    val userName by profileViewModel.userName.collectAsState()

    // 화면 사이에 넘겨야 하는 값 (백엔드 없는 와이어프레임 데모용 간이 상태)
    // 10 프로필 만들기가 채우는 가입 정보 — 12 매너 서약에서 제출한다
    var signupDraft by remember { mutableStateOf(SignupDraft()) }
    // 로그인 화면으로 돌아온 이유. 세션 만료일 때만 채워지고 사용자가 스스로 로그아웃하면 비운다.
    var signOutNotice by remember { mutableStateOf<String?>(null) }
    var logoutRequested by remember { mutableStateOf(false) }
    var searchInitialQuery by remember { mutableStateOf("") }
    // 15 에서 고른 출발지·도착지(좌표 포함) — 16 방 생성에 필요하다.
    // 출발지는 고르지 않으면 null 이고 16 이 DemoOrigin 으로 떨어진다.
    var confirmedOrigin by remember { mutableStateOf<Place?>(null) }
    var confirmedDestination by remember { mutableStateOf<Place?>(null) }
    var selectedPartyId by remember { mutableStateOf<Long?>(null) }
    // 16 에서 만든 방 — 21 매칭 대기가 이 방을 조회한다
    var createdPartyId by remember { mutableStateOf<Long?>(null) }
    // 지금 배차·운행 중인 방 — 25 배차 현황과 27 신고가 이 id 로 서버를 조회한다.
    // 탐색용 selectedPartyId 와 분리한다(탐색 중 다른 방을 눌러도 내 운행이 바뀌면 안 된다).
    var activePartyId by remember { mutableStateOf<Long?>(null) }

    fun back() {
        navController.popBackStack()
    }

    // 공통 규칙: 13 가입 완료 · 25 배차 이후에는 뒤로 갈 수 없도록 스택 초기화
    fun resetTo(route: String) {
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // 하단탭(14 홈 · 17 합승 · 24 채팅 · 35 마이) 이동 — 홈을 탭 루트로 유지
    fun navigateTab(tab: MoyeotaTab) {
        val route = when (tab) {
            MoyeotaTab.HOME -> Routes.HOME
            MoyeotaTab.EXPLORE -> Routes.EXPLORE
            MoyeotaTab.CHAT -> Routes.CHAT
            MoyeotaTab.MYPAGE -> Routes.MYPAGE
        }
        // 이미 그 탭이면 아무 것도 하지 않는다 (엔트리 재생성 방지)
        if (navController.currentDestination?.route == route) return
        // 홈탭은 스택에 남아있는 기존 HOME 엔트리로 pop 복귀한다.
        // navigate 로 새 엔트리를 만들면 NavHost 의 SaveableStateHolder 가 엔트리 id 로
        // 상태를 보관하는 구조라 HomeScreen 의 rememberSaveable(지도 카메라 등)이 통째로 폐기된다.
        if (route == Routes.HOME && navController.popBackStack(Routes.HOME, inclusive = false)) {
            return
        }
        navController.navigate(route) {
            // 홈을 탭 루트로 유지하고, 벗어나는 탭의 상태는 저장·복원한다
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 로그인 상태는 앱을 켤 때 한 번만 시작 화면을 정한다. 이후 전이는 아래 LaunchedEffect 가 다룬다
    // — startDestination 을 계속 따라가게 만들면 NavHost 가 통째로 재생성돼 백스택이 날아간다.
    val startDestination = remember { if (loggedIn) Routes.HOME else Routes.ONBOARDING_SAVING }

    // 로그인 → 미로그인 전이는 두 경로로 온다: 사용자의 로그아웃, 그리고 재발급까지 실패한 세션 만료.
    // 어느 화면에 있든 결과는 같아야 하므로 개별 화면이 아니라 여기 한 곳에서 처리한다
    // (별도 이벤트 채널은 없다 — authState 스트림이 유일한 통보 경로다).
    var wasLoggedIn by remember { mutableStateOf(loggedIn) }
    LaunchedEffect(loggedIn) {
        if (wasLoggedIn && !loggedIn) {
            signOutNotice = if (logoutRequested) null else "세션이 만료됐어요. 다시 로그인해 주세요"
            logoutRequested = false
            resetTo(Routes.LOGIN_FORM)
        }
        wasLoggedIn = loggedIn
    }

    NavHost(navController = navController, startDestination = startDestination) {
        // A · 온보딩 01–03 — 건너뛰기는 항상 04 로그인
        composable(Routes.ONBOARDING_SAVING) {
            OnboardingSavingScreen(
                onNext = { navController.navigate(Routes.ONBOARDING_TRUST) },
                onSkip = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.ONBOARDING_TRUST) {
            OnboardingTrustScreen(
                onNext = { navController.navigate(Routes.ONBOARDING_SAFETY) },
                onSkip = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.ONBOARDING_SAFETY) {
            OnboardingSafetyScreen(
                onStart = { navController.navigate(Routes.LOGIN) },
                // 계정이 이미 있는 사람은 가입 플로우가 아니라 로그인으로 보낸다
                onAlreadyHaveAccount = { navController.navigate(Routes.LOGIN_FORM) },
                onSkip = { navController.navigate(Routes.LOGIN) },
            )
        }

        // B · 로그인 04 · 04a
        composable(Routes.LOGIN) {
            LoginScreen(
                // 「이메일로 시작하기」 = 가입 시작. 카카오는 백엔드에 없어 화면 안에서 「준비 중」만 안내한다
                onEmailStart = {
                    signupDraft = SignupDraft() // 새 가입은 빈 초안에서 시작
                    navController.navigate(Routes.PROFILE_SETUP)
                },
                onLogin = { navController.navigate(Routes.LOGIN_FORM) },
            )
        }
        composable(Routes.LOGIN_FORM) {
            LoginFormRoute(
                repository = authRepository,
                onBack = {
                    // 세션 만료·로그아웃으로 스택이 비워진 채 들어왔으면 돌아갈 곳이 없다 — 04 로 보낸다.
                    // 그냥 popBackStack 하면 화면 없는 빈 그래프가 남는다.
                    if (navController.previousBackStackEntry != null) back() else resetTo(Routes.LOGIN)
                },
                onLoggedIn = {
                    signOutNotice = null
                    resetTo(Routes.HOME)
                },
                onSignUp = {
                    signupDraft = SignupDraft()
                    navController.navigate(Routes.PROFILE_SETUP)
                },
                noticeMessage = signOutNotice,
            )
        }

        // C · 가입 10–13 (3단계)
        //
        // 05 계정 유형 · 06 학교 이메일 · 07 인증 코드 · 08 재직 인증 · 09 본인 인증은 여기 없다.
        // 가입 API 에 계정 유형이 없고, 학교·재직 인증은 마이페이지에서 나중에 붙이는 컨셉이며,
        // 휴대폰 본인 인증은 MVP 범위 밖이다 — 필수 경로에 두면 인증 없이 값만 묻거나
        // 서버로 가지도 않는 값을 묻는 화면이 된다(Routes.kt B' 절 참고).
        // 09 가 받던 실명·생년월일·성별·휴대폰은 10 의 「기본 정보」 절이 이어받았다.
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onBack = ::back,
                onNext = { _, info ->
                    // 서버로 갈 7개 값이 이 화면에서 한 번에 채워진다.
                    // 표시 이름은 가입 필드에 자리가 없어 아직 전송되지 않는다.
                    signupDraft = info
                    navController.navigate(Routes.SAFETY_SETTINGS)
                },
            )
        }
        composable(Routes.SAFETY_SETTINGS) {
            SafetySettingsScreen(
                onBack = ::back,
                onContinue = { _, _ -> navController.navigate(Routes.MANNER_PLEDGE) },
            )
        }
        composable(Routes.MANNER_PLEDGE) {
            // 여기서 회원가입 + 자동 로그인이 실행된다. 성공했을 때만 13 으로 넘어간다.
            MannerPledgeRoute(
                repository = authRepository,
                draft = signupDraft,
                onBack = ::back,
                onCompleted = { navController.navigate(Routes.SIGNUP_COMPLETE) },
            )
        }
        composable(Routes.SIGNUP_COMPLETE) {
            SignupCompleteScreen(
                onStart = { resetTo(Routes.HOME) },
                onExplore = { resetTo(Routes.EXPLORE) },
            )
        }

        // D · 홈 · 목적지 14–16
        composable(Routes.HOME) {
            HomeRoute(
                repository = placeRepository,
                userName = (userName as? UserNameState.Resolved)?.name,
                onSearchClick = {
                    searchInitialQuery = ""
                    navController.navigate(Routes.DESTINATION)
                },
                onPlaceQuery = { query ->
                    searchInitialQuery = query
                    navController.navigate(Routes.DESTINATION)
                },
                onTabSelect = ::navigateTab,
            )
        }
        composable(Routes.DESTINATION) {
            DestinationRoute(
                repository = placeRepository,
                initialQuery = searchInitialQuery,
                onBack = ::back,
                onConfirmRoute = { origin, place ->
                    confirmedOrigin = origin
                    confirmedDestination = place
                    navController.navigate(Routes.DESTINATION_CONFIRM)
                },
            )
        }
        // 16 은 15 위에 뜨는 모달 — 스크림 탭·닫기로만 닫힌다
        dialog(
            Routes.DESTINATION_CONFIRM,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DestinationConfirmRoute(
                repository = rideRepository,
                // 핀 조정·GPS 출발지의 좌표를 실주소로 되짚는다 (GET /places/reverse)
                placeRepository = placeRepository,
                userSession = userSession,
                origin = confirmedOrigin ?: DemoOrigin,
                destination = confirmedDestination,
                onDismiss = ::back,
                onPartyCreated = { ride ->
                    createdPartyId = ride.id.toLongOrNull()
                    activePartyId = createdPartyId
                    // 모달을 닫고 21 매칭 대기로 — 뒤로 눌러 모달로 돌아오지 않게 한다
                    navController.navigate(Routes.MATCH_WAITING) {
                        popUpTo(Routes.DESTINATION_CONFIRM) { inclusive = true }
                    }
                },
            )
        }

        // E · 합승 탐색 17–20 (17·18·19 는 한 화면의 시트 상태)
        composable(Routes.EXPLORE) {
            ExploreRoute(
                repository = rideRepository,
                onJoinParty = { ride ->
                    selectedPartyId = ride.id.toLongOrNull()
                    navController.navigate(Routes.JOIN_CONFIRM)
                },
                onOngoingRideClick = { navController.navigate(Routes.MY_RIDES) },
                onCreateRoomClick = { navController.navigate(Routes.DESTINATION) },
                onTabSelect = ::navigateTab,
            )
        }
        composable(Routes.JOIN_CONFIRM) {
            JoinConfirmRoute(
                repository = rideRepository,
                partyId = selectedPartyId,
                onDismiss = ::back,
                // 합류 응답이 곧 최신 방 상세 — 그대로 21 매칭 대기로 넘겨 정원 도달을 기다린다.
                // (준비/출발 버튼이 사라진 뒤로 합류의 다음 단계는 22 상세가 아니라 대기다)
                onJoined = { ride ->
                    createdPartyId = ride.id.toLongOrNull()
                    activePartyId = createdPartyId
                    navController.navigate(Routes.MATCH_WAITING) {
                        popUpTo(Routes.JOIN_CONFIRM) { inclusive = true }
                    }
                },
                onMemberClick = { navController.navigate(Routes.PARTNER_PROFILE) },
            )
        }

        // F · 매칭 · 탑승 21–23 · 25
        composable(Routes.MATCH_WAITING) {
            MatchWaitingRoute(
                repository = rideRepository,
                partyId = createdPartyId,
                onCancelSearch = { navigateTab(MoyeotaTab.HOME) }, // 나가기 성공 후 14 홈
                onCardClick = {
                    selectedPartyId = createdPartyId
                    navController.navigate(Routes.RIDE_DETAIL)
                },
                // 정원이 차면 서버가 스스로 기사 매칭을 시작한다 — 앱은 status 전이를 받아 넘어갈 뿐이다.
                // 대기 화면은 스택에서 지운다. 남겨두면 뒤로 돌아왔을 때 status 가 여전히 배차 상태라
                // 다시 앞으로 튕겨나가는 루프가 된다(공통 규칙: 배차 후 되돌리기 차단).
                onMatchingStarted = {
                    activePartyId = createdPartyId
                    navController.navigate(Routes.DISPATCH_STATUS) {
                        popUpTo(Routes.MATCH_WAITING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.RIDE_DETAIL) {
            RideDetailRoute(
                repository = rideRepository,
                userSession = userSession,
                partyId = selectedPartyId,
                onBack = ::back,
                onPartnerClick = { navController.navigate(Routes.PARTNER_PROFILE) },
                onLeave = { navigateTab(MoyeotaTab.HOME) },
                onDepart = {
                    activePartyId = selectedPartyId
                    navController.navigate(Routes.DISPATCH_STATUS)
                },
            )
        }
        composable(Routes.PARTNER_PROFILE) {
            PartnerProfileScreen(
                onBack = ::back,
                onChatClick = { navController.navigate(Routes.CHAT) },
            )
        }
        composable(Routes.DISPATCH_STATUS) {
            DispatchStatusRoute(
                rideRepository = rideRepository,
                dispatchRepository = dispatchRepository,
                partyId = activePartyId,
                onStartRide = { resetTo(Routes.RIDE_ONGOING) },
                onBack = ::back,
            )
        }

        // G · 채팅 · 안심 24 · 26 · 27
        composable(Routes.CHAT) {
            ChatRoute(
                repository = chatRepository,
                userSession = userSession,
                onOpenRideOngoing = { navController.navigate(Routes.RIDE_ONGOING) },
                onStartLocationShare = { navController.navigate(Routes.RIDE_ONGOING) },
                onLeaveChat = { navigateTab(MoyeotaTab.HOME) },
                onTabSelect = ::navigateTab,
            )
        }
        composable(Routes.RIDE_ONGOING) {
            // 데모용 15초 자동 전환 타이머를 제거했다 (QA D-3).
            // 26 은 신고 진입점인데 15초 뒤 28 로 넘어가버려, 급할 때 눌러야 할 「신고」 자리에
            // 28 의 확인 버튼이 놓이면서 정산으로 새는 사고가 났다(재현 3/3).
            //
            // 28 은 원래 기사측 이벤트로 전이되는 화면이지만, 승객이 폴링할 수 있는 운행 완료 상태가
            // 아직 없다 — 서버 `startRide`/`completeRide` 가 저장되지 않아 파티가 DRIVER_ASSIGNED 를
            // 벗어나지 못한다(QA D-2). status==COMPLETED 폴링으로 바꾸면 28~32 가 통째로 도달 불가가 된다.
            // 그래서 그때까지는 경유 카드 탭(=하차)을 임시 수동 트리거로 쓴다.
            RideOngoingScreen(
                onBack = ::back,
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onReport = { navController.navigate(Routes.EMERGENCY) },
                onArrived = { navController.navigate(Routes.FARE_FINAL) },
            )
        }
        composable(Routes.EMERGENCY) {
            // 접수 → 통화 확인까지 마치면 EmergencyRoute 가 onBack 으로 26 운행 중에 되돌린다
            EmergencyRoute(
                repository = reportRepository,
                partyId = activePartyId,
                onBack = ::back,
            )
        }

        // H · 요금 · 정산 · 결제 28–32
        composable(Routes.FARE_FINAL) {
            FareFinalScreen(
                onBack = ::back,
                onConfirm = { navController.navigate(Routes.SETTLEMENT) },
            )
        }
        composable(Routes.SETTLEMENT) {
            SettlementScreen(
                onBack = ::back,
                onChangeMethod = { navController.navigate(Routes.PAYMENT_METHODS) },
                onPay = { navController.navigate(Routes.PAYMENT_RESULT) },
            )
        }
        composable(Routes.PAYMENT_METHODS) {
            PaymentMethodsScreen(
                onBack = ::back,
                onAddMethod = { navController.navigate(Routes.PAYMENT_ADD) },
                onSaveDefault = { back() },
            )
        }
        composable(Routes.PAYMENT_ADD) {
            PaymentAddScreen(
                onBack = ::back,
                onAdded = ::back,
            )
        }
        composable(Routes.PAYMENT_RESULT) {
            PaymentResultScreen(
                onBack = ::back,
                onConfirm = { navController.navigate(Routes.RIDE_COMPLETE) },
            )
        }

        // I · 완료 · 평가 · 기록 33–35
        composable(Routes.RIDE_COMPLETE) {
            RideCompleteScreen(
                onSubmit = { _, _ -> resetTo(Routes.HOME) },
                onSkip = { resetTo(Routes.HOME) },
            )
        }
        composable(Routes.MY_RIDES) {
            MyRidesScreen(
                onRideClick = { navController.navigate(Routes.RIDE_DETAIL) },
                onLiveLocationClick = { navController.navigate(Routes.RIDE_ONGOING) },
                onHistoryClick = { navController.navigate(Routes.MYPAGE) },
                onTabSelect = ::navigateTab,
            )
        }
        composable(Routes.MYPAGE) {
            MyPageScreen(
                userName = userName,
                onRideHistoryClick = { navController.navigate(Routes.MY_RIDES) },
                // logout() 은 실패하지 않는다 — 상태 전이는 위 LaunchedEffect 가 받아 04a 로 보낸다.
                // logoutRequested 를 먼저 세워 두면 그 전이를 세션 만료로 오해하지 않는다.
                onLogout = {
                    logoutRequested = true
                    scope.launch { authRepository.logout() }
                },
                onTabSelect = ::navigateTab,
            )
        }
    }
}
