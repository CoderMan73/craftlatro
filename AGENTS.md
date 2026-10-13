# AGENTS.md - craftlatro

## Agent Role

Specialist maintainer of **craftlatro**. Prioritize correctness of the integration glue, keep
third-party/externally-supplied content out of the repo (see Boundaries > Never), and verify
each milestone against PLAN.md (tracked in the tracking workspace, not in this repo).

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Fabric Loader + API | 1.26.2 | Mod loader for Minecraft Java Edition |
| Java | 21 | Language/runtime (MC 1.26 / Rinku requires Java 21+) |
| Gradle | Fabric Loom | Build system |
| Rinku | 3.0.4 (Chromium 151.0.7922.34) | Embedded Chromium browser via `de.keksuccino:rinku-fabric:3.0.4-26.2` (Maven: https://keksuccino.github.io/maven/) |
| Cloth Config | 26.2.155 (LGPLv3) | Settings screen for `CraftlatroConfig`; hard dependency, declared in `fabric.mod.json` (Maven: https://maven.shedaniel.me/) |
| Mod Menu | 20.0.3 (compile only) | Optional: exposes the settings screen as its config button. Not a runtime dependency |
| love.js | 11.5 (2dengine player) | Web port of LÖVE; runs the user's extracted `.love` |

## Key Commands

```bash
./gradlew genIntellij  # Generate IDE run configs (Fabric Loom)
./gradlew build        # Build the mod jar
./gradlew runClient    # Run a Minecraft client with the mod
./gradlew check        # Run tests (none yet)
./gradlew idea         # Alternative IDE config task
```

## Architecture

Integration glue only — the mod does **not** reimplement Balatro. Data flow at runtime:

1. `Balatro.exe` (user-supplied, recorded via chat link / keybind / command) -> mod extracts the
   `.love` (reuse of the web-balatro / Balatro-Web-Port extraction approach).
2. Mod bundles the `.love` with the love.js (2dengine) player into a served bundle.
3. A minimal local HTTP server (`java com.sun.net.httpserver`) serves the bundle with
   COOP/COEP headers on `http://localhost:<port>` (required: love.js 2dengine player won't run
   from `file://`).
4. Rinku v3.0.4 renders the local URL in an embedded Chromium on a flat Fabric GUI screen.
5. Minecraft keyboard/mouse input is mapped into the embedded browser view.

Key directories:
- `src/main/java` - Common init only (`com.craftlatro.CraftlatroMod`)
- `src/client/java` - All client code: Rinku screen, HTTP server, `.love` extraction, file-picker UX
- `src/client/resources` - `fabric.mod.json` assets, key bindings, Lua compatibility shims
- `ai-docs/` - AI field notes, decisions, lessons learned across sessions
- `docs/` - Human-facing documentation only

## Project status

**The concept is proven and Balatro runs in-game** (confirmed 2026-10-12). The love.js pipeline in
the Architecture section above is working end to end. See
`ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md` for the four bugs that were required to get
there and the runtime tuning commands. Do not re-litigate the approach — the earlier pivot decision
in `ai-docs/decisions/pivot-from-lovejs.md` was wrong and is marked resolved.

Remaining work is UX/optimization only (config polish, default tuning, a user-facing run guide, and
a client test harness). No architectural work is open.

## Code Style and Conventions

- Java 21, standard Minecraft modding conventions (Fabric Loom). Write self-documenting code;
  prefer explanatory method names over inline comments. The one exception is non-obvious
  behavior (e.g. COOP/COEP header necessity, love.js compatibility quirks) which the guidelines
  elsewhere already mark as a gotcha.
- Functional style where practical; avoid deeply nested Minecraft client event handlers.

## Testing

- **Framework**: none configured yet (Fabric mod; M1 will evaluate a client-side test harness).
- **Run all**: `./gradlew check`
- **Coverage target**: _TODO (set in M1)_

## Boundaries

### Always
- Keep externally-supplied content (e.g. Balatro code/assets, the LÖVE runtime) out of the
  repo and out of the mod jar.
- Record non-obvious findings in `ai-docs/decisions/` or `ai-docs/notes/`.

### Ask First
- Creating a remote repository or pushing tags/releases.
- Changing the project license or adding dependencies with non-open runtimes.

### Never
- Bundle third-party game code/assets or redistributable runtimes in the jar or repo.
- Hardcode paths or credentials; keep every file the mod writes inside the Minecraft instance (settings in `config/`, generated data under the instance root). `CraftlatroPaths` is the single place that resolves them, and it migrates the old `~/.craftlatro` layout on first read.

## Context Loading

| Task | Read |
|------|------|
| Start work | PLAN.md (tracking workspace: `E:\Coding_Projects\future-projects\public/active/craftlatro/PLAN.md`), AGENTS.md, this file |
| Runtime docs | `docs/` |
| Field notes | `ai-docs/` |

**Before touching anything in the browser/pipeline code**, read
`ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md`. It records the four compounding bugs behind
the love.js pipeline, the architecture of `BalatroBrowser` as a screen-agnostic singleton, the
runtime command table, and the verification limits (notably: an agent cannot open the Balatro
screen without the user driving the game, so browser changes must be handed back to them to check).

Ideas awaiting implementation are parked in `ROADMAP.md` > Backlog. None are scoped or committed.

## Runtime commands

All tuning is available in-game and persists to `config/craftlatro.properties`, inside the Minecraft
instance (the `~/.craftlatro` location the mod used before is migrated on first read; see
`CraftlatroPaths`):

| Command | Effect |
|---|---|
| `/craftlatro config` | Open the Cloth Config settings screen (also the `S` button on the Balatro window, and Mod Menu's config button when Mod Menu is installed) |
| `/craftlatro open` | Open the Balatro screen |
| `/craftlatro pick` | Choose a Balatro.exe |
| `/craftlatro load <path>` | Load an exe from a path |
| `/craftlatro warn` | Re-show the fidelity warning |
| `/craftlatro render <0.25-2.0>` | Offscreen resolution multiple (largest perf lever; >1 renders above the window and is capped at the window's full-screen size) |
| `/craftlatro fps <10-240>` | Chromium paint rate |
| `/craftlatro stats` | Toggle stats overlay + copy a diagnostics report |
| `/craftlatro reload` | Drop the browser session; next open starts fresh |
| `/craftlatro save export <name>` | Copy the browser's IndexedDB store to `config/craftlatro/saves/<name>.zip` |
| `/craftlatro save import <file>` | Stage a saved store for restore; applies at the next game restart |
| `/craftlatro save list` | List exports with the game version each was taken against |
| `/craftlatro save clear [confirm]` | Erase the game's save data (confirm required; reloads the page) |

`B` opens the screen once the game is loaded. Leaving a world releases the browser session.

The Balatro window is a real window: drag its title bar to move, drag any edge to resize,
double-click the title bar to expand to the full viewport, and the world stays visible around it
while framed (it is paused, not ticking, unless `windowWorldRunning` says otherwise).

## Gotchas

- **Rinku's flat-GUI browser API is fully usable** (RESEARCH.md unknown area 1, resolved at M1).
  `Rinku.createBrowser(url, transparent)` + a `Screen` that blits `getTextureIdentifier()`. See
  `docs/rinku-api-notes.md`. Rinku's own
  `common/src/main/java/de/keksuccino/rinku/example/ExampleScreen.java` is the reference pattern.
- **love.js's loader cannot read Java-produced ZIP data descriptors** (PhysFS is compiled C). This
  is the single most expensive lesson in this repo: a `.love` must be repacked with `STORED`
  entries and explicit sizes/CRC, and the symptom (`module 'bit' not found`) appears long after the
  actual corruption. See `LoveExtractor` and
  `ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md`.
- **love.js's `player.js` sets `Player.cache = true`** and keys its IndexedDB package cache only on
  the `?g=` value, never re-fetching. Server `Cache-Control` cannot evict it. Version the `?g`
  parameter (mtime of `game.love`) instead; see `GameLauncher.ensureIndexHtml`.
- **love.js/Balatro sizes its game canvas once, at page load, and never follows a browser resize**
  (verified hands-on 2026-10-13: resizing the offscreen surface only stretches the old canvas, and
  re-dispatching the browser resize event does not re-create it). The surface therefore follows the
  window live, and `BrowserScreen` offers a page reload when the window settles somewhere
  substantially different from the size the page was loaded at (10% on either axis, either direction,
  400ms of stillness): a strip along the top of the window reads "Screen resolution changed. Reload
  page to apply." with Reload page / Ignore. Both buttons are the player's, never automatic, because
  a reload restarts the game. Do **not** replace this with a surface pinned to the monitor's video
  mode: that crashed the client natively twice, deterministically, inside Rinku's async paint path
  (`jemalloc.dll` AV under `AsyncResourceLeaseManager.drain`, `run/hs_err_pid15784.log`,
  `hs_err_pid4532.log`), at sizes the window-sized surface had painted at safely for sessions. See
  `ai-docs/notes/2026-10-13-lovejs-canvas-sizes-at-page-load.md`.
- **Never size a freshly created Rinku browser through `Rinku.createBrowser(url, transparent, width,
  height)`.** Resizing a browser inside that overload crashed the client natively — an
  `EXCEPTION_ACCESS_VIOLATION` in `jemalloc.dll` under `AsyncResourceLeaseManager.drain` in Rinku's
  async paint path (`run/hs_err_pid15784.log`, 2026-10-13, the only native crash this project has
  produced). Create the browser two-arg and resize it through `BalatroBrowser.showViewport` on the
  same frame instead: that is what every clean session before the pin used, and it lands before the
  page's canvas is created anyway.
- **Rinku never sets the Chromium offscreen paint rate**, so that surface runs at Chromium's default.
  `CefBrowser_N.setWindowlessFrameRate()` is public and inherited by `RinkuBrowser`. Paint faster than
  Minecraft composites is discarded work — Rinku keeps only the newest paint.
- **Rinku pumps paint frames for every live browser on the Minecraft render thread regardless of
  screen state.** A browser left open while no screen shows costs frames; park it (mute + 5 fps +
  shrink) rather than closing it. `setWindowVisibility(false)` exists but is untested here.
- **The AWT clipboard is unavailable in a dev launch** (headless JVM). Use `Window.handle()` +
  `glfwSetClipboardString` for anything clipboard-related.
- love.js (2dengine player) won't run a `file://` page, so the local HTTP server with COOP/COEP
  headers is mandatory (RESEARCH.md Gap 5). Compatibility mode (`-c`) is the fallback.
- love.js fidelity is not 1-to-1 with native Balatro: RNG/shader/audio differ and seed parity
  is not guaranteed (RESEARCH.md Gap 3). Warn the player in-game on first run.
- The Chromium runtime is ~150 MB and is Rinku's responsibility, fetched at runtime — do not
  bundle it in the mod jar.
- love.js requires COOP/COEP headers if served to a browser.
- The in-world 3D/computer-block surface is deferred (CONCEPT.md resolved decisions); M3 is
  the flat GUI only (RESEARCH.md Gap 4).

## Local Skills

This project may define repo-local skills in `.agents/skills/`. When starting a task, scan
this folder for skills whose descriptions match the current work.

- **Discovery**: Check `.agents/skills/` at task start.
- **When to use**: Any skill whose description matches the task at hand.
- **When to suggest**: Recurring, repeatable processes worth standardizing.
- **When NOT to suggest**: One-off decisions, single-use observations (use ai-docs/).

## ai-docs

This project uses an `ai-docs/` subdirectory for field notes, architectural decisions, and
lessons learned. See `ai-docs/README.md` for guidelines on recording knowledge between
agent sessions.

> Guidance from `references/agents-guide.md`.
