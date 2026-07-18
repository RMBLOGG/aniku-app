package com.example.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun LockScreen(
    lockType: String,
    savedPin: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var shakeError by remember { mutableStateOf(false) }

    // Biometrik: langsung prompt saat pertama tampil
    LaunchedEffect(lockType) {
        if (lockType == "biometric") {
            showBiometricPrompt(
                activity = context as FragmentActivity,
                onSuccess = onUnlocked,
                onFallback = { /* biarkan user input PIN */ }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Logo / icon kunci
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Aplikasi Terkunci",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (lockType == "biometric") "Gunakan sidik jari atau masukkan PIN"
                    else "Masukkan PIN untuk membuka",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Dot indikator PIN
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pinInput.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Error message
            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            // Numpad
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(
                        if (lockType == "biometric") "bio" else "",
                        "0",
                        "del"
                    )
                )
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { key ->
                            NumpadKey(
                                key = key,
                                onClick = {
                                    when (key) {
                                        "del" -> {
                                            if (pinInput.isNotEmpty()) {
                                                pinInput = pinInput.dropLast(1)
                                                errorMsg = ""
                                            }
                                        }
                                        "bio" -> {
                                            showBiometricPrompt(
                                                activity = context as FragmentActivity,
                                                onSuccess = onUnlocked,
                                                onFallback = {}
                                            )
                                        }
                                        "" -> {}
                                        else -> {
                                            if (pinInput.length < 4) {
                                                pinInput += key
                                                errorMsg = ""
                                                // Auto-check saat 4 digit
                                                if (pinInput.length == 4) {
                                                    if (pinInput == savedPin) {
                                                        onUnlocked()
                                                    } else {
                                                        errorMsg = "PIN salah, coba lagi"
                                                        pinInput = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumpadKey(key: String, onClick: () -> Unit) {
    val size = 72.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when (key) {
                    "del" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    "" -> Color.Transparent
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (key.isNotEmpty()) Modifier.clickable { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "del" -> Icon(
                Icons.Default.Backspace,
                contentDescription = "Hapus",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            "bio" -> Icon(
                Icons.Default.Fingerprint,
                contentDescription = "Sidik jari",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            "" -> {}
            else -> Text(
                key,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFallback: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFallback()
            }
            override fun onAuthenticationFailed() {}
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Buka Kunci Aniku")
        .setSubtitle("Gunakan sidik jari untuk masuk")
        .setNegativeButtonText("Gunakan PIN")
        .build()
    biometricPrompt.authenticate(promptInfo)
}
