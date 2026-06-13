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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val fabSize = 56.dp
    val navHeight = 64.dp
    val cutoutRadius = 36.dp

    // Split nav items: 2 kiri + 2 kanan, FAB di tengah
    val leftItems = mainNavItems.take(2)
    val rightItems = mainNavItems.drop(2)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Background nav dengan cutout lengkung di tengah
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter)
                .drawBehind {
                    drawCurvedNav(
                        color = surfaceColor,
                        cutoutRadius = cutoutRadius.toPx(),
                        cornerRadius = 24.dp.toPx()
                    )
                }
        )

        // Nav items row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kiri
            leftItems.forEach { (route, label, icon) ->
                val isSelected = currentRoute == route
                NavItem(
                    icon = icon,
                    label = label,
                    isSelected = isSelected,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(route) }
                )
            }

            // Spacer tengah untuk FAB
            Spacer(modifier = Modifier.width(fabSize + 16.dp))

            // Kanan
            rightItems.forEach { (route, label, icon) ->
                val isSelected = currentRoute == route
                NavItem(
                    icon = icon,
                    label = label,
                    isSelected = isSelected,
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(route) }
                )
            }

            // Tombol Lainnya
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onMoreClick() }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
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
                        text = "Lainnya",
                        fontSize = 10.sp,
                        fontWeight = if (isSheetRouteActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSheetRouteActive) primaryColor
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // FAB melayang di tengah
        Box(
            modifier = Modifier
                .size(fabSize)
                .align(Alignment.TopCenter)
                .offset(y = (-fabSize / 2) + 8.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor,
                            primaryColor.copy(red = primaryColor.red * 0.8f)
                        )
                    )
                )
                .shadow(12.dp, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onNavigate("home") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Home",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // Dot indicator kalau FAB/home aktif
        if (currentRoute == "home") {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-6).dp)
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isSelected) Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(primaryColor.copy(alpha = 0.12f))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

private fun DrawScope.drawCurvedNav(
    color: Color,
    cutoutRadius: Float,
    cornerRadius: Float
) {
    val width = size.width
    val height = size.height
    val centerX = width / 2f
    val cutoutTop = 0f
    val curveWidth = cutoutRadius * 2.2f

    val path = Path().apply {
        // Start top-left dengan rounded corner
        moveTo(cornerRadius, cutoutTop)

        // Garis ke awal kurva kiri
        lineTo(centerX - curveWidth, cutoutTop)

        // Kurva kiri (naik ke atas untuk cutout)
        cubicTo(
            centerX - curveWidth + cutoutRadius * 0.6f, cutoutTop,
            centerX - cutoutRadius, cutoutTop - cutoutRadius * 0.9f,
            centerX, cutoutTop - cutoutRadius * 0.9f
        )

        // Kurva kanan (turun kembali)
        cubicTo(
            centerX + cutoutRadius, cutoutTop - cutoutRadius * 0.9f,
            centerX + curveWidth - cutoutRadius * 0.6f, cutoutTop,
            centerX + curveWidth, cutoutTop
        )

        // Garis ke top-right
        lineTo(width - cornerRadius, cutoutTop)

        // Corner kanan atas
        quadraticBezierTo(width, cutoutTop, width, cutoutTop + cornerRadius)

        // Sisi kanan
        lineTo(width, height)

        // Bawah
        lineTo(0f, height)

        // Sisi kiri
        lineTo(0f, cutoutTop + cornerRadius)

        // Corner kiri atas
        quadraticBezierTo(0f, cutoutTop, cornerRadius, cutoutTop)

        close()
    }

    drawPath(path = path, color = color)

    // Shadow tipis di atas nav
    drawPath(
        path = Path().apply {
            moveTo(0f, cutoutTop)
            lineTo(centerX - curveWidth, cutoutTop)
            cubicTo(
                centerX - curveWidth + cutoutRadius * 0.6f, cutoutTop,
                centerX - cutoutRadius, cutoutTop - cutoutRadius * 0.9f,
                centerX, cutoutTop - cutoutRadius * 0.9f
            )
            cubicTo(
                centerX + cutoutRadius, cutoutTop - cutoutRadius * 0.9f,
                centerX + curveWidth - cutoutRadius * 0.6f, cutoutTop,
                centerX + curveWidth, cutoutTop
            )
            lineTo(width, cutoutTop)
        },
        color = Color.Black.copy(alpha = 0.08f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
    )
}
