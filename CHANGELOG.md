# Changelog

This changelog records changes made in DobryDom3D after the upstream Sweet Home
3D 7.5 snapshot. The fork baseline is commit `8823bdd`.


## [Unreleased]

### Added

- Added a standalone reproducer and an upstream JDK bug-report draft for the
  JDK 22+ multi-monitor menu-popup positioning regression.

### Changed

- Started independent product versioning at **DobryDom3D 0.1.0** while keeping
  the upstream source, catalog, plugin, and `.sh3d` format compatibility
  versions unchanged.
- Adopted **DobryDom3D** as the project name and documented the planned branding
  migration. The original Sweet Home 3D attribution and licensing remain
  unchanged.
- Identified this GitHub repository as the canonical project home until a
  GitHub Pages website is published.


## [2026-08-18]

### Changed

- Batched plan-grid lines into paths and limited grid painting to the clipped
  region, avoiding work across the rest of a large plan.
- Skipped room, furniture and label text, dimension lines, and polylines that
  fall completely outside the repaint clip.
- Cached the computed points of furniture, dimension lines, and observer
  cameras, invalidating each cache when the corresponding geometry changes.
- Added bounding-box checks before expensive area intersections when cutting
  doors and windows through walls and when magnetizing dragged furniture.
- Opened `com.apple.eio` to test JVMs so macOS tests receive the same module
  access as the executable application.

### Fixed

- Invalidated a polyline's cached shapes when its thickness or closed state
  changes.
- Invalidated cached furniture sides after rotation so magnetism follows the
  rotated contour.
- Invalidated door and window cut-out caches when either the opening or a wall
  changes level, preventing stale openings from being painted on another
  level.
- Preserved grid precision far from the plan origin and accounted for open
  arrow miters when deciding whether a polyline is outside the clip.


## [2026-08-14]

### Changed

- Gathered the doors and windows cutting through a wall once per update of its
  3D geometry. Every wall side and every baseboard used to walk the whole home
  furniture, recursing into each group, to collect the same pieces again.
- Remembered the top view icon cache keys in a map keyed by the key itself, so
  a plan finds the shared instance directly instead of scanning the whole key
  set on each cache miss. The scan is kept as a fallback.

### Fixed

- Serialized the renders sharing the static scene, background and off-screen
  canvas the top view icons of furniture are drawn through. Icons are rendered
  both while a plan is painted on screen and, in the calling thread, while it
  is printed or exported, so two renders could interleave and an icon could
  capture an other piece's model. The model is now detached in a `finally`
  block, a model left in the scene being drawn into the icons rendered next.
- Made the top view icon cache key honour the equals contract. A key built from
  a model matched a key built from the plan icon of a piece with the same model
  while the reverse comparison didn't, and the data of a model was hashed for
  the pieces which have a plan icon but compared for those which don't, so two
  equal keys could get different hash codes and miss each other.
- Mapped each piece to the very key instance its icon is cached under. Replacing
  the icon of an existing entry doesn't replace its key, so a piece could hold
  an equal sibling instead, and both maps being weak, the icon could then be
  dropped while the piece was still painted.
- Kept a wall's shape caches in step with its points. A wall cached one shape
  for both its baseboard variants, so whichever was asked for first decided the
  answer for the other, and clearing the points cache left the walls joined to a
  moved wall holding a shape built from the old join. Both made `containsPoint`
  and `intersectsRectangle` answer from stale geometry.


## [2026-08-13]

### Changed

- Skipped painting the rooms and the furniture whose bounds, grown by what they
  draw around themselves, fall outside the clip. A scrolled repaint of a 1600
  item plan goes from 7.1 ms to 4.6 ms.
- Repainted only the region a change touches. Selection changes and the drags
  which redraw a plan on each mouse move used to ask for the whole component
  back; a drag step now repaints 1.6% of it instead of 100%.
- Held a home's selection in a set keyed by identity, so painting reads whether
  an item is selected instead of searching the selection list for each item it
  draws. A repaint of a 12000 item plan with everything selected goes from
  63 ms to 42 ms.


## [2026-08-12]

### Added

- Added a FlatLaf application theme.

### Changed

- Stabilized GUI event handling in the tests.
- Stopped environment credentials from reaching the CI test reports.
- Scanned the CI test reports with `grep` instead of ripgrep, which GitHub
  runners don't ship, so the report safety step exited 127 and failed every
  build.


