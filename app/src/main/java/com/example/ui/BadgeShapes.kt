package com.example.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shape "ribbon/flag" — kayak badge clan "JF" / "TSR" di referensi.
 * Sudut kiri rounded, sudut kanan-bawah dipotong diagonal (efek ujung pita dilipat).
 */
class RibbonBadgeShape(
    private val cornerRadius: Dp = 5.dp,
    private val notchFraction: Float = 0.42f // seberapa dalam potongan di kanan-bawah, 0f-1f dari tinggi
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.height / 2f)
        val notch = size.height * notchFraction

        val path = Path().apply {
            // mulai dari kiri-atas (setelah radius), searah jarum jam
            moveTo(r, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - notch)
            lineTo(size.width - notch, size.height)
            lineTo(r, size.height)
            // sudut kiri-bawah rounded
            quadraticTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, r)
            // sudut kiri-atas rounded
            quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Shape "pennant/tag" — kayak badge role "OTF" di referensi.
 * Kiri rata (rounded), kanan runcing ke tengah kayak label harga.
 */
class PennantBadgeShape(
    private val cornerRadius: Dp = 5.dp,
    private val pointFraction: Float = 0.32f // seberapa lancip ujung kanan, relatif ke lebar
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.height / 2f)
        val point = size.width * pointFraction

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - point, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width - point, size.height)
            lineTo(r, size.height)
            quadraticTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}
