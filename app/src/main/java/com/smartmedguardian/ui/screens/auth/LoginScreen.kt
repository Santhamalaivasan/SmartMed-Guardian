package com.smartmedguardian.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartmedguardian.ui.components.*
import com.smartmedguardian.ui.theme.*
import com.smartmedguardian.util.BiometricHelper
import com.smartmedguardian.viewmodel.AuthState
import com.smartmedguardian.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    
    val biometricHelper = remember { BiometricHelper(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    // Auto-show biometric if enabled
    LaunchedEffect(isBiometricEnabled) {
        if (isBiometricEnabled && biometricHelper.isBiometricAvailable() && activity != null) {
            biometricHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = { onLoginSuccess() },
                onError = { _, _ -> },
                onFailed = {}
            )
        }
    }

    Scaffold(containerColor = Background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MedicalGreen
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = TextLight
            )

            Spacer(modifier = Modifier.height(48.dp))

            NeumorphicTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "Email Address",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            NeumorphicTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (authState is AuthState.Loading) {
                CircularProgressIndicator(color = MedicalGreen)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NeumorphicButton(
                        text = "Login",
                        onClick = { viewModel.login(email, password) },
                        modifier = Modifier.weight(1f),
                        color = MedicalGreen,
                        textColor = androidx.compose.ui.graphics.Color.White
                    )

                    if (biometricHelper.isBiometricAvailable()) {
                        NeumorphicIconButton(
                            onClick = {
                                activity?.let {
                                    biometricHelper.showBiometricPrompt(
                                        activity = it,
                                        onSuccess = { onLoginSuccess() },
                                        onError = { _, _ -> },
                                        onFailed = {}
                                    )
                                }
                            },
                            icon = { Icon(Icons.Default.Fingerprint, "Biometric", tint = MedicalGreen) }
                        )
                    }
                }
            }

            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onNavigateToSignUp) {
                Text("Don't have an account? Sign Up", color = MedicalGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.loginDemo() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicalGreen),
                border = androidx.compose.foundation.BorderStroke(1.dp, MedicalGreen)
            ) {
                Text("Enter Demo Mode (Bypass Login)")
            }
        }
    }
}
