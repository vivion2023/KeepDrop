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

package com.cleansweep.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.util.Size
import com.cleansweep.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailPrewarmer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) {
    private val contentResolver = context.contentResolver
    private val logTag ="ThumbnailPrewarmer"

    companion object {
        private const val BATCH_SIZE = 50
        private const val BATCH_DELAY_MS = 200L
    }

    /**
     * A robust, multi-step process to ensure newly discovered media files are fully indexed
     * in the MediaStore, including their crucial thumbnails.
     *
     * This process is designed to overcome the lazy, asynchronous nature of MediaStore scanning.
     *
     * @param filePaths A list of absolute paths for the media files to be indexed and pre-warmed.
     */
    suspend fun prewarm(filePaths: List<String>) {
        if (filePaths.isEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            Log.d(logTag, "Starting pre-warm process for ${filePaths.size} files in batches of $BATCH_SIZE.")

            var prewarmedCount = 0
            filePaths.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                if (batchIndex > 0) {
                    delay(BATCH_DELAY_MS)
                }

                val scanSuccess = mediaRepository.scanPathsAndWait(batch)
                if (!scanSuccess) {
                    Log.e(logTag, "Pre-warm failed for batch $batchIndex: scanPathsAndWait returned false.")
                    return@forEachIndexed
                }

                val mediaItems = mediaRepository.getMediaItemsFromPaths(batch)
                if (mediaItems.isEmpty()) {
                    Log.w(logTag, "Pre-warm warning: No media items found after scan for batch $batchIndex.")
                    return@forEachIndexed
                }

                mediaItems.forEach { item ->
                    if (item.uri.scheme == "content") {
                        try {
                            contentResolver.loadThumbnail(item.uri, Size(256, 256), null)
                            prewarmedCount++
                        } catch (e: Exception) {
                            Log.w(logTag, "Failed to pre-warm thumbnail for ${item.id}", e)
                        }
                    }
                }
            }
            Log.d(logTag, "Pre-warm process complete. Successfully pre-warmed $prewarmedCount of ${filePaths.size} items.")
        }
    }
}