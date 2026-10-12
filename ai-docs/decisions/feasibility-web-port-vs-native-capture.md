# Feasibility Study: Balatro Web Port Approaches (Option A vs Option B)

## Executive summary with verdict

**Partially feasible — with a clear preferred path.** Option A (reuse an existing working love.js web port such as W0W53R/web-balatro) is the viable, lower-risk path: a complete, maintained, MIT-licensed reference exists and the patch assets craftlatro already vendors match it verbatim. The single remaining blocker is ZIP re-serialization correctness, with a known-good fix (re-serialize via Python's `zipfile` or JSZip, not Java's `ZipOutputStream`). Option B (run native Balatro.exe + Windows Graphics Capture) is technically feasible but high-complexity, Windows-only, and introduces input-forwarding, runtime-dependency, and fidelity risks that outweigh the benefit.

## Feasibility of required mechanisms (the core technical blocker)

The current implementation fails with `module 'bit' not found` even though `bit.lua` is injected into the archive and the code sets explicit CRC/size on every entry. Research into love.js's bundled PhysFS source (`physfs_archiver_zip.c`) clarifies the mechanism and points to the real cause:

- PhysFS reads each entry's CRC and (un)compressed sizes from the **central directory**, not the local file header. In `zip_parse_local`, it explicitly tolerates a zero CRC/size in the local header when general-purpose bit 3 is set (the data-descriptor flag). The inline comment reads "we don't care about these values anyhow."
- Therefore the data-descriptor flag **by itself does not prevent PhysFS from locating or reading `bit.lua`**. The "module 'bit' not found" error indicates PhysFS successfully opened the archive but could not resolve the `bit` module at runtime — i.e., the `bit.lua` entry is absent from the central directory, its offset is wrong, or the seek landed on garbage.
- Java's `java.util.zip.ZipOutputStream` is known to still set general-purpose bit 3 (data descriptor) on DEFLATED entries in certain JDK versions, even when CRC/size are supplied. More importantly, the re-serialization step is the most likely corruption vector: if any central-directory offset becomes inconsistent with the actual local-header/data positions, PhysFS seeks to the wrong byte and the entry is effectively invisible.

**Required mechanism / fix:** produce a ZIP whose central directory offsets are guaranteed correct and seekable. The known-good reference implementations (see below) all avoid Java's `ZipOutputStream` for the final repackaging:
- W0W53R re-serializes the in-memory archive with **JSZip `generateAsync`**.
- mauri870 repacks with `zip -9 -r` (Info-ZIP).
- The project notes recommend **Python's `zipfile`**, which is empirically reliable (it writes seekable, non-streaming output with clean central directories).

Points of entry: `LoveExtractor.patchLoveArchive` (Java repack), `GameLauncher` (orchestrates patch + serve), and the COOP/COEP `LocalHttpServer` (already correct). Points of failure: any further step that re-serializes the `.love` with a streaming/zipwriter that does not emit a correct central directory.

## Architectural suitability

Option A fits the existing architecture with minimal disruption:
- The mod already vendors `bit.lua`, `web_patches.lua`, `nativefs.lua`, and `lovely.lua` under `src/client/resources/assets/craftlatro/lua/`, and these **match W0W53R's `window.patches` blob verbatim** (confirmed by diff against `patches.js`). The text patches to `main.lua`, `globals.lua`, and `hologram.fs` also mirror W0W53R's `build.js` logic.
- The only architectural change required is swapping the ZIP write backend from Java `ZipOutputStream` to a clean re-serializer. The smallest, most verifiable move is to validate the Python `zipfile` approach on a real `Balatro.exe` before committing to a Java library change. W0W53R's JSZip path is an alternative but would require embedding a JS engine or moving patching client-side, which does not fit the Rinku/embedded-browser model.
- love.js 11.5 `require('bit')` resolution depends on the module being resolvable on love's require path. A `bit.lua` at the archive root satisfies `require 'bit'` in the web port because PhysFS mounts the `.love` at the search root — so the archive-root placement the code uses is correct in principle.

Option B (native window capture) diverges from the established "integration glue, never bundle runtimes" architecture:
- Balatro.exe is a LOVE packaged executable; running it natively requires the LÖVE runtime to be installed on the host, defeating the "user supplies Balatro.exe" simplicity and adding a hidden prerequisite.
- It also re-introduces the compliance boundary question (the mod would orchestrate running the game rather than just serving a web port).

## Environment and constraints

