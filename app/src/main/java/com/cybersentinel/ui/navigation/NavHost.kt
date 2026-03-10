package com.cybersentinel.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cybersentinel.ui.screens.apps.AppAuditScreen
import com.cybersentinel.ui.screens.email.EmailAnalyserScreen
import com.cybersentinel.ui.screens.hidden.HiddenAppScreen
import com.cybersentinel.ui.screens.wifi.WifiScannerScreen
import com.cybersentinel.ui.theme.NeonBlue
import com.cybersentinel.ui.theme.NeonGreen
import com.cybersentinel.ui.theme.OnSurfaceMuted
import com.cybersentinel.ui.theme.SurfaceDark

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Wifi   : Screen("wifi",   "WiFi",    Icons.Default.Wifi)
    object Apps   : Screen("apps",   "Apps",    Icons.Default.PhoneAndroid)
    object Hidden : Screen("hidden", "Hidden",  Icons.Default.Search)
    object Email  : Screen("email",  "Phishing",Icons.Default.Email)
}

val screens = listOf(Screen.Wifi, Screen.Apps, Screen.Hidden, Screen.Email)

@Composable
fun CyberSentinelNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                modifier = Modifier.height(64.dp)
            ) {
                screens.forEach { screen ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = NeonBlue,
                            selectedTextColor   = NeonBlue,
                            unselectedIconColor = OnSurfaceMuted,
                            unselectedTextColor = OnSurfaceMuted,
                            indicatorColor      = NeonBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Wifi.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Wifi.route)   { WifiScannerScreen() }
            composable(Screen.Apps.route)   { AppAuditScreen() }
            composable(Screen.Hidden.route) { HiddenAppScreen() }
            composable(Screen.Email.route)  { EmailAnalyserScreen() }
        }
    }
}
