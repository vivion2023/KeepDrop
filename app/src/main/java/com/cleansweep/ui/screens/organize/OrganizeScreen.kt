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

package com.cleansweep.ui.screens.organize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleansweep.R
import com.cleansweep.data.repository.OrganizeSegment
import com.cleansweep.data.repository.OrganizeViewMode
import com.cleansweep.domain.model.MonthGroup
import com.cleansweep.ui.components.organize.OrganizeSegmentedControl
import com.cleansweep.ui.screens.session.SessionSetupScreen
import com.cleansweep.ui.screens.session.SessionSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    windowSizeClass: WindowSizeClass,
    forceRefresh: Boolean,
    onStartSession: (List<String>) -> Unit,
    onStartMonthSession: (year: Int, month: Int) -> Unit,
    organizeViewModel: OrganizeViewModel = hiltViewModel(),
    folderViewModel: SessionSetupViewModel = hiltViewModel(),
) {
    val segment by organizeViewModel.segment.collectAsStateWithLifecycle()
    val viewMode by organizeViewModel.viewMode.collectAsStateWithLifecycle()

    LaunchedEffect(forceRefresh) {
        if (forceRefresh) {
            folderViewModel.refreshFolders()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            OrganizeTopAppBar(
                viewMode = viewMode,
                onToggleViewMode = organizeViewModel::toggleViewMode,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            OrganizeSegmentedControl(
                selectedSegment = segment,
                onSegmentSelected = organizeViewModel::selectSegment,
            )

            when (segment) {
                OrganizeSegment.DATE -> {
                    OrganizeByDateScreen(
                        viewMode = viewMode,
                        onMonthClick = { month: MonthGroup ->
                            onStartMonthSession(month.year, month.month)
                        },
                    )
                }

                OrganizeSegment.ALBUM -> {
                    SessionSetupScreen(
                        windowSizeClass = windowSizeClass,
                        showShellNavigationActions = false,
                        hideTopBar = true,
                        organizeViewMode = viewMode,
                        onStartSession = onStartSession,
                        viewModel = folderViewModel,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizeTopAppBar(
    viewMode: OrganizeViewMode,
    onToggleViewMode: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.nav_tab_organize)) },
        actions = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip {
                        Text(
                            if (viewMode == OrganizeViewMode.CARD) {
                                stringResource(R.string.organize_view_list)
                            } else {
                                stringResource(R.string.organize_view_cards)
                            },
                        )
                    }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        imageVector = if (viewMode == OrganizeViewMode.CARD) {
                            Icons.AutoMirrored.Outlined.ViewList
                        } else {
                            Icons.Outlined.ViewModule
                        },
                        contentDescription = if (viewMode == OrganizeViewMode.CARD) {
                            stringResource(R.string.organize_view_list)
                        } else {
                            stringResource(R.string.organize_view_cards)
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        ),
    )
}