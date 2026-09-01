# Panel layout bug on JDK 26 (issue #61)

**Status: fixed.** See "Fix implemented" at the bottom for the change and its
verification. The rest of this document is the investigation trail that led there.

This continues an earlier investigation summary from a prior session (not itself
checked into this repo). That summary correctly ruled out
`ComponentOrientation.getOrientation(Locale)` itself as regressed between Java 8 and
Java 26, but stopped before finding the actual mechanism. This session found it.

## Correction to the earlier reading of the screenshots

The prior summary (and this session, initially) described the DD3D screenshot as "mirrored
left-to-right". That's incomplete. Comparing the GitHub issue's two screenshots
quadrant-by-quadrant:

- **Correct (stock SH3D):** TL = catalog, BL = furniture list, TR = plan view, BR = 3D view.
- **Broken (DD3D):** TL = 3D view, BL = plan view, TR = furniture list, BR = catalog.

That's a **180° point rotation** (TL↔BR, TR↔BL), not a simple left-right mirror: the
top-level left/right split *and* both nested top/bottom splits are reversed
simultaneously. A plain `RIGHT_TO_LEFT` `ComponentOrientation` cascade can't produce
this on its own — `JSplitPane.VERTICAL_SPLIT` has always ignored component orientation
(confirmed empirically below), so RTL alone only explains the horizontal half of the
rotation, not the vertical half.

## Root cause found

Built DD3D from current `HEAD` and ran it under both Java 8 and Java 26 in this sandbox
(Xvfb + `import`), starting from a completely fresh, empty preferences directory (no
stored language, no real user data). **The bug reproduces on Java 26 with zero prior
configuration**, which rules out anything preference/history-dependent.

Added temporary instrumentation to `HomePane.createMainPane()` (reverted afterward,
repo is clean) and confirmed:

- `Locale.getDefault()` is `en_US`/LTR throughout, on both JDKs.
- `ComponentOrientation.getOrientation(Locale.getDefault())` is the `LEFT_TO_RIGHT`
  singleton throughout, on both JDKs (confirmed by identity, not just `.isLeftToRight()`).
- `mainPane.getLeftComponent() == catalogFurniturePane` reads **`true`** at every
  checkpoint I could instrument inside `createMainPane()` — construction, and
  immediately after the `"componentOrientation"` listener's own
  `setLeftComponent`/`setRightComponent` calls.

In other words: the application's own model-building logic is provably correct on
Java 26. The corruption happens *after* that, somewhere inside Swing's own
`Container.applyComponentOrientation()` cascade.

### Minimal, framework-free reproduction

Isolated the exact shape of `HomePane.createMainPane()` (build a `JSplitPane` while the
component tree is still at the default `UNKNOWN` orientation, attach a
`"componentOrientation"` `PropertyChangeListener` that reassigns
`setLeftComponent`/`setRightComponent` based on `getComponentOrientation()`, then later
call `applyComponentOrientation(...)` again from an ancestor — exactly what
`HomePane` line 317 and `HomeFramePane.displayView()` line 123 do, in that order) in a
~60-line standalone test with no FlatLaf, no DD3D code:

```java
JSplitPane mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalogGroup, planGroup);
mainPane.addPropertyChangeListener("componentOrientation", ev -> {
    if (mainPane.getComponentOrientation().isLeftToRight()) {
        mainPane.setRightComponent(null);
        mainPane.setLeftComponent(catalogGroup);   // <- confirmed true right here
        mainPane.setRightComponent(planGroup);     // <- confirmed true right here
    } else { /* ... */ }
});
root.add(mainPane);
frame.applyComponentOrientation(ComponentOrientation.getOrientation(Locale.getDefault())); // triggers the listener
// immediately after this call returns:
System.out.println(mainPane.getLeftComponent() == catalogGroup);
```

Result:

| JDK      | Inside listener, right after `setRightComponent(planGroup)` | Immediately after `applyComponentOrientation()` returns |
|----------|---------------------------------------------------------------|-----------------------------------------------------------|
| 8u502    | `true`                                                         | `true`                                                     |
| 26.0.2   | `true`                                                         | **`false`**                                                |

