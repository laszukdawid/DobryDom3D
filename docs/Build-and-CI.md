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
  `macosxInstaller`(+Signed), `linux64Installer`, `viewerInstaller`,
  `sourceArchive`, `javadoc`.
- **Legacy Web Start/applet targets retained**: `javaWebStart`, `applet`,
  `viewer`, `java3dLibraries` — dead weight since Web Start removal (see
  risks).

Dependency management: fully vendored jars in `lib/`/`libtest/`. Only external
downloads are JUnit 4.13.2 / Hamcrest Core 1.3 via `fetch-junit` (build.xml:222)
from Maven Central **with pinned SHA-256 checksums** (build.xml:230,239) into
`~/.cache/sweethome3d`. No Ivy/Maven/Gradle. The ~30 vendored jars have no
checksums or provenance manifest.

Compiler settings:

- Two-stage javac (applet entry points compiled first).
- `release="21"` via property `java.release=21` (build.xml:39, 180–187).
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

- `JAVA_VERSION: temurin-21.0.6+7.0.LTS` is hardcoded in Taskfile.yml:4;
  `JAVA_HOME` derived via `asdf where java`.
- **Risk: there is no `.tool-versions` file in the repo** despite documentation
  referring to one — the pin lives only inside Taskfile.yml, so asdf users get
  no automatic version selection and the pin can drift from CI's floating
  "21" (`actions/setup-java`) and from Eclipse metadata.
- Caching via go-task `sources/generates/method: timestamp`, only for
  `build:executable`.
- `task test` = `xvfb-run -a -s "-screen 0 1920x1080x24 -ac +extension GLX
  +render -noreset" ant clean test-all`.

## GitHub Actions (.github/workflows/java-ci.yml)

Exactly one workflow, single job `test-and-build` on `ubuntu-latest`, triggered
on push/PR to `main`.

| Aspect | Status |
|---|---|
| Permissions | ✅ top-level `permissions: contents: read` |
| Action pinning | ⚠️ tag-pinned (`actions/checkout@v4`, `setup-java@v4`, `upload-artifact@v4`) — not SHA-pinned |
| Matrix | ❌ none (Java 21 Linux only) |
| Caching | ❌ none (`apt-get install ant ant-optional xvfb` every run; no setup-java cache) |
| Artifacts | ✅ JUnit reports, app JAR, linux x64 tgz; env-leak scan gate before upload |
| Timeout | ✅ 20 min |

Runs `ant ci` under xvfb then `ant linux64Installer`. Consequences:

- **Windows/macOS installer targets have never executed in CI**, although they
  are release targets requiring host-native runs.
- No release-publishing workflow; signing exists as manual Ant targets only.
- No Dependabot/renovate configuration anywhere in the repo.

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
weight for a JDK 21 product that no longer ships 32-bit packages.

`libtest/` (8 jars): abbot (~2008 era), AppleJavaExtensions, gnu-regexp-1.1.4,
javaAwtDesktop, jdepend-2.10 (rebuilt `--release 11`), **jdom-1.1.1**
(pre-JDOM2, XXE-era; test-only so lower severity), jnlp.jar, profile.jar.

**Bottom line: ~7 of 9 runtime jars are 10–20 years past end-of-life; iText
2.1.7 ships to end users.**

## Packaging (`install/`)

- Modern flow is JDK 21 `jpackage`: per-OS targets with host/arch guards
  (`_checkPackageHost` enforcing JDK 21 and x64 everywhere, arm64 mac-only).
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
  (25 KB jdt prefs). `.classpath` pins a `JavaSE-25` JRE container —
  inconsistent with `release=21` everywhere else; also shipped inside
  `sourceArchive` (build.xml:1460–1464).
- Size: src/ ≈ 32 MB (5.8 MB of it io/resources furniture/texture/example data
  — inherent to Sweet Home 3D), lib/ ≈ 85 MB, .git ≈ 46 MB. Acceptable for
  this project type.
- Vendored source tree `src/com/sun/swing/...` (Swing PLAF resource overrides)
  adds confusion alongside `com.eteks.sweethome3d`.
- Good: AGENTS.md (Conventional Commits), CHANGELOG, engineering wiki,
  consistent rebrand.

## Dependency update strategy

**None.** No dependabot.yml, no renovate.json, no SBOM, no OWASP
dependency-check, no manifest of jar coordinates/hashes for vendored libs
(only JUnit/Hamcrest carry pinned checksums). Upgrades require manually
replacing binaries in `lib/`.

## Ranked risks

1. **iText 2.1.7 shipped to end users** (known-CVE-era, EOL) — highest-severity
   item in the repo. Retirement plan:
   [iText Retirement](iText-Retirement.md).
2. **No dependency update mechanism or vulnerability scanning**; 7+ EOL
   runtime jars without checksums/provenance.
3. **Actions tag-pinned, not SHA-pinned**; single Linux-only workflow means
   Windows/macOS packaging paths are untested in CI.
4. **Toolchain drift**: Taskfile pins temurin 21.0.6 inline, CI floats "21",
   Eclipse `.classpath` says JavaSE-25, `.tool-versions` missing.
5. Legacy attack surface kept alive: Web Start/applet/JNLP targets with
   `Permissions: all-permissions` manifests and a hardcoded PKCS#11 storepass
   `0000` (build.xml:727 et al.) — mostly inert but confusing and sign-capable.

## Quick wins

- Add `.github/dependabot.yml` (at minimum for Actions); SHA-pin the three
  actions.
- Enable `setup-java` `cache:` or cache `~/.cache/sweethome3d`.
- Commit a `.tool-versions` matching the Taskfile temurin pin; align
  `.classpath` to 21.
- Plan iText replacement (OpenPDF 1.x is the GPL-compatible continuation);
  document a Batik/JMF/sunflow retirement plan.
- Move the two `-src-diff.zip` files out of the repo root; delete dead
  `deploy/` JNLP/applet assets and Web Start targets if unsupported.