- Cross-platform: love.js + Rinku Chromium is cross-platform (Windows/macOS/Linux Minecraft). Option B's Windows Graphics Capture and the Balatro.exe binary make it Windows-only.
- love.js fidelity gaps apply to both: RNG/shader/audio non-parity vs native, LuaJIT-only features (`goto`, `bit`) absent in Lua 5.1. These are inherent to the love.js path and already documented as a first-run warning (M4).
- Rinku Chromium runtime (~150 MB) is fetched by Rinku at first launch — unchanged by either option.
- Legal/boundary constraint (AGENTS.md): Balatro code/assets and the LÖVE runtime must stay out of the jar/repo. All known-good web ports respect this by applying patches at runtime on the user-supplied `.love`.

## Existing solutions and foundational research

Reference projects for Option A (patch + repack + serve), all MIT/oss:

1. **W0W53R/web-balatro** — 32 stars, actively maintained (latest commits Aug 2025). In-browser JSZip extraction + text patching of `main.lua`/`globals.lua`/`hologram.fs`, then injects the same four shim files (`bit.lua`, `web_patches.lua`, `nativefs.lua`, `lovely.lua`) via `zipfile.file(...)` and re-serializes with `generateAsync({type:'blob'})`, served to the 2dengine love.js player. Empirically working. The craftlatro vendored shims are a subset of this project's `patches.js`.
2. **mauri870/balatro-web** (now archived/redirected to personal site) — Go-based; extracts with `unzip`, patches, and repacks with `zip -9 -r`, which produces clean ZIPs (the same conclusion as the Python recommendation). Logic is portable to Java or callable as a build-time helper.
3. **ytrewq000/Balatro-Web-Port** and **luluxe008/balatro-browser** (fork of W0W53R) — confirm the patch set is stable and reused across ports.

Reference for Option B (native capture):

- **Windows.Graphics.Capture** (Microsoft docs): supports `CreateForWindow(HWND)` and `CreateForMonitor(HMON)`. Frames arrive via `Direct3D11CaptureFramePool::FrameArrived` as `B8G8R8A8` Direct3D11 textures; requires a `DispatcherQueue` and the "Graphics Capture" capability / user consent.
- A StackOverflow case confirms Java interop is possible via a C++ JNI/JNA bridge DLL (Direct3D11 + COM + `DispatcherQueueController`), but the author reports intermittent "capture session unexpectedly removed" failures from race conditions between the Java-owned capture map and the native thread.
- JavaCV/FFmpeg can consume D3D frames for encoding/rendering, but adds a large native dependency and another translation/copy step before frames reach Minecraft's GUI.
- The broader `4RH1T3CT0R7/balatro-port-tui` Rust port shows the full alternative: a pure-Rust LOVE API reimplementation — a viable long-term path if the web port proves insufficient, but a much larger scope.

## Technical verdict

| Criterion | Option A (web port reuse) | Option B (native window capture) |
|---|---|---|
| Core blocker | ZIP repack correctness (fix exists) | n/a |
| Cross-platform | Yes | No (Windows only) |
| Scope/complexity | Low (swap ZIP writer) | High (native bridge, input fwd, window mgmt) |
| Runtime dependency | None extra (Rinku + love.js, both existing) | LOVE runtime must be installed |
| Fidelity | Web-port caveats (already warned) | Native parity, but adds capture/input lag |
| Compliance boundary | Clean (user .love only) | Risky (orchestrating native exe) |

**Verdict:** Option A is feasible and preferred. Recommended next step (on a branch for testing): reproduce the `module 'bit' not found` failure, then repack the patched archive with Python `zipfile` (or Info-ZIP) from a test harness to confirm the central-directory correctness is the actual fix. If confirmed, either port that clean serialization back to Java (e.g., use `STORED` entries or a vetted Java zip library, or shell out to Python/info-zip as a build helper) or adopt W0W53R's proven patch set more directly.

**Unknowns requiring validation (branch test):**
1. Does Java `ZipOutputStream` actually emit bit 3 on these entries, and is the central directory offset the real defect? (Validate by inspecting a generated `.love` with `zipinfo`/Python `zipfile`.)
2. Does a Python/info-zip-repacked `.love` with the same patch set load correctly in love.js? (End-to-end test against the local HTTP server + Rinku.)
3. If Option B is ever revisited: does WGC capture at Minecraft frame rates introduce latency acceptable for Balatro's input model? (Windows-only spike.)