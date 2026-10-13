# 2026-10-13 (evening): session handoff — 0.9.1

Everything a new agent needs to pick this repo up, in reading order. Everything is on **`main`** and
**pushed**; the feature branch is merged and has nothing left on it. Releases published this session:
**[0.9.0](https://github.com/CoderMan73/craftlatro/releases/tag/v0.9.0)** and
**[0.9.1](https://github.com/CoderMan73/craftlatro/releases/tag/v0.9.1)** (current).

## What changed since the mid-session handoff

- **0.9.0 released** (tag `v0.9.0`, main@`d9cd48c`), then **0.9.1** (`v0.9.1`, main@`8e5df84`) as a
  patch whose content is the CI repair plus release automation.
- **CI works for the first time in this repo's history.** Three separate defects had kept every run
  red: the workflow's first line was `name=build` (not YAML, so runs died with zero jobs), the Gradle
  wrapper jar was in `.gitignore` (nothing for a clone to launch), and `gradlew` had no executable bit
  on Linux. Fixed in that order; the runs since are green in ~50-70s.
- **Release automation**: `.github/workflows/release.yml` builds the tagged source on a published
  release and uploads the jar with the token GitHub provides. 0.9.1's asset was attached by it, not
  copied from a laptop. Creating a release is still a human decision.
- **The conventions are codified**: `docs/commits-and-releases.md` (Conventional Commits, the
  `gradle.properties` version line, the release runbook with 0.9.0 as the worked example),
  `CHANGELOG.md` (Keep a Changelog), a section in `AGENTS.md`, and the `CONTRIBUTING.md` commit rules
  updated to match what the history actually does.
- **The lesson worth keeping**: CI is a convenience, not a release gate. The release artifact is the
  local jar, and one cache-cold run hung in the Build step for six minutes before it was cancelled —
  that must not hold a release hostage, and the runbook now says so.

## Read these first, in this order

1. `AGENTS.md` — the role, the stack table, the runtime command table, the gotchas. The gotchas
   section is load-bearing: the love.js `.love` repack rule, the `?g=` cache key, and the
   pinned-surface crash below are all there so they are not re-derived.
2. `ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md` — how the pipeline actually works, the four
   bugs behind it, `BalatroBrowser` as a screen-agnostic singleton, and the verification limits.
3. `ai-docs/notes/2026-10-13-lovejs-canvas-sizes-at-page-load.md` — the whole resolution thread: why
   the game's canvas cannot follow a window resize, the two native crashes, what ships now.
4. `ROADMAP.md` > Backlog — everything not done, each with enough context to be scoped.
5. `PLAN.md` (tracking workspace, `E:\Coding_Projects\future-projects\public\active\craftlatro`) —
   the milestone table and the status sections.

## What the player sees now, that they did not this morning

- A framed, movable, resizable Balatro window over the world (title bar, four buttons, aspect lock,
  optional blur); full screen fills the viewport edge to edge and keeps only the title bar.
- A Cloth Config settings screen (`/craftlatro config` or the `S` button) split into Browser
  (General / Performance) and Craftlatro (installation instructions + fidelity disclaimer).
- Save export / import / list, plus `/craftlatro save clear [confirm]` to erase the game's save.
- A notice strip when the window settles somewhere >10% off the size the page was loaded at, offering
  Reload page / Ignore, because the game's canvas is fixed at page load (details in note 3 above).
- Render scale from 25% to 200%, where >100% renders above the window and is capped at the window's
  own full-screen size.
- All files now live inside the Minecraft instance: `config/craftlatro.properties`,
  `config/craftlatro/saves/`, `<instance>/craftlatro/game`. `~/.craftlatro` is migrated on first read
  by `CraftlatroPaths` — one class owns every path, so changing the layout is a one-file edit.

## State of the repo

Everything is on `main` and pushed; `feature/autonomous-backlog` was merged `--no-ff` and holds
nothing further. `main`'s HEAD is the v0.9.1 tag. Commits worth knowing, oldest first:

| Commit | What |
|---|---|
| `eb46d2f` | framed, movable, resizable window (the whole window work follows from it) |
| `a9aebcf`, `be6fd5a` | portable saves; the Cloth Config settings screen |
| `a9459d0` | edge-to-edge fullscreen, settings text pass |
| `c10745a` | the surface pin that crashed (kept for history; do not resurrect) |
| `0b52645`, `7e17b24` | unpin after the crash; two-arg browser creation restored |
| `d820586`, `f648f03` | notice strip: offers a reload instead of doing it |
| `32d6d4d` | bounded manual resolution (render scale to 200%) + notice padding/centering |
| `d94175f` | notice keeps its explanation at the smallest window size |
| `59e406c` | instance-local paths, `save clear`, 0.9.0 bump |
| `98cae02` | the conventions codified |
| `60c1236`, `949d404`, `711e5b8`, `18adafd` | the four CI repairs (YAML key, wrapper jar, exec bit, action inputs) |
| `d9cd48c` (v0.9.0), `8e5df84` (v0.9.1) | the releases |

`.\gradlew.bat clean build check` passes locally; the same command passes in CI on every push to
`main`. There are still no test sources — `check` compiles and lint-checks only.

## What is verified, and what is not

**Verified**: compilation, and the hands-on checks the player has been running each session — the
window, the settings screen text, the notice strip at small and large sizes, and the full-screen
surface size reading 1920x1014 in the stats overlay.

**Not verified, and an agent cannot verify these** (browser changes need a real `runClient` with a
player driving it):
- The notice strip end to end: appearance at a >10% change, Reload page actually re-creating the
  canvas at the new size, Ignore staying gone, the stacked two-row layout at the window's floor.
- Render scale above 100%: whether a reload after changing it really makes the game pick its larger
  canvas.
- `/craftlatro save clear`, both paths (live page, and no session at all).
- The `~/.craftlatro` -> instance migration, which is the thing most likely to surprise on first
  launch: if it fails, the mod silently starts from defaults and the player loses settings.
- First-run from a truly clean instance (no cached Chromium, no love.js) — never done by anyone.

## The crash, in one paragraph, because it will otherwise be re-attempted

love.js/Balatro sizes its game canvas once, at page load, and never follows a browser resize — the
effect was blur whenever the window ended up bigger than it loaded at. Pinning the browser surface to
the monitor's video mode crashed the client twice, deterministically, ~1s into page load: an
`EXCEPTION_ACCESS_VIOLATION` in `jemalloc.dll` under Rinku 3.0.4's `AsyncResourceLeaseManager.drain`,
an invalid free of a paint buffer. Sizing from the window interior never crashed, at any size, across
many sessions; the one variable was a surface bigger than the window. Rinku is a prebuilt dependency,
so the bug is not fixable from this repo — it is a Rinku bug and worth reporting upstream with the two
`run/hs_err_pid*.log` files. The rule that ships is therefore "never bigger than the window's own
full-screen size", which is what caps render scale and why growth offers a reload instead of forcing
one.

## Suggested next moves, in order

1. Get the unverified list above in front of the player (they run `runClient`; we cannot).
2. Testing pass: save export/import/clear round trip, and the first-run clean-install path.
3. Review the command tree — the config screen absorbed most tuning, so several commands are now
   duplicates; decide what stays before 1.0.
4. Browser volume slider: needs PCM capture (`CefAudioHandler`, JCEF has it; Rinku dispatches to
   registered handlers but the `RinkuClient` instance is not reachable from `RinkuBrowser`) plus an
   audio output of our own (LWJGL OpenAL). Scoped in ROADMAP.md > Audio.
5. Library extraction, proven by porting a second game; then the rest of the release path
   (README, landing page), with 1.0 as the gate the owner set.
6. Write the changelog entry as work lands. 0.9.1's entry was reconstructed afterwards, which is why
   the last item in its section is a note to itself.

## Cutting the next release, concretely

The full runbook is `docs/commits-and-releases.md`; the short version for 0.9.2 or 1.0:

```bash
# 1. version + changelog entry, commit as build:/docs:
#    gradle.properties: version=0.9.2+26.2
# 2. prove it locally
./gradlew clean build check     # or .\gradlew.bat clean build check
# 3. push main, wait for CI if you like (it is not a gate)
git push origin main
# 4. create the release; CI attaches the jar itself, do not upload it by hand
gh release create v0.9.2 --title "0.9.2" --notes-file .release-notes.md --target main
```

The tag is `vMAJOR.MINOR.PATCH` — never `vX.Y.Z+26.2`, which is only the jar's file name. Creating a
remote repo, pushing tags and cutting releases are Ask First in AGENTS.md; this session's two releases
were authorized by explicit messages, which is the precedent for asking.
