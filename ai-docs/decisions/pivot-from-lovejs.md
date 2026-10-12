# Decision: Pivot to Alternative Balatro Web Port

## Status: 2026-10-11 — **RESOLVED, PIVOT NOT NEEDED**

> Superseded on 2026-10-12. The love.js pipeline below was fixed rather than replaced. **Do not
> pivot to a web-balatro fork on the basis of this document** — the custom pipeline now works and
> Balatro loads in-game. See `ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md` for the fix and
> the measured evidence.

## Summary (historical)

An earlier session believed the custom love.js patching approach had failed after two attempts:
1. Separate-file approach: Game loaded forever (missing patches)
2. In-archive patching approach: `module 'bit' not found` (ZIP format issues)

The stated root cause was ZIP compatibility — Java's `ZipOutputStream` produces ZIPs with data
descriptors that PhysFS cannot parse, even when CRC/sizes are set explicitly.

## Why this was wrong

The diagnosis was right but the conclusion was wrong. Java's `ZipOutputStream` was indeed writing
data descriptors, but that was a **bug in our repacking code**, not a limitation of the approach.
Chunking the `.love` with STORED entries and explicit sizes produces a PhysFS-readable archive.

The `module 'bit' not found` error was a *symptom* of the malformed ZIP, not evidence that patching
was impossible. Once the archive was valid, the bundled Lua compatibility shims in
`src/client/resources/assets/craftlatro/lua/` (bit, nativefs, lovely, web_patches) resolved
correctly.

## Lesson

A single failing symptom was read as architectural failure. Two other compounding bugs also hid
behind it and were only found by measurement:

- love.js's IndexedDB package cache (`Player.cache = true`) never re-fetches, so a fixed archive on
  disk was still being ignored.
- No offscreen paint rate was ever set, so Chromium ran at its default.

**Verify a diagnosis is the whole story before writing a pivot decision.** The verbose record of the
fix in `ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md` is the correct reference for this area.

## Historical research directions (kept for reference)

### Direction 1: Use known-working web-balatro implementation
- **W0W53R/web-balatro**: Complete JS implementation, patches applied in-browser via JSZip
- **mauri870/balatro-web**: Go-based, extracts+repacks with system `zip` command
- **ytrewq000/Balatro-Web-Port**: JS-based with patches.js

### Direction 2: Native Balatro capture
- Run actual Balatro.exe as subprocess, capture window via graphics API
- Render captured frames in Minecraft via Rinku/Chromium texture
- Forward Minecraft input to the Balatro window
