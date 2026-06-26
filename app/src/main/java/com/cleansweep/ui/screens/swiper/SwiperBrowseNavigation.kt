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

/**
 * Pending item IDs that count as processed when advancing from [currentIndex].
 * Deletes are always excluded (even after browse-back); other decisions only apply
 * at or before the current view position so kept items ahead can be revisited.
 */
internal fun effectivePendingItemIdsAtPosition(
    entries: List<Triple<String, Int, Boolean>>,
    currentIndex: Int
): Set<String> {
    return entries.mapNotNull { (itemId, itemIndex, isPermanentHide) ->
        if (isPermanentHide || itemIndex <= currentIndex) itemId else null
    }.toSet()
}

/** Remaining items in the swiper queue after pending deletes are excluded. */
internal fun visibleQueueTotal(listSize: Int, hiddenIndices: Set<Int>): Int =
    (listSize - hiddenIndices.size).coerceAtLeast(1)

/** 1-based position of [currentIndex] among non-deleted items in the queue. */
internal fun visibleQueuePosition(currentIndex: Int, hiddenIndices: Set<Int>): Int {
    var position = 0
    for (i in 0..currentIndex) {
        if (i !in hiddenIndices) position++
    }
    return position.coerceAtLeast(1)
}