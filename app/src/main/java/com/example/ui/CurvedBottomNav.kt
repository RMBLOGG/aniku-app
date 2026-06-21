package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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

@Composable
fun CurvedBottomNav(
    mainNavItems: List<Triple<String, String, ImageVector>>,
    currentRoute: String?,
    isSheetRouteActive: Boolean,
    hasUnreadChat: Boolean,
    onNavigate: (String) -> Unit,
    onMoreClick: () -> Unit,
    navStyle: String = "IconLabel"
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

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
                    MoreNavItem(moreSelected, hasUnreadChat, primaryColor, showLabel = true, pill = false) { onMoreClick() }
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
                    MoreNavItem(moreSelected, hasUnreadChat, primaryColor, showLabel = false, pill = false) { onMoreClick() }
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
                    MoreNavItem(moreSelected, hasUnreadChat, primaryColor, showLabel = moreSelected, pill = true) { onMoreClick() }
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
    primaryColor: Color, showLabel: Boolean, pill: Boolean, onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(0.45f),
        animationSpec = tween(250), label = "more_tint"
    )
    val bgColor by animateColorAsState(
        if (isSelected && pill) primaryColor.copy(0.15f) else Color.Transparent,
        animationSpec = tween(250), label = "more_bg"
    )
    Box(
        modifier = Modifier
            .then(if (pill) Modifier.clip(RoundedCornerShape(22.dp)).background(bgColor)
                .padding(horizontal = if (isSelected && showLabel) 14.dp else 12.dp, vertical = 8.dp)
            else Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(bgColor).padding(vertical = 6.dp))
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
