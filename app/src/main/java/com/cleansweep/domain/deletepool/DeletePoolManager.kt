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

import android.net.Uri
import com.cleansweep.data.db.entity.DeletePoolEntry
import com.cleansweep.data.model.MediaItem
import com.cleansweep.data.repository.DeletePoolRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletePoolManager @Inject constructor(
    private val deletePoolRepository: DeletePoolRepository
) {
    suspend fun add(item: MediaItem): Boolean {
        return deletePoolRepository.addIfAbsent(item.toDeletePoolEntry())
    }

    suspend fun addAll(items: List<MediaItem>) {
        items.forEach { add(it) }
    }

    suspend fun restore(item: MediaItem) {
        deletePoolRepository.removeActive(item.mediaKey())
    }

    suspend fun clearAllActive() {
        deletePoolRepository.clearAllActive()
    }

    suspend fun getActiveMediaKeys(): Set<String> {
        return deletePoolRepository.getActiveMediaKeys()
    }
}

fun MediaItem.mediaKey(): String = id.ifBlank { uri.toString() }

private fun MediaItem.toDeletePoolEntry(): DeletePoolEntry {
    val now = System.currentTimeMillis()
    val locatorType = when {
        uri.scheme == "file" -> DeletePoolLocatorType.FILE_PATH
        uri.scheme == "content" && uri.authority?.contains("media", ignoreCase = true) == true -> DeletePoolLocatorType.MEDIASTORE_URI
        uri.scheme == "content" -> DeletePoolLocatorType.SAF_URI
        else -> DeletePoolLocatorType.FILE_PATH
    }
    val path = when (locatorType) {
        DeletePoolLocatorType.FILE_PATH -> filePath.ifBlank { id }
        else -> filePath.ifBlank { null }
    }

    return DeletePoolEntry(
        entryId = UUID.randomUUID().toString(),
        mediaKey = mediaKey(),
        locatorType = locatorType,
        uri = uri.toString().takeIf { it.isNotBlank() },
        filePath = path,
        mediaType = if (isVideo) "VIDEO" else "IMAGE",
        sizeSnapshot = size,
        modifiedSnapshot = dateModified,
        status = DeletePoolStatus.IN_POOL,
        addedAt = now,
        updatedAt = now
    )
}

fun DeletePoolEntry.locatorUri(): Uri? = uri?.let { Uri.parse(it) }
