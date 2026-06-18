# Swipe Card Stack — Interaction Spec (Frozen)

<!-- AI / Grok NOTE: If user requires git commits, MUST use Conventional Commits. See AGENTS.md + CONTRIBUTING.md. Annotation present so rule is read every time this file is accessed. -->

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

## Organize Mode: Horizontal Actions, Delete, and Reversible Undo (with Icon Switch)

In organize/swiper mode (phone layout), horizontal gestures and bottom bar buttons perform either **decisions** (affect final summary and "processed" items) or **navigation** (review previous cards). All are reversible. The middle action button switches from "?" (help) to "↺" (undo) as soon as the first reversible action is recorded (`reversibleActions.isNotEmpty()`). Clicking undo reverses the *last* action in LIFO order (from `reversibleActions` history). Undo of decisions removes from `pendingChanges`; browse undos only affect view position. Animations play in reverse where applicable (via `undoDirection` + forced `handoffItem` for correct item during undo).

### Action → Trigger Effect → Records → Undo Effect → Animation
- **Left swipe** (horizontal lock in stack) **or** "下一个" button: Record decision to *keep* current item, advance to next undecided item (skipping processed via `effectivePending` / `allProcessedIds`). Triggers undo state (icon to ↺).
  - Records: `ReversibleAction.Decision(Keep(item))` (appended to `reversibleActions`; also to `pendingChanges`).
  - Undo: Remove the matching Keep from `pendingChanges`. Restore item as current (if advanced past it). 
  - Animation: Reverse of left (ToPrevious: current comes from left, previous brought in).

- **Right swipe** (horizontal): Browse back to previous item in list (allows review of prior cards, no decision/commit). Triggers undo state.
  - Records: `ReversibleAction.BrowseBack(forwardIndex = old currentIndex)`.
  - Undo: Advance forward to the recorded `forwardIndex`.
  - Animation: Reverse of right (ToNext).

- **Upper-right diagonal (free drag) or "清除" button**: Record decision to *delete* current, add to delete pool, advance to next (skips deleted). Triggers undo state.
  - Records: `ReversibleAction.Decision(Delete(item))`.
  - Undo: Remove Delete from `pendingChanges` + `deletePoolMediaKeys` / pool manager restore. Set current to the item (re-show it in swiper list).
  - Animation: None (or direct state update; current layer shows the restored item).

**Notes**:
- Left swipe == next button (both keep+advance).
- Upper-right swipe == clear button (both delete+advance).
- Browse (right) is *not* a decision; it doesn't add to `pendingChanges` or affect summary. Only affects reversible history for view undo.
- Browse history is managed via `reversibleActions` (LIFO with decisions).
- Deleted items not shown in swiper "directory" (see below).
- Icon back to "?" when `reversibleActions` empty (all undone, back to initial state / first image).

### Not Showing Deleted Images in Swiper List After Delete
Deleted items must not re-appear in the swipe list (adjacent for gestures, card-stack preview, or next advance) until explicitly restored via undo.

**Implemented mechanism** (pending Delete as visibility mark — Method 1):
- Delete adds `Decision(Delete)` to `reversibleActions` and `pendingChanges`.
- `pendingDeleteIndices()` derives hidden list indices from pending `Delete` decisions.
- **Advance** (`processAndAdvance`): `processedIdsForAdvance()` uses `effectivePending` (decisions with item idx `<= currentIndex`) to build `allProcessedIds`; deleted items are processed → skipped in `nextIndexInList`.
- **Browse adjacent** (`getAdjacentItemsForBrowse`, `getPreviousBrowsableItem`): skips indices with pending `Delete`.
- **Browse commit** (`navigateToAdjacentItem` / `handleSwipeRight`): uses `adjacentBrowsableIndexFiltered()` so right-swipe navigation never lands on a pending-delete item (preview and commit stay aligned).
- **Keep / advance preview** (phone layout): `getUpcomingAdvanceItems()` skips all processed decisions (not only deletes), so the next card shown during left-swipe keep matches the post-advance target. Expanded layout still uses `getUpcomingBrowsableItems()` for pure browse-forward preview.
- **Undo delete**: `revertChange()` removes the `Decision` from `pendingChanges` and matching entry from `reversibleActions`, restores delete pool, and sets `currentItem` / `currentIndex` when the user had advanced past the item.

**Delete after browse back**: With `effectivePending` (only `<= currentIndex`), after right-swipe back + delete at the back position, advance goes to the immediate next item (even if "kept" earlier in the forward pass), because higher keeps are excluded from processed.

This ensures LIFO undo works interleaved with browse/delete.

See `reversibleActions`, `handle*`, `processAndAdvance`, `revertChange`, `performUndo`, `commitReversibleUndo`, `getAdjacentItemsForBrowse`, `getUpcomingAdvanceItems`, `adjacentBrowsableIndexFiltered` in `SwiperViewModel.kt` / `SwiperBrowseNavigation.kt` / `SwiperScreen.kt`.

## Performance (drag path) (continued)