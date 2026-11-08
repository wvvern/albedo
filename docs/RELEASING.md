# Releasing

A release is a tag such as `v1.2.0`. CI builds all jars, makes a GitHub release with the notes from `CHANGELOG.md`, and uploads the jars to Modrinth (slug `albedo`) when the repository variable `MODRINTH_PROJECT_ID` and the secret `MODRINTH_TOKEN` exist.

## Steps

1. Make sure that CI is green on `master`.
2. Select the version (semantic versioning): a breaking config change increases the major number, a `feat` increases the minor number, only `fix` and `perf` increase the patch number. Before 1.0.0, increase the minor number for breaking changes.
3. Set `mod_version` in `modern/gradle.properties` and `version` in `legacy/gradle.properties` (`X.Y.Z+1.8.9`) to the version.
4. Write the `CHANGELOG.md` section for the version (rules below). Put it at the top, under the title.
5. Commit as `chore: Release X.Y.Z`, push, and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
6. Monitor the CI run. Check that the release has 7 jars and the notes.

## Changelog rules

The section is a summary of the net change since the previous tag. It is not a list of commits.

1. Read all commits since the previous tag: `git log --format='%h %s%n%b' vPREV..HEAD`. For the first release, read all commits.
2. Keep only changes that users see: features, fixes of bugs that a released version had, performance, compatibility, config changes. Remove refactors, tests, CI, docs and tool changes.
3. Remove a bug that was added and fixed between the two tags. Its net change is zero.
4. Merge commits that change the same thing into one line. Describe the result, not the steps.
5. Write each line as a Conventional Commit title in ASD-STE100 Simplified Technical English: `type: Verb ...`, one sentence, no period, ASCII only. The colon after the type is the only colon. Do not use semicolons. Write for a player, not a developer. Types, in this order: `feat`, `fix`, `perf`, `compat`, `config` (a change that needs users to edit their config). Mark a breaking change with `!` after the type, for example `config!: Rename the category key "entities" to "mobs"`.
6. Start the section with `## X.Y.Z`. Optional: one short sentence under it (for example, "First release.").

Example:

```
## 1.3.0

- feat: Add a search field to the Textures tab
- fix: Keep the sky color when you change the profile with F7
- perf: Repaint only the changed textures on 1.8.9
```

`bash tools/release-notes.sh X.Y.Z` prints the section as CI uses it. The release job fails when the section is missing.
