package com.example.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.R

@OptIn(ExperimentalTextApi::class)
private val signalFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/** Condensed & tegas -- dipakai buat headline "transmisi diputus", beda nada
 * sama ComicDisplayFont (Bangers) yang playful, soalnya ini momen serius. */
@OptIn(ExperimentalTextApi::class)
val SignalDisplayFont = FontFamily(
    Font(googleFont = GoogleFont("Oswald"), fontProvider = signalFontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Oswald"), fontProvider = signalFontProvider, weight = FontWeight.Medium)
)

/** Monospace teknis -- buat kode status/detail, kesan "log sistem" bukan dekorasi. */
@OptIn(ExperimentalTextApi::class)
val SignalMonoFont = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = signalFontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = signalFontProvider, weight = FontWeight.Bold)
)
