# Releasing

Pushing a tag such as `v1.2.0` makes CI build all 39 jars, create a GitHub release with the notes from `CHANGELOG.md`, and upload to Modrinth (slug `albedo`) when the variable `MODRINTH_PROJECT_ID` and the secret `MODRINTH_TOKEN` are set.

## Steps

1. Check that CI is green on `master`.
2. Pick the version: features bump the minor, fixes only bump the patch, breaking config changes bump the major (the minor before 1.0.0).
3. Set `mod_version` in `modern/gradle.properties`.
4. Write the `CHANGELOG.md` section at the top (rules below) and check it with `bash tools/release-notes.sh X.Y.Z`.
5. Run the harness suites on every target, update `docs/TESTED.md`, and start the release jars in a normal game.
6. Commit `chore: Release X.Y.Z`, push, then `git tag vX.Y.Z && git push origin vX.Y.Z`.
7. Watch the CI run and check the release has 39 jars and the notes.

## Changelog

- Summarize what changed for players since the previous tag (`git log vPREV..HEAD`); it is not a commit list. Leave out refactors, tests, CI, docs and tools, and bugs that were added and fixed within the range.
- One line per change: `- type: Sentence`, imperative, no period, ASCII, written for a player. Types in this order: `feat`, `fix`, `perf`, `compat`, `config` (players must edit their config). Add `!` for a breaking change, for example `config!:`.
- Do not add the `gh attestation verify` command; CI appends it.

## CI jobs

- `check`: lint and `:core:check`; on a tag it also extracts the notes, so a missing section stops the release.
- `modern` (one per node) and `legacy`: build, run `tools/check-jar.sh` or `tools/check-legacy-jar.sh`, upload `jars-<minecraft version>`.
- `release`: on a tag, checks for 39 jars, adds a build attestation to each, creates the GitHub release.
- `modrinth`: one upload per loader and jar group. The Fabric upload also lists `quilt` for the rows with `fabric_loaders` in the matrix. If an upload fails, delete the partial version on Modrinth and run `.github/workflows/modrinth.yml`.
- `modrinth-page`: sets the Modrinth description to `modrinth/DESCRIPTION.md` and replaces the gallery with the images in `modrinth/gallery.txt`. The token needs the scope "Write projects". To run it without a release: `gh workflow run modrinth-page.yml`.

Jar names are `albedo-<loader>-<version>+<minecraft version>.jar`; the `26.1` node builds `+26.1.2`.
