# 2026-10-13: the game canvas sizes itself at page load, so the browser surface is pinned

Reported hands-on 2026-10-13: toggling the Balatro window to full screen still left the game blurry,
and the in-game Video settings still showed the resolution the game had picked while windowed
(`1090 x 570` in the player's session). This note records the two attempts and what ships now.

## What is not the problem

The offscreen Chromium surface and the GUI blit both scale correctly. The diagnostics captured with
the window in full screen read `browser surface: 1920x1014` against `screen viewport: 960x529 at gui
scale 2` — that is the interior width (960 GUI) x 2 and (529-22 title bar) x 2, so
`BrowserScreen.syncViewport` -> `BalatroBrowser.showViewport` -> `RinkuBrowser.resize`
(`browser_rect_.setBounds` + `wasResized`, see RinkuBrowser.java:541 in
`E:\Coding_Projects\MCSourceCode\Rinku`) is live and exact. The blur lives inside the page.

## Root cause: love.js sizes its canvas once

The served page (player, glue and styles are what `GameLauncher` writes to `~/.craftlatro/game/`)
sizes the game canvas at page load only:

- `style.css` gives `#canvas { width: 100%; height: 100% }`, so the canvas *box* follows the
  viewport, while the canvas *pixel buffer* (`canvas.width/height`) is set once by the engine.
- `player.js` has no resize handling at all, and `11.5/love.js` (the Emscripten glue, 448 KB) only
  calls `Browser.setCanvasSize` from `resizeForWindowed`/`resizeForFullscreen` — the DOM-fullscreen
  paths, not ordinary window resizes.
- The engine imports `emscripten_set_resize_callback_on_thread` (verified by string search over
  `11.5/love.wasm`), and LÖVE itself does not re-create its drawing buffer on window resize — it
  stretches the canvas it booted with instead.

So a viewport change re-lays the page out but leaves the engine's fixed-size canvas stretched over
its new box. Balatro even picks its canvas from a discrete resolution list that fits the viewport it
loads at, which is why the game's own Video menu showed the windowed-size resolution afterwards.

## Attempt 1: re-dispatch the resize event — did not work

`showViewport` ran a small snippet dispatching `resize` on `window`, `document` and `#canvas` after
every OSR resize, on the theory that the Emscripten callback the engine registers would re-create
the canvas. The player's test (windowed -> full screen) disproved it: the canvas stayed at its
load-time size, so both belief and code were removed rather than left as a placebo.

## What ships: the surface follows the window, and growth reloads the page

Pinning the surface to the monitor's video mode — create the browser at 1920x1080 so the page picks
its largest canvas once and the window is only a view onto that surface — was implemented first and
**crashed the client twice, deterministically**, always about a second into the page load:

```
EXCEPTION_ACCESS_VIOLATION (0xc0000005) ... Problematic frame: C [jemalloc.dll+0x31870]
RinkuBrowser.lambda$new$0(AsyncPaintFrame)          <- memFree(frame.buffer())
AsyncResourceLeaseManager.releaseResource / runLease / drain
RinkuBrowser.pumpAsyncPaintsOnRenderThread
```

An invalid free in Rinku 3.0.4's async paint path, i.e. a paint buffer the render thread drains and
frees is already corrupted or oversized. The same crash came back after reverting the birth resize,
so the call path was never the trigger: sizes every previous session had run safely (~1500x800
windowed, ~1880x980 fullscreen) never crashed, and the pin (1920x1080) crashed 2/2. Rinku is a
prebuilt dependency, so the trigger cannot be fixed from here, and the pin was abandoned rather than
size-tuned blind — `run/hs_err_pid15784.log` and `hs_err_pid4532.log` are the two reports, the only
native crashes this project has produced, and both are worth reporting to Rinku upstream.

What ships instead never asks for a size that has not already run safely:

- The surface is sized from the window interior again (`BrowserScreen.browserPixelWidth/Height`),
  exactly as before this whole thread, so every size is one the client has already painted at.
- When the surface grows well past the size the page was loaded at (more than 10% on either axis,
  once the size holds still for 400ms — so a drag reloads once, at the end), the page is reloaded:
  the game re-creates its canvas at the new size and renders sharp instead of stretched. Shrinking
  reloads nothing, because the same canvas shown downscaled stays sharp.
- A reload costs the running game — the page restarts at the Balatro menu with the save intact, the
  same thing the refresh button does. That is the price of the game rendering at the resolution the
  window actually has.

**Not verified hands-on** — reload-on-growth needs the player to open windowed, toggle to full
screen, and confirm the page reloads once and the game renders sharp at the new size. The
consequences of the pinned-size crash are recorded in AGENTS.md.

## First attempt: the birth resize, which crashed natively

The pin first shipped through `Rinku.createBrowser(url, transparent, width, height)`, which creates
the browser at the pinned size so the page is born at it. That crashed the client (see the reports
below), so the birth resize was reverted. The crash returned, which is what ruled the pin out
entirely rather than just that one call.

## Also changed in the same pass (2026-10-13)

- Full screen fills the viewport edge to edge, with the title bar as its only chrome pinned to the
  top of the viewport (`BrowserWindow.fillViewport`, `interiorX/Width/Height`,
  `renderFrame`/`renderTitleBar`). The previous fullscreen look kept an 8px margin and a 2px border
  ring.
- The stats overlay no longer says `craftlatro diagnostics`; it is headed `browser diagnostics`,
  matching the eventual library shape.
