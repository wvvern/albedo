# Releasing

A release is a tag such as `v1.2.0`. CI builds all jars, makes a GitHub release with the notes from `CHANGELOG.md`, and uploads the jars to Modrinth (slug `albedo`) when the repository variable `MODRINTH_PROJECT_ID` and the secret `MODRINTH_TOKEN` exist.

## The jars

A release has 39 jars: 18 Fabric, 7 Forge and 12 NeoForge for the nodes in `modern/settings.gradle`, and the Forge jars of 1.8.9 and 1.12.2 from `legacy/`. The name of a jar is `albedo-<loader>-<version>+<minecraft version>.jar`. The node `26.1` builds the jar `+26.1.2`.

To build all of them on your computer, run `pwsh tools/build-all.ps1`. It builds one node in each Gradle run and copies the jars to `build/libs`.

## CI

- `check` runs `tools/lint.sh` and `:core:check`. On a tag it also reads the notes with `tools/release-notes.sh`.
- `modern` has one job for each row of the matrix in `.github/workflows/ci.yml`. A job builds all loaders of its node in one Gradle run, runs `tools/check-jar.sh` on each jar, and uploads the artifact `jars-<minecraft version>`.
- `legacy` builds the jars of 1.8.9 and 1.12.2 and uploads `jars-1.8.9` and `jars-1.12.2`.
- `release` runs on a tag. It checks that there are 39 jars, signs each jar with a build attestation, and makes the GitHub release.
- `modrinth` has one job for each jar group. Each loader has its own upload step with the game versions of the row. The Fabric step also lists the loader `quilt` for each row with `fabric_loaders` in the matrix, which are the nodes that start through their entry point on the release Quilt Loader (`docs/TESTED.md`). If an upload fails, delete the incomplete version on Modrinth and run `.github/workflows/modrinth.yml`.

## Steps

1. Make sure that CI is green on `master`.
2. Select the version (semantic versioning): a breaking config change increases the major number, a `feat` increases the minor number, only `fix` and `perf` increase the patch number. Before 1.0.0, increase the minor number for breaking changes.
3. Set `mod_version` in `modern/gradle.properties` to the version. The legacy build reads the same value.
4. Write the `CHANGELOG.md` section for the version (rules below). Put it at the top, under the title.
5. Run the validation matrix and fill `docs/TESTED.md`. Start the release jars of the candidate commit and install them in the instances of the owner.
6. Commit as `chore: Release X.Y.Z`, push, and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
7. Monitor the CI run. Check that the release has 39 jars and the notes.

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

CI signs each jar with a GitHub build attestation and adds a `gh attestation verify` command to the end of the GitHub notes. Do not write it in the changelog.

`bash tools/release-notes.sh X.Y.Z` prints the section as CI uses it. When the section is missing, the `Check` job fails and CI makes no release.
