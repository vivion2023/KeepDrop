/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 */

package com.cleansweep.ui.screens.swiper

import android.net.Uri
import com.cleansweep.data.model.MediaItem

internal object SwipeCardStackTestFixtures {
    fun mediaItem(
        id: String,
        displayName: String = "photo_$id.jpg"
    ): MediaItem = MediaItem(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        displayName = displayName,
        mimeType = "image/jpeg",
        dateAdded = 0L,
        dateModified = 0L,
        size = 1024L,
        bucketId = "bucket",
        bucketName = "Camera",
        isVideo = false,
        width = 1080,
        height = 1920
    )

    val previous: MediaItem = mediaItem("1")
    val current: MediaItem = mediaItem("2")
    val next: MediaItem = mediaItem("3")
}