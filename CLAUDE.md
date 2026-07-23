# CLAUDE.md — GOTR Progress Bar

Instant-context doc for any future session (human or AI). Read this first; it captures the
rules, the hard-won game knowledge, the design decisions and *why*, the code architecture,
and the exact dev + deployment processes. Everything here was verified in-client or against
primary sources — do not silently change "verified" facts without re-verifying.

- **Plugin repo:** https://github.com/rstubbs94/gotr-progress-bar (branch `master`)
- **Plugin Hub PR:** https://github.com/runelite/plugin-hub/pull/14236 (submitted, CI green,
  awaiting maintainer review as of 2026-07-23)
- **Local path:** `C:\Dev\runelite plugin\gotr-progress-bar`
- **Author / RSN:** SpoonedScrub (GitHub rstubbs94)

---

## 1. What this plugin is

A passive RuneLite overlay for the Guardians of the Rift (GOTR) minigame. It renders a whole
round as a single at-a-glance bar so the player knows the phase and when the next portal is due
without reading chat or the HUD. Deliberately narrow scope to differentiate from the existing
"Guardians of the Rift Helper" (DatBear) plugin: **timeline bar only** — no altar highlights,
no essence/cell tracking, no notifications.

Two overlay states:
- **Between rounds:** a plain `Next game m:ss` countdown bar. Nothing else — nothing is
  actionable in that phase. (Can be hidden via config.)
- **In a round:** one **fixed-scale, camera-scrolling timeline** with a thin goal sub-bar under
  it (fragments while mining, points while crafting).

---

## 2. Core rules (owner-set — never violate)

1. **100% Plugin Hub / Jagex compliance.** Passive display only. NEVER add: input automation,
   automatic "stand here"/tile indicators, menu entries that send server actions, autotyping,
   unhiding of hidden interface elements, or any external HTTP / data transmission. The portal
   estimate is *information*, always labelled an estimate, never a directive.
2. **No guessing.** Every game ID (widget, varbit, script, item, object, chat string, timing)
   must come from a primary source: an accepted Plugin Hub plugin's source, runelite/runelite,
   the OSRS Wiki, or in-client developer-mode inspection. Record the source as a comment in
   `GotrConstants.java`.
3. **If unsure of direction, ASK the owner.** Do not make scope/UX decisions unilaterally.
4. **Verify UI claims against live data.** The owner iterates on visuals via screenshots; when
   debugging behaviour, check the actual HUD logs before asserting a cause.

---

## 3. Repository layout

```
gotr-progress-bar/
├── build.gradle              standard RuneLite external-plugin template (Gradle 8.10, Java 11)
├── settings.gradle           rootProject.name = 'gotr-progress-bar'
├── gradlew / gradlew.bat     wrapper (LF-enforced for gradlew via .gitattributes — see §11)
├── .gitattributes            forces LF on gradlew so the Linux Hub CI can execute it
├── LICENSE                   BSD 2-Clause (Hub requirement)
├── runelite-plugin.properties  Hub manifest (displayName/author/description/tags/plugins/build)
├── icon.png                  48x42 (Hub limit: <=48x72 and w*h<=5000)
├── README.md                 user-facing; references screenshots/{mining,crafting}.png
├── CHANGELOG.md
├── CLAUDE.md                 this file
├── screenshots/              mining.png, crafting.png (used by README + PR)
├── docs/                     dev-testing.md, deployment.md, hub-submission.md
└── src/
    ├── main/java/com/gotrprogressbar/
    │   ├── GotrProgressBarPlugin.java     event router; owns GotrSession; in-game gate
    │   ├── GotrProgressBarConfig.java     @ConfigGroup("gotrprogressbar")
    │   ├── GotrConstants.java             ALL raw ids + timing, each with a source comment
    │   ├── model/
    │   │   ├── GamePhase.java             OUTSIDE/WAITING_FOR_GAME/MINING_PHASE/RIFT_ACTIVE/GAME_END
    │   │   ├── HudSnapshot.java           immutable parse of script-5980 args; phase classifiers
    │   │   ├── GotrSession.java           THE state machine + timeline data (pure Java, no Client)
    │   │   ├── PortalMark.java            one timeline marker in absolute time (PAST/ACTIVE/FUTURE)
    │   │   ├── PortalPrediction.java      spawn history + anchor bookkeeping behind portalMarks
    │   │   ├── PointsMetric.java          COMBINED / ELEMENTAL / CATALYTIC / BOTH_SPLIT
    │   │   ├── BarSize.java               SMALL / MEDIUM / LARGE presets (bar px + font)
    │   │   └── GoalState.java             DISABLED / BELOW / MET tri-state
    │   └── overlay/GotrProgressBarOverlay.java   the entire visible product (custom Graphics2D)
    └── test/java/com/gotrprogressbar/
        ├── GotrProgressBarPluginTest.java   dev launcher (ExternalPluginManager + RuneLite.main)
        ├── GotrSessionTest.java             state-machine + timeline + goal unit tests
        └── PortalPredictionTest.java
```

