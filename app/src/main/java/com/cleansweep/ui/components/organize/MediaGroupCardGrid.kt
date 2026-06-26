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

package com.cleansweep.ui.components.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cleansweep.R
import com.cleansweep.domain.model.MonthGroup
import com.cleansweep.domain.model.YearMonthSection
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthGroupCardGrid(
    sections: List<YearMonthSection>,
    onMonthClick: (MonthGroup) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { section ->
            item(key = "year-${section.year}") {
                Text(
                    text = stringResource(R.string.organize_year_label, section.year),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            item(key = "grid-${section.year}") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(section.months, key = { "${it.year}-${it.month}" }) { month ->
                        val monthLabel = Month.of(month.month)
                            .getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
                        val subtitle = pluralStringResource(
                            R.plurals.organize_media_count,
                            month.itemCount,
                            month.itemCount,
                        )

                        MediaCoverCard(
                            title = monthLabel,
                            subtitle = subtitle,
                            coverUri = month.coverMedia?.uri,
                            isVideo = month.coverMedia?.isVideo == true,
                            isSelected = false,
                            onClick = { onMonthClick(month) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthGroupList(
    sections: List<YearMonthSection>,
    onMonthClick: (MonthGroup) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            item(key = "year-header-${section.year}") {
                Text(
                    text = stringResource(R.string.organize_year_label, section.year),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(section.months, key = { "${it.year}-${it.month}" }) { month ->
                val monthLabel = Month.of(month.month)
                    .getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                val subtitle = pluralStringResource(
                    R.plurals.organize_media_count,
                    month.itemCount,
                    month.itemCount,
                )

                MediaCoverListRow(
                    title = monthLabel,
                    subtitle = subtitle,
                    coverUri = month.coverMedia?.uri,
                    isVideo = month.coverMedia?.isVideo == true,
                    isSelected = false,
                    onClick = { onMonthClick(month) },
                )
            }
        }
    }
}