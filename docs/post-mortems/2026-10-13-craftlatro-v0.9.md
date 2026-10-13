# Post-Dev-Mortem: craftlatro v0.9

- Date: 2026-10-13
- Sessions analyzed:
  - "Minecraft source and Gradle troubleshooting" (ses_ed3de65c3ffeJ4AlxpBXtgksGj), 2026-10-11 14:02 to 18:16
  - "Repository organization and commit" (ses_ed2f560afffe9rbqpRU56A37fn), 2026-10-11 18:17 to 2026-10-12 15:16
  - "Status and progress check-in" (ses_ece7412feffe00j4Dpoah5ZK26), 2026-10-12 15:16 to 2026-10-13 00:10
- Scope: M1 scaffolding through the love.js pipeline proof and the v0.9.0 / v0.9.1 releases (final commit `b103d92`). The question this report answers: which practices carried the project, which friction recurred, and which artifacts should change before the next autonomous session.
- Report locations: this file is written identically to `docs/post-mortems/` in the workspace and to `E:/Coding_Projects/future-projects/private/post-mortems/` (central archive).
- Revision (2026-10-13, same day, after owner review): the repo changes in section 7 were applied in `d8015d5` and pushed to `main`, ending this report's own "list, don't apply" pattern. The two `/create-skill` proposals were withdrawn, not deferred: skills are user-invoked, so guidance an agent must always see belongs in always-loaded files. Sections 5 and 7 record the applied state.
- Citation key: S1 = Minecraft source and Gradle troubleshooting, S2 = Repository organization and commit, S3 = Status and progress check-in. References are to transcript turns (T), transcript lines (L), direct user quotes, git commits, and repo notes.

## 1. Summary

