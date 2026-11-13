"""The build, check and test tools of Albedo. Run one with: python -m tools <command>."""

import sys

if sys.version_info < (3, 12):
    sys.exit("the tools need Python 3.12 or newer")
