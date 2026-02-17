package com.parcoursparis

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.parcoursparis.navigation.BottomNavBar
import com.parcoursparis.navigation.ParcoursNavHost

@Composable
fun ParcoursParisApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavBar(navController = navController) }
    ) { innerPadding ->
        ParcoursNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
