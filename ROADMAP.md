# craftlatro Roadmap

## Project Goals

- Run the genuine Balatro game (1-to-1 logic: all decks, stakes, jokers, blinds, profiles) from
  inside Minecraft Java Edition, by composing an existing browser-embed library (Rinku) with
  the love.js web port — not by reimplementing Balatro's rules.
- The player supplies their own Balatro.exe; the mod extracts its `.love`, packages it with the
  2dengine love.js player, and serves it to an in-game Chromium browser on a flat GUI screen.
- Never bundle or distribute Balatro code/assets; ship only the integration glue
  (extraction/serving, in-game screen, input mapping).

## Project Non-Goals

- Reimplementing Balatro's jokers, decks, stakes, blinds, or RNG by hand (this is integration
  glue, not a clone — rejected path: LuaJ + from-scratch LÖVE API).
- Bundling Balatro code/assets or the Chromium/love.js runtime in the mod jar.
- Java Edition is the only target (not Bedrock/C++ add-ons).

## Shipped

- [x] M1: Scaffolding and dependency integration — Fabric 1.26.2 mod project with Loom, Rinku 3.0.4-26.2 dependency
  resolves and compiles. `docs/rinku-api-notes.md` documents the full public API surface. A flat GUI browser
  screen (`BrowserScreen`) is scaffolded using Rinku's `RinkuBrowser` rendering and input mapping.

## Current Objectives

- [x] Scaffolding and dependency integration (M1, done)
- [ ] Balatro.exe -> love.js package pipeline (M2)
- [ ] In-game screen surface (M3)
- [ ] Controls, UX, and fidelity checks (M4)
- [ ] Packaging and legal guardrail (M5)

## Planned

- (M1-M5 are the current objectives; see PLAN.md for the detailed breakdown.)

## Out of Scope

- In-world 3D / computer-block display surface — deferred to a later milestone (CONCEPT.md
  resolved decisions; M3 targets the flat GUI only).
- Minecraft Bedrock / add-on integration.
- Running Balatro's native `.exe` directly without the love.js web port.

## Aspirational Goals

- A reusable, loader-agnostic "browser-as-a-screen" helper extracted from the Rinku integration.
- Save-game handoff between the love.js IndexedDB profile and a Minecraft-side config so progress
  persists across launcher sessions (see M4).

> Guidance from `references/roadmap-guide.md`. Populate from PLAN.md before first release.
