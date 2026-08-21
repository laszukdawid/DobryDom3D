# Code Quality

Overall: the fork's own changes are higher quality than the inherited code.
The weakness is not quality of what exists — it is untested surface (see
[Testing](Testing.md)).

## The fork's changes — good, with receipts

Patterns worth keeping consistent:

- **Cache invalidation is event-driven and thorough.** `PlanComponent`
  nulls the right caches on every furniture/wall/room/level mutation,
  including cross-level areas (`PlanComponent.java` ~815–1234).
  `Wall.clearPointsCache()` propagates invalidation to joined walls — a bug
  class upstream got wrong (fixed in `db95db3`, #35).
- **Identity-keyed selection set.** `Home.setSelectedItemsList`
  (`model/Home.java:961`) backs `isItemSelected` with an identity-based set.
  Correct call: furniture `equals` is value-based and unstable mid-drag.
- **Fail-open culling.** The clip test in `PlanComponent.intersectsClipBounds`
  (~3467) is written as the negation of "provably outside", so NaN coordinates
  from hand-edited files cause the item to be *painted*, never silently
  dropped.
- **Reentrancy-safe listener notification** — listeners are copied before
  iteration everywhere (`Home.java:945`).
- Commit messages explain root cause and mechanism; each fix ships its own
  regression test.

## Inherited code — dated but not rotten

Known warts, fix opportunistically when touching nearby code:

- Pre-generics-style collections in places; raw threads in older panels.
- ~65 `printStackTrace()` calls and ~42 broad `catch (Exception)` blocks.
- `ModelManager` lock-juggling around non-thread-safe Java3D cloning
  (`cloneLock`; cloning is documented not thread safe by Java 3D).
- Per-repaint allocation of identical strokes/paints duplicated in
  `paintContent` and `paintHomeItems` (`PlanComponent.java` ~3234 and ~3334) —
  trivially hoistable, sits in the hottest path.

## Perf work status

The 2026-08 performance series (#31–#38) is reviewed and sound:

- Clip-based item culling per painter, with margins for strokes/labels.
- Dirty-region repaint computation, verified differentially by
  `PlanComponentRepaintBoundsTest`.
- Grid painting restricted to clip + batched into single paths.
- O(1) selection membership and top-view icon key lookup.

Known risk to watch: caches keyed by mutable model objects must stay
invalidation-complete; new item properties added to the model must be checked
against the caches in `PlanComponent` (and shape caches in `Wall`,
`Polyline`, `Room`).
