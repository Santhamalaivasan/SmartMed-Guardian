package com.smartmedguardian.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object SignUp : Screen("sign_up")
    object Home : Screen("home") // Still kept for compatibility if needed
    object Dashboard : Screen("dashboard")
    object Camera : Screen("camera?targetMedicine={targetMedicine}&targetSlot={targetSlot}&targetTime={targetTime}&prescriptionId={prescriptionId}") {
        fun createRoute(
            targetMedicine: String = "",
            targetSlot: String = "",
            targetTime: String = "",
            prescriptionId: Long = -1L
        ): String {
            val encMed = Uri.encode(targetMedicine)
            val encSlot = Uri.encode(targetSlot)
            val encTime = Uri.encode(targetTime)
            return "camera?targetMedicine=$encMed&targetSlot=$encSlot&targetTime=$encTime&prescriptionId=$prescriptionId"
        }
    }
    object MedicineDetails : Screen("details")
    object DetectionResult : Screen("detection_result")
    object Verification : Screen("verification")
    object SafetyAdvice : Screen("safety_advice")
    object PrescriptionList : Screen("prescription_list")
    object AddPrescription : Screen("add_prescription")
    object AllergyProfile : Screen("allergy_profile")
    object History : Screen("history")
    object Schedule : Screen("schedule")
    object BarcodeScanner : Screen("barcode_scanner")
    object Analytics : Screen("analytics")
    object ConditionTrend : Screen("condition_trend")
    object Caregiver : Screen("caregiver")
    object CaregiverDashboard : Screen("caregiver_dashboard/{patientId}")
    object Settings : Screen("settings")
    object Evaluation : Screen("evaluation")
}
