# Architecture

## Layering

The code base is a classic layered MVC. The seams below are the most valuable
property of this code base — they are what makes headless testing possible at
all. **Do not erode them** (e.g. by letting controllers call Swing components
directly "just this once").

```
model/            pure domain objects, zero UI imports
  ↑               (observable via PropertyChangeSupport / CollectionChangeSupport)
viewcontroller/   controllers + view INTERFACES (mostly no implementations)
  ↑
swing/, j3d/      view implementations (Swing plan/panels, Java 3D scene)
io/               persistence: legacy binary .sh3d + XML
plugin/           plugin API (small, currently untested)
tools/            misc utilities (URL content, OS detection, class loading)
```

### Verified invariants (measured 2026-08)

Import counts per package (`javax.swing` / imports of `.j3d`):

| Package | Files | javax.swing | imports `.j3d` | Verdict |
|---|---|---|---|---|
| `model` | 95 | **0** | **0** | ✅ Pure domain |
| `viewcontroller` | 49 | 69 (18 files) | 0 | ⚠️ Swing-tainted |
| `swing` | 64 | 909 | 33 | By design |
| `j3d` | 25 | 4 | – | ⚠️ Small leak |
| `io` | 21 | 0 | 0 | ✅ |
| `plugin` | 5 | 2 | 0 | ✅ |

Keep-true rules:

- `model/` contains **no** `javax.swing`, `javax.media.j3d`, or
  `com.eteks.sweethome3d.j3d` references. It *does* use `java.awt.geom`
  (`Shape`, `Point2D`) — headless-safe AWT geometry, acceptable.
- No class in `viewcontroller/` references the `swing/` or `j3d/`
  implementation **packages**; views are injected interfaces.

Known, tolerated violations (do not add to them):

- `viewcontroller/ → javax.swing.undo` in 18 files (~69 imports):
  `HomeController.java:48–55`, `PlanController.java:43–47`,
  `FurnitureController.java:35–37`. Undo is arguably a controller concern;
  tolerated.
- Controllers assume an EDT-based toolkit: they call
  `getView().invokeLater(...)` (`HomeController.java:382,493,1338`) even though
  the `View` interface does not declare it — controllers are written *for*
  Swing despite defining view interfaces.
- `j3d/Component3DManager.java` imports `SwingUtilities`/`Timer`; also
  `Label3D`, `DimensionLine3D`. Inversion violation inside the 3D layer.
- `swing/ → j3d` (33 imports) is by design, but means `swing/` is really
  "Swing + Java 3D presentation", not a pure adapter layer.

