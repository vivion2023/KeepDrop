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
 */

package com.cleansweep.ui.screens.swiper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

private const val PREVIEW_REVEAL_THRESHOLD = 0.15f
/** Frozen card-stack constants — see docs/swiper-card-stack.md */
private const val ADJACENT_CARD_MIN_SCALE = 0.92f
private const val ADJACENT_CARD_MIN_ALPHA = 0.35f

/** -1 = swiping to next (finger moves left), 0 = free, 1 = swiping to previous */
private const val HORIZONTAL_NONE = 0
private const val HORIZONTAL_NEXT = -1
private const val HORIZONTAL_PREVIOUS = 1

/**
 * Visual transition driven by [transitionProgress] after the finger lifts.
 * [dragOffsetX]/[dragOffsetY] drive live finger tracking while [transitionMode] is [TransitionMode.Dragging].
 */
private enum class TransitionMode {
    Idle,
    Dragging,
    ToNext,
    ToPrevious,
    Cancel
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
    onDeletePoolProgress: (Float) -> Unit = {},
    pageContent: @Composable (
        mediaItem: MediaItem,
        isCurrent: Boolean,
        isPreview: Boolean,
        modifier: Modifier
    ) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val swipeHintColor = MaterialTheme.colorScheme.primary

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomPanX by remember { mutableFloatStateOf(0f) }
    var zoomPanY by remember { mutableFloatStateOf(0f) }

    val transitionProgress = remember { Animatable(0f) }
    var horizontalLock by remember { mutableIntStateOf(HORIZONTAL_NONE) }
    var transitionMode by remember { mutableStateOf(TransitionMode.Idle) }
    var showNextPreview by remember { mutableStateOf(false) }
    var showPreviousPreview by remember { mutableStateOf(false) }
    var cancelFromPrevious by remember { mutableStateOf(false) }
    var toPreviousStartProgress by remember { mutableFloatStateOf(0f) }

    val swipeThreshold = when (sensitivity) {
        SwipeSensitivity.LOW -> with(density) { 60.dp.toPx() }
        SwipeSensitivity.MEDIUM -> with(density) { 80.dp.toPx() }
        SwipeSensitivity.HIGH -> with(density) { 140.dp.toPx() }
    }
    val swipeDownThreshold = swipeThreshold * 0.8f
    val transitionDistance = swipeThreshold * 2.2f

