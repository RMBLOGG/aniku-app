package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

@Composable
fun crossfadeModel(url: Any?, durationMs: Int = 300): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .crossfade(durationMs)
        .build()
}
