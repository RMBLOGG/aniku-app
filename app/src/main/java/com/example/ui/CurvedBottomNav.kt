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
    val navHeight = 68.dp
    val fabSize = 58.dp
    val fabOverhang = 20.dp // seberapa jauh FAB menonjol ke atas dari nav

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(navHeight + fabOverhang),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Nav bar — rounded top corners, FAB cutout di tengah via canvas
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(navHeight)
                .align(Alignment.BottomCenter)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val topR = 28.dp.toPx()      // rounded corner atas kiri/kanan
            val cutR = 46.dp.toPx()      // radius lingkaran cutout FAB
            val cutDepth = 22.dp.toPx()  // seberapa dalam lekukan

            val path = Path().apply {
                // Mulai dari kiri bawah
                moveTo(0f, h)
                lineTo(0f, topR)
                // Rounded kiri atas
                quadraticBezierTo(0f, 0f, topR, 0f)
                // Garis ke kiri lekukan
                lineTo(cx - cutR - 16f, 0f)
                // Kurva turun ke lekukan kiri
                cubicTo(
                    cx - cutR + 8f, 0f,
                    cx - cutR * 0.5f, cutDepth,
                    cx, cutDepth
                )
                // Kurva naik dari lekukan kanan
                cubicTo(
                    cx + cutR * 0.5f, cutDepth,
                    cx + cutR - 8f, 0f,
                    cx + cutR + 16f, 0f
                )
                // Garis ke rounded kanan atas
                lineTo(w - topR, 0f)
                quadraticBezierTo(w, 0f, w, topR)
                lineTo(w, h)
                close()
            }
            drawPath(path, color = surfaceColor)
        }

        // Nav items row
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

            // Ruang untuk FAB
            Spacer(modifier = Modifier.weight(1.2f))

            // 1 item kanan (Bookmark)
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
                            Icons.Default.MoreHoriz,
                            contentDescription = "Lainnya",
                            tint = if (isSheetRouteActive) primaryColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }

        // FAB menonjol ke atas — posisi tepat di tengah, setengah badan di atas nav
        Box(
            modifier = Modifier
                .size(fabSize)
                .align(Alignment.TopCenter)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = primaryColor.copy(alpha = 0.3f),
                    spotColor = primaryColor.copy(alpha = 0.4f)
                )
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) primaryColor
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primaryColor
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        // Dot aktif
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) primaryColor else Color.Transparent)
        )
    }
}
