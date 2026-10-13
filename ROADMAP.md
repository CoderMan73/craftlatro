# craftlatro Roadmap

## Project Goals

- Run the genuine Balatro game (1-to-1 logic: all decks, stakes, jokers, blinds, profiles) from
  inside Minecraft Java Edition, by composing an existing browser-embed library (Rinku) with
  the love.js web port — not by reimplementing Balatro's rules.
- The player supplies their own Balatro.exe; the mod extracts its `.love`, packages it with the
  2dengine love.js player, and serves it to an in-game Chromium browser on a flat GUI screen.
- Never bundle or distribute Balatro code/assets; ship only the integration glue
  (extraction/serving, in-game screen, input mapping).

## Project Non-Goals

- Reimplementing Balatro's jokers, decks, stakes, blinds, or RNG by hand (this is integration
  glue, not a clone — rejected path: LuaJ + from-scratch LÖVE API).
- Bundling Balatro code/assets or the Chromium/love.js runtime in the mod jar.
- Java Edition is the only target (not Bedrock/C++ add-ons).

## Shipped

- [x] M1: Scaffolding and dependency integration — Fabric 1.26.2 mod project with Loom, Rinku 3.0.4-26.2 dependency
  resolves and compiles. `docs/rinku-api-notes.md` documents the full public API surface. A flat GUI browser
  screen (`BrowserScreen`) is scaffolded using Rinku's `RinkuBrowser` rendering and input mapping.

## Current Objectives

- [x] Scaffolding and dependency integration (M1, done)
- [x] Balatro.exe -> love.js package pipeline (M2)

The pipeline extracts the `.love` archive from the user's Balatro.exe (which is a LOOVE game
packaged as a ZIP), starts a `com.sun.net.httpserver` local HTTP server with COOP/COEP headers,
and serves the game bundle. File picker via JFileChooser (F9 key or `/craftlatro pick`), direct
path via `/craftlatro load <path>`, and browser via `/craftlatro open` or B key.
See `src/client/java/.../extractor/` and `src/client/java/.../server/`.
- [x] In-game screen surface (M3)

The flat Fabric GUI screen (`BrowserScreen`) hosts the Rinku browser view with:
  - Deferred initialization via `Rinku.scheduleForInit()` (waits for Rinku to finish loading)
  - Browser texture rendering via `RenderPipelines.GUI_TEXTURED` + `GuiGraphicsExtractor.blit()`
  - Full mouse (click/drag/wheel/move) and keyboard input mapping to `RinkuBrowser`
  - Loading indicator bar while `browser.isLoading()`
  - `reload(url)` for updating the browser when a game is loaded via M2
  - Proper cleanup in `onClose()` (closes browser, nulls reference)
  - ESC key handling, null-safe input guards, `tick()` retry for browser creation
The in-world 3D/computer-block surface remains deferred per PLAN.md.

**Known issue**: The Chromium runtime (~150 MB) is fetched at first launch by Rinku. The mod
jar stays small (38 KB) — no runtime is bundled.
- [x] Controls, UX, and fidelity checks (M4)

  - Chat link flow: pressing B without a loaded game sends a clickable chat message
    `[Click here to select Balatro.exe]` that runs `/craftlatro pick` (opens JFileChooser).
  - First-run warning: `FidelityWarningScreen` shows love.js fidelity gaps (RNG/shader/audio
    non-parity, Chromium runtime download) before the player selects Balatro.exe.
  - Keybind/command entrypoints: F9, `/craftlatro pick`, `/craftlatro load <path>`,
    `/craftlatro open`, `/craftlatro warn`.
  - Save persistence: game files extracted to `~/.craftlatro/game/` persist across sessions;
    the HTTP server serves the same bundle, and love.js IndexedDB (per `http://localhost:18080/`)
    persists save data within the Chromium profile cache.
  - love.js player autodelete: `LoveJsDownloader` fetches the 2dengine love.js web player
    files (player.js, style.css, 11.5/love.js, 11.5/love.wasm, lua/normalize1.lua, lua/normalize2.lua)
    from `raw.githubusercontent.com/2dengine/love.js/main/` (MIT License). Cached in game dir,
    only downloaded if files are missing.
  - Steam auto-detection: `SteamLocator` scans default Steam library paths and parses
    `libraryfolders.vdf` for user-installed libraries (covers multi-disk setups).
    Falls back to JFileChooser (with headless-mode guard) or a chat-suggested `/craftlatro load <path>`
    command if the file picker is unavailable.
- [x] Packaging and legal guardrail (M5)

  - Verified the built jar (`craftlatro-1.0.0+26.2.jar`) contains only 24 entries: mod classes,
    `fabric.mod.json`, language file, icon, and license. No Rinku, JCEF, love.js, or Balatro
    assets are bundled.
  - Rinku is declared as a `depends` entry in `fabric.mod.json` and resolved from the Keksuccino
    Maven at runtime; the ~150MB Chromium runtime is Rinku's responsibility (fetched to
    `rinku-libraries/jcef-v1/` cache, not the mod jar).
  - love.js web player (2dengine, MIT License) is downloaded from GitHub at runtime by
    `LoveJsDownloader` to `~/.craftlatro/game/`, not bundled in the jar.
  - Balatro.exe is user-supplied only; extracted .love is stored at `~/.craftlatro/game/`
    (runtime data, not the repo/jar).

