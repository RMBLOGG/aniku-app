package com.example.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import com.example.network.DiamondTopupStatusDto
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.AnikuViewModel
import com.example.network.SakurupiahDiamondInvoiceResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiamondTopUpScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var amountInput by remember { mutableStateOf("") }
    var isCreatingInvoice by remember { mutableStateOf(false) }
    var invoiceError by remember { mutableStateOf<String?>(null) }
    var invoiceResult by remember { mutableStateOf<SakurupiahDiamondInvoiceResponse?>(null) }
    var showInvoiceSheet by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf(PAYMENT_CHANNELS.first()) }
    var channelMenuExpanded by remember { mutableStateOf(false) }
    var showManualSheet by remember { mutableStateOf(false) }

    val amountValue = amountInput.toIntOrNull() ?: 0
    val estimatedDiamond = amountValue / 4

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Top-up Diamond", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HeroBanner()

            Spacer(modifier = Modifier.height(20.dp))
            Text("Buat Apa Aja DM Ini?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BenefitCard(icon = Icons.Default.Groups, title = "Buat Clan", subtitle = "2.000 DM", modifier = Modifier.weight(1f))
                BenefitCard(icon = Icons.Default.TrendingUp, title = "Kontribusi", subtitle = "Naikin level", modifier = Modifier.weight(1f))
                BenefitCard(icon = Icons.Default.EmojiEvents, title = "Puncak Board", subtitle = "Jadi #1", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text("Estimasi Konversi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(vertical = 4.dp)
            ) {
                listOf("Rp5.000" to "1.250 DM", "Rp20.000" to "5.000 DM", "Rp50.000" to "12.500 DM").forEachIndexed { i, (rp, dm) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(rp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(dm, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text("Metode Pembayaran", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            ExposedDropdownMenuBox(
                expanded = channelMenuExpanded,
                onExpandedChange = { channelMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedChannel.label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelMenuExpanded) },
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = channelMenuExpanded,
                    onDismissRequest = { channelMenuExpanded = false }
                ) {
                    PAYMENT_CHANNELS.forEach { channel ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(channel.label, fontSize = 14.sp)
                                    Text(
                                        "Min Rp${channel.min.toLocaleIdString()} - Max Rp${channel.max.toLocaleIdString()}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            onClick = {
                                selectedChannel = channel
                                channelMenuExpanded = false
                                invoiceError = null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("Masukin Nominal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = amountInput,
                onValueChange = { new ->
                    if (new.length <= 8 && new.all { it.isDigit() }) amountInput = new
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Contoh: 10000") },
                prefix = { Text("Rp") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Min Rp${selectedChannel.min.toLocaleIdString()} - Max Rp${selectedChannel.max.toLocaleIdString()} buat ${selectedChannel.label}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            invoiceError?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    invoiceError = null
                    if (amountValue < selectedChannel.min || amountValue > selectedChannel.max) {
                        invoiceError = "Nominal buat ${selectedChannel.label} harus antara Rp${selectedChannel.min.toLocaleIdString()} - Rp${selectedChannel.max.toLocaleIdString()}"
                        return@Button
                    }
                    isCreatingInvoice = true
                    viewModel.createSakurupiahDiamondInvoice(amountValue, selectedChannel.code) { result, error ->
                        isCreatingInvoice = false
                        if (result != null) {
                            invoiceResult = result
                            showInvoiceSheet = true
                        } else {
                            invoiceError = error ?: "Gagal membuat invoice pembayaran"
                        }
                    }
                },
                enabled = amountValue in selectedChannel.min..selectedChannel.max && !isCreatingInvoice,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBF))
            ) {
                if (isCreatingInvoice) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bayar dengan ${selectedChannel.label}", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Diamond otomatis masuk ke akun kamu setelah pembayaran terverifikasi. Kalau belum masuk lebih dari 15 menit, hubungi admin di Chat Room.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .clickable { showManualSheet = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bayar dari luar negeri", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("QRIS otomatis gak kebaca? Bayar manual, direview admin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showManualSheet) {
        ManualDiamondSheet(
            viewModel = viewModel,
            onDismiss = { showManualSheet = false }
        )
    }

    if (showInvoiceSheet && invoiceResult != null) {
        DiamondInvoiceSheet(
            invoice = invoiceResult!!,
            viewModel = viewModel,
            onDismiss = {
                showInvoiceSheet = false
                invoiceResult = null
                amountInput = ""
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiamondInvoiceSheet(
    invoice: SakurupiahDiamondInvoiceResponse,
    viewModel: AnikuViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // PENTING: field "qr" dari Sakurupiah itu URL ke GAMBAR PNG QR code yang
    // udah jadi (https://sakurupiah.id/qr-codeIMG/....png), BUKAN raw string
    // EMVCo QRIS. Sempat salah kirain raw string dan di-generate ulang jadi
    // QR baru pakai ZXing -- hasilnya QR yang isinya cuma URL itu doang,
    // bukan data pembayaran asli, makanya invalid pas discan e-wallet.
    // Fix: langsung load & tampilin gambar itu apa adanya.
    val qrImageUrl = invoice.qr

    var latestStatus by remember(invoice.merchant_ref) { mutableStateOf<DiamondTopupStatusDto?>(null) }
    var keepPolling by remember(invoice.merchant_ref) { mutableStateOf(true) }

    // Polling tiap 4 detik selagi sheet ini kebuka, biar begitu callback
    // Sakurupiah keproses di server, popup berhasil/gagal langsung muncul
    // otomatis tanpa user harus refresh manual.
    LaunchedEffect(invoice.merchant_ref) {
        val ref = invoice.merchant_ref
        if (ref.isNullOrBlank()) return@LaunchedEffect
        while (keepPolling) {
            delay(4000)
            viewModel.getDiamondTopupStatus(ref) { result ->
                latestStatus = result
                if (result?.status == "credited" || result?.status == "invalid") {
                    keepPolling = false
                }
            }
        }
    }

    val hasQrImage = !qrImageUrl.isNullOrBlank()

    // "payment_no" isinya beda-beda tergantung channel: buat Virtual Account
    // itu nomor VA asli (angka), tapi buat e-wallet REDIRECT (GoPay/DANA/
    // ShopeePay/OVO/LinkAja) itu sebenarnya deep link URL ke app-nya, bukan
    // nomor yang bisa di-copy.
    val isRedirectLink = invoice.payment_no?.startsWith("http") == true
    val vaNumber = if (invoice.payment_no != null && !isRedirectLink) invoice.payment_no else null
    val primaryActionUrl = if (isRedirectLink) invoice.payment_no else invoice.checkout_url

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Invoice Top-up Diamond Dibuat!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when {
                    hasQrImage -> "Scan QRIS di bawah buat top-up ${invoice.diamond_amount ?: 0} DM. Diamond otomatis masuk setelah pembayaran terverifikasi."
                    vaNumber != null -> "Transfer ke nomor Virtual Account di bawah buat top-up ${invoice.diamond_amount ?: 0} DM. Diamond otomatis masuk setelah pembayaran terverifikasi."
                    isRedirectLink -> "Buka aplikasi ${invoice.method ?: "pembayaran"} buat top-up ${invoice.diamond_amount ?: 0} DM. Diamond otomatis masuk setelah pembayaran terverifikasi."
                    else -> "Selesaikan pembayaran buat top-up ${invoice.diamond_amount ?: 0} DM. Diamond otomatis masuk setelah pembayaran terverifikasi."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (hasQrImage) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = qrImageUrl,
                        contentDescription = "QRIS Top-up Diamond",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (vaNumber != null) {
                val clipboardManager = LocalClipboardManager.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2FA8BF).copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            invoice.method ?: "Virtual Account",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(vaNumber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(vaNumber))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin nomor")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    val url = primaryActionUrl
                    if (!url.isNullOrBlank()) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Log.e("DiamondTopUpScreen", "Failed to open payment url", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        isRedirectLink -> "Buka ${invoice.method ?: "Aplikasi"}"
                        hasQrImage || vaNumber != null -> "Buka Halaman Pembayaran"
                        else -> "Bayar Sekarang"
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            ) {
                Text("Tutup", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }

    if (latestStatus?.status == "credited") {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2FBF6D)) },
            title = { Text("Top-up Berhasil!") },
            text = {
                Text("Saldo ${latestStatus?.diamond_amount ?: invoice.diamond_amount ?: 0} DM udah masuk ke akun kamu.")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Oke") }
            }
        )
    } else if (latestStatus?.status == "invalid") {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Pembayaran Gagal/Expired") },
            text = {
                Text("Invoice ini udah gak berlaku. Kalau kamu udah bayar tapi ini muncul, hubungi admin di Chat Room ya.")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Oke") }
            }
        )
    }
}

@Composable
private fun HeroBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotate"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF241530), Color(0xFF16414D))),
                RoundedCornerShape(22.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopStart)
                .offset(x = (-40).dp, y = (-40).dp)
                .background(Color(0xFF7B2FBF).copy(alpha = glowAlpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 30.dp)
                .background(Color(0xFF2FA8BF).copy(alpha = glowAlpha * 0.6f), CircleShape)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF5FC9DE),
                modifier = Modifier.size(46.dp).rotate(rotation)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Top-up Diamond (DM)", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
            Text("Dipakai buat bikin & kontribusi Clan", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun BenefitCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF241530), Color(0xFF16414D))))
            .border(1.dp, Color(0xFF7B2FBF).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF2FA8BF).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Color(0xFF2FA8BF), modifier = Modifier.size(18.dp)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

data class PaymentChannelOption(
    val code: String,
    val label: String,
    val min: Int,
    val max: Int
)

// Daftar channel pembayaran aktif Sakurupiah, HARUS sinkron sama limit yang
// divalidasi di edge function sakurupiah-create-diamond-invoice (server tetap
// validasi ulang, ini cuma buat UX biar user gak input nominal yang bakal
// ditolak server).
val PAYMENT_CHANNELS = listOf(
    PaymentChannelOption("QRIS", "QRIS", 500, 2_000_000),
    PaymentChannelOption("QRISMU", "QRISMU", 500, 5_000_000),
    PaymentChannelOption("QRIS2", "QRIS2", 100, 10_000_000),
    PaymentChannelOption("QRISC", "QRISC", 200, 20_000_000),
    PaymentChannelOption("ShopeePay", "ShopeePay", 1_000, 2_000_000),
    PaymentChannelOption("DANA", "DANA E-Wallet", 1_000, 2_000_000),
    PaymentChannelOption("GOPAY", "GoPay E-Wallet", 500, 5_000_000),
    PaymentChannelOption("OVO", "OVO E-Wallet", 1_000, 2_000_000),
    PaymentChannelOption("LinkAja", "LinkAja", 1_000, 2_000_000),
    PaymentChannelOption("BCAVA", "BCA Virtual Account", 10_000, 15_000_000),
    PaymentChannelOption("BNIVA", "BNI Virtual Account", 10_000, 20_000_000),
    PaymentChannelOption("BRIVA", "BRI Virtual Account", 10_000, 10_000_000),
    PaymentChannelOption("MANDIRIVA", "Mandiri Virtual Account", 10_000, 10_000_000),
    PaymentChannelOption("PERMATAVA", "Permata Virtual Account", 10_000, 20_000_000),
    PaymentChannelOption("CIMBVA", "CIMB Niaga Virtual Account", 10_000, 10_000_000),
    PaymentChannelOption("DANAMON", "Danamon Virtual Account", 10_000, 15_000_000),
    PaymentChannelOption("OCBC", "OCBC Virtual Account", 10_000, 10_000_000),
    PaymentChannelOption("BSIVA", "BSI Virtual Account", 10_000, 20_000_000),
    PaymentChannelOption("MUAMALAT", "Muamalat Virtual Account", 10_000, 15_000_000),
    PaymentChannelOption("SINARMAS", "Sinarmas Virtual Account", 10_000, 10_000_000),
    PaymentChannelOption("BNCVA", "BNC Virtual Account", 10_000, 10_000_000),
    PaymentChannelOption("BAGVA", "BAG Virtual Account", 10_000, 15_000_000),
    PaymentChannelOption("ALFAMART", "Alfamart", 10_000, 5_000_000),
    PaymentChannelOption("INDOMARET", "Indomaret", 10_000, 2_500_000)
)

private fun Int.toLocaleIdString(): String {
    return "%,d".format(this).replace(",", ".")
}

