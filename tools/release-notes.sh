#!/usr/bin/env bash
# Prints the CHANGELOG.md section of one version, without its heading. CI uses it as the release notes.
# Usage: bash tools/release-notes.sh 1.2.0
set -eu
version="${1#v}"
notes="$(awk -v v="$version" '
    /^## / { if (found) exit; found = ($2 == v) ; next }
    found { print }
' "$(dirname "$0")/../CHANGELOG.md")"
if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then
    echo "error: CHANGELOG.md has no section '## $version'. Write it before you push the tag (see docs/RELEASING.md)." >&2
    exit 1
fi
printf '%s\n' "$notes" | sed -e '/./,$!d'
