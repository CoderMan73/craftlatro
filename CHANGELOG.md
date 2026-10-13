# Changelog

All notable changes to craftlatro are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/). The release process is
documented in [docs/commits-and-releases.md](docs/commits-and-releases.md).

## [Unreleased]

### Changed

- **CI runs only on a published release.** Pushes, merges and pull requests no longer trigger a build
  at all — the only thing worth building is the release artifact, and the local
  `./gradlew clean build check` is now the gate between a commit and a tag. A failed release workflow
  leaves the release published without a jar, never with a broken one; the runbook documents the
  recovery.
- **Release notes carry a Full Changelog link** to GitHub's compare view, so the total difference
  across every commit in the release is one click away. Both published releases have it; the runbook
  has the template.

## [0.9.1] - 2026-10-13

Patch release. No gameplay or window behaviour changed since 0.9.0.

### Added

- **Release automation.** Publishing a GitHub release now builds the tagged source and attaches the
  jar to it (`.github/workflows/release.yml`), so the artifact on a release is what that tag builds,
  not a file copied across from a laptop. Creating a release stays a human decision.

### Fixed

- **CI works.** The build workflow had never gone green: its first line (`name=build`) was not YAML so
  every run died with zero jobs, the Gradle wrapper jar was gitignored so there was nothing to
  launch, and `gradlew` had no executable bit for Linux runners. All three are fixed, the workflow now
  runs `./gradlew build check` on pushes and pull requests into `main`, and it keeps the jar as a
  build artifact.

### Changed

- The release runbook now says what is already true: the local jar is the release artifact and CI is a
  convenience, not a gate. The CI failure history is written down so it is not rediscovered.
- Reaching 1.0 is unchanged: the ROADMAP backlog is still the gate, and 0.9.1 is a waypoint.

[0.9.1]: https://github.com/CoderMan73/craftlatro/releases/tag/v0.9.1
[0.9.0]: https://github.com/CoderMan73/craftlatro/releases/tag/v0.9.0

## [0.9.0] - 2026-10-13

First release prepared for publication. The concept has been proven since 2026-10-12 (Balatro runs
in-game through the love.js pipeline); everything below is the UX and polish that makes it a mod a
player can be handed.

### Added

- **A real window for Balatro.** Movable, resizable, aspect-lockable, with a title bar carrying
  expand/restore, refresh, settings and close buttons, an optional blurred backdrop, and a full-screen
  mode that fills the viewport edge to edge and keeps only the title bar.
- **A settings screen** on Cloth Config, reachable with `/craftlatro config`, the `S` button on the
  window, or Mod Menu's config button, split into Browser (General / Performance) and Craftlatro
  (installation instructions and the fidelity disclaimer).
- **Portable save export and import.** `/craftlatro save export <name>` copies the game's IndexedDB
  store to `config/craftlatro/saves/<name>.zip`; `/craftlatro save import <file>` stages one for the
  next restart; `/craftlatro save list` shows what is saved and which game version each came from.
- **Save data erasure.** `/craftlatro save clear` (with a `confirm` step) erases the game's save data
  and restarts it on a fresh profile.
- **A resolution notice.** When the window settles somewhere more than 10% away from the size the game
  loaded at, a strip along the top of the window offers Reload page or Ignore, because the game's
  canvas is sized once, at page load, and never follows a resize.
- **Render scale up to 200%.** Above 100% the game renders sharper than the window, capped at the
  window's own full-screen size.
- **Everything the mod writes now lives inside the Minecraft instance** — `config/craftlatro.properties`,
  `config/craftlatro/saves/` and `<instance>/craftlatro/game/` — migrated automatically from the old
  `~/.craftlatro` location.

### Changed

- The Chromium surface follows the window live, so resizing and dragging no longer leave the game
  stretched at a stale resolution.
- The stats overlay is headed `browser diagnostics` and no longer names Balatro.
- The whole settings and in-window text was rewritten for clarity (tooltips now say when a change
  applies, and how).

### Fixed

- Full screen no longer leaves an 8px margin and border ring around the game.
- A window that changed size no longer needs a client restart for the game to render at the new
  resolution.
- The open-key toggle could never fire, because clicks stop when a screen is open.
- Tooltips no longer render as a narrow box, and the keybinds have their own category.

### Security

- No Balatro code, assets or redistributable runtimes are bundled; the Chromium runtime remains
  Rinku's responsibility at runtime.
