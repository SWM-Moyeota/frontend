package com.moyeota.presentation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.moyeota.core.designsystem.component.MoyeotaTab
import com.moyeota.domain.repository.RideRepository
import com.moyeota.presentation.feature.auth.AccountType
import com.moyeota.presentation.feature.auth.AccountTypeScreen
import com.moyeota.presentation.feature.auth.EmailCodeScreen
import com.moyeota.presentation.feature.auth.IdentityVerifyScreen
import com.moyeota.presentation.feature.auth.LoginScreen
import com.moyeota.presentation.feature.auth.MannerPledgeScreen
import com.moyeota.presentation.feature.auth.ProfileSetupScreen
import com.moyeota.presentation.feature.auth.SafetySettingsScreen
import com.moyeota.presentation.feature.auth.SchoolEmailScreen
import com.moyeota.presentation.feature.auth.SignupCompleteScreen
import com.moyeota.presentation.feature.auth.WorkVerifyScreen
import com.moyeota.presentation.feature.chat.ChatScreen
import com.moyeota.presentation.feature.chat.EmergencyScreen
import com.moyeota.presentation.feature.chat.RideOngoingScreen
import com.moyeota.presentation.feature.explore.ExploreRoute
import com.moyeota.presentation.feature.explore.JoinConfirmScreen
import com.moyeota.presentation.feature.home.DestinationConfirmModal
import com.moyeota.presentation.feature.home.DestinationScreen
import com.moyeota.presentation.feature.home.HomeScreen
import com.moyeota.presentation.feature.matching.DispatchStatusScreen
import com.moyeota.presentation.feature.matching.MatchWaitingScreen
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
import kotlinx.coroutines.delay

