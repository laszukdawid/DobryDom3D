# Engineering Priorities

Ranked list of actionable improvements. Update as items land.

## 1. Run the full suite in CI (trivial, highest ROI)

CI already runs under `xvfb-run` but gates on the headless allow-list only.
Switch the CI step to `ant test-all` (or run both) so ~22 GUI-heavy test
classes stop being local-only.

## 2. Persistence tests

- XML round-trip: `HomeXMLExporter` → `HomeXMLHandler` → structural compare
  against the original home model.
- `AutoRecoveryManager`: simulate crash/recovery scenarios.
- Use the existing damaged-home fixtures to cover `ContentDigestManager`.

## 3. Hoist per-repaint allocations in PlanComponent

Strokes/paints rebuilt identically on every repaint in `paintContent` and
duplicated in `paintHomeItems`. Cache per scale; it is the hottest path.

## 4. Modernization (unblocked by upstream abandonment)

Upstream is no longer maintained, so there is no merge-debt argument against
modernizing:

- Gradual generics / enhanced-switch cleanups, starting in code being touched.
- Eventually rename `com.eteks.sweethome3d` → project namespace as a single
  mechanical, compiler-verified refactor (not a string replace). Keep upstream
  attribution in license headers/NOTICE regardless.
- Decide the fate of legacy subsystems: applets/JNLP (`deploy/`), JMF, and the
  duplicated Java 3D 1.5 + JOGL 1.x trees alongside 1.6 (~10+ MB committed).

## 5. Guard the MVC seam

Add a JDepend/architecture check asserting `model/` has no `javax.swing` /
`javax.media.j3d` imports and that `viewcontroller/` never references
`swing/`/`j3d/` — currently enforced only by convention.

## 6. Tooling portability

`Taskfile.yml` resolves JAVA_HOME via asdf with a hardcoded version. Fine for
now; revisit if a second regular contributor appears (CI is unaffected).
