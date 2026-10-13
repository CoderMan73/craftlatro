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

## 5. Render-order bug that hid every title-bar button

The second screenshot came back with the window chrome correct but the buttons still invisible — and
the user's sharpest observation was that the hitboxes were clickable and functional while nothing
drew. That combination pointed straight at layering rather than at the widget itself.

`Screen.extractRenderState` iterates the `renderables` list, and `BrowserScreen` called
`super.extractRenderState(...)` as its **first** statement, so the buttons were drawn and then
immediately painted over by the title bar and the opaque browser surface. The vanilla `Button`s in
the first screenshot had never rendered either — the three specks the user annotated were their own
red circles marking invisible hit areas, not rendered buttons.

The fix is one line moved: `super.extractRenderState(...)` is now the **last** statement of the
override, so widgets draw on top of the chrome and the surface. The buttons sit in the title bar,
outside the surface's rectangle, so nothing of the game is covered. The rule is the same one the
resize grip already had to obey, and it is now stated once in the note.

No GUI library was needed for this — it was never a library problem, it was ordering. A library such
as Cloth Config would not have helped, because the same `extractRenderState` contract applies to any
widget.

## 6. Three feature requests from the second hands-on

- **16:9 aspect lock on resize, shift to override.** `BrowserWindow` gained an `ASPECT_RATIO` constant
  and a `fitSize(...)` helper; `dragTo` now takes `shiftHeld` and derives the non-dragged axis from
  the dragged one at 16:9. `dragAspectLocked` is captured at `beginResize` from config so toggling
  the setting mid-drag cannot jolt the window, while shift is read live so a drag can always escape
  the ratio. Persisted as `windowAspectLocked` (default true).
- **Refresh button.** Balatro's QUIT stops the love.js page with no way back except closing and
  reopening the window, which locks a player out over a misclick. A fourth title-bar button
  (circular-arrow icon) calls `rebind()`, which drops the session and starts a fresh page — the same
  path `/craftlatro reload` uses, so there is no new lifecycle to reason about.
- **Background opacity behind a framed window.** `windowBackdropOpacity` 0-100, default 0, drawn as a
  full-viewport scrim before the window. 0 paints nothing and keeps the world fully visible, which is
  the default and the point of framed mode.

The resize math was verified by a throwaway simulation of `dragTo` (13 assertions: ratio held for
corner, bottom-only and left+bottom drags; shift breaks it; floors; left edge anchors the right edge;
and the two clamping cases). That sim caught two real bugs that a compile could not:

1. `MIN_WIDTH` was 240 against a `MIN_HEIGHT` of 200, so under a 16:9 lock the minimum was
   unreachable — a 240-wide window wants 135px of height. The width floor now derives from the height
   floor (`Math.round(MIN_HEIGHT * ASPECT_RATIO)`).
2. `Mth.clamp(value, MIN, Math.max(MIN, available))` returns the floor when the space available is
   smaller than the floor, which pushed the window off the screen near the right edge. `fitSize`
   clamps to `min(floor, available)` so the floor is a soft target that always yields to the viewport.

## 7. Fullscreen double-click, and what the "dimming" actually was

Two corrections from the third hands-on.

**Double-click fullscreen did nothing.** `isOverTitleBar` was gated on `framed &&`, so the title bar
did not exist for hit-testing in fullscreen and the double-click never fired. The geometry test is now
independent of the mode — the bar is drawn in both — and `beginMove` gates on framed instead, so a
fullscreen bar answers a click without dragging anything.

**The backdrop feature was misread.** I had implemented a plain dark scrim. What the player actually
wanted was control over the *blur* Minecraft paints behind every screen. The source of it, from the
bytecode: `Screen.extractBackground` runs from the Screen render wrapper *before*
`extractRenderState`, takes the world path when `isInGameUi()` is false, and calls
`extractBlurredBackground`, which is `GuiGraphicsExtractor.blurBeforeThisStratum()` guarded only by
`options.getMenuBackgroundBlurriness() >= 1`. That is a binary render-pass boundary — the
`GuiRenderState` method throws if called twice per frame and carries no radius — so there is no
strength to scale.