## Planned

- (M1-M5 are the current objectives; see PLAN.md for the detailed breakdown.)

## Out of Scope

- In-world 3D / computer-block display surface — deferred to a later milestone (CONCEPT.md
  resolved decisions; M3 targets the flat GUI only).
- Minecraft Bedrock / add-on integration.
- Running Balatro's native `.exe` directly without the love.js web port.

## Backlog (unscheduled)

Ideas raised by the project owner on 2026-10-12. **None are commitments** — this is a parking lot,
not a plan. Nothing here has been scoped, prioritized, or agreed; treat each as a candidate that needs
its own discovery pass before implementation. Items marked ⚠ touch an AGENTS.md boundary.

### Testing
- [ ] **Pipeline test from nothing to running** — end-to-end verification that a fresh install
      (empty instance data, no cached Chromium, no cached love.js) reaches a playable Balatro.
      The current proof is one successful `runClient` session against a pre-populated config, which
      does not cover first-run paths: love.js download, `~150 MB` Chromium fetch, Steam
      auto-detection, or the file-picker fallbacks. There is still no automated test suite
      (`./gradlew check` has no sources), so this is manual harness work or a first real test effort.
- [ ] **Test save import/export end to end** — flagged by the owner 2026-10-13, never exercised by
      anyone. Export, import, restart, and confirm the run state actually comes back; the version
      caveat in `SaveStore`'s javadoc (`?g=` cache keys can orphan a store) is unverified without it.
      Also test `/craftlatro save clear`.
- [ ] **Code review of the whole repo** — flagged by the owner 2026-10-13. Nobody has reviewed the
      window work, the config screen, the save store, or the notice path against each other.

### Packaging / reuse
- [ ] **Generify into a "browser-as-a-screen" library** — extract the Rinku + HTTP-server + session
      management glue (`BalatroBrowser`, `LocalHttpServer`, `BrowserScreen`) from Balatro-specific
      concerns (`LoveExtractor`, `GameLauncher`, the Lua shims) so other games/runtimes can be ported
      to Minecraft via the same path. This subsumes the old "reusable loader-agnostic helper"
      aspiration and is the largest item on this list; it is an architecture change, not a refactor.
      Owner's follow-up (2026-10-13): prove it by porting a second game to Minecraft with it.

### Audio
- [ ] **Browser volume slider** — flagged by the owner 2026-10-13: browser volume from 0 to 200%.
      Blocked on API surface, not on design: `CefBrowser` exposes only `setAudioMuted`/`isAudioMuted`,
      no volume. The real path is PCM capture — JCEF's `CefAudioHandler` (`onAudioStreamPacket`
      delivers raw frames, and Rinku's `RinkuClient` dispatches to registered handlers) — scaled in
      Java and played through an output this mod owns (LWJGL OpenAL, since Minecraft's sound engine
      has no streaming entry point). Needs: access to the `RinkuClient` instance to register a handler
      (not currently reachable from `RinkuBrowser`), the CEF opt-in that actually starts delivery, an
      audio thread that survives the browser's own output being muted, and a decision on whether
      200% just clips. Note Balatro also has its own in-game volume settings.

### Commands
- [ ] **Review whether the command tree still earns its keep** — flagged by the owner 2026-10-13: the
      config screen has absorbed most of the tuning, so `/craftlatro render`, `fps` and `stats` are
      near-duplicates of config entries. Decide what stays (the file/path commands have no UI
      equivalent: `pick`, `load`, `save export/import/list/clear`) and what should be dropped or
      folded into the settings screen before 1.0.

### Release
- [x] **Release 0.9.0** — shipped 2026-10-13: `v0.9.0` on
      <https://github.com/CoderMan73/craftlatro/releases/tag/v0.9.0> with the jar attached, from `main`
      at `d9cd48c`. The repo now carries the conventions it was released under:
      `docs/commits-and-releases.md` (Conventional Commits, `gradle.properties` versioning, the release
      runbook), `CHANGELOG.md` (Keep a Changelog), and the fixed CI workflow. Still open for the *next*
      release: a changelog entry written as you go rather than reconstructed at the end, and the
      Ask First boundary on pushing tags/releases — 0.9.0 was authorized by an explicit message, which
      is the precedent for asking, not standing permission.
- [ ] **Release checklist for repeated builds** — first-run clean-install test (above), a documented
      "does the jar run in a real launcher" step, and the save import/export test wired into it.

### Window UX
- [x] **Resizable window** — the browser surface is currently sized to the full GUI viewport minus a
      fixed 8px margin, so there is nothing to grab or adjust. *(done 2026-10-12: eb46d2f, e01d157)*
- [x] **Movable window** — no title bar / drag region exists; the surface is implicitly fullscreen.
      *(done 2026-10-12: eb46d2f)*
