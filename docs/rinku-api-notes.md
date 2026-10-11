# Rinku API Notes

## Overview

Rinku v3.0.4 is a Fabric library mod that embeds a Chromium-based browser (JCEF) in Minecraft.
It provides a public API for creating browser instances, rendering them on flat GUI screens, and
mapping Minecraft input to the embedded browser.

## Dependency

```
Maven: https://keksuccino.github.io/maven/
Coordinate: de.keksuccino:rinku-fabric:3.0.4-26.2
```

The Rinku Maven repository is hosted at `https://keksuccino.github.io/maven/` and publishes
artifacts following the pattern `de.keksuccino:rinku-fabric:<mod_version>-<minecraft_version>`.
Verified available for MC 26.2: versions 3.0.0 through 3.0.4.

## Public API Surface

### Rinku (de.keksuccino.rinku.Rinku)

Static facade for browser creation and lifecycle management.

| Method | Signature | Notes |
|--------|-----------|-------|
| `createBrowser` | `RinkuBrowser createBrowser(String url, boolean transparent)` | Creates a new off-screen browser. Must be called after initialization (asserted internally). Thread-safe: delegates to render thread if called from client thread. |
| `createBrowser` | `RinkuBrowser createBrowser(String url, boolean transparent, int width, int height)` | Same as above, also resizes the browser immediately. |
| `isInitialized` | `boolean isInitialized()` | Check if CEF has been initialized. Initialization is driven by `MixinMinecraft` — not called by user code. |
| `assertInitialized` | `void assertInitialized()` | Throws `RuntimeException` if not initialized. |
| `scheduleForInit` | `void scheduleForInit(RinkuInitListener task)` | Queue a task to run when Rinku finishes initializing. |
| `isInitializationAllowed` | `boolean isInitializationAllowed()` | Check if a new init attempt may be scheduled. |
| `getClient` | `RinkuClient getClient()` | Access the `CefClient` wrapper for adding event handlers. |
| `getSettings` | `RinkuSettings getSettings()` | Access persisted settings (loads from disk). |
| `shutdown` | `void shutdown()` | Permanently shuts down CEF for this process. |

**Key insight:** `Rinku.initialize()` is called automatically by `MixinMinecraft` when the game
client starts. User code does not need to call it. Use `Rinku.isInitialized()` or
`Rinku.scheduleForInit()` to gate browser creation if needed.

### RinkuBrowser (de.keksuccino.rinku.RinkuBrowser)

Extends `org.cef.browser.CefBrowserOsr`. The core class for rendering and input.

| Method | Signature | Notes |
|--------|-----------|-------|
| `getTextureIdentifier` | `Identifier getTextureIdentifier()` | Returns the GL texture Identifier for rendering with `GuiGraphics.blit()`. Returns `null` if not ready. |
| `isTextureReady` | `boolean isTextureReady()` | Check if the texture is initialized and ready to render. |
| `resize` | `void resize(int width, int height)` | Resize the browser surface. |
| `loadURL` | `void loadURL(String url)` | Navigate to a URL (inherited from CefBrowser). |
| `goBack` | `void goBack()` | Navigate back (inherited). |
| `goForward` | `void goForward()` | Navigate forward (inherited). |
| `reload` | `void reload()` | Reload current page (inherited). |
| `canGoBack` | `boolean canGoBack()` | Can navigate back (inherited). |
| `canGoForward` | `boolean canGoForward()` | Can navigate forward (inherited). |
| `getURL` | `String getURL()` | Current URL (inherited). |
| `isLoading` | `boolean isLoading()` | Current loading state (inherited). |
| `setFocus` | `void setFocus(boolean focus)` | Set keyboard focus (inherited). |
| `close` | `void close()` | Close the browser and free resources. |
| `isValid` | `boolean isValid()` | Check if the native browser is valid. |
| `useBrowserControls` | `RinkuBrowser useBrowserControls(boolean)` | Enable built-in browser shortcuts (Ctrl+R reload, Ctrl+scroll zoom, Alt+Left/Right nav). Returns `this` for chaining. |
| `getZoomLevel` | `int getZoomLevel()` | Current zoom level. |
| `setZoomLevel` | `void setZoomLevel(int)` | Set zoom level (range -9 to 9). |
| `getRenderer` | `RinkuRenderer getRenderer()` | Access the renderer for texture management. |
| `sendKeyPress` | `void sendKeyPress(int keyCode, long scanCode, int modifiers)` | Send a key press event to the browser. |
| `sendKeyRelease` | `void sendKeyRelease(int keyCode, long scanCode, int modifiers)` | Send a key release event. |
| `sendKeyTyped` | `void sendKeyTyped(char c, int modifiers)` | Send a character typed event. |
| `sendMouseMove` | `void sendMouseMove(int mouseX, int mouseY)` | Send mouse move (coordinates in browser pixel space). |
| `sendMousePress` | `void sendMousePress(int mouseX, int mouseY, int button)` | Send mouse button press. Note: button 1 (middle) and 2 (right) are swapped to match CEF conventions. |
| `sendMouseRelease` | `void sendMouseRelease(int mouseX, int mouseY, int button)` | Send mouse button release. |
| `sendMouseWheel` | `void sendMouseWheel(int mouseX, int mouseY, double amount, int modifiers)` | Send mouse wheel event. On non-macOS, amounts are multiplied by 3 and floored/ceiled. |

**Coordinate note:** Mouse coordinates passed to the browser are in browser pixel space, not screen space. Must be scaled by `minecraft.getWindow().getGuiScale()` and offset by the browser's screen position.

