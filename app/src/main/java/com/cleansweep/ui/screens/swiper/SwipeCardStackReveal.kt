/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Frozen horizontal browse math for SwipeCardStack.
 * Spec: docs/swiper-card-stack.md
 */

package com.cleansweep.ui.screens.swiper

/** Frozen card-stack constants — see docs/swiper-card-stack.md */
internal const val ADJACENT_CARD_MIN_SCALE = 0.92f
internal const val ADJACENT_CARD_MIN_ALPHA = 0.35f

/** -1 = swiping to next (finger moves left), 0 = free, 1 = swiping to previous */
internal const val HORIZONTAL_NONE = 0
internal const val HORIZONTAL_NEXT = -1
internal const val HORIZONTAL_PREVIOUS = 1

/**
 * Visual transition driven by [transitionProgress] after the finger lifts.
 * [dragOffsetX] drives live finger tracking while [transitionMode] is [TransitionMode.Dragging].
 */
internal enum class TransitionMode {
    Idle,
    Dragging,
    ToNext,
    ToPrevious,
    Cancel,
    /** Post-commit hold: keeps layer positions frozen while [item] catches up; no swipe hints. */
    Handoff,
    /** Release after upper-right delete-pool commit: shrink and fly toward the trash icon. */
    DeletePoolFly
}

/** exitDistancePx = (containerWidth + cardWidth) / 2 + edgePaddingPx — frozen spec formula. */
internal fun swipeCardExitDistancePx(
    containerWidthPx: Float,
    cardWidthPx: Float,
    edgePaddingPx: Float
): Float = (containerWidthPx + cardWidthPx) / 2f + edgePaddingPx

/** Frozen reveal math — call only from layer modifiers. */
internal fun leftRevealProgress(
    transitionMode: TransitionMode,
    horizontalLock: Int,
    dragOffsetX: Float,
    exitDistancePx: Float,
    transitionProgress: Float,
    cancelFromPrevious: Boolean,
    handoffToNext: Boolean
): Float = when {
    transitionMode == TransitionMode.ToNext -> transitionProgress
    transitionMode == TransitionMode.Handoff && handoffToNext -> transitionProgress
    transitionMode == TransitionMode.Cancel && !cancelFromPrevious -> transitionProgress
    transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_NEXT ->
        (-dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
    else -> 0f
}

internal fun rightRevealProgress(
    transitionMode: TransitionMode,
    horizontalLock: Int,
    dragOffsetX: Float,
    exitDistancePx: Float,
    transitionProgress: Float,
    cancelFromPrevious: Boolean,
    handoffToNext: Boolean
): Float = when {
    transitionMode == TransitionMode.ToPrevious -> transitionProgress
    transitionMode == TransitionMode.Handoff && !handoffToNext -> transitionProgress
    transitionMode == TransitionMode.Cancel && cancelFromPrevious -> transitionProgress
    transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_PREVIOUS ->
        (dragOffsetX / exitDistancePx).coerceIn(0f, 1f)
    else -> 0f
}

/** Smoothstep shrink during delete-pool fly — no flat region after finger release. */
internal fun deleteFlyShrinkProgress(flyT: Float): Float {
    val t = flyT.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Rotation during free diagonal drag (right-external pivot). Shared by live drag and fly snapshot.
 */
internal fun rightPivotFreeDragRotationZ(
    offsetX: Float,
    offsetY: Float,
    layerWidthPx: Float,
    dragRotationReferencePx: Float,
    startAngleDeg: Float,
    leverFraction: Float = 1.5f,
    angleScale: Float = 0.25f,
    maxDeg: Float = 15f
): Float {
    if (layerWidthPx <= 0f) return 0f
    val lever = layerWidthPx * leverFraction
    val dx = offsetX + lever
    val dy = offsetY
    val currentRaw = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    var delta = currentRaw - startAngleDeg
    delta = delta % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    val distSq = offsetX * offsetX + offsetY * offsetY
    val outward = if (dragRotationReferencePx <= 0f || distSq < 100f) {
        0f
    } else {
        (kotlin.math.sqrt(distSq) / dragRotationReferencePx).coerceIn(0f, 1f)
    }
    val rotationEased = outward * 0.85f + outward * outward * 0.15f
    return (-delta * angleScale * rotationEased).coerceIn(-maxDeg, maxDeg)
}

internal fun isPreviousLayerOnTop(
    transitionMode: TransitionMode,
    horizontalLock: Int,
    cancelFromPrevious: Boolean,
    handoffToNext: Boolean
): Boolean =
    transitionMode == TransitionMode.ToPrevious ||
        (transitionMode == TransitionMode.Handoff && !handoffToNext) ||
        (transitionMode == TransitionMode.Cancel && cancelFromPrevious) ||
        (transitionMode == TransitionMode.Dragging && horizontalLock == HORIZONTAL_PREVIOUS)