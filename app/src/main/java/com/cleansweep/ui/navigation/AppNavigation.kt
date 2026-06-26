/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.cleansweep.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.cleansweep.ui.screens.onboarding.OnboardingScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val MAIN_SHELL_ROUTE = "main_shell"
const val DUPLICATES_GRAPH_ROUTE = "duplicates_graph"
private const val DEEP_LINK_URI_BASE = "app://com.cleansweep"
const val RESET_SEARCH_RESULT_KEY = "reset_search_result"

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")

    object MainShell : Screen("$MAIN_SHELL_ROUTE?tab={tab}&forceRefresh={forceRefresh}") {
        fun createRoute(
            tab: MainTab = MainTab.Organize,
            forceRefresh: Boolean = false,
        ): String = "$MAIN_SHELL_ROUTE?tab=${tab.route}&forceRefresh=$forceRefresh"
    }

    object Swiper : Screen("swiper/{bucketIds}") {
        fun createRoute(bucketIds: List<String>): String {
            val encodedPaths = bucketIds.joinToString("|") { path ->
                android.util.Base64.encodeToString(
                    path.toByteArray(StandardCharsets.UTF_8),
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
                )
            }
            return "swiper/${URLEncoder.encode(encodedPaths, StandardCharsets.UTF_8.toString())}"
        }
    }

    object Settings : Screen("settings")
    object Libraries : Screen("libraries")

    object Duplicates : Screen("duplicates_overview")
    object GroupDetails : Screen("duplicates_group_details/{groupId}") {
        fun createRoute(groupId: String): String {
            val encodedGroupId = URLEncoder.encode(groupId, StandardCharsets.UTF_8.toString())
            return "duplicates_group_details/$encodedGroupId"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    windowSizeClass: WindowSizeClass,
    startDestination: String = Screen.Onboarding.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.MainShell.createRoute(forceRefresh = true)) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Screen.MainShell.route,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = MainTab.Organize.route
                },
                navArgument("forceRefresh") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val tabRoute = backStackEntry.arguments?.getString("tab") ?: MainTab.Organize.route
            val initialTab = MainTab.fromRoute(tabRoute) ?: MainTab.Organize
            val forceRefresh = backStackEntry.arguments?.getBoolean("forceRefresh") == true

            MainShell(
                windowSizeClass = windowSizeClass,
                initialTab = initialTab,
                forceRefreshOrganize = forceRefresh,
            )
        }

        composable(
            route = DUPLICATES_GRAPH_ROUTE,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$DEEP_LINK_URI_BASE/$DUPLICATES_GRAPH_ROUTE" },
            ),
        ) {
            LaunchedEffect(Unit) {
                navController.navigate(Screen.MainShell.createRoute(tab = MainTab.Duplicates)) {
                    popUpTo(DUPLICATES_GRAPH_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
}