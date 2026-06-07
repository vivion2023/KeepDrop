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

package com.cleansweep.domain.deletepool

import com.cleansweep.data.repository.DeletePoolRepository
import com.cleansweep.util.FileOperationsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class FinalDeleteResult(
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val permissionCount: Int,
    val alreadyGoneCount: Int,
    val deletedMediaKeys: List<String>
) {
    val allHandled: Boolean
        get() = failedCount == 0 && permissionCount == 0
}

@Singleton
class FinalDeleteUseCase @Inject constructor(
    private val deletePoolRepository: DeletePoolRepository,
    private val physicalDeleteExecutor: PhysicalDeleteExecutor,
    private val fileOperationsHelper: FileOperationsHelper
) {
    suspend fun deleteActiveEntries(mediaKeys: Set<String>): FinalDeleteResult = withContext(Dispatchers.IO) {
        val entries = deletePoolRepository.getActiveEntries()
            .filter { mediaKeys.isEmpty() || it.mediaKey in mediaKeys }

        if (entries.isEmpty()) {
            return@withContext FinalDeleteResult(
                totalCount = 0,
                successCount = 0,
                failedCount = 0,
                permissionCount = 0,
                alreadyGoneCount = 0,
                deletedMediaKeys = emptyList()
            )
        }

        deletePoolRepository.markDeleting(entries)

        var successCount = 0
        var failedCount = 0
        var permissionCount = 0
        var alreadyGoneCount = 0
        val pathsToScan = mutableListOf<String>()
        val deletedMediaKeys = mutableListOf<String>()

        entries.forEach { entry ->
            val result = physicalDeleteExecutor.delete(entry)
            deletePoolRepository.updateDeleteResult(
                entryId = entry.entryId,
                status = result.status,
                resultCode = result.resultCode,
                errorMessage = result.errorMessage
            )

            when {
                result.success -> {
                    successCount++
                    deletedMediaKeys.add(entry.mediaKey)
                    if (result.resultCode == DeletePoolResultCode.ALREADY_GONE) {
                        alreadyGoneCount++
                    }
                    result.pathToScan?.let { pathsToScan.add(it) }
                }
                result.status == DeletePoolStatus.NEEDS_PERMISSION -> permissionCount++
                else -> failedCount++
            }
        }

        if (pathsToScan.isNotEmpty()) {
            fileOperationsHelper.scanPaths(pathsToScan.distinct())
        }

        FinalDeleteResult(
            totalCount = entries.size,
            successCount = successCount,
            failedCount = failedCount,
            permissionCount = permissionCount,
            alreadyGoneCount = alreadyGoneCount,
            deletedMediaKeys = deletedMediaKeys
        )
    }
}
