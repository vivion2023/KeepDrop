# Swipe Card Stack — Interaction Spec (Frozen)

> **Status:** Approved UX as of 2026-06-14.  
> **Implementation:** `app/src/main/java/com/cleansweep/ui/screens/swiper/SwipeCardStack.kt`  
> **Do not change** the horizontal browse transition logic unless the user explicitly requests a redesign. UI chrome around the stack (`OrganizeUi.kt`, `SwiperScreen.kt`) may evolve independently.

## Purpose

Card-stack photo browsing on the organize screen. Not a full-width pager. Left and right swipes are **mirror images** of each other (right swipe = reverse playback of left swipe).

## Layer order (z-index)

| z-index | Layer    | Role |
|--------:|----------|------|
| 1       | Next     | Back — only visible during left swipe |
| 2 or 3  | Current  | 3 when idle / left swipe; **2** when `rightReveal > 0` |
| 0.5 or 3| Previous | Hidden when idle; **3** (on top) when `rightReveal > 0` |

## Idle state

- Only **current** card is visible (opaque, full scale, centered).
- **Next** and **previous** are preloaded but `alpha = 0` (not composed visibly). Do not show adjacent cards at rest.

## Constants (do not tune without UX review)

```kotlin
ADJACENT_CARD_MIN_SCALE = 0.92f
ADJACENT_CARD_MIN_ALPHA = 0.35f
```

Progress for drag and commit animations uses **`exitDistancePx`** (not `transitionDistance`) so finger tracking matches release animation.

```kotlin
exitDistancePx = (containerWidth + cardWidth) / 2 + 32.dp
leftReveal  = (-dragOffsetX / exitDistancePx)  // finger moves left
rightReveal = ( dragOffsetX / exitDistancePx)  // finger moves right
```

## Left swipe → next (finger moves left)

| Card     | Translation | Scale | Alpha |
|----------|-------------|-------|-------|
| Current  | Follows finger left; on commit animates to `-exitDistancePx` | 1.0 | 1.0 |
| Next     | **Fixed at center** | `0.92 → 1.0` with `leftReveal` | `0.35 → 1.0` with `leftReveal` |

- Current is the **top** card and **moves horizontally**.
- Next stays centered underneath and **grows + fades in**.

## Right swipe → previous (finger moves right) — mirror of left

| Card     | Translation | Scale | Alpha |
|----------|-------------|-------|-------|
| Current  | **Fixed at center** (`translationX = 0`) | `1.0 → 0.92` with `rightReveal` | `1.0 → 0.35` with `rightReveal` |
| Previous | `-exitDistancePx → 0` (slides in from where left-exit ends) | 1.0 | **1.0** (fully opaque when visible) |

- Previous is the **top** card and **moves horizontally** from the left.
- Current stays centered underneath and **shrinks + fades out**.
- **Do not** translate current right during right swipe (causes side-by-side peek bug).
- **Do not** fade previous in — incoming card is opaque; only current fades.

## Commit / cancel

- **Left commit:** `transitionProgress` 0→1, current exits left, then `onSwipeLeft()`.
- **Right commit:** `transitionProgress` `startProgress→1`, previous lands center, current at min scale/alpha, then `onSwipeRight()`.
- **Cancel left:** `Cancel` + `cancelFromPrevious = false`, snap current back from partial left exit.
- **Cancel right:** `Cancel` + `cancelFromPrevious = true`, snap previous back off-screen left, current scale/alpha restore.

## Diagonal drag (orthogonal — separate doc)

**Full spec:** [`docs/swiper-diagonal-drag.md`](swiper-diagonal-drag.md)

Diagonal / free drag, delete-pool fly, and planned album-transfer gestures are **not** part of this frozen browse spec. They use `freeDragEnabled`, distance-based scale, and separate release handlers.

**Do not** change `leftReveal` / `rightReveal` or horizontal commit/cancel when working on diagonal features.

## Performance (drag path)

Horizontal browse math above is frozen; these rules apply to **how** it is implemented:

1. **Reveal progress** (`leftReveal` / `rightReveal`) must be computed inside `graphicsLayer` / `drawBehind` lambdas via `leftRevealProgress()` / `rightRevealProgress()` — not in the Composable function body. Same formulas as the spec.
2. **z-index** during right swipe uses `horizontalLock` / `transitionMode`, not a body-level reveal float.
3. **Delete-pool progress** callbacks fire only when the value changes (avoids parent chrome recomposition).
4. **Adjacent preload** and preview `AsyncImage` decode at screen-width cap (~1440px max), not full resolution.
5. **Left hint** uses a light `drawBehind` horizontal gradient, not a per-frame radial `Canvas`.

If drag feels laggy after edits, check for new `dragOffsetX` reads in the Composable body or heavy work inside `pageContent` on every pointer event.

## Regression checklist

Before merging changes to `SwipeCardStack.kt`:

1. Idle: no second card visible at bottom or sides.
2. Left drag: current follows finger left; next scales up at center only.
3. Right drag: previous slides from left at full opacity; current centered, shrinking, fading.
4. No horizontal jump on finger release (same `exitDistancePx` for drag progress and commit).
5. Right swipe looks like left swipe played in reverse.