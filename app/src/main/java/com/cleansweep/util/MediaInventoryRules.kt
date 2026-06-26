/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Rules for aligning in-app media inventory with MediaStore / system gallery counts.
 */

package com.cleansweep.util

import com.cleansweep.data.model.MediaItem
import java.io.File
import java.util.Locale

/**
 * Names that must never enter organize / swiper / folder counts.
 * Includes hidden dotfiles and cross-platform trash marker files (e.g. `.trashed-*`).
 */
fun isExcludedMediaFileName(fileName: String?): Boolean {
    if (fileName.isNullOrBlank()) return true
    val base = fileName.substringAfterLast('/').lowercase(Locale.ROOT)
    if (base.startsWith('.')) return true
    if (base.startsWith("trashed-")) return true
    if (base.contains(".trashed-")) return true
    return false
}

fun MediaItem.isExcludedFromInventory(): Boolean =
    isExcludedMediaFileName(displayName) || isExcludedMediaFileName(File(id).name)

/** Stable path key for deduplication across MediaStore DATA paths and file listings. */
fun canonicalMediaPath(file: File): String = try {
    file.canonicalPath
} catch (_: Exception) {
    file.absolutePath
}

fun parentFolderPath(file: File): String? = try {
    file.parentFile?.canonicalPath
} catch (_: Exception) {
    file.parent
}

fun isInAlbumFolders(file: File, albumFolderPaths: Set<String>): Boolean {
    val parent = parentFolderPath(file) ?: return false
    return parent in albumFolderPaths
}

/**
 * Files that should appear in organize/swiper queues — matches what system gallery can show.
 * Skips hidden, empty, and image files that cannot be decoded (often render as a white card).
 */
fun isDisplayableMediaFile(
    file: File,
    isVideo: Boolean,
    cachedWidth: Int = 0,
    cachedHeight: Int = 0,
): Boolean {
    if (!file.isFile || !file.canRead() || file.length() <= 0L) return false
    if (isExcludedMediaFileName(file.name)) return false

    if (isVideo) {
        return cachedWidth > 0 && cachedHeight > 0 || file.length() > 512L
    }

    if (cachedWidth > 0 && cachedHeight > 0) return true

    val (width, height) = ImageDimensions.readFromFile(file)
    return width > 0 && height > 0
}