/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 */

package com.cleansweep.ui.screens.swiper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwiperBrowseNavigationTest {

    @Test
    fun rightSwipe_fromSeventhItem_returnsSixthIndex() {
        assertEquals(5, adjacentBrowseIndex(currentIndex = 6, direction = -1, listSize = 15214))
    }

    @Test
    fun leftSwipe_fromSeventhItem_returnsEighthIndex() {
        assertEquals(7, adjacentBrowseIndex(currentIndex = 6, direction = 1, listSize = 15214))
    }

    @Test
    fun rightSwipe_atFirstItem_returnsNull() {
        assertNull(adjacentBrowseIndex(currentIndex = 0, direction = -1, listSize = 100))
    }

    @Test
    fun leftSwipe_atLastItem_returnsNull() {
        assertNull(adjacentBrowseIndex(currentIndex = 99, direction = 1, listSize = 100))
    }

    @Test
    fun rightSwipe_skipsHiddenDeletedIndex() {
        assertEquals(3, adjacentBrowsableIndexFiltered(currentIndex = 5, direction = -1, listSize = 10, hiddenIndices = setOf(4)))
    }

    @Test
    fun leftSwipe_skipsHiddenDeletedIndex() {
        assertEquals(7, adjacentBrowsableIndexFiltered(currentIndex = 5, direction = 1, listSize = 10, hiddenIndices = setOf(6)))
    }

    @Test
    fun rightSwipe_allPreviousHidden_returnsNull() {
        assertNull(
            adjacentBrowsableIndexFiltered(
                currentIndex = 3,
                direction = -1,
                listSize = 10,
                hiddenIndices = setOf(0, 1, 2)
            )
        )
    }

    @Test
    fun effectivePending_afterBrowseBack_keepsDeletesButNotAheadKeeps() {
        val entries = listOf(
            Triple("keep-0", 0, false),
            Triple("delete-1", 1, true),
            Triple("delete-2", 2, true),
            Triple("keep-3", 3, false),
        )
        assertEquals(
            setOf("keep-0", "delete-1", "delete-2"),
            effectivePendingItemIdsAtPosition(entries, currentIndex = 0)
        )
    }

    @Test
    fun effectivePending_atCurrentIndex_includesAllDecisionsUpToPosition() {
        val entries = listOf(
            Triple("keep-0", 0, false),
            Triple("delete-1", 1, true),
            Triple("keep-2", 2, false),
        )
        assertEquals(
            setOf("keep-0", "delete-1", "keep-2"),
            effectivePendingItemIdsAtPosition(entries, currentIndex = 2)
        )
    }
}