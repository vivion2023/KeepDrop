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

package com.cleansweep.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "delete_pool_entries",
    indices = [
        Index(value = ["status", "addedAt"]),
        Index(value = ["mediaKey"])
    ]
)
data class DeletePoolEntry(
    @PrimaryKey val entryId: String,
    val mediaKey: String,
    val locatorType: String,
    val uri: String?,
    val filePath: String?,
    val mediaType: String,
    val sizeSnapshot: Long,
    val modifiedSnapshot: Long,
    val status: String,
    val resultCode: String? = null,
    val errorMessage: String? = null,
    val addedAt: Long,
    val updatedAt: Long,
    val deleteStartedAt: Long? = null,
    val deleteFinishedAt: Long? = null
)