This seam structure is additionally enforced at test time by
`PackageDependenciesTest` (JDepend over the built jar) — see
[Testing](Testing.md#architectural-gate).

## Coupling & communication

Two coexisting notification protocols; every view must implement both:

1. **Property changes** — `model/Home.java:117` holds a transient
   `PropertyChangeSupport`; setters fire typed events using a `Property` enum
   (`Home.java:75`, registration at `Home.java:1418`).
2. **Collection changes** — custom `CollectionEvent` / `CollectionListener<T>`
   with `CollectionChangeSupport` (`Home.addFurnitureListener` at
   `Home.java:781`, levels listeners at `Home.java:610–620`). `Home` also wires
   internal level listeners itself (`Home.java:468`).

Listener hygiene is good: listeners are copied before iteration everywhere
(`Home.java:945`), making notification reentrancy-safe.

Global state (5 singletons): `Component3DManager`, `ModelManager`,
`TextureManager`, `IconManager` (all `j3d/`), `io/ContentDigestManager`.
`tools/OperatingSystem` is heavily static (~28 static members). `UserPreferences`
acts as a de-facto application-wide service passed by reference through all
controllers. Several managers are accessed from multiple threads; correctness
relies on Java 3D internals (see [Threading](#threading-model)).

## Key files (god-class concentration)

The dominant structural debt is three god classes holding nearly all
interaction/rendering logic. Patterns used are right (State + Command +
Strategy); their placement is wrong — all fused into single files.

| File | LOC | Inner classes | Responsibilities |
|---|---|---|---|
| `viewcontroller/PlanController.java` | **15,944** | ~100 | Pointer-input FSM, wall/room/dimension/polyline/label/furniture editing, undo composition, magnetism/alignment, levels. 120 public methods. |
| `swing/PlanComponent.java` | 7,755 | 10+ | All plan rendering (furniture, walls, rooms, dims, polylines, texts, selection), hit testing, printing, SVG export, rulers. |
| `swing/HomePane.java` | 5,988 | 10 | 266 methods; main window, menus, actions — plus an entire OBJ exporter inside the view (`OBJExporter`:5475). Misplaced responsibility. |
| `viewcontroller/HomeController.java` | 3,794 | 13 | App-level controller including a SAX handler (`UpdatesHandler`:3510) parsing home-update XML inside a controller. |

### PlanController anatomy (evidence for decomposition planning)

- Abstract FSM machinery: `ControllerState`:9284,
  `ControllerStateDecorator`:9346.
- ~40 interaction states as nested classes: `SelectionState`:9495,
  `WallDrawingState`:10601, `RoomDrawingState`:14025,
  `PolylineDrawingState`:15055, seven `DimensionLine*` states,
  camera states :12477–12648, `CompassResizeState`:15860.
- 55 `*UndoableEdit` nested classes (lines 949–8465) plus
  `LocalizedUndoableEdit`.
- 5 magnetism helpers (`PointWithAngleMagnetism`:8906 …).

Consequences:

- Highest-risk file for regressions; hardest to test in isolation.
- If splitting it up, the natural first cut is mechanical and zero-behavior:
  lift each state class and undoable edit into its own top-level file
  (compiler-verified). A later cut could introduce a real state-machine
  abstraction; do not by-layer.

### PlanComponent

Rendering pipeline: `paintComponent` → `paintContent` → per-item-type painters
(rooms → walls → furniture → polylines → dimension lines → texts → selection
outlines). Recent fork work added clip-based culling, dirty-region repaints and
several paint caches (see [Code Quality](Code-Quality.md)). Nested classes
include `SVGSupport`:2736, `PlanRulerComponent`:6990, furniture plan-icon
renderers :7263–7412.

## Persistence

`.sh3d` files are ZIPs containing either a Java-serialized `Home` (entry
`Home`) or an XML entry `Home.xml`.

- Reader gives **priority to `Home.xml` when present**
  (`DefaultHomeInputStream.java:286`), falling back to deserialization (:312).
- Default write path is still **Java serialization**:
  `HomeFileRecorder.writeHome()` → `DefaultHomeOutputStream`
  (`io/HomeFileRecorder.java:183–198`). XML is emitted additionally only when
  `preferXmlEntry=true`. The app enables it
  (`SweetHome3D.java:205,215`), so current files contain both entries; legacy
  files remain serialized-only.
- Atomic save via temp file + rename (`HomeFileRecorder.java:186–211`);
  auto-recovery via `AutoRecoveryManager` (timer + dedicated executor,
  `AutoRecoveryManager.java:37–87`).

Risks (tracked in [Engineering Priorities](Engineering-Priorities.md)):

- Classic Java-deserialization attack surface on untrusted `.sh3d` files — no
  class filtering on `ObjectInputStream`.
- Fragile schema evolution: `Home.java:43–48` documents that field/enum changes
  must keep serialization compatibility; unknown enum constants are silently
  ignored (`Home.java:210,223`) — silent data loss on downgrade.
- 14 Serializable classes across `model/`+`io/`, 33 `serialVersionUID`s — every
  model refactor is potentially format-breaking.
- Dual-format maintenance burden: `HomeXMLHandler` (2,038 lines) and
  `HomeXMLExporter` (928 lines) must mirror every model change forever.
- Strategic direction: make XML the default write format and keep binary
  reading only for legacy import.

## Threading model

EDT-centric throughout; cross-thread hops via `getView().invokeLater(...)`
(`HomeController.java:382,1338,1381,1424`) and raw `EventQueue.invokeLater`
(`VideoPanel.java:648,1495,…`).

Background work uses ad-hoc single-thread executors created per operation, no
shared pool policy:

- `PhotoPanel`: `Executors.newSingleThreadExecutor()` per photo
  (:806–808); cooperative cancellation by polling `isShutdown()` (:826,845),
  hard stop via `shutdownNow()` (:933).
- `VideoPanel`: same pattern (:1471,:1637).
- `YafarayRenderer`: JNI renderer driven from background threads; interruption
  by flag polling `checkCurrentThreadIsntInterrupted()` (:581–586); class-level
  lock `synchronized (YafarayRenderer.class)` (:321).
- `AutoRecoveryManager`: `java.util.Timer` + dedicated executor (:75–87).

Hazards to watch:

- Cancellation is poll-based, not future-based; races are guarded only by
  re-checking fields ("Check a second time in case rendering stopped meanwhile",
  `PhotoPanel.java:930`).
- Photo/video threads read the live mutable `Home` graph while the user edits
  on the EDT; safety relies on convention, there are no snapshots.
- Non-thread-safe singleton managers (`ModelManager`, `TextureManager`,
  `IconManager`, `ContentDigestManager`) accessed from multiple threads.
  `ModelManager` splits cache vs clone locks, but Java 3D cloning itself is
  documented non-thread-safe (see [Code Quality](Code-Quality.md)).
- **`VideoPanel` reflects into removed internal API `sun.awt.AppContext`**
  (:1802–1814) — guaranteed breakage path on modern JDKs; retire before any
  runtime-JDK bump.

## Extensibility (plugin API)

Small API in `plugin/`: `Plugin`, `PluginAction`, `HomePluginController`,
`PluginManager`. Loading: JARs scanned in the application plugins folder;
per-jar child-first `URLClassLoader` (`PluginManager.java:155`); descriptor
discovered via `ResourceBundle.getBundle("…PluginApplicationPlugin", locale,
classLoader)`; entry class validated to extend `Plugin` with a public no-arg
constructor (:268–275); wired lazily in `SweetHome3D.getPluginManager()`
(:310–323). Plugins can only add menu actions bound to handed-over
controllers/views — no extension points for persistence formats, model
entities, or renderers; no versioning/signing/isolation (shared parent
classloader namespace).

## Modernization debt (inherited)

Upstream is unmaintained; there is no merge-debt argument against modernizing:

- Legacy collections: `Enumeration<>` ×48, `Vector` ×2
  (`FileContentManager.java:1250`, `UserPreferencesPanel.java:274`),
  `Hashtable` ×3 (`ModelMaterialsComponent`, `VideoPanel`, `PlanController`).
- Reflection-as-compat-fossil ×21 files, e.g. `HomeFileRecorder.java:214–215`
  (`File.getUsableSpace` via reflection, a Java 1.6-era workaround that is no
  longer needed on JDK 21).
- Java 3D 1.6 preview line (upstream frozen since ~2015) — whole `j3d/`
  package, offscreen-render workarounds, GC-forcing escape hatch
  (`Component3DManager.java:62–65,381`), string-keyed shape prefixes as pseudo
  API (`ModelManager.java:131–157`).
- Anonymous inner-class style throughout (pre-lambda); ~10 raw-type/unchecked
  suppression hotspots.
- Dead subsystems: `applet/` package (incl. `SweetHome3DApplet`, JNLP-era
  `ViewerHelper`), JMF license file, Web Start build targets — all post-Web
  Start removal dead weight.
- i18n via ~20 `package_*.properties` bundles tightly coupled to class names —
  fine, but renaming classes silently breaks translations.
