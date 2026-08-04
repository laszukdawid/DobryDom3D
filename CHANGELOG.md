# Changelog

This changelog records changes made in this fork after the upstream Sweet Home
3D 7.5 snapshot. The fork baseline is commit `8823bdd`.


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
