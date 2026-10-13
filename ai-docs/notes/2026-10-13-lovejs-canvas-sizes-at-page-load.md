# 2026-10-13: the game canvas sizes itself at page load, so a live browser resize alone goes blurry

Reported hands-on 2026-10-13 after the window work: toggling the Balatro window to full screen fills
the viewport, but the game inside stays blurry, and only a restart of the client restores the
resolution the game receives. This note records why, and the fix that ships for it.

## Symptom, and what it is not

The offscreen Chromium surface already follows the window live: the diagnostics captured with the
window in full screen read `browser surface: 1880x978` against `screen viewport: 960x528 at gui
scale 2`, i.e. the OSR paint size tracks `BrowserWindow.interiorWidth() * guiScale` (see
`BrowserScreen.syncViewport` -> `BalatroBrowser.showViewport` -> `RinkuBrowser.resize`, which is
`browser_rect_.setBounds` + `wasResized` in Rinku's source at
`E:\Coding_Projects\MCSourceCode\Rinku\common/src/main/java/de/keksuccino/rinku/RinkuBrowser.java`).
So `browser.resize` is live and correct — the blur is inside the page, not in the Rinku surface or
the GUI blit.

## Root cause: love.js sizes its canvas once

The served page (player, glue and styles are what `GameLauncher` writes to `~/.craftlatro/game/`)
sizes the game canvas at page load only:

- `style.css` gives `#canvas { width: 100%; height: 100% }`, so the canvas *box* follows the
  viewport, while the canvas *pixel buffer* (`canvas.width/height`) is set once by the engine.
- `player.js` has no resize handling at all, and `11.5/love.js` (the Emscripten glue, 448 KB) only
  calls `Browser.setCanvasSize` from `resizeForWindowed`/`resizeForFullscreen` — the DOM-fullscreen
  paths, not ordinary window resizes.
- The engine itself does import `emscripten_set_resize_callback_on_thread` (verified by string
  search over `11.5/love.wasm`), so LÖVE registers an Emscripten resize callback and can be told
  about a new size.

A viewport change therefore re-lays the page out but leaves the engine's fixed-size canvas
stretched over its new box: blurry until the next page load, which is exactly what the player saw
("only a restart makes the resolution change").

## What now happens

`BalatroBrowser.showViewport` re-dispatches the browser resize event at the page after every OSR
resize (`CANVAS_RESIZE_SCRIPT`, dispatched on `window`, `document` and `#canvas`). If the engine's
registered callback reads the viewport it re-creates the canvas at the new size and the game
renders at the resolution it actually has; if a page ignores it, dispatching loses nothing. It
rides along for free with every size change, so window drags resize the game's canvas live rather
than at restart.

**Not yet verified hands-on** — an agent cannot open the Balatro screen (see the verification
limits in `notes/2026-10-12-lovejs-pipeline-proven.md`), so this needs the player to drag or
toggle the window and confirm the game sharpens without a restart. The escalation if the dispatch
proves insufficient is a page reload once a resize settles (`browser.reload()`), which certainly
re-creates the canvas but ends the running game — deliberately not shipped until asked.

## Also changed in the same pass (2026-10-13)

- Full screen no longer keeps an 8px margin and 2px border ring: it fills the viewport edge to
  edge, and the title bar stays as its only chrome, pinned to the top of the viewport
  (`BrowserWindow.fillViewport`, `interiorX/Width/Height`, `renderFrame`/`renderTitleBar`).
  `FULLSCREEN_MARGIN` became `WINDOW_MARGIN` (the margin a framed window starts at).
- The config screen split stayed: `Pause on Browser open` (the inverse of the stored
  `windowWorldRunning`, no tooltip) and `Open key is a toggle` moved from the Craftlatro category
  to Browser > General; the Craftlatro category now holds the install instructions and the
  fidelity disclaimer only.