---

## 4. GOTR game model — LIVE-VERIFIED (2026-07-13, corrects early assumptions)

Phase flow: `OUTSIDE → WAITING_FOR_GAME → MINING_PHASE → RIFT_ACTIVE → GAME_END → WAITING_FOR_GAME`

- **"The rift becomes active!" starts the ROUND = the MINING phase**, NOT crafting. This was the
  single biggest early misconception. The 30/10/5 s chat countdown runs BETWEEN rounds, toward
  the round's start.
- **Between rounds (WAITING):** HUD power = 0; rune indexes 0/0; `guardianTicks` is a garbage
  negative value. Only the 30/10/5 s chat anchors exist (~60 s intermission).
- **Mining (~120 s = 200 ticks):** power parked at exactly `maxPower/10`; rune indexes 0/0; and
  **script arg 10 (`guardianTicks`) is an EXACT tick countdown to the altars opening** — no chat
  anchor needed inside the round.
- **Crafting (RIFT_ACTIVE):** a rune index goes > 0 (altars open). Power climbs 10% → 100% = win;
  power dropping back to 0 from crafting = loss/close. `maxPower` varies per round with player
  count, so always use `currentPower/maxPower`, never a fixed max.
- **Portals (live-measured):** full lifetime **44 ticks (~26 s)**; spawn-to-spawn cycle
  **~141 s** (confirms the 140 s constant). Exact open-portal countdown = script arg 11
  (`portalTicks`). First-portal delay (160 s) is wiki-sourced and still unmeasured from a full
  round start — a known gap.
- **GAME_END:** 10 s summary (`Rift sealed - 190E / 210C`), then back to WAITING.

### In-game gate (subtle — got fixed twice)
Gate is `GotrProgressBarPlugin.isHudPresent()` = game state LOGGED_IN **AND** HUD widget
`746:2` (`PARENT_WIDGET_ID`) present **AND `!widget.isHidden()`**.
- Do **NOT** gate on region 14484: the rune altars are separate map regions where the HUD widget
  and script 5980 keep running (verified: 286 continuous snapshots across altar visits). A region
  gate made the bar vanish/reset mid-round.
- The `!isHidden()` check is essential: after you leave GOTR the widget lingers *hidden*, so a
  null-only check left the "Next game" bar up in the bank. Hidden = leave; visible = still in it.
- An 8-tick grace period (`GATE_GRACE_TICKS`) in `onGameTick` debounces loading transitions.

---

## 5. Verified data sources (locked — do not change without re-verifying)

Reference repos (both accepted Plugin Hub plugins):
- https://github.com/DatBear/Guardians-of-the-Rift-Helper
- https://github.com/hawolt/guardian-of-the-rift