    fun deletePoolProgressFor(offsetX: Float, offsetY: Float): Float {
        return if (offsetX > 0f && offsetY < 0f) {
            min(
                offsetX / swipeThreshold,
                -offsetY / (swipeThreshold * 0.6f)
            ).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    fun resetAllState() {
        dragOffsetX = 0f
        dragOffsetY = 0f
        zoomScale = 1f
        zoomPanX = 0f
        zoomPanY = 0f
        horizontalLock = HORIZONTAL_NONE
        transitionMode = TransitionMode.Idle
        showNextPreview = false
        showPreviousPreview = false
        cancelFromPrevious = false
        toPreviousStartProgress = 0f
        onDeletePoolProgress(0f)
    }

    LaunchedEffect(item.id) {
        resetAllState()
        transitionProgress.snapTo(0f)
    }

    LaunchedEffect(nextItem?.id) {
        nextItem?.let { adjacent ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(adjacent.uri)
                    .build()
            )
        }
    }

    LaunchedEffect(previousItem?.id) {
        previousItem?.let { adjacent ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(adjacent.uri)
                    .build()
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        val containerWidth = maxWidth
        val maxCardWidth = containerWidth * 0.98f
        val maxCardHeight = maxHeight * 0.9f

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
        val nextCardSize = nextItem?.let { cardSizeFor(it) }
        val previousCardSize = previousItem?.let { cardSizeFor(it) }

        fun edgeDistancePx(cardWidthForLayer: Dp): Float {
            return with(density) {
                (containerWidth.toPx() + cardWidthForLayer.toPx()) / 2f + 32.dp.toPx()
            }
        }

        val exitDistancePx = edgeDistancePx(cardWidth)
        val previousEntryLeftPx = previousCardSize?.let { (w, _) -> -edgeDistancePx(w) }

        val gestureModifier = Modifier.pointerInput(
            item.id,
            nextItem?.id,
            previousItem?.id,
            swipeDownAction,
            swipeThreshold,
            transitionDistance,
            exitDistancePx
        ) {
            forEachGesture {
                coroutineScope {
                    val animScope = this

                    suspend fun animateDragOffsetXTo(
                        target: Float,
                        spec: AnimationSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 420f)
                    ) {
                        val start = dragOffsetX
                        if (start == target) {
                            dragOffsetX = target
                            return
                        }
                        animate(
                            initialValue = start,
                            targetValue = target,
                            animationSpec = spec
                        ) { value: Float, _ ->
                            dragOffsetX = value
                        }
                        dragOffsetX = target
                    }

                    awaitPointerEventScope {
                        var wasDragging = false
                        var wasZooming = false
                        var dragActive = false
                        var cumulativeDrag = Offset.Zero
                        var localHorizontalLock = horizontalLock

                        fun isDeletePool(offsetX: Float, offsetY: Float): Boolean =
                            offsetX > 0f && offsetY < 0f

                        fun applyDrag(delta: Offset) {
                            if (zoomScale > 1f) return

                            transitionMode = TransitionMode.Dragging

                            if (localHorizontalLock != HORIZONTAL_NONE) {
                                dragOffsetX += delta.x
                                dragOffsetY = 0f
                            } else {
                                val nextX = dragOffsetX + delta.x
                                val nextY = dragOffsetY + delta.y
                                dragOffsetX = nextX
                                dragOffsetY = if (nextY > 0f && swipeDownAction == SwipeDownAction.NONE) {
                                    0f
                                } else {
                                    nextY
                                }

                                if (!isDeletePool(nextX, dragOffsetY) &&
                                    abs(nextX) > viewConfiguration.touchSlop &&
                                    abs(nextX) > abs(dragOffsetY)
                                ) {
                                    localHorizontalLock = if (nextX < 0) HORIZONTAL_NEXT else HORIZONTAL_PREVIOUS
                                    horizontalLock = localHorizontalLock
                                    dragOffsetY = 0f
                                }
                            }

                            when (localHorizontalLock) {
                                HORIZONTAL_NEXT -> {
                                    val p = (-dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
                                    if (p >= PREVIEW_REVEAL_THRESHOLD) showNextPreview = true
                                }
                                HORIZONTAL_PREVIOUS -> {
                                    val p = (dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
                                    if (p >= PREVIEW_REVEAL_THRESHOLD) showPreviousPreview = true
                                }
                            }
                        }

                        awaitFirstDown(requireUnconsumed = true)

                        do {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }

                            if (event.changes.size > 1) {
                                wasZooming = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val newScale = zoomScale * zoom
                                if (newScale < 1f) {
                                    zoomScale = 1f
                                    zoomPanX = 0f
                                    zoomPanY = 0f
                                } else {
                                    zoomScale = newScale.coerceIn(1f, 5f)
                                    if (zoomScale > 1f) {
                                        val xMax = (cardWidth.toPx() * (zoomScale - 1f)) / 2f
                                        val yMax = (cardHeight.toPx() * (zoomScale - 1f)) / 2f
                                        zoomPanX = (zoomPanX + pan.x).coerceIn(-xMax, xMax)
                                        zoomPanY = (zoomPanY + pan.y).coerceIn(-yMax, yMax)
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
                                        applyDrag(cumulativeDrag)
                                    }
                                } else {
                                    wasDragging = true
                                    applyDrag(delta)
                                }
                                if (dragActive) {
                                    onDeletePoolProgress(deletePoolProgressFor(dragOffsetX, dragOffsetY))
                                    change.consume()
                                }
                            }
                        } while (anyPressed)

                        if (wasDragging) {
                            val offsetX = dragOffsetX
                            val offsetY = dragOffsetY
                            val isDeletePoolSwipe = offsetX > swipeThreshold &&
                                -offsetY > swipeThreshold * 0.6f

                            val endDrag = {
                                horizontalLock = HORIZONTAL_NONE
                                localHorizontalLock = HORIZONTAL_NONE
                                showNextPreview = false
                                showPreviousPreview = false
                                transitionMode = TransitionMode.Idle
                                onDeletePoolProgress(0f)
                            }

                            when {
                                isDeletePoolSwipe -> {
                                    onSwipeToDeletePool()
                                    resetAllState()
                                    animScope.launch { transitionProgress.snapTo(0f) }
                                }
                                offsetX < -swipeThreshold -> {
                                    val startProgress = (-offsetX / exitDistancePx).coerceIn(0f, 1f)
                                    transitionMode = TransitionMode.ToNext
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    horizontalLock = HORIZONTAL_NONE
                                    animScope.launch {
                                        transitionProgress.snapTo(startProgress)
                                        if (nextItem == null) {
                                            transitionProgress.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f))
                                            endDrag()
                                        } else {
                                            transitionProgress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                                            onSwipeLeft()
                                            transitionProgress.snapTo(0f)
                                            endDrag()
                                        }
                                        showNextPreview = false
                                    }
                                }
                                offsetX > swipeThreshold -> {
                                    val startProgress = (offsetX / exitDistancePx).coerceIn(0f, 1f)
                                    toPreviousStartProgress = startProgress
                                    transitionMode = TransitionMode.ToPrevious
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    horizontalLock = HORIZONTAL_NONE
                                    animScope.launch {
                                        transitionProgress.snapTo(startProgress)
                                        if (previousItem == null) {
                                            transitionProgress.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.86f, stiffness = 420f)
                                            )
                                            endDrag()
                                        } else {
                                            transitionProgress.animateTo(
                                                1f,
                                                tween(220, easing = FastOutSlowInEasing)
                                            )
                                            onSwipeRight()
                                            transitionProgress.snapTo(0f)
                                            endDrag()
                                        }
                                        showPreviousPreview = false
                                    }
                                }
                                offsetY > swipeDownThreshold -> {
                                    onSwipeDown()
                                    dragOffsetY = 0f
                                    endDrag()
                                    animScope.launch { transitionProgress.snapTo(0f) }
                                }
                                else -> {
                                    animScope.launch {
                                        when (localHorizontalLock) {
                                            HORIZONTAL_NEXT -> {
                                                val startProgress = (-offsetX / exitDistancePx).coerceIn(0f, 1f)
                                                if (startProgress > 0f) {
                                                    cancelFromPrevious = false
                                                    transitionMode = TransitionMode.Cancel
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    transitionProgress.snapTo(startProgress)
                                                    transitionProgress.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f))
                                                } else {
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                }
                                                endDrag()
                                            }
                                            HORIZONTAL_PREVIOUS -> {
                                                val startProgress = (offsetX / exitDistancePx).coerceIn(0f, 1f)
                                                if (startProgress > 0f) {
                                                    toPreviousStartProgress = startProgress
                                                    cancelFromPrevious = true
                                                    transitionMode = TransitionMode.Cancel
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    transitionProgress.snapTo(startProgress)
                                                    transitionProgress.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.86f, stiffness = 420f)
                                                    )
                                                } else {
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                }
                                                endDrag()
                                            }
                                            else -> {
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                                transitionProgress.snapTo(0f)
                                                endDrag()
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!wasZooming && !dragActive) {
                            if (zoomScale > 1f) {
                                zoomScale = 1f
                                zoomPanX = 0f
                                zoomPanY = 0f
                            } else {
                                onTap(item)
                            }
                        }
                    }
                }
            }
        }

