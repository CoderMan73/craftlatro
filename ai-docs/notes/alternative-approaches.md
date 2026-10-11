# Research Notes: love.js + Balatro Approaches

## Current State (2026-10-11)

### What we have working
- `Balatro.exe` extraction to `.love` (56MB ZIP with PK header)
- `love.js 11.5` download from 2dengine/love.js (player.js, love.js, love.wasm, etc.)
- COOP/COEP HTTP server serving all files
- Rinku/Chromium browser embedded in Minecraft GUI
- Clean Minecraft client shutdown (daemon threads fix works)
- ZIP entries with explicit CRC/sizes (no reliance on data descriptors)

### What we've tried (and why it fails)

1. **Separate bit.lua + player.js patching approach**: Copy `bit.lua` as a separate file,
   patch `player.js` to add it to the package list. Game loaded forever because the
   other critical patches were missing.

2. **Inject all patches into .love archive approach**: `bit.lua`, `web_patches.lua`,
   `nativefs.lua`, `lovely.lua` injected into the .love ZIP; `main.lua`, `globals.lua`,
   `hologram.fs` patched. Result:
   ```
   main.lua:3: module 'bit' not found
   ```
   Even though `bit.lua` IS in the archive (verified via Python zipfile), PhysFS
   cannot find it. This is likely because ZIP entries (even with explicit CRC/sizes
   set in Java) still have the data descriptor flag bit (0x08) set in the general
   purpose bit flag, which confuses PhysFS.

### Key finding: Data Descriptor Issue
Python's `zipfile` reports ALL entries have `data_desc=True` (flag bit 0x08 set).
This includes entries from the ORIGINAL Balatro.exe ZIP (not just our Java-processed entries).

The original `injectShim` method was correct in setting explicit CRC/sizes, but Java's
`ZipOutputStream` may still set the data descriptor flag bit in the general purpose
bit flag when using DEFLATED compression, even with explicit CRC/size provided.

Need to verify: Does setting `entry.setMethod(ZipEntry.DEFLATED)` + `setCrc()` + `setSize()`
actually prevent the data descriptor flag from being written?

## Alternative Approaches (Research Needed)

### Option A: Embed a known-working web-balatro implementation
Instead of our own patching, use an already-working web port:
- **W0W53R/web-balatro** - JS-based, uses JSZip, patches applied in-browser
  - Files: patches.js, build.js, index.html, player.js (modified)
  - Approach: Load .love as blob, apply patches in browser, then run in love.js
  - Problem: Our flow extracts .love ahead of time for Rinku browser
  - Could potentially embed the JS into our index.html/player.js

- **mauri870/balatro-web** - Go-based, uses unzip + zip to repack
  - Files: patches.js, Makefile, unpack.sh, main.go
  - Approach: Extract -> patch -> repack -> serve
  - Uses `zip -9 -r` for clean ZIP creation (no data descriptors)
  - Could port the patching logic to Java

- **ytrewq000/Balatro-Web-Port** - Full port with mod support
  - Has patches.js with all web compatibility patches
  - Also uses separate patch files approach

### Option B: Run native Balatro in a window, capture/render in Minecraft
Instead of love.js, run the actual Balatro.exe:
- Run Balatro.exe (native Windows exe) as a subprocess
- Use something like:
  - **Windows Graphics Capture API** - capture window contents
  - **JavaCV/FFmpeg** - capture and render frames
  - **Rinku/Chromium** - can't render native exe
  - **Custom JNI** - native window embedding
- Challenges:
  - Input forwarding (keyboard/mouse) from Minecraft to Balatro window
  - Balatro.exe is a LOVE game, needs LOVE framework installed
  - Window management and synchronization
  - Performance overhead of frame capture

### Option C: Use a different love.js approach
- Instead of patching the .love, patch love.js itself
- The standard 2dengine love.js player.js might work if we can get bit.lua into the FS
- The issue is that `require('bit')` looks inside the .love archive
- Maybe serve bit.lua via a separate mechanism (e.g., add to package.path)

### Recommendation
Option A (mauri870) is the most promising: their Go code extracts, patches, and repacks
with `zip -9 -r` which produces clean ZIPs. We could port their Go logic to Java,
or potentially use their patches.js as-is and embed the JavaScript in our page.

The core issue seems to be ZIP format compatibility. Using Python's `zipfile` module
to repack (like W0W53R does with JSZip) may be more reliable than Java's ZipOutputStream.
