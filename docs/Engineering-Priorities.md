# Engineering Priorities

Ranked list of actionable improvements, consolidated from the 2026-08 staff
engineering review. Update as items land. Cross-references:
[Architecture](Architecture.md) · [Code Quality](Code-Quality.md) ·
[Testing](Testing.md) · [Build & CI](Build-and-CI.md).

## P0 — do now (days)

### 1. Run the full suite in CI (trivial, highest ROI) — DONE

CI now runs `ant ci-full` (`clean,test-all,test-yafaray,application`) under
`xvfb-run`, so all 43 test classes gate every push. `ant ci` remains the fast
headless local gate.

### 2. Add coverage + static analysis tooling — DONE (non-gating)

`ant coverage` runs the headless suite under pinned JaCoCo 0.8.15 and writes
XML+HTML reports; `ant spotbugs` analyzes the built jar with SpotBugs 4.10.4
(all downloads SHA-256 verified, same pattern as `fetch-junit`). CI runs both
and uploads reports as artifacts. Follow-ups: triage the initial SpotBugs
findings into a baseline/exclude filter, then make both gate the build.

### 3. Supply-chain hygiene

- Add `.github/dependabot.yml` (at least for GitHub Actions).
- SHA-pin `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`.
- Add `setup-java` caching (or cache `~/.cache/sweethome3d`) to kill redundant
  downloads.

### 4. Plan iText retirement — PLAN READY

Investigation complete: the dependency has a single thin consumer
(`HomePDFPrinter`, ~80 lines, no fonts/forms/encryption) and OpenPDF is a
verified drop-in (`com.lowagie` namespace kept up to 2.x; 3.x renames to
`org.openpdf`, 6 imports). Full plan and execution steps:
[iText Retirement](iText-Retirement.md). Remaining work is executing it.

## P1 — this quarter

### 5. Persistence tests, then XML as default format

- XML round-trip: `HomeXMLExporter` → `HomeXMLHandler` → structural compare
  against the original home model.
- `AutoRecoveryManager`: simulate crash/recovery scenarios.
- Use the existing damaged-home fixtures to cover `ContentDigestManager`.
- After round-trip coverage exists, make **XML the default write format** and
  gate Java serialization behind a legacy-compatibility flag. This retires the
  deserialization attack surface and halves dual-format maintenance risk.
  Context: [Architecture — Persistence](Architecture.md#persistence).

### 6. Kill the `sun.awt.AppContext` reflection hack

`VideoPanel.java:1802–1814` reflects into removed internal API — a guaranteed
breakage path on modern JDKs. Fix before any runtime JDK bump, not after.

### 7. Improve `PackageDependenciesTest` diagnostics

Iterate JDepend's `analyze()` results and report which edges mismatch instead
of one monolithic "Dependency mismatch" failure; revisit the odd
`viewcontroller → swing.text/html` allowance. ~~Also add the missing
`.tool-versions` file and align Eclipse `.classpath` with the compiler
release.~~ Done; both now use Temurin / Java 26.

## P2 — strategic (quarters)

### 8. Decompose the god classes mechanically first

`PlanController` (15,944 lines, ~100 nested classes), `PlanComponent`
(7,755), `HomePane` (5,988). First pass is zero-behavior: lift each
interaction state (~40) and undoable edit (55) in `PlanController` into
top-level files; compiler-verified. Later passes: move `HomePane.OBJExporter`
into `io/`; evaluate a real state-machine abstraction for plan interaction.
Details: [Architecture — Key files](Architecture.md#key-files-god-class-concentration).

### 9. Unify or formalize the dual notification mechanisms

PropertyChangeSupport and custom CollectionEvent both exist; every new view
must learn both. Either unify or document when each applies.

### 10. Retire dead subsystems decisively

Applet/JNLP code and build targets (`applet/`, `deploy/`, `javaWebStart`),
JMF, 32-bit natives trees, vendored `src/com/sun/swing` overrides, root-level
license source-diff zips. Each removal shrinks audit surface. Unblocks
dropping jmf.jar / jnlp.jar from lib entirely.

### 11. Snapshot model state for renders

Photo/video threads read the live mutable `Home` graph while the user edits;
safety relies on convention. Introduce snapshots once persistence work has
settled the model API. Related hazards: poll-based cancellation
(`PhotoPanel.java:930`), singleton manager thread-safety assumptions.

### 12. Modernization cleanups (opportunistic)

- Gradual generics / enhanced-switch cleanups, starting in code being touched.
- Remove Java 1.6-era reflection fossils (e.g. `File.getUsableSpace` via
  reflection, `HomeFileRecorder.java:214–215`).
- Eventually rename `com.eteks.sweethome3d` → project namespace as a single
  mechanical, compiler-verified refactor (not a string replace). Keep upstream
  attribution in license headers/NOTICE regardless; beware i18n bundles keyed
  by class names.
- JUnit 3→4 annotation migration across all 43 test classes as one mechanical
  PR, unlocking parameterized persistence tests.
- Replace raw sleeps with latches in `PlanComponentTest` / `HomeCameraTest`
  when touched.

### 13. Tooling portability

`Taskfile.yml` resolves JAVA_HOME via asdf with a hardcoded version. Fine for
now; revisit if a second regular contributor appears (CI is unaffected).

## Done

- ~~Add a JDepend/architecture check guarding the MVC seam~~ — already exists:
  `PackageDependenciesTest` enforces model purity and package layering at test
  time. Remaining work is diagnostics quality (item 7).
