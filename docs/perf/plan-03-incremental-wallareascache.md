# Plan #3 — Incremental `wallAreasCache` via per‑wall Area memoization

## Goal

Stop rebuilding every wall's `java.awt.geom.Area` from scratch on every paint
(stderr event) during a drag. Today, every wall property‑change event nullifies
`wallAreasCache` (`src/com/eteks/sweethome3d/swing/PlanComponent.java:873`).
On the next paint, `paintWalls` (`PlanComponent.java:3688-3719`) and the
selected-walls outline path (`PlanComponent.java:3823-3828`) call
`getWallAreas(...)` (`PlanComponent.java:3948-3984`) which calls
`getItemsArea(...)` (`PlanComponent.java:3989-3995`) which performs, for each
wall:

```java
itemsArea.add(new Area(ShapeTools.getShape(item.getPoints(), true, null)));
```

That re‑wraps every wall's points into a `Path2D` and then into an `Area` on
every paint — even though only one wall typically changed shape during the
drag.

This plan introduces a per-`Wall` `Area` memoization map. The expensive
`ShapeTools.getShape(...)` + `new Area(...)` work for an unchanged wall happens
once and is reused across paints and across both the fill and outline paint
passes. Only the wall whose geometry changed pays the wrap cost on the next
paint. The per‑pattern `Area` union itself is still re‑unioned on each paint
(the cheap `Area.add` of cached per‑wall `Area`s), so the work scales with the
total wall count but never with the per‑wall geometry recomputation.

This deliberately stops short of incremental per‑pattern `Area` *subtraction*
(which would avoid the union rebuild entirely): `Area.subtract` on overlapping
walls can fragment the path geometry and create pathological complexity over
many updates. The lower‑risk change here — per‑wall memoization plus union
rebuild from cached operands — captures most of the speedup with no geometric
risk.

## Behavior preservation requirements

- Visual output of all paint paths MUST be byte‑wise identical to today's
  output for the same input: same wall shapes, same per‑pattern grouping, same
  union shape used for fill and stroke, same outlines for selected walls.
- `getWallAreas(Collection<Wall>)` MUST continue to return the same type —
  `Map<Collection<Wall>, Area>` — with the same keys (the per‑pattern wall
  collections) and the same union values. Callers at lines 3710‑3714 and
  3823‑3828 that read `.getKey().iterator().next().getPattern()` and `.values()`
  MUST NOT be modified.
- `getItemsArea(Collection<? extends Selectable>)` MUST continue to return an
  `Area` that is the union of the input items' shapes, with identical results
  for non‑Wall items (Rooms, Labels' anchor‑shape). The signature, name, and
  return value MUST stay unchanged.
- Memory: the per‑wall cache MUST NOT retain walls after they are removed from
  the home (no leak beyond home lifetime). Walls deleted via
  `home.deleteWall(...)`/`deleteWalls(...)` MUST have their `Area` entry
  removed from the cache.
- Wall geometry that does NOT change during a drag (LEVEL, HEIGHT, HEIGHT_ATEnd,
  PATTERN adjustments) MUST NOT invalidate the per‑wall `Area`. The shape in
  the plan view depends only on `X_START`, `Y_START`, `X_END`, `Y_END`,
  `THICKNESS`, `ARC_EXTENT`, `WALL_AT_START`, `WALL_AT_END`.

## Files to edit

Only one source file is modified:

`src/com/eteks/sweethome3d/swing/PlanComponent.java`

## Step‑by‑step implementation

### Step 1 — Add the per‑wall cache field

Insert a new field immediately after the existing
`private Map<Collection<Wall>, Area> wallAreasCache;` line (line 323):

```java
private Map<Wall, Area>                     wallAreaCache = new IdentityHashMap<>();
```

`IdentityHashMap` is chosen because `Wall` does NOT override
`equals`/`hashCode` (`verify with: grep -n "public boolean equals\|public int hashCode" src/com/eteks/sweethome3d/model/Wall.java` — both should return no matches). Using identity semantics makes the memoization
explicit and avoids accidental collisions if a `Wall` ever gets its
`equals` overridden in the future.

`IdentityHashMap` is already imported at line 99
(`import java.util.IdentityHashMap;`); no new imports are needed. `Wall`
is imported at the top of `PlanComponent.java`.

### Step 2 — Add a `getWallArea(Wall)` helper

Insert a private helper method directly above
`private Map<Collection<Wall>, Area> getWallAreasAtLevel(Level level)`
(defined at line 3925):

```java
/**
 * Returns the memoized {@link Area} matching the plan‑view shape of the given
 * wall. Computing the area is the expensive wrapping of
 * {@link ShapeTools#getShape(float[][], boolean, java.awt.Shape)} with
 * {@code new Area(...)}; subsequent reads for the same wall instance return
 * the cached instance until the wall's geometry changes (at which point the
 * caller removes the stale entry, see {@link #wallAreaCache}).
 */
private Area getWallArea(Wall wall) {
  Area area = this.wallAreaCache.get(wall);
  if (area == null) {
    area = new Area(ShapeTools.getShape(wall.getPoints(), true, null));
    this.wallAreaCache.put(wall, area);
  }
  return area;
}
```

### Step 3 — Make `getItemsArea` use the per‑wall memoization for walls

Edit the existing `getItemsArea(Collection<? extends Selectable> items)`
method at `PlanComponent.java:3989-3995`. The current body is:

```java
private Area getItemsArea(Collection<? extends Selectable> items) {
  Area itemsArea = new Area();
  for (Selectable item : items) {
    itemsArea.add(new Area(ShapeTools.getShape(item.getPoints(), true, null)));
  }
  return itemsArea;
}
```

Replace the body so per‑wall items pull from the memoization; non‑Wall items
fall back to the inline construction so behavior for `Room`,
`Polyline`, etc. is unchanged byte‑for‑byte:

```java
private Area getItemsArea(Collection<? extends Selectable> items) {
  Area itemsArea = new Area();
  for (Selectable item : items) {
    if (item instanceof Wall) {
      itemsArea.add(getWallArea((Wall) item));
    } else {
      itemsArea.add(new Area(ShapeTools.getShape(item.getPoints(), true, null)));
    }
  }
  return itemsArea;
}
```

Because:

- `getItemsArea` is called by `getWallAreas` at lines 3963 and 3980 for wall
  collections — those callers automatically pick up the memoization.
- It's also called by `paintOtherLevels` at line 2730
  (`getItemsArea(otherLevelswalls)`) — pick up memoization for free.
- And at line 2707 (`getItemsArea(otherLevelsRooms)`) and any other Room / non‑Wall
  call sites — fall back to the original inline path; behavior is unchanged.

The cast `(Wall) item` is safe because the `instanceof` check guarantees the
type.

### Step 4 — Invalidate the per‑wall cache entry on geometry-changing events

Modify the `wallChangeListener` anonymous listener
(`PlanComponent.java:857-885`). Currently the FIRST branch (geometry /
pattern / wall‑at‑start / wall‑at‑end properties) does:

```java
if (Wall.Property.X_START.name().equals(propertyName)
    || Wall.Property.X_END.name().equals(propertyName)
    || Wall.Property.Y_START.name().equals(propertyName)
    || Wall.Property.Y_END.name().equals(propertyName)
    || Wall.Property.WALL_AT_START.name().equals(propertyName)
    || Wall.Property.WALL_AT_END.name().equals(propertyName)
    || Wall.Property.THICKNESS.name().equals(propertyName)
    || Wall.Property.ARC_EXTENT.name().equals(propertyName)
    || Wall.Property.PATTERN.name().equals(propertyName)) {
  if (home.isAllLevelsSelection()) {
    otherLevelsWallAreaCache = null;
    otherLevelsWallsCache = null;
  }
  wallAreasCache = null;
  doorOrWindowWallThicknessAreasCache = null;
  revalidate();           // NOTE: when Plan #1 is also applied, this line
                         // will already be `revalidateOrDefer(controller)`.
                         // Do NOT touch this line in this plan — Plan #1
                         // owns the revalidate deferral.
}
```

Insert a single line — `wallAreaCache.remove(ev.getSource());` — placed FIRST
in the branch body (before the `if (home.isAllLevelsSelection())` block).
The final branch body is:

```java
if (Wall.Property.X_START.name().equals(propertyName)
    || Wall.Property.X_END.name().equals(propertyName)
    || Wall.Property.Y_START.name().equals(propertyName)
    || Wall.Property.Y_END.name().equals(propertyName)
    || Wall.Property.WALL_AT_START.name().equals(propertyName)
    || Wall.Property.WALL_AT_END.name().equals(propertyName)
    || Wall.Property.THICKNESS.name().equals(propertyName)
    || Wall.Property.ARC_EXTENT.name().equals(propertyName)
    || Wall.Property.PATTERN.name().equals(propertyName)) {
  if (propertyName != null
      && !Wall.Property.PATTERN.name().equals(propertyName)) {
    wallAreaCache.remove((Wall) ev.getSource());
  }
  if (home.isAllLevelsSelection()) {
    otherLevelsWallAreaCache = null;
    otherLevelsWallsCache = null;
  }
  wallAreasCache = null;
  doorOrWindowWallThicknessAreasCache = null;
  revalidate();           // left untouched here (Plan #1 may change it).
}
```

The `if (... != null && !PATTERN)` guard ensures:

- On PATTERN change the per-wall geometry is unchanged → keep the cached
  `Area`; only `wallAreasCache` (the per-pattern union) is nullified so the
  unions regroup on next paint.
- On LEVEL / HEIGHT / HEIGHT_AT_END (handled in the `else if` branch at lines
  876-883), the per-wall shape is also unchanged → leave the cached `Area` in
  place. The existing branch already nulls `wallAreasCache`, `otherLevelsWallAreaCache`,
  and `otherLevelsWallsCache`, which is sufficient — the union rebuild will
  reuse the still-valid per-wall `Area`s.

### Step 5 — Clean up per‑wall cache entries on wall add / delete

Modify the `home.addWallsListener` collection listener at
`PlanComponent.java:889-902`. The current body is:

```java
home.addWallsListener(new CollectionListener<Wall> () {
    public void collectionChanged(CollectionEvent<Wall> ev) {
      if (ev.getType() == CollectionEvent.Type.ADD) {
        ev.getItem().addPropertyChangeListener(wallChangeListener);
      } else if (ev.getType() == CollectionEvent.Type.DELETE) {
        ev.getItem().removePropertyChangeListener(wallChangeListener);
      }
      otherLevelsWallAreaCache = null;
      otherLevelsWallsCache = null;
      wallAreasCache = null;
      doorOrWindowWallThicknessAreasCache = null;
      revalidate();
    }
  });
```

Replace with explicit cache eviction on DELETE (avoids leaks and prevents
stale Areas surviving a delete+re-add of the same `Wall` instance):

```java
home.addWallsListener(new CollectionListener<Wall> () {
    public void collectionChanged(CollectionEvent<Wall> ev) {
      if (ev.getType() == CollectionEvent.Type.ADD) {
        ev.getItem().addPropertyChangeListener(wallChangeListener);
      } else if (ev.getType() == CollectionEvent.Type.DELETE) {
        ev.getItem().removePropertyChangeListener(wallChangeListener);
        wallAreaCache.remove(ev.getItem());
      }
      otherLevelsWallAreaCache = null;
      otherLevelsWallsCache = null;
      wallAreasCache = null;
      doorOrWindowWallThicknessAreasCache = null;
      revalidate();           // left untouched here (Plan #1 may change it).
    }
  });
```

(On ADD, the cache miss path in `getWallArea(Wall)` populates the entry on first
read, so no add-time insertion is required — and inserting eagerly would race
with the geometry the wall was actually added with.)

### Step 6 — Do NOT clear `wallAreaCache` in `clearLevelCache()`

The existing `clearLevelCache()` at `PlanComponent.java:1185-1195` is:

```java
private void clearLevelCache() {
  this.backgroundImageCache = null;
  this.otherLevelsWallAreaCache = null;
  this.otherLevelsWallsCache = null;
  this.otherLevelsRoomAreaCache = null;
  this.otherLevelsRoomsCache = null;
  this.wallAreasCache = null;
  this.doorOrWindowWallThicknessAreasCache = null;
  this.sortedLevelRooms = null;
  this.sortedLevelFurniture = null;
}
```

A wall's plan‑view shape does NOT depend on which `Level` is selected — only
on `X_START`, `Y_START`, `X_END`, `Y_END`, `THICKNESS`, `ARC_EXTENT`, and the
two adjacent walls. Therefore the per‑wall `Area` cache MUST persist across
level switches.

Do NOT add `this.wallAreaCache = null;` or `this.wallAreaCache.clear();` to
`clearLevelCache()`. Leave it as‑is.

### Step 7 — Do NOT clear `wallAreaCache` on `WALL_PATTERN` preference change

The existing `preferencesListener` `case WALL_PATTERN` branch
(`PlanComponent.java:1160-1162`) is:

```java
case WALL_PATTERN :
  planComponent.wallAreasCache = null;
  break;
```

A pattern change does not affect geometry; the per‑wall `Area`s remain valid.
Only the per‑pattern grouping of the union changes (and the pattern image
cache is handled separately). Leave this branch as‑is.

### Summary of edits

| Line range | Listener / method | Edit |
| --- | --- | --- |
| ~323 | field declarations | Add `private Map<Wall, Area> wallAreaCache = new IdentityHashMap<>();` |
| ~3924 | new method | Add `private Area getWallArea(Wall wall)` helper |
| 3989‑3995 | `getItemsArea` body | Branch on `item instanceof Wall` to use `getWallArea(...)` |
| 868‑875 (`wallChangeListener`, first branch) | first branch body | Insert `if (!PATTERN.equals(propertyName)) wallAreaCache.remove((Wall) ev.getSource());` at the top of the branch. Do NOT touch `revalidate()`. |
| 889‑902 (`home.addWallsListener`) | add a `wallAreaCache.remove(ev.getItem())` line in the `Type.DELETE` branch. Do NOT touch `revalidate()`. |
| 1185‑1195 | `clearLevelCache()` | leave unchanged; do NOT add wallAreaCache eviction. |
| 1160‑1162 | `case WALL_PATTERN` preferences listener | leave unchanged. |

No edits anywhere else. In particular:

- The `public void revalidate()` override (lines 1200‑1214) is untouched.
- All `revalidate()` calls remain in place — Plan #1 is the one that converts
  those to `revalidateOrDefer(controller)`. The two plans are independent and
  can be applied in either order. If Plan #1 lands first, the new lines
  `wallAreaCache.remove(...)` MUST be inserted before the
  `revalidateOrDefer(controller)` call in the affected branches (they run
  immediately on the EDT; the cache eviction must happen before any coalesced
  paint runs that uses the cache).
- The two `paintWallsOutline` paint pass uses `getWallAreas(...)` which
  ultimately calls `getItemsArea(...)` — through the step 3 change, they pick
  up memoization with no further edits.

## Risks and mitigations

- **Identity-vs-equality semantics:** `Wall` does not override
  `equals`/`hashCode`, so a regular `HashMap` would also behave by identity.
  `IdentityHashMap` makes this explicit and is faster (no
  `System.identityHashCode` boxing concerns — `IdentityHashMap` uses
  `==`/`System.identityHashCode` directly). Also `IdentityHashMap` is already
  imported (`PlanComponent.java:99`).
- **Memory leak after wall delete:** Step 5 explicitly calls
  `wallAreaCache.remove(ev.getItem())` on the `DELETE` branch. Without this
  step, deleted walls would be retained by the cache. Note: the wall is also
  referenced briefly by other listeners (e.g. undo/redo controllers) before
  being garbage‑collected, so a `WeakHashMap` would also work; an explicit
  eviction is preferred over `WeakHashMap` because eviction is deterministic
  and a wall's `Area` shape is cheap to drop the instant the wall is removed
  from the home (a user action).
- **Correctness for `WALL_AT_START`/`WALL_AT_END`:** These properties changing
  is a geometry change because `Wall.getPoints()` depends on the joined wall
  at start/end (try `$ grep -n "getPoints" src/com/eteks/sweethome3d/model/Wall.java`
  if unsure — the method recomputes the cached points array any time a join
  partner changes). So invalidating `wallAreaCache.remove(wall)` on
  `WALL_AT_START`/`WALL_AT_END` is required and is covered by Step 4 (those
  property names ARE in the listed branch condition).
- **Stale memoization when geometry changes via the model without firing a
  property change:** This cannot happen — `Wall` exposes no public way to
  mutate geometry without firing `firePropertyChange`; verify with
  `$ grep -n "firePropertyChange" src/com/eteks/sweethome3d/model/Wall.java`
  (all setters go through `firePropertyChange`).
- **Concurrent modification of `wallAreaCache`:** `wallAreaCache` is read and
  written only on the EDT (paint + listeners are all EDT). Do not change this
  assumption; do not add synchronization or `ConcurrentHashMap`.

## Verification

1. **Compile:**
   ```
   ant compile
   ```

2. **Headless tests:**
   ```
   ant test
   ```
   All ten test classes listed in `build.xml:281-290` must pass (these include
   `HomeTest`, `HomeFileRecorderTest`, `WallPanelTest` — all touch wall model
   mutation paths and serialization).

3. **Full GUI/Xvfb tests:**
   ```
   xvfb-run -a -s "-screen 0 1920x1080x24 -ac +extension GLX +render -noreset" ant clean test-all
   ```
   Or use the project Taskfile entry:
   ```
   task test
   ```
   `PlanComponentTest` exercises wall creation, selection, modification, drag
   feedback, ruler reading, and paint output. The byte‑identical paint output
   invariant is captured by the existing screenshot‑based assertions in that
   test; they MUST pass without modification.

4. **Manual smoke test:** Build the standalone JAR (`ant jarExecutable`) and
   run. Create a home with 100+ walls, change one wall's thickness via the
   properties panel repeatedly, drag endpoints; verify visual output is the
   same as before (no missing walls, no broken outlines, same hatching /
   per‑pattern coloring). Run with thousands of walls to confirm the
   amortized speedup.

5. **Quick bench check (optional, manual):** Use the application's profile / a
   swing‑based bench via the existing `test/com/eteks/sweethome3d/junit/`
   scaffolding if available, or simply compare the wall‑drag frame rate with
   ~500 walls before and after — without Plan #1 alone this is most visible
   when walls also have changing geometry.

## Acceptance checklist

- [ ] `private Map<Wall, Area> wallAreaCache = new IdentityHashMap<>();`
      field added next to `wallAreasCache` (around line 323).
- [ ] `private Area getWallArea(Wall wall)` helper added above
      `getWallAreasAtLevel(Level)` (line ~3924).
- [ ] `getItemsArea` body branches on `item instanceof Wall` and uses
      `getWallArea(...)`.
- [ ] `wallChangeListener` first branch evicts `wallAreaCache.remove(wall)`
      for non‑PATTERN properties; the `else if` LEVEL/HEIGHT branch is left
      unchanged.
- [ ] `home.addWallsListener` DELETE branch evicts
      `wallAreaCache.remove(ev.getItem())`.
- [ ] `clearLevelCache()` is NOT extended to clear `wallAreaCache`.
- [ ] `case WALL_PATTERN` preferences branch is NOT extended to clear
      `wallAreaCache`.
- [ ] No `revalidate()` call sites are touched by this plan (Plan #1 owns
      those).
- [ ] `ant compile` succeeds.
- [ ] `ant test` (headless) passes.
- [ ] `task test` (Xvfb test‑all) passes — particularly `PlanComponentTest`.