| Constant (GotrConstants) | Value | Source |
|---|---|---|
| `GOTR_REGION_ID` | 14484 | DatBear `MINIGAME_MAIN_REGION` / hawolt `MINIGAME_REGION_ID` (NOT used for gating) |
| `HUD_UPDATE_SCRIPT_ID` | 5980 | hawolt `MINIGAME_HUD_UPDATE_SCRIPT_ID`; fires ~1/tick in GOTR |
| Script args 1–11 | energies, power, maxPower, portalLoc, runeIdx ×2, guardians ×2, guardianTicks, portalTicks | hawolt `MinigameSlice.onScriptPreFired` |
| `PARENT_WIDGET_ID` | 48889858 (group 746 child 2) | DatBear `PARENT_WIDGET_ID` |
| `POWER_TEXT_WIDGET_ID` | 48889874 (746:18) | hawolt (fallback only; not currently used) |
| `VARBIT_ELEMENTAL/CATALYTIC_POINTS` | 13686 / 13685 | DatBear `onVarbitChanged` (defined but not currently read — banked-points UI was cut) |
| `INVENTORY_CONTAINER_ID` | 93 | hawolt |
| Guardian fragments item | `net.runelite.api.gameval.ItemID.GOTR_GUARDIAN_FRAGMENT` | DatBear import — use the named gameval constant, do NOT hardcode |
| Chat anchors | `CHAT_START_30S/10S/5S`, `CHAT_RIFT_ACTIVE`, `CHAT_PORTALS_EXTEND` | DatBear `onChatMessage`, verbatim incl. trailing periods |
| `PORTAL_LIFETIME_TICKS` | 44 | live-measured |
| `PORTAL_CYCLE_SECONDS` | 140 | live-measured ~141 |
| `FIRST_PORTAL_DELAY_SECONDS` | 160 | OSRS Wiki (UNVERIFIED baseline — known gap) |
| `MINING_PHASE_SECONDS` / `_TICKS_NOMINAL` | 120 / 200 | live-verified |
| `GAME_TICK_MILLIS` | 600 | standard |

Script arg layout is **unofficial** → parsing is defensive: `HudSnapshot.fromArgs(...)` returns
null on any length/type mismatch, and the plugin logs a single WARN then ignores it.

---

## 6. Architecture & data flow

Event router: **`GotrProgressBarPlugin`** (`@Slf4j`, Guice `@Inject`, `@Provides` config).
Subscriptions:
- `ScriptPreFired` (id 5980) → `HudSnapshot.fromArgs` → `session.onHudUpdate(snapshot, now)` — the
  **primary** data feed (power, energies, portal loc/ticks, rune indexes, guardianTicks).
- `ChatMessage` (GAMEMESSAGE/SPAM, tags stripped) → `session.onChatMessage(msg, now)` — phase
  anchors (round start + between-round countdown).
- `GameTick` → gate check (`isHudPresent`), `session.setInMinigame(bool)`, `session.tick(now)`.
- `ItemContainerChanged` (container 93) → count `GOTR_GUARDIAN_FRAGMENT` → `setFragmentCount`.
- `GameStateChanged` (LOGIN_SCREEN/HOPPING) → `session.reset()`.
- `ConfigChanged` (group "gotrprogressbar") → `pushGoals()`.

**`GotrSession`** is the whole model: phase state machine + timeline data (portal marks in
ABSOLUTE `Instant` time, spawn back-dating, goal tri-states). It is **pure Java and never
references `Client`**, so unit tests need no mocks — all time is passed in as `Instant`. Scroll
geometry (mapping instants → pixels) lives in the overlay, not the session.

**`GotrProgressBarOverlay`** is the single visible component (one `Overlay`, TOP_CENTER default,
drag/persist as a unit). It reads the session each `render()` and draws with custom `Graphics2D`.

Design invariants:
- Session/PortalPrediction stay client-free and deterministic.
- HUD script is primary; chat anchors are secondary; widget reads are just the gate.
- Portal estimate is suppressed while a portal is open and always shown with `~`.

---

## 7. UI design & standing rulings (with history of what was rejected)

The current design is the result of several live-tested iterations. **Respect these rulings; do
not regress to earlier rejected designs.**

- **Two bar types only.** Between-rounds = plain countdown (no sub-bar, nothing actionable).
  In-round = one unified timeline. (Rejected: separate mining/crafting/portal bars.)
