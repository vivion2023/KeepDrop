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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cleansweep.ui.screens.duplicates.DuplicatesScreen
import com.cleansweep.ui.screens.duplicates.DuplicatesViewModel
import com.cleansweep.ui.screens.duplicates.GroupDetailsScreen
import com.cleansweep.ui.screens.gallery.GalleryPlaceholderScreen
import com.cleansweep.ui.screens.osslicenses.OpenSourceLicensesScreen
import com.cleansweep.ui.screens.organize.OrganizeScreen
import com.cleansweep.ui.screens.session.SessionSetupViewModel
import com.cleansweep.ui.screens.settings.SettingsScreen
import com.cleansweep.ui.screens.swiper.SwiperScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    windowSizeClass: WindowSizeClass,
    initialTab: MainTab = MainTab.Organize,
    forceRefreshOrganize: Boolean = false,
    innerNavController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in MainTab.tabRoutes

    LaunchedEffect(initialTab) {
        if (initialTab != MainTab.Organize) {
            innerNavController.navigate(initialTab.route) {
                popUpTo(innerNavController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateToTab(tab: MainTab) {
        innerNavController.navigate(tab.route) {
            popUpTo(innerNavController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                MainBottomNavigationBar(
                    currentRoute = currentRoute,
                    onTabSelected = ::navigateToTab,
                )
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = innerNavController,
            startDestination = MainTab.Organize.route,
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
        ) {
            composable(MainTab.Duplicates.route) { backStackEntry ->
                val viewModel = hiltViewModel<DuplicatesViewModel>(backStackEntry)

                DuplicatesScreen(
                    viewModel = viewModel,
                    showBackNavigation = false,
                    onNavigateUp = {},
                    onNavigateToGroup = { groupId ->
                        innerNavController.navigate(Screen.GroupDetails.createRoute(groupId))
                    },
                    onNavigateToSettings = { navigateToTab(MainTab.Settings) },
                )
            }

            composable(MainTab.Organize.route) { backStackEntry ->
                val folderViewModel = hiltViewModel<SessionSetupViewModel>()

                val result by backStackEntry
                    .savedStateHandle
                    .getStateFlow(RESET_SEARCH_RESULT_KEY, false)
                    .collectAsStateWithLifecycle()

                LaunchedEffect(result) {
                    if (result) {
                        folderViewModel.handleResetResult()
                    }
                }

                OrganizeScreen(
                    windowSizeClass = windowSizeClass,
                    forceRefresh = forceRefreshOrganize,
                    onStartSession = { bucketIds ->
                        folderViewModel.saveSelectedBucketsPreference()
                        innerNavController.navigate(Screen.Swiper.createRoute(bucketIds))
                    },
                    onStartMonthSession = { year, month ->
                        innerNavController.navigate(Screen.SwiperMonth.createRoute(year, month))
                    },
                    folderViewModel = folderViewModel,
                )
            }

            composable(MainTab.Gallery.route) {
                GalleryPlaceholderScreen()
            }

            composable(MainTab.Settings.route) {
                SettingsScreen(
                    showBackNavigation = false,
                    onNavigateUp = {},
                    onNavigateToLibraries = {
                        innerNavController.navigate(Screen.Libraries.route)
                    },
                )
            }

            composable(
                route = Screen.Swiper.route,
                arguments = listOf(navArgument("bucketIds") { type = NavType.StringType }),
            ) { backStackEntry ->
                val encodedBucketIds = backStackEntry.arguments?.getString("bucketIds") ?: ""
                val bucketIds = decodeBucketIds(encodedBucketIds)

                SwiperScreen(
                    windowSizeClass = windowSizeClass,
                    bucketIds = bucketIds,
                    onNavigateUp = { innerNavController.navigateUp() },
                    onNavigateUpAndReset = {
                        innerNavController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(RESET_SEARCH_RESULT_KEY, true)
                        innerNavController.popBackStack()
                    },
                    onNavigateToSettings = { navigateToTab(MainTab.Settings) },
                    onNavigateToDuplicates = { navigateToTab(MainTab.Duplicates) },
                )
            }

            composable(
                route = Screen.SwiperMonth.route,
                arguments = listOf(
                    navArgument("year") { type = NavType.IntType },
                    navArgument("month") { type = NavType.IntType },
                ),
            ) { backStackEntry ->
                val year = backStackEntry.arguments?.getInt("year") ?: return@composable
                val month = backStackEntry.arguments?.getInt("month") ?: return@composable

                SwiperScreen(
                    windowSizeClass = windowSizeClass,
                    monthYear = year to month,
                    onNavigateUp = { innerNavController.navigateUp() },
                    onNavigateUpAndReset = { innerNavController.popBackStack() },
                    onNavigateToSettings = { navigateToTab(MainTab.Settings) },
                    onNavigateToDuplicates = { navigateToTab(MainTab.Duplicates) },
                )
            }

            composable(
                route = Screen.GroupDetails.route,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    innerNavController.getBackStackEntry(MainTab.Duplicates.route)
                }
                val viewModel = hiltViewModel<DuplicatesViewModel>(parentEntry)
                val encodedGroupId = backStackEntry.arguments?.getString("groupId") ?: ""
                val groupId = URLDecoder.decode(encodedGroupId, StandardCharsets.UTF_8.toString())

                GroupDetailsScreen(
                    viewModel = viewModel,
                    groupId = groupId,
                    onNavigateUp = { innerNavController.navigateUp() },
                )
            }

            composable(Screen.Libraries.route) {
                OpenSourceLicensesScreen(
                    onNavigateUp = { innerNavController.navigateUp() },
                )
            }
        }
    }
}

@Composable
private fun MainBottomNavigationBar(
    currentRoute: String?,
    onTabSelected: (MainTab) -> Unit,
) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

private fun decodeBucketIds(encodedBucketIds: String): List<String> {
    return try {
        if (encodedBucketIds.isNotEmpty()) {
            val decodedString = URLDecoder.decode(encodedBucketIds, StandardCharsets.UTF_8.toString())
            decodedString.split("|").map { encodedPath ->
                String(
                    android.util.Base64.decode(encodedPath, android.util.Base64.URL_SAFE),
                    StandardCharsets.UTF_8,
                )
            }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}