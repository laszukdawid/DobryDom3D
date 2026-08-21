# DobryDom3D — Engineering Wiki

Internal engineering documentation for the DobryDom3D code base (a fork of
Sweet Home 3D 7.5). This wiki is written for contributors; user-facing docs
live in the repository README.

## Pages

- [Architecture](Architecture.md) — layering, module map, coupling, threading,
  persistence, key design decisions
- [Code Quality](Code-Quality.md) — assessment of the inherited code and the fork's own changes
- [Testing](Testing.md) — test inventory, what runs where, coverage gaps, how to add tests
- [Build & CI](Build-and-CI.md) — build system, CI pipeline, vendored dependencies & supply-chain risks
- [iText Retirement](iText-Retirement.md) — investigation and plan for replacing the EOL iText 2.1.7 PDF dependency
- [Engineering Priorities](Engineering-Priorities.md) — ranked list of actionable improvements

## Quick facts

| | |
|---|---|
| Language | Java (Swing + Java 3D), pinned to JDK 21 |
| Build | Apache Ant (`build.xml`), Task runner (`Taskfile.yml`) |
| Tests | JUnit 4.13.2 (+ Abbot for GUI), `ant test` / `ant test-all` |
| CI | GitHub Actions: `.github/workflows/java-ci.yml` |
| Source size | ~244 production classes / ~163k LOC |
| Test size | 43 test classes / ~134 test methods / ~13.4k LOC |

## Local commands

```sh
task run              # build and run the app
task test             # full suite under Xvfb (test-all)
task test:headless    # headless suite only (what CI gates on)
task package:image    # jpackage image for current platform
```