- **Fixed-scale camera-scrolling timeline** (`GotrConstants.TIMELINE_*`). The bar shows a
  constant 300 s window. The white "now" cursor **sweeps left→right through a stationary
  timeline until it reaches 40%** (`TIMELINE_CURSOR_FRACTION`), then **pins** while the timeline
  scrolls past it. 0.40 × 300 s = 120 s, so the cursor pins exactly when mining ends — mining
  reads as a normal left-to-right progress bar and only crafting scrolls.
  - Overlay math: `viewportStart = max(now - 120s, roundStart)`; `pxPerSec = innerW/300`;
    `xOf(instant) = 1 + round((instant-viewportStart)*pxPerSec)`.
  - Rejected v1: a fixed bar where the mining fill grew leftward out of a pinned cursor
    ("fills the wrong direction"). Rejected v2: rescaling the crafting segment by a live
    round-length estimate from power rate ("keeps scaling and changing size randomly"). Both the
    length estimator and the power-based scaling were **removed** — do not reintroduce.
- **Mining = light blue.** Whole 2-min section always visible: `MINE_DONE (120,180,220,150)`
  behind the cursor (elapsed), `MINE_TODO (120,180,220,55)` ahead. Label "Mining", in-bar
  countdown text "altars open m:ss" (NOT "rifts").
- **Crafting trail = green** `TRAIL_FILL (64,142,72,110)` behind the cursor. Label "Crafting".
- **Portals are SECTIONS spanning their real open duration**, not single ticks:
  - PAST = dim yellow `PORTAL_PAST (255,215,64,110)`.
  - ACTIVE = solid yellow `PORTAL_ACTIVE (255,215,64,190)` + exact `Portal m:ss` countdown text.
  - FUTURE = transparent-yellow guess window `PORTAL_GUESS (255,230,120,50)` with edges,
    spanning the ±10 s spawn tolerance plus the portal duration; only when `showPortalMarkers`.
  - Future windows re-anchor **only** when a portal actually spawns. A portal first seen
    mid-life (join/login with one open) is **back-dated** by `(44 - portalTicks) × 0.6 s` to its
    true spawn, or every later estimate inherits the offset (a real reported bug).
  - No P1/P2/... labels (removed — they clipped the caption/clock text).
- **Guardian power is NEVER displayed** — the game already shows it. Power only feeds win/close
  detection. Do not re-add any power number/bar.
- **Caption row** above the bar: phase label left, elapsed round clock right (yellow; `+m:ss` dim
  when mid-join with no known start). Clock is toggleable (`showGameTimer`).
- **Goal sub-bar** (same height as the main bar — 13 px clipped the RS font, must be >=16):
  fragments while mining, points while crafting; red below goal (`belowGoalColor`), green at/above
  (`goalMetColor`). BOTH_SPLIT shows E and C halves each against their **own** goal.
- **Mid-join liveness:** joining mid-crafting with no portal seen yet shows a pulsing
  "watching for portal..." so the bar never looks dead.
- **Native RuneLite styling:** background is `ComponentConstants.STANDARD_BACKGROUND_COLOR` made
  **opaque** (so opacity=100 is truly solid — the standard colour is translucent, which is why
  "fully opaque" was previously unreachable); the opacity slider then scales the whole overlay via
  one `AlphaComposite`. (Rejected: the earlier solid-black slab look.)

Mockups (layout vocabulary only; geometry rulings above supersede them):
https://claude.ai/code/artifact/b8a34b05-e7b4-4598-a64f-46901c015f7b

---

## 8. Config (`GotrProgressBarConfig`, group "gotrprogressbar")

Goals section:
- `fragmentGoal` int 0–999, default **120** — mining sub-bar (0 hides it).
- `pointsMetric` enum `PointsMetric`, default **COMBINED** — what the crafting sub-bar counts.
- `combinedPointsGoal` int 0–9999, default **300** — used by COMBINED.
- `elementalPointsGoal` int 0–9999, default **150** — used by ELEMENTAL and the E half of BOTH_SPLIT.
- `catalyticPointsGoal` int 0–9999, default **150** — used by CATALYTIC and the C half of BOTH_SPLIT.

Display section:
- `barSize` enum `BarSize`, default **SMALL** — SMALL(small font,16px)/MEDIUM(large font,20px)/
  LARGE(large font,26px). Large font = `FontManager.getRunescapeFont()`, small = `getRunescapeSmallFont()`.
