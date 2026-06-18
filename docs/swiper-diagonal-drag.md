# Swipe Card Stack — Diagonal Drag Spec

<!-- AI / Grok NOTE: If user requires git commits, MUST use Conventional Commits. See AGENTS.md + CONTRIBUTING.md. This marker is added so the requirement appears in every file read. -->

> **Status:** Approved UX as of 2026-06-18 (evolving).  
> **Implementation:** `SwipeCardStack.kt`, `SwipeCardStackReveal.kt`  
> **Frozen horizontal browse:** `docs/swiper-card-stack.md` — **do not merge** diagonal logic into left/right reveal math.

## Purpose

Orthogonal gestures on the organize screen that are **not** horizontal photo browsing:

- **Free diagonal drag** — user starts with a tilted finger path; current card follows 1:1 in X and Y.
- **Upper-right commit** — move to delete pool (trash fly animation).
- **Downward diagonal (planned)** — visual prep for transfer-to-album; commit TBD.

Horizontal left/right browse is decided **once** at touch-slop crossing and never shares these visuals.

---

## Gesture intent lock (touch slop)

When cumulative finger travel exceeds `touchSlop`, classify **once** per pointer down:

| Initial direction | `freeDragEnabled` | `horizontalLock` | Release outcomes |
|-------------------|-------------------|------------------|------------------|
| \|X\| > \|Y\| × `HORIZONTAL_DOMINANCE_RATIO` (2.2) | `false` | `HORIZONTAL_NEXT` or `HORIZONTAL_PREVIOUS` | Browse commit/cancel per frozen spec |
| Otherwise (tilted) | `true` | `HORIZONTAL_NONE` | Delete-pool fly **or** spring back — **never** browse commit |

Constants: `HORIZONTAL_DOMINANCE_RATIO = 2.2f` in `SwipeCardStack.kt`.

**Do not** re-enable mid-gesture switching from diagonal to horizontal (causes browse/drag conflicts).

---

## Free drag visuals (all directions)

While `transitionMode == Dragging` and `freeDragEnabled == true`:

### Translation

- `translationX = dragOffsetX`, `translationY = dragOffsetY` (1:1 finger tracking).
- Center = `(0, 0)` offsets at rest.

### Scale (distance from center)

Farther from center → smaller card. Uses shared distance progress:

```kotlin
referencePx = swipeThreshold * DRAG_ROTATION_REFERENCE_MULTIPLIER  // 2.8×
distProgress = sqrt(offsetX² + offsetY²) / referencePx   // 0..1, dead zone < 10px
eased = distProgress²
scale = 1f - FREE_DRAG_SCALE_MAX_DROP * eased           // max drop 0.22 → min ~0.78
```

Functions: `freeDragDistanceProgress()`, `freeDragScaleFor()`.

**Applies uniformly** to up, down, left-diagonal, right-diagonal — not only upper-right.

### Alpha (distance + delete-pool quadrant)

Base fade from distance; upper-right uses the **stricter** (lower) of distance alpha vs delete-pool alpha:

```kotlin
distanceAlpha = (1f - FREE_DRAG_ALPHA_MAX_DROP * eased).coerceAtLeast(0.72f)
// Upper-right only:
poolAlpha = (1f - 0.3f * deletePoolProgress).coerceAtLeast(0.55f)
alpha = min(distanceAlpha, poolAlpha) when in delete quadrant, else distanceAlpha
```

Function: `freeDragAlphaFor()`.

### Rotation (right-external pivot for live drag)

- **Purpose:** Simulate right-hand thumb gripping the right edge of the card and swinging it
  diagonally (to trash upper-right or to folders lower). The tilt angle is the polar angle of
  a radius vector originating from a pivot **outside the card on the right**.
- **Pivot for live free drag:** Fixed right-external `TransformOrigin(RIGHT_EXTERNAL_PIVOT_FRACTION_X, 0.5f)`
  (x > 1.0). The pivot is **not** the trash icon and **not** the card center.
- **Angle computation:** `rightPivotFreeDragRotationZ()` in `SwipeCardStackReveal.kt` uses `atan2(dy, dx + lever)`
  where the lever accounts for the external right pivot. Magnitude is eased by squared distance progress.
  Live drag updates `freeDragCurrentRotation` each frame for a seamless fly handoff.
- **Fly animation (DeletePoolFly):** Outer layer keeps the **same** right-external pivot and **frozen**
  `deleteFlyStartRotation` — do not lerp rotation to 0° or switch pivot at finger-up (causes visible jump).