So the listener does the right thing, and then **something later in the same
`Container.applyComponentOrientation()` recursive cascade — after the listener already
returns — flips `mainPane`'s left/right assignment back**, only on Java 26. This also
explains the nested nested-split top/bottom reversal for free: the same cascade
recurses into `catalogFurniturePane`'s and `planView3DPane`'s own internal
`VERTICAL_SPLIT` panes, and a separately-confirmed JDK 26 change (below) means
`VERTICAL_SPLIT` panes are no longer immune to orientation-driven reordering the way
they were on Java 8.

### Supporting finding: `VERTICAL_SPLIT` + `RIGHT_TO_LEFT` also changed, starting at JDK 25

Independently of the above, a much simpler standalone test
(`JSplitPane(VERTICAL_SPLIT, top, bottom)` + `frame.applyComponentOrientation(RIGHT_TO_LEFT)`)
shows:

- **Java 8–21:** top/bottom order is unaffected by RTL (as documented/expected —
  `VERTICAL_SPLIT` is only about vertical stacking, orientation is a horizontal-text
  concept).
- **Java 25 and 26:** RTL **does** swap which component renders on top vs. bottom for
  `VERTICAL_SPLIT`.

This is a second, separable behavior change in the same area of Swing, and — per the
bisection below — it landed **one release earlier** than the main bug. Locale sweep
(`Locale.getAvailableLocales()` on both JDK 8 and 26, ~1189 locales on 26 due to CLDR
replacing the old ~160-locale COMPAT set) confirms **no locale's RTL/LTR classification
changed** between 8 and 26 — so this doesn't fire in DD3D via the locale path (DD3D
ships no RTL languages), but it's clearly the same family of Swing regression as the
main bug and worth keeping in the eventual upstream bug report.

## Bisection: JDK 8 / 11 / 17 / 21 / 25 / 26

Ran both minimal repros (the `mainPane`-listener repro and the `VERTICAL_SPLIT` + RTL
repro) as a single Java-8-bytecode `.class` (compiled once with `javac` targeting 8, so
the exact same bytecode runs unmodified on every JDK) against all six installed JDKs.
Results:

| JDK | `mainPane` listener repro (`getLeftComponent()==catalogGroup` after the cascade) | `VERTICAL_SPLIT` + RTL repro (top/bottom order) |
|-----|------------------------------------------------------------------------------------|----------------------------------------------------|
| 8   | `true` (correct)                                                                    | normal (top stays on top)                           |
| 11  | `true` (correct)                                                                    | normal                                              |
| 17  | `true` (correct)                                                                    | normal                                              |
| 21  | `true` (correct)                                                                    | normal                                              |
| 25  | `true` (correct)                                                                    | **swapped**                                         |
| 26  | **`false` (broken)**                                                                | **swapped**                                         |

Conclusions:

- The bug that actually reproduces DD3D's reported symptom (the `mainPane`
  left/right-listener flip) is **specific to JDK 26** — JDK 25 is clean. So this is a
  very recent regression, not something that's been silently present since JDK 9+ and
  only just noticed.
- The related-but-separate `VERTICAL_SPLIT` + RTL top/bottom swap was introduced **one
  release earlier, in JDK 25**, and persists in JDK 26. It doesn't affect DD3D directly
  (no RTL locale in play) but is worth mentioning in any upstream report since it's
  almost certainly the same underlying code change or a sibling of it.
- Nothing changed between 8 and 21 for either behavior — four major LTS/interim
  releases (11, 17, 21) all match Java 8's original behavior exactly.

## What this means

This is a **known, currently-open JDK Swing regression**, not a locale/CLDR issue, not
FlatLaf, and not anything DD3D-specific in how it stores state. Found the exact chain
of upstream OpenJDK bugs (all fetched directly from `bugs.openjdk.org`'s REST API,
since the web UI 403s automated fetches):