- `barWidth` int 280–700, default **420**.
- `opacity` int 15–100, default **100** (100 = fully opaque).
- `showGameTimer` bool, default **true**.
- `showPortalMarkers` bool, default **true** (a live portal always shows regardless).
- `showBetweenRounds` bool, default **true** (off = hide the "Next game" bar entirely).
- `belowGoalColor` default (176,71,58); `goalMetColor` default (87,166,74).

Adding config: add the `@ConfigItem` here, read it where needed; goals are pushed into the
session via `pushGoals()` on startup and `onConfigChanged`.

---

## 9. State-machine logic detail (edge cases that matter)

- **Normal round:** chat "rift becomes active" → MINING_PHASE, `riftActiveAt = now`,
  `miningEndsAt = now+120s` (then refined each snapshot from exact `guardianTicks`). Rune index
  > 0 → RIFT_ACTIVE. Power == max → GAME_END(won); power == 0 from crafting → GAME_END(lost).
- **Mid-join during mining** (no chat seen): `isMiningPhase()` snapshot derives the start as
  `riftActiveAt = now - max(0, 120s - guardianTicks×0.6s)`. The `max(0, …)` clamp is essential —
  a longer-than-nominal countdown must never place the start in the future (that rendered the bar
  backwards). Covered by `GotrSessionTest.midJoinMiningNeverPlacesRoundStartInFuture`.
- **Mid-join during crafting:** phase = RIFT_ACTIVE, `riftActiveAt` stays null (round clock shows
  `+observed` time; portal marks/estimates only appear once a portal is observed and back-dated).
- **Leaving / hop / logout:** gate closes (widget hidden) after grace → `setInMinigame(false)` →
  reset to OUTSIDE. `GameStateChanged` LOGIN_SCREEN/HOPPING → `reset()`.

---

## 10. Environment & dev workflow (Windows)

- **JDK 11 required.** On this machine: `C:\Program Files\Eclipse Adoptium\jdk-11.0.29.7-hotspot`.
  Set `JAVA_HOME` to it before gradle.
- **Build + unit tests:** `gradlew.bat build` (from Bash: `export JAVA_HOME=... && ./gradlew build`).
- **Run the client with the plugin:** `gradlew.bat run` → launches RuneLite `--developer-mode
  --debug` via `GotrProgressBarPluginTest.main`.
- **Running the client during a session (how it's been done here):**
  - Launch in the background (`run_in_background`), capture the log file.
  - Arm a Monitor tailing the log, filtered to `at com\.gotrprogressbar|Unexpected GOTR|BUILD FAILED`
    to catch only our plugin's errors (third-party plugins like Tasks Tracker / Watchdog / 117HD
    throw unrelated NPEs — ignore those).
  - To restart after a rebuild: TaskStop the run + monitor, then kill leftover java procs
    (PowerShell: `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ? { $_.CommandLine
    -match 'gradlew|GotrProgressBar' } | % { Stop-Process -Id $_.ProcessId -Force }`), then relaunch.
  - The client persists across turns; the user logs in manually. Never assume they're in a game.
- **Developer tools for verification:** Widget Inspector (widget group 746 tree), Var Inspector,
  and our DEBUG `HudSnapshot` log line (`log.debug("{}", snapshot)`).
- **Reading HUD cadence / measuring timings from a run's log:** grep `HudSnapshot` lines for
  `portalLoc`/`guardianTicks` transitions to measure portal spawns, mining length, etc.

Unit tests: `GotrSession` and `PortalPrediction` are client-free; tests feed scripted
`HudSnapshot`s / chat strings / fixed `Instant`s. Manual per-release checklist is in
`docs/dev-testing.md`.

---

## 11. Deployment → Plugin Hub (full process + hard-won gotchas)

The plugin lives in its OWN repo; the Plugin Hub is a separate repo you PR a one-line manifest to.

### Release a change to the plugin repo
1. Make + verify the change; `gradlew.bat build` green.
2. Commit to `master` (conventional prefix; end message with the Co-Authored-By line), push:
   `git push origin master`.
3. Note the new full 40-char SHA (`git rev-parse HEAD`) — this is what the Hub manifest pins.