- **Scale pivot:** card center (`TransformOrigin(0.5f, 0.5f)` on the inner layer) for both drag and fly.
- **Max tilt:** Capped around `DRAG_ROTATION_MAX_DEG` (slightly higher allowance for external pivot).
- **Layers:** outer `graphicsLayer` = translation + rotation (right-external pivot during drag);
  inner = scale + alpha (card center).

This produces a natural arc/swing motion around the virtual right grip point.

---

## Quadrant behaviors on release

### Upper-right → delete pool

**During drag:** `deletePoolProgressFor()` drives trash icon scale in `OrganizeTopBar` (via `onDeletePoolProgress`).

**Commit threshold** (on finger up, only when `freeDragEnabled`):

```kotlin
offsetX > swipeThreshold && -offsetY > swipeThreshold * 0.6f
```

**Fly animation** (`TransitionMode.DeletePoolFly`):

**Seamless handoff at finger-up (t = 0):**

- Snapshot `deleteFlyStartOffset`, `deleteFlyStartScale`, `deleteFlyStartAlpha`, and rotation
  (via `rightPivotFreeDragRotationZ` + `cardLayerWidthPx` — same formula as the last drag frame).
- Set `transitionMode = DeletePoolFly` **before** clearing `freeDragEnabled`; keep `dragOffset` at release
  values until `resetAllState()` after the animation — avoids a one-frame pose snap.
- At t = 0: translation, rotation, scale, and alpha must match the last free-drag frame exactly
  (`deleteFlyShrinkProgress(0) = 0`).

**During fly (t → 1):**

1. Outer `translationX/Y` lerps from `deleteFlyStartOffset` to trash window position (card center → trash icon).
2. Outer `rotationZ` stays at `deleteFlyStartRotation` (no rotation lerp).
3. Inner `scale` / `alpha` shrink via `deleteFlyShrinkProgress(flyT)` (smoothstep on full `flyT`, 300ms `FastOutSlowInEasing`).
4. Then `onSwipeToDeletePool()` → `SwiperViewModel.handleDelete()`.

**Do not** switch inner scale pivot to the trash icon at release — that re-anchors the card and causes a jump.
Shrink-into-trash is achieved by **translation toward trash + center-pivot scale**, not pivot swap.

### Downward diagonal → album transfer (planned)

**Current (2026-06-18):** Same distance-based scale/alpha/rotation as other diagonal directions.
**On release:** spring back to center (`animateDragToOrigin`) — **no** album commit yet.

**Future:** Down-dominant tilt may commit to folder/album transfer (wire to `OrganizeFolderTransferSection` / `moveToFolder`).  
When implementing:

- Add `albumTransferProgressFor()` analogous to `deletePoolProgressFor()`.
- Keep gesture intent lock — do not route through horizontal browse.
- Document thresholds here before coding.

### Other diagonal directions

Release without delete-pool threshold → spring back. No browse commit.

---

## Performance rules

Same as horizontal spec — plus:

1. Drag offsets live in `SwipeGestureState`; read only inside layer `graphicsLayer` / `drawBehind`.
2. `onDeletePoolProgress` only when `freeDragEnabled` and value changes.
3. Delete-pool progress state hoisted in `OrganizePhoneLayout`, not `SwiperScreen` root.
4. Do not put per-frame window positions in `pointerInput` keys.

---

## Files

| File | Role |
|------|------|
| `SwipeCardStack.kt` | Gesture lock, free-drag math, fly animation |
| `SwipeCardStackReveal.kt` | `deleteFlyShrinkProgress`, `rightPivotFreeDragRotationZ`, frozen browse math |
| `SwiperScreen.kt` | `OrganizePhoneLayout`, fly targets, stable `pageContent` |
| `OrganizeUi.kt` | Trash icon scale from `deletePoolSwipeProgress` |
| `SwiperViewModel.kt` | `handleDelete`, delete pool, reversible undo (see card-stack doc) |

---

## Regression checklist (diagonal)

1. Horizontal swipe: card does **not** shrink with Y or rotate; only frozen browse motion.
2. Tilted swipe: card scales down as it moves away from center in **any** direction.
3. Upper-right past threshold: flies to trash, vanishes at icon.
4. **No pose jump at finger-up:** first fly frame matches last drag frame (position, angle, scale).
5. Downward tilt: shrinks while dragging; springs back on release (until album commit exists).
6. Tilted swipe that later moves far left: does **not** switch to next/previous photo.

## Regression checklist (horizontal — unchanged)

See `docs/swiper-card-stack.md`.