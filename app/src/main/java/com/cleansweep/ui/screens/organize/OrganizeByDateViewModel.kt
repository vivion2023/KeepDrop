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
import com.cleansweep.domain.model.YearMonthSection
import com.cleansweep.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrganizeByDateUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sections: List<YearMonthSection> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class OrganizeByDateViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizeByDateUiState())
    val uiState: StateFlow<OrganizeByDateUiState> = _uiState.asStateFlow()

    init {
        loadMonths()
    }

    fun loadMonths() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.sections.isEmpty(), error = null) }
            try {
                val sections = mediaRepository.getMediaGroupedByMonth()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        sections = sections,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadMonths()
        }
    }
}