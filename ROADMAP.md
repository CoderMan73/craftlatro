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

## Aspirational Goals

- A reusable, loader-agnostic "browser-as-a-screen" helper extracted from the Rinku integration.
- Save-game handoff between the love.js IndexedDB profile and a Minecraft-side config so progress
  persists across launcher sessions (see M4).

> Guidance from `references/roadmap-guide.md`. Populate from PLAN.md before first release.
