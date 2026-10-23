package com.smartmedguardian

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.smartmedguardian.ui.navigation.NavGraph
import com.smartmedguardian.ui.theme.SmartMedGuardianTheme
import com.smartmedguardian.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        renderUi(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderUi(intent)
    }

    private fun renderUi(intent: Intent?) {
        val openVerification = intent?.getBooleanExtra("openVerification", false) ?: false
        val openCaregiverDashboard = intent?.getBooleanExtra("openCaregiverDashboard", false) ?: false
        val patientId = intent?.getStringExtra("patientId") ?: "local"
        val targetMedicine = intent?.getStringExtra("medicineName") ?: ""
        val targetSlot = intent?.getStringExtra("slotName") ?: ""
        val targetTime = intent?.getStringExtra("scheduledTimeStr") ?: ""
        val prescriptionId = intent?.getLongExtra("prescriptionId", -1L) ?: -1L

        setContent {
            val isDarkMode = settingsViewModel.isDarkMode.value ?: isSystemInDarkTheme()
            SmartMedGuardianTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    openVerification = openVerification,
                    openCaregiverDashboard = openCaregiverDashboard,
                    patientId = patientId,
                    targetMedicine = targetMedicine,
                    targetSlot = targetSlot,
                    targetTime = targetTime,
                    prescriptionId = prescriptionId
                )
            }
        }
    }
}
