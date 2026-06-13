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

@Composable
fun CurvedBottomNav(
    mainNavItems: List<Triple<String, String, ImageVector>>,
    currentRoute: String?,
    isSheetRouteActive: Boolean,
    hasUnreadChat: Boolean,
    onNavigate: (String) -> Unit,
    onMoreClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val navHeight = 72.dp
    val fabSize = 60.dp
    val cutoutR = 40f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Nav background dengan cutout canvas
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val depth = cutoutR * 1.15f

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(cx - cutoutR * 2.2f, 0f)
                cubicTo(
                    cx - cutoutR * 1.1f, 0f,
                    cx - cutoutR * 0.6f, -depth,
                    cx, -depth
                )
                cubicTo(
                    cx + cutoutR * 0.6f, -depth,
                    cx + cutoutR * 1.1f, 0f,
                    cx + cutoutR * 2.2f, 0f
                )
                lineTo(w, 0f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path, color = surfaceColor)
        }

        // Nav item row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Kiri: Cari, Eksplor
            mainNavItems.take(2).forEach { (route, label, icon) ->
                NavItem(
                    icon = icon,
                    label = label,
                    isSelected = currentRoute == route,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(route) }
                )
            }

            // Space FAB tengah
            Spacer(modifier = Modifier.weight(1.2f))

            // Kanan: Bookmark, Lainnya
            mainNavItems.drop(2).forEach { (route, label, icon) ->
                NavItem(
                    icon = icon,
                    label = label,
                    isSelected = currentRoute == route,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(route) }
                )
            }

            // Lainnya
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onMoreClick() }
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Lainnya",
                            tint = if (isSheetRouteActive) primaryColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        if (hasUnreadChat) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                    Text(
                        "Lainnya",
                        fontSize = 10.sp,
                        fontWeight = if (isSheetRouteActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSheetRouteActive) primaryColor
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // FAB
        Box(
            modifier = Modifier
                .size(fabSize)
                .align(Alignment.TopCenter)
                .offset(y = 8.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(primaryColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onNavigate("home") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = "Home",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) primaryColor else Color.Transparent)
        )
    }
}
