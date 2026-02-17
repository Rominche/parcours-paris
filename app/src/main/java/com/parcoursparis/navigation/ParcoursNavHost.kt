package com.parcoursparis.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.parcoursparis.map.MapScreen
import com.parcoursparis.profile.ProfileScreen
import com.parcoursparis.settings.SettingsScreen

@Composable
fun ParcoursNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.MAP,
        modifier = modifier
    ) {
        composable(NavRoutes.MAP) { MapScreen() }
        composable(NavRoutes.PROFILE) { ProfileScreen() }
        composable(NavRoutes.SETTINGS) { SettingsScreen() }
    }
}
