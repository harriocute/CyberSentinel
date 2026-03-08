package com.cybersentinel.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.cybersentinel.ui.screens.apps.AppAuditScreen
import com.cybersentinel.ui.screens.email.EmailAnalyserScreen
import com.cybersentinel.ui.screens.hidden.HiddenAppScreen
import com.cybersentinel.ui.screens.wifi.WifiScannerScreen
import com.cybersentinel.ui.theme.*

// ── Navigation Destinations ────────────────────────────────────────────────

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Wifi     : Screen("wifi",     "WiFi",        Icons.Default.Wifi)
    object Apps     : Screen("apps",     "Apps",        Icons.Default.Apps)
    object Hidden   : Screen("hidden",   "Hidden",      Icons.Default.VisibilityOff)
    object Email    : Screen("email",    "Email",       Icons.Default.Email)
}

private val bottomNavItems = listOf(
    Screen.Wifi,
    Screen.Apps,
    Screen.Hidden,
    Screen.Email
)

// ── Root Navigation Composable ────────────────────────────────────────────

@Composable
fun CyberSentinelNavHost() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            CyberBottomNav(navController = navController)
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

// ── Bottom Navigation Bar ──────────────────────────────────────────────────

@Composable
private fun CyberBottomNav(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = SurfaceDark,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

            NavigationBarItem(
                selected = selected,
                onClick  = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                icon = {
                    Icon(
                        imageVector      = screen.icon,
                        contentDescription = screen.label
                    )
                },
                label = {
                    Text(
                        text     = screen.label.uppercase(),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = NeonGreen,
                    selectedTextColor   = NeonGreen,
                    unselectedIconColor = OnSurfaceMuted,
                    unselectedTextColor = OnSurfaceMuted,
                    indicatorColor      = NeonGreen.copy(alpha = 0.12f)
                )
            )
        }
    }
}

// ── Placeholder for future screens ────────────────────────────────────────

@Composable
private fun PlaceholderScreen(title: String) {
    androidx.compose.foundation.layout.Box(
        modifier            = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment    = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Build, null,
                tint     = NeonBlue,
                modifier = androidx.compose.ui.Modifier.size(56.dp)
            )
            androidx.compose.foundation.layout.Spacer(
                androidx.compose.ui.Modifier.height(16.dp)
            )
            Text(
                text  = title,
                style = MaterialTheme.typography.titleLarge.copy(color = NeonBlue)
            )
            Text(
                "Coming in the next step",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
