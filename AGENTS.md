# AGENTS.md

Guidelines for AI coding agents and contributors working in this repository.

## Git commits

This repository follows the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
specification for all commit messages.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

- `feat` — a new feature (e.g. a new user-facing option)
- `fix` — a bug fix
- `docs` — documentation only changes
- `style` — changes that do not affect code meaning (formatting, whitespace)
- `refactor` — code change that neither fixes a bug nor adds a feature
- `perf` — a performance improvement
- `test` — adding or correcting tests
- `build` — changes affecting the build system or external dependencies
- `ci` — changes to CI configuration files and scripts
- `chore` — other changes that don't modify source or test files
- `revert` — reverts a previous commit

### Rules

- Use the imperative mood in the description (`add`, `fix`, not `added`, `fixed`).
- Keep the subject line under 72 characters.
- Do not capitalize the subject line; no trailing period.
- Use an optional scope in parentheses when the change is limited to a subsystem,
  e.g. `feat(preferences): add plan and 3D view split orientation option`.
- Use a body to explain the `what` and `why` when the subject alone is not enough.
- Use footer `BREAKING CHANGE:` for breaking changes; otherwise prefer `!` after the
  type/scope (e.g. `feat!:`).

### Examples

```
feat(preferences): add option to show 3D view next to the plan

The plan and 3D view split orientation can now be chosen in the
Preferences dialog and is persisted across sessions.
```

```
fix: restore divider ratio when changing split orientation
```
