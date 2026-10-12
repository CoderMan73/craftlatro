Autonomous backlog pass landed windowing, save export/import and a Cloth Config settings screen on branch `feature/autonomous-backlog`; verified by compilation plus a headless save-store smoke test, and everything in-game still needs a launch.

## Problem

Three backlog items were open against the proven love.js pipeline (ROADMAP.md > Backlog), all UX
rather than architectural: the browser surface had no window chrome of any kind, progress was
locked inside the Chromium profile, and settings were only reachable by knowing `/craftlatro` syntax.
The pipeline itself was not allowed to be destabilised, and `./gradlew runClient` is off-limits because
the Balatro screen only opens on a real input event, so no change here can be verified in-game by an
agent.

Work happened on `feature/autonomous-backlog`, cut off `main` at commit `56bce0f`, with `main`
untouched and nothing pushed (pushing is an Ask First item in AGENTS.md). Three commits:
`eb46d2f` (window), `a9aebcf` (saves), and the config work. `./gradlew build` is green at each.

## 1. Window UX — one drag-and-resize widget, not three

Problem: the viewport was an implicit fullscreen panel behind an opaque backdrop (`fill(0, 0, ...)`
over the whole GUI viewport), so nothing was grabbable and the world was hidden entirely. Wanted: a
movable window, a resizable window, and see-world-at-once, which are one interaction rather than
three.

Solution: new `src/client/java/com/craftlatro/client/browser/BrowserWindow.java` (390 lines) holds all
window state, and `BrowserScreen.java` delegates to it.

- Geometry: `titleBarX/Y`, `titleButtonX(index)`, `interiorX/Y/Width/Height()`. All GUI-scaled
  pixels, which is the space the browser texture is blitted into. `resizeMaskAt(x, y)` returns a
  bitmask of `EDGE_LEFT | EDGE_RIGHT | EDGE_BOTTOM`, so any edge and any corner grabs.
- Chrome: `render(...)` paints the title bar, an outline around the whole window, and the clipped
  "Balatro" title; `TITLE_BUTTON_COUNT = 3` buttons at `TITLE_BUTTON_SETTINGS (0)`,
  `TITLE_BUTTON_MODE_TOGGLE (1)`, `TITLE_BUTTON_CLOSE (2)`.
- Drags: `beginMove` / `beginResize` / `dragTo` / `endDrag`. Moving is clamped so the title bar always
  stays on screen; resizing enforces `MIN_WIDTH 240` / `MIN_HEIGHT 200` and the current viewport, and
  the left edge moves `x` as well as shrinking `width` so the right edge stays put under the cursor.
- Modes: `toggleFramed(...)` flips between the persisted framed rectangle and a viewport fill;
  double-clicking the title bar and the `M`/`R` button both call it. `isFramed()` gates whether
  `isPauseScreen()` says the world keeps running.
- Persistence: `CraftlatroConfig` gained `windowMode`, `windowWorldRunning`, `windowX/Y/Width/Height`
  (nine accessors, `parseInt` parse-once in the constructor). Geometry is written back only on drag
  end and mode toggle — deliberately not per frame, or a drag would write the file every tick.
- Clamping: `applyViewport(...)` re-applies whichever mode is active, `clampIntoViewport(...)` keeps
  the rectangle reachable when the GUI scale or game window shrinks underneath it. Called from
  `syncViewport()` and from the new `resize(...)` override, because screen fields go stale on resize.

`BrowserScreen` changes (11 hunks): window field replaces the margin math; `init()` calls
`window.restore(...)` and builds the buttons at window coordinates; `updateTitleButtons()` follows the
window every frame; `syncViewport()` drives `browserPixelWidth()/Height()` from the window interior so
live drags resize the surface; `resize(...)` and `isPauseScreen()` overrides; `mouseMoved`,
`mouseClicked`, `mouseScrolled`, and the new `mouseDragged`/`mouseReleased` all bounds-check against
`isInBrowserBounds`; a `browserButtonDown` flag stops a release being sent when no press was
forwarded; `mouseClicked` returns `true` for title-bar and edge grabs before the browser press path.
Fullscreen keeps the old behaviour exactly: a whole-viewport backdrop plus an 8px margin.

