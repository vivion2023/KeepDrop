/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Compose UI regression skeleton for SwipeCardStack — docs/swiper-card-stack.md
 */

package com.cleansweep.ui.screens.swiper

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.cleansweep.data.model.MediaItem
import com.cleansweep.data.repository.SwipeDownAction
import com.cleansweep.data.repository.SwipeSensitivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SwipeCardStackComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var imageLoader: ImageLoader
    private lateinit var exoPlayer: ExoPlayer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imageLoader = ImageLoader(context)
        exoPlayer = ExoPlayer.Builder(context).build()
    }

    @After
    fun tearDown() {
        exoPlayer.release()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun idle_rendersCurrentCard_andPreloadsAdjacentLayers() {
        setTestContent()

        composeTestRule.onNodeWithTag(SwipeCardStackTestTags.STACK).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SwipeCardStackTestTags.CURRENT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SwipeCardStackTestTags.NEXT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SwipeCardStackTestTags.PREVIOUS).assertIsDisplayed()
        composeTestRule.onNodeWithText(SwipeCardStackTestFixtures.current.id).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun leftSwipe_commit_invokesOnSwipeLeft() {
        var swipeLeftCount = 0
        setTestContent(
            onSwipeLeft = {
                swipeLeftCount++
                true
            }
        )

        swipeHorizontal(deltaX = -500f)
        advanceAnimations()

        assertEquals(1, swipeLeftCount)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun rightSwipe_commit_invokesOnSwipeRight() {
        var swipeRightCount = 0
        setTestContent(
            onSwipeRight = {
                swipeRightCount++
                true
            }
        )

        swipeHorizontal(deltaX = 500f)
        advanceAnimations()

        assertEquals(1, swipeRightCount)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun rapidLeftSwipes_invokeOnSwipeLeftEachTime() {
        var swipeLeftCount = 0
        setTestContent(
            onSwipeLeft = {
                swipeLeftCount++
                true
            }
        )

        repeat(3) {
            swipeHorizontal(deltaX = -500f)
            composeTestRule.waitForIdle()
        }
        advanceAnimations()

        assertEquals(3, swipeLeftCount)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun shortLeftSwipe_cancel_doesNotInvokeOnSwipeLeft() {
        var swipeLeftCount = 0
        setTestContent(
            onSwipeLeft = {
                swipeLeftCount++
                true
            }
        )

        swipeHorizontal(deltaX = -20f)
        advanceAnimations()

        assertEquals(0, swipeLeftCount)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun leftSwipe_withoutNextItem_doesNotInvokeCallback() {
        var swipeLeftCount = 0
        setTestContent(
            nextItem = null,
            onSwipeLeft = {
                swipeLeftCount++
                true
            }
        )

        swipeHorizontal(deltaX = -500f)
        advanceAnimations()

        assertEquals(0, swipeLeftCount)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun onSwipeLeftReturningFalse_keepsShowingCurrentCard() {
        setTestContent(
            onSwipeLeft = { false }
        )

        swipeHorizontal(deltaX = -500f)
        advanceAnimations()

        composeTestRule.onNodeWithText(SwipeCardStackTestFixtures.current.id).assertIsDisplayed()
    }

    private fun swipeHorizontal(deltaX: Float) {
        composeTestRule.onNodeWithTag(SwipeCardStackTestTags.STACK).performTouchInput {
            val start = Offset(width / 2f, height / 2f)
            down(start)
            moveBy(Offset(deltaX, 0f))
            up()
        }
    }

    private fun advanceAnimations() {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun setTestContent(
        item: MediaItem = SwipeCardStackTestFixtures.current,
        previousItem: MediaItem? = SwipeCardStackTestFixtures.previous,
        nextItem: MediaItem? = SwipeCardStackTestFixtures.next,
        onSwipeLeft: () -> Boolean = { true },
        onSwipeRight: () -> Boolean = { true }
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                SwipeCardStack(
                    item = item,
                    previousItem = previousItem,
                    nextItem = nextItem,
                    exoPlayer = exoPlayer,
                    imageLoader = imageLoader,
                    gifImageLoader = imageLoader,
                    onSwipeLeft = onSwipeLeft,
                    onSwipeRight = onSwipeRight,
                    onSwipeDown = {},
                    onSwipeToDeletePool = {},
                    onTap = {},
                    sensitivity = SwipeSensitivity.LOW,
                    swipeDownAction = SwipeDownAction.NONE,
                    videoPlaybackSpeed = 1f,
                    onSetVideoPlaybackSpeed = {},
                    isVideoMuted = true,
                    onToggleMute = {},
                    isPendingConversion = false,
                    screenshotDeletesVideo = false,
                    fullScreenSwipe = true,
                    pageContent = { mediaItem, _, _, modifier ->
                        Box(
                            modifier = modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(mediaItem.id)
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}