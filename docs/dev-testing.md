# Dev & Testing Process

## Environment

- JDK 11 (Temurin). On this machine: `C:\Program Files\Eclipse Adoptium\jdk-11.0.29.7-hotspot`.
- Gradle wrapper is committed (8.10) — never install Gradle globally, always use `gradlew.bat`.

## Daily loop

```
gradlew.bat build     # compile + unit tests
gradlew.bat run       # launch RuneLite with the plugin, --developer-mode --debug
```

`run` starts a full client via `GotrProgressBarPluginTest.main`. Log output is verbose
(`--debug`); our HUD snapshots are logged at DEBUG level by `GotrProgressBarPlugin`.

## Unit tests

`gradlew.bat test`. The state machine (`GotrSession`) and portal math (`PortalPrediction`)
are pure Java — tests feed scripted sequences of `HudSnapshot`s, chat strings and fixed
`Instant`s. No mocking framework is used or needed. When you change transition logic, add a
scenario test; the full happy path lives in `GotrSessionTest.fullHappyPath`.

## In-client verification (developer mode)

RuneLite's Developer Tools plugin (enabled automatically with `--developer-mode`) provides:

- **Widget Inspector** — verify the GOTR HUD is group 746 (parent child 2, power text child 18).
- **Var Inspector** — watch varbits 13685/13686 change as points are earned/spent; use it for
  the barrier spike below.
- Our DEBUG log — every firing of HUD script 5980 prints a `HudSnapshot`. If the layout ever
  stops parsing, the plugin logs a single WARN with the raw args.

### Pending spikes (record findings in GotrConstants comments and claude.md)

1. **S4.2 Barrier status** (timeboxed): during a game where barriers take damage/break, watch
   the Var Inspector for varbits flipping near barrier events and check the object inspector
   for barrier object ID swaps (intact vs rubble), plus the chat log for any breach message.
   Outcome: either a documented data source (then implement the BAR chip) or a written
   decision to leave it out.
2. **S4.3 Portal cadence**: log spawn timestamps for ≥3 full games; confirm first ~160 s,
   cycle ~140 s. Adjust `GotrConstants` if reality differs.
3. **S4.5 Earlier start anchor**: check whether anything (widget, varbit, script) announces
   the next game start earlier than the 30 s chat warning; if found, extend `MINING_PHASE`.

## Manual test checklist (run one full pass before every release)

1. Outside GOTR: no overlay is drawn anywhere.
2. Enter the GOTR area between games: WAITING bar appears; countdown appears after the first
   "rift will become active" message and matches the 30/10/5 s chat messages within 1 s.
3. Mining fill turns green exactly when the fragment count reaches the configured goal;
   stays red below it; with goal = 0 the fill is neutral and the text shows plain count.
4. Rift opens: bar switches to POWER at ~10%, matching the native HUD power number.
5. First portal (~2:40 in): the estimate band covered the actual spawn; while open, the
   PORTAL countdown matches the native portal widget.
6. Second portal ~140 s after the first.
7. Energy readout: each side flips red → green when crossing its goal.
8. PTS chip: gray at game start (fresh account state), amber once both energies > 0, green +
   two checks at ≥ 100/100; one check present whenever a banked pair exists; spend points at
   the rewards guardian and watch it update.
9. 60% power: notch/color shift coincides with the temple rumble.
10. 100% power: end summary shows final energies, resets to WAITING after ~10 s.
11. World-hop / logout mid-game: overlay disappears, state fully resets.
12. Drag the overlay somewhere else; restart the client; position persisted.
13. Fixed, resizable and stretched client modes all render correctly.
