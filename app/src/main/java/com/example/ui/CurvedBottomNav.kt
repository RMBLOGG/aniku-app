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
    val navHeight = 64.dp
    val fabSize = 52.dp
    val fabElevation = 8.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(navHeight + fabSize / 2)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Nav bar background dengan canvas cutout
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter)
        ) {
            drawCurvedNavBackground(color = surfaceColor)
        }

        // Nav items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 2 item kiri
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

            // Spacer untuk FAB di tengah
            Spacer(modifier = Modifier.weight(1f))

            // 2 item kanan (atau 1 + Lainnya)
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
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Lainnya",
                            tint = if (isSheetRouteActive) primaryColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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

        // FAB melayang di tengah atas
        Box(
            modifier = Modifier
                .size(fabSize)
                .align(Alignment.TopCenter)
                .shadow(fabElevation, CircleShape)
                .clip(CircleShape)
                .background(primaryColor)
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

        // Dot indikator home aktif
        if (currentRoute == "home") {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-8).dp)
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
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) primaryColor
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primaryColor
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        // Dot indikator aktif
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) primaryColor else Color.Transparent
                )
        )
    }
}

private fun DrawScope.drawCurvedNavBackground(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cutR = 90f      // radius area cutout
    val curveDepth = 52f // seberapa dalam lekukan ke atas
    val topY = 0f

    val path = Path().apply {
        moveTo(0f, topY + 28f)
        // Rounded top-left
        quadraticBezierTo(0f, topY, 28f, topY)

        // Garis ke kiri kurva
        lineTo(cx - cutR - 20f, topY)

        // Kurva naik kiri
        cubicTo(
            cx - cutR + 10f, topY,
            cx - cutR * 0.5f, topY - curveDepth,
            cx, topY - curveDepth
        )

        // Kurva turun kanan
        cubicTo(
            cx + cutR * 0.5f, topY - curveDepth,
            cx + cutR - 10f, topY,
            cx + cutR + 20f, topY
        )

        // Garis ke top-right
        lineTo(w - 28f, topY)

        // Rounded top-right
        quadraticBezierTo(w, topY, w, topY + 28f)

        // Sisi kanan & bawah & kiri
        lineTo(w, h)
        lineTo(0f, h)
        lineTo(0f, topY + 28f)
        close()
    }

    drawPath(path = path, color = color)
}
