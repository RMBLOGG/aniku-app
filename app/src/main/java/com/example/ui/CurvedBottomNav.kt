package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

// Blend warna menuju putih sebesar `amount` (0f..1f). Dipakai buat bikin
// pill floating nav yang "sedikit lebih terang" dari background halaman,
// alih-alih pakai surfaceColor tema yang bisa selisih jauh dan keliatan
// seperti kotak solid terpisah.
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
    navStyle: String = "IconLabel",
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = MaterialTheme.colorScheme.background

    // Warna khusus buat pill "Floating" -- BUKAN surfaceColor tema biasa.
    // surfaceColor tiap preset (Netflix, Midnight, dst) selisihnya jauh dari
    // background halaman, jadi kalau dipakai apa adanya, pill nav keliatan
    // sebagai "kotak" solid yang motong layar alih-alih ngambang nyatu kayak
    // Kuroflix. Kuroflix sengaja bikin surface cuma sedikit lebih terang dari
    // background (contoh: 0xFF0A0A0B -> 0xFF141416, selisih tipis). Di sini
    // kita hitung ulang warna pill dari background halaman + lighten dikit
    // (~7%), independen dari preset tema apa yang lagi aktif, supaya efek
    // "floating & clean"-nya konsisten di semua tema.
    val floatingNavBg = remember(backgroundColor) { backgroundColor.lightened(0.07f) }

    val allItems = listOf(Triple("home", "Home", Icons.Default.Home)) + mainNavItems
    val moreSelected = isSheetRouteActive

    when (navStyle) {

        // ── A: Icon + Label (default) ──
        "IconLabel" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(surfaceColor)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allItems.forEach { (route, label, icon) ->
                        FlatNavItem(icon, label, currentRoute == route, primaryColor, Modifier.weight(1f)) { onNavigate(route) }
                    }
                    MoreNavItem(moreSelected, hasUnreadChat, primaryColor, showLabel = true, pill = false, onClick = { onMoreClick() }, modifier = Modifier.weight(1f))
                }
            }
        }

        // ── B: Icon Only + dot ──
        "IconOnly" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor.copy(alpha = 0.97f))
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allItems.forEach { (route, label, icon) ->
                        IconOnlyNavItem(icon, label, currentRoute == route, primaryColor) { onNavigate(route) }
                    }
                    MoreNavItem(moreSelected, hasUnreadChat, primaryColor, showLabel = false, pill = false, onClick = { onMoreClick() })
                }
            }
        }

        // ── C: Floating Pill icon+label aktif ──
        "PillLabel" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allItems.forEach { (route, label, icon) ->
                        PillNavItem(icon, label, currentRoute == route, primaryColor, showLabel = true) { onNavigate(route) }
                    }
                    MoreNavItem(moreSelected, hasUnreadChat, primaryColor, showLabel = moreSelected, pill = true, onClick = { onMoreClick() })
                }
            }
        }

        // ── D: Floating Pill icon only ──
        "PillIcon" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allItems.forEach { (route, label, icon) ->
                        PillIconOnlyItem(icon, label, currentRoute == route, primaryColor) { onNavigate(route) }
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (moreSelected) primaryColor else Color.Transparent)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onMoreClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "Lainnya",
                                tint = if (moreSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.size(22.dp))
                            if (hasUnreadChat) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error).align(Alignment.TopEnd))
                            }
                        }
                    }
                }
            }
        }

        // ── E: Floating (niru Kuroflix) — pill solid, icon-only, circle putih pas aktif ──
        // Beda sama "PillIcon": ini dirender sebagai OVERLAY di atas konten (lihat MainActivity,
        // bukan lewat Scaffold.bottomBar), jadi beneran ngambang & konten scroll di belakangnya
        // tanpa ada area solid "nempel" yang direserve Scaffold.
        "Floating" -> {
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
                        FloatingCircleNavItem(icon, label, currentRoute == route, primaryColor, Modifier.weight(1f)) { onNavigate(route) }
                    }
                    FloatingCircleMoreItem(moreSelected, hasUnreadChat, primaryColor, Modifier.weight(1f)) { onMoreClick() }
                }
            }
        }
    }
}