World ticking decision (recorded, per the backlog's "a decision on whether the world still ticks"):
default `false`, i.e. the world pauses as it always did. Reason: a screen captures all input, so a
running world can damage the player while they cannot react; the world still renders behind the
window either way, so see-world-at-once is unaffected. Flipping it is one property,
`windowWorldRunning=true`.

Input fix that came with it: `CraftlatroClient.onClientTick` used `openBalatroKey.isDown()` /
`selectExeKey.isDown()`, so holding `B` rebuilt the screen every tick (each rebuild called
`window.restore`, resetting a window the player had just moved and re-`init`-ing the browser) and
holding `F9` queued one Swing file picker per tick. Both now `consumeClick()`.

## 2. Save import/export over the Chromium store

Problem: progress lives in the browser's IndexedDB inside Rinku's persistent profile
(`%LOCALAPPDATA%\Rinku\cef-cache` — resolved by `CefUtil.resolvePersistentCefCachePath` from
`LOCALAPPDATA`/`APPDATA`/`user.home`/`XDG_DATA_HOME` with a `Rinku/cef-cache` tail; the constant pool
of the published Rinku jar was read to confirm this rather than guessing). Nothing in the mod owned
that directory. The backlog also flagged that love.js keys its IndexedDB package cache on the `?g=`
value, so a version bump can orphan entries — an export therefore has to record what it was taken
against.

Solution: new `src/client/java/com/craftlatro/client/save/SaveStore.java` (572 lines).

- `browserCacheRoot()` discovers the directory instead of assuming it, and falls back to the first
  candidate path when nothing exists yet so a save can be transferred onto a machine that has never
  run the game.
- `dataDirectories(root)` walks for `IndexedDB` (depth 4, nearest first).
- `export(name)` drops the browser session (`BalatroBrowser.release()`) before zipping, because a
  snapshot of a LevelDB-backed store taken while the page is live can be torn. Output
  `~/.craftlatro/saves/<sanitized name>.zip` with `indexeddb/<relative path>/...` entries plus a
  `meta.json` recording `format`, `gameVersion` (the `?g=`, i.e. the mtime of `game.love` via
  `GameLauncher.currentGameVersion()`), `exportedAt`, `storeRoots`, `fileCount`. `Local Storage` is
  deliberately excluded — only the love.js save store is exported.
- `importStaged(nameOrPath)` validates the format, extracts under a `staging/<millis>-<nanos>`
  directory (forward-slash normalisation, and a `startsWith(staging)` reject for path traversal),
  writes a `pending-restore.json` marker, and drops the live session. It does **not** touch the live
  store.
- `applyPendingRestore()` runs from `CraftlatroClient.onInitializeClient()`, before Rinku
  initializes CEF, and is retried from the client tick while it returns `false` (bounded at 60
  attempts). It backs the current store up to `saves/backups/auto-<millis>.zip` first, then
  `replaceStore(...)` copies into a `*.incoming` directory next to the target, renames the live store
  to `*.replaced`, moves the new one in, and puts the old one back if that move throws — so a failure
  cannot leave a half-written store.
- `listExports()` returns `SaveSummary(name, exportedAtMillis, gameVersion, fileCount)`, newest first.
- Commands: `/craftlatro save export <name>`, `save import <file>`, `save list`. `CraftlatroClient`
  gained `rebindOpenBrowserScreen()`, which reattaches any open `BrowserScreen` to a fresh session
  (`BrowserScreen.rebind()`), because the export/import path drops the browser out from under one.

Verification beyond compilation, because this is file logic with no browser in it: the real
`SaveStore.java` source was compiled with `BalatroBrowser` and `GameLauncher` stubbed and run against
fake profiles in a temp home with an overridden `LOCALAPPDATA` (JDK 21; the mod targets 25 but the
class version is irrelevant to the logic). 28 assertions covered cache-root discovery, store
enumeration, name sanitisation, zip contents, meta parse, `listExports`, staging, marker lifecycle,
restore over a wiped profile, restore over a populated profile (contents fully replaced), a
zip-slip reject, and the auto-backup contents. That run caught a real bug: `browserCacheRoot()`
originally returned `null` when no profile existed, so on a fresh machine the restore could never
apply and a transferred save was silently lost.

Not verified and needs a launch: whether the store contains what the player expects (singleplayer
runs, wins, unlocks), whether dropping the session actually flushes the last Emscripten write before
the zip is taken, and whether love.js's IDBFS survives a `?g=` bump. The `?g=` mismatch currently
warns via `save list` rather than blocking — a one-line change to block instead, left as a decision.

## 3. Config screen via Cloth Config

Tradeoff researched and recorded, then implemented as chosen: Cloth Config over YACL (both publish
26.2 artifacts — `cloth-config-fabric:26.2.155`, YACL `3.9.7+26.2-fabric`) and over a vanilla
widget-only screen. Chosen because the API is a straight builder over the existing
`CraftlatroConfig`, YACL's `ValueList`/builder surface maps to the same entries but is shaped around
`ConfigCategory` objects rather than a builder, and a vanilla screen would have re-implemented
sliders, toggles, tooltips and scrolling for no gain. Library is LGPLv3, which is compatible with the
GPL-3.0 mod, and no game or runtime code is bundled.

- `build.gradle`: added `maven.shedaniel.me` and `maven.terraformersmc.com/releases` repositories,
  `implementation "me.shedaniel.cloth:cloth-config-fabric:${project.cloth_config_version}"`, and
  `compileOnly("com.terraformersmc:modmenu:${project.modmenu_version}") { transitive(false) }`. Plain
  `implementation` rather than `modImplementation` because this project already uses plain
  `implementation` for fabric-api and rinku — Loom 1.18.4 with Mojang mappings needs no remap of a
  Mojmap-published jar, and `modImplementation` does not exist in this setup.
- `gradle.properties`: `cloth_config_version=26.2.155`, `modmenu_version=20.0.3`.
- `src/main/resources/fabric.mod.json`: `"cloth-config": "*"` in `depends`; a `"modmenu"` entrypoint.
- New `src/client/java/com/craftlatro/client/ui/CraftlatroConfigScreen.java`: `ConfigBuilder.create()`
  with `setDoesConfirmSave(false)` (entries save themselves), two categories, and entries that write
  straight through to `CraftlatroConfig` via `setSaveConsumer`. Window mode is a
  `startEnumSelector` over a private `WindowMode` enum with `setEnumNameProvider` for
  `Windowed`/`Full screen` labels; render scale is a 25–100 int slider mapped back to the 0.25–1.0
  double; the rest are boolean toggles. Text-description rows explain that the executable is still
  `/craftlatro pick`, that the commands still work, and that love.js fidelity is not guaranteed.
- New `...ui/CraftlatroModMenuApi.java` implements `ModMenuApi` and returns
  `CraftlatroConfigScreen::create`, so Mod Menu shows the button when present.
- `src/client/resources/assets/craftlatro/lang/en_us.json`: 20 keys (`config.craftlatro.*`).
- `CraftlatroClient`: `/craftlatro config` opens the screen; `BrowserScreen` gained a third
  title-bar button `S` that opens it as well. All previous commands are untouched.
- AGENTS.md: Cloth Config + Mod Menu rows in the tech-stack table and both new commands in the
  runtime-command table.

The two ROADMAP cases, resolved rather than left open:

1. **Applying a change while `BrowserScreen` is not open.** The config object is the single source of
   truth and is read fresh wherever it is consumed, so nothing is applied "from the render loop" as
   such. `syncViewport()` reads render scale and frame rate every frame, so an open window picks a
   change up immediately. When no window is open there is no loop to pick it up, so the change lands
   on the next open — which is why the tooltips say so explicitly. `windowWorldRunning` is read from
   `isPauseScreen()`, which the framework calls when the screen is created, so it too applies on the
   next open. Window mode applies on the next open for the same reason.
2. **Game-package changes still needing a full session release.** Left as `/craftlatro pick`, which
   already calls `browserScreen.rebind()` when the screen is open; the config screen deliberately
   does not offer a package switcher, and its copy says why.

Command surface was **not** collapsed, per the instruction to keep every existing command working;
the settings screen is presented as the supported path and the screen's own description row lists
the commands that remain.

## What was verified and how

- `./gradlew clean build --rerun-tasks` and `./gradlew build` green after every change. Nothing else
  was run.
- `./gradlew build` jar inspected: `CraftlatroConfigScreen.class` and `CraftlatroModMenuApi.class`
  present, `fabric.mod.json` in the jar carries the `modmenu` entrypoint and `cloth-config` dependency,
  and no `me/shedaniel`, `com/terraformersmc` or `blue/endless` class is bundled.
- The headless `SaveStore` smoke run (28 assertions, all passing) described above.
- Cloth Config API use was checked against the local source at
  `E:\Coding_Projects\MCSourceCode\cloth-config` (26.2 line, `gradle.properties`
  `minecraft_version=26.2`) and validated by compiling against the published 26.2.155 jar.

## 4. Window chrome fixes after the first hands-on

The first real screenshot came back from the user with two defects, both of which a compile-only pass
could not see and both of which the ASCII simulations below would have caught earlier:

1. **The border looked "weird".** The interior was not inset by the border, so `BrowserScreen`
   painted the opaque browser surface over the left, right and bottom borders: only the title bar
   survived, and the window read as a floating rectangle with no frame. `interiorX/Y/Width/Height`
   are now inset by `BORDER_WIDTH` on every side, so the 2px ring is visible on all four edges. The
   same pass found that `titleLimit` clipped the window title against the *right-most* button
   (`TITLE_BUTTON_CLOSE`) instead of the left-most (`TITLE_BUTTON_SETTINGS`), so a long title would
   have run under the settings button.

2. **The three buttons existed but had no icon.** They were 16x16 vanilla `Button`s with one-letter
   labels, which reads as a speck of nothing in a screenshot — the user's words were "it would be
   impossible for the user to know they exist". Replaced with `TitleBarButton` (new,
   `...browser/TitleBarButton.java`), which extends `AbstractWidget` because
   `AbstractButton.extractWidgetRenderState` is `final` and a `Button` subclass cannot draw anything
   but text. It keeps vanilla press, sound, focus, hover and tooltip behaviour
   (`AbstractWidget.extractRenderState` is final but calls `extractWidgetRenderState` and then pushes
   the tooltip) and draws pixel-art icons at 18x18: three sliders for settings, a window for expand,
   two overlapping windows for restore, an X for close. The title bar grew from 18px to 22px to hold
   them.

