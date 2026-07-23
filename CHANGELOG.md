# Changelog

## 1.0.0

Initial release.

- One scrolling, fixed-scale timeline bar for a Guardians of the Rift round. The white "now"
  cursor sweeps through the mining phase, then pins while the timeline scrolls during crafting.
- Mining phase shown as a light-blue section with an exact "altars open" countdown (from the
  game's HUD tick data); green crafting trail behind the cursor.
- Portal sections at their real times: transparent-yellow estimated windows, solid-yellow
  actual open sections with an exact despawn countdown, re-anchoring only on real spawns
  (including back-dating a portal first seen mid-life). Correctly handles joining a game in
  progress during mining or crafting.
- Plain "Next game" countdown between rounds.
- Goal sub-bar: fragments while mining; points while crafting, with a configurable metric
  (combined / elemental / catalytic / both split) and per-type goals.
- Display options: size presets, bar width, opacity, and toggles for the game timer, portal
  markers, and the between-round countdown.
- Renders only inside Guardians of the Rift; fully passive (reads game state only, no
  automation, no network).
