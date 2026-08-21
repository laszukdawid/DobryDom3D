# Testing

## Inventory

- **43 test classes** (+ `TestUtilities.java` helper), JUnit 4.13.2 runner,
  JUnit 3-style `TestCase` API (31 classes extend `TestCase`, 12 extend
  Abbot's `ComponentTestFixture`). **Zero `@Test` annotations** — migration to
  annotations would be mechanical but touches all 43 files.
- ~134 test methods, ~13.5k LOC in `test/com/eteks/sweethome3d/junit/`
  against ~163k LOC / 244 files of source (**~8% test:source ratio by LOC**).
- Abbot drives GUI tests (~2008-era); JDepend powers the architectural gate.
- Test fixtures include `.sh3d` homes (incl. damaged-home variants) and XML.
  The damaged-home fixtures are currently **unused** for their intended purpose
  (see top risks).

## How tests run

| Target | What runs | Key settings | Where |
|---|---|---|---|
| `ant test` (build.xml:325) | Headless allow-list of **20 explicitly listed classes** (build.xml:349–372) | `fork=true forkmode=perTest`, haltonerror/failure, per-test timeout 120s, `-Djava.awt.headless=true -Dcom.eteks.sweethome3d.no3D=true`, sandboxed `user.home`/`java.util.prefs.userRoot`/`java.io.tmpdir` under `build/test-*`, targeted `--add-opens` (java.awt, sun.awt, com.apple.eio) | CI gate |
| `ant test-all` (build.xml:377) | All 43 classes (`**/*Test.java`) | GUI enabled, native lib path, JOGL disk cache off, more add-opens + `--enable-native-access`, continues-on-failure then aggregates via `all.tests.failed`, timeout 60s per JVM | local via `task test` (Xvfb) |
| `ant test-yafaray` (build.xml:415) | Native renderer lifecycle only | `--finalization=disabled`, requires YafaRay natives | part of `ci`/`ci-full` targets |
| `ant ci` | clean → `test` → `test-yafaray` → application build | headless subset only — local quick gate | GitHub Actions (historically) |
| `ant ci-full` | clean → `test-all` → `test-yafaray` → application build | the full suite; **what CI gates on** | GitHub Actions |

JUnit + Hamcrest are fetched by `fetch-junit` (build.xml:222) from Maven
Central with pinned SHA-256 checksums into `~/.cache/sweethome3d`.

Security hygiene in the harness (unusual and worth keeping):

- `_checkTestCredentialIsolation` (build.xml:281): plants a sentinel env var
  and fails if any JUnit output leaks it.
- `_checkTestReportProperties` (build.xml:309): refuses to publish JUnit XML
  containing `env.*` properties; mirrored in CI before artifact upload.

**Resolved:** CI previously gated on the headless allow-list only, leaving
~23 GUI-heavy classes able to regress while CI stayed green. CI now runs
`ant ci-full` (the full suite) under `xvfb-run`; `ant ci` remains the fast
local/headless gate.

## Coverage by package

Classes referenced by *any* test file — an upper bound on coverage:

| Package | Files | Tested | Notes |
|---|---|---|---|
| model | 65 | 40 (~62%) | Best covered: geometry, selection, caches |
| viewcontroller | 49 | 37 (~75%) | Controllers partially; many are interfaces |
| swing | 64 | 30 (~47%) | PlanComponent heavily tested; widgets not |
| j3d | 21 | 8 (~38%) | Loaders via ModelManagerTest (GUI-only) |
| io | 21 | **4 (~19%)** | Biggest dark area — see below |
| plugin | 4 | **0%** | Untested |
| tools | 7 | 3 | |

### Class-level dark spots that matter most

- `io/`: **`HomeXMLHandler` (2,038 lines), `HomeXMLExporter`,
  `AutoRecoveryManager`, `ContentDigestManager`, `DefaultHomeInput/OutputStream`,
  `XMLWriter`, `ObjectXMLExporter`, `Base64`, `DefaultTexturesCatalog`** — i.e.
  the home file format and crash recovery are untested despite being the
  highest-consequence code in the repo.
- `swing/`: all 6 `*TransferHandler`s, `AutoComplete*`, `NullableSpinner`,
  `CalculatorFormat`, `ProportionalLayout`, `HelpPane`, most panels,
  `HomePDFPrinter`, `JPEGImagesToVideo`.
- `j3d/`: `Room3D`, `TextureManager`, `Component3DManager`, `DAELoader`, all
  `*3D` scene-graph classes, `ShapeTools`.
- `plugin/`: everything.

Coverage measurement tooling does not exist yet (no JaCoCo/Cobertura/Clover in
`build.xml`) — these tables are manual mappings. Adding JaCoCo is a tracked
priority so the dark areas become quantified automatically.

## Test quality

High where it exists:

- `WallShapeTest`, `PolylineShapeTest` — cache-coherence regression tests
  (baseboard vs plain shape independence of call order, stale caches on joined
  walls, defensive copies).
- `HomeSelectionTest` — invariant checking after every mutation incl.
  serialize/deserialize round-trips; asserts handed-out list immutability.
- `PlanComponentRepaintBoundsTest` — differential rendering: renders fully to
  an offscreen image and verifies the requested dirty region covers the diff.
- `IconManagerTest` uses `CyclicBarrier`s for deterministic concurrency.
- Assertion style is domain-level with custom helpers
  (`assertCoordinatesEqualWallPoints` at 1E-4 tolerance,
  `assertWallsAreJoined`, `assertSelectionContains`); exception paths checked
  via try/fail/catch (`ArchitectureTest.java:105`).

Two GUI techniques coexist:

1. Real event synthesis through Abbot's Robot (`EM_AWT` mode):
   `PlanComponentTest.testPlanComponentWithMouse` (:67). Platform-specific
   magnetism chords centralized in
   `TestUtilities.pressMagnetismToggleKey` (:81).
2. Direct controller invocation on the EDT:
   `PlanControllerTest.testPlanContoller` wraps everything in
   `EventQueue.invokeAndWait` and calls `planController.pressMouse(...)`
   directly (:60–109) — preferred where possible; avoids OS event plumbing.

Undo/redo is covered only incidentally through integration paths
(`PlanControllerTest`, `RoomTest`); `LocalizedUndoableEdit` itself has no
direct tests.

### Flakiness vectors (known)

- Raw sleeps: `Thread.sleep(500)` ×4 in `PlanComponentTest.java:325,363,365`
  (double-click-to-repeat wall creation), `Thread.sleep(1000)` in
  `HomeCameraTest.java:194`, `Thread.sleep(100)` in
  `BackgroundImageWizardTest.java:138`. Main flake candidates under Xvfb load.
  Good counter-example: `HomeControllerTest.java:543` uses
  `CountDownLatch.await(5, TimeUnit.SECONDS)`.
- Pixel-coordinate-dependent mouse clicks break under DPI/scaling changes.
- `Locale.setDefault(Locale.FRANCE)` inside test constructors
  (e.g. `PlanComponentTest.java:502`) leaks across JVMs — mitigated only by
  `forkmode="perTest"`. Prefer injecting locale or restoring in teardown.

## Architectural gate

`PackageDependenciesTest.java:38–255` runs **JDepend** over the built jar
(passed via the `com.eteks.sweethome3d.applicationJar` system property;
`classes/` dir as IDE fallback) and asserts one declared dependency graph.
Enforced rules:

- `model` depends on nothing (pure domain).
- `tools` → model only.
- `viewcontroller` → {model, tools} plus limited Swing sub-packages
  (event/undo/text — no Swing components).
- `io` → {model, tools} + XML parsing only.
- `plugin` → {model, tools, viewcontroller}.
- `j3d` → model/tools/viewcontroller + Java3D/Sunflow/Batik (all 3D/native
  deps quarantined here).
- `swing` on top of everything (incl. iText/FreeHEP/JMF).
- Application/applet packages are pure composition roots.

Value: a genuinely useful, near-free hexagonal-architecture guard that keeps
the layering honest. Weaknesses: a single monolithic assertion
("Dependency mismatch") gives no diagnostics about which edge broke — iterate
JDepend's `analyze()` results and report offending edges instead; the
`viewcontroller → swing.text/html` allowance is odd and worth revisiting; no
cycle reporting beyond the constraint. A companion `jdepend` Ant target
launches a GUI for manual graph updates.

Do not confuse this with `ArchitectureTest` (6 plain unit tests for CPU-arch
string normalization in `tools.Architecture`).

## Tooling gaps

- **Coverage measurement** — `ant coverage` runs the headless suite under
  JaCoCo (pinned download, SHA-256 verified) and writes XML + HTML reports to
  `build/coverage/`; CI uploads them as artifacts. JaCoCo does not gate the
  build yet; introduce thresholds once the baseline is known.
- **Static analysis** — `ant spotbugs` analyzes `build/DobryDom3D.jar`
  (SpotBugs 4.10.4, pinned downloads) and writes `build/spotbugs/spotbugs.xml`;
  CI uploads it as an artifact. Findings are non-gating until the inherited
  legacy findings are triaged and baselined.
- **No mutation testing** (e.g. PIT) — assertion strength unknown; several long
  scenario-script tests would likely reveal weak assertions.
- Framework debt: JUnit 3 API on the 4.x runner, no parameterized tests, no
  Hamcrest matchers despite shipping it, some float comparisons via
  `assertTrue` instead of `assertEquals(expected, actual, delta)`.

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
  if reflection is needed, route it through `TestUtilities.getField/setField`.
- Controller-level tests should wire a real `UndoableEditSupport` +
  `UndoManager` and assert exact restored state (see `PlanControllerTest`).
- Rendering-behaviour tests: prefer the differential dirty-region technique or
  offscreen `BufferedImage` rendering over pixel snapshots.
- Prefer latches over sleeps for async completion; never mutate global state
  (locale, default toolkit settings) without restoration.
