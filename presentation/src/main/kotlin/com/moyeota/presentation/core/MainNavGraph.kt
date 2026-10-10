package com.moyeota.presentation.core

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.model.AuthState
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.User
import com.moyeota.domain.repository.ActivePartyRepository
import com.moyeota.domain.repository.AppConfigRepository
import com.moyeota.domain.repository.AuthRepository
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.DispatchRepository
import com.moyeota.domain.repository.PlaceRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.feature.auth.LoginFormRoute
import com.moyeota.presentation.feature.auth.LoginScreen
import com.moyeota.presentation.feature.auth.MannerPledgeRoute
import com.moyeota.presentation.feature.auth.ProfileSetupRoute
import com.moyeota.presentation.feature.auth.SignupCompleteScreen
import com.moyeota.presentation.feature.auth.SignupDraft
import com.moyeota.presentation.feature.chat.ChatRoomDestinationRoute
import com.moyeota.presentation.feature.chat.ChatRoute
import com.moyeota.presentation.feature.chat.EmergencyRoute
import com.moyeota.presentation.feature.chat.RideOngoingRoute
import com.moyeota.presentation.feature.explore.ExploreRoute
import com.moyeota.presentation.feature.explore.JoinConfirmRoute
import com.moyeota.presentation.feature.home.DestinationConfirmRoute
import com.moyeota.presentation.feature.home.DestinationRoute
import com.moyeota.presentation.feature.home.HomeRoute
import com.moyeota.presentation.feature.matching.DispatchStatusRoute
import com.moyeota.presentation.feature.matching.MatchWaitingRoute
import com.moyeota.presentation.feature.matching.PartnerProfileScreen
import com.moyeota.presentation.feature.matching.RideDetailRoute
import com.moyeota.presentation.feature.mypage.MyPageScreen
import com.moyeota.presentation.feature.mypage.ModeDebugOptions
import com.moyeota.presentation.feature.mypage.ProfileEditRoute
import com.moyeota.presentation.feature.mypage.RideCompleteScreen
import com.moyeota.presentation.feature.onboarding.OnboardingSafetyScreen
import com.moyeota.presentation.feature.onboarding.OnboardingSavingScreen
import com.moyeota.presentation.feature.onboarding.OnboardingTrustScreen
import kotlinx.coroutines.delay
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
    appConfigRepository: AppConfigRepository,
    rideRepository: RideRepository,
    placeRepository: PlaceRepository,
    chatRepository: ChatRepository,
    dispatchRepository: DispatchRepository,
    activePartyRepository: ActivePartyRepository,
    /**
     * 채팅 알림 탭으로 열어 달라는 방. 로그인돼 있고 그래프가 준비되면 그 채팅방을 **스택에 쌓고**
     * [onPendingChatRoomHandled] 로 소비를 알린다. 미로그인이면 무시한다(로그인 뒤 엉뚱한 방을 여는 것보다 낫다).
     */
    pendingChatRoomId: Long? = null,
    onPendingChatRoomHandled: () -> Unit = {},
) {
    val authState by authRepository.authState.collectAsState()

    if (authState is AuthState.Unknown) {
        SplashScreen()
        return
    }

    MainNavHost(
        loggedIn = authState is AuthState.Authenticated,
        authRepository = authRepository,
        appConfigRepository = appConfigRepository,
        rideRepository = rideRepository,
        placeRepository = placeRepository,
        chatRepository = chatRepository,
        dispatchRepository = dispatchRepository,
        activePartyRepository = activePartyRepository,
        pendingChatRoomId = pendingChatRoomId,
        onPendingChatRoomHandled = onPendingChatRoomHandled,
    )
}

// Routes.CHAT_ROOM 의 경로 인자 이름. 라우트 문자열과 navArgument 선언이 어긋나면
// 런타임에야 드러나므로 한 곳에서만 적는다.
private const val CHAT_ROOM_ID_ARG = "roomId"

// 하단탭 네 루트 — 탭 전환이 saveState 로 상태를 보관하는 대상이자, 로그아웃 때 그 보관분을 지울 대상.
private val TabRoutes = listOf(Routes.HOME, Routes.EXPLORE, Routes.CHAT, Routes.MYPAGE)

