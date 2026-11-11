# Coding standards

Before you push, run `./gradlew :core:check` in `modern/` (compile, Checkstyle, tests) and `bash tools/lint.sh` in the repository root. CI runs both.

## Architecture

1. The core uses no Minecraft, loader, Mixin, LWJGL, AWT, Swing or ImageIO types, and compiles for Java 8. The compiler and Checkstyle enforce this.
2. The core never writes to `System.out` or `System.err`; use `Platform.info` and `Platform.warn`.
3. Mixin handler names start with `albedo$` (lint). `@Shadow` and `@Accessor` members keep the target name.
4. A mixin handler extracts values and makes one call into the adapter class of its concept. Decisions belong in core.
5. Config types are immutable; a `with*` method returns a new object.
6. Each change to core behavior comes with a JUnit 5 test.

## Version conditions (modern)

7. Code that only one rendering family can compile goes in a class with a condition around the whole file. Name the family with a constant from `modern/stonecutter.gradle` (`gl_fixed`, `gl_core`, `frame_graph`, `gpu_device`, `renderpearl`, and `gl_sky`/`gl_chroma` for the raw GL classes). Use a version predicate only for a single API change.
8. Many conditions in one shared method are a sign: move the varying call into a version shim, or the code into a family file.
9. Every version in a condition is a row of the boundary table in [ARCHITECTURE.md](ARCHITECTURE.md) (lint).
10. Shared modern files must compile with Java 8, because the 1.16.5 node builds with it.

## Style

11. Checkstyle (`core/config/checkstyle/checkstyle.xml`) is the Java style for core. To silence one check for one element, use `@SuppressWarnings("checkstyle:<check>")` with a comment that says why.
12. Text files are ASCII, indented with spaces, with no trailing spaces and a final newline (lint).
13. UI text, README and changelog: short plain English for a player. Code comments and docs: normal technical English.
14. No comment when the name says it. Otherwise say why, briefly. A real trap (driver bug, ordering constraint) can take a short paragraph at the code it protects.
15. Javadoc only where a caller needs something the signature does not say.
16. Comments describe the code as it is. History goes in the commit message. No `TODO` or `FIXME`; open an issue.

## Commits

17. Titles are Conventional Commits in the imperative, at most 72 characters, no period: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `ci`, `chore`. The body says why.
