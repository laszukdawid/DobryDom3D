# Sweet Home 3D

This repository is an unofficial GitHub fork of the Sweet Home 3D 7.5 source
distribution.

The original project is hosted on [SourceForge](https://sourceforge.net/projects/sweethome3d/).
This repository does not claim original ownership or official project status.
It preserves the upstream source snapshot as a starting point for incremental
modernization.

## Upstream Snapshot

- Version: 7.5
- Upstream source reference: `https://svn.code.sf.net/p/sweethome3d/code/tags/V_7_5/SweetHome3D`
- Original copyright notices: Space Mushrooms, 2024
- License: GNU General Public License version 2 or later
- Third-party components: see the `THIRDPARTY-LICENSE-*.TXT` files

The original source documentation remains available in [`README.TXT`](README.TXT).

## Repository Contents

- `src/` - Sweet Home 3D application source code and resources
- `test/` - JUnit and GUI test sources and test resources
- `lib/` and `libtest/` - runtime and test dependencies
- `deploy/` - legacy deployment descriptors and optional server scripts
- `install/` - platform launcher and installer templates
- `include/` - native YafaRay integration headers

## Building

The project requires JDK 21 (LTS) and Apache Ant 1.9.8 or newer. The JDK is
pinned to 21; see [JDK version pin](#jdk-version-pin) for why. Build the
standalone application with:

```sh
ant clean application
```

Run the headless unit test suite. JUnit 4.13.2 and Hamcrest Core 1.3 are
downloaded automatically, verified by SHA-256 checksum, and cached:

```sh
ant clean test
```

The `ci` target cleans the workspace, runs the tests, and leaves the standalone
application at `build/SweetHome3D.jar`:

```sh
ant ci
```

The default Ant target builds an executable JAR under `install/`.

### FlatLaf

The standalone application includes FlatLaf and uses the FlatLaf Light theme by
default. Select another FlatLaf theme with the existing `swing.defaultlaf`
system property, for example:

```sh
JAVA_HOME=/home/dawid/.asdf/installs/java/temurin-21.0.6+7.0.LTS
"$JAVA_HOME/bin/java" \
  -Dswing.defaultlaf=com.formdev.flatlaf.FlatDarkLaf \
  -jar install/SweetHome3D-7.5.jar
```

The core theme classes include `FlatLightLaf`, `FlatDarkLaf`, `FlatIntelliJLaf`,
and `FlatDarculaLaf`. The FlatLaf dependency is stored in `lib/flatlaf.jar` and
is included automatically in executable JARs and platform packages.

### Platform Packages

Build a self-contained application image for the current platform with:

```sh
ant clean packageAppImage
```

The image contains a runtime linked from the JDK 21 running Ant. Packaging is
host-native and supports Windows x64, Linux x64, and macOS x64 or arm64. The
32-bit Windows and Linux packages and the old cross-platform portable archive
are no longer supported.

Platform release targets are:

```sh
ant clean windowsInstaller
ant clean macosxInstaller
ant clean linux64Installer
```

Run each target on its matching operating system. The Windows target requires
the native tooling used by `jpackage` to create an EXE. macOS creates separate
x64 and arm64 DMGs rather than merging runtimes into a universal application.
The Linux target creates `install/SweetHome3D-7.5-linux-x64.tgz`.

Signed release targets require platform credentials that are intentionally not
stored in this repository:

```sh
ant -Dwindows.signing.thumbprint=THUMBPRINT windowsSignedInstaller
ant macosxSignedInstaller
```

`signtool.exe` must be available on `PATH` for Windows signing. The macOS target
prompts for the team or user portion of an installed Developer ID identity.

With [Task](https://taskfile.dev/) installed, the common local commands are:

```sh
task run
task package:image
task package:linux
task test
task test:headless
task test:virtual-x-server
```

`task test` delegates to `task test:virtual-x-server`, which runs the complete
suite in Xvfb without opening windows on the desktop. `task test:headless` runs
the stable non-GUI suite used by CI.

## JDK Version Pin

The build is pinned to **JDK 21 (LTS)**. This is deliberate and enforced across
`build.xml` (`java.release=21`), `Taskfile.yml`
(`JAVA_VERSION=temurin-21.0.6+7.0.LTS`), CI (`actions/setup-java`, version 21),
and Eclipse metadata.

**Why 21 and not newer:** JDK 22+ regresses the placement of top-level menu
dropdowns on ultra-wide and multi-monitor X11/XWayland desktops. On this fork's
development setup (an ultra-wide monitor next to a laptop with a left dock),
clicking `File`, `Edit`, or `Plan` renders every popup at the same
`x = screenInsets.left` — hundreds of pixels right of the menu title — because:

- JDK 22 added a "keep the popup on the correct screen" clamp to
  `JMenu.getPopupMenuOrigin()` (comment cites `JDK-6415065`). The clamp compares
  an inset-shifted `position.x` against the raw `screenBounds.x`, so a large left
  screen inset snaps every top-level menu popup to the inset edge.
- Mutter/XWayland exposes a single global `_NET_WORKAREA`; when a panel or dock
  reserves space on one monitor, adjacent monitors can report a large
  `getScreenInsets().left` even though nothing is actually docked there.

Bisecting across asdf JDK builds confirmed the boundary: JDK 21 renders popups
under their menu, while JDK 22, 23, 24, 25, and 26 snap them to the inset edge.
There is no clean application workaround — the displacement happens inside
`getPopupMenuOrigin()`, before `adjustPopupLocationToFitScreen`, so the documented
`-Djavax.swing.adjustPopupLocationToFit=false` does not help. Moving to a newer
JDK is tracked in [issue #24](https://github.com/laszukdawid/SweetHome3D/issues/24);
a self-contained reproducer and a ready-to-file upstream report live under
[`test/jbs/`](test/jbs/).

**Note on `libtest/jdepend-2.10.jar`:** it is rebuilt from the upstream JDepend
2.10 source with `--release 11` so it remains readable when compiling with the
pinned JDK 21 while still parsing modern class files.

## Fork Status

This is the initial repository staging pass. No application code or behavior was
changed here; future changes will be developed separately from the preserved
upstream snapshot.
