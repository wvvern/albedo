"""Runs one command of the tools: python -m tools <command> [arguments]."""

import importlib
import sys

from tools.report import ToolError

# Each command is a module with main(arguments), loaded only when it runs, so that a command needs only its own packages.
COMMANDS = {
    "build": ("tools.build", "build the release jar of each target into build/libs"),
    "lint": ("tools.lint", "run the repository checks that Checkstyle cannot do"),
    "check-jar": ("tools.jar_check", "examine release jars with no game"),
    "compare-jars": ("tools.jar_compare", "compare the built jars with the jars of a release"),
    "release-notes": ("tools.release_notes", "print the CHANGELOG.md section of one version"),
    "boundaries": ("tools.boundaries", "print the shape of each game member that the mod names, on each node"),
    "ci-matrix": ("tools.ci_matrix", "print the build and publish matrix of the CI workflow"),
    "image": ("tools.images", "frame, rim, split or compare screenshots"),
    "harness": ("tools.harness.run", "run a harness suite in the dev game of each target on a hidden desktop"),
    "optifine": ("tools.harness.optifine", "run the 1.8.9 release jar with OptiFine on a hidden desktop"),
}


def usage() -> str:
    width = max(len(name) for name in COMMANDS)
    lines = ["usage: python -m tools <command> [arguments]", "", "commands:"]
    lines += [f"  {name.ljust(width)}  {summary}" for name, (_, summary) in COMMANDS.items()]
    lines += ["", "python -m tools <command> --help shows the arguments of a command."]
    return "\n".join(lines)


def main(arguments: list[str]) -> int:
    if not arguments or arguments[0] in ("-h", "--help"):
        print(usage())
        return 0 if arguments else 2
    name, rest = arguments[0], arguments[1:]
    if name not in COMMANDS:
        print(f"error: no command {name}\n\n{usage()}", file=sys.stderr)
        return 2
    try:
        return importlib.import_module(COMMANDS[name][0]).main(rest) or 0
    except ToolError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    # The same bytes on Windows and Linux, for output that goes to a file or to a different tool.
    sys.stdout.reconfigure(newline="\n")
    sys.exit(main(sys.argv[1:]))