A third bug fell out of the same reasoning: the resize grip was drawn inside `BrowserWindow.render`,
which runs *before* the opaque browser surface, so it was invisible. It now renders from
`BrowserScreen` after the `blit`, via a public `BrowserWindow.renderResizeGrip`.

Both fixes were checked by simulating the drawing arithmetic as ASCII
(`IconArt`, `ChromeArt`, throwaway) and reading the 18x18 / 34x64 grids, which is how the covered-away
highlight and the clipped title were spotted. The rule worth keeping: **anything drawn before the
browser `blit` is covered wherever the surface is opaque**, so chrome is either outside the surface's
rectangle or drawn after it.

`localRuntime` was added for Mod Menu (`build.gradle`) so the dev client has it and the config button
can be exercised locally; Cloth Config was already on the runtime classpath via `implementation`, and
`./gradlew dependencies --configuration runtimeClasspath` was used to confirm both are there.

## What could NOT be verified, and why

Anything requiring a real launch, which is the standing constraint. Specifically: that the settings
screen actually renders and saves (the compile proves the API calls resolve; the layout, scrolling
and save flow are untested), that dragging and resizing feel right and that the world is visible
outside the framed window, that a save export round-trips real progress, and that the
`modmenu` entrypoint loads without Mod Menu installed (Fabric ignores an unknown entrypoint key, and
`compileOnly` keeps it out of the jar, but neither was exercised).

Skipped deliberately, not missed: the custom mod icon, pipeline testing from a clean
`~/.craftlatro/`, generifying into a library, demo media, other Minecraft versions and NeoForge.

## What a future session must decide

- Whether `/craftlatro save list` should keep warning on a `?g=` version mismatch or refuse the
  import (`SaveStore.importStaged` has the comparison available and currently only logs).
- Whether an export should force an Emscripten flush before zipping. Today it drops the session and
  hopes the last write landed; a page-side flush signal would need browser-side work.
- Whether `windowWorldRunning` should default to `true` once someone has actually played through a
  windowed session.
- Whether to collapse `/craftlatro render|fps|stats` now that the screen covers them, which would
  break the muscle memory of anyone already using the commands.
