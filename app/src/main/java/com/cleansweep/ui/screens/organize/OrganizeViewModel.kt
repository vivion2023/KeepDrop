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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleansweep.data.repository.OrganizeSegment
import com.cleansweep.data.repository.OrganizeViewMode
import com.cleansweep.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _segment = MutableStateFlow(OrganizeSegment.ALBUM)
    val segment: StateFlow<OrganizeSegment> = _segment.asStateFlow()

    val viewMode: StateFlow<OrganizeViewMode> = preferencesRepository.organizeViewModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OrganizeViewMode.CARD,
        )

    fun selectSegment(segment: OrganizeSegment) {
        _segment.value = segment
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val next = when (viewMode.value) {
                OrganizeViewMode.CARD -> OrganizeViewMode.LIST
                OrganizeViewMode.LIST -> OrganizeViewMode.CARD
            }
            preferencesRepository.setOrganizeViewMode(next)
        }
    }
}