// v15 와이어프레임 35화면 이동 규칙을 한곳에서 배선한다.
// 각 화면은 콜백만 노출하는 순수 컴포저블 — 화면 안에는 네비게이션 코드가 없다.
@Composable
private fun MainNavHost(
    loggedIn: Boolean,
    authRepository: AuthRepository,
    appConfigRepository: AppConfigRepository,
    rideRepository: RideRepository,
    placeRepository: PlaceRepository,
    chatRepository: ChatRepository,
    dispatchRepository: DispatchRepository,
    activePartyRepository: ActivePartyRepository,
    pendingChatRoomId: Long? = null,
    onPendingChatRoomHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // 로그인 사용자의 이름 — NavHost 바깥(액티비티 스코프)에 두어 홈·마이가 한 인스턴스를 공유한다.
    // 화면별 ViewModel 로 두면 탭을 오갈 때마다 같은 조회가 반복된다.
    val profileViewModel: UserProfileViewModel =
        viewModel(factory = UserProfileViewModel.factory(authRepository))
    val userName by profileViewModel.userName.collectAsState()

    // 서버 운영 설정(동승/택시 모드). 앱 시작 시 받아 21 대기 화면이 「합승 완료」 / 기사 대기를 고른다.
    // 못 받으면 동승 모드(실배포)다. 모드별 화면 표는 docs/MODE-ROUTES.md.
    val appConfigViewModel: AppConfigViewModel =
        viewModel(factory = AppConfigViewModel.factory(appConfigRepository))
    val appConfig by appConfigViewModel.config.collectAsState()
    val serverAppConfig by appConfigViewModel.serverConfig.collectAsState()
    val appConfigResolved by appConfigViewModel.resolved.collectAsState()
    val appModeOverride by appConfigViewModel.debugOverride.collectAsState()
    // 개발자 옵션(모드 강제)은 디버그 빌드에서만 그린다 — presentation 모듈엔 BuildConfig 가 없어 플래그로 본다
    val debuggable = (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // 「지금 내가 타고 있는 방」 — 홈·합승 배너, 앱 시작 시 단계 복귀가 같은 값을 본다.
    // profileViewModel 과 같은 이유로 NavHost 바깥에 둔다(탭을 옮길 때마다 재조회하지 않게).
    val activePartyViewModel: ActivePartyViewModel = viewModel(
        factory = ActivePartyViewModel.factory(activePartyRepository, chatRepository),
    )
    val activeRide by activePartyViewModel.ride.collectAsState()
    val activeChatRoomId by activePartyViewModel.chatRoomId.collectAsState()
    val activeResolved by activePartyViewModel.resolvedOnce.collectAsState()

    // 합류 시점에 생기는 채팅방을 뒤늦게라도 잡는다(21·25 의 「채팅 열기」가 이 값으로 뜬다).
    // 컴포지션 스코프라 화면이 사라지면 함께 멈춘다.
    LaunchedEffect(activePartyViewModel) { activePartyViewModel.pollChatRoom() }

    // 앱을 다시 앞으로 가져올 때마다 재조회한다 — 배경에 있는 동안 방이 끝났을 수 있다.
    // 탭 진입마다의 재조회는 각 화면(홈·합승)의 LaunchedEffect 가 따로 건다.
    LifecycleResumeEffect(Unit) {
        activePartyViewModel.refresh()
        // 시작 시 설정 조회가 실패했으면(서버 장애 등) 복귀할 때 다시 받는다 — 그동안은 동승 모드 기본값
        appConfigViewModel.loadIfUnresolved()
        onPauseOrDispose { }
    }

    // 화면 사이에 넘겨야 하는 값 (백엔드 없는 와이어프레임 데모용 간이 상태)
    // 10 프로필 만들기가 채우는 가입 정보 — 12 매너 서약에서 제출한다
    var signupDraft by remember { mutableStateOf(SignupDraft()) }
    // 로그인 화면으로 돌아온 이유. 세션 만료일 때만 채워지고 사용자가 스스로 로그아웃하면 비운다.
    var signOutNotice by remember { mutableStateOf<String?>(null) }
    var logoutRequested by remember { mutableStateOf(false) }
    var searchInitialQuery by remember { mutableStateOf("") }
    // 15 에서 고른 출발지·도착지(좌표 포함) — 16 방 생성에 필요하다.
    // 15 는 출발지(고른 곳 또는 실위치)가 있어야만 16 으로 넘긴다. 15 를 거치지 않은 복원 진입이면 null — 16 이 안내만 한다.
    var confirmedOrigin by remember { mutableStateOf<Place?>(null) }
    var confirmedDestination by remember { mutableStateOf<Place?>(null) }
    // 26 에서 방금 끝난 방 — 33 도착 완료 요약용. 전이 직전 clearParty() 가 activeRide 를 비우므로 따로 잡아 둔다
    var finishedRide by remember { mutableStateOf<Ride?>(null) }
    var selectedPartyId by remember { mutableStateOf<Long?>(null) }
    // 20·22 에서 탭한 동승자 — 23 프로필이 이 값을 그린다.
    // 라우트 인자로 넘기지 않는 이유: User 는 방 상세 응답의 일부라 id 만 넘기면 23 이 방을 다시
    // 조회해야 하는데, 그 조회 API 는 "멤버 한 명"이 아니라 방 전체다.
    var selectedPartner by remember { mutableStateOf<User?>(null) }
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

    // 로그아웃·세션 만료 전용 초기화.
    // popUpTo(0) 은 "지금 스택"만 지운다 — 탭 전환이 saveState=true 로 따로 보관해 둔 엔트리는
    // ViewModelStore 째 살아남아, 새 계정으로 그 탭에 들어가면 이전 계정의 ViewModel 이 복원된다.
    // 채팅에서는 그게 이전 방을 향한 3초 폴링으로 나타났다(QA 결함-1: 새 토큰으로 403 반복).
    // 저장분까지 명시적으로 폐기해 계정 전환 뒤에는 어떤 탭도 이전 상태를 되살리지 않게 한다.
    fun resetAfterSignOut(route: String) {
        resetTo(route)
        TabRoutes.forEach { navController.clearBackStack(it) }
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

    /**
     * 진행 중인 방의 **지금 단계 화면**으로 보낸다. 배너·채팅방 헤더·앱 시작 복귀가 모두 여기로 모인다.
     *
     * | [Ride] | 목적지 |
     * |---|---|
     * | RECRUITING · MATCHED(서버 COMPLETED = 정원 충족) | 21 매칭 대기 |
     * | DISPATCHING (기사 미배정) | 25b — [Routes.DISPATCH_STATUS] 안에서 갈린다 |
     * | DISPATCHING (기사 배정) | 25c·25 — 같은 라우트 |
     * | ONGOING | 26 운행 중 |
     * | 그 외(끝난 방) | 14 홈 |
     *
     * 스택은 **홈 위에 단계 화면 하나**로 만든다(resetTo 로 통째로 비우지 않는다) — 단계 화면의
     * 뒤로가기가 갈 곳이 있어야 하고, 홈은 언제나 스택 바닥이라 popUpTo 로 나머지만 걷어내면 된다.
     */
    fun navigateToStage(ride: Ride) {
        ride.id.toLongOrNull()?.let { id ->
            createdPartyId = id
            activePartyId = id
        }
        // 동승 모드인데 배차·운행 단계가 왔다 = 서버는 택시 모드, 앱 설정이 낡았다(조회 실패·운영 중 전환).
        // 화면은 status 를 따라 25·26 으로 가고, 설정만 다시 읽어 문구를 맞춘다(docs/MODE-ROUTES.md).
        if (isStageOutsideMode(ride.activeStage, appConfig.taxiEnabled)) {
            Log.w("AppMode", "동승 모드에 ${ride.activeStage} 단계 — 서버 설정 재조회")
            appConfigViewModel.load()
        }
        val route = when (ride.activeStage) {
            ActiveStage.WAITING -> Routes.MATCH_WAITING
            // 25b/25c/25 는 한 라우트가 서버 status 로 갈아 끼운다(DispatchStatusRoute)
            ActiveStage.DRIVER_SEARCH, ActiveStage.DRIVER_COMING -> Routes.DISPATCH_STATUS
            ActiveStage.ONGOING -> Routes.RIDE_ONGOING
            // 끝난 방을 가리키는 배너가 남아 있을 수 있다 — 없는 단계로 보내는 대신 홈으로
            ActiveStage.NONE -> null
        }
        if (route == null) {
            activePartyViewModel.clearParty()
            navigateTab(MoyeotaTab.HOME)
            return
        }
        if (navController.currentDestination?.route == route) return
        // 그 단계 화면이 이미 스택에 있으면(채팅방을 쌓아 연 상태 등) **되돌아간다**.
        // 새로 쌓으면 같은 화면이 두 겹이 되고, 뒤로가기가 자기 자신으로 돌아가는 길이 생긴다.
        if (navController.popBackStack(route, inclusive = false)) return
        navController.navigate(route) {
            // 홈은 스택 바닥이라 여기까지만 걷어내면 「홈 위에 단계 화면 하나」가 된다.
            // 26 처럼 스택을 비우고 진입한 화면에서는 홈이 없어 아무것도 걷어내지 않는다(그대로 쌓인다).
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    /**
     * 진행 화면(21·25·26)의 「채팅 열기」. 채팅 **탭**이 아니라 채팅방을 **쌓아** 연다 —
     * 탭으로 보내면 매칭 화면이 스택에서 빠져 돌아올 길이 없어진다(이 작업의 출발점이 된 결함).
     */
    fun openActiveChatRoom(roomId: Long) {
        navController.navigate(Routes.chatRoom(roomId))
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
            // 로컬 기억은 남긴다(계정에 매인 값이라 같은 계정 재로그인 시 되살아나야 한다) —
            // 화면이 들고 있던 값만 버린다
            activePartyViewModel.forget()
            resetAfterSignOut(Routes.LOGIN_FORM)
        }
        wasLoggedIn = loggedIn
    }

    /**
     * 앱을 껐다 켜도 매칭 화면으로 돌아온다 — 첫 [ActivePartyViewModel.resolve] 가 끝난 뒤 딱 한 번.
     *
     * [ActivePartyViewModel.resolvedOnce] 를 기다리는 이유는 「아직 모른다」와 「없다」가 둘 다
     * null 이기 때문이다. 이미 사용자가 홈을 떠났으면 끼어들지 않는다 — 켜자마자 목적지를 검색하는
     * 사람의 화면을 빼앗는 것이 복귀보다 나을 리 없다.
     */
    var startupStageHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activeResolved, activeRide) {
        if (!loggedIn || startupStageHandled || !activeResolved) return@LaunchedEffect
        // resolve 가 I/O 를 거치는 동안 NavHost 는 이미 그래프를 세웠을 것이다. 그래도 아주 빠른
        // 응답(캐시된 실패 등)이 같은 프레임에 닿으면 목적지가 아직 없어 navigate 가 던진다.
        if (navController.currentDestination == null) delay(100)
        startupStageHandled = true
        val ride = activeRide ?: return@LaunchedEffect
        if (navController.currentDestination?.route != Routes.HOME) return@LaunchedEffect
        navigateToStage(ride)
    }

    // 채팅 알림 탭 → 그 채팅방. 현재 화면 위에 **쌓는다**(뒤로가기로 원래 자리 복귀). 이미 그 방이
    // 맨 위면 다시 쌓지 않는다. 미로그인이면 소비만 하고 지나간다 — 로그인 화면을 채팅방으로 덮을 순 없다.
    LaunchedEffect(pendingChatRoomId, loggedIn) {
        val roomId = pendingChatRoomId ?: return@LaunchedEffect
        if (!loggedIn) {
            onPendingChatRoomHandled()
            return@LaunchedEffect
        }
        if (navController.currentDestination == null) delay(100)
        val target = Routes.chatRoom(roomId)
        val onTop = navController.currentBackStackEntry?.let { entry ->
            entry.destination.route == Routes.CHAT_ROOM && entry.arguments?.getLong(CHAT_ROOM_ID_ARG) == roomId
        } ?: false
        if (!onTop) navController.navigate(target)
        onPendingChatRoomHandled()
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
                // 「이메일로 시작하기」 = 가입 시작 → 10 프로필 설정. 소셜(카카오) 로그인은 백엔드에 없어 제공하지 않는다
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

        // C · 가입 10 · 12 · 13 (2단계 — 11 안심 설정은 삭제)
        //
        // 05 계정 유형 · 06 학교 이메일 · 07 인증 코드 · 08 재직 인증 · 09 본인 인증은 여기 없다.
        // 가입 API 에 계정 유형이 없고, 학교·재직 인증은 마이페이지에서 나중에 붙이는 컨셉이며,
        // 휴대폰 본인 인증은 MVP 범위 밖이다 — 필수 경로에 두면 인증 없이 값만 묻거나
        // 서버로 가지도 않는 값을 묻는 화면이 된다(Routes.kt B' 절 참고).
        // 09 가 받던 실명·생년월일·성별·휴대폰은 10 의 「기본 정보」 절이 이어받았다.
        composable(Routes.PROFILE_SETUP) {
            // Route 인 이유는 닉네임 중복 확인(POST /auth/nickname/check) 하나 때문이다 —
            // 입력값 자체는 여전히 화면이 들고 있다.
            ProfileSetupRoute(
                repository = authRepository,
                onBack = ::back,
                onNext = { info ->
                    // 닉네임을 포함한 서버 가입 8개 값이 이 화면에서 한 번에 채워진다
                    signupDraft = info
                    navController.navigate(Routes.MANNER_PLEDGE)
                },
            )
        }
        composable(Routes.MANNER_PLEDGE) {
            // 여기서 회원가입 + 자동 로그인이 실행된다. 성공했을 때만 13 으로 넘어간다.
            MannerPledgeRoute(
                repository = authRepository,
                draft = signupDraft,
                onBack = ::back,
                onCompleted = { navController.navigate(Routes.SIGNUP_COMPLETE) },
                // 409 닉네임 중복 — 10 으로 되돌린다. 스택에 남아 있는 그 엔트리로 pop 하므로
                // 이미 채운 나머지 7개 필드는 그대로 있고 닉네임만 고치면 된다.
                onEditNickname = { navController.popBackStack(Routes.PROFILE_SETUP, inclusive = false) },
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
            // 탭으로 돌아올 때마다 진행 중 방을 다시 읽는다 — 배너가 끝난 방을 가리키면 안 된다
            LaunchedEffect(Unit) { activePartyViewModel.refresh() }
            HomeRoute(
                repository = placeRepository,
                userName = (userName as? UserNameState.Resolved)?.name,
                activeRide = activeRide,
                onActiveRideClick = { activeRide?.let(::navigateToStage) },
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
                origin = confirmedOrigin,
                destination = confirmedDestination,
                onDismiss = ::back,
                onPartyCreated = { ride ->
                    createdPartyId = ride.id.toLongOrNull()
                    activePartyId = createdPartyId
                    // 진행 중 방으로 기억한다 — 앱을 껐다 켜거나 탭을 옮겨도 여기로 돌아올 근거
                    activePartyViewModel.rememberParty(ride.id)
                    // 모달을 닫고 21 매칭 대기로 — 뒤로 눌러 모달로 돌아오지 않게 한다
                    navController.navigate(Routes.MATCH_WAITING) {
                        popUpTo(Routes.DESTINATION_CONFIRM) { inclusive = true }
                    }
                },
            )
        }

        // E · 합승 탐색 17–20 (17·18·19 는 한 화면의 시트 상태)
        composable(Routes.EXPLORE) {
            LaunchedEffect(Unit) { activePartyViewModel.refresh() }
            ExploreRoute(
                repository = rideRepository,
                activeRide = activeRide,
                onJoinParty = { ride ->
                    selectedPartyId = ride.id.toLongOrNull()
                    navController.navigate(Routes.JOIN_CONFIRM)
                },
                // 배너는 **지금 단계 화면**으로 바로 간다
                onOngoingRideClick = { activeRide?.let(::navigateToStage) },
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
                    activePartyViewModel.rememberParty(ride.id)
                    navController.navigate(Routes.MATCH_WAITING) {
                        popUpTo(Routes.JOIN_CONFIRM) { inclusive = true }
                    }
                },
                onMemberClick = { member ->
                    selectedPartner = member
                    navController.navigate(Routes.PARTNER_PROFILE)
                },
            )
        }

        // F · 매칭 · 탑승 21–23 · 25
        composable(Routes.MATCH_WAITING) {
            MatchWaitingRoute(
                repository = rideRepository,
                partyId = createdPartyId,
                taxiEnabled = appConfig.taxiEnabled,
                // 나가기 성공 후 14 홈 — 방에서 빠졌으니 진행 중 기억도 함께 지운다
                onCancelSearch = {
                    activePartyViewModel.clearParty()
                    navigateTab(MoyeotaTab.HOME)
                },
                // 방이 닫혔다(내·남의 「합승 완료」, 30분 스윕). 끝난 방이라 진행 중 기억을 지우고 홈으로.
                onPartyClosed = {
                    activePartyViewModel.clearParty()
                    navigateTab(MoyeotaTab.HOME)
                },
                // 매칭이 시작되면 서버가 나가기를 막는다(ensureRecruiting). 그 단계의 뒤로가기는
                // 방을 그대로 두고 홈으로만 보낸다 — 방은 25b 로 이어진다.
                onExitKeepingParty = { navigateTab(MoyeotaTab.HOME) },
                onCardClick = {
                    selectedPartyId = createdPartyId
                    navController.navigate(Routes.RIDE_DETAIL)
                },
                // 정원이 차면 서버가 스스로 기사 매칭을 시작한다 — 앱은 status 전이를 받아 넘어갈 뿐이다.
                // 대기 화면은 스택에서 지운다. 남겨두면 뒤로 돌아왔을 때 status 가 여전히 배차 상태라
                // 다시 앞으로 튕겨나가는 루프가 된다(공통 규칙: 배차 후 되돌리기 차단).
                onMatchingStarted = {
                    activePartyId = createdPartyId
                    // 동승 모드에선 올 수 없는 전이다 — 왔다면 서버가 택시 모드이니 설정을 다시 읽는다
                    if (!appConfig.taxiEnabled) {
                        Log.w("AppMode", "동승 모드에 배차 전이 — 서버 설정 재조회")
                        appConfigViewModel.load()
                    }
                    // 정원이 찼다 = 채팅방이 생기는 시점. 단계와 채팅방 id 를 같이 다시 읽는다
                    activePartyViewModel.refresh()
                    navController.navigate(Routes.DISPATCH_STATUS) {
                        popUpTo(Routes.MATCH_WAITING) { inclusive = true }
                    }
                },
                onOpenChat = activeChatRoomId?.let { roomId -> { openActiveChatRoom(roomId) } },
            )
        }
        composable(Routes.RIDE_DETAIL) {
            RideDetailRoute(
                repository = rideRepository,
                partyId = selectedPartyId,
                onBack = ::back,
                onPartnerClick = { partner ->
                    selectedPartner = partner
                    navController.navigate(Routes.PARTNER_PROFILE)
                },
                // 25 배차 현황으로 넘기는 건 status 전이를 관찰하는 21 매칭 대기뿐이다 —
                // 자동 기사 매칭 전환으로 이 화면에는 수동 출발 CTA 가 없다.
                onLeave = { navigateTab(MoyeotaTab.HOME) },
            )
        }
        composable(Routes.PARTNER_PROFILE) {
            // 20·22 를 거치지 않고 이 라우트에 닿을 길은 없다. 그래도 null 이면 데모 프로필을
            // 그리는 대신 사실대로 말하고 되돌린다 — 존재하지 않는 사람을 보여주는 것보다 낫다.
            val partner = selectedPartner
            if (partner == null) {
                BackStateScaffold("프로필", ::back) {
                    ErrorBox(message = "동승자 정보를 불러오지 못했어요", onRetry = ::back)
                }
            } else {
                PartnerProfileScreen(
                    user = partner,
                    onBack = ::back,
                    onChatClick = { navController.navigate(Routes.CHAT) },
                )
            }
        }
        composable(Routes.DISPATCH_STATUS) {
            DispatchStatusRoute(
                rideRepository = rideRepository,
                dispatchRepository = dispatchRepository,
                partyId = activePartyId,
                onStartRide = {
                    activePartyViewModel.refresh()
                    resetTo(Routes.RIDE_ONGOING)
                },
                // 매칭 3분 타임아웃 — 서버가 방을 취소해 되살릴 대상이 없다. 같은 조건으로
                // 다시 만들려면 14 홈부터 시작해야 하므로 진행 중 방 id 도 함께 비운다.
                onRetryMatching = {
                    activePartyId = null
                    createdPartyId = null
                    // 서버가 방을 CANCELED 로 닫았다 — 되살릴 대상이 없으니 기억도 지운다
                    activePartyViewModel.clearParty()
                    navigateTab(MoyeotaTab.HOME)
                },
                // 배차 단계에는 나가기가 없다(서버가 막는다). 뒤로가기는 방을 유지한 채 홈으로만 —
                // 스택을 되돌리면 이미 지운 21 자리로 떨어져 상태와 화면이 어긋난다.
                onBack = { navigateTab(MoyeotaTab.HOME) },
                onOpenChat = activeChatRoomId?.let { roomId -> { openActiveChatRoom(roomId) } },
            )
        }

        // G · 채팅 · 안심 24 · 26 · 27
        composable(Routes.CHAT) {
            ChatRoute(
                repository = chatRepository,
                // 열린 방이 진행 중인 그 방이면 헤더에 「매칭 화면으로 →」가 뜬다
                activePartyId = activeRide?.id,
                onOpenMatching = { activeRide?.let(::navigateToStage) },
                onOpenRideOngoing = { navController.navigate(Routes.RIDE_ONGOING) },
                onLeaveChat = {
                    // 나간 방 id 를 진행 화면이 들고 있으면 「채팅 열기」가 죽은 방으로 간다 — 캐시를 버린다
                    activePartyViewModel.forgetChatRoom()
                    navigateTab(MoyeotaTab.HOME)
                },
                onNotParticipant = activePartyViewModel::forgetChatRoom,
                onTabSelect = ::navigateTab,
            )
        }
        // 24 채팅방 **단독 목적지** — 21·25·26 의 「채팅 열기」가 이 위에 쌓인다.
        // 뒤로가기 한 번이면 원래 진행 화면으로 돌아온다(탭 이동은 스택을 갈아엎어 그럴 수 없었다).
        composable(
            Routes.CHAT_ROOM,
            arguments = listOf(navArgument(CHAT_ROOM_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            val roomId = entry.arguments?.getLong(CHAT_ROOM_ID_ARG)
            if (roomId == null) {
                // 인자 없이 이 라우트에 닿을 길은 없다. 그래도 빈 채팅방을 그리는 대신 되돌린다
                BackStateScaffold("채팅", ::back) {
                    ErrorBox(message = "채팅방을 찾지 못했어요", onRetry = ::back)
                }
            } else {
                ChatRoomDestinationRoute(
                    repository = chatRepository,
                    roomId = roomId,
                    activePartyId = activeRide?.id,
                    onBack = ::back,
                    onOpenMatching = { activeRide?.let(::navigateToStage) },
                    onOpenRideOngoing = { navController.navigate(Routes.RIDE_ONGOING) },
                    onLeaveChat = {
                    // 나간 방 id 를 진행 화면이 들고 있으면 「채팅 열기」가 죽은 방으로 간다 — 캐시를 버린다
                    activePartyViewModel.forgetChatRoom()
                    navigateTab(MoyeotaTab.HOME)
                },
                onNotParticipant = activePartyViewModel::forgetChatRoom,
                    onTabSelect = ::navigateTab,
                )
            }
        }
        composable(Routes.RIDE_ONGOING) {
            // 33 으로의 전이는 기사측 운행 종료(FINISHED)를 폴링으로 잡아 자동으로 넘어간다.
            // 임시 수동 트리거였던 경유 카드 탭은 서버가 IN_RIDE→FINISHED 를 실제로 저장하게 되면서(D-2 해소) 걷어냈다.
            // 데모용 15초 자동 전환 타이머도 이전에 제거된 상태다 — 26 은 27 신고 진입점이라
            // 사용자가 머무는 동안 화면이 제멋대로 바뀌면 안 된다(QA D-3).
            RideOngoingRoute(
                repository = rideRepository,
                partyId = activePartyId,
                onBack = ::back,
                // 채팅방을 알면 쌓아 열어 뒤로가기로 26 에 돌아오게 한다.
                // 아직 못 찾았으면(목록 조회 실패 등) 예전처럼 채팅 탭으로 — 길을 아예 막지는 않는다
                onOpenChat = {
                    val roomId = activeChatRoomId
                    if (roomId != null) openActiveChatRoom(roomId) else navigateTab(MoyeotaTab.CHAT)
                },
                onReport = { navController.navigate(Routes.EMERGENCY) },
                // 운행이 끝나면 33 도착 완료로 직행하고 26 은 스택에서 지운다. 남겨두면 33 에서 뒤로 왔을 때
                // status 가 여전히 FINISHED 라 폴링이 다시 33 으로 튕겨내는 루프가 된다(21→25 와 같은 이유).
                onRideFinished = {
                    // 운행이 끝났다(서버 FINISHED) — 33 요약용으로 방을 잡아 둔 뒤
                    // 진행 중 기억을 지워 홈 배너가 따라 사라지게 한다
                    finishedRide = activeRide
                    activePartyViewModel.clearParty()
                    navController.navigate(Routes.RIDE_COMPLETE) {
                        popUpTo(Routes.RIDE_ONGOING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.EMERGENCY) {
            // 확정 흐름(3초 홀드 → 신고 저장 + 즉시 112 다이얼 → 복귀 후 통화 확인 → 26 복귀).
            // partyId 는 내 운행(activePartyId)이 우선 — 없으면 탐색/생성 값으로 폴백한다
            // (서버가 IN_RIDE 멤버가 아니면 REPORT_NOT_ALLOWED 로 거절하므로 안전하다).
            EmergencyRoute(
                repository = rideRepository,
                partyId = activePartyId ?: selectedPartyId ?: createdPartyId,
                // 27 「지금 타고 있는 차」 카드 — 진행 중 방이 없으면 카드를 숨긴다(번호판은 모델에 없다)
                rideSummary = activeRide?.let { "${it.origin} → ${it.destination}" },
                onBack = ::back,
                onReportSubmitted = ::back,
            )
        }

        // I · 완료 33 · 마이페이지 35 · 프로필 수정 36
        composable(Routes.RIDE_COMPLETE) {
            // 방금 끝난 방(finishedRide)이 있으면 그 값으로, 없으면(복원 진입 등) 해당 요소를 숨긴다
            val ride = finishedRide
            RideCompleteScreen(
                routeLabel = ride?.let { "${it.origin} → ${it.destination}" },
                paidAmount = ride?.farePerPerson?.takeIf { it > 0 },
                companionCount = ride?.members?.count { !it.isMe }?.takeIf { it > 0 },
                onDone = { resetTo(Routes.HOME) },
            )
        }
        composable(Routes.MYPAGE) {
            MyPageScreen(
                userName = userName,
                onEditProfile = { navController.navigate(Routes.PROFILE_EDIT) },
                // logout() 은 실패하지 않는다 — 상태 전이는 위 LaunchedEffect 가 받아 04a 로 보낸다.
                // logoutRequested 를 먼저 세워 두면 그 전이를 세션 만료로 오해하지 않는다.
                onLogout = {
                    logoutRequested = true
                    scope.launch { authRepository.logout() }
                },
                onTabSelect = ::navigateTab,
                // 디버그 빌드 전용 — 서버 모드와 무관하게 동승/택시 화면을 강제해 본다. 릴리스엔 없다.
                modeDebug = if (debuggable) {
                    ModeDebugOptions(
                        serverTaxiEnabled = serverAppConfig.taxiEnabled,
                        serverResolved = appConfigResolved,
                        override = appModeOverride,
                        onOverride = appConfigViewModel::setDebugOverride,
                    )
                } else {
                    null
                },
            )
        }
        composable(Routes.PROFILE_EDIT) {
            // 36 프로필 수정 — 35 와 같은 이름 홀더를 쓴다. 저장이 끝나면 그 홀더를 다시 조회해
            // 35 프로필 카드·14 홈 인사말이 새 닉네임으로 함께 바뀌게 한 뒤 되돌아간다
            // (화면마다 따로 들고 있으면 한쪽만 옛 이름으로 남는다).
            ProfileEditRoute(
                repository = authRepository,
                userName = userName,
                onBack = ::back,
                onSaved = {
                    profileViewModel.refresh()
                    back()
                },
            )
        }
    }
}
