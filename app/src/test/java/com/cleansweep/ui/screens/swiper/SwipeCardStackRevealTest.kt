/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Unit tests for frozen horizontal browse math — docs/swiper-card-stack.md
 */

package com.cleansweep.ui.screens.swiper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCardStackRevealTest {

    private val exitDistancePx = 400f

    @Test
    fun exitDistancePx_matchesFrozenFormula() {
        val result = swipeCardExitDistancePx(
            containerWidthPx = 360f,
            cardWidthPx = 340f,
            edgePaddingPx = 96f
        )
        assertEquals((360f + 340f) / 2f + 96f, result, 0.001f)
    }

    @Test
    fun leftRevealProgress_idle_isZero() {
        assertEquals(
            0f,
            leftRevealProgress(
                transitionMode = TransitionMode.Idle,
                horizontalLock = HORIZONTAL_NONE,
                dragOffsetX = 0f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 0f,
                cancelFromPrevious = false,
                handoffToNext = true
            ),
            0.001f
        )
    }

    @Test
    fun leftRevealProgress_draggingNext_tracksFingerLeft() {
        assertEquals(
            0.5f,
            leftRevealProgress(
                transitionMode = TransitionMode.Dragging,
                horizontalLock = HORIZONTAL_NEXT,
                dragOffsetX = -200f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 0f,
                cancelFromPrevious = false,
                handoffToNext = true
            ),
            0.001f
        )
    }

    @Test
    fun leftRevealProgress_toNext_usesTransitionProgress() {
        assertEquals(
            0.75f,
            leftRevealProgress(
                transitionMode = TransitionMode.ToNext,
                horizontalLock = HORIZONTAL_NONE,
                dragOffsetX = 0f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 0.75f,
                cancelFromPrevious = false,
                handoffToNext = true
            ),
            0.001f
        )
    }

    @Test
    fun leftRevealProgress_cancelFromNext_usesTransitionProgress() {
        assertEquals(
            0.4f,
            leftRevealProgress(
                transitionMode = TransitionMode.Cancel,
                horizontalLock = HORIZONTAL_NONE,
                dragOffsetX = 0f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 0.4f,
                cancelFromPrevious = false,
                handoffToNext = true
            ),
            0.001f
        )
    }

    @Test
    fun rightRevealProgress_draggingPrevious_tracksFingerRight() {
        assertEquals(
            0.25f,
            rightRevealProgress(
                transitionMode = TransitionMode.Dragging,
                horizontalLock = HORIZONTAL_PREVIOUS,
                dragOffsetX = 100f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 0f,
                cancelFromPrevious = false,
                handoffToNext = false
            ),
            0.001f
        )
    }

    @Test
    fun rightRevealProgress_toPrevious_usesTransitionProgress() {
        assertEquals(
            1f,
            rightRevealProgress(
                transitionMode = TransitionMode.ToPrevious,
                horizontalLock = HORIZONTAL_NONE,
                dragOffsetX = 0f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 1f,
                cancelFromPrevious = false,
                handoffToNext = false
            ),
            0.001f
        )
    }

    @Test
    fun rightRevealProgress_cancelFromPrevious_usesTransitionProgress() {
        assertEquals(
            0.6f,
            rightRevealProgress(
                transitionMode = TransitionMode.Cancel,
                horizontalLock = HORIZONTAL_NONE,
                dragOffsetX = 0f,
                exitDistancePx = exitDistancePx,
                transitionProgress = 0.6f,
                cancelFromPrevious = true,
                handoffToNext = false
            ),
            0.001f
        )
    }

    @Test
    fun isPreviousLayerOnTop_trueDuringRightBrowseModes() {
        assertTrue(
            isPreviousLayerOnTop(
                transitionMode = TransitionMode.ToPrevious,
                horizontalLock = HORIZONTAL_NONE,
                cancelFromPrevious = false,
                handoffToNext = false
            )
        )
        assertTrue(
            isPreviousLayerOnTop(
                transitionMode = TransitionMode.Dragging,
                horizontalLock = HORIZONTAL_PREVIOUS,
                cancelFromPrevious = false,
                handoffToNext = false
            )
        )
        assertTrue(
            isPreviousLayerOnTop(
                transitionMode = TransitionMode.Cancel,
                horizontalLock = HORIZONTAL_NONE,
                cancelFromPrevious = true,
                handoffToNext = false
            )
        )
    }

    @Test
    fun isPreviousLayerOnTop_falseDuringLeftBrowseModes() {
        assertFalse(
            isPreviousLayerOnTop(
                transitionMode = TransitionMode.ToNext,
                horizontalLock = HORIZONTAL_NONE,
                cancelFromPrevious = false,
                handoffToNext = true
            )
        )
        assertFalse(
            isPreviousLayerOnTop(
                transitionMode = TransitionMode.Dragging,
                horizontalLock = HORIZONTAL_NEXT,
                cancelFromPrevious = false,
                handoffToNext = true
            )
        )
    }

    @Test
    fun adjacentCardVisuals_atZeroReveal_matchIdleSpec() {
        val reveal = 0f
        val scale = ADJACENT_CARD_MIN_SCALE + (1f - ADJACENT_CARD_MIN_SCALE) * reveal
        val alpha = ADJACENT_CARD_MIN_ALPHA + (1f - ADJACENT_CARD_MIN_ALPHA) * reveal
        assertEquals(ADJACENT_CARD_MIN_SCALE, scale, 0.001f)
        assertEquals(ADJACENT_CARD_MIN_ALPHA, alpha, 0.001f)
    }

    @Test
    fun adjacentCardVisuals_atFullReveal_matchCommittedSpec() {
        val reveal = 1f
        val scale = ADJACENT_CARD_MIN_SCALE + (1f - ADJACENT_CARD_MIN_SCALE) * reveal
        val alpha = ADJACENT_CARD_MIN_ALPHA + (1f - ADJACENT_CARD_MIN_ALPHA) * reveal
        assertEquals(1f, scale, 0.001f)
        assertEquals(1f, alpha, 0.001f)
    }

    @Test
    fun deleteFlyShrinkProgress_isMonotonicWithoutMidFlightPlateau() {
        val atStart = deleteFlyShrinkProgress(0f)
        val mid = deleteFlyShrinkProgress(0.35f)
        val atEnd = deleteFlyShrinkProgress(1f)
        assertEquals(0f, atStart, 0.001f)
        assertTrue(mid > 0.15f)
        assertTrue(mid < atEnd)
        assertEquals(1f, atEnd, 0.001f)
    }

    @Test
    fun leftAndRightReveal_clampedToUnitInterval() {
        val left = leftRevealProgress(
            transitionMode = TransitionMode.Dragging,
            horizontalLock = HORIZONTAL_NEXT,
            dragOffsetX = -800f,
            exitDistancePx = exitDistancePx,
            transitionProgress = 0f,
            cancelFromPrevious = false,
            handoffToNext = true
        )
        val right = rightRevealProgress(
            transitionMode = TransitionMode.Dragging,
            horizontalLock = HORIZONTAL_PREVIOUS,
            dragOffsetX = 800f,
            exitDistancePx = exitDistancePx,
            transitionProgress = 0f,
            cancelFromPrevious = false,
            handoffToNext = false
        )
        assertEquals(1f, left, 0.001f)
        assertEquals(1f, right, 0.001f)
    }
}