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

package com.cleansweep.data.repository

import com.cleansweep.data.db.dao.DeletePoolDao
import com.cleansweep.data.db.entity.DeletePoolEntry
import com.cleansweep.domain.deletepool.DeletePoolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletePoolRepository @Inject constructor(
    private val deletePoolDao: DeletePoolDao
) {
    fun observeActiveEntries(): Flow<List<DeletePoolEntry>> {
        return deletePoolDao.observeEntriesByStatus(DeletePoolStatus.IN_POOL)
    }

    suspend fun getActiveEntries(): List<DeletePoolEntry> = withContext(Dispatchers.IO) {
        deletePoolDao.getEntriesByStatus(DeletePoolStatus.IN_POOL)
    }

    suspend fun getActiveMediaKeys(): Set<String> = withContext(Dispatchers.IO) {
        deletePoolDao.getMediaKeysByStatus(DeletePoolStatus.IN_POOL).toSet()
    }

    suspend fun addIfAbsent(entry: DeletePoolEntry): Boolean = withContext(Dispatchers.IO) {
        val existing = deletePoolDao.getEntry(entry.mediaKey, DeletePoolStatus.IN_POOL)
        if (existing != null) {
            false
        } else {
            deletePoolDao.insert(entry) != -1L
        }
    }

    suspend fun removeActive(mediaKey: String) = withContext(Dispatchers.IO) {
        deletePoolDao.deleteByMediaKeyAndStatus(mediaKey, DeletePoolStatus.IN_POOL)
    }

    suspend fun clearAllActive() = withContext(Dispatchers.IO) {
        deletePoolDao.deleteAllByStatus(DeletePoolStatus.IN_POOL)
    }

    suspend fun removeActiveByIds(entryIds: List<String>) = withContext(Dispatchers.IO) {
        if (entryIds.isNotEmpty()) {
            deletePoolDao.deleteByIds(entryIds)
        }
    }

    suspend fun markDeleting(entries: List<DeletePoolEntry>) = withContext(Dispatchers.IO) {
        if (entries.isNotEmpty()) {
            val now = System.currentTimeMillis()
            deletePoolDao.markDeleting(
                entryIds = entries.map { it.entryId },
                expectedStatus = DeletePoolStatus.IN_POOL,
                newStatus = DeletePoolStatus.DELETING,
                updatedAt = now,
                deleteStartedAt = now
            )
        }
    }

    suspend fun updateDeleteResult(
        entryId: String,
        status: String,
        resultCode: String?,
        errorMessage: String?
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        deletePoolDao.updateDeleteResult(
            entryId = entryId,
            status = status,
            resultCode = resultCode,
            errorMessage = errorMessage,
            updatedAt = now,
            deleteFinishedAt = now
        )
    }
}
