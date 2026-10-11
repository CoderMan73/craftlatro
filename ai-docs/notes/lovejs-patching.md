# love.js Patching for Balatro

## Current Issue (2026-10-11)
After injecting `web_patches.lua`, `nativefs.lua`, `lovely.lua`, and `bit.lua` into the `.love` archive,
and patching `main.lua` with `require 'web_patches'`, the game **still fails to start**:

```
main.lua:3: module 'bit' not found:
  no field package.preload['bit']
  no 'bit' in LOVE game directories
  no file 'bit' in LOVE paths
  no file './bit.lua'
  no file '/usr/local/share/lua/5.1/bit.lua'
  ...
```

The `bit.lua` file IS in the .love archive (verified via zipfile), but `require('bit')` fails.
Root cause (confirmed, 2026-10-11): Java's `ZipOutputStream` sets the data-descriptor flag
(bit 0x08) on DEFLATED entries even when explicit CRC/size are supplied — a standalone Java
diagnostic confirmed `DEFLATED + setCrc+setSize` still yields `gpFlag=0x808` (bit 3 set),
while `STORED` yields `gpFlag=0x800` (bit 3 clear). love.js's Emscripten-bundled PhysFS
reads sizes/CRC from the central directory and tolerates a zero local-header CRC when bit 3
is set, but its seek-backed memory IO cannot resolve data-descriptor-streamed entries; the
injected `bit.lua` entry (appended at the end) becomes invisible to `require`. This matches
the known-good ports: W0W53R re-serializes via JSZip `generateAsync` and mauri870 via
`zip -9 -r` (info-zip), neither of which emits data descriptors.

Fix: write all repacked entries with `ZipEntry.STORED` (method 0) plus explicit CRC,
size, and compressedSize. This guarantees a clean central directory and no data-descriptor
flag. Verified standalone that the resulting archive has bit 3 clear and that `ZipFile`
round-trips `bit.lua` (resolvable at the archive root, satisfying `require 'bit')`.

## Problem
Balatro is built for LuaJIT (native LÖVE), but love.js uses Lua 5.1 with several incompatibilities:
1. No `bit` module (LuaJIT specific)
2. No threading support (`love.thread` is a no-op in love.js)
3. Different `math.log` behavior (no base parameter in Lua 5.1)
4. `string.format` doesn't handle Balatro's format strings the same way
5. Mipmaps cause issues - must disable
6. GLSL ES requires `const` for loop variables
7. NativeFS module not available in love.js

## Solution
Based on W0W53R/web-balatro and mauri870/balatro-web approaches. Patches are injected INTO the .love archive:

### Files injected into .love
- `bit.lua` - LuaBitOp + Lua 5.2 bit32 compatibility module
- `web_patches.lua` - Monkey-patches love.* APIs for web compat
- `nativefs.lua` - Fake nativefs module backed by love.filesystem
- `lovely.lua` - Stub for Lovely mod loader

### Files patched in .love
- `main.lua` - Prepend `require 'web_patches'`, disable OS check
- `globals.lua` - Set `F_SOUND_THREAD = false`
- `resources/shaders/hologram.fs` - Add `const` to `glow_samples`

### ZIP format
All ZIP entries are written with method STORED (no compression) and explicit CRC, size,
and compressed size. STORED output never sets the data-descriptor flag (bit 0x08), which
love.js's PhysFS-based ZIP reader cannot parse. (Setting explicit CRC/size on DEFLATED
entries is NOT sufficient — Java's ZipOutputStream still emits the data-descriptor flag;
see confirmed root cause above.) `main.lua` is patched last so the injected shims remain
reachable to `require`.

## References
- https://github.com/W0W53R/web-balatro
- https://github.com/mauri870/balatro-web
- https://github.com/2dengine/love.js
- https://icculus.org/physfs/
