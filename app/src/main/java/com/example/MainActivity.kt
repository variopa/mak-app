package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.InventoryViewModel

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      // Track dark mode locally so user setting can easily toggle between soft gray and deep charcoal neostock!
      val darkModeState = remember { mutableStateOf(false) }

      MyApplicationTheme(darkTheme = darkModeState.value) {
        val inventoryViewModel: InventoryViewModel = viewModel()
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MainAppScreen(
            viewModel = inventoryViewModel,
            darkModeState = darkModeState,
            modifier = Modifier.padding(innerPadding),
            onTriggerBiometric = { onSuccess ->
              triggerBiometricAuth(onSuccess)
            }
          )
        }
      }
    }
  }

  private fun triggerBiometricAuth(onSuccess: () -> Unit) {
    val biometricManager = BiometricManager.from(this)
    val canAuthenticate = biometricManager.canAuthenticate(
      BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
      Toast.makeText(this, "Biometrics (Fingerprint) not available or not configured.", Toast.LENGTH_LONG).show()
      return
    }

    val executor = ContextCompat.getMainExecutor(this)
    val biometricPrompt = BiometricPrompt(this, executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          Toast.makeText(this@MainActivity, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          runOnUiThread {
            onSuccess()
          }
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
        }
      })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle("Fingerprint Unlock")
      .setSubtitle("Authenticate to access Mak Stock Manager")
      .setNegativeButtonText("Use PIN")
      .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
      .build()

    biometricPrompt.authenticate(promptInfo)
  }
}