`windowBackdropOpacity` is therefore replaced by `windowBackgroundStrength` 0-100, default 0, and
`extractBackground` is now overridden:

- 0 draws nothing at all, so the world stays sharp and fully visible. (`Screen.extractTransparentBackground`
  was not an option for this: it draws a gradient, and `extractBackground`'s blur path also mixes in
  the in-world menu texture, so the treatment is suppressed rather than swapped.)
- 100 delegates to `super.extractBackground(...)`, which is exactly the vanilla look the player sees
  today.
- In between the world is blurred and covered by a scrim of proportional alpha, because the blur
  itself cannot be interpolated. The tooltip says so.

The `Hud.extractDeferredSubtitles()` call that `Screen.extractBackground` ends with is reproduced on
the non-delegating paths, since skipping it would silently drop subtitles while the window is open. A
fullscreen window covers the whole viewport, so it skips the treatment entirely rather than paying for
a blur pass no one can see.

**Round-trip bug found while wiring the double-click.** `toggleFramed` saved geometry on *every*
transition, so going fullscreen overwrote the windowed rectangle and coming back restored a
fullscreen-sized window — the player's layout was destroyed by the very action that was supposed to
be reversible. Going fullscreen now fills the rectangle in memory without persisting it, and coming
back reloads the windowed rectangle from the config. `ModeArt` (12 assertions) covers the round trip,
reopening in fullscreen, a resize while fullscreen, the small-viewport case, a viewport smaller than
the size floors, and the first-run default.

## 8. Blur toggle replaces the strength slider, and how the world-running toggle works

**The strength slider became a toggle.** A slider was able to do nothing the player could perceive:
`GuiGraphicsExtractor.blurBeforeThisStratum` is a binary render-pass boundary with no radius, so the
in-between values were "blurred, with a scrim of varying darkness" — a dial whose first click was a
jump from sharp to blurred and whose rest only darkened. `windowBackgroundStrength` (int 0-100) is
replaced by `windowBackgroundBlur` (boolean, default off), and `extractBackground` became three lines:
framed with blur off (or fullscreen, where the treatment is invisible) draws nothing at all and
reproduces `Hud.extractDeferredSubtitles()`, otherwise it delegates to `super` and keeps the vanilla
look exactly.

**`windowWorldRunning` now defaults to true**, because the point of the framed window is a living
world behind it, and the toggle is one property away for anyone who wants the old pause. The tooltip
now says the tradeoff and notes that multiplayer always runs.

**How "keep the world running" actually works**, verified from the client bytecode rather than
assumed. The chain is:

1. `Minecraft.runTick` recomputes the pause flag every tick as
   `hasSingleplayerServer() && gui.isPausing() && !singleplayerServer.isPublished()`.
2. `Gui.isPausing()` is `screen != null && screen.isPauseScreen()` (plus the overlay check).
3. `BrowserScreen.isPauseScreen()` returns `!config.isWindowWorldRunning()`, so the toggle is a single
   override feeding that decision.
4. The resulting flag reaches `IntegratedServer.paused`, which stops the integrated server's world
   tick while the world keeps rendering behind the window.

Two consequences that fall out of those conditions and were not obvious:

- **Multiplayer never pauses regardless of the toggle.** `hasSingleplayerServer()` is false on a
  dedicated server, so the flag is false no matter what `isPauseScreen()` returns. The Balatro window
  floats over a live remote world by construction, which is the wanted "client mod" behaviour with no
  code for it.
- **A singleplayer world opened to LAN ignores the pause too**, because of the `!isPublished()`
  condition, so the same setting behaves differently on a published world.

