# Plan #1 — Defer `revalidate()` during drag operations

## Goal

Eliminate the per-property-change `revalidate()` cascade that fires inside
`PlanComponent`'s model listeners during interactive drags in the plan view.

When the user drags a wall or piece of furniture, the `PlanController` fires
4–8 separate `PropertyChangeEvent`s per mouse‑move (e.g. `setXStart`,
`setYStart`, `setXEnd`, `setYEnd`, possibly twice for joined walls). Each event
hits a `PropertyChangeListener` in `PlanComponent` that today calls
`revalidate()`. `PlanComponent.revalidate()`
(`src/com/eteks/sweethome3d/swing/PlanComponent.java:1201') runs the Swing
layout pass on the plan, repaints the plan, **and** revalidates *and* repaints
**both rulers**. The override of `invalidate(boolean)`
(`PlanComponent.java:1219-1232`) that runs inside that layout pass also clears
`planBoundsCacheValid`, which then forces `getPlanBounds()` to walk every
selectable item again on the next paint.

The expected speedup comes fromtwo effects:

1. Removing the layout-pass / ruler-revalidation cost from inside each mouse
   event (the EDT now only schedules a single coalesced `repaint()`, plus
   "ruler repaints", per event; the heavier `revalidate()` pass happens exactly
   once, when the drag finishes).
2. Keeping `planBoundsCacheValid = true` during the drag, because the deferred
   path no longer runs `super.revalidate()` → `invalidate(true)`. As a result,
   `paintComponent` (line 2197) and the scroll-pane preferred-size path can
   reuse the cached plan bounds for the whole drag instead of recomputing them
   on every move. This is the dominant per-move saving for large plans.

Existing patterns to mirror: `PlanComponent.java:765-783` already defers
expensive per-event work — the top-view icon key removal — until the
controller's `MODIFICATION_STATE` property fires back to `false`, by
registering a one‑shot `PropertyChangeListener` on
`PlanController.Property.MODIFICATION_STATE` that deregisters itself after
flushing. The same pattern (implemented once and reused) is the right shape
for the `revalidate()` deferral.

## Behavior preservation requirements

- Behavior OUTSIDE any drag (`controller == null || !controller.isModificationState()`)
  MUST remain identical to today: every listener branch still calls
  `revalidate()` (the existing override), i.e. plan + rulers are revalidated
  and repainted immediately. All paths exercised by file load, undo/redo, paste,
  programmatic model mutation, or final mouse release must look and behave
  exactly as before.
- Behavior AT THE END of the drag: when `MODIFICATION_STATE` fires back to
  `false`, a single `revalidate()` (the full existing override) MUST be invoked
  so plan + rulers layout/scrollbars update for the new bounds.
- Behavior DURING the drag:
  - `repaint()` (coalesced, cheap) MUST still be issued so the visual drag
    feedback stays live.
  - Ruler components MUST still be repainted (the rulers display the live
    cursor coordinate, tick marks, etc.), but they do NOT need to be
    revalidated (no live resize of rulers is required during a drag — content
    update is enough).
  - Cache fields that the listeners null per event today (e.g.
    `wallAreasCache`, `doorOrWindowWallThicknessAreasCache`,
    `sortedLevelFurniture`, etc.) MUST still be nulled during the drag — those
    are needed so each drag-step's paint draws the current geometry. ONLY the
    `revalidate()` call is being deferred.

## Files to edit

Only one source file is modified:

`src/com/eteks/sweethome3d/swing/PlanComponent.java`

No model, controller, or test files change behaviorally. New tests are not
required because the change is a pure performance refactor inside the view;
existing tests in `test/com/eteks/sweethome3d/junit/PlanComponentTest.java` (run
under Xvfb) cover plan rendering and drag semantics.

## Step‑by‑step implementation

### Step 1 — Add two fields and a flush helper near the other cache fields

Insert immediately after the existing field at line 323
(`private Map<Collection<Wall>, Area> wallAreasCache;`):

```java
private boolean                            deferredRevalidateScheduled;
```

Insert a new private method just below the existing
`public void revalidate()` override at lines 1200‑1214. The override itself
stays unchanged. The new method is `revalidateOrDefer`:

```java
/**
 * Revalidates this component immediately, or defers the revalidation until
 * the end of the current drag modification when the controller reports
 * a drag is in progress. In the deferred path only visual {@code repaint()}
 * calls are issued (this component and its rulers); the heavier
 * {@link #revalidate()} pass that propagates up to the scroll pane and
 * overrides {@code planBoundsCacheValid} is fired once when the
 * modification state returns to {@code false}.
 */
private void revalidateOrDefer(PlanController controller) {
  if (controller != null && controller.isModificationState()) {
    if (!this.deferredRevalidateScheduled) {
      this.deferredRevalidateScheduled = true;
      final PropertyChangeListener deferredRevalidateListener = new PropertyChangeListener() {
          public void propertyChange(PropertyChangeEvent ev) {
            PlanComponent.this.deferredRevalidateScheduled = false;
            controller.removePropertyChangeListener(
                PlanController.Property.MODIFICATION_STATE, this);
            // Single full revalidate (this override also revalidates+repaints rulers).
            PlanComponent.this.revalidate();
          }
        };
      controller.addPropertyChangeListener(
          PlanController.Property.MODIFICATION_STATE, deferredRevalidateListener);
    }
    // Visual-only update during the drag — no layout pass, no plan-bounds-cache invalidation.
    repaint();
    if (this.horizontalRuler != null) {
      this.horizontalRuler.repaint();
    }
    if (this.verticalRuler != null) {
      this.verticalRuler.repaint();
    }
  } else {
    // Outside a drag, behave exactly as before.
    revalidate();
  }
}
```

Notes / gotchas:
- `PlanComponent.this` is required inside the anonymous inner class to reach
  the outer instance field/method from inside a nested anonymous class declared
  in a non‑static method; if the compiler complains, use the unqualified name
  (Java 8+ allows `deferredRevalidateScheduled = false;` directly when there
  is no naming ambiguity). Prefer the qualified form for safety.
- The anonymous listener is registered on the existing `PropertyChangeListener`
  support at `PlanController.addPropertyChangeListener(PlanController.Property,
  PropertyChangeListener)` (`PlanController.java:323-325`). The
  `MODIFICATION_STATE` property is fired by `setState()` at
  `PlanController.java:310-313` whenever `isModificationState()` flips between
  the previous and the new state. Because the listener is only ever registered
  while `controller.isModificationState()` is `true`, the next fire of that
  property is guaranteed to be the drag‑exit transition — exactly like the
  existing pattern at lines 771‑781.
- The same `deferredRevalidateScheduled` flag is reused across all listener
  sites; only the first event during a drag registers the one‑shot listener.
  All subsequent events only call `repaint()` / ruler `repaint()` until drag
  end, where the single shared `revalidate()` flushes.

### Step 2 — Route every `revalidate()` call inside the model listeners through `revalidateOrDefer(controller)`

The model listeners are set up inside the private method
`addModelListeners(final Home home, final UserPreferences preferences,
final PlanController controller)` defined at line 740. The `controller` local
variable is in scope inside every anonymous listener instance, so
`revalidateOrDefer(controller)` can be used directly.

Replace each occurrence listed below. Branches that currently call `repaint()`
instead of `revalidate()` MUST be left untouched (those events never touched
the plan-bounds cache in the first place — they only need a visual refresh).

| Line (before change) | Listener | Old call | New call |
| --- | --- | --- | --- |
| 785  | `furnitureChangeListener` (model/transform change branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 819  | `furnitureChangeListener` (door/window wall‑thickness branch, after `remove(...) != null`) | `revalidate();` | `revalidateOrDefer(controller);` |
| 821  | `furnitureChangeListener` (final `else` branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 852  | `home.addFurnitureListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 875  | `wallChangeListener` (X/Y start/end, thickness, arc extent, pattern, wall‑at‑start/end branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 882  | `wallChangeListener` (LEVEL / HEIGHT / HEIGHT_AT_END branch) — currently `repaint();` | `repaint();` | **leave as `repaint();`** (no change) |
| 900  | `home.addWallsListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 924  | `roomChangeListener` (POINTS/NAME/AREA_VISIBLE/FLOOR_VISIBLE/CEILING_VISIBLE branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 929  | `roomChangeListener` (FLOOR_COLOR/FLOOR_TEXTURE branch) — currently `repaint();` | `repaint();` | **leave unchanged** |
| 946  | `home.addRoomsListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 958  | `changeListener` for polylines (`else` branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 956  | `changeListener` for polylines — COLOR/DASH_STYLE branch — currently `repaint();` | `repaint();` | **leave unchanged** |
| 972  | `home.addPolylinesListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 990  | `dimensionLineChangeListener` (geometry/offset/style branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 992  | `dimensionLineChangeListener` — COLOR branch — currently `repaint();` | `repaint();` | **leave unchanged** |
| 1006 | `home.addDimensionLinesListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 1013 | `labelChangeListener` | `revalidate();` | `revalidateOrDefer(controller);` |
| 1026 | `home.addLabelsListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 1036 | `levelChangeListener` (BACKGROUND_IMAGE branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 1041 | `levelChangeListener` (ELEVATION/ELEVATION_INDEX/VIEWABLE branch) — currently `repaint();` | `repaint();` | **leave unchanged** |
| 1056 | `home.addLevelsListener` collection listener | `revalidate();` | `revalidateOrDefer(controller);` |
| 1062 | `home.addPropertyChangeListener(Home.Property.CAMERA, …)` | `revalidate();` | `revalidateOrDefer(controller);` |
| 1075 | `home.getObserverCamera().addPropertyChangeListener(...)` (X/Y/FOV/YAW/WIDTH/DEPTH/HEIGHT branch) | `revalidate();` | `revalidateOrDefer(controller);` |
| 1087 | `home.getCompass().addPropertyChangeListener(...)` (X/Y/NORTH_DIRECTION/DIAMETER/VISIBLE branch) | `revalidate();` | `revalidateOrDefer(controller);` |

Also leave UNCHANGED every `revalidate()` call site that lives outside
`addModelListeners`:

- `PlanComponent.java:1158` (`preferencesListener` → `case DEFAULT_FONT_NAME`)
  and any other revalidate calls inside `Utilities` / `addControllerListener`
  / `addMouseListeners` — these are NOT in the per-event drag hot path, and
  they run outside a drag (font size change, controller mode change, etc.).
- The override `public void revalidate()` itself (lines 1200‑1214) stays as
  written.

### Step 3 — Verify imports

`PlanController` is already imported
(`src/com/eteks/sweethome3d/swing/PlanComponent.java` references
`PlanController.Property.MODIFICATION_STATE` at line 771). No new imports are
required for this change.

## Risks and mitigations

- **Dragging beyond the visible plan (negative or far‑positive coordinates):**
  While the scroll-pane's preferred size is not refreshed mid‑drag under this
  change, the drag operators already call
  `PlanView.makePointVisible(x, y)` (`PlanComponent.java:5729`) — typically
  from `WallDrawingState.moveMouse`
  (`PlanController.java:10662-10730`) and the furniture-drag variants — which
  uses `JComponent.scrollRectToVisible` to keep the cursor on‑screen. The
  end‑of‑drag `revalidate()` then updates the scroll bars and the viewport
  position (via `validate()` at `PlanComponent.java:1242-1266`). Net effect:
  the cursor stays visible during the drag, and the bars catch up at drag end.
  This is exactly the behavior the original ranking expected.
- **Listener cleanup when the controller becomes garbage:** the one‑shot
  listener removes itself as soon as it fires, so it can never pile up. The
  controller is held by the plan controller / home, not by `PlanComponent`,
  so no new long-lived reference is introduced.
- **Re‑entrancy:** `deferredRevalidateScheduled` is read/written on the EDT
  only (model property-change events and `MODIFICATION_STATE` events are EDT
  notifications). The non‑`volatile` boolean is therefore safe; do not change
  its visibility or add synchronization.
- **Multiple concurrent drag source types:** All listeners share the same
  single flag and a single `revalidate()` flush. If, say, a wall change and a
  furniture change fire back‑to‑back in the same EDT batch during the same
  drag, only one of them registers the one‑shot listener; the second sees the
  flag already set and skips the registration. When the drag ends, the shared
  listener fires `revalidate()` once, which is the desired coalesced final
  update.

## Verification

1. **Compile:**  
   From the repository root:
   ```
   ant compile
   ```
   If the build downloads the pinned JUnit / Hamcrest JARs automatically,
   that's fine — it does not affect compilation.

2. **Headless tests:**  
   ```
   ant test
   ```
   All ten test classes listed in `build.xml:281-290` must pass; these cover
   model mutation paths that previously triggered the listeners' `revalidate()`
   branches outside a drag.

3. **GUI tests (require an X server; use the project's existing task):**
   ```
   xvfb-run -a -s "-screen 0 1920x1080x24 -ac +extension GLX +render -noreset" ant clean test-all
   ```
   Or use the project Taskfile entry:
   ```
   task test
   ```
   This must pass — particularly `PlanComponentTest`, `HomeCameraTest`, and
   `BackgroundImageWizardTest`. These tests instantiate `PlanComponent`, drive
   the controller through mouse events, and assert on the resulting plan
   content; the deferred‑revalidate path must produce the same end state.

4. **Manual smoke test:** Build the standalone JAR (`ant jarExecutable`) and
   run it. Add 50+ walls to a home and drag one across the plan; the drag
   should feel smoother than before, the scroll bars should update on mouse
   release, and the rulers should keep updating their live coordinate readout
   during the drag. Behavior must be visually equivalent to before (same paint
   output, same end‑state).

5. **No new files** should be added. Leave the existing
   `private boolean deferredRevalidateScheduled;` field right next to the
   other Swing cache fields.

## Acceptance checklist

- [ ] `deferredRevalidateScheduled` field added near line 323.
- [ ] `revalidateOrDefer(PlanController)` method added below the existing
      `revalidate()` override.
- [ ] Every `revalidate()` site listed in the table above is replaced with
      `revalidateOrDefer(controller)`; the `repaint()`‑only branches are left
      untouched.
- [ ] No other `revalidate()` call sites are modified.
- [ ] `public void revalidate()` override at lines 1200‑1214 is unchanged.
- [ ] `ant compile` succeeds.
- [ ] `ant test` passes headless.
- [ ] `task test` (Xvfb test-all) passes.