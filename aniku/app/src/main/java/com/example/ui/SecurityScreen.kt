package com.example.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.example.network.AnikuViewModel
import com.example.util.nullIfBlank
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appLockType by viewModel.appLockType.collectAsState()
    val appPin by viewModel.appPin.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    var showPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    // Cek apakah perangkat support biometrik
    val biometricManager = BiometricManager.from(context)
    val biometricAvailable = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS

    // Info perangkat
    val deviceName = remember { "${Build.MANUFACTURER} ${Build.MODEL}" }
    val loginTime = remember {
        val fmt = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("id"))
        fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        fmt.format(Date())
    }
    val androidVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keamanan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Kunci Aplikasi ──
            SectionHeader(title = "Kunci Aplikasi", icon = Icons.Default.Lock, tint = accentColor)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Toggle aktif/nonaktif
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Aktifkan Kunci Aplikasi",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Minta autentikasi saat membuka app",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    // Tampilkan dialog set PIN
                                    showPinDialog = true
                                } else {
                                    viewModel.saveAppLock(false, appLockType, appPin)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor
                            )
                        )
                    }

                    if (appLockEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Tipe kunci
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Metode Kunci",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (appLockType == "biometric") "Sidik jari / Wajah" else "PIN 4 digit",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Pilih metode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = appLockType == "pin",
                                onClick = { viewModel.saveAppLock(true, "pin", appPin) },
                                label = { Text("PIN") },
                                leadingIcon = {
                                    Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            if (biometricAvailable) {
                                FilterChip(
                                    selected = appLockType == "biometric",
                                    onClick = { viewModel.saveAppLock(true, "biometric", appPin) },
                                    label = { Text("Sidik Jari") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Ganti PIN
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChangePinDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Ubah PIN",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Sesi Aktif ──
            SectionHeader(title = "Aktif di Perangkat Ini", icon = Icons.Default.PhoneAndroid, tint = accentColor)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                    SessionInfoRow(
                        icon = Icons.Default.PhoneAndroid,
                        label = "Perangkat",
                        value = deviceName,
                        tint = accentColor
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    SessionInfoRow(
                        icon = Icons.Default.Android,
                        label = "Sistem",
                        value = androidVersion,
                        tint = accentColor
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    SessionInfoRow(
                        icon = Icons.Default.AccountCircle,
                        label = "Akun",
                        value = session.email.nullIfBlank() ?: session.username.nullIfBlank() ?: "-",
                        tint = accentColor
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    SessionInfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Sesi dimulai",
                        value = loginTime,
                        tint = accentColor
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    SessionInfoRow(
                        icon = Icons.Default.VerifiedUser,
                        label = "Status",
                        value = if (session.isAdmin) "Admin" else "Pengguna",
                        tint = if (session.isAdmin) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialog set PIN
    if (showPinDialog) {
        PinDialog(
            title = "Buat PIN Baru",
            subtitle = "Masukkan 4 digit PIN untuk mengunci aplikasi",
            onConfirm = { pin ->
                if (pin.length < 4) {
                    pinError = "PIN harus 4 digit"
                } else {
                    viewModel.saveAppLock(true, "pin", pin)
                    showPinDialog = false
                    pinError = ""
                }
            },
            onDismiss = { showPinDialog = false },
            error = pinError
        )
    }

    // Dialog ganti PIN
    if (showChangePinDialog) {
        PinDialog(
            title = "Ubah PIN",
            subtitle = "Masukkan PIN baru 4 digit",
            onConfirm = { pin ->
                if (pin.length < 4) {
                    pinError = "PIN harus 4 digit"
                } else {
                    viewModel.saveAppLock(true, appLockType, pin)
                    showChangePinDialog = false
                    pinError = ""
                }
            },
            onDismiss = { showChangePinDialog = false },
            error = pinError
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SessionInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PinDialog(
    title: String,
    subtitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String = ""
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    placeholder = { Text("• • • •") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) {
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    } else null
                )
                // Tampilkan dot indikator
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < pin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
