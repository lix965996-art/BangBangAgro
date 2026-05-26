package com.bangbangagro.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.bangbangagro.app.navigation.Routes

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val MainBottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "首页", Icons.Default.Home),
    BottomNavItem(Routes.FARMLAND, "农田", Icons.Default.Grass),
    BottomNavItem(Routes.MONITOR, "监测", Icons.Default.DeviceThermostat),
    BottomNavItem(Routes.ALERT, "预警", Icons.Default.Notifications),
    BottomNavItem(Routes.PROFILE, "我的", Icons.Default.Person)
)

@Composable
fun AgroBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        MainBottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { if (currentRoute != item.route) onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
