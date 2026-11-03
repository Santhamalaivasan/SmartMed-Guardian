package com.smartmedguardian.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartmedguardian.ui.screens.analytics.AnalyticsScreen
import com.smartmedguardian.ui.screens.analytics.ConditionTrendScreen
import com.smartmedguardian.ui.screens.auth.LoginScreen
import com.smartmedguardian.ui.screens.auth.SignUpScreen
import com.smartmedguardian.ui.screens.camera.BarcodeScannerScreen
import com.smartmedguardian.ui.screens.camera.CameraScreen
import com.smartmedguardian.ui.screens.dashboard.DashboardScreen
import com.smartmedguardian.ui.screens.details.DetectionResultScreen
import com.smartmedguardian.ui.screens.details.MedicineDetailsScreen
import com.smartmedguardian.ui.screens.details.SafetyRecommendationsScreen
import com.smartmedguardian.ui.screens.history.HistoryScreen
import com.smartmedguardian.ui.screens.home.HomeScreen
import com.smartmedguardian.ui.screens.prescription.PrescriptionScreen
import com.smartmedguardian.ui.screens.schedule.ScheduleScreen
import com.smartmedguardian.ui.screens.settings.AllergyProfileScreen
import com.smartmedguardian.ui.screens.settings.SettingsScreen
import com.smartmedguardian.ui.screens.evaluation.MedicineEvaluationScreen
import com.smartmedguardian.ui.screens.splash.SplashScreen
import com.smartmedguardian.ui.screens.verification.VerificationScreen
import com.smartmedguardian.ui.screens.caregiver.CaregiverScreen
import com.smartmedguardian.ui.screens.caregiver.CaregiverDashboardScreen
import com.smartmedguardian.viewmodel.CameraViewModel
import com.smartmedguardian.viewmodel.HistoryViewModel
import com.smartmedguardian.viewmodel.PrescriptionViewModel
import com.smartmedguardian.viewmodel.AuthViewModel
import com.smartmedguardian.viewmodel.CameraUiState