// v15 와이어프레임 35화면 이동 규칙을 한곳에서 배선한다.
// 각 화면은 콜백만 노출하는 순수 컴포저블 — 화면 안에는 네비게이션 코드가 없다.
@Composable
fun MainNavGraph(rideRepository: RideRepository) {
    val navController = rememberNavController()

    // 화면 사이에 넘겨야 하는 값 (백엔드 없는 와이어프레임 데모용 간이 상태)
    var schoolEmail by remember { mutableStateOf("moyeota@pusan.ac.kr") }
    var workVerifyIsWorker by remember { mutableStateOf(true) }
    var searchInitialDestination by remember { mutableStateOf("") }
    var confirmedDestination by remember { mutableStateOf("서면역 1번 출구") }
    var selectedPartyId by remember { mutableStateOf<Long?>(null) }

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
        navController.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.ONBOARDING_SAVING) {
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
                onAlreadyHaveAccount = { resetTo(Routes.HOME) },
                onSkip = { navController.navigate(Routes.LOGIN) },
            )
        }

        // B · 로그인 04–05
        composable(Routes.LOGIN) {
            LoginScreen(
                onKakaoStart = { navController.navigate(Routes.ACCOUNT_TYPE) },
                onEmailStart = { navController.navigate(Routes.ACCOUNT_TYPE) },
                onLogin = { resetTo(Routes.HOME) },
            )
        }
        composable(Routes.ACCOUNT_TYPE) {
            AccountTypeScreen(
                onBack = ::back,
                onNext = { type ->
                    when (type) {
                        AccountType.STUDENT -> navController.navigate(Routes.SCHOOL_EMAIL)
                        AccountType.WORKER -> {
                            workVerifyIsWorker = true
                            navController.navigate(Routes.WORK_VERIFY)
                        }
                        AccountType.GENERAL -> {
                            workVerifyIsWorker = false
                            navController.navigate(Routes.WORK_VERIFY)
                        }
                    }
                },
                onSkipVerification = { resetTo(Routes.HOME) },
            )
        }

        // C · 인증 · 가입 06–13
        composable(Routes.SCHOOL_EMAIL) {
            SchoolEmailScreen(
                onBack = ::back,
                onSendMail = { email ->
                    schoolEmail = email
                    navController.navigate(Routes.EMAIL_CODE)
                },
            )
        }
        composable(Routes.EMAIL_CODE) {
            EmailCodeScreen(
                email = schoolEmail,
                onBack = ::back,
                onVerified = { navController.navigate(Routes.IDENTITY_VERIFY) },
                onEditEmail = ::back,
            )
        }
        composable(Routes.WORK_VERIFY) {
            WorkVerifyScreen(
                isWorker = workVerifyIsWorker,
                onBack = ::back,
                onSubmit = { navController.navigate(Routes.IDENTITY_VERIFY) },
            )
        }
        composable(Routes.IDENTITY_VERIFY) {
            IdentityVerifyScreen(
                onBack = ::back,
                onRequestCode = { _, _, _ -> navController.navigate(Routes.PROFILE_SETUP) },
            )
        }
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onBack = ::back,
                onNext = { navController.navigate(Routes.SAFETY_SETTINGS) },
            )
        }
        composable(Routes.SAFETY_SETTINGS) {
            SafetySettingsScreen(
                onBack = ::back,
                onContinue = { _, _ -> navController.navigate(Routes.MANNER_PLEDGE) },
            )
        }
        composable(Routes.MANNER_PLEDGE) {
            MannerPledgeScreen(
                onBack = ::back,
                onComplete = { navController.navigate(Routes.SIGNUP_COMPLETE) },
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
            HomeScreen(
                onSearchClick = {
                    searchInitialDestination = ""
                    navController.navigate(Routes.DESTINATION)
                },
                onFavoritePlaceClick = { place ->
                    searchInitialDestination = place.address
                    navController.navigate(Routes.DESTINATION)
                },
                onRecentPlaceClick = { place ->
                    searchInitialDestination = place.name
                    navController.navigate(Routes.DESTINATION)
                },
                onDemandBannerClick = { navigateTab(MoyeotaTab.EXPLORE) },
                onTabSelect = ::navigateTab,
            )
        }
        composable(Routes.DESTINATION) {
            DestinationScreen(
                initialDestination = searchInitialDestination,
                onBack = ::back,
                onConfirmRoute = { destination ->
                    confirmedDestination = destination
                    navController.navigate(Routes.DESTINATION_CONFIRM)
                },
            )
        }
        // 16 은 15 위에 뜨는 모달 — 스크림 탭·닫기로만 닫힌다
        dialog(
            Routes.DESTINATION_CONFIRM,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DestinationConfirmModal(
                destinationName = confirmedDestination,
                onDismiss = ::back,
                onFindCompanions = { navController.navigate(Routes.MATCH_WAITING) },
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
            JoinConfirmScreen(
                onDismiss = ::back,
                onConfirmJoin = { navController.navigate(Routes.RIDE_DETAIL) },
                onMemberClick = { navController.navigate(Routes.PARTNER_PROFILE) },
            )
        }

        // F · 매칭 · 탑승 21–23 · 25
        composable(Routes.MATCH_WAITING) {
            MatchWaitingScreen(
                onCancelSearch = { navigateTab(MoyeotaTab.HOME) },
                onCardClick = { navController.navigate(Routes.RIDE_DETAIL) },
            )
        }
        composable(Routes.RIDE_DETAIL) {
            RideDetailRoute(
                repository = rideRepository,
                partyId = selectedPartyId,
                onBack = ::back,
                onPartnerClick = { navController.navigate(Routes.PARTNER_PROFILE) },
                onLeave = { navigateTab(MoyeotaTab.HOME) },
                onDepart = { navController.navigate(Routes.DISPATCH_STATUS) },
            )
        }
        composable(Routes.PARTNER_PROFILE) {
            PartnerProfileScreen(
                onBack = ::back,
                onChatClick = { navController.navigate(Routes.CHAT) },
            )
        }
        composable(Routes.DISPATCH_STATUS) {
            DispatchStatusScreen(
                onStartRide = { resetTo(Routes.RIDE_ONGOING) },
            )
        }

        // G · 채팅 · 안심 24 · 26 · 27
        composable(Routes.CHAT) {
            ChatScreen(
                onBack = ::back,
                onOpenRideOngoing = { navController.navigate(Routes.RIDE_ONGOING) },
                onStartLocationShare = { navController.navigate(Routes.RIDE_ONGOING) },
                onLeaveChat = { navigateTab(MoyeotaTab.HOME) },
                onTabSelect = ::navigateTab,
            )
        }
        composable(Routes.RIDE_ONGOING) {
            // 디스크립션의 "도착 후 28 자동 전환"을 데모용 15초 타이머로 재현
            LaunchedEffect(Unit) {
                delay(15_000)
                navController.navigate(Routes.FARE_FINAL)
            }
            RideOngoingScreen(
                onBack = ::back,
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onReport = { navController.navigate(Routes.EMERGENCY) },
            )
        }
        composable(Routes.EMERGENCY) {
            EmergencyScreen(
                onBack = ::back,
                onReportSubmitted = { _, _ -> back() },
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
                onRideHistoryClick = { navController.navigate(Routes.MY_RIDES) },
                onTabSelect = ::navigateTab,
            )
        }
    }
}
