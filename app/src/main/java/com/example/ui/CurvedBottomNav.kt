package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// ── Bottom Navigation — desain dikunci ke satu bentuk: floating pill, niru
// PERSIS FloatingBottomNavigation punya Kuroflix. Bukan lagi salah satu dari
// beberapa pilihan style (dulu ada IconLabel/IconOnly/PillLabel/PillIcon) --
// semua itu sudah dihapus total karena salah satunya (IconLabel) dirender
// sebagai bar solid full-width lewat Scaffold.bottomBar, itu penyebab "kotak
// hitam yang motong layar" yang dilaporkan. Sekarang cuma ada satu jalur
// render, selalu overlay mengambang, konsisten di semua halaman & semua
// preset tema. Parameter `navStyle` tetap ada di signature biar caller lama
// gak perlu diubah, tapi nilainya sudah tidak dipakai untuk bercabang lagi.

// Blend warna menuju putih sebesar `amount` (0f..1f). Dipakai buat bikin
// pill floating nav yang "sedikit lebih terang" dari background halaman,
// alih-alih pakai surfaceColor tema yang bisa selisih jauh dan keliatan
// seperti kotak solid terpisah. Kuroflix sendiri sengaja bikin surface cuma
// dikit lebih terang dari background (0xFF0A0A0B -> 0xFF141416, selisih
// tipis) -- di sini kita hitung ulang dari background halaman + lighten
// dikit, independen dari preset tema apa yang lagi aktif.
private fun Color.lightened(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * a,
        green = green + (1f - green) * a,
        blue = blue + (1f - blue) * a,
        alpha = alpha
    )
}

@Composable
fun CurvedBottomNav(
    mainNavItems: List<Triple<String, String, ImageVector>>,
    currentRoute: String?,
    isSheetRouteActive: Boolean,
    hasUnreadChat: Boolean,
    onNavigate: (String) -> Unit,
    onMoreClick: () -> Unit,
    navStyle: String = "Floating",
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val floatingNavBg = remember(backgroundColor) { backgroundColor.lightened(0.07f) }

    val allItems = listOf(Triple("home", "Home", Icons.Filled.Home)) + mainNavItems
    val moreSelected = isSheetRouteActive

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(floatingNavBg) // dekat ke background halaman -> ngambang, bukan "kotak"
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(32.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            allItems.forEach { (route, label, icon) ->
                FloatingCircleNavItem(
                    icon = icon,
                    label = label,
                    isSelected = currentRoute == route,
                    modifier = Modifier.weight(1f)
                ) { onNavigate(route) }
            }
            FloatingCircleMoreItem(
                isSelected = moreSelected,
                hasUnreadChat = hasUnreadChat,
                modifier = Modifier.weight(1f)
            ) { onMoreClick() }
        }
    }
}

// ── Item nav floating, niru persis FloatingBottomNavigation Kuroflix ──
// Icon-only, scale animasi pas aktif, background jadi circle putih solid
// kalau aktif, transparan kalau nggak.

@Composable
private fun FloatingCircleNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "floating_icon_scale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(if (isSelected) 40.dp else 34.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FloatingCircleMoreItem(
    isSelected: Boolean,
    hasUnreadChat: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "floating_more_scale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(if (isSelected) 40.dp else 34.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Box {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Lainnya",
                    tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                if (hasUnreadChat) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}
