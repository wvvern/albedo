# Releasing

Pushing a tag such as `v1.2.0` makes CI build the jar of each target (39 now), create a GitHub release with the notes from `CHANGELOG.md`, and upload to Modrinth (slug `albedo`) when the variable `MODRINTH_PROJECT_ID` and the secret `MODRINTH_TOKEN` are set.

## Steps

1. Check that CI is green on `master`.
2. Pick the version: features bump the minor, fixes only bump the patch, breaking config changes bump the major (the minor before 1.0.0).
3. Set `mod_version` in `modern/gradle.properties`.
4. Write the `CHANGELOG.md` section at the top (rules below) and check it with `python -m tools release-notes X.Y.Z`.
5. Run the harness suites on every target, update `docs/TESTED.md`, and start the release jars in a normal game.
6. Commit `chore: Release X.Y.Z`, push, then `git tag vX.Y.Z && git push origin vX.Y.Z`.
7. Watch the CI run and check the release has 39 jars and the notes.

## Changelog

- Summarize what changed for players since the previous tag (`git log vPREV..HEAD`); it is not a commit list. Leave out refactors, tests, CI, docs and tools, and bugs that were added and fixed within the range.
- One line per change: `- type: Sentence`, imperative, no period, ASCII, written for a player. Types in this order: `feat`, `fix`, `perf`, `compat`, `config` (players must edit their config). Add `!` for a breaking change, for example `config!:`.
- Do not add the `gh attestation verify` command; CI appends it.

## CI jobs

- `plan`: runs `python -m tools ci-matrix`, which gives the other jobs their rows from the node lists and the node properties, and the number of jars.
- `check`: lint, the tests of the tools and `:core:check`; on a tag it also extracts the notes, so a missing section stops the release.
- `modern` (one per node) and `legacy` (one per version): build, run `python -m tools check-jar`, upload `jars-<minecraft version>`. Each builds one node in one Gradle run. The `modern` jobs only read the Gradle cache, because all nodes together exceed the repository's cache limit.
- `release`: on a tag, checks that each target has its jar, adds a build attestation to each, creates the GitHub release.
- `modrinth`: one upload per loader and jar group. The Fabric upload also lists `quilt` for the nodes with `modrinth_loaders_fabric=fabric quilt`. If an upload fails, delete the partial version on Modrinth and run `.github/workflows/modrinth.yml`.
- `modrinth-page`: sets the Modrinth description to `modrinth/DESCRIPTION.md` and replaces the gallery with the images in `modrinth/gallery.txt`. The token needs the scope "Write projects". To run it without a release: `gh workflow run modrinth-page.yml`.

Jar names are `albedo-<loader>-<version>+<minecraft version>.jar`; the `26.1` node builds `+26.1.2`.
