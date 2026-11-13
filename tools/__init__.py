"""The build, check and test tools of Albedo. Run one with: python -m tools <command>."""

import sys

if sys.version_info < (3, 12):  # noqa: UP036 (the message is for a Python that is older than the one ruff assumes)
    sys.exit("the tools need Python 3.12 or newer")