1. **[JDK-4265389](https://bugs.openjdk.org/browse/JDK-4265389) — "JSplitPane does not
   support ComponentOrientation".** Filed **1999-08-24**. Resolved **2024-07-22**, fix
   version **24**. For JSplitPane's entire ~25-year history through Java 21,
   `setComponentOrientation()` did nothing special — no left/right swap on RTL, ever.
   This is why nothing in this investigation reproduces on JDK 8–21: the feature this
   bug depends on didn't exist yet. JDK 24 made `JSplitPane.setComponentOrientation()`
   swap `leftComponent`/`rightComponent` **unconditionally, on every call**, whenever
   the resulting orientation was RTL (and, per point 2 below, also for `VERTICAL_SPLIT`,
   which it should never have touched).

2. **[JDK-8356594](https://bugs.openjdk.org/browse/JDK-8356594) — "JSplitPane loses
   divider location when reopened via JOptionPane.createDialog()".** Fixed in JDK
   **25** ([PR #25294](https://github.com/openjdk/jdk/pull/25294), diff confirmed).
   Its fix is unrelated to orientation — it wraps the swap-on-orientation-change logic
   inside a new `if (!orientation.equals(curOrn))` guard, to stop `setComponentOrientation`
   from redundantly re-triggering side effects (including the divider-reset bug) when
   called with the orientation it already has. This guard is the origin of the
   `VERTICAL_SPLIT`+RTL top/bottom swap this session bisected to JDK 25: Alan Snyder
   flagged it directly in a comment on JDK-8365886 ("`setComponentOrientation` will flip
   top and bottom components in a vertical split pane. LTR and RTL should not affect
   vertical split panes") — it was never fixed, just moved.

3. **[JDK-8365886](https://bugs.openjdk.org/browse/JDK-8365886) — "JSplitPane loses
   track of the left component when the component orientation is changed".** Fixed in
   JDK **26** ([PR #26893](https://git.openjdk.org/jdk/pull/26893), diff confirmed,
   resolved 2025-10-09). The swap logic added by JDK-4265389 exchanged left/right by
   calling the public `setLeftComponent`/`setRightComponent`, which internally go
   through `Container.addImpl`/`remove`; removing-then-re-adding the *same* component
   object mid-swap could null out `leftComponent` entirely. The fix replaces that with
   a **direct field swap** (`this.leftComponent = ...`) that bypasses `add`/`remove`
   altogether, and explicitly fires a property-change + `revalidate()`/`repaint()` at
   the end. This is where the bisection boundary for the actual DD3D-reproducing bug
   sits (25 clean, 26 broken) — see point 4.

4. **[JDK-8391317](https://bugs.openjdk.org/browse/JDK-8391317) — "JSplitPane
   misinterprets component orientation changes".** **Filed 2026-08-28 — four days
   before this session — status Open, unresolved, target fix version 28, priority P3,
   reported by Alan Snyder** (the same reviewer who flagged point 2). Its description is
   an exact, word-for-word match for the DD3D bug:

   > If I create a JSplitPane and then set its component orientation to
   > `ComponentOrientation.LEFT_TO_RIGHT`, the components will be swapped. The problem
   > is that `setComponentOrientation` uses `equals` to compare the new orientation with
   > the existing one. The original orientation is not equal to `LEFT_TO_RIGHT` because
   > the Unknown bit is set. This code was added in the fix of JDK-8356594.

   That's precisely `HomePane.createMainPane()`'s situation: `mainPane` is built while
   still at the JComponent default `ComponentOrientation.UNKNOWN`; `HomeFramePane.
   displayView()` later calls `applyComponentOrientation(LEFT_TO_RIGHT)`. Since
   `ComponentOrientation` doesn't override `equals()`, `UNKNOWN.equals(LEFT_TO_RIGHT)`
   is `false` (different singleton objects) even though both report
   `isLeftToRight() == true` — so the `!orientation.equals(curOrn)` guard from
   JDK-8356594 fails to recognize this as a no-op, and JSplitPane swaps its components
   anyway. A comment on that issue from Jayathirth D V confirms reverting both
   JDK-8365886 and JDK-8356594 makes the symptom go away — i.e. Oracle's own engineers
   have already isolated it to the same two commits this investigation found
   independently.

   **Affects versions listed on the bug: 25, 26, 27** (27 is presumably an EA build not
   tested here). It is **not fixed as of this writing** — there is no workaround
   available from a newer JDK release; DD3D needs its own fix regardless of upstream
   timing (see below).

So: JDK-4265389 introduced the *feature* (JSplitPane swaps left/right for RTL) 25 years
after it was requested; JDK-8356594's *unrelated* fix for a divider-location bug
accidentally introduced the false-positive-swap-on-`UNKNOWN` bug (JDK-8391317, still
open); and JDK-8365886 changed *how* that erroneous swap is carried out (from a
buggy add/remove dance to a direct field swap) without touching whether it should have
happened at all — which is why this session's bisection lands the DD3D-reproducing
symptom specifically on JDK 26 even though the underlying false-positive-equals bug has
been present since JDK 25.

## Recommended fix direction for DD3D

The root trigger is DD3D's own `createMainPane()` pattern: build the split pane while
its orientation is still the default `UNKNOWN`, then rely on a **second**,
later `applyComponentOrientation()` cascade (from `HomeFramePane.displayView()`) to
"catch up" and fire the `"componentOrientation"` listener that assigns
left/right. That two-cascade, listener-driven re-assignment is exactly the pattern that
trips the Java 26 regression in the minimal repro above.

**Important correction found while implementing the fix:** the obvious-looking version
of "set orientation explicitly at construction" —
`new JSplitPane(HORIZONTAL_SPLIT, catalog, plan); mainPane.setComponentOrientation(...)`
— does **not** work. Verified this empirically before touching real source (see
`MainPaneFixVerify.java` in the scratch dir this session used). The reason: JSplitPane's
own `setComponentOrientation()` override performs the same buggy left/right field swap
*synchronously inside that very call*, the moment orientation transitions away from
`UNKNOWN` — regardless of whether the call comes from an external cascade or from
application code calling it directly right after construction. Tracing the actual
sequence (confirmed by re-reading the JDK-8365886 diff, `super.setComponentOrientation()`
fires the property-change event *before* JSplitPane's own post-`super` swap code runs)
shows why: any registered `"componentOrientation"` listener fires and can correctly fix
things *first*, but then JSplitPane's own swap logic runs immediately after and
re-reads the (now-correct) `left`/`right` fields — only to swap them right back,
unconditionally, undoing the fix. There is no way to out-race this from a listener.

**The fix that actually works**, verified against the real bug on JDK 26 before and
after applying it to `HomePane.java`: construct each `JSplitPane` with **no**
components (the no-arg-components constructor), call `setComponentOrientation(...)`
immediately while there is nothing to swap yet (so the buggy swap-on-first-real-
orientation is a no-op — both `leftComponent`/`rightComponent` are still `null`), and
*only then* call `setLeftComponent`/`setRightComponent` (or `setTop`/`setBottomComponent`,
same methods) to add the real content. `setLeftComponent`/`setRightComponent` don't
carry the same bug — it lives specifically inside `setComponentOrientation()` — so this
ordering sidesteps it entirely rather than racing it. Verified stable across three
redundant `applyComponentOrientation()` cascades in isolation, and confirmed on the
actual application (screenshot: correct catalog/furniture-list/plan/3D-view layout on
JDK 26 from a completely fresh profile).

This must be a DD3D-side fix regardless of upstream: JDK-8391317 is targeted at JDK
**28**, so there is no "just wait for a JDK patch release" option — every JDK 25, 26,
and (presumably, EA) 27 build is affected indefinitely, and DD3D's stated minimum
supported version is 26.

## Fix implemented

Applied the "construct empty → set orientation → add components" pattern to all three
`JSplitPane`s in `HomePane.java` that get reached by `HomeFramePane.displayView()`'s
`applyComponentOrientation()` cascade while still at `UNKNOWN` orientation:

- `createMainPane()` — `mainPane` (`HORIZONTAL_SPLIT`, catalog/plan). The pre-existing
  `"componentOrientation"` listener that reassigns left/right was left in place
  unchanged — it's dead code under normal startup now (orientation is set once and
  never changes again for this instance), but it's harmless and remains available if
  DD3D ever adds live RTL language switching for an already-open home.
- `createCatalogFurniturePane()` — `catalogFurniturePane` (`VERTICAL_SPLIT`, catalog
  view over furniture list). This one had no listener and no orientation-based
  left/right decision at all (always catalog-top/furniture-bottom) — it was purely a
  collateral casualty of JDK-8391317 applying its swap unconditionally regardless of
  split orientation, exactly as Alan Snyder's review comment on JDK-8365886 warned.
- `createPlanView3DPane()` — `planView3DSplitPane` (`HORIZONTAL_SPLIT` or
  `VERTICAL_SPLIT` depending on the `PLAN_VIEW_3D_SPLIT_ORIENTATION` preference).

`updatePlanView3DSplitOrientation()` (the DD3D-only live split-orientation-switch
method from commit `4ab2353`) was checked and left alone: it calls
`JSplitPane.setOrientation(int)` (`HORIZONTAL_SPLIT`/`VERTICAL_SPLIT`, the *split
direction*), a completely different property from `setComponentOrientation
(ComponentOrientation)` (LTR/RTL). Not implicated in this bug.

**Verified:**
- `ant build` compiles cleanly against JDK 26.
- The exact fix pattern was proven correct in isolation first (`MainPaneFixVerify2.java`
  /`MainPaneFixVerify3.java` in the scratch dir), including surviving 3 redundant
  `applyComponentOrientation()` cascades, before touching real source.
- Ran the actual built application on JDK 26 (`--add-opens`/`--add-exports` flags for
  the bundled Java 3D native pipeline, Xvfb display, completely fresh `-Duser.home`) and
  confirmed the panel arrangement is now correct: catalog top-left, furniture list
  bottom-left, 2D plan top-right, 3D view bottom-right — matching stock SweetHome3D's
  layout, from a totally clean profile.

## Suggested next steps

1. ~~Fix this in DD3D~~ — done, see above.
2. Optionally add a comment to
   [JDK-8391317](https://bugs.openjdk.org/browse/JDK-8391317) with this real-world
   repro — it's a young P3 report with only Oracle-internal reproductions so far; a
   concrete downstream consumer hitting it in the wild (with the exact
   `UNKNOWN`→`LEFT_TO_RIGHT`-via-late-`applyComponentOrientation` trigger, and the
   "listener fixes it, then JSplitPane's own post-`super.setComponentOrientation()` code
   swaps it right back" sequencing detail found while implementing the fix) is useful
   corroborating evidence and might help it get prioritized before JDK 28.
3. Consider running the app manually (real furniture/texture resources, real window
   manager, ideally a native display rather than Xvfb) as a final sanity check before
   merging — this session's verification used a `ant build`-only jar (no furniture
   catalog resources) under Xvfb, which is enough to confirm the *layout* fix but
   doesn't exercise everything a full `ant jarExecutable` run would.

## Reference

- Prior investigation session's summary (not checked into this repo; recounted at the
  top of this document).
- DD3D GitHub issue: https://github.com/laszukdawid/DobryDom3D/issues/61
- [JDK-4265389](https://bugs.openjdk.org/browse/JDK-4265389) — JSplitPane does not
  support ComponentOrientation (filed 1999, fixed in JDK 24)
- [JDK-8356594](https://bugs.openjdk.org/browse/JDK-8356594) — JSplitPane loses divider
  location when reopened via JOptionPane.createDialog() (fixed in JDK 25; introduced the
  `equals()`-vs-`UNKNOWN` guard and the `VERTICAL_SPLIT` flip as side effects)
- [JDK-8365886](https://bugs.openjdk.org/browse/JDK-8365886) — JSplitPane loses track of
  the left component when the component orientation is changed (fixed in JDK 26)
- [JDK-8391317](https://bugs.openjdk.org/browse/JDK-8391317) — JSplitPane misinterprets
  component orientation changes (**open**, filed 2026-08-28, target fix version 28) —
  this is the bug DD3D is actually hitting