// ── Nav item components ──

@Composable
private fun FlatNavItem(
    icon: ImageVector, label: String, isSelected: Boolean,
    primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) primaryColor.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(250, easing = EaseOutCubic), label = "nav_bg"
    )
    val iconTint by animateColorAsState(
        if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(0.45f),
        animationSpec = tween(250), label = "nav_tint"
    )
    val scale by animateFloatAsState(
        if (isSelected) 1.15f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "nav_scale"
    )
    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(bgColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, label, tint = iconTint, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale })
            Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = iconTint)
        }
    }
}

@Composable
private fun IconOnlyNavItem(
    icon: ImageVector, label: String, isSelected: Boolean,
    primaryColor: Color, onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(0.35f),
        animationSpec = tween(250), label = "nav_tint"
    )
    Box(
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, label, tint = iconTint, modifier = Modifier.size(24.dp))
            if (isSelected) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(primaryColor))
            }
        }
    }
}

@Composable
private fun PillNavItem(
    icon: ImageVector, label: String, isSelected: Boolean,
    primaryColor: Color, showLabel: Boolean, onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) primaryColor.copy(0.15f) else Color.Transparent,
        animationSpec = tween(250), label = "pill_bg"
    )
    val iconTint by animateColorAsState(
        if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(0.35f),
        animationSpec = tween(250), label = "pill_tint"
    )
    Box(
        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(bgColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (isSelected && showLabel) 14.dp else 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, label, tint = iconTint, modifier = Modifier.size(20.dp))
            if (isSelected && showLabel) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor)
            }
        }
    }
}

@Composable
private fun PillIconOnlyItem(
    icon: ImageVector, label: String, isSelected: Boolean,
    primaryColor: Color, onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape)
            .background(if (isSelected) primaryColor else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label,
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.4f),
            modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MoreNavItem(
    isSelected: Boolean, hasUnreadChat: Boolean,
    primaryColor: Color, showLabel: Boolean, pill: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconTint by animateColorAsState(
        if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(0.45f),
        animationSpec = tween(250), label = "more_tint"
    )
    val bgColor by animateColorAsState(
        if (isSelected && pill) primaryColor.copy(0.15f) else Color.Transparent,
        animationSpec = tween(250), label = "more_bg"
    )

    val baseModifier = if (pill) {
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .padding(horizontal = if (isSelected && showLabel) 14.dp else 12.dp, vertical = 8.dp)
    } else {
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .padding(vertical = 6.dp)
    }

    Box(
        modifier = baseModifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (pill && isSelected && showLabel) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box {
                    Icon(Icons.Default.MoreHoriz, "Lainnya", tint = primaryColor, modifier = Modifier.size(20.dp))
                    if (hasUnreadChat) Box(modifier = Modifier.size(7.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error).align(Alignment.TopEnd))
                }
                Text("Lainnya", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box {
                    Icon(Icons.Default.MoreHoriz, "Lainnya", tint = iconTint, modifier = Modifier.size(22.dp))
                    if (hasUnreadChat) Box(modifier = Modifier.size(7.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error).align(Alignment.TopEnd))
                }
                if (showLabel) Text("Lainnya", fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = iconTint)
            }
        }
    }
}

// ── "Floating" style nav item (niru persis FloatingBottomNavigation Kuroflix) ──
// Icon-only, scale animasi pas aktif, background jadi circle putih solid kalau aktif.

@Composable
private fun FloatingCircleNavItem(
    icon: ImageVector, label: String, isSelected: Boolean,
    primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
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
    isSelected: Boolean, hasUnreadChat: Boolean,
    primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
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