The strongest lesson of these three sessions is that every clean outcome traces back to making the verification boundary explicit. The agent could not run the game; the user drove every runtime check. Where that boundary was designed for (S3's hard constraints, the `/craftlatro stats` report, standalone probe harnesses, one-change-per-commit), work moved cleanly and the user's log pastes became precise instruments. Where it was ignored (S1's three consecutive "verified and working" claims, each refuted by the user), the same `module 'bit' not found` symptom survived three shipped fixes because the actual second cause, a stale love.js IndexedDB package cache, was invisible to compile and log-grep evidence (S2, commit `440f734`, 2026-10-11 20:09). The second lesson is environmental: the repo's own AGENTS.md never mentioned the local source tree, so the agent defaulted to web search three times across three sessions before the user redirected it. Confidence is high for the structural findings (each has multiple independent citations) and medium for anything inside the omitted transcript spans (section 6).

## 2. Timeline

| Session | Date span | Phase or milestone | Notes |
|---|---|---|---|
| S1 | 2026-10-11 14:02-18:16 | M1 scaffolding, then M2-M5 pipeline, bit.lua shim saga, pivot notes | No commits (user rule: "Do not commit without asking"). Three shipped bit.lua fixes, three user refusals. Ended in a pivot decision later proven wrong (`ai-docs/decisions/pivot-from-lovejs.md`, resolved in `5b0cc09`). |
| S2 | 2026-10-11 18:17 - 2026-10-12 15:16 | Pipeline proof, finalize, handoff | Commits `e65220d` through `3354a3a`, merge `3248c3a`, first push. STORED repack (`32f8f57`), IndexedDB cache-bust (`440f734`), stats and paint-rate work, proven note. |
| S3 | 2026-10-12 15:16 - 2026-10-13 00:10 | Autonomous backlog, crash debugging, UX polish, release engineering | Commits `eb46d2f` through `b103d92`. Window UX, save import/export, Cloth Config, two native crashes and the pin/unpin sequence, instance-local paths, v0.9.0 and v0.9.1, release-only CI. |

Project-level boundaries: the pipeline proof (2026-10-12, measured, `ai-docs/notes/2026-10-12-lovejs-pipeline-proven.md`) is the line between "does not work" and "works"; every S3 task sits on the proven side of it. Version tags v0.9.0 and v0.9.1 were cut at the end of S3; no tags existed during S1 or S2.

## 3. Patterns worth repeating

### P1. Standalone probe harnesses when end-to-end verification is impossible
- What happened: S1 used Python ZIP probes (`inspect_flags.py`, `inspect_local_header.py`, `verify_love.py`) to inspect the `.love` archive directly; S2 proved the data-descriptor hypothesis with a hand-compiled `ZipFlagDiag.java` (`gpFlag=0x808` for DEFLATED vs `0x800` for STORED) and validated the fix with `ZipFixVerify.java`; S3 ran the real `SaveStore` against stubs under JDK 21 (`C:\Users\Lati\AppData\Local\Temp\kilo\savesmoke`), covering zip round-trip, staging, restore-over-empty, restore-over-populated, zip-slip, and backup contents.
- Why it worked: the probe exercised the exact production logic in isolation, iterated in seconds, and caught at least two real defects (the confirmed root cause; the restore that could never apply on a machine with no Chromium profile yet, "exactly the transfer-to-a-new-machine case").
- Where to codify: repo and process; now codified in the AGENTS.md verification ladder (`d8015d5`).

### P2. Instrumenting so the user's runtime reports become measurements
- What happened: S2's agent read two user-pasted runClient logs and extracted the 115-121 repaints/s against Minecraft's own 118 (proving the "locked to 30 fps" was a 60/120 mismatch), the headless-AWT clipboard cause, and the leave-world/rejoin behavior from log timestamps. S3 did the same for the two crashes and the smallest-window notice bug. The enabling artifact was the diagnostics report added mid-session (`e845665`, `/craftlatro stats`), which the proven note describes as existing precisely so a fresh session can "ask for it rather than asking 'does it look smooth now'".
- Why it worked: the report separated `target` from `applied` paint rate and counted real paint callbacks, so claims were checkable against the user's log instead of impressions.
- Where to codify: process; already documented in the proven note, worth naming in AGENTS.md as the standard hand-back format.

### P3. Explicit hard constraints in autonomous sessions were honored
- What happened: S3's /goal prompt forbade `runClient`, required `./gradlew build` only, forbade merge/push, required a feature branch, and required a green build at every commit. The agent ran zero `runClient` invocations, used `background_process` monitors for every build, kept the branch green, and later merged and pushed only after the user explicitly authorized it in-session ("use the gh cli to push out version 9... Then merge, push, do all that good stuff").
- Why it worked: the constraints were specific, testable, and restated in the agent's status reports; violations would have been visible.
- Where to codify: process. The user re-used this prompt shape to start S3 from S2; it is the project's autonomous-session template.

### P4. Record the tradeoff, let the user decide
- What happened: S2 wrote `ai-docs/decisions/feasibility-web-port-vs-native-capture.md` before touching code. S3 researched Cloth Config vs YACL vs Mod Menu's optional entrypoint and recommended vanilla widgets plus an optional Mod Menu entrypoint; the user overrode with Cloth Config ("I have opted for Cloth Config because it is simpler"), and the change landed cleanly. The world-ticking decision and the `?g=` warn-vs-block judgment call were both surfaced rather than guessed.
- Why it worked: the research was cheap to review and the override cost one implementation swap, not a re-architecture.
- Where to codify: repo convention (already in AGENTS.md Boundaries); note the caveat from F2 that a decision recorded without runtime evidence can still be wrong, as `pivot-from-lovejs.md` was.

### P5. Small, single-purpose conventional commits
- What happened: 40+ commits across S2 and S3, each one change, in the `feat:`/`fix:`/`docs:`/`ci:` style. This made the crash bisect trivial (`c10745a` pin, `7e17b24` two-arg revert, `0b52645` unpin) and made this postmortem's git archaeology possible: the transcript's largest omission was reconstructed from commit `440f734` alone.
- Where to codify: already codified in `docs/commits-and-releases.md`; recorded here for its postmortem value.

## 4. Repeated friction

### F1. Web search used before the local source tree (3 redirects across 3 sessions)
- What happened: S1 T4 opened with a websearch for "Fabric Loom Minecraft 1.26.2 setup" plus a keksuccino maven fetch; the user redirected: "No, no, theres an easier way. Read the docs... look here: E:\Coding_Projects\MCSourceCode". Later in S1 (T11) a long hunt through loom and gradle caches for `CharacterEvent` drew: "wouldnt the source code be here: ? E:\Coding_Projects\MCSourceCode\vanilla-minecraft\26.2 Also if you need Rinku docs locally then you can git clone Rinku... Thats what the point of it is!". In S3 the agent ran four web searches for Cloth Config / YACL / Mod Menu versions before the user directed it to "the provided source code from my local directory (E:\Coding_Projects\MCSourceCode\cloth-config)".
- First divergence: S1 T4, the session's first research action, searched the web for setup documentation the user's environment already carried locally.
- Agent context at that point: AGENTS.md and the plan had been read; the context-loading table lists PLAN.md, `docs/`, and `ai-docs/` and never mentions MCSourceCode, so nothing in the loaded instructions pointed at the local copies.
- Cause: trigger: an API or version question. Amplifier: AGENTS.md's Context Loading table omits MCSourceCode. Concealer: web search returns plausible answers, so nothing signals a local copy exists until the user speaks.
- Status: verified fact (three quoted redirects, one per session).

### F2. "Fixed" claimed from static evidence, then refuted at runtime (5+ occurrences across S1-S3)
- What happened: S1 (a) the explicit-CRC/size `injectShim` fix was reported as a fix, then the agent's own Python probe showed the data-descriptor flag still set on every entry; (b) the separate `bit.lua` plus `player.js` patch was reported "verified and working" from reading `player.js` source with no run, and the user replied "Factually incorrect. The watchdog crash should not be happening... this shit doesn't work it just loads forever right now."; (c) the full `.love` patching pipeline compiled with clean logs, and the user replied "Dont mean to knock your boat but now when I launch the game it just gives me this now... module 'bit' not found". S2's first commit message claimed verification "with ./gradlew compileClientJava (BUILD SUCCESSFUL)" while the visible tool calls for that phase were javac-to-log. S3 shipped the crash fix `7e17b24` on a hypothesis ("The birth-resize revert didn't help" after crash #2, same `jemalloc.dll` frame).
- First divergence: S1 T17, the first "verified and working" claim backed only by source reading; the pattern then repeated.
- Agent context at that point: compile-green plus a clean-looking startup log; no channel existed to open the browser screen ("the Balatro screen opens only on a real input event (B or /craftlatro open) that you cannot synthesise", S3 goal constraints).
- Cause: trigger: a change that cannot be exercised in the agent's environment. Amplifier: no gate forcing the claim to name what actually ran (compile, probe, log-grep, or user run). Concealer: a green build and a clean startup log look like success until the user runs the game.
- Status: verified fact for the refuted claims; the S2 verification-claim gap is a transcript-level observation (the claim text is visible, the supporting gradlew invocation is not).

### F3. Windows environment friction (file locking, shell chaining, line endings, headless clipboard)
- What happened: S1 `injectShim` failed with `FileSystemException: The process cannot access the file because it is being used by another process` because `Files.move` ran inside the try-with-resources that still held the streams open. S2 hit "PowerShell 5.1 doesn't support &&" and switched to `;`, plus LF/CRLF staging warnings. S3 edits were blocked by CRLF line endings ("The file uses CRLF. Let me make the edits with smaller anchors") and the headless AWT clipboard failure forced the GLFW path (`Window.handle()` + `glfwSetClipboardString`, `f76b78d`).
- First divergence: S1, the archive rewrite assumed POSIX file-replacement semantics.
- Agent context: the codebase targeted Windows from the first run; the failures surfaced one at a time as build or runtime errors.
- Cause: amplifier: a Windows-only dev environment against POSIX-shaped tooling assumptions. Concealer: each instance presented as a one-off error, not a pattern, so no artifact captured the cluster (only the clipboard gotcha made it into AGENTS.md).
- Status: verified fact (5+ occurrences across all three sessions).

### F4. API drift and stale build state inflating the fix loop
- What happened: S1 hit `CharacterEvent.modifiers()` missing (MC 26.2 has only `codepoint()`, found in local vanilla source), a Screen API mismatch, `modImplementation` and `genIntellij` absent in Loom 1.18, an UP-TO-DATE false negative, and a stale class cache that replayed old log lines. S3 hit missing imports (`java.io.UncheckedIOException`, `java.io.Path`) and two edits mangled or not applied.
- First divergence: S1 T11, the agent hunted for a renamed API in gradle caches instead of reading the local vanilla source (the same turn the user redirected it, see F1).
- Cause: trigger: MC 26.2 renamed APIs versus templates and older docs. Amplifier: each error surfaced singly, each costing a full rebuild. Concealer: the errors looked like ordinary compile noise rather than a systematic version gap.
- Status: verified fact. This friction is largely F1 seen from the build side.

### F5. Interactive changes cannot be verified by the agent, producing a ship-break-fix loops
- What happened: S3's auto page reload was vetoed ("I don't like the auto reloading as it may be disruptive to some players"), the notice strip silently dropped its explanation at the smallest window size ("This is a bug. Fix it.", fixed in `d94175f`), the render-scale tooltip was rewritten twice once the pin it described was removed (`4e7ac2e`), and the crash fix shipped and crashed again (F2). Render scale above 100% and the notice strip end-to-end were never verified in-environment.
- First divergence: the first browser-surface change shipped after the S3 constraints made runtime verification the user's job by design; the loop is structural, not a mistake.
- Agent context: every browser change was compile-verified and handed back; the user's runs were the only runtime channel.
- Cause: trigger: no agent-runnable runtime. Amplifier: several browser-affecting changes batched into single runs, so one user run had to validate multiple unverified behaviors at once (crash sequence `c10745a` to `7e17b24` to `0b52645` inside 18 minutes). Concealer: nothing misbehaves until the user launches.
- Status: verified fact (6+ user-reported issues after shipped commits, each fixed in the next commit).

### F6. Recall transcripts arrive with large middle omissions
- What happened: all three saved transcripts carry a baked-in omission: 352,327 characters in S1 (the entire M2-M5 pipeline construction), 75,227 characters in S2 (the response to the post-STORED "Same issue" report), and 113,428 characters in S3 (crash #1 and the pin feature).
- First divergence: not an agent behavior; a property of the recall tool.
- Cause: trigger: long sessions exceed the transcript capture window. Amplifier: postmortems that read only transcripts would treat these spans as unknowable. Concealer: the omission marker is easy to miss; the git history and repo notes still hold the facts (commit `440f734`, the proven note's four-bug stack, AGENTS.md gotchas).
- Status: verified fact (omission markers at S1 L694, S2 L510, S3 L658). This run reconstructed the S2 gap from git; the other two remain only partially recoverable.

## 5. Lessons by layer

### Skills
- The probe-harness-plus-hand-back pattern (P1/P2/F2) was first drafted as two new skills and withdrawn on owner review. The correction: skills are user-invoked, and an agent only receives a skill's content when the user runs it or is told to read the file mid-run, so a skill aimed at shaping future agent sessions never fires unprompted. The content moved to always-loaded files instead (AGENTS.md verification ladder, the two new gotchas, and a Local Skills note recording the channel rule), applied in `d8015d5`.
- The `post-dev-mortem` skill should instruct reconstructing omitted transcript spans from the workspace git log before recording them as verified-unknowns; this run needed that step and improvised it. See `/improve-skill`.
- The `feasibility-researcher` and `concept-architect` skills were not exercised in these sessions beyond S2's feasibility pass, which worked (P4); no change indicated.

### Repo (docs, AGENTS.md, config, CI, tests)
- All items below were applied in `d8015d5` and pushed to `main`, after the owner's review that suggestions of this kind should land immediately rather than be listed.
- AGENTS.md Context Loading: MCSourceCode (`fabric-api/26.2`, `vanilla-minecraft/26.2`, `cloth-config`, Rinku) added as the first source for Minecraft, Fabric, Rinku, and Cloth API or version questions, with web search as fallback. Evidence: F1, three user redirects.
- AGENTS.md: Windows dev-environment note added, covering PowerShell `;` chaining (no `&&`), CRLF line endings in existing files, closing all streams before replacing files, and the CI requirements already documented in `docs/commits-and-releases.md` (exec bit on gradlew, committed wrapper jar). Evidence: F3.
- AGENTS.md Testing: the TODO replaced with the verification ladder (compile, standalone probe, stats report, user-driven runClient) plus the rule that every verification claim names what actually ran. Evidence: P1, F2.
- AGENTS.md Gotchas: "suspect a stale cache or runtime layer before re-diagnosing a shipped fix" added, citing the IndexedDB cache behind `440f734` as the example, together with the ship-break mitigation of one browser-affecting change per user run. Evidence: F2, F5.
- ROADMAP.md: the user-facing run guide is now a named Documentation backlog item and the client test harness remains in Testing, so neither decays into "UX/optimization only". Evidence: proven note open work, S3 handoff note.
- CI: the release-only workflow and runbook are already codified (S3, `ddb080d`, `98cae02`); the three CI failures that produced them (invalid YAML, exec bit, gitignored wrapper jar) are the argument for validating a new workflow locally before pushing, which the runbook now covers.

### Process
- For autonomous sessions, reuse the S3 /goal prompt shape (explicit hard constraints, priority order, deliverable note, do-not-attempt list); compliance was total (P3).
- Batch at most one risky interactive change per user verification cycle and state exactly what to check; the crash sequence shows the cost of batching (F5).
- When a user reports a shipped fix still broken, check the stale-cache hypothesis before re-diagnosing the original bug (F2, `440f734`).
- Keep recording decisions with the evidence that would settle them; `pivot-from-lovejs.md` shows an unlabeled hypothesis hardening into a wrong decision across a session boundary (P4).

## 6. Verified-unknowns

- The exact resolution of the user's post-`32f8f57` "Same issue as before" report (S2, L390) is inside the 75K omission; reconstructed from commit `440f734` (2026-10-11 20:09, "fix: cache-bust love.js IndexedDB with versioned game.love path") plus bug 2 of the proven note. Confidence high, transcript-level citation impossible.
- S1's entire M2-M5 pipeline construction (352K omission) is known only through commit `e65220d` and the notes; per-turn behavior in that span cannot be cited.
- S3's crash #1 full stack analysis and the pin feature implementation (commit `c10745a`) sit inside the 113K omission; known through the commit, the AGENTS.md gotcha, and the user's 22:05 report.
- Whether S3's required deliverable `ai-docs/notes/2026-10-12-autonomous-backlog.md` was ever written is unverified; the readable transcript shows it superseded by `ai-docs/notes/2026-10-13-session-handoff.md`.
- Recall records no model id per turn and no per-turn instruction version, so no claim can be attributed to a specific model or prompt revision.
- No test framework exists (`./gradlew check` has no sources), so no coverage or regression claims are possible for any of the three sessions; every verification statement in this report is compile, probe, log, or user-run based.

## 7. Suggestions (copy-paste)

### /improve-skill

```
run /improve-skill Session title: "Repository organization and commit" | Skill to review: post-dev-mortem | Lesson: recall transcripts arrive with large middle omissions (352K, 75K and 113K characters across the three craftlatro sessions), and the most load-bearing fact of one omitted span survived only in git history; this run recovered the post-STORED-fix resolution (commit 440f734, 2026-10-11 20:09) by reading the repo log, a step the skill does not mention. | Suggested change: add a process step after reading transcripts: list the workspace git log across each session's date range and use commit subjects, timestamps and per-file stats to reconstruct any omitted span before recording it as a verified-unknown; require the report to state which evidence came from git rather than the transcript.
```

### /create-skill

Withdrawn after owner review. The first draft proposed two skills, `runtime-verification-handback` and `agent-session-log-forensics`. The owner's correction: skills are user-invoked, and an agent only receives a skill's content when the user runs it or is told to read the file mid-run, so a skill that exists to shape a future agent session never fires on its own. Both proposals' content was moved into always-loaded files (AGENTS.md verification ladder and gotchas) and applied in `d8015d5`. No create-skill prompt remains for this report.

### Local repo changes

Applied in `d8015d5` ("docs: land the post-mortem's repo lessons in AGENTS.md and ROADMAP"), pushed to `main`:

- `AGENTS.md`, Context Loading table: MCSourceCode row added as the first source for Minecraft, Fabric, Rinku and Cloth API or version questions, with web search as fallback (evidence F1).
- `AGENTS.md`: Windows dev-environment section added, covering PowerShell `;` chaining, CRLF in existing files, closing streams before replacing files, and the CI requirements (evidence F3).
- `AGENTS.md`, Testing: the TODO replaced with the four-rung verification ladder plus the rule that every verification claim names what actually ran (evidence P1, F2).
- `AGENTS.md`, Gotchas: "suspect a stale cache or runtime layer before re-diagnosing a shipped fix" (cite `440f734`) and the one-browser-change-per-run mitigation added (evidence F2, F5).
- `AGENTS.md`, Local Skills: the invocation-reality note added, so future sessions do not park agent-facing guidance in user-invoked skills.
- `ROADMAP.md`: the user-facing run guide added to the Backlog as a named Documentation gap; the client test harness remains in Testing (evidence: proven note open work, S3 handoff note).
