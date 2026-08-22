# Build, CI & Supply Chain

Assessment of the build system, CI pipeline, packaging, vendored dependencies,
and repository hygiene. Findings from the 2026-08 engineering review; ranked
risks at the bottom.

## build.xml (Apache Ant, 1,530 lines)

Default target: `jarExecutable`.

Target graph:

- Compile chain: `build` → `application` → `jarExecutable` (depends on
  application + furniture/textures/examples/help/jogl-java3d×2 resource jars).
- Test chain: `fetch-junit` → `compile-tests` → `test` / `test-all` /
  `test-yafaray`; aggregate target `ci` = `clean,test,test-yafaray,application`.
- Packaging: `packageAppImage`, `windowsInstaller`(+Signed),
  `macosxInstaller`(+Signed), `linux64Installer`, `sourceArchive`, `javadoc`.
- Legacy Web Start, applet, and viewer deployment targets were removed with
  the Java 26 upgrade because the Applet API no longer exists.

Dependency management: fully vendored jars in `lib/`/`libtest/`. Only external
downloads are JUnit 4.13.2 / Hamcrest Core 1.3 via `fetch-junit` (build.xml:222)
from Maven Central **with pinned SHA-256 checksums** (build.xml:230,239) into
`~/.cache/sweethome3d`. No Ivy/Maven/Gradle. The ~30 vendored jars have no
checksums or provenance manifest.

Compiler settings:

- Desktop application sources are compiled with `javac` in the `build` target.
- `release="26"` via property `java.release=26` (build.xml:42, 183–190).
- Encoding ISO-8859-1.
- **No `-Xlint`, no deprecation warnings enabled; `debug=false`** for
  production code (`debug=true` for tests). No static analysis beyond the
  JDepend-based `PackageDependenciesTest`; a legacy `jdepend` target launches a
  GUI to update the dependency constraint manually.
