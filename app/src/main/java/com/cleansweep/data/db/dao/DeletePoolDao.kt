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

package com.cleansweep.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cleansweep.data.db.entity.DeletePoolEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletePoolDao {
    @Query("SELECT * FROM delete_pool_entries WHERE status = :status ORDER BY addedAt DESC")
    fun observeEntriesByStatus(status: String): Flow<List<DeletePoolEntry>>

    @Query("SELECT * FROM delete_pool_entries WHERE status = :status ORDER BY addedAt ASC")
    suspend fun getEntriesByStatus(status: String): List<DeletePoolEntry>

    @Query("SELECT * FROM delete_pool_entries WHERE mediaKey = :mediaKey AND status = :status LIMIT 1")
    suspend fun getEntry(mediaKey: String, status: String): DeletePoolEntry?

    @Query("SELECT mediaKey FROM delete_pool_entries WHERE status = :status")
    suspend fun getMediaKeysByStatus(status: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DeletePoolEntry): Long

    @Query("DELETE FROM delete_pool_entries WHERE mediaKey = :mediaKey AND status = :status")
    suspend fun deleteByMediaKeyAndStatus(mediaKey: String, status: String)

    @Query("DELETE FROM delete_pool_entries WHERE entryId IN (:entryIds)")
    suspend fun deleteByIds(entryIds: List<String>)

    @Query("UPDATE delete_pool_entries SET status = :newStatus, updatedAt = :updatedAt, deleteStartedAt = :deleteStartedAt WHERE entryId IN (:entryIds) AND status = :expectedStatus")
    suspend fun markDeleting(entryIds: List<String>, expectedStatus: String, newStatus: String, updatedAt: Long, deleteStartedAt: Long)

    @Query("UPDATE delete_pool_entries SET status = :status, resultCode = :resultCode, errorMessage = :errorMessage, updatedAt = :updatedAt, deleteFinishedAt = :deleteFinishedAt WHERE entryId = :entryId")
    suspend fun updateDeleteResult(
        entryId: String,
        status: String,
        resultCode: String?,
        errorMessage: String?,
        updatedAt: Long,
        deleteFinishedAt: Long
    )
}
