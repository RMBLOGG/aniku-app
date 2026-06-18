package com.example.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun CurvedBottomNav(
    mainNavItems: List<Triple<String, String, ImageVector>>,
    currentRoute: String?,
    isSheetRouteActive: Boolean,
    hasUnreadChat: Boolean,
    onNavigate: (String) -> Unit,
    onMoreClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val allItems = listOf(
        Triple("home", "Home", Icons.Default.Home)
    ) + mainNavItems

    // Floating pill — tidak ada background di bawahnya
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pill container utama
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0xFF1C1C1E))
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(50.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allItems.forEach { (route, label, icon) ->
                val isSelected = currentRoute == route
                FloatingNavItem(
                    icon = icon,
                    label = label,
                    isSelected = isSelected,
                    primaryColor = primaryColor,
                    onClick = { onNavigate(route) }
                )
            }

            // Lainnya
            val moreSelected = isSheetRouteActive
            FloatingNavItemMore(
                isSelected = moreSelected,
                primaryColor = primaryColor,
                hasUnreadChat = hasUnreadChat,
                onClick = onMoreClick
            )
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2C2C2E) else Color.Transparent,
        animationSpec = tween(300, easing = EaseOutCubic),
        label = "nav_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
        animationSpec = tween(250),
        label = "nav_tint"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_scale"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (isSelected) 16.dp else 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // Active: icon + label inline horizontal
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                )
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            )
        }
    }
}

@Composable
private fun FloatingNavItemMore(
    isSelected: Boolean,
    primaryColor: Color,
    hasUnreadChat: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2C2C2E) else Color.Transparent,
        animationSpec = tween(300, easing = EaseOutCubic),
        label = "more_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
        animationSpec = tween(250),
        label = "more_tint"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (isSelected) 16.dp else 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "Lainnya",
                        tint = iconTint,
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
                Text(
                    "Lainnya",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        } else {
            Box {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "Lainnya",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
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
