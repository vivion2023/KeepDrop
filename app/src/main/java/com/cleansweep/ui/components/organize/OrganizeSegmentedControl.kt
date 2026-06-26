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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cleansweep.R
import com.cleansweep.data.repository.OrganizeSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeSegmentedControl(
    selectedSegment: OrganizeSegment,
    onSegmentSelected: (OrganizeSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = OrganizeSegment.entries

    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            SegmentedButton(
                selected = selectedSegment == segment,
                onClick = { onSegmentSelected(segment) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                label = {
                    Text(
                        when (segment) {
                            OrganizeSegment.DATE -> stringResource(R.string.organize_segment_date)
                            OrganizeSegment.ALBUM -> stringResource(R.string.organize_segment_album)
                        },
                    )
                },
            )
        }
    }
}