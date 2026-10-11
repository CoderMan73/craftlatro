# AGENTS.md - craftlatro

## Agent Role

Specialist maintainer of **craftlatro**. Prioritize correctness of the integration glue, keep
third-party/externally-supplied content out of the repo (see Boundaries > Never), and verify
each milestone against PLAN.md (tracked in the tracking workspace, not in this repo).

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Fabric Loader + API | 1.26.2 | Mod loader for Minecraft Java Edition |
| Java | 21 | Language/runtime (MC 1.26 / Rinku requires Java 21+) |
| Gradle | Fabric Loom | Build system |
| Rinku | 3.0.4 (Chromium 151.0.7922.34) | Embedded Chromium browser via `de.keksuccino:rinku-fabric:3.0.4-1.26.2` (Maven: https://keksuccino.github.io/maven/) |
| love.js | 11.5 (2dengine player) | Web port of LÖVE; runs the user's extracted `.love` |

## Key Commands

```bash
./gradlew genIntellij  # Generate IDE run configs (Fabric Loom)
./gradlew build        # Build the mod jar
./gradlew runClient    # Run a Minecraft client with the mod
./gradlew check        # Run tests (none yet)
./gradlew idea         # Alternative IDE config task
```

## Architecture

Integration glue only — the mod does **not** reimplement Balatro. Data flow at runtime:

1. `Balatro.exe` (user-supplied, recorded via chat link / keybind / command) -> mod extracts the
   `.love` (reuse of the web-balatro / Balatro-Web-Port extraction approach).
2. Mod bundles the `.love` with the love.js (2dengine) player into a served bundle.
3. A minimal local HTTP server (`java com.sun.net.httpserver`) serves the bundle with
   COOP/COEP headers on `http://localhost:<port>` (required: love.js 2dengine player won't run
   from `file://`).
4. Rinku v3.0.4 renders the local URL in an embedded Chromium on a flat Fabric GUI screen.
5. Minecraft keyboard/mouse input is mapped into the embedded browser view.

Key directories:
- `src/main/java` - Mod source (Rinku screen, HTTP server, `.love` extraction, file-picker UX)
- `src/main/resources` - `fabric.mod.json`, mod metadata/assets, key bindings
- `ai-docs/` - AI field notes, decisions, lessons learned across sessions
- `docs/` - Human-facing documentation only

## Code Style and Conventions

- Java 21, standard Minecraft modding conventions (Fabric Loom). Write self-documenting code;
  prefer explanatory method names over inline comments. The one exception is non-obvious
  behavior (e.g. COOP/COEP header necessity, love.js compatibility quirks) which the guidelines
  elsewhere already mark as a gotcha.
- Functional style where practical; avoid deeply nested Minecraft client event handlers.

## Testing

- **Framework**: none configured yet (Fabric mod; M1 will evaluate a client-side test harness).
- **Run all**: `./gradlew check`
- **Coverage target**: _TODO (set in M1)_

## Boundaries

### Always
- Keep externally-supplied content (e.g. Balatro code/assets, the LÖVE runtime) out of the
  repo and out of the mod jar.
- Record non-obvious findings in `ai-docs/decisions/` or `ai-docs/notes/`.

### Ask First
- Creating a remote repository or pushing tags/releases.
- Changing the project license or adding dependencies with non-open runtimes.

### Never
- Bundle third-party game code/assets or redistributable runtimes in the jar or repo.
- Hardcode paths or credentials; store user file-chooser state under the config directory.

## Context Loading

| Task | Read |
|------|------|
| Start work | PLAN.md (tracking workspace: `E:\Coding_Projects\future-projects\public/active/craftlatro/PLAN.md`), AGENTS.md, this file |
| Runtime docs | `docs/` |
| Field notes | `ai-docs/` |

## Gotchas

- Rinku's public API for driving a flat GUI browser screen programmatically is unverified
  (RESEARCH.md unknown area 1). Inspect Rinku source at M1 kickoff; fall back to
  Browsermod-entrypoint reuse if no programmatic surface exists.
- love.js (2dengine player) won't run a `file://` page, so the local HTTP server with COOP/COEP
  headers is mandatory (RESEARCH.md Gap 5). Compatibility mode (`-c`) is the fallback.
- love.js fidelity is not 1-to-1 with native Balatro: RNG/shader/audio differ and seed parity
  is not guaranteed (RESEARCH.md Gap 3). Warn the player in-game on first run.
- The Chromium runtime is ~150 MB and is Rinku's responsibility, fetched at runtime — do not
  bundle it in the mod jar.
- love.js requires COOP/COEP headers if served to a browser.
- The in-world 3D/computer-block surface is deferred (CONCEPT.md resolved decisions); M3 is
  the flat GUI only (RESEARCH.md Gap 4).

## Local Skills

This project may define repo-local skills in `.agents/skills/`. When starting a task, scan
this folder for skills whose descriptions match the current work.

- **Discovery**: Check `.agents/skills/` at task start.
- **When to use**: Any skill whose description matches the task at hand.
- **When to suggest**: Recurring, repeatable processes worth standardizing.
- **When NOT to suggest**: One-off decisions, single-use observations (use ai-docs/).

## ai-docs

This project uses an `ai-docs/` subdirectory for field notes, architectural decisions, and
lessons learned. See `ai-docs/README.md` for guidelines on recording knowledge between
agent sessions.

> Guidance from `references/agents-guide.md`.
