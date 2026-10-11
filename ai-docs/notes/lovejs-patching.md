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
This means PhysFS cannot read the `.love` archive properly - likely the data descriptors in the
patched ZIP are still causing issues with PhysFS.

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
All ZIP entries must have explicit CRC and sizes set (no data descriptor flag bit 0x08).
Java's `ZipOutputStream` uses data descriptors by default when sizes aren't set,
but PhysFS in love.js requires proper local file headers.

## References
- https://github.com/W0W53R/web-balatro
- https://github.com/mauri870/balatro-web
- https://github.com/2dengine/love.js
- https://icculus.org/physfs/