**Declared as a client-only mod.** `fabric.mod.json` now says `"environment": "client"` instead of
`"*"`. This was already true in substance — the `main` source set is a no-op `ModInitializer` that
only logs, every registration (keybinds, `ClientCommandRegistrationCallback`, client tick events)
lives behind the `client` entrypoint, there are no mixins and no server-side networking or
lifecycle hooks — but `"*"` meant a server that ever saw the jar would still enforce the `depends` on
Rinku and Cloth Config. With `"client"` the mod is simply not loaded on a dedicated server, so a
player can have it installed on their client while a multiplayer world needs nothing from its server
and the server never learns the mod exists. Verified the packaged jar's metadata carries the new
environment, and that no `Server*` API is referenced anywhere in `src/main`.

## 9. Own keybind category, the B toggle, and what Rinku does not ship

**A keybind category of our own.** `KeyMapping.Category` in 26.2 is a record wrapping an `Identifier`
with three ways in: the plain constructor, `new KeyMapping.Category(id)`, which registers nothing, and
the static `register(Identifier)`, which appends to a `SORT_ORDER` list used by the controls screen and
throws on a duplicate. Vanilla builds all of its own categories (`MOVEMENT`, `MISC`, ...) through the
`String`-taking `register(String)` overload, which delegates to `Identifier.withDefaultNamespace`.
Craftlatro now registers `Identifier.fromNamespaceAndPath("craftlatro", "controls")` and both bindings
carry it instead of `Category.MISC`, so they group under their own heading.

The display name comes from `Category.label()`, which is `Component.translatable(id.toLanguageKey("key.category"))`,
so the key is `key.category.craftlatro.controls`. Verified by running the real `Identifier` class from
the mapped jar rather than inferring the format: it prints `key.category.craftlatro.controls`, and the
vanilla form for comparison includes the namespace too (`key.category.minecraft.misc`). That lang key is
what makes the heading read "Craftlatro" instead of a raw key in the controls screen.

**Rinku ships no Minecraft keybind of its own in 3.0.4-26.2**, so there is nothing to remove. Verified
from the artifact: the `fabric.mod.json` entrypoints are `main` (`RinkuFabric`), `client` (an empty
array) and `modmenu` (`ModMenuIntegration`); the only classes in the jar that reference `KeyMapping` are
`example/RinkuExampleMod` (a demo of how a *consuming* mod registers one, not loaded by any entrypoint)
and `platform/FabricPlatformHelper`, whose `getKeyMappingKey(KeyMapping)` is a service accessor other
mods call; no GLFW key constants or keybind registration appear anywhere in the jar; and Rinku's mixes
(`MixinClientPackSource`, `MixinGui`, `MixinMinecraft`, `MixinGameRenderer`) do not touch input
binding. What looks like a keybind from the outside is `RinkuBrowser.useBrowserControls(boolean)`,
which enables shortcuts *inside the page* (F5, Ctrl+R) rather than an MC binding. The repo's earlier
note about "Keybindings (bind at first render...)" is about Rinku's key bridge that forwards MC input
into the browser, not a registered binding.

For the record, had a third-party binding needed suppressing there is no official API: the options are
a Mixin into `Minecraft.handleKeybinds` or `KeyMapping.click` to filter it, or reflecting the mapping
and resetting its key to `UNKNOWN`. Both are fragile across versions and affect any other mod relying
on that binding, which is the concern that made this a question rather than an instruction.

**The open key is now a toggle** (`openBalatroToggle`, default on). `onClientTick` checks whether the
screen currently showing is a `BrowserScreen` and calls its `onClose()` (which parks the browser and
closes, exactly as Escape does) instead of `setScreen(null)`, so the toggle path leaves the browser in
the same state Escape always has. Turning the setting off restores the old behaviour where a second
press does nothing. The `select_exe` language entry was renamed to "Select Balatro.exe" as asked.

## 10. Why the toggle could not work from a tick, and where it now lives