## [2026-08-10]

### Changed

- Reused measured furniture-table column widths across pages of the same print
  job instead of rendering every cell again whenever the printing system asks
  for another page or probes the page count.
- Reused int index buffers in `OBJLoader` instead of boxing every vertex,
  texture coordinate and normal index into an `Integer` appended to a
  per-element `ArrayList`. Indices are almost always above the `Integer` cache
  range, so each add allocated. Adds `OBJLoaderTest`.
- Read the remainders of ignored 3DS chunks through an 8 KB buffer rather than
  one `read()` call per byte, cutting 64 MB of skipped data from ~282 ms to
  ~6 ms. `skip()` is not used because `FileInputStream.skip` may seek past the
  end of a file without reporting the truncation.
- Sized the heap of the packaged bundles from machine RAM with
  `-XX:MaxRAMPercentage=50` instead of a fixed `-Xmx2g`. 50 is the largest
  percentage whose heap and direct-buffer ceilings cannot together exceed
  physical memory, `MaxDirectMemorySize` defaulting to the max heap 1:1 while
  JOGL allocates its buffers as direct NIO buffers.


## [2026-08-09]

### Changed

- Deferred expensive plan revalidation until the end of a drag while continuing
  to repaint the plan and rulers during the drag.
- Cached each wall's plan-view area until its geometry changes, avoiding repeated
  shape-to-area conversion when building combined item areas.


## [2026-08-07]

### Fixed

- Repositioned tooltips displaced to a monitor's usable-area edge by phantom
  screen insets on multi-monitor X11 desktops, without overriding genuine dock
  or taskbar insets.


## [2026-08-06]

### Changed

- Scaled catalog icons with a bilinear `Graphics2D.drawImage` instead of
  `Image.getScaledInstance(SCALE_SMOOTH)`, halving each axis in turn while the
  source is still more than twice the requested size so large downscales stay
  smooth. `IconManagerTest` now compares the scaled pixels within the measured
  rounding tolerance.
- Scaled exported video frames the same way in `VideoPanel`, drawing the
  double-size off-screen frame through a bilinear `Graphics2D`.
- Reused the off-screen `Canvas3D` in `Component3DManager` across renders of
  the same size rather than creating and disposing one per image, copying the
  returned buffer so callers keep a stable image. Canvases larger than 16 M
  pixels, and any canvas whose render failed, are still released immediately.
- Dropped the unconditional `System.gc()` before every `Canvas3D` creation. It
  is now opt-in through the `com.eteks.sweethome3d.j3d.forceGarbageCollection`
  system property, as an escape hatch for drivers that need freed canvases.
- Split `ModelManager`'s single `loadedModelNodes` monitor into separate cache
  and clone locks, so cache lookups no longer wait behind node cloning.
- Disabled the ImageIO disk cache at startup in the application, applet, and
  viewer entry points. Images are read once from start to end, which makes the
  temporary cache files pure overhead.
- Lowered the build from Java 25 to Java 21 across `build.xml`, the Eclipse
  compiler settings, CI, and the packaging targets. No source change was needed;
  the tree compiles unchanged under `--release 21`.
- Pinned the local JDK and Ant versions in `.tool-versions` and resolved both
  through asdf in `Taskfile.yml`, so `task run` builds and launches without a
  system-wide Ant or JDK install.
- Rebuilt the vendored `libtest/jdepend-2.10.jar` from the same upstream JDepend
  2.10 source with `--release 21`. The previous build carried class file version
  69, which Java 21 `javac` cannot read, so test compilation failed outright.

### Fixed

- Guarded the transformed model bounds cache in `ModelManager`, which was read
  and written without any lock, and cloned the shared default material under
  the clone lock instead of racing across loader threads.
- Added the `com.apple.eio` and `com.apple.eawt` `--add-opens` flags, already
  documented in `README.TXT`, to the JUnit targets when running on macOS.
  `HomeFileRecorderTest` previously died with `IllegalAccessError` on macOS on
  any Java 16 or newer; CI runs Linux and never saw it.


## [2026-08-05]

### Added

- Added a Preferences option choosing whether the 3D view sits below the plan
  or next to it. The choice is persisted across sessions and the split pane is
  re-oriented live, keeping the current divider ratio.
