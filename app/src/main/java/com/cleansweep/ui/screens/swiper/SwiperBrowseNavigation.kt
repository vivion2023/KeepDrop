/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Pure index math for horizontal swipe browsing in the organize queue.
 */

package com.cleansweep.ui.screens.swiper

/** Target index for left (+1) or right (-1) browse; null if out of range. */
internal fun adjacentBrowseIndex(
    currentIndex: Int,
    direction: Int,
    listSize: Int
): Int? {
    if (direction == 0 || listSize <= 0) return null
    val target = currentIndex + direction
    return target.takeIf { it in 0 until listSize }
}

/**
 * Nearest browsable index in [direction], skipping [hiddenIndices] (e.g. pending deletes).
 * Used for horizontal browse so deleted items never become current via swipe.
 */
internal fun adjacentBrowsableIndexFiltered(
    currentIndex: Int,
    direction: Int,
    listSize: Int,
    hiddenIndices: Set<Int> = emptySet()
): Int? {
    if (direction == 0 || listSize <= 0) return null
    val range = if (direction > 0) {
        (currentIndex + 1) until listSize
    } else {
        (currentIndex - 1) downTo 0
    }
    return range.firstOrNull { it !in hiddenIndices }
}