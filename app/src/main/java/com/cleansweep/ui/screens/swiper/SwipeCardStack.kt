/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Card-stack swipe for the organize screen.
 *
 * FROZEN UX (2026-06-14) — do not change horizontal browse logic without explicit
 * product approval. Full spec: docs/swiper-card-stack.md
 *
 * Left swipe (next):  current translates left; next card stays centered, scales
 *                     0.92→1.0, alpha 0.35→1.0.
 * Right swipe (prev): mirror / reverse of left — previous slides in from
 *                     -exitDistancePx at alpha 1.0 on top; current stays centered,
 *                     scale 1.0→0.92, alpha 1.0→0.35 (no horizontal movement).
 * Idle: adjacent cards preloaded but alpha = 0.
 *
 * Progress for drag and commit uses exitDistancePx so release does not jump.
 *
 * Diagonal / delete-pool / album-prep drag is specified in docs/swiper-diagonal-drag.md.
 * Do not fold that logic into left/right browse without explicit approval.
 */

package com.cleansweep.ui.screens.swiper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.request.ImageRequest
import com.cleansweep.R
import com.cleansweep.data.model.MediaItem
import com.cleansweep.data.repository.SwipeDownAction
import com.cleansweep.data.repository.SwipeSensitivity
import kotlinx.coroutines.Job

import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

private const val PREVIEW_REVEAL_THRESHOLD = 0.10f
/** Horizontal browse locks only when X movement clearly dominates diagonal drag. */
private const val HORIZONTAL_DOMINANCE_RATIO = 2.2f
private const val DELETE_FLY_TARGET_SCALE = 0f
private const val DRAG_ROTATION_MAX_DEG = 15f
private const val DRAG_ROTATION_REFERENCE_MULTIPLIER = 2.8f
/** Free diagonal drag: scale/alpha falloff vs distance from center — docs/swiper-diagonal-drag.md */
private const val FREE_DRAG_SCALE_MAX_DROP = 0.22f
private const val FREE_DRAG_ALPHA_MAX_DROP = 0.28f
private const val FREE_DRAG_ALPHA_FLOOR = 0.55f
private const val FREE_DRAG_ALPHA_MIN = 0.72f
private const val FREE_DRAG_DISTANCE_DEAD_ZONE_SQ = 100f

/**
 * Right-external pivot for free diagonal drag (simulates right thumb grip).
 * Pivot is OUTSIDE the card on the right (x > 1.0).
 * Rotation angle is the polar angle of the radius vector from this external pivot.
 */
private const val RIGHT_EXTERNAL_PIVOT_FRACTION_X = 1.22f
private const val RIGHT_PIVOT_LEVER_FRACTION = 1.5f
private const val RIGHT_PIVOT_ANGLE_SCALE = 0.25f  // fraction of raw atan2 angle to apply (reduced for less sensitivity)


private fun isDeletePoolDrag(offsetX: Float, offsetY: Float): Boolean =
    offsetX > 0f && offsetY < 0f

private fun deletePoolProgressFor(
    offsetX: Float,
    offsetY: Float,
    swipeThresholdPx: Float
): Float = if (isDeletePoolDrag(offsetX, offsetY)) {
    min(
        offsetX / swipeThresholdPx,
        -offsetY / (swipeThresholdPx * 0.6f)
    ).coerceIn(0f, 1f)
} else {
    0f
}

/** 0 at center; ~1 at [referencePx]; shared by free-drag scale, alpha, and rotation. */
private fun freeDragDistanceProgress(
    offsetX: Float,
    offsetY: Float,
    referencePx: Float
): Float {
    if (referencePx <= 0f) return 0f
    val distSq = offsetX * offsetX + offsetY * offsetY
    if (distSq < FREE_DRAG_DISTANCE_DEAD_ZONE_SQ) return 0f
    return (sqrt(distSq) / referencePx).coerceIn(0f, 1f)
}

private fun freeDragScaleFor(offsetX: Float, offsetY: Float, referencePx: Float): Float {
    val eased = freeDragDistanceProgress(offsetX, offsetY, referencePx)
    val t = eased * eased
    return 1f - FREE_DRAG_SCALE_MAX_DROP * t
}

private fun freeDragAlphaFor(
    offsetX: Float,
    offsetY: Float,
    referencePx: Float,
    swipeThresholdPx: Float
): Float {
    val eased = freeDragDistanceProgress(offsetX, offsetY, referencePx)
    val t = eased * eased
    val distanceAlpha = (1f - FREE_DRAG_ALPHA_MAX_DROP * t).coerceAtLeast(FREE_DRAG_ALPHA_MIN)
    val poolProgress = deletePoolProgressFor(offsetX, offsetY, swipeThresholdPx)
    return if (poolProgress > 0f) {
        min(distanceAlpha, (1f - 0.3f * poolProgress).coerceAtLeast(FREE_DRAG_ALPHA_FLOOR))
    } else {
        distanceAlpha
    }
}

/**
 * Rotation during free diagonal drag using polar angle around a right-external pivot.
 *
 * The pivot is intentionally placed OUTSIDE the card to the right (see RIGHT_EXTERNAL_PIVOT_FRACTION_X).
 * This produces an arc/swing motion as if the right thumb is gripping the right edge
 * and the card body is rotating around that grip point (radius angle).
 *
 * Used only for live free drag. Delete-pool fly animation continues to use trash-centered pivot.
 */
/** 
 * Pivot at trash icon center. 
 * Only used during DeletePoolFly animation (live free-drag rotation uses right-external pivot instead).
 */
