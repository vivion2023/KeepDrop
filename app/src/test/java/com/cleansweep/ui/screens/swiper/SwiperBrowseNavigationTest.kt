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
}