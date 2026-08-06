package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.network.AnikuViewModel
import com.example.network.ManualCheckoutResponseDto
import com.example.network.PremiumPackageDto

// QR statis, sama file yang dipakai halaman web /premium/manual & /diamond/manual.
private const val MANUAL_QRIS_IMAGE_URL = "https://aniku-store.my.id/manual-qris-qr.png"

// Step yang sama dipakai Premium maupun Diamond -- form pilih (paket/nominal),
// upload bukti bayar, nunggu admin review, terus hasil akhir (sukses/ditolak).
private enum class ManualQrisStep { FORM, PROOF, WAITING, SUCCESS, REJECTED }

// ── Premium ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPremiumSheet(
    packages: List<PremiumPackageDto>,
    viewModel: AnikuViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ManualQrisStep.FORM) }
    var selectedPackage by remember { mutableStateOf<PremiumPackageDto?>(null) }
    var order by remember { mutableStateOf<ManualCheckoutResponseDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun startPolling() {
        step = ManualQrisStep.WAITING
    }

    val claimId = order?.claim_id
    if (step == ManualQrisStep.WAITING && claimId != null) {
        LaunchedEffect(claimId) {
            while (true) {
                delay(5000)
                var done = false
                viewModel.getPremiumClaimStatus(claimId) { result ->
                    if (result?.status == "ready" || result?.status == "claimed") {
                        step = ManualQrisStep.SUCCESS
                        viewModel.refreshProfile()
                        done = true
                    } else if (result?.status == "invalid" || result?.manual_review_status == "rejected") {
                        step = ManualQrisStep.REJECTED
                        done = true
                    }
                }
                if (done) break
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (step) {
                ManualQrisStep.FORM -> {
                    Text("Premium — Bayar dari luar negeri", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Buat pembeli yang e-wallet-nya (misal Malaysia) gak kebaca QRIS otomatis. Pembayaran direview manual admin.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    packages.forEach { pkg ->
                        val selected = selectedPackage?.id == pkg.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) Color(0xFFFFB800).copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFFFFB800) else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedPackage = pkg }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(pkg.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${pkg.duration_days} hari premium",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                "Rp${"%,d".format(pkg.price).replace(",", ".")}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4FC3F7)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    errorMsg?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val pkg = selectedPackage ?: return@Button
                            errorMsg = null
                            isSubmitting = true
                            viewModel.manualPremiumCheckout(pkg.id) { result, error ->
                                isSubmitting = false
                                if (result != null) {
                                    order = result
                                    step = ManualQrisStep.PROOF
                                } else {
                                    errorMsg = error ?: "Gagal membuat pesanan, coba lagi."
                                }
                            }
                        },
                        enabled = selectedPackage != null && !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Lanjut ke pembayaran", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ManualQrisStep.PROOF -> {
                    val currentOrder = order
                    if (currentOrder != null) {
                        ManualProofStep(
                            type = "premium",
                            transactionId = currentOrder.claim_id.orEmpty(),
                            amount = currentOrder.amount ?: selectedPackage?.price ?: 0,
                            merchantRef = currentOrder.merchant_ref,
                            viewModel = viewModel,
                            onSubmitted = { startPolling() }
                        )
                    }
                }

                ManualQrisStep.WAITING -> ManualWaitingStep(merchantRef = order?.merchant_ref)

                ManualQrisStep.SUCCESS -> ManualResultStep(
                    success = true,
                    message = "Premium buat akun kamu udah aktif.",
                    onClose = onDismiss
                )

                ManualQrisStep.REJECTED -> ManualResultStep(
                    success = false,
                    message = "Pembayaran ini ditolak admin. Kalau ini keliru, hubungi admin di Chat Room bawa ref. transaksi ${order?.merchant_ref ?: "-"}.",
                    onClose = onDismiss
                )
            }
        }
    }
}