The toggle shipped and did nothing. The report ("category renders, config exists, behaviour does not
change") matched a diagnosis that has nothing to do with the browser: `KeyboardHandler.keyPress` only
dispatches `KeyMapping.set` and `KeyMapping.click` when `minecraft.gui.screen() == null`. The
bytecode is explicit — the screen-null test at 694-712 selects between a block ending in
`set(key, true); click(key)` and a block that only does debug-modifier bookkeeping and returns. So a
press while the Balatro window is open is never counted as a keybind click, `consumeClick()` never
returns true, and the tick-side branch was unreachable dead code. The screen was receiving the key and
the browser still saw it, but Minecraft never learned the binding had fired.

The two halves now live where the event actually arrives:

- **Open** stays in `onClientTick` on `consumeClick()`, which is correct there because no screen is open.
- **Close** moved into `BrowserScreen.keyPressed` (and `mouseClicked` for a binding rebound onto a
  mouse button, via `KeyMapping.matchesMouse`), ahead of the browser forwarding, so the key is not also
  pressed in the page by the same keystroke that closes the window. `CraftlatroClient.isOpenBalatroKey`
  goes through `KeyMapping.matches(KeyEvent)` rather than a hardcoded `GLFW_KEY_B`, so a rebind still
  works; `matches` handles both KEYSYM and SCANCODE bindings.

Turning the setting off now genuinely changes behaviour: with it off the key falls through to the
browser and the window only closes on Escape or the close button, which is the only way to observe the
difference without a toggle, and the reason this shipped broken without anyone noticing.

## 11. Tooltips wrapped to a tooltip width, and categories split Browser / Craftlatro

**Why the tooltip covered the row.** From the Cloth Config source: `TooltipListEntry.extractRenderState`
calls `Tooltip.of(new Point(mouseX, mouseY), wrapLinesToScreen(lines))`, and `wrapLinesToScreen` calls
`wrapLines(lines, screen.width)`. So the wrap width is the *screen* width, and `AbstractConfigScreen`
then feeds those already-wrapped lines to `setTooltipForNextFrame` at the mouse position. A
single-sentence tooltip therefore rendered as one line as wide as the screen, drawn over the row being
hovered. The position is fixed by the library (mouse coordinates, no positioner choice in that
overload), so the width is the only lever available from outside.

`CraftlatroConfigScreen` now pre-wraps every tooltip to 220px through a `wrap` helper that uses
`Font.split(component, 220)` and flattens each `FormattedCharSequence` back to a `Component` via its
`accept` sink. Because Cloth re-splits each line it is given to the screen width, a line that already
fits passes through unchanged, so the tooltip ends up as wide as its longest line instead of the whole
screen. Longest tooltip on the screen goes from one full-width line to roughly six short ones.

Verified headlessly: the flattening half was run against the real `Component` and
`FormattedCharSequence` classes (round-trips 208 of 208 characters exactly, short lines intact), which
is the half that could have silently mangled text. The `Font.split` half is vanilla behaviour and is
exercised by the running game only.

**Categories restructured** for the eventual library split. Tabs are now Browser and Craftlatro rather
than Balatro and Performance, each with General and Performance subcategories
(`startSubCategory(...).setExpanded(true)`), because the perf levers are the ones a player tunes last
and should not have to scroll past presentation to reach:

- Browser: General is window mode, background blur, aspect lock; Performance is render scale, repaint
  rate, stats overlay. Everything here would move with the embedded-browser library.
- Craftlatro: General is the game-path note, keep-world-running and the open-key toggle, plus the
  love.js disclaimer. Everything here is Balatro integration policy.

The "every command still works alongside this screen" row is gone, since the chat commands are the
fallback and not something a player needs told. The disclaimer now reads: "Craftlatro runs in love.js,
a port of the LÖVE engine. RNG, visual shaders, and audio may differ. 100% Parity is not guaranteed,
but gameplay should be essentially the same."

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
