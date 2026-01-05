package com.example.telecamera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.telecamera.ui.screens.CameraScreen
import com.example.telecamera.ui.screens.HomeScreen
import com.example.telecamera.ui.screens.RemoteScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera")
    object Remote : Screen("remote")
}

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCameraClick = {
                    navController.navigate(Screen.Camera.route)
                },
                onRemoteClick = {
                    navController.navigate(Screen.Remote.route)
                }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Remote.route) {
            RemoteScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