- Test runner: `<junit fork="true" forkmode="perTest">`, haltonerror/failure,
  per-test timeouts (120 s headless, 60 s test-all). Details in
  [Testing](Testing.md#how-tests-run).

Security-conscious extras worth keeping:

- `_checkTestCredentialIsolation` (build.xml:281): sentinel env-var leak probe.
- `_checkTestReportProperties` (build.xml:309): refuses to publish JUnit XML
  containing `env.*` properties.

## Taskfile.yml & toolchain pinning

Tasks: `run`, `package:image`, `package:linux`, `test`, `test:headless`,
`test:virtual-x-server`; all gated on `check-java` preconditions requiring
asdf.

- `JAVA_VERSION: temurin-26.0.2+10` is hardcoded in Taskfile.yml;
  `JAVA_HOME` derived via `asdf where java`.
- `.tool-versions` pins the same temurin build for asdf/mise users (the
  plugin spelling of Adoptium release `jdk-26.0.2+10`; non-LTS releases carry
  no GA/LTS suffix); keep it in sync with `JAVA_VERSION` and CI's setup-java pin.
- Caching via go-task `sources/generates/method: timestamp`, only for
  `build:executable`.
- `task test` = `xvfb-run -a -s "-screen 0 1920x1080x24 -ac +extension GLX
  +render -noreset" ant clean test-all`.

## GitHub Actions (.github/workflows/)

Two workflows plus Dependabot:

- **`java-ci.yml`** — push/PR to `main`. Single job `test-and-build` on
  `ubuntu-latest`: runs `ant ci-full` under `xvfb-run`, then
  `linux64Installer`, `coverage`, `spotbugs`, an env-leak scan gate before
  artifact upload.
- **`release.yml`** — tag push `v*`. Refuses tags that don't match the
  `version` property in build.xml, runs `ci-full` under xvfb, builds
  `linux64Installer`, validates the tgz (exactly one archive, gzip
  integrity, launcher + application jar entries present), uploads it as a
  workflow artifact, and attaches it to a **draft** GitHub release via the
  built-in `GITHUB_TOKEN`. Rerun-safe: an existing draft gets its archive
  asset replaced (`--clobber`); a published release is never modified and
  fails the run instead. Nothing is auto-published; no external secrets.

| Aspect | Status |
|---|---|
| Permissions | ✅ top-level `permissions: contents: read`; the release job alone elevates to `contents: write` for draft-release attachment |
| Action pinning | ✅ all actions pinned by SHA with version comment |
| Concurrency | ✅ per-ref group; superseded PR runs cancelled (tag/release runs never cancelled) |
| Java pin | ✅ exact temurin release `26.0.2+10`, matching Taskfile.yml / .tool-versions |
| Caching | ✅ `~/.cache/sweethome3d` (checksum-pinned JUnit/Hamcrest/JaCoCo/SpotBugs) |
| Artifacts | ✅ JUnit reports, app JAR, linux x64 tgz, coverage, SpotBugs; env-leak scan gate before upload |
| Timeouts | ✅ job-level (40 min) plus step-level on the long ant steps |
| Matrix | ❌ none (Java 26 Linux only) |

Consequences still true:

- Runs `ant ci-full` under xvfb then `ant linux64Installer`.
- **Windows/macOS installer targets have never executed in CI**, although they
  are release targets requiring host-native runs.
- Signing remains manual Ant targets only; the release workflow stops at a
  validated draft release by design (no deployment secrets configured).

## Vendored dependencies (`lib/`, ~85 MB with natives)

| Jar | Version | Concern |
|---|---|---|
| iText-2.1.7.jar | 2.1.7 (2009) | **High risk.** EOL pre-iText 4.2.0 GPL-era; CVE-era XXE exposure (CVE-2017-9096 family). Used for PDF print; dropped only on the macOS package path (build.xml:1035). Ships in every other distributed artifact incl. installers. |
| batik-svgpathparser-1.7.jar | Batik 1.7 (2008) | Only the svgpathparser subset jar, but Batik < 1.17 carries SSRF/RCE CVE families (e.g. CVE-2022-38398); exploitability likely nil at this surface but unverifiable without provenance. |
| jmf.jar | JMF 2.x (EOL ~2003) | Abandoned; tied to dead applet/video paths. |
| sunflow-0.07.3i.jar | 0.07.3i (~2010) | Dead project; used by photo rendering path. |
| freehep-vectorgraphics-svg-2.1.1c.jar | 2.1.1c (2009) | EOL FreeHEP; SVG export. |
| jeksparser-calculator.jar | unlabeled | Unknown vintage/provenance. |
| flatlaf.jar | 3.7.2 | ✅ Current; the only modernized runtime dep. |
| java3d-1.6/{j3dcore,j3dutils,vecmath}.jar | Java 3D 1.6.2a | Unofficial jogamp 1.6 preview line; upstream frozen ~2015–16. |
| java3d-1.6/jogl-all.jar | JOGL 2.5.0 (2023 build) | Reasonably current JogAmp. |

Platform natives: `lib/linux/{i386,x64}`, `lib/windows/{i386,x64}`,
`lib/macosx` (legacy JOGL jnilibs), `lib/java3d-1.6/{i586,linux,windows,macosx}`,
`lib/yafaray/{linux,macosx,windows}`. The 32-bit (`i386`/`i586`) trees are dead
weight for a JDK 26 product that no longer ships 32-bit packages.

`libtest/` (8 jars): abbot (~2008 era), AppleJavaExtensions, gnu-regexp-1.1.4,
javaAwtDesktop, jdepend-2.10 (rebuilt `--release 11`), **jdom-1.1.1**
(pre-JDOM2, XXE-era; test-only so lower severity), jnlp.jar, profile.jar.

**Bottom line: ~7 of 9 runtime jars are 10–20 years past end-of-life; iText
2.1.7 ships to end users.**

## Packaging (`install/`)

- Modern flow is JDK 26 `jpackage`: per-OS targets with host/arch guards
  (`_checkPackageHost` enforcing JDK 26 and x64 everywhere, arm64 mac-only).
- `install/jpackage/*.properties`: 6 file associations
  (.sh3d/.sh3f/.sh3l/.sh3p/.sh3t/.sh3x).
- Windows: exe (`--win-menu/dir-chooser/shortcut`, upgrade UUID), SHA-256 +
  timestamp signtool signing, post-image hook
  (`install/windows/DobryDom3D-post-image.wsf`) injecting signing env vars.
- macOS: DMG, signed variant with entitlements
  (`install/macosx/SweetHome3D.entitlements`).
- Linux: tgz of the app image (`install/DobryDom3D-*-linux-x64.tgz`).
- Legacy cruft remains: `install/macosx/Sweet Home 3D/` bundle template,
  `deploy/` PHP/JNLP/applet assets (dead since Web Start removal).

## Repository hygiene

- `.gitignore`: reasonable; covers build output, release artifacts, `.task/`,
  `issues.md`.
- **Binary patch artifacts at repo root**:
  `freehep-vectorgraphics-svg-2.1.1c-src-diff.zip` (15 KB) and
  `sunflow-0.07.3i-src-diff.zip` (118 KB) — license-compliance source dumps.
  Should live under docs/ or be referenced, not root clutter.
- **Eclipse metadata committed**: `.project`, `.classpath`, `.settings/`
  (25 KB jdt prefs). `.classpath` pins a `JavaSE-26` JRE container,
  consistent with `release=26` elsewhere; also shipped inside
  `sourceArchive` (build.xml:1460–1464).
- Size: src/ ≈ 32 MB (5.8 MB of it io/resources furniture/texture/example data
  — inherent to Sweet Home 3D), lib/ ≈ 85 MB, .git ≈ 46 MB. Acceptable for
  this project type.
- Vendored source tree `src/com/sun/swing/...` (Swing PLAF resource overrides)
  adds confusion alongside `com.eteks.sweethome3d`.
- Good: AGENTS.md (Conventional Commits), CHANGELOG, engineering wiki,
  consistent rebrand.

## Dependency update strategy

**GitHub Actions only.** Dependabot keeps the pinned actions up to date
(weekly, grouped). It does **not** cover the vendored Java jars in `lib/` /
`libtest/` — no SBOM, no OWASP dependency-check, no manifest of jar
coordinates/hashes for vendored libs (only JUnit/Hamcrest carry pinned
checksums). Upgrades of runtime jars require manually replacing binaries in
`lib/`.

## Ranked risks

1. **iText 2.1.7 shipped to end users** (known-CVE-era, EOL) — highest-severity
   item in the repo. Retirement plan:
   [iText Retirement](iText-Retirement.md).
2. **No dependency update mechanism or vulnerability scanning**; 7+ EOL
   runtime jars without checksums/provenance.
3. ~~**Actions tag-pinned, not SHA-pinned**;~~ single Linux-only workflow means
   Windows/macOS packaging paths are untested in CI (resolved: actions are
   SHA-pinned; a `release.yml` tag pipeline validates the Linux archive and
   produces a draft release).
4. ~~**Toolchain drift**~~ resolved: CI, `.tool-versions`, and Taskfile's
   inline pin all track temurin `26.0.2+10`, and the Eclipse `.classpath`
   container (`JavaSE-26`) matches the compile release.
5. Legacy `deploy/` Web Start and applet assets remain as inert repository
   files, although their source package and build targets were removed.

## Quick wins

- ~~Add `.github/dependabot.yml` (at minimum for Actions); SHA-pin the three
  actions.~~ Done.
- ~~Enable `setup-java` `cache:` or cache `~/.cache/sweethome3d`.~~ Done.
- ~~Commit a `.tool-versions` matching the Taskfile temurin pin.~~ Done
  (`java temurin-26.0.2+10`); the `.classpath` container is aligned too
  (`JavaSE-26`).
- Plan iText replacement (OpenPDF 1.x is the GPL-compatible continuation);
  document a Batik/JMF/sunflow retirement plan.
- Move the two `-src-diff.zip` files out of the repo root; delete dead
  `deploy/` JNLP/applet assets and Web Start targets if unsupported.
