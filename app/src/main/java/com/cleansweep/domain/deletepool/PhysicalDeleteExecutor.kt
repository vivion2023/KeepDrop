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

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.cleansweep.data.db.entity.DeletePoolEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class PhysicalDeleteResult(
    val success: Boolean,
    val status: String,
    val resultCode: String,
    val errorMessage: String? = null,
    val pathToScan: String? = null
)

@Singleton
class PhysicalDeleteExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun delete(entry: DeletePoolEntry): PhysicalDeleteResult {
        return when (entry.locatorType) {
            DeletePoolLocatorType.MEDIASTORE_URI -> deleteMediaStore(entry)
            DeletePoolLocatorType.SAF_URI -> deleteSaf(entry)
            else -> deleteFilePath(entry)
        }
    }

    private fun deleteFilePath(entry: DeletePoolEntry): PhysicalDeleteResult {
        val path = entry.filePath ?: entry.uri?.removePrefix("file://")
        if (path.isNullOrBlank()) {
            return failed(DeletePoolResultCode.FAILED, "Missing file path")
        }

        return try {
            val file = File(path)
            if (!file.exists()) {
                return deleted(DeletePoolResultCode.ALREADY_GONE, path)
            }
            if (hasChanged(file, entry)) {
                return failed(DeletePoolResultCode.FILE_CHANGED, "File changed after it was added to the delete pool")
            }
            if (file.delete()) {
                deleted(DeletePoolResultCode.DELETED, path)
            } else {
                failed(DeletePoolResultCode.FAILED, "File.delete() returned false")
            }
        } catch (security: SecurityException) {
            needsPermission(security.message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file path entry ${entry.entryId}", e)
            failed(DeletePoolResultCode.FAILED, e.message)
        }
    }

    private fun deleteMediaStore(entry: DeletePoolEntry): PhysicalDeleteResult {
        val uri = entry.locatorUri() ?: return failed(DeletePoolResultCode.FAILED, "Missing MediaStore URI")
        return try {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            if (deletedRows > 0) {
                deleted(DeletePoolResultCode.DELETED, entry.filePath)
            } else {
                deleted(DeletePoolResultCode.ALREADY_GONE, entry.filePath)
            }
        } catch (security: SecurityException) {
            needsPermission(security.message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete MediaStore entry ${entry.entryId}", e)
            failed(DeletePoolResultCode.FAILED, e.message)
        }
    }

    private fun deleteSaf(entry: DeletePoolEntry): PhysicalDeleteResult {
        val uri = entry.locatorUri() ?: return failed(DeletePoolResultCode.FAILED, "Missing SAF URI")
        return try {
            val document = DocumentFile.fromSingleUri(context, uri)
            if (document == null || !document.exists()) {
                return deleted(DeletePoolResultCode.ALREADY_GONE, entry.filePath)
            }
            if (document.delete()) {
                deleted(DeletePoolResultCode.DELETED, entry.filePath)
            } else {
                failed(DeletePoolResultCode.FAILED, "DocumentFile.delete() returned false")
            }
        } catch (security: SecurityException) {
            needsPermission(security.message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SAF entry ${entry.entryId}", e)
            failed(DeletePoolResultCode.FAILED, e.message)
        }
    }

    private fun hasChanged(file: File, entry: DeletePoolEntry): Boolean {
        if (entry.sizeSnapshot >= 0 && file.length() != entry.sizeSnapshot) return true
        val snapshotModified = entry.modifiedSnapshot
        val currentModified = file.lastModified()
        if (snapshotModified <= 0 || currentModified <= 0) return false

        // Some MediaStore values are seconds while File values are milliseconds.
        val sameMilliseconds = abs(currentModified - snapshotModified) <= 2_000L
        val sameSeconds = abs((currentModified / 1_000L) - snapshotModified) <= 2L
        return !sameMilliseconds && !sameSeconds
    }

    private fun deleted(resultCode: String, pathToScan: String?) = PhysicalDeleteResult(
        success = true,
        status = DeletePoolStatus.DELETED,
        resultCode = resultCode,
        pathToScan = pathToScan
    )

    private fun failed(resultCode: String, message: String?) = PhysicalDeleteResult(
        success = false,
        status = DeletePoolStatus.FAILED,
        resultCode = resultCode,
        errorMessage = message
    )

    private fun needsPermission(message: String?) = PhysicalDeleteResult(
        success = false,
        status = DeletePoolStatus.NEEDS_PERMISSION,
        resultCode = DeletePoolResultCode.PERMISSION_REQUIRED,
        errorMessage = message
    )

    private companion object {
        const val TAG = "PhysicalDeleteExecutor"
    }
}
