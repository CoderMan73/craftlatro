# M2: Balatro.exe -> love.js Package Pipeline — Findings

## Extraction Approach

Balatro.exe is a LOVE game archive packaged as a Windows executable wrapper around a ZIP archive.
The ZIP data starts with the magic bytes `PK\x03\x04` (0x504B0304).

### Two approaches investigated:

1. **web-balatro (W0W53R)**: Uses JSZip in-browser to extract the ZIP from the .exe blob.
   - Scans forward for `PK\x03\x04` in the ArrayBuffer (handles .exe stubs)
   - Applies patches: main.lua, globals.lua, resources/shaders/hologram.fs
   - Uses `?g=game.love` URL parameter for the love.js player

2. **balatro-web-port (mauri870)**: Uses `unzip -o "$1" -d unpacked` then zips the result.
   - Simpler: treats .exe as a plain ZIP, extracts via standard unzip
   - Patches `hologram.fs` shader with `dos2unix` + patch

### Chosen approach: balatro-web-port (simpler)

Balatro.exe is a standard ZIP archive (no launcher stub in typical distributions).
Java's `java.util.zip.ZipInputStream` can read it directly. The extraction scans for
the ZIP magic bytes to handle edge cases where the archive doesn't start at offset 0.

## Web Compatibility Patches

The following patches are needed for love.js (2dengine) compatibility:

1. **main.lua**:
   - Add `require "web_patches"` at the top
   - Replace `if os == 'OS X' or os == 'Windows' then` with `if false then`
   - Add dummy sound manager: `G.SOUND_MANAGER = { channel = { push = function() end } }`

2. **globals.lua**:
   - Replace `F_SOUND_THREAD = true` with `F_SOUND_THREAD = false`

3. **resources/shaders/hologram.fs**:
   - Replace `glow_samples;` with `4;` (GLSL ES compatibility)

Note: These patches are NOT applied yet in the current implementation. The extraction simply
unpacks the archive. Patches will be added in M2 refinement or M4.

## love.js Player Integration

The 2dengine love.js player is NOT bundled in the mod jar (per AGENTS.md boundaries:
"Keep externally-supplied content (e.g. Balatro code/assets, the LÖVE runtime) out of
the repo and out of the mod jar").

The `index.html` in the game directory serves as a wrapper that loads the love.js player:
```html
<iframe src="player.js?g=game.love"></iframe>
```

The `player.js` file should be placed in the game directory alongside `game.love`.
The user is responsible for providing the love.js player files.

URL: https://github.com/2dengine/love.js (released under zlib license)

## MC 26.2 API Changes Encountered

- `LocalPlayer.displayClientMessage(Component, boolean)` → renamed to `sendSystemMessage(Component)`
  The boolean parameter for action bar display was removed.
- `Screen.resize(int, int)` still exists (compatible with BrowserScreen M1 code)
- Input event signatures: `mouseClicked(MouseButtonEvent, boolean)`, `keyPressed(KeyEvent)`,
  `charTyped(CharacterEvent)` — all compatible with existing BrowserScreen code.

## Architecture Summary (M2)

```
CraftlatroClient (keybind "B", "F9", /craftlatro commands)
    ├── GameLauncher (file picker via JFileChooser, extraction orchestration)
    │       ├── LoveExtractor (ZIP extraction from .exe → game directory)
    │       │       └── Creates game.love (ZIP repackaging of extracted files)
    │       └── LocalHttpServer (com.sun.net.httpserver, COOP/COEP headers)
    │               └── CoopCoepFileHandler (sets required CORS headers)
    └── BrowserScreen (Rinku-powered flat GUI browser, loads localhost:18080)
```

Data flow:
1. User selects Balatro.exe (F9 / chat command / chat link)
2. LoveExtractor scans for ZIP magic, extracts to `~/.craftlatro/game/`
3. LoveExtractor creates game.love from extracted files
4. LocalHttpServer serves game directory with COOP/COEP headers
5. BrowserScreen loads `http://localhost:18080/` which serves index.html → love.js player
6. love.js player loads game.love via `?g=game.love` parameter