private fun trashPivotOrigin(
    layerWidthPx: Float,
    layerHeightPx: Float,
    trashInWindow: Offset?,
    stackCenterInWindow: Offset?
): TransformOrigin {
    if (trashInWindow != null && stackCenterInWindow != null &&
        layerWidthPx > 0f && layerHeightPx > 0f
    ) {
        return TransformOrigin(
            pivotFractionX = 0.5f + (trashInWindow.x - stackCenterInWindow.x) / layerWidthPx,
            pivotFractionY = 0.5f + (trashInWindow.y - stackCenterInWindow.y) / layerHeightPx
        )
    }
    return TransformOrigin(0.88f, -0.40f)
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

/**
 * Mutable gesture/transition fields in one stable holder so drag writes do not
 * recompose the whole card stack — only layer [graphicsLayer] nodes invalidate.
 */
@Stable
private class SwipeGestureState {
    var dragOffsetX by mutableFloatStateOf(0f)
    var dragOffsetY by mutableFloatStateOf(0f)
    var zoomScale by mutableFloatStateOf(1f)
    var zoomPanX by mutableFloatStateOf(0f)
    var zoomPanY by mutableFloatStateOf(0f)
    val transitionProgress = Animatable(0f)
    var horizontalLock by mutableIntStateOf(HORIZONTAL_NONE)
    var transitionMode by mutableStateOf(TransitionMode.Idle)
    var cancelFromPrevious by mutableStateOf(false)
    var toPreviousStartProgress by mutableFloatStateOf(0f)
    var handoffItem by mutableStateOf<MediaItem?>(null)
    var handoffOutgoingItem by mutableStateOf<MediaItem?>(null)
    var handoffToNext by mutableStateOf(true)
    var handoffRevealActive by mutableStateOf(false)
    var deleteFlyStartOffset by mutableStateOf(Offset.Zero)
    var deleteFlyStartScale by mutableFloatStateOf(1f)
    var deleteFlyStartAlpha by mutableFloatStateOf(1f)
    var deleteFlyStartRotation by mutableFloatStateOf(0f)
    var deleteFlyTargetPx by mutableStateOf(Offset.Zero)
    val deleteFlyProgress = Animatable(0f)
    var deleteFlyInProgress by mutableStateOf(false)
    var lastReportedDeletePoolProgress by mutableFloatStateOf(0f)
    /** True when the finger crossed slop on a diagonal path — enables free card drag. */
    var freeDragEnabled by mutableStateOf(false)
    var freeDragStartAngle by mutableFloatStateOf(0f)
    var freeDragAngleValid by mutableStateOf(false)
    var browseTransitionJob: Job? = null

    fun resetAllState(onDeletePoolProgress: (Float) -> Unit) {
        dragOffsetX = 0f
        dragOffsetY = 0f
        zoomScale = 1f
        zoomPanX = 0f
        zoomPanY = 0f
        horizontalLock = HORIZONTAL_NONE
        transitionMode = TransitionMode.Idle
        cancelFromPrevious = false
        toPreviousStartProgress = 0f
        handoffItem = null
        handoffOutgoingItem = null
        handoffToNext = true
        handoffRevealActive = false
        deleteFlyInProgress = false
        freeDragEnabled = false
        freeDragAngleValid = false
        if (lastReportedDeletePoolProgress != 0f) {
            lastReportedDeletePoolProgress = 0f
            onDeletePoolProgress(0f)
        }
    }
}

@Composable
private fun SwipeStackPage(
    mediaItem: MediaItem,
    isCurrent: Boolean,
    isPreview: Boolean,
    modifier: Modifier,
    pageContent: @Composable (
        mediaItem: MediaItem,
        isCurrent: Boolean,
        isPreview: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    key(mediaItem.id) {
        pageContent(mediaItem, isCurrent, isPreview, modifier)
    }
}

/**
 * Interactive card stack. Horizontal browse behavior is documented in `docs/swiper-card-stack.md`.
 * Prefer changing OrganizeUi / SwiperScreen for layout chrome, not this file's swipe physics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeCardStack(
    item: MediaItem,
    previousItem: MediaItem?,
    nextItem: MediaItem?,
    exoPlayer: ExoPlayer,
    imageLoader: ImageLoader,
    gifImageLoader: ImageLoader,
    onSwipeLeft: () -> Boolean,
    onSwipeRight: () -> Boolean,
    onSwipeDown: () -> Unit,
    onSwipeToDeletePool: () -> Unit,
    onTap: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    sensitivity: SwipeSensitivity,
    swipeDownAction: SwipeDownAction,
    videoPlaybackSpeed: Float,
    onSetVideoPlaybackSpeed: (Float) -> Unit,
    isVideoMuted: Boolean,
    onToggleMute: () -> Unit,
    isPendingConversion: Boolean,
    screenshotDeletesVideo: Boolean,
    fullScreenSwipe: Boolean,
    deletePoolFlyTargetInWindow: Offset? = null,
    cardStackCenterInWindow: Offset? = null,
    onDeletePoolProgress: (Float) -> Unit = {},
    undoDirection: Int = 0,
    undoHandoffItem: MediaItem? = null,
    onUndoCommit: () -> Unit = {},
    pageContent: @Composable (
        mediaItem: MediaItem,
        isCurrent: Boolean,
        isPreview: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val gesture = remember { SwipeGestureState() }
    val animationScope = rememberCoroutineScope()
    val latestOnDeletePoolProgress = rememberUpdatedState(onDeletePoolProgress)
    val latestItem = rememberUpdatedState(item)
    val latestNextItem = rememberUpdatedState(nextItem)
    val latestPreviousItem = rememberUpdatedState(previousItem)

    LaunchedEffect(undoDirection) {
        if (undoDirection != 0) {
            val playToPrevious = undoDirection == 1  // HORIZONTAL_PREVIOUS : undo of left-swipe keep -> play ToPrevious animation (right swipe reverse)
            gesture.handoffToNext = !playToPrevious
            gesture.transitionMode = if (playToPrevious) TransitionMode.ToPrevious else TransitionMode.ToNext
            gesture.handoffOutgoingItem = latestItem.value
            gesture.handoffItem = undoHandoffItem ?: if (playToPrevious) latestPreviousItem.value else latestNextItem.value
            gesture.handoffRevealActive = true
            animationScope.launch {
                gesture.transitionProgress.snapTo(0f)
                gesture.transitionProgress.animateTo(
                    1f,
                    tween(300, easing = FastOutSlowInEasing)
                )
                gesture.transitionProgress.snapTo(0f)
                gesture.handoffOutgoingItem = null
                gesture.handoffItem = null
                gesture.handoffRevealActive = false
                gesture.transitionMode = TransitionMode.Idle
                onUndoCommit()
            }
        }
    }
    val latestOnSwipeLeft = rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight = rememberUpdatedState(onSwipeRight)
    val latestOnSwipeDown = rememberUpdatedState(onSwipeDown)
    val latestOnSwipeToDeletePool = rememberUpdatedState(onSwipeToDeletePool)
    val latestOnTap = rememberUpdatedState(onTap)

    val swipeThreshold = when (sensitivity) {
        SwipeSensitivity.LOW -> with(density) { 36.dp.toPx() }
        SwipeSensitivity.MEDIUM -> with(density) { 48.dp.toPx() }
        SwipeSensitivity.HIGH -> with(density) { 80.dp.toPx() }
    }
    val swipeDownThreshold = swipeThreshold * 0.8f
    val transitionDistance = swipeThreshold * 2.2f
    val configuration = LocalConfiguration.current
    val adjacentDecodePx = remember(density, configuration) {
        with(density) {
            (configuration.screenWidthDp.dp.toPx() * 0.98f).toInt().coerceIn(480, 1440)
        }
    }
    LaunchedEffect(item.id) {
        if (gesture.handoffItem != null || gesture.deleteFlyInProgress) return@LaunchedEffect
        gesture.transitionProgress.snapTo(0f)
        gesture.resetAllState(latestOnDeletePoolProgress.value)
    }

    LaunchedEffect(nextItem?.id) {
        nextItem?.let { adjacent ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(adjacent.uri)
                    .size(adjacentDecodePx, adjacentDecodePx)
                    .build()
            )
        }
    }

    LaunchedEffect(previousItem?.id) {
        previousItem?.let { adjacent ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(adjacent.uri)
                    .size(adjacentDecodePx, adjacentDecodePx)
                    .build()
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 0.dp)
    ) {
        val containerWidth = maxWidth
        val maxCardWidth = containerWidth
        val maxCardHeight = maxHeight * 0.99f

        fun cardSizeFor(mediaItem: MediaItem): Pair<Dp, Dp> {
            val aspectRatio = if (mediaItem.width > 0 && mediaItem.height > 0) {
                mediaItem.width.toFloat() / mediaItem.height.toFloat()
            } else {
                1f
            }
            val widthByHeight = maxCardHeight * aspectRatio
            val heightByWidth = maxCardWidth / aspectRatio
            return if (widthByHeight <= maxCardWidth) {
                widthByHeight to maxCardHeight
            } else {
                maxCardWidth to heightByWidth
            }
        }

        val (cardWidth, cardHeight) = cardSizeFor(item)
        val displayedCurrentItem = if (
            gesture.handoffRevealActive &&
            gesture.handoffOutgoingItem != null
        ) {
            gesture.handoffOutgoingItem!!
        } else {
            item
        }
        val displayedNextItem = if (
            gesture.handoffRevealActive &&
            gesture.handoffToNext &&
            gesture.handoffItem != null
        ) {
            gesture.handoffItem
        } else {
            nextItem
        }
        val displayedPreviousItem = if (
            gesture.handoffRevealActive &&
            !gesture.handoffToNext &&
            gesture.handoffItem != null
        ) {
            gesture.handoffItem
        } else {
            previousItem
        }
        val nextCardSize = displayedNextItem?.let { cardSizeFor(it) }
        val previousCardSize = displayedPreviousItem?.let { cardSizeFor(it) }

        fun edgeDistancePx(cardWidthForLayer: Dp): Float {
            return with(density) {
                (containerWidth.toPx() + cardWidthForLayer.toPx()) / 2f + 32.dp.toPx()
            }
        }

        val exitDistancePx = edgeDistancePx(cardWidth)
        val dragRotationReferencePx = swipeThreshold * DRAG_ROTATION_REFERENCE_MULTIPLIER
        val deleteFlyTargetRelativePx = remember(
            deletePoolFlyTargetInWindow,
            cardStackCenterInWindow
        ) {
            val trash = deletePoolFlyTargetInWindow
            val center = cardStackCenterInWindow
            if (trash != null && center != null) {
                Offset(trash.x - center.x, trash.y - center.y)
            } else {
                null
            }
        }
        val deleteFlyTargetXPx = deleteFlyTargetRelativePx?.x ?: with(density) {
            containerWidth.toPx() / 2f - 20.dp.toPx()
        }
        val deleteFlyTargetYPx = deleteFlyTargetRelativePx?.y ?: with(density) {
            -(maxHeight.toPx() / 2f + 132.dp.toPx())
        }
        val scaleRange = 1f - ADJACENT_CARD_MIN_SCALE
        val alphaRange = 1f - ADJACENT_CARD_MIN_ALPHA
        val previousOnTop = isPreviousLayerOnTop(
            gesture.transitionMode,
            gesture.horizontalLock,
            gesture.cancelFromPrevious,
            gesture.handoffToNext
        )

        val latestDeletePoolFlyTarget = rememberUpdatedState(deletePoolFlyTargetInWindow)
        val latestCardStackCenter = rememberUpdatedState(cardStackCenterInWindow)
        val latestDeleteFlyTargetX = rememberUpdatedState(deleteFlyTargetXPx)
        val latestDeleteFlyTargetY = rememberUpdatedState(deleteFlyTargetYPx)

        val gestureModifier = Modifier.pointerInput(
            swipeDownAction,
            swipeThreshold,
            transitionDistance,
            exitDistancePx
        ) {
            val browseCommitDurationMs = 180

            fun launchAnimation(block: suspend () -> Unit) {
                animationScope.launch { block() }
            }

            fun launchBrowseTransition(block: suspend () -> Unit) {
                gesture.browseTransitionJob?.cancel()
                gesture.browseTransitionJob = animationScope.launch {
                    try {
                        block()
                    } finally {
                        gesture.browseTransitionJob = null
                    }
                }
            }

            forEachGesture {
                awaitPointerEventScope {
                        var wasDragging = false
                        var wasZooming = false
                        var dragActive = false
                        var cumulativeDrag = Offset.Zero
                        var localHorizontalLock = gesture.horizontalLock
                        var gestureIntentResolved = false
                        val reportDeletePoolProgress = latestOnDeletePoolProgress.value

                        fun resolveGestureIntent(totalOffset: Offset) {
                            if (gestureIntentResolved) return
                            if (totalOffset.getDistance() <= viewConfiguration.touchSlop) return
                            gestureIntentResolved = true
                            if (abs(totalOffset.x) > abs(totalOffset.y) * HORIZONTAL_DOMINANCE_RATIO) {
                                localHorizontalLock = if (totalOffset.x < 0f) {
                                    HORIZONTAL_NEXT
                                } else {
                                    HORIZONTAL_PREVIOUS
                                }
                                gesture.horizontalLock = localHorizontalLock
                                gesture.freeDragEnabled = false
                                gesture.freeDragAngleValid = false
                            } else {
                                gesture.freeDragEnabled = true
                                localHorizontalLock = HORIZONTAL_NONE
                                gesture.horizontalLock = HORIZONTAL_NONE
                            }
                        }

                        suspend fun animateDragToOrigin() {
                            val startX = gesture.dragOffsetX
                            val startY = gesture.dragOffsetY
                            if (startX == 0f && startY == 0f) return
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)
                            ) { progress, _ ->
                                gesture.dragOffsetX = startX * (1f - progress)
                                gesture.dragOffsetY = startY * (1f - progress)
                            }
                            gesture.dragOffsetX = 0f
                            gesture.dragOffsetY = 0f
                        }

                        fun applyDrag(delta: Offset, totalOffset: Offset? = null) {
                            if (gesture.zoomScale > 1f ||
                                gesture.transitionMode == TransitionMode.DeletePoolFly
                            ) {
                                return
                            }

                            if (gesture.transitionMode != TransitionMode.Dragging) {
                                gesture.transitionMode = TransitionMode.Dragging
                            }

                            if (totalOffset != null) {
                                resolveGestureIntent(totalOffset)
                                if (!gestureIntentResolved) return
                                if (localHorizontalLock != HORIZONTAL_NONE) {
                                    gesture.dragOffsetX = totalOffset.x
                                    gesture.dragOffsetY = 0f
                                } else if (gesture.freeDragEnabled) {
                                    gesture.dragOffsetX = totalOffset.x
                                    gesture.dragOffsetY = totalOffset.y
                                }
                                return
                            }

                            if (!gestureIntentResolved) return

                            if (localHorizontalLock != HORIZONTAL_NONE) {
                                gesture.dragOffsetX += delta.x
                                gesture.dragOffsetY = 0f
                            } else if (gesture.freeDragEnabled) {
                                gesture.dragOffsetX += delta.x
                                gesture.dragOffsetY += delta.y
                            }
                        }

                        awaitFirstDown(requireUnconsumed = true)

                        gesture.browseTransitionJob?.cancel()
                        gesture.browseTransitionJob = null
                        if (gesture.handoffRevealActive) {
                            launchAnimation { gesture.transitionProgress.snapTo(0f) }
                            gesture.handoffOutgoingItem = null
                            gesture.resetAllState(reportDeletePoolProgress)
                            localHorizontalLock = HORIZONTAL_NONE
                        } else when (gesture.transitionMode) {
                            TransitionMode.ToNext -> {
                                val progress = gesture.transitionProgress.value
                                if (progress > 0f) {
                                    gesture.dragOffsetX = -progress * exitDistancePx
                                    gesture.horizontalLock = HORIZONTAL_NEXT
                                    localHorizontalLock = HORIZONTAL_NEXT
                                    gesture.transitionMode = TransitionMode.Dragging
                                } else {
                                    gesture.transitionMode = TransitionMode.Idle
                                }
                                launchAnimation { gesture.transitionProgress.snapTo(0f) }
                            }
                            TransitionMode.ToPrevious -> {
                                val progress = gesture.transitionProgress.value
                                if (progress > 0f) {
                                    gesture.dragOffsetX = progress * exitDistancePx
                                    gesture.toPreviousStartProgress = progress
                                    gesture.horizontalLock = HORIZONTAL_PREVIOUS
                                    localHorizontalLock = HORIZONTAL_PREVIOUS
                                    gesture.transitionMode = TransitionMode.Dragging
                                } else {
                                    gesture.transitionMode = TransitionMode.Idle
                                }
                                launchAnimation { gesture.transitionProgress.snapTo(0f) }
                            }
                            TransitionMode.Cancel -> {
                                gesture.resetAllState(reportDeletePoolProgress)
                                localHorizontalLock = HORIZONTAL_NONE
                                launchAnimation { gesture.transitionProgress.snapTo(0f) }
                            }
                            else -> Unit
                        }

                        do {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }

                            if (event.changes.size > 1) {
                                wasZooming = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val newScale = gesture.zoomScale * zoom
                                if (newScale < 1f) {
                                    gesture.zoomScale = 1f
                                    gesture.zoomPanX = 0f
                                    gesture.zoomPanY = 0f
                                } else {
                                    gesture.zoomScale = newScale.coerceIn(1f, 5f)
                                    if (gesture.zoomScale > 1f) {
                                        val xMax = (cardWidth.toPx() * (gesture.zoomScale - 1f)) / 2f
                                        val yMax = (cardHeight.toPx() * (gesture.zoomScale - 1f)) / 2f
                                        gesture.zoomPanX = (gesture.zoomPanX + pan.x).coerceIn(-xMax, xMax)
                                        gesture.zoomPanY = (gesture.zoomPanY + pan.y).coerceIn(-yMax, yMax)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (!wasZooming) {
                                val change = event.changes.first()
                                val delta = change.positionChange()
                                if (!dragActive) {
                                    cumulativeDrag += delta
                                    if (cumulativeDrag.getDistance() > viewConfiguration.touchSlop) {
                                        dragActive = true
                                        wasDragging = true
                                        applyDrag(Offset.Zero, cumulativeDrag)
                                    }
                                } else {
                                    wasDragging = true
                                    applyDrag(delta)
                                }
                                if (dragActive) {
                                    if (gesture.freeDragEnabled) {
                                        val poolProgress = deletePoolProgressFor(
                                            gesture.dragOffsetX,
                                            gesture.dragOffsetY,
                                            swipeThreshold
                                        )
                                        if (poolProgress != gesture.lastReportedDeletePoolProgress) {
                                            gesture.lastReportedDeletePoolProgress = poolProgress
                                            reportDeletePoolProgress(poolProgress)
                                        }
                                    } else if (gesture.lastReportedDeletePoolProgress != 0f) {
                                        gesture.lastReportedDeletePoolProgress = 0f
                                        reportDeletePoolProgress(0f)
                                    }
                                    change.consume()
                                }
                            }
                        } while (anyPressed)

                        if (wasDragging) {
                            val offsetX = gesture.dragOffsetX
                            val offsetY = gesture.dragOffsetY
                            val isDeletePoolSwipe = offsetX > swipeThreshold &&
                                -offsetY > swipeThreshold * 0.6f

                            val endDrag = {
                                gesture.horizontalLock = HORIZONTAL_NONE
                                localHorizontalLock = HORIZONTAL_NONE
                                gesture.freeDragEnabled = false
                                gesture.freeDragAngleValid = false
                                gesture.transitionMode = TransitionMode.Idle
                                if (gesture.lastReportedDeletePoolProgress != 0f) {
                                    gesture.lastReportedDeletePoolProgress = 0f
                                    reportDeletePoolProgress(0f)
                                }
                            }

                            when {
                                gesture.freeDragEnabled && isDeletePoolSwipe -> {
                                    val poolProgress = deletePoolProgressFor(
                                        offsetX,
                                        offsetY,
                                        swipeThreshold
                                    )
                                    gesture.deleteFlyStartOffset = Offset(offsetX, offsetY)
                                    gesture.deleteFlyStartScale = freeDragScaleFor(
                                        offsetX,
                                        offsetY,
                                        dragRotationReferencePx
                                    )
                                    gesture.deleteFlyStartAlpha = freeDragAlphaFor(
                                        offsetX,
                                        offsetY,
                                        dragRotationReferencePx,
                                        swipeThreshold
                                    )
                                    // Snapshot the effective (delta) rotation at release for smooth fly start
                                    if (gesture.freeDragAngleValid) {
                                        val lever = dragRotationReferencePx * 0.70f
                                        val dx = offsetX + lever
                                        val dy = offsetY
                                        val currentRaw = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        var delta = currentRaw - gesture.freeDragStartAngle
                                        // Robust unwrap to [-180, 180]
                                        delta = delta % 360f
                                        if (delta > 180f) delta -= 360f
                                        if (delta < -180f) delta += 360f
                                        val outward = freeDragDistanceProgress(offsetX, offsetY, dragRotationReferencePx)
                                        // Use same milder easing as live drag for consistent start rotation
                                        val rotationEased = outward * 0.85f + (outward * outward) * 0.15f
                                        val rot = -delta * RIGHT_PIVOT_ANGLE_SCALE * rotationEased
                                        gesture.deleteFlyStartRotation = rot.coerceIn(-DRAG_ROTATION_MAX_DEG , DRAG_ROTATION_MAX_DEG )
                                    } else {
                                        gesture.deleteFlyStartRotation = 0f
                                    }
                                    val trashWindow = latestDeletePoolFlyTarget.value
                                    val stackWindow = latestCardStackCenter.value
                                    gesture.deleteFlyTargetPx = if (
                                        trashWindow != null && stackWindow != null
                                    ) {
                                        Offset(
                                            trashWindow.x - stackWindow.x,
                                            trashWindow.y - stackWindow.y
                                        )
                                    } else {
                                        Offset(
                                            latestDeleteFlyTargetX.value,
                                            latestDeleteFlyTargetY.value
                                        )
                                    }
                                    if (gesture.lastReportedDeletePoolProgress != 0f) {
                                        gesture.lastReportedDeletePoolProgress = 0f
                                        reportDeletePoolProgress(0f)
                                    }
                                    gesture.dragOffsetX = 0f
                                    gesture.dragOffsetY = 0f
                                    gesture.freeDragEnabled = false
                                    gesture.freeDragAngleValid = false
                                    gesture.deleteFlyInProgress = true
                                    gesture.transitionMode = TransitionMode.DeletePoolFly
                                    launchAnimation {
                                        gesture.deleteFlyProgress.snapTo(0f)
                                        gesture.deleteFlyProgress.animateTo(
                                            1f,
                                            tween(300, easing = FastOutSlowInEasing)
                                        )
                                        latestOnSwipeToDeletePool.value()
                                        gesture.deleteFlyProgress.snapTo(0f)
                                        gesture.transitionProgress.snapTo(0f)
                                        gesture.resetAllState(reportDeletePoolProgress)
                                    }
                                }
                                gesture.freeDragEnabled -> {
                                    launchAnimation {
                                        if (offsetX != 0f || offsetY != 0f) {
                                            animateDragToOrigin()
                                        } else {
                                            gesture.dragOffsetX = 0f
                                            gesture.dragOffsetY = 0f
                                        }
                                        gesture.transitionProgress.snapTo(0f)
                                        endDrag()
                                    }
                                }
                                offsetX < -swipeThreshold -> {
                                    val startProgress = (-offsetX / exitDistancePx).coerceIn(0f, 1f)
                                    gesture.dragOffsetX = 0f
                                    gesture.dragOffsetY = 0f
                                    gesture.horizontalLock = HORIZONTAL_NONE
                                    val targetNext = latestNextItem.value
                                    if (targetNext == null) {
                                        gesture.transitionMode = TransitionMode.ToNext
                                        launchBrowseTransition {
                                            gesture.transitionProgress.snapTo(startProgress)
                                            gesture.transitionProgress.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.86f, stiffness = 420f)
                                            )
                                            endDrag()
                                        }
                                    } else {
                                        gesture.handoffOutgoingItem = latestItem.value
                                        gesture.handoffToNext = true
                                        gesture.handoffItem = targetNext
                                        gesture.handoffRevealActive = true
                                        gesture.transitionMode = TransitionMode.ToNext
                                        if (!latestOnSwipeLeft.value()) {
                                            gesture.handoffOutgoingItem = null
                                            gesture.handoffItem = null
                                            gesture.handoffRevealActive = false
                                            launchBrowseTransition {
                                                gesture.transitionProgress.snapTo(startProgress)
                                                gesture.transitionProgress.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.86f, stiffness = 420f)
                                                )
                                                endDrag()
                                            }
                                        } else {
                                            launchBrowseTransition {
                                                gesture.transitionProgress.snapTo(startProgress)
                                                gesture.transitionProgress.animateTo(
                                                    1f,
                                                    tween(
                                                        browseCommitDurationMs,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                                gesture.transitionProgress.snapTo(0f)
                                                gesture.handoffOutgoingItem = null
                                                gesture.resetAllState(reportDeletePoolProgress)
                                            }
                                        }
                                    }
                                }
                                offsetX > swipeThreshold -> {
                                    val startProgress = (offsetX / exitDistancePx).coerceIn(0f, 1f)
                                    gesture.toPreviousStartProgress = startProgress
                                    gesture.dragOffsetX = 0f
                                    gesture.dragOffsetY = 0f
                                    gesture.horizontalLock = HORIZONTAL_NONE
                                    val targetPrevious = latestPreviousItem.value
                                    if (targetPrevious == null) {
                                        gesture.transitionMode = TransitionMode.ToPrevious
                                        launchBrowseTransition {
                                            gesture.transitionProgress.snapTo(startProgress)
                                            gesture.transitionProgress.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.86f, stiffness = 420f)
                                            )
                                            endDrag()
                                        }
                                    } else {
                                        gesture.handoffOutgoingItem = latestItem.value
                                        gesture.handoffToNext = false
                                        gesture.handoffItem = targetPrevious
                                        gesture.handoffRevealActive = true
                                        gesture.transitionMode = TransitionMode.ToPrevious
                                        if (!latestOnSwipeRight.value()) {
                                            gesture.handoffOutgoingItem = null
                                            gesture.handoffItem = null
                                            gesture.handoffRevealActive = false
                                            launchBrowseTransition {
                                                gesture.transitionProgress.snapTo(startProgress)
                                                gesture.transitionProgress.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.86f, stiffness = 420f)
                                                )
                                                endDrag()
                                            }
                                        } else {
                                            launchBrowseTransition {
                                                gesture.transitionProgress.snapTo(startProgress)
                                                gesture.transitionProgress.animateTo(
                                                    1f,
                                                    tween(
                                                        browseCommitDurationMs,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )
                                                gesture.transitionProgress.snapTo(0f)
                                                gesture.handoffOutgoingItem = null
                                                gesture.resetAllState(reportDeletePoolProgress)
                                            }
                                        }
                                    }
                                }
                                offsetY > swipeDownThreshold &&
                                    abs(offsetY) > abs(offsetX) * 1.2f -> {
                                    latestOnSwipeDown.value()
                                    gesture.dragOffsetY = 0f
                                    endDrag()
                                    launchAnimation { gesture.transitionProgress.snapTo(0f) }
                                }
                                else -> {
                                    launchAnimation {
                                        when (localHorizontalLock) {
                                            HORIZONTAL_NEXT -> {
                                                val startProgress = (-offsetX / exitDistancePx)
                                                    .coerceIn(0f, 1f)
                                                if (startProgress > 0f) {
                                                    gesture.cancelFromPrevious = false
                                                    gesture.transitionMode = TransitionMode.Cancel
                                                    gesture.dragOffsetX = 0f
                                                    gesture.dragOffsetY = 0f
                                                    gesture.transitionProgress.snapTo(startProgress)
                                                    gesture.transitionProgress.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.86f, stiffness = 420f)
                                                    )
                                                } else {
                                                    gesture.dragOffsetX = 0f
                                                    gesture.dragOffsetY = 0f
                                                }
                                                endDrag()
                                            }
                                            HORIZONTAL_PREVIOUS -> {
                                                val startProgress = (offsetX / exitDistancePx)
                                                    .coerceIn(0f, 1f)
                                                if (startProgress > 0f) {
                                                    gesture.toPreviousStartProgress = startProgress
                                                    gesture.cancelFromPrevious = true
                                                    gesture.transitionMode = TransitionMode.Cancel
                                                    gesture.dragOffsetX = 0f
                                                    gesture.dragOffsetY = 0f
                                                    gesture.transitionProgress.snapTo(startProgress)
                                                    gesture.transitionProgress.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.86f, stiffness = 420f)
                                                    )
                                                } else {
                                                    gesture.dragOffsetX = 0f
                                                    gesture.dragOffsetY = 0f
                                                }
                                                endDrag()
                                            }
                                            else -> {
                                                if (offsetX != 0f || offsetY != 0f) {
                                                    animateDragToOrigin()
                                                } else {
                                                    gesture.dragOffsetX = 0f
                                                    gesture.dragOffsetY = 0f
                                                }
                                                gesture.transitionProgress.snapTo(0f)
                                                endDrag()
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!wasZooming && !dragActive) {
                            if (gesture.zoomScale > 1f) {
                                gesture.zoomScale = 1f
                                gesture.zoomPanX = 0f
                                gesture.zoomPanY = 0f
                            } else {
                                latestOnTap.value(latestItem.value)
                            }
                        }
                    }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(SwipeCardStackTestTags.STACK)
                .then(if (fullScreenSwipe) gestureModifier else Modifier)
        ) {
            if (displayedNextItem != null && nextCardSize != null) {
                val (nextW, nextH) = nextCardSize
                NextCardLayer(
                    gesture = gesture,
                    mediaItem = displayedNextItem,
                    cardWidth = nextW,
                    cardHeight = nextH,
                    exitDistancePx = exitDistancePx,
                    scaleRange = scaleRange,
                    alphaRange = alphaRange,
                    pageContent = pageContent
                )
            }

            CurrentCardLayer(
                gesture = gesture,
                item = displayedCurrentItem,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                exitDistancePx = exitDistancePx,
                swipeThreshold = swipeThreshold,
                dragRotationReferencePx = dragRotationReferencePx,
                scaleRange = scaleRange,
                alphaRange = alphaRange,
                previousOnTop = previousOnTop,
                gestureModifier = if (!fullScreenSwipe) gestureModifier else Modifier,
                deletePoolFlyTargetInWindow = deletePoolFlyTargetInWindow,
                cardStackCenterInWindow = cardStackCenterInWindow,
                isVideoMuted = isVideoMuted,
                onToggleMute = onToggleMute,
                isPendingConversion = isPendingConversion,
                screenshotDeletesVideo = screenshotDeletesVideo,
                videoPlaybackSpeed = videoPlaybackSpeed,
                onSetVideoPlaybackSpeed = onSetVideoPlaybackSpeed,
                pageContent = pageContent
            )

            if (displayedPreviousItem != null && previousCardSize != null) {
                val (prevW, prevH) = previousCardSize
                PreviousCardLayer(
                    gesture = gesture,
                    mediaItem = displayedPreviousItem,
                    cardWidth = prevW,
                    cardHeight = prevH,
                    exitDistancePx = exitDistancePx,
                    previousOnTop = previousOnTop,
                    pageContent = pageContent
                )
            }
        }
    }
}

@Composable
private fun BoxScope.NextCardLayer(
    gesture: SwipeGestureState,
    mediaItem: MediaItem,
    cardWidth: Dp,
    cardHeight: Dp,
    exitDistancePx: Float,
    scaleRange: Float,
    alphaRange: Float,
    pageContent: @Composable (
        mediaItem: MediaItem,
        isCurrent: Boolean,
        isPreview: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .align(Alignment.Center)
            .testTag(SwipeCardStackTestTags.NEXT)
            .zIndex(1f)
            .graphicsLayer {
                val reveal = leftRevealProgress(
                    transitionMode = gesture.transitionMode,
                    horizontalLock = gesture.horizontalLock,
                    dragOffsetX = gesture.dragOffsetX,
                    exitDistancePx = exitDistancePx,
                    transitionProgress = gesture.transitionProgress.value,
                    cancelFromPrevious = gesture.cancelFromPrevious,
                    handoffToNext = gesture.handoffToNext
                )
                val s = ADJACENT_CARD_MIN_SCALE + scaleRange * reveal
                scaleX = s
                scaleY = s
                alpha = if (reveal > 0f) {
                    (ADJACENT_CARD_MIN_ALPHA + alphaRange * reveal).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
    ) {
        SwipeStackPage(
            mediaItem = mediaItem,
            isCurrent = false,
            isPreview = true,
            modifier = Modifier.fillMaxSize(),
            pageContent = pageContent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.CurrentCardLayer(
    gesture: SwipeGestureState,
    item: MediaItem,
    cardWidth: Dp,
    cardHeight: Dp,
    exitDistancePx: Float,
    swipeThreshold: Float,
    dragRotationReferencePx: Float,
    scaleRange: Float,
    alphaRange: Float,
    previousOnTop: Boolean,
    gestureModifier: Modifier,
    deletePoolFlyTargetInWindow: Offset?,
    cardStackCenterInWindow: Offset?,
    isVideoMuted: Boolean,
    onToggleMute: () -> Unit,
    isPendingConversion: Boolean,
    screenshotDeletesVideo: Boolean,
    videoPlaybackSpeed: Float,
    onSetVideoPlaybackSpeed: (Float) -> Unit,
    pageContent: @Composable (
        mediaItem: MediaItem,
        isCurrent: Boolean,
        isPreview: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .align(Alignment.Center)
            .testTag(SwipeCardStackTestTags.CURRENT)
            .zIndex(if (previousOnTop) 2f else 3f)
            .then(gestureModifier)
            .drawBehind {
                val reveal = leftRevealProgress(
                    transitionMode = gesture.transitionMode,
                    horizontalLock = gesture.horizontalLock,
                    dragOffsetX = gesture.dragOffsetX,
                    exitDistancePx = exitDistancePx,
                    transitionProgress = gesture.transitionProgress.value,
                    cancelFromPrevious = gesture.cancelFromPrevious,
                    handoffToNext = gesture.handoffToNext
                )
                val showHint = gesture.transitionMode != TransitionMode.Handoff &&
                    (reveal >= PREVIEW_REVEAL_THRESHOLD ||
                        gesture.transitionMode == TransitionMode.ToNext ||
                        (gesture.transitionMode == TransitionMode.Cancel &&
                            !gesture.cancelFromPrevious))
                if (!showHint) return@drawBehind
                val intensity = (reveal * (1f - reveal) * 4f * 0.22f).coerceIn(0f, 0.22f)
                if (intensity > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = intensity),
                            0.35f to Color.Black.copy(alpha = intensity * 0.1f),
                            1f to Color.Transparent,
                            startX = 0f,
                            endX = size.width * 0.38f
                        )
                    )
                }
            }
            .graphicsLayer {
                val offsetX = gesture.dragOffsetX
                val offsetY = gesture.dragOffsetY
                val progress = gesture.transitionProgress.value
                val rightReveal = rightRevealProgress(
                    transitionMode = gesture.transitionMode,
                    horizontalLock = gesture.horizontalLock,
                    dragOffsetX = offsetX,
                    exitDistancePx = exitDistancePx,
                    transitionProgress = progress,
                    cancelFromPrevious = gesture.cancelFromPrevious,
                    handoffToNext = gesture.handoffToNext
                )
                val flyT = gesture.deleteFlyProgress.value
                val isFreeDragging = gesture.transitionMode == TransitionMode.Dragging &&
                    gesture.freeDragEnabled
                val isDeletePoolFlying = gesture.transitionMode == TransitionMode.DeletePoolFly

                if (gesture.zoomScale > 1f) {
                    translationX = gesture.zoomPanX
                    translationY = gesture.zoomPanY
                    rotationZ = 0f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    return@graphicsLayer
                }

                translationX = when {
                    isDeletePoolFlying ->
                        lerp(gesture.deleteFlyStartOffset.x, gesture.deleteFlyTargetPx.x, flyT)
                    gesture.transitionMode == TransitionMode.ToNext ||
                        (gesture.transitionMode == TransitionMode.Handoff && gesture.handoffToNext) ->
                        -exitDistancePx * progress
                    gesture.transitionMode == TransitionMode.Cancel && !gesture.cancelFromPrevious ->
                        -exitDistancePx * progress
                    rightReveal > 0f -> 0f
                    gesture.transitionMode == TransitionMode.Dragging &&
                        gesture.horizontalLock == HORIZONTAL_NEXT -> offsetX
                    isFreeDragging -> offsetX
                    else -> 0f
                }
                translationY = when {
                    isDeletePoolFlying ->
                        lerp(gesture.deleteFlyStartOffset.y, gesture.deleteFlyTargetPx.y, flyT)
                    isFreeDragging -> offsetY
                    else -> 0f
                }
                rotationZ = when {
                    isDeletePoolFlying ->
                        lerp(gesture.deleteFlyStartRotation, 0f, flyT)
                    isFreeDragging -> {
                        // Capture initial angle on first frame of this free drag
                        if (!gesture.freeDragAngleValid) {
                            val lever = size.width * RIGHT_PIVOT_LEVER_FRACTION
                            val dx0 = offsetX + lever
                            val dy0 = offsetY
                            gesture.freeDragStartAngle = Math.toDegrees(atan2(dy0.toDouble(), dx0.toDouble())).toFloat()
                            gesture.freeDragAngleValid = true
                        }

                        val lever = size.width * RIGHT_PIVOT_LEVER_FRACTION
                        val dx = offsetX + lever
                        val dy = offsetY
                        val currentRaw = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                        var delta = currentRaw - gesture.freeDragStartAngle
                        // Robust unwrap to [-180, 180] to prevent sudden jumps when crossing the branch cut
                        delta = delta % 360f
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f

                        val outward = freeDragDistanceProgress(offsetX, offsetY, dragRotationReferencePx)
                        // Use milder easing for rotation to feel silkier (less "snappy" than scale/alpha)
                        val rotationEased = outward * 0.85f + (outward * outward) * 0.15f
                        val scaled = -delta * RIGHT_PIVOT_ANGLE_SCALE * rotationEased
                        scaled.coerceIn(-DRAG_ROTATION_MAX_DEG , DRAG_ROTATION_MAX_DEG )
                    }
                    else -> 0f
                }
                transformOrigin = when {
                    isDeletePoolFlying -> trashPivotOrigin(
                        layerWidthPx = size.width,
                        layerHeightPx = size.height,
                        trashInWindow = deletePoolFlyTargetInWindow,
                        stackCenterInWindow = cardStackCenterInWindow
                    )
                    isFreeDragging -> TransformOrigin(RIGHT_EXTERNAL_PIVOT_FRACTION_X, 0.5f)
                    else -> TransformOrigin(0.5f, 0.5f)
                }
                clip = false
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offsetX = gesture.dragOffsetX
                    val offsetY = gesture.dragOffsetY
                    val progress = gesture.transitionProgress.value
                    val rightReveal = rightRevealProgress(
                        transitionMode = gesture.transitionMode,
                        horizontalLock = gesture.horizontalLock,
                        dragOffsetX = offsetX,
                        exitDistancePx = exitDistancePx,
                        transitionProgress = progress,
                        cancelFromPrevious = gesture.cancelFromPrevious,
                        handoffToNext = gesture.handoffToNext
                    )
                    val flyT = gesture.deleteFlyProgress.value
                    val isFreeDragging = gesture.transitionMode == TransitionMode.Dragging &&
                        gesture.freeDragEnabled
                    val isDeletePoolFlying = gesture.transitionMode == TransitionMode.DeletePoolFly
                    val flyShrinkT = deleteFlyShrinkProgress(flyT)

                    transformOrigin = TransformOrigin(0.5f, 0.5f)

                    if (gesture.zoomScale > 1f) {
                        scaleX = gesture.zoomScale
                        scaleY = gesture.zoomScale
                        alpha = 1f
                        return@graphicsLayer
                    }

                    val transitionScale = when {
                        isDeletePoolFlying ->
                            lerp(gesture.deleteFlyStartScale, DELETE_FLY_TARGET_SCALE, flyShrinkT)
                        isFreeDragging ->
                            freeDragScaleFor(offsetX, offsetY, dragRotationReferencePx)
                        rightReveal > 0f ->
                            ADJACENT_CARD_MIN_SCALE + scaleRange * (1f - rightReveal)
                        else -> 1f
                    }
                    scaleX = transitionScale
                    scaleY = transitionScale
                    alpha = when {
                        isDeletePoolFlying ->
                            lerp(gesture.deleteFlyStartAlpha, 0f, flyShrinkT)
                        isFreeDragging ->
                            freeDragAlphaFor(
                                offsetX,
                                offsetY,
                                dragRotationReferencePx,
                                swipeThreshold
                            )
                        (gesture.transitionMode == TransitionMode.ToNext ||
                            (gesture.transitionMode == TransitionMode.Handoff && gesture.handoffToNext)) &&
                            progress >= 1f -> 0f
                        (gesture.transitionMode == TransitionMode.ToPrevious ||
                            (gesture.transitionMode == TransitionMode.Handoff && !gesture.handoffToNext)) &&
                            progress >= 1f -> 0f
                        rightReveal > 0f ->
                            (1f - alphaRange * rightReveal).coerceIn(ADJACENT_CARD_MIN_ALPHA, 1f)
                        else -> 1f
                    }
                }
        ) {
            SwipeStackPage(
                mediaItem = item,
                isCurrent = true,
                isPreview = false,
                modifier = Modifier.fillMaxSize(),
                pageContent = pageContent
            )

            Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = stringResource(R.string.next_image),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .graphicsLayer {
                    alpha = leftRevealProgress(
                        transitionMode = gesture.transitionMode,
                        horizontalLock = gesture.horizontalLock,
                        dragOffsetX = gesture.dragOffsetX,
                        exitDistancePx = exitDistancePx,
                        transitionProgress = gesture.transitionProgress.value,
                        cancelFromPrevious = gesture.cancelFromPrevious,
                        handoffToNext = gesture.handoffToNext
                    )
                }
        )

            if (item.isVideo) {
                VideoOverlayControls(
                    isVideoMuted = isVideoMuted,
                    onToggleMute = onToggleMute,
                    isPendingConversion = isPendingConversion,
                    screenshotDeletesVideo = screenshotDeletesVideo,
                    videoPlaybackSpeed = videoPlaybackSpeed,
                    onSetVideoPlaybackSpeed = onSetVideoPlaybackSpeed
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviousCardLayer(
    gesture: SwipeGestureState,
    mediaItem: MediaItem,
    cardWidth: Dp,
    cardHeight: Dp,
    exitDistancePx: Float,
    previousOnTop: Boolean,
    pageContent: @Composable (
        mediaItem: MediaItem,
        isCurrent: Boolean,
        isPreview: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    val previousExitLeftPx = -exitDistancePx
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .align(Alignment.Center)
            .testTag(SwipeCardStackTestTags.PREVIOUS)
            .zIndex(if (previousOnTop) 3f else 0.5f)
            .graphicsLayer {
                val reveal = rightRevealProgress(
                    transitionMode = gesture.transitionMode,
                    horizontalLock = gesture.horizontalLock,
                    dragOffsetX = gesture.dragOffsetX,
                    exitDistancePx = exitDistancePx,
                    transitionProgress = gesture.transitionProgress.value,
                    cancelFromPrevious = gesture.cancelFromPrevious,
                    handoffToNext = gesture.handoffToNext
                )
                translationX = previousExitLeftPx * (1f - reveal)
                scaleX = 1f
                scaleY = 1f
                alpha = if (reveal > 0f) 1f else 0f
            }
    ) {
        SwipeStackPage(
            mediaItem = mediaItem,
            isCurrent = false,
            isPreview = true,
            modifier = Modifier.fillMaxSize(),
            pageContent = pageContent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoOverlayControls(
    isVideoMuted: Boolean,
    onToggleMute: () -> Unit,
    isPendingConversion: Boolean,
    screenshotDeletesVideo: Boolean,
    videoPlaybackSpeed: Float,
    onSetVideoPlaybackSpeed: (Float) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        val muteDesc = if (isVideoMuted) {
            stringResource(R.string.unmute_video)
        } else {
            stringResource(R.string.mute_video)
        }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
                spacingBetweenTooltipAndAnchor = 4.dp
            ),
            tooltip = { PlainTooltip { Text(muteDesc) } },
            state = rememberTooltipState()
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (isVideoMuted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = muteDesc,
                    tint = Color.White
                )
            }
        }
        if (isPendingConversion && !screenshotDeletesVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = stringResource(R.string.pending_conversion_desc),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        TextButton(
            onClick = {
                val nextSpeed = when (videoPlaybackSpeed) {
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    else -> 1.0f
                }
                onSetVideoPlaybackSpeed(nextSpeed)
            },
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp)
        ) {
            Text(
                "${videoPlaybackSpeed}x",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}