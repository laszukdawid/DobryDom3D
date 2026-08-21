# Testing

## Inventory

- **43 test classes** (JUnit 4, JUnit 3-style `TestCase` API), ~134 test
  methods, ~13.4k LOC in `test/com/eteks/sweethome3d/junit/`.
- Abbot drives GUI tests; JDepend powers `ArchitectureTest`-style checks.
- Test fixtures include `.sh3d` homes (incl. damaged-home variants) and XML.

## How tests run

| Target | What runs | Where |
|---|---|---|
| `ant test` | Headless allow-list of **21 classes** (`headless=true`, `no3D=true`, isolated prefs/tmp dirs, haltonerror) | CI gate |
| `ant test-all` | All 43 classes, GUI enabled, continues-on-failure then fails | local via `task test` (Xvfb) |
| `ant test-yafaray` | Native renderer lifecycle | CI (`ci` target) |

**Known gap:** CI already executes under `xvfb-run`
(`java-ci.yml` step "Test and build") but still runs only the headless
allow-list — so ~22 GUI-heavy classes (`PlanControllerTest`,
`PlanComponentTest`, `ModelManagerTest`, panel/wizard tests, ...) can regress
while CI stays green. Fix: run `test-all` in CI.

## Coverage by package (classes referenced by any test)

| Package | Tested | Notes |
|---|---|---|
| model | 40/65 (~62%) | Best covered: geometry, selection, caches |
| viewcontroller | 36/49 (~73%) | Controllers partially; many are interfaces |
| swing | 30/64 (~47%) | PlanComponent heavily tested; widgets not |
| j3d | 8/21 (~38%) | Loaders via ModelManagerTest (GUI-only) |
| io | **4/21 (~19%)** | Biggest dark area |
| plugin | **0/4** | Untested |
| tools | 3/7 | |

## Test quality

High where it exists:

- `WallShapeTest`, `PolylineShapeTest` — cache-coherence regression tests
  (baseboard vs plain shape independence of call order, stale caches on joined
  walls, defensive copies).
- `HomeSelectionTest` — invariant checking after every mutation incl.
  serialize/deserialize round-trips; asserts handed-out list immutability.
- `PlanComponentRepaintBoundsTest` — differential rendering: renders fully to
  an offscreen image and verifies the requested dirty region covers the diff.
- Reflection is limited and centralized in `TestUtilities.getField/setField`.

Undo/redo is covered only incidentally through integration paths
(`PlanControllerTest`, `RoomTest`); `LocalizedUndoableEdit` itself has no
direct tests.

## Top untested risks (priority order)

1. `io/HomeXMLHandler` + `io/HomeXMLExporter` — no export→parse→compare
   round-trip test exists for the format new development targets.
2. `io/AutoRecoveryManager` — crash recovery; untested data-loss guard.
3. `io/ContentDigestManager` / `Base64` — integrity code with damaged-home
   fixtures already in test resources but unused for this purpose.
4. `plugin/*` — whole package dark.
5. Drag-and-drop `*TransferHandler` classes — all untested.
6. `j3d/TextureManager` — caching/loading logic untested.
7. `LocalizedUndoableEdit` and per-controller undo coverage.

## Conventions for new tests

- Prefer public-API behavioral assertions over reflection into private state;
  if reflection is needed, route it through `TestUtilities`.
- Controller-level tests should wire a real `UndoableEditSupport` +
  `UndoManager` and assert exact restored state (see `PlanControllerTest`).
- Rendering-behaviour tests: prefer the differential dirty-region technique or
  offscreen `BufferedImage` rendering over pixel snapshots.