@Composable
fun NavGraph(
    navController: NavHostController,
    openVerification: Boolean = false,
    openCaregiverDashboard: Boolean = false,
    patientId: String = "local",
    targetMedicine: String = "",
    targetSlot: String = "",
    targetTime: String = "",
    prescriptionId: Long = -1L
) {
    val startDest = when {
        openVerification -> Screen.Camera.route
        openCaregiverDashboard -> if (patientId.isBlank()) "caregiver_dashboard/local" else "caregiver_dashboard/$patientId"
        else -> Screen.Splash.route
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onSplashFinished = {
                val nextRoute = if (openVerification) Screen.Camera.route else Screen.Login.route
                navController.navigate(nextRoute) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    val nextRoute = if (openVerification) Screen.Camera.route else Screen.Dashboard.route
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) }
            )
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) }
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onScanClick = { navController.navigate(Screen.Camera.createRoute()) },
                onPrescriptionClick = { navController.navigate(Screen.PrescriptionList.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onAnalyticsClick = { navController.navigate(Screen.Analytics.route) },
                onCaregiverClick = { navController.navigate(Screen.Caregiver.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onEvaluationClick = { navController.navigate(Screen.Evaluation.route) }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onScanClick = { navController.navigate(Screen.Camera.createRoute()) },
                onBarcodeClick = { navController.navigate(Screen.BarcodeScanner.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onPrescriptionClick = { navController.navigate(Screen.PrescriptionList.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onScheduleClick = { navController.navigate(Screen.Schedule.route) }
            )
        }
        composable(
            route = Screen.Camera.route,
            arguments = listOf(
                navArgument("targetMedicine") { type = NavType.StringType; defaultValue = "" },
                navArgument("targetSlot") { type = NavType.StringType; defaultValue = "" },
                navArgument("targetTime") { type = NavType.StringType; defaultValue = "" },
                navArgument("prescriptionId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val rawNavMed = backStackEntry.arguments?.getString("targetMedicine") ?: ""
            val rawNavSlot = backStackEntry.arguments?.getString("targetSlot") ?: ""
            val rawNavTime = backStackEntry.arguments?.getString("targetTime") ?: ""
            val navPrescriptionId = backStackEntry.arguments?.getLong("prescriptionId") ?: -1L

            fun sanitizeNavArg(value: String): String {
                return if (value.isBlank() || value.startsWith("{")) "" else value
            }

            val navMed = sanitizeNavArg(rawNavMed)
            val navSlot = sanitizeNavArg(rawNavSlot)
            val navTime = sanitizeNavArg(rawNavTime)

            val med = navMed.ifBlank { targetMedicine }
            val slot = navSlot.ifBlank { targetSlot }
            val time = navTime.ifBlank { targetTime }
            val pId = if (navPrescriptionId != -1L) navPrescriptionId else prescriptionId

            val viewModel: CameraViewModel = hiltViewModel()
            CameraScreen(
                viewModel = viewModel,
                targetMedicine = med,
                targetSlot = slot,
                targetTime = time,
                prescriptionId = pId,
                onBackClick = { navController.popBackStack() },
                onResultReady = { 
                    navController.navigate(Screen.DetectionResult.route)
                }
            )
        }
        composable(Screen.DetectionResult.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Camera.route)
            }
            val viewModel: CameraViewModel = hiltViewModel(parentEntry)
            DetectionResultScreen(
                viewModel = viewModel,
                onBackClick = { 
                    viewModel.resetState()
                    navController.popBackStack() 
                },
                onDetailsClick = { 
                    val medicineName = if (viewModel.uiState.value is CameraUiState.Success) {
                        (viewModel.uiState.value as CameraUiState.Success).result.name
                    } else ""
                    navController.navigate("${Screen.MedicineDetails.route}/$medicineName") 
                },
                onVerifyClick = { navController.navigate(Screen.Verification.route + "/camera") }
            )
        }
        composable(Screen.BarcodeScanner.route) {
            val viewModel: CameraViewModel = hiltViewModel()
            BarcodeScannerScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onResultReady = { 
                    navController.navigate(Screen.Verification.route + "/barcode")
                }
            )
        }
        composable(
            route = Screen.Verification.route + "/{source}"
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source")
            val parentRoute = if (source == "camera") Screen.Camera.route else Screen.BarcodeScanner.route
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(parentRoute)
            }
            val viewModel: CameraViewModel = hiltViewModel(parentEntry)
            VerificationScreen(
                viewModel = viewModel,
                onBackClick = { 
                    viewModel.resetState()
                    navController.popBackStack() 
                },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onSafetyAdviceClick = { name, status ->
                    navController.navigate("${Screen.SafetyAdvice.route}/$name/$status")
                },
                onMedicineDetailsClick = { name ->
                    navController.navigate("${Screen.MedicineDetails.route}/$name")
                }
            )
        }
        composable(
            route = "${Screen.MedicineDetails.route}/{medicineName}",
            arguments = listOf(navArgument("medicineName") { type = NavType.StringType })
        ) { backStackEntry ->
            val medicineName = backStackEntry.arguments?.getString("medicineName")
            MedicineDetailsScreen(
                medicineName = medicineName,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = "${Screen.SafetyAdvice.route}/{medicineName}/{status}",
            arguments = listOf(
                navArgument("medicineName") { type = NavType.StringType },
                navArgument("status") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val medicineName = backStackEntry.arguments?.getString("medicineName")
            val status = backStackEntry.arguments?.getString("status")
            SafetyRecommendationsScreen(
                medicineName = medicineName,
                verificationStatus = status,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.PrescriptionList.route) {
            val viewModel: PrescriptionViewModel = hiltViewModel()
            PrescriptionScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.AddPrescription.route) {
            val viewModel: PrescriptionViewModel = hiltViewModel()
            PrescriptionScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            val viewModel: HistoryViewModel = hiltViewModel()
            HistoryScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Schedule.route) {
            val viewModel: PrescriptionViewModel = hiltViewModel()
            ScheduleScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onVerifyDose = { prescription, slot, timeStr ->
                    navController.navigate(Screen.Camera.createRoute(prescription.medicineName, slot, timeStr, prescription.id))
                }
            )
        }
        composable(Screen.AllergyProfile.route) {
            AllergyProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToConditionTrend = { navController.navigate(Screen.ConditionTrend.route) }
            )
        }
        composable(Screen.ConditionTrend.route) {
            ConditionTrendScreen(onBackClick = { navController.popBackStack() })
        }
        composable(Screen.Caregiver.route) {
            CaregiverScreen(
                onBackClick = { navController.popBackStack() },
                onViewDashboardClick = { patientId -> 
                    val route = if (patientId.isBlank()) "caregiver_dashboard/local" else "caregiver_dashboard/$patientId"
                    navController.navigate(route) 
                }
            )
        }
        composable(
            route = Screen.CaregiverDashboard.route,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: "local"
            CaregiverDashboardScreen(
                patientId = patientId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onAllergyProfileClick = { navController.navigate(Screen.AllergyProfile.route) },
                onEvaluationClick = { navController.navigate(Screen.Evaluation.route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Evaluation.route) {
            MedicineEvaluationScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
