# Decision: Pivot to Alternative Balatro Web Port

## Status: 2026-10-11

## Summary
Our custom love.js patching approach has failed after two attempts:
1. Separate-file approach: Game loaded forever (missing patches)
2. In-archive patching approach: `module 'bit' not found` (ZIP format issues)

The root cause is ZIP compatibility - Java's `ZipOutputStream` produces ZIPs with
data descriptors that PhysFS cannot parse, even when CRC/sizes are set explicitly.
The original Balatro.exe ZIP also uses this format, and while raw extraction works
for reading, repacking with Java's ZIP library does not produce PhysFS-compatible output.

## Research Directions

### Direction 1: Use known-working web-balatro implementation
- **W0W53R/web-balatro**: Complete JS implementation, patches applied in-browser via JSZip
- **mauri870/balatro-web**: Go-based, extracts+repacks with system `zip` command
- **ytrewq000/Balatro-Web-Port**: JS-based with patches.js

### Direction 2: Native Balatro capture
- Run actual Balatro.exe as subprocess, capture window via graphics API
- Render captured frames in Minecraft via Rinku/Chromium texture
- Forward Minecraft input to the Balatro window

## Next Steps
Awaiting user direction on which approach to pursue.
