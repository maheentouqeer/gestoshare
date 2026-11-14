package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScreenRoute
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed class NavItem(
    val route: ScreenRoute,
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    data object Home : NavItem(ScreenRoute.HOME, "Home", Icons.Default.Home, "nav_home")
    data object Content : NavItem(ScreenRoute.CONTENT_SELECTION, "Content", Icons.AutoMirrored.Filled.Send, "nav_content")
    data object Devices : NavItem(ScreenRoute.NEARBY_DEVICES, "Devices", Icons.Default.Devices, "nav_devices")
    data object Gestures : NavItem(ScreenRoute.GESTURE_CONTROL, "Gestures", Icons.Default.PanTool, "nav_gestures")
    data object History : NavItem(ScreenRoute.HISTORY, "History", Icons.Default.History, "nav_history")
}

@Composable
fun AirShareBottomNav(
    currentRoute: ScreenRoute,
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem.Home,
        NavItem.Content,
        NavItem.Devices,
        NavItem.Gestures,
        NavItem.History
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xBA0C152B),
                        Color(0xE8070C18)
                    )
                )
            )
    ) {
        // Specular frosted glass top rim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.22f),
                            NeonCyan.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        NavigationBar(
            modifier = Modifier.testTag("airshare_bottom_nav"),
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = NeonCyan,
                        indicatorColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag(item.testTag)
                )
            }
        }
    }
}
