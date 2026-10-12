The craftlatro love.js pipeline is proven end-to-end: Balatro runs inside Minecraft via Rinku, confirmed by a measured client run on 2026-10-12. This note records the four compounding bugs that blocked it, why the earlier "pivot" decision was wrong, and the tuning knobs that now exist.

# love.js pipeline: proven, and what it took

Status: **working as of 2026-10-12.** Confirmed by a full `./gradlew runClient` session — Balatro
loads, plays, closes, and reopens. The concept in `PLAN.md` is validated; no architectural pivot is
needed (see `decisions/pivot-from-lovejs.md`, which is now resolved/superseded).

## The four-bug stack

Getting Balatro on screen required fixing four independent bugs. Each one hid the next, and the
first three were misread as a single architectural failure.

### 1. ZIP data descriptors (the original blocker)
`src/client/java/com/craftlatro/client/extractor/LoveExtractor.java`

Java's `ZipOutputStream` writes a **data descriptor** (local header flag bit 0x08) for DEFLATED
entries, which pushes the real CRC/size/sizes behind the entry's data. love.js's loader is compiled
C (Emscripten/PhysFS) and **cannot parse that layout**. `bit.lua` was simply the first such entry, so
`module 'bit' not found` appeared long after the actual corruption.

Verified with a standalone `ZipFlagDiag` probe: `gpFlag=0x808` (dataDescriptorBit3 set) for DEFLATED
entries, `0x800` for STORED entries written with explicit sizes.

**Fix:** repack `.love` with `STORED` entries whose sizes and CRC are written into the header. The
output is larger but PhysFS-readable. The bundled Lua shims in
`src/client/resources/assets/craftlatro/lua/` then resolved normally.

### 2. love.js IndexedDB cache
`src/client/java/com/craftlatro/client/extractor/GameLauncher.java`,
`src/client/java/com/craftlatro/client/server/LocalHttpServer.java`

The love.js `player.js` sets `Player.cache = true`. Its Emscripten IndexedDB package cache is keyed
**only on the `?g=` query value** and never re-fetches, so HTTP `Cache-Control` cannot evict it. A
correct archive on disk was still being ignored in favor of the stale cached one.

**Fix:** version the request. `ensureIndexHtml` rewrites `index.html` unconditionally and points
`player.js` at `<version>/game.love`, where `<version>` is the mtime of `game.love`. A new version is
a new cache key, forcing a fresh fetch. `LocalHttpServer.CoopCoepFileHandler` remaps
`<version>/game.love` back to `game.love` on disk, and `index.html` is sent `no-store`.

### 3. Offscreen paint rate (the "locked to 30 fps")
`src/client/java/com/craftlatro/client/browser/BalatroBrowser.java`

Rinku creates browsers without `CefBrowserSettings`, so the Chromium offscreen surface inherited
Chromium's default windowless frame rate — which is lower than Minecraft's own framerate limit. The
game inside ran at roughly half the frame rate of everything around it and read as "pinned".

**Fix:** `CefBrowser_N.setWindowlessFrameRate(int)` is public and inherited by `RinkuBrowser`, so the
rate is now set explicitly on every viewport change. Config default 120, tunable at runtime with
`/craftlatro fps <10-240>`. It is additionally **capped to Minecraft's own measured fps**, because
Chromium painting faster than Minecraft composites is discarded work — Rinku keeps only the newest
paint, so frames between two composites are thrown away unshown.

There is no `windowless_frame_rate` in `CefSettings` (global) in this JCEF build; it is a
per-browser `CefBrowserSettings` field and is set at runtime via the host method above.

### 4. Screen size never propagated on window resize
`src/client/java/com/craftlatro/client/browser/BrowserScreen.java`

The browser surface stuck at whatever size it started at. Root cause was subtle: `Screen`'s own
`width`/`height` fields only refresh when the **screen is rebuilt**, so enlarging the game window
never reached the browser. `/craftlatro reload` happened to fix it because it rebuilds.

**Fix:** read the logical size from `minecraft.getWindow().getGuiScaledWidth()/getGuiScaledHeight()`
instead of the screen fields, and re-check the target size every frame in `extractRenderState`.
Mouse coordinates are mapped through the same ratio. This also made `/craftlatro render <scale>`
apply live.

## Architecture worth knowing

`BalatroBrowser` is a process-lifetime singleton that **outlives the screen**. Closing the screen
detaches rather than closes, so the Lua session survives and reopening is instant (no re-extraction,
no love.js reboot). Two consequences shape the design:

- Because Rinku pumps pending paint frames for every live browser on the Minecraft render thread
  regardless of screen state, an unseen full-rate surface would cost frames. So while detached the
  surface is **parked**: audio muted via `setAudioMuted(true)`, paint rate cut to 5 fps, surface
  shrunk to 320x180.
- Leaving a world **releases** the browser outright (`CraftlatroClient.onClientTick` watches
  `client.level`), so no audio or Lua session survives between worlds. The local HTTP server is left
  running deliberately — it is a stateless localhost socket that would otherwise churn.

## Diagnostics

`/craftlatro stats` toggles an on-screen overlay and copies a clipboard report, and logs the same
report to the console every 10 seconds. The measure that mattered was **`chromium paints/s`**, which
counts real paint callbacks via a `CefBrowserOsr.addOnPaintListener` counter. That figure is what
distinguished "browser is capped" from "you are comparing 60 to 120".

The report separates `target` from `applied` paint rate so the cap is observable. Measured steady
state: Minecraft 136-137 fps, surface 131-140 paints/s, paint gap ~6.8 ms (i.e. 144 Hz).

Clipboard goes through the GLFW window (`Window.handle()` → `glfwSetClipboardString`). The AWT path
fails in a dev launch because the JVM is headless, and that failure was originally swallowed.

## Measured result

Balatro runs, plays, and survives open/close cycles within one browser session. Leaving the world
releases it and rejoining creates a fresh one. Window resizes propagate correctly. The remaining
cost is inherent: Minecraft's fps drops from ~144 to ~130 with the browser open at `render scale`
1.0, because Chromium shares the GPU.

## Tuning knobs (all runtime, all persisted)

| Command | Effect |
|---|---|
| `/craftlatro render <0.25-1.0>` | Offscreen resolution fraction. Largest perf lever — cost scales with pixel count. |
| `/craftlatro fps <10-240>` | Chromium paint rate. Auto-capped to Minecraft's own fps. |
| `/craftlatro stats` | Toggle stats overlay + clipboard report. |
| `/craftlatro reload` | Drop the session; next open starts a fresh page. |
| `/craftlatro open` / `pick` / `load` | Open game / pick exe / load a path. |
| `/craftlatro warn` | Re-show the fidelity warning. |

Config persists at `~/.craftlatro/config/craftlatro.properties`. Defaults: renderScale 0.75,
browserFrameRate 120, statsOverlay off.

## Open work (not done, deliberately parked)

- `render scale` still defaults to 0.75 in code but the persisted config was set to 1.0 during
  testing; confirm which a fresh install gets.
- No automated tests (`./gradlew check` has no sources). AGENTS.md Testing section is still TODO.
- The `pivot-from-lovejs.md` ADR was left stale and misleading until this session; the lesson is in
  that file.
- Docs: `docs/` contains PROMPT_GUIDE.md and rinku-api-notes.md but no user-facing build/run guide.
- `src/main/java` (common init, `CraftlatroMod`) vs `src/client/java` (all real code) — AGENTS.md
  Key Directories only mentions `src/main/java`, which is now inaccurate.
