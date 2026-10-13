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

## What ships: pin the surface at the game window's largest resolution

`BrowserScreen.captureReferenceSurface()` reads the primary monitor's video mode once per screen
open (falling back to the current framebuffer), and the browser surface is created and kept at that
size times the render scale — no longer at the window interior's size. `Rinku.createBrowser(url,
transparent, width, height)` creates the browser at the pinned size so the page picks its largest
canvas while loading. Consequences:

- A smaller window shows the whole render downscaled, which stays sharp; growing the window to
  full screen maps the pinned surface 1:1 instead of stretching a small canvas. That is the whole
  point of the pin, and it is what removes the restart dependency.
- The window is now only a view onto the surface: `extractRenderState` blits the whole texture into
  the window rectangle and the mouse mapping already scales through the same bounds, so nothing
  about drag/resize changed — only the size the surface is created at.
- The cost is the payment: the browser paints the pinned resolution at *every* window size, where
  before a small window painted small. Render scale (which now scales the pin) and the repaint rate
  are the levers, and both already apply live.
- Parking still shrinks the surface to 320x180 for the frames no screen is showing; unparking grows
  it back with the canvas intact, because the canvas never followed a resize anyway.

**Verified**: the build compiles. **Not verified hands-on** — the pin needs the player to confirm
that a fresh windowed open now shows the game's largest resolution in its Video menu and that a
full-screen toggle stays sharp. The escalation if the pin is too heavy is to cap the pin below the
monitor mode (e.g. the current framebuffer at open), trading full-screen sharpness for cost.

## First attempt crashed natively — the birth resize is off-limits

The pin first shipped through `Rinku.createBrowser(url, false, width, height)`, which creates the
browser at the pinned size so the page is born at it. The client crashed on the first windowed open
(`run/hs_err_pid15784.log`, the only native crash this project has produced):

```
EXCEPTION_ACCESS_VIOLATION (0xc0000005) ... Problematic frame: C [jemalloc.dll+0x31870]
RinkuBrowser.lambda$new$0(AsyncPaintFrame)          <- memFree(frame.buffer())
AsyncResourceLeaseManager.releaseResource / runLease / drain
RinkuBrowser.pumpAsyncPaintsOnRenderThread
```

An invalid free inside Rinku 3.0.4's async paint path (`AsyncResourceLeaseManager`, the CEF paint
thread offering cloned frames that the render thread drains and frees). Not reproducible from
outside a live client, and ours is not the double free: `showViewport`/`park` only ever resize from
the render thread, which is the thread that drains. What *is* ours is the novel call the crash
needs: that overload resizes the browser inside `Rinku.createBrowser`, before the caller can attach
anything to it (`Rinku.java:195-204`), and the previous sessions' construction — create browser
two-arg, then resize through `showViewport` on the same frame — had never crashed. So the pin now
ships that way, and the page still loads at the pinned size because the `.love` fetch takes far
longer than the frame in which the resize lands.

If the client crashes again natively in `AsyncResourceLeaseManager`, the trigger is the pinned size
itself (1920x1080 paints instead of ~1500x800) and not this call path; step the pin down (cap at the
current framebuffer at open) and report the crash to Rinku upstream either way.

## Also changed in the same pass (2026-10-13)

- Full screen fills the viewport edge to edge, with the title bar as its only chrome pinned to the
  top of the viewport (`BrowserWindow.fillViewport`, `interiorX/Width/Height`,
  `renderFrame`/`renderTitleBar`). The previous fullscreen look kept an 8px margin and a 2px border
  ring.
- The stats overlay no longer says `craftlatro diagnostics`; it is headed `browser diagnostics`,
  matching the eventual library shape.