### Submit / update the Plugin Hub PR
The Hub PR must contain **only** `plugins/gotr-progress-bar`. Critical gotchas learned here:

- **`build=standard` is REQUIRED** in `runelite-plugin.properties`. The packager now fails with
  `"build" must be set` if it's absent (older merged plugins predate this). Use `standard` (we
  have no third-party deps — only client + Lombok, which the standard build provides). Use
  `gradle` only if you add custom deps / build logic.
- **Base the PR branch on UPSTREAM master, never the fork's master.** The fork `rstubbs94/
  plugin-hub` is thousands of commits behind and its master still carries a **DENIED**
  `plugins/projectile-highlighter` manifest. Basing on the fork would drag that file into the PR.
  Correct flow (in a throwaway clone):
  ```
  git clone https://github.com/rstubbs94/plugin-hub.git
  git remote add upstream https://github.com/runelite/plugin-hub.git
  git fetch upstream
  git checkout -b add-gotr-progress-bar upstream/master
  # write plugins/gotr-progress-bar with repository= + commit=<sha>
  git diff --cached upstream/master   # MUST be exactly one file
  git commit && git push origin add-gotr-progress-bar
  ```
  Then open the PR on GitHub: base `runelite/plugin-hub:master`, head `rstubbs94:add-gotr-progress-bar`.
- **The manifest** `plugins/gotr-progress-bar` is exactly:
  ```
  repository=https://github.com/rstubbs94/gotr-progress-bar.git
  commit=<full 40-char sha of the plugin repo HEAD>
  ```
  Filename must match `^[a-z0-9-]+$`. Keep it LF (properties tolerate CRLF, but stay clean).
- **Updating an open PR after a fix:** push the plugin repo, then edit only the `commit=` line on
  the same branch and push — CI re-runs automatically on the PR.
- **The Hub CI** downloads the packager from `runelite/plugin-hub-tooling`, compiles the plugin
  against the pinned client in `runelite.version` (was 1.12.33) with the STANDARD build (ignores
  your build.gradle, compiles `src/main` against core deps), then runs `Plugin.java` validators
  (manifest fields, icon <=48x72 & w*h<=5000, LICENSE present, known property keys, no
  net.runelite package, gradle lines <=120 chars). Checks appear as "build" + "RuneLite Plugin
  Hub Checks". To debug a red build **build compiles fine locally**: compile against the exact
  `runelite.version` (`sed` the version in build.gradle), and read the CI log — it's auth-gated,
  so the owner must paste it (there is no `gh` CLI here; curl works for the read-only API only).
- **Local tooling facts:** git credentials for rstubbs94 are configured (push works without a
  prompt). `curl` reaches the GitHub REST API (unauth, 200; 60/hr limit). `gh` is NOT installed.
- **Never push/commit/PR without the owner asking.** Pushing to the plugin repo master does NOT
  affect an open Hub PR (the PR pins a specific commit).

Detailed step docs: `docs/deployment.md`, `docs/hub-submission.md`.

---

## 12. Known gaps / future work (do not implement without verification)

- **`FIRST_PORTAL_DELAY_SECONDS` = 160** is wiki-sourced and its baseline is ambiguous (from
  round start incl. mining, or from altars opening?). Measure the real first-spawn time from a
  live run's HUD log (first `portalLoc>0` transition relative to `riftActiveAt`) and correct it.
- **Barrier status**: no known readable source; neither reference plugin reads it. Deliberately
  dropped from scope (owner wanted a leaner bar). Revisit only if asked, and only with a verified
  data source (in-client Var/object inspection during a barrier break).
- Banked reward-point varbits (13685/13686) are defined but unused — the banked-points chip was
  cut. Re-add only if the owner asks.

---

## 13. Style conventions

RuneLite conventions: **tabs** for indentation, braces on their own lines, `@Slf4j` logging,
Guice `@Inject`, `@Subscribe` handlers, config via a `Config` interface + `@Provides`. Keep gradle
lines <=120 chars. **No third-party dependencies** beyond what runelite-client provides (keeps the
Hub review trivial and avoids dependency-hash verification). Match the existing file's density and
naming; comments state constraints/sources, not narration.
