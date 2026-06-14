# Swipe Card Stack — Diagonal Drag Spec

> **Status:** Approved UX as of 2026-06-14 (evolving).  
> **Implementation:** `app/src/main/java/com/cleansweep/ui/screens/swiper/SwipeCardStack.kt`  
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

### Rotation

- Upright at center; max ~6° at full reference distance.
- `dragRotationZ()` — horizontal bias × squared distance falloff.

---

## Quadrant behaviors on release

### Upper-right → delete pool

**During drag:** `deletePoolProgressFor()` drives trash icon scale in `OrganizeTopBar` (via `onDeletePoolProgress`).

**Commit threshold** (on finger up, only when `freeDragEnabled`):

```kotlin
offsetX > swipeThreshold && -offsetY > swipeThreshold * 0.6f
```

**Fly animation** (`TransitionMode.DeletePoolFly`):

1. Translation lerps to trash window position (card center lands on trash icon).
2. Scale/alpha shrink to 0 in the last ~42% of fly (`deleteFlyShrinkProgress`, arrive fraction 0.58).
3. Then `onSwipeToDeletePool()`.

Fly start scale/alpha snapshot: `freeDragScaleFor` / `freeDragAlphaFor` at release offset.

### Downward diagonal → album transfer (planned)

**Current (2026-06-14):** Same distance-based scale/alpha/rotation as other diagonal directions.  
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
| `SwiperScreen.kt` | `OrganizePhoneLayout`, fly targets, stable `pageContent` |
| `OrganizeUi.kt` | Trash icon scale from `deletePoolSwipeProgress` |

---

## Regression checklist (diagonal)

1. Horizontal swipe: card does **not** shrink with Y or rotate; only frozen browse motion.
2. Tilted swipe: card scales down as it moves away from center in **any** direction.
3. Upper-right past threshold: flies to trash, vanishes at icon.
4. Downward tilt: shrinks while dragging; springs back on release (until album commit exists).
5. Tilted swipe that later moves far left: does **not** switch to next/previous photo.

## Regression checklist (horizontal — unchanged)

See `docs/swiper-card-stack.md`.