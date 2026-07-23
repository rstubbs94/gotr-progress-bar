# Plugin Hub Submission Process

Verified against https://github.com/runelite/plugin-hub (README) — re-read it before
submitting; the process occasionally changes.

## Prerequisites (this repo already satisfies them)

- [x] Own public GitHub repository containing the plugin.
- [x] `LICENSE` — BSD 2-Clause (the Hub convention).
- [x] `runelite-plugin.properties` at repo root with `displayName`, `author`, `description`,
      `tags`, `plugins` (fully-qualified main class).
- [x] `icon.png` at repo root, max 48x72 px.
- [x] No third-party dependencies beyond runelite-client. (If one is ever added, it must go
      in the `thirdParty` gradle configuration with hash verification via
      `gradlew --write-verification-metadata sha256` — avoid this; it slows review.)
- [x] Every line of code reviewable from source — no reflection tricks, no native code, no
      downloads at runtime.

## Compliance self-check (Jagex Third-Party Client Guidelines)

The plugin must remain passive display only. Before every submission confirm it still has:

- no input automation of any kind,
- no automatic tile/"stand here" indicators,
- no added menu entries that send actions to the server,
- no autotyping / chatbox injection,
- no unhiding of hidden interface elements,
- no external HTTP calls or data transmission,
- portal prediction presented as an estimate (`~`), never as an instruction.

## First submission

1. Push the release commit and note its full 40-character SHA (`git rev-parse HEAD`).
2. Fork https://github.com/runelite/plugin-hub and create a branch.
3. In the fork, add one file: `plugins/gotr-progress-bar` (no extension), containing exactly:

   ```
   repository=https://github.com/<your-username>/gotr-progress-bar.git
   commit=<full 40-character commit sha>
   ```

4. Open a PR titled `Add GOTR Progress Bar`. In the description include:
   - one-paragraph summary of what it does,
   - screenshots of the bar in a few states (between rounds, mining, crafting with portals),
   - a **differentiation statement**: the Hub already has "Guardians of the Rift Helper";
     explain that this plugin is a single timeline/progress bar with goal coloring and no
     overlap with that plugin's altar highlights/essence counters/notifications, and links
     to it for those features.
5. CI must pass (it builds your repo at the pinned commit). Address review feedback in your
   plugin repo, push, and update the `commit=` hash in the PR branch.

## Updating an already-listed plugin

1. Push the new release commit to the plugin repo (see deployment.md).
2. Fork/branch plugin-hub again and edit only the `commit=` line in
   `plugins/gotr-progress-bar` to the new SHA.
3. Open the PR. Simple version-bump PRs are reviewed quickly (partially automated).

## Review expectations

- Reviewers check security (malice, dependencies, reflection) and game-rule compliance —
  not usefulness or code quality. Keep the code simple and obviously compliant.
- "If it is difficult for us to ensure the plugin isn't against the rules we will not merge
  it" — keep the compliance surface small (no new event types, no new data sinks) unless
  there's a real feature need.