// ── Diamond ──────────────────────────────────────────────────────────────
private const val MANUAL_MIN_AMOUNT = 500
private const val MANUAL_MAX_AMOUNT = 2_000_000
private const val RUPIAH_PER_DIAMOND = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualDiamondSheet(
    viewModel: AnikuViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ManualQrisStep.FORM) }
    var amountInput by remember { mutableStateOf("") }
    var order by remember { mutableStateOf<ManualCheckoutResponseDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val amountValue = amountInput.toIntOrNull() ?: 0
    val estimatedDiamond = amountValue / RUPIAH_PER_DIAMOND

    val merchantRef = order?.merchant_ref
    if (step == ManualQrisStep.WAITING && merchantRef != null) {
        LaunchedEffect(merchantRef) {
            while (true) {
                delay(5000)
                var done = false
                viewModel.getDiamondTopupStatus(merchantRef) { result ->
                    if (result?.status == "credited") {
                        step = ManualQrisStep.SUCCESS
                        done = true
                    } else if (result?.status == "invalid" || result?.manual_review_status == "rejected") {
                        step = ManualQrisStep.REJECTED
                        done = true
                    }
                }
                if (done) break
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (step) {
                ManualQrisStep.FORM -> {
                    Text("Diamond — Bayar dari luar negeri", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Buat pembeli yang e-wallet-nya (misal Malaysia) gak kebaca QRIS otomatis. Pembayaran direview manual admin.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { new -> if (new.length <= 8 && new.all { it.isDigit() }) amountInput = new },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Contoh: 10000") },
                        prefix = { Text("Rp") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Min Rp${MANUAL_MIN_AMOUNT.toLocaleIdString()} - Max Rp${MANUAL_MAX_AMOUNT.toLocaleIdString()}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5000, 20000, 50000).forEach { preset ->
                            OutlinedButton(
                                onClick = { amountInput = preset.toString() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Rp${preset / 1000}rb", fontSize = 12.sp)
                            }
                        }
                    }

                    if (amountValue > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2FA8BF).copy(alpha = 0.1f))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kamu akan dapat", fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("$estimatedDiamond DM", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    errorMsg?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            errorMsg = null
                            if (amountValue < MANUAL_MIN_AMOUNT || amountValue > MANUAL_MAX_AMOUNT) {
                                errorMsg = "Nominal harus antara Rp${MANUAL_MIN_AMOUNT.toLocaleIdString()} - Rp${MANUAL_MAX_AMOUNT.toLocaleIdString()}"
                                return@Button
                            }
                            isSubmitting = true
                            viewModel.manualDiamondCheckout(amountValue) { result, error ->
                                isSubmitting = false
                                if (result != null) {
                                    order = result
                                    step = ManualQrisStep.PROOF
                                } else {
                                    errorMsg = error ?: "Gagal membuat pesanan, coba lagi."
                                }
                            }
                        },
                        enabled = amountValue in MANUAL_MIN_AMOUNT..MANUAL_MAX_AMOUNT && !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Lanjut ke pembayaran", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ManualQrisStep.PROOF -> {
                    val currentOrder = order
                    if (currentOrder != null) {
                        ManualProofStep(
                            type = "diamond",
                            transactionId = currentOrder.merchant_ref.orEmpty(),
                            amount = currentOrder.amount ?: amountValue,
                            merchantRef = currentOrder.merchant_ref,
                            viewModel = viewModel,
                            onSubmitted = { step = ManualQrisStep.WAITING }
                        )
                    }
                }

                ManualQrisStep.WAITING -> ManualWaitingStep(merchantRef = order?.merchant_ref)

                ManualQrisStep.SUCCESS -> ManualResultStep(
                    success = true,
                    message = "${order?.diamond_amount ?: estimatedDiamond} Diamond udah masuk ke akun kamu.",
                    onClose = onDismiss
                )

                ManualQrisStep.REJECTED -> ManualResultStep(
                    success = false,
                    message = "Pembayaran ini ditolak admin. Kalau ini keliru, hubungi admin di Chat Room bawa ref. transaksi ${order?.merchant_ref ?: "-"}.",
                    onClose = onDismiss
                )
            }
        }
    }
}

// ── Bagian bersama ──────────────────────────────────────────────────────

// type: "premium" | "diamond". transactionId: claim_id (premium) atau
// merchant_ref (diamond) -- persis field "id" yang diharapkan /api/manual-proof.
@Composable
private fun ManualProofStep(
    type: String,
    transactionId: String,
    amount: Int,
    merchantRef: String?,
    viewModel: AnikuViewModel,
    onSubmitted: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) imageUri = uri }

    Text("Scan QRIS & kirim bukti bayar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Rp${"%,d".format(amount).replace(",", ".")}",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp)
        ) {
            AsyncImage(
                model = MANUAL_QRIS_IMAGE_URL,
                contentDescription = "QRIS",
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (!merchantRef.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Ref: $merchantRef",
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text("Catatan / no. referensi transaksi (opsional)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("misal: TnG ref #123456") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )

    Spacer(modifier = Modifier.height(14.dp))
    Text("Screenshot bukti bayar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedButton(
        onClick = { imagePickerLauncher.launch("image/*") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(imageUri?.lastPathSegment ?: "Pilih gambar")
    }

    errorMsg?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            val uri = imageUri ?: return@Button
            errorMsg = null
            isSubmitting = true
            viewModel.uploadManualProof(type, transactionId, note, uri) { ok, error ->
                isSubmitting = false
                if (ok) onSubmitted() else errorMsg = error ?: "Gagal upload bukti bayar, coba lagi."
            }
        },
        enabled = imageUri != null && !isSubmitting,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(50)
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text("Kirim bukti bayar", fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        "Karena ini pembayaran manual (buat pembeli luar negeri), gak masuk otomatis kayak QRIS biasa — perlu direview admin dulu.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

@Composable
private fun ManualWaitingStep(merchantRef: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFFFFB800).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text("Menunggu verifikasi", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Bukti bayar kamu udah dikirim dan lagi dicek manual sama admin. Halaman ini otomatis update begitu udah dikonfirmasi, jangan tutup dulu.",
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!merchantRef.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Ref: $merchantRef",
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ManualResultStep(success: Boolean, message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background((if (success) Color(0xFF2FBF6D) else MaterialTheme.colorScheme.error).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (success) Color(0xFF2FBF6D) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(if (success) "Pembayaran dikonfirmasi" else "Pembayaran ditolak", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
            Text("Tutup")
        }
    }
}

private fun Int.toLocaleIdString(): String = "%,d".format(this).replace(",", ".")