- [x] **See Minecraft and the window at once** — related to both above: a framed, non-fullscreen
      window so the 3D world stays visible and interactive. Would need the world to keep rendering
      behind the panel and a decision on whether the world still ticks while the game has focus.
      Note the current screen fills an opaque backdrop over the world, which would be inverted here.
      *(done 2026-10-12: eb46d2f, f2cfc39; 2026-10-13: a9459d0 made full screen fill the viewport
      edge to edge)*
- [x] These three interact and are probably one piece of work (a draggable/resizable panel widget),
      not three. Worth scoping together. *(scoped and shipped as one piece, as predicted)*

### Save data
- [x] **Save import/export** — surface the love.js IndexedDB store as portable files so progress
      survives launcher/profile changes and can be shared. Absorbs the older "save-game handoff
      between IndexedDB and a Minecraft-side config" aspiration. Caution: love.js's IndexedDB cache
      is keyed on the `?g=` version (see AGENTS.md gotcha), so export/import has to be aware that a
      version bump can orphan stored data. *(done 2026-10-12: a9aebcf)*

### Presentation
- [ ] **General visual improvements** — flagged by the owner 2026-10-13, deliberately unscoped. Known
      candidates from this session: the window chrome and the notice strip were built by feel, the
      title bar has no close/minimize affordances beyond the four buttons, the loading state is a
      single animated bar, and nothing in the window has ever been looked at next to a real Minecraft
      screen. Needs a design pass, not a list.
- [ ] **README overhaul** — flagged by the owner 2026-10-13: "needs to look cooler". The README is
      currently an architecture document; it should lead with what the mod does, then how to run it,
      then the internals. Combine with the demo-media item below.
- [ ] ⚠ **Demo media for the README** — animated demos/screenshots of the mod running. Keep captures
      to Minecraft + the mod's own UI; do not record or commit Balatro's own artwork, sprites, or
      logos, since the repo's Boundaries rule forbids bundling Balatro assets. Also note the
      in-game stats overlay (`/craftlatro stats`) can produce the numbers-but-not-images content that
      makes a demo convincing.
- [ ] ⚠ **GitHub Pages landing page** — flagged by the owner 2026-10-13: a public-facing page to
      advertise the mod. Needs a remote repository first, which is an Ask First boundary in AGENTS.md,
      so this is gated on the owner creating the remote, not on the page design. Same asset rules as
      the demo media: Minecraft and the mod's own UI only.

### Portability
- [ ] **Other Minecraft versions** — today the mod is pinned to `mc 1.26.2` via Rinku
      `3.0.4-26.2`, so a port is mostly a dependency swap, but it must be validated per target version
      (Rinku publishes per-MC artifacts, and the flat-GUI API surface used here is documented for
      this one). Requires confirming Rinku availability for each target before committing to it.
- [ ] **NeoForge** — a second loader target. Fabric-specific pieces (`fabric.mod.json`,
      Fabric Loader entrypoints, ClientLifecycleEvents, ClientCommandRegistrationCallback) would need
      NeoForge equivalents, while the browser/pipeline/core classes could be shared. This is the
      second-largest item after the library extraction, and the library extraction above would make
      it considerably cheaper.

### Config surface
- [ ] **Custom mod icon** — `src/main/resources/assets/craftlatro/icon.png` is a 488-byte
      placeholder. Minecraft renders this in the Mods list and on the title screen, so this is a
      one-file art task at MC's expected icon size rather than code work.

- [x] **Standard config library** — replace the in-game tuning commands with a normal config screen
      so players edit values through a supported UI instead of learning `/craftlatro` syntax. Today
      every setting lives in `CraftlatroConfig` backed by `craftlatro.properties`
      (`~/.craftlatro/config/`), so the storage layer would carry over; the work is a presentation
      layer plus a save trigger. Needs a dependency decision first — the usual Fabric options are
      Cloth Config or YACL, both open-source and compatible with the repo's GPL-3.0 license, but a
      **hard** dependency forces every player to install them. The other route is Mod Menu's entry
      point, which keeps the config lib optional and greys the button when absent. That tradeoff is
      the research to do before picking. *(done 2026-10-12: be6fd5a — Cloth Config as a hard
      dependency, Mod Menu compile-only, per AGENTS.md's stack table)*

- [ ] **Collapse the commands to "open config"** — the owner flagged the risk that this changes
      on-the-fly tuning, and it is a real question worth answering before building. What appears
      answerable from the shipped code: `render scale` and `fps` both already apply live, because
      `BalatroBrowser.showViewport`/`observeFrameRate` push new values into the running browser on
      the next frame (`setWindowlessFrameRate` and `resize` are both runtime calls), and the
      window-resize fix proved `resize` applies without a page reload. What is *not* obviously live
      is applying a change while `BrowserScreen` is not open, since the apply path is driven from
      the screen's render loop, and anything tied to the game package (changing/relacing a `.love`)
      still needs `BalatroBrowser.release()` — a full session drop. Verify those two cases before
      promising a config screen that "just works".



> Guidance from `references/roadmap-guide.md`. Populate from PLAN.md before first release.
