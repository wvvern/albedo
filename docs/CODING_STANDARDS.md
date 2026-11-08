# Coding standards

Obey these rules when you change the code. The tag after each rule shows how the project examines it:

- `[cs]`: Checkstyle, with the rules in `core/config/checkstyle/checkstyle.xml`.
- `[lint]`: `tools/lint.sh`. CI runs it in the `Lint` job.
- `[javac]`: the compiler settings in `core/build.gradle`.
- `[review]`: a reviewer examines it. No tool can do this check.

A `[cs]`, `[lint]` or `[javac]` violation stops the build. Before you push, run `./gradlew :core:check` in `modern/` (compile, Checkstyle and tests) and `bash tools/lint.sh` in the repository root.

## Scope

Checkstyle examines `core/src/main` and `core/src/test` only. It does not examine `modern/` and `legacy/`:

- Stonecutter changes the `modern/` source text for each version. The inactive branches are code in comments.
- Mixin handler names contain `$`. The adapters use Mojang and MCP names and long target descriptors.

`tools/lint.sh` examines all files that git tracks, the adapters included.

## Architecture

1. The core does not use Minecraft, loader, Mixin, LWJGL, AWT, Swing or ImageIO types. `[javac]` `[cs] IllegalImport, noGameOrDesktopTypes`
2. The core compiles for Java 8. `[javac] options.release = 8`
3. The core has no Stonecutter comments (`//?`). `[cs] noStonecutter`
4. The core does not write to `System.out` or `System.err`. Use `Platform.info` and `Platform.warn`. `[cs] noConsole`
5. The name of each mixin handler starts with `albedo$`. `@Shadow` and `@Accessor` members keep the target name. `[lint]`
6. A mixin handler has no game logic. It sends plain values to `ModernHooks` or `LegacyHooks`, and the core makes the decision. `[review]`
7. Config types are immutable. A `with*` method gives a new object. `[review]`

## Text and files

8. Tracked text files contain only ASCII characters. `[lint]` `[cs] asciiOnly`
9. Use spaces, not tabs. Do not put spaces at the end of a line. Each file ends with a newline. `[lint]` `[cs]`
10. Write all text that users read in ASD-STE100 Simplified Technical English: messages, screen labels, README and docs. `[review]`

## Java (core)

11. A line has a maximum of 150 characters. Package lines, import lines and lines with a URL are not examined. `[cs] LineLength`
12. Do not use wildcard imports. You can use a static wildcard import. Remove unused imports. `[cs] AvoidStarImport, UnusedImports`
13. Put imports in three groups, with one empty line between them: `dev`, `org` and other libraries; `java` and `javax`; static. Sort each group in ASCII order. `[cs] CustomImportOrder`
14. Names: `UpperCamel` for types, `lowerCamel` for methods, fields, parameters and variables, `UPPER_SNAKE` for constants, lowercase letters and digits for packages. `[cs]` naming checks
15. Put one space around binary operators and after keywords, commas and casts. Do not put spaces inside parentheses. `[cs]` whitespace checks
16. Put `{` at the end of the line. You can write a short body on one line, for example `{ return name; }`. `[cs] braceOnOwnLine`
17. Each `switch` has a `default`. Mark a fall-through with the comment `// fall through`. `[cs] MissingSwitchDefault, FallThrough`
18. Write modifiers in JLS order. Do not write redundant modifiers. `[cs] ModifierOrder, RedundantModifier`
19. A class with only static members is `final` and has a private constructor. `[cs] HideUtilityClassConstructor, FinalClass`
20. If you override `equals`, also override `hashCode`. Compare strings with `equals`, not `==`. `[cs] EqualsHashCode, StringLiteralEquality`
21. An empty `catch` block has a comment that gives the reason, or its variable has the name `ignored`. `[cs] EmptyCatchBlock`
22. Do not commit `TODO` or `FIXME` comments. Open an issue. `[cs] TodoComment`
23. Layout: indent with 4 spaces. You can write an `if`, `for` or `else` with no braces if the statement fits on one line. `[review]`
24. Javadoc is short. Write it for public types, and for members when the name does not tell enough. For each change to core behavior, add or change a JUnit 5 test. `[review]`

## When a check is incorrect

If a check is incorrect for one element, add `@SuppressWarnings("checkstyle:<check>")`, for example `checkstyle:linelength`, with a comment that gives the reason. Do not change `checkstyle.xml` for one file. The regex checks also match text in string literals.