### RinkuClient (de.keksuccino.rinku.RinkuClient)

Wrapper around `org.cef.CefClient`. Provides event handler registration:

| Method | Signature | Notes |
|--------|-----------|-------|
| `getHandle` | `CefClient getHandle()` | The raw CEF client. |
| `addLoadHandler` | `void addLoadHandler(CefLoadHandler)` | Register a load handler. |
| `addDisplayHandler` | `void addDisplayHandler(CefDisplayHandler)` | Register a display handler (receives URL changes, title changes, etc.). |
| `removeDisplayHandler` | `void removeDisplayHandler(CefDisplayHandler)` | Remove a display handler. |
| `addContextMenuHandler` | `void addContextMenuHandler(CefContextMenuHandler)` | Register a context menu handler. |
| `addAudioHandler` | `void addAudioHandler(CefAudioHandler)` | Register an audio handler. |
| `addDownloadHandler` | `void addDownloadHandler(CefDownloadHandler)` | Register a download handler. |

### RinkuSettings (de.keksuccino.rinku.RinkuSettings)

Loads/saves settings to disk. Accessible via `Rinku.getSettings()`.

| Method | Notes |
|--------|-------|
| `isBrowserPreloadEnabled()` | Whether browser preloading is enabled (default: true). |
| `getBrowserPreloadTransparentPoolSize()` | Pool size for transparent browsers. |
| `getBrowserPreloadOpaquePoolSize()` | Pool size for opaque browsers. |
| `getConsoleLogForwardingMinSeverity()` | Minimum console log severity to forward to MC log. |
| `getCefLogSeverity()` | CEF native log verbosity. |

## Flat GUI Screen Pattern

The `Rinku` example module (package `de.keksuccino.rinku.example`) provides a reference
implementation of a flat GUI browser screen:

### ExampleScreen (de.keksuccino.rinku.example.ExampleScreen)

Extends `net.minecraft.client.gui.screens.Screen`. Key patterns:

1. **Browser creation** in `init()`:
   ```java
   browser = Rinku.createBrowser(url, transparent);
   ```

2. **Rendering** in `extractRenderState(GuiGraphicsExtractor, ...)`:
   ```java
   if (browser != null && browser.isTextureReady()) {
       Identifier texture = browser.getTextureIdentifier();
       guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w, h);
   }
   ```

3. **Mouse input mapping**:
   - `sendMousePress(x, y, button)` — note Minecraft swaps middle/right buttons vs CEF
   - `sendMouseRelease(x, y, button)`
   - `sendMouseMove(x, y)`
   - `sendMouseWheel(x, y, amount, modifiers)`

4. **Keyboard input mapping**:
   - `sendKeyPress(key, scancode, modifiers)`
   - `sendKeyRelease(key, scancode, modifiers)`
   - `sendKeyTyped(codepoint, modifiers)`

5. **Cleanup** in `onClose()`:
   ```java
   browser.close();
   ```

### RinkuExampleMod (de.keksuccino.rinku.example.RinkuExampleMod) — Fabric only

Demonstrates keybind registration via Fabric API:
```java
KeyMapping key = new KeyMapping("Open Browser", InputConstants.KEY_F12, KeyMapping.Category.MISC);
KeyMappingHelper.registerKeyMapping(key);
ClientTickEvents.START_CLIENT_TICK.register(client -> {
    if (key.isDown() && !(client.gui.screen() instanceof ExampleScreen)) {
        client.gui.setScreen(new ExampleScreen(Component.literal("Example Screen")));
    }
});
```

## M1 Checklist

- [x] Fabric 1.26.2 mod project scaffolded with Loom 1.18.4
- [x] Rinku dependency resolves: `de.keksuccino:rinku-fabric:3.0.4-26.2` from `https://keksuccino.github.io/maven/`
- [x] Rinku public API inspected — flat GUI browser screen can be driven programmatically via `Rinku.createBrowser()` + `RinkuBrowser` input/render methods
- [ ] Verify Rinku loads in a dev run (requires local Minecraft client — see notes below)

## Dev Run Notes

Rinku initialization is triggered by `MixinMinecraft` when the game starts. In a development
environment, Rinku will attempt to download the JCEF runtime (~150 MB Chromium bundle) on first
launch. The Rinku GitHub README documents cache locations:
- JCEF runtime: `<game instance>/rinku-libraries/jcef-v1/<platform>/<commit>/`
- Config: `<game instance>/config/rinku/rinku.properties`
- Browser cache: `%LOCALAPPDATA%\Rinku\cef-cache` (Windows)

The dev run directory is `runClient/` relative to the project root (Fabric Loom default).

Rinku's own `ExampleModHandler.init()` only registers the F12 demo browser in development
environments. Our mod should call `Rinku.isInitialized()` before creating browsers, or use
`Rinku.scheduleForInit()` to queue browser creation until initialization completes.

## Rinku Initialization Flow (Observed)

1. `RinkuFabric.onInitialize()` calls `ExampleModHandler.init()` (Rinku's own entrypoint)
2. `MixinMinecraft` triggers `Rinku.initialize()` on first client tick
3. `Rinku.initialize()` calls `CefUtil.init()` which sets up JCEF
4. `Rinku.client` and `Rinku.app` are set
5. Preloaded browser pools are filled asynchronously
6. `Rinku.isInitialized()` returns true
7. `Rinku.createBrowser(url, transparent)` can now be called to get a `RinkuBrowser`