- Added `com.eteks.sweethome3d.tools.Architecture`, a dependency-free
  normalizer mapping `os.arch` aliases to a family and bitness and holding the
  per-subsystem native library folder names (Java 3D `amd64` or `i586`,
  YafaRay `x64` or `i386`), with `ArchitectureTest` covering x86 and ARM
  aliases, unknown architectures, and the folder mapping.
- Added a `fetch-junit` Ant target that downloads pinned JUnit 4.13.2 and
  Hamcrest Core 1.3 into `~/.cache/sweethome3d` with SHA-256 verification,
  replacing the machine-specific JUnit discovery. CI no longer passes
  `-Djunit.jar`, the Ubuntu `/usr/share/java` default is gone from
  `Taskfile.yml`, and the Eclipse metadata is aligned to JUnit 4.

### Changed

- `SweetHome3DBootstrap`, `YafarayRenderer`, and `HomePane` now resolve native
  library folders through `Architecture` instead of the unsupported
  `sun.arch.data.model` property, and fail with a clear error on unsupported
  architectures rather than silently falling back to 32-bit libraries.
- `PackageDependenciesTest` analyzes `build/SweetHome3D.jar` directly, passed
  through the `com.eteks.sweethome3d.applicationJar` system property, instead
  of exploding it into the root `classes/` directory. `test-all` no longer
  deletes and rebuilds an Eclipse incremental build, and root `classes/`
  remains only as an IDE fallback.
- Replaced the vendored `jdepend-2.9.jar` with a build of upstream JDepend
  2.10. JDepend 2.9 cannot parse `MethodHandle` and `InvokeDynamic` constant
  pool entries, so it silently dropped Java 25 classes from the dependency
  graph and the package dependency constraint failed on a mismatch. The
  constraint was refreshed against the fully analyzed graph.


## [2026-08-04]

### Added

- Added ground-plane panning in Aerial View and Virtual Visit, including
  Shift-drag and keyboard navigation with Shift plus the arrow or W/A/S/D keys.
- Added mode-aware mouse-wheel zoom that preserves orbit zoom in Aerial View
  and follows the full viewing direction in Virtual Visit.
- Added `HomeController3DTest` coverage for sideways and forward/backward
  movement, preserving the panned orbit center, and Virtual Visit zoom.
- Added JDK 25 `jpackage` application-image and platform installer targets for
  Windows x64, Linux x64, and macOS x64 or arm64.
- Added package file associations, platform packaging metadata, and the Windows
  post-image customization script.
- Added Task targets for app images, Linux packages, headless tests, and tests
  running in an isolated Xvfb display.
- Added Linux package building and artifact upload to GitHub Actions CI.
- Added a mandatory YafaRay native lifecycle CI test that runs with JVM
  finalization disabled under an isolated virtual X server.

### Changed

- Replaced the legacy platform packaging flow with host-native JDK 25
  `jpackage` builds, including optional signed Windows and macOS installers.
- Isolated test homes, preferences, temporary files, native libraries, and XML
  reports for more reliable automated testing.
- Updated the application sources for JDK 25 warnings and APIs, including
  security exception handling and explicit deprecation annotations on legacy
  APIs.
- Made `task run` rebuild the executable only when its inputs have changed.
- Updated build, packaging, licensing, and user documentation for the new
  platform package workflow.
- Made YafaRay renderer cleanup deterministic across photo, batch-photo, video,
  cancellation, failure, and scene-rebuild paths using the existing
  cross-platform JNI ABI.
- Excluded generated package directories, Task state, and local `issues.md`
  files from repository and source archive output.

### Removed

- Removed legacy Linux launchers, portable archives, old macOS application
  bundles, and Windows Inno Setup and Launch4j installer configurations.
- Removed 32-bit Windows and Linux package definitions and the old cross-platform
  portable package workflow.

## [2026-08-03]

### Added

- Added GitHub Actions CI using Temurin JDK 25 to run tests, build the
  application JAR, and publish test reports and build artifacts.
- Added Ant `clean`, headless `test`, full `test-all`, and `ci` targets.
- Added initial Task commands for running the application and test suite.

### Changed

- Raised the build, compiler, Eclipse, and documented runtime requirement from
  legacy Java compatibility to JDK 25 while retaining application version 7.5.
- Updated Ant classpaths and test configuration for the Java 25 build.
