# Deployment Process

## Versioning

- `version` lives in `build.gradle` (semver, starts at `1.0.0`). Bump it for every release
  that will be submitted to the Plugin Hub.
- Tag releases in git: `git tag 1.0.0 && git push --tags`.

## Release gate (all must pass before any submission)

1. `gradlew.bat build` green (compile + unit tests).
2. Clean-clone build check: clone the repo into a temp dir with only JDK 11 available and run
   `gradlew.bat build` — this mirrors what Plugin Hub CI does.
3. Full manual test checklist from [dev-testing.md](dev-testing.md), one complete real game.
4. README screenshots up to date with what the plugin actually renders.

## GitHub repo

- Public repo, suggested name `gotr-progress-bar`.
- Must contain at repo root: `LICENSE` (BSD 2-Clause), `runelite-plugin.properties`,
  `icon.png` (≤ 48x72 px), the gradle wrapper, and `src/`.
- Never commit `.gradle/`, `build/`, or IDE files (already covered by `.gitignore`).

## Publishing a release

```
git add -A
git commit -m "Release 1.0.0"
git tag 1.0.0
git push origin master --tags
git rev-parse HEAD    # note the full 40-char commit hash for the Hub submission
```

Then follow [hub-submission.md](hub-submission.md).

## Post-release updates

1. Make and verify changes (release gate above), bump `version` in `build.gradle`.
2. Commit, tag, push; note the new commit hash.
3. Open a new plugin-hub PR that edits only the `commit=` line of `plugins/gotr-progress-bar`
   (see hub-submission.md, "Updating").
