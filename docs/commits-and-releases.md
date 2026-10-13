# Commits, versions and releases

How this repo writes commits, numbers versions and cuts releases. Written for the next agent as much
as for humans: everything here was codified while shipping **v0.9.0** on 2026-10-13, so the first
release is also the first worked example (see [The 0.9.0 release](#the-090-release)).

## Commit messages: Conventional Commits

This repo uses [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/). The
history already reads as `feat:` / `fix:` / `docs:` / `style:` and this makes that deliberate.

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | When | Example from this repo |
|---|---|---|
| `feat` | a player-visible capability | `feat: framed, movable and resizable Balatro window` |
| `fix` | a defect, or a bug that never shipped | `fix: the open-key toggle could never fire, because clicks stop when a screen opens` |
| `docs` | documentation only, including `ai-docs/` | `docs: tick the shipped backlog items and mark the superseded fullscreen note` |
| `style` | formatting, naming, no behaviour change | `style: keep the notice bar out of the game's scroll, and wrap a long line` |
| `refactor` | restructuring with no behaviour change | — |
| `perf` | a change whose point is speed | — |
| `test` | tests only | — (no test sources yet) |
| `build` | Gradle, dependencies, CI | — |
| `ci` | GitHub Actions | `ci: run the build on main, and keep the jar` |
| `chore` | anything that fits nowhere else | — |
| `revert` | reverting a commit | `revert: pin the browser surface ...` |

### Rules

- **Subject**: imperative mood ("add", not "added"/"adds"), lowercase after the colon, no trailing
  period, aim for 50 characters and never past 72.
- **Body**: wrap at 72. Say **why**, not what — the diff already says what. A body that only restates
  the subject is noise; a body that records the dead end is gold (see `c10745a` and `0b52645`, the
  pin and its un-pinning, which are deliberately both in history).
- **Scope**: optional, and this repo rarely uses one. When a change is naturally one of the known
  areas, prefer it: `window`, `notice`, `config`, `save`, `browser`, `extractor`, `save`.
- **Footers**: `Refs #12` or `Closes #12` for issue references; a `BREAKING CHANGE:` footer (with a
  `!` after the type) when a config key moves or a command disappears — none yet.
- **One commit, one change.** A commit that lands a feature and its revert-able mistake is fine; a
  commit that lands a feature and three unrelated fixes is not. `docs:` commits that fix a sentence
  the previous commit got wrong are fine and preferred over rewriting history, because this repo's
  notes are the record.

## Versioning

The version lives in exactly one place: `gradle.properties`.

```properties
version=0.9.0+26.2
```

- `MAJOR.MINOR.PATCH` is [semver](https://semver.org/). `PATCH` bumps for fixes, `MINOR` for
  player-visible features, `MAJOR` when something a config or a command depends on breaks.
- `+26.2` is the Minecraft version, semver build metadata. It is **not** part of the release version:
  the tag and the GitHub release are `vMAJOR.MINOR.PATCH` (`v0.9.0`), never `v0.9.0+26.2`.
- `0.x` means pre-1.0 by the owner's rule: **1.0 is the gate for the whole ROADMAP backlog**, and
  0.9.0 is a waypoint, not a promise.

## The release runbook

Runbook used for 0.9.0, in order. Every step is a real command; nothing here is optional except the
notes file, which is still worth writing.

1. **Bump the version** in `gradle.properties` and commit it:
   ```bash
   git add gradle.properties
   git commit -m "build: version 0.9.0+26.2"
   ```
2. **Write the CHANGELOG.md entry** under `## [Unreleased]` → rename to `## [0.9.0] - YYYY-MM-DD`,
   following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Group Added / Changed / Fixed;
   describe what changed for a player, not the commit list. Commit as `docs:`.
3. **Prove the build**:
   ```bash
   ./gradlew clean build check
   ```
   Green or it does not ship. There are still no test sources, so `check` compiles and lint-checks
   only — the hands-on list from the most recent session note has to be green too. **This is the
   gate**: CI only runs on releases (deliberately — see [CI](#ci) below), so nothing else stands
   between a commit and a tag except this command.
4. **Merge to main.** `main` is the default branch (an older workflow still says `master`; treat that
   as a bug, not a convention):
   ```bash
   git checkout main
   git merge --no-ff feature/my-branch -m "Merge 'feature/my-branch' for 0.9.0"
   ```
5. **Push main**:
   ```bash
   git push origin main
   ```
   Nothing runs on the push. Creating the release is the next thing that does.
6. **Tag and release with the GitHub CLI**, letting it create the tag at main's HEAD:
   ```bash
   gh release create vX.Y.Z --title "..." --notes-file .release-notes.md
   ```
   The notes file is the CHANGELOG section with the `## [X.Y.Z] - date` header removed, plus the
   compare link, which is the last line GitHub shows and the fastest way to see what actually changed:
   ```markdown
   **Full Changelog**: https://github.com/CoderMan73/craftlatro/compare/v<previous>...vX.Y.Z
   ```
   For the first release, where there is no previous tag, compare from the root commit instead
   (`git rev-list --max-parents=0 HEAD`) — 0.9.0's link is exactly that.
7. **CI attaches the jar** — there is no manual upload step. The workflow
   (`.github/workflows/release.yml`) builds the tagged source and attaches the jar with the token
   GitHub provides, so what a player downloads is what that tag builds. Watch it:
   ```bash
   gh run watch $(gh run list --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')
   ```
8. **Record it**: add the release link to `CHANGELOG.md`'s `[0.9.0]` header and, if anything about the
   runbook turned out to be wrong, fix this file in the same pass.

## CI

GitHub Actions runs **only on a published release** (`.github/workflows/release.yml`), so a push, a
merge or a PR costs no runner time. That is a deliberate trade: the thing that needs building is the
release artifact, so that is the only thing that gets built. The price is that no commit is verified
automatically — which is why step 3 is the gate and why it is a `clean build check` and not just a
`build`.

**When the release workflow fails**, here is exactly what has and has not happened:

- The release **exists**. Publishing it is what triggers the workflow, so the tag, the notes and the
  release page are already there.
- The release has **no jar**. The upload is the workflow's last step, and it only runs after a green
  build, so nothing partial or broken is ever attached. A player sees a release page with no
  download, and the Actions tab shows a red run naming the Gradle failure.
- Fix forward. Either attach a locally built jar (prove it locally first):
  ```bash
  ./gradlew clean build check
  gh release upload vX.Y.Z build/libs/craftlatro-X.Y.Z+26.2.jar
  ```
  or fix the code, re-cut the release, and let the workflow attach it:
  ```bash
  gh release delete vX.Y.Z --yes && git push origin :refs/tags/vX.Y.Z
  git tag vX.Y.Z && git push origin vX.Y.Z
  gh release create vX.Y.Z --title "..." --notes-file .release-notes.md
  ```

If the repo ever takes outside contributions, add a `pull_request`-only build workflow back before
merging someone's first PR: `CONTRIBUTING.md` currently tells contributors to open one, and nothing
checks it. The removed workflow was a valid template — its only sin was also running on every push.

## Boundaries that survive this runbook

- **Creating a remote repository, pushing tags, and cutting releases are Ask First** — see AGENTS.md.
  The 0.9.0 release was authorized in an explicit 2026-10-13 message; treat that as the precedent for
  asking, not as standing permission.
- **Never release a commit that is not on `main`**, and never tag anything other than the merge point
  the runbook produced.
- **Never commit the jar, generated data, or anything under `~/.craftlatro`.** The jar is a build
  artifact; releases are where it belongs.

## The 0.9.0 release

Shipped 2026-10-13 from `feature/autonomous-backlog` (22 commits, `eb46d2f`..`51774d9`), merged to
`main` with a `--no-ff` merge commit so the branch is one reviewable unit. Highlights: the framed
window, the Cloth Config settings screen, save export/import plus `save clear`, the resolution notice
strip, render scale up to 200%, and the move of every file the mod writes into the Minecraft instance.

The notable *non*-release fact for whoever cuts 0.9.1: two commits in that range (`c10745a`, `0b52645`)
land and then undo a browser-surface pin that crashed the client natively. They are kept, not
squashed, because the crash evidence is the reason the current rule exists. See
`ai-docs/notes/2026-10-13-lovejs-canvas-sizes-at-page-load.md`.
