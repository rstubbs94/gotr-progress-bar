# GOTR Progress Bar — Developer Context (claude.md)

This file preserves the project's rules, verified facts, and architecture so any future
session (human or AI) can continue without re-deriving them.

## Core rules (set by the project owner — never violate)

1. **100% compliance** with RuneLite Plugin Hub rules and Jagex Third-Party Client
   Guidelines. This plugin is passive display only. Never add: input automation, automatic
   "stand here"/tile indicators, menu entries that send server actions, autotyping,
   unhiding of hidden interfaces, or any external HTTP/data transmission.
2. **No guessing.** Every game ID (widget, varbit, script, item, object) must be verified
   against a primary source: an accepted Plugin Hub plugin's source code, the runelite/runelite
   repo, the OSRS Wiki, or in-client developer-mode inspection. Record the source as a comment
   in `GotrConstants.java`.
3. **If unsure of direction, ask the project owner.** Do not make scope decisions unilaterally.
4. Scope is deliberately narrow (differentiation from DatBear's "Guardians of the Rift
   Helper"): ONE timeline bar. No altar highlights, no essence counters, no notifications.

## What this plugin is

Two overlay bars for Guardians of the Rift, drawn in the client's native HUD style
(square corners, near-black translucent ground, hard-shadowed small text):

- **Bar A (between rounds)**: a plain "Next game m:ss" countdown. Nothing else — nothing can
  be done in that phase.
- **Bar B (in a round)**: ONE **scrolling timeline at constant scale** (user ruling v5,
  2026-07-13 — supersedes the earlier fixed-scale-with-length-estimate design, which visibly
  rescaled and was rejected). The bar shows a fixed 300 s window of game time; the "now"
  cursor is pinned at 25% and everything drifts smoothly past it. Mining region sand-filled,
  crafting trail green, portal marks at their (absolute) times: hatched ±10 s windows for
  future, solid ticks for past, bright + exact countdown while open. Future windows re-anchor
  ONLY when a portal actually spawns. A thin goal sub-bar underneath tracks fragments while
  mining and points (configurable metric) while crafting.
- **Guardian power is never displayed** (the game shows it already). It only feeds
  end-of-round detection. User decision — do not re-add power UI, and no round-length
  estimation (removed with the fixed-scale design).

## Game model — LIVE-VERIFIED 2026-07-13 (this corrects earlier assumptions!)

`OUTSIDE → WAITING_FOR_GAME → MINING_PHASE → RIFT_ACTIVE → GAME_END → WAITING_FOR_GAME`

- **"The rift becomes active!" starts the ROUND = the MINING phase**, not the crafting game.
  The 30/10/5 s chat countdown runs BETWEEN rounds, toward the round start.
- Between rounds (WAITING): HUD power = 0, rune indexes 0/0, guardianTicks = garbage negative.
- Mining (~120 s = 200 ticks): power parked at exactly max/10, rune indexes 0/0, and
  **script arg 10 (guardianTicks) is an exact tick countdown to the altars opening** —
  no chat anchor needed inside the round.
- Crafting (RIFT_ACTIVE): rune index > 0 (altars open). Power climbs 10% → 100% = win;
  power dropping to 0 from crafting = loss/close. maxPower varies per round (player count).
- **OUTSIDE gate: widget 746:2 non-null ONLY — never gate on region 14484.** The rune altars
  are separate map regions but the HUD widget and script 5980 persist inside them
  (live-verified: 286 continuous snapshots across altar visits). A region gate made the bar
  vanish and reset mid-round. An 8-tick grace period covers loading transitions.
- **Portals (live-measured 2026-07-13)**: full lifetime 44 ticks (~26 s); spawn-to-spawn
  cycle 141 s (confirms the 140 s constant). When a portal is first SEEN mid-life (login/join
  with one open), back-date its spawn by (44 − portalTicks) × 0.6 s or every later estimate
  inherits the offset — this was a real reported bug. First-portal delay (160 s) still
  unmeasured from a full round.
- GAME_END: 10 s summary ("Rift sealed - 190E / 210C"), then back to waiting.

## Verified game data (sources locked — do not change without re-verification)

| Constant | Value | Source |
|---|---|---|
| Region | 14484 | DatBear `MINIGAME_MAIN_REGION`, hawolt `MINIGAME_REGION_ID` |
| HUD update script | 5980 | hawolt `MINIGAME_HUD_UPDATE_SCRIPT_ID`; fires ~1/tick in GOTR (live-verified) |
| Script args 1–11 | energies, power, max power, portal loc, rune idx ×2, guardians ×2, guardian ticks, portal ticks | hawolt `MinigameSlice.onScriptPreFired`; arg 10 = exact mining countdown during mining, garbage negative between rounds (live-verified) |
| HUD parent widget | 48889858 (group 746 child 2) | DatBear `PARENT_WIDGET_ID` |
| Power text widget (fallback) | 48889874 (746:18) | hawolt |
| Elemental/catalytic point varbits | 13686 / 13685 | DatBear `onVarbitChanged` |
| Inventory container | 93 | hawolt `INVENTORY_CONTAINER_ID` |
| Guardian fragments item | `net.runelite.api.gameval.ItemID.GOTR_GUARDIAN_FRAGMENT` | DatBear import (named core constant — do not hardcode) |
| Chat anchors | exact strings in `GotrConstants` | DatBear `onChatMessage`, verbatim incl. trailing periods |
| Portal cadence | first ~160 s after rift opens, then ~140 s, open ~25 s | OSRS Wiki (estimate only; exact open-portal countdown = script arg 11) |

Reference repos (both are accepted Plugin Hub plugins):
- https://github.com/DatBear/Guardians-of-the-Rift-Helper
- https://github.com/hawolt/guardian-of-the-rift

## Architecture

```
GotrProgressBarPlugin      thin event router; owns the GotrSession; hard in-game gate
GotrProgressBarConfig      @ConfigGroup("gotrprogressbar"): fragmentGoal, pointsMetric,
                           pointsGoal, barWidth, showPortalMarkers, goal colors
GotrConstants              ALL raw ids + timing, each with a source comment
model/GotrSession          THE state machine + timeline data (portalMarks in absolute time,
                           spawn back-dating, goal tri-states). Pure Java — never touches
                           Client. Scroll geometry lives in the overlay (xOf mapping).
model/HudSnapshot          immutable, defensive parse of script-5980 args (null on mismatch);
                           classifies isMiningPhase / isCrafting / isIdle / isGameWon
model/PortalMark           one timeline marker in absolute time (PAST / ACTIVE / FUTURE)
model/PointsMetric         crafting goal metric (Combined / Elemental / Catalytic / Both split)
model/PortalPrediction     spawn history + anchor bookkeeping behind portalMarks
overlay/GotrProgressBarOverlay  the entire visible product; custom Graphics2D; one Overlay
                           so it drags/persists as a unit (TOP_CENTER default)
```

UI ground truth: mockups at
https://claude.ai/code/artifact/b8a34b05-e7b4-4598-a64f-46901c015f7b (v4) show the layout
vocabulary (marker styles, sub-bar, texts), but the geometry ruling changed after live
testing (v5): the bar is a FIXED-SIZE SCROLLING window (300 s span, cursor pinned at 25%) —
markers drift smoothly, they never rescale. Other standing rulings: no power display
anywhere; plain countdown between rounds (no sub-bar there — nothing can be done in that
phase); goal sub-bar with configurable points metric while in a round.

Design invariants:
- `GotrSession` and `PortalPrediction` stay client-free so unit tests need no mocks.
  All time comes in as `java.time.Instant` parameters.
- The HUD script (5980) is the primary data feed; chat messages anchor countdowns; the
  widget/varbit reads are secondary. Script arg layout is unofficial — parsing must stay
  defensive (`HudSnapshot.fromArgs` returns null, plugin logs once).
- The portal estimate is always labelled as an estimate and is suppressed while a portal
  is open. It is information, never a directive (compliance).

## Known gaps / future work (do not implement without verification)

- **Portal cadence constants (160/140 s)** are wiki-sourced AND the wiki's baseline is now
  ambiguous (160 s from round start incl. mining, or from altars opening?). Measure actual
  first-spawn rel times from live HUD logs (portalLoc > 0 transitions) and correct
  `FIRST_PORTAL_DELAY_SECONDS` if needed.
- **Barrier status**: no known readable source for barrier health; neither reference plugin
  reads it. Feature dropped from the design (user chose a leaner bar); revisit only if asked.
- ~~Earlier game-start anchor~~ RESOLVED: script arg 10 is an exact mining countdown; between
  rounds only the 30/10/5 s chat anchors exist (~60 s intermission).

## Dev workflow

- JDK 11 required (`C:\Program Files\Eclipse Adoptium\jdk-11.0.29.7-hotspot` on this machine).
- Build + tests: `gradlew.bat build`
- Launch client with plugin: `gradlew.bat run` (starts RuneLite `--developer-mode --debug`)
- Full process docs: `docs/dev-testing.md`, `docs/deployment.md`, `docs/hub-submission.md`.

## Style

- RuneLite conventions: tabs, braces on own lines, `@Slf4j` logging, Guice `@Inject`,
  `@Subscribe` event handlers, config via `Config` interface + `@Provides`.
- No third-party dependencies beyond what runelite-client provides (keeps Hub review trivial).