        val showLeftHint = showNextPreview ||
            transitionMode == TransitionMode.ToNext ||
            (transitionMode == TransitionMode.Cancel && !cancelFromPrevious) ||
            (transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_NEXT)
        val showRightHint = showPreviousPreview ||
            transitionMode == TransitionMode.ToPrevious ||
            (transitionMode == TransitionMode.Cancel && cancelFromPrevious) ||
            (transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_PREVIOUS)

        val leftReveal = when {
            transitionMode == TransitionMode.ToNext -> transitionProgress.value
            transitionMode == TransitionMode.Cancel && !cancelFromPrevious -> transitionProgress.value
            transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_NEXT ->
                (-dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
            else -> 0f
        }
        val rightReveal = when {
            transitionMode == TransitionMode.ToPrevious -> transitionProgress.value
            transitionMode == TransitionMode.Cancel && cancelFromPrevious -> transitionProgress.value
            transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_PREVIOUS ->
                (dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
            else -> 0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullScreenSwipe) gestureModifier else Modifier)
        ) {
            // Next card (back layer): scales up at center while the current card slides left
            if (nextItem != null && nextCardSize != null) {
                val (nextW, nextH) = nextCardSize
                val scaleRange = 1f - ADJACENT_CARD_MIN_SCALE
                val alphaRange = 1f - ADJACENT_CARD_MIN_ALPHA
                Box(
                    modifier = Modifier
                        .width(nextW)
                        .height(nextH)
                        .align(Alignment.Center)
                        .zIndex(1f)
                        .graphicsLayer {
                            val s = ADJACENT_CARD_MIN_SCALE + scaleRange * leftReveal
                            scaleX = s
                            scaleY = s
                            alpha = if (leftReveal > 0f) {
                                (ADJACENT_CARD_MIN_ALPHA + alphaRange * leftReveal).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        }
                ) {
                    pageContent(nextItem, false, true, Modifier.fillMaxSize())
                }
            }

            // Current card: slides left on next swipe; stays centered and scales down on previous swipe (mirror)
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .align(Alignment.Center)
                    .zIndex(if (rightReveal > 0f) 2f else 3f)
                    .then(if (!fullScreenSwipe) gestureModifier else Modifier)
                    .graphicsLayer {
                        val offsetX = dragOffsetX
                        val offsetY = dragOffsetY
                        val progress = transitionProgress.value

                        val deletePoolProgress = if (offsetX > 0f && offsetY < 0f) {
                            min(
                                offsetX / swipeThreshold,
                                -offsetY / (swipeThreshold * 0.6f)
                            ).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        if (zoomScale > 1f) {
                            translationX = zoomPanX
                            translationY = zoomPanY
                            scaleX = zoomScale
                            scaleY = zoomScale
                            return@graphicsLayer
                        }

                        val scaleRange = 1f - ADJACENT_CARD_MIN_SCALE
                        val alphaRange = 1f - ADJACENT_CARD_MIN_ALPHA

                        translationX = when {
                            deletePoolProgress > 0f ->
                                offsetX * 0.5f + with(density) { 48.dp.toPx() } * deletePoolProgress
                            transitionMode == TransitionMode.ToNext ->
                                -exitDistancePx * progress
                            transitionMode == TransitionMode.Cancel && !cancelFromPrevious ->
                                -exitDistancePx * progress
                            rightReveal > 0f -> 0f
                            transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_NEXT ->
                                offsetX
                            else -> 0f
                        }
                        translationY = when {
                            deletePoolProgress > 0f ->
                                offsetY * 0.5f - with(density) { 72.dp.toPx() } * deletePoolProgress
                            transitionMode == TransitionMode.Dragging && horizontalLock != HORIZONTAL_NONE -> 0f
                            else -> offsetY
                        }

                        val transitionScale = when {
                            deletePoolProgress > 0f -> 1f - 0.22f * deletePoolProgress
                            rightReveal > 0f ->
                                ADJACENT_CARD_MIN_SCALE + scaleRange * (1f - rightReveal)
                            else -> 1f
                        }
                        scaleX = transitionScale * zoomScale.let { if (it > 1f) it else 1f }
                        scaleY = scaleX
                        if (zoomScale <= 1f) {
                            scaleX = transitionScale
                            scaleY = transitionScale
                        }
                        alpha = when {
                            deletePoolProgress > 0f ->
                                (1f - 0.35f * deletePoolProgress).coerceAtLeast(0.6f)
                            rightReveal > 0f ->
                                (1f - alphaRange * rightReveal).coerceIn(ADJACENT_CARD_MIN_ALPHA, 1f)
                            else -> 1f
                        }
                        rotationZ = if (deletePoolProgress > 0f) 10f * deletePoolProgress else 0f
                        clip = false
                    }
            ) {
                pageContent(item, true, false, Modifier.fillMaxSize())

                if (showLeftHint) {
                    Canvas(Modifier.fillMaxSize()) {
                        val hintAlpha = when {
                            transitionMode == TransitionMode.ToNext ||
                                transitionMode == TransitionMode.Cancel -> transitionProgress.value
                            else -> (-dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
                        }
                        val intensity = (hintAlpha * 0.7f).coerceAtMost(0.7f)
                        drawRect(
                            brush = Brush.radialGradient(
                                0.0f to swipeHintColor.copy(alpha = intensity),
                                0.15f to swipeHintColor.copy(alpha = intensity * 0.2f),
                                1.0f to Color.Transparent,
                                center = Offset(0f, size.height),
                                radius = size.maxDimension * (1.0f + hintAlpha)
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = stringResource(R.string.next_image),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                            .graphicsLayer {
                                alpha = when {
                                    transitionMode == TransitionMode.ToNext ||
                                        transitionMode == TransitionMode.Cancel -> transitionProgress.value
                                    else -> (-dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
                                }
                            }
                    )
                }

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

            // Previous card: slides in from the left on top — exact reverse of the current card exiting left
            if (previousItem != null && previousCardSize != null) {
                val (prevW, prevH) = previousCardSize
                val previousExitLeftPx = -exitDistancePx
                Box(
                    modifier = Modifier
                        .width(prevW)
                        .height(prevH)
                        .align(Alignment.Center)
                        .zIndex(if (rightReveal > 0f) 3f else 0.5f)
                        .graphicsLayer {
                            translationX = previousExitLeftPx * (1f - rightReveal)
                            scaleX = 1f
                            scaleY = 1f
                            alpha = if (rightReveal > 0f) 1f else 0f
                        }
                ) {
                    pageContent(previousItem, false, true, Modifier.fillMaxSize())
                }
            }

        }
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