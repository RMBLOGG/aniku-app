package com.example.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.R

@OptIn(ExperimentalTextApi::class)
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/** Font judul bergaya comic/pop-art — dipakai buat logo "Aniku". */
@OptIn(ExperimentalTextApi::class)
val ComicDisplayFont = FontFamily(
    Font(googleFont = GoogleFont("Bangers"), fontProvider = fontProvider, weight = FontWeight.Normal)
)

/** Font body yang tetap playful tapi gampang dibaca — buat tagline/subtext. */
@OptIn(ExperimentalTextApi::class)
val ComicBodyFont = FontFamily(
    Font(googleFont = GoogleFont("Baloo 2"), fontProvider = fontProvider, weight = FontWeight.Medium)
)
