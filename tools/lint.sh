#!/usr/bin/env bash
# Repository checks that Checkstyle cannot do (see docs/CODING_STANDARDS.md). CI runs this file.
# Run from the repository root: bash tools/lint.sh
set -u
fail=0

# Text: all tracked text files. -I skips binary files. .bat files use CRLF (.gitattributes).
git grep -nIP '[^\x00-\x7F]' && { echo 'error: use only ASCII characters'; fail=1; }
git grep -nIP '\t' -- . ':!*.bat' && { echo 'error: use spaces, not tabs'; fail=1; }
git grep -nIP '[ \t\r]+$' -- . ':!*.bat' && { echo 'error: remove the spaces at the end of the line'; fail=1; }
while IFS= read -r -d '' f; do
    if [ -n "$(tail -c1 "$f")" ]; then echo "$f: error: add a newline at the end of the file"; fail=1; fi
done < <(git grep -zlI -e '')

# Mixin handler names start with albedo$, also in inactive Stonecutter branches.
# After an injector annotation, skip the rest of it (paren depth, string literals removed), other annotations,
# blank lines and Stonecutter markers. The next line is the handler header.
# Limits: the handler name must be on the header line, and the annotation must not share a line with the header.
git ls-files -z -- '*Mixin.java' | xargs -0 awk '
    FNR == 1 { p = 0; d = 0 }
    {
        s = $0; gsub(/"[^"]*"/, "", s)
        t = s; sub(/^[ \t]*(\/\*)?[ \t]*/, "", t)
        n = gsub(/\(/, "(", s) - gsub(/\)/, ")", s)
    }
    d > 0 { d += n; next }
    t ~ /^@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|ModifyExpressionValue|ModifyReturnValue|WrapOperation|WrapWithCondition)\(/ { p = 1; d = n; next }
    p && t ~ /^@/ { d = n; next }
    p && (t ~ /^$/ || t ~ /^(\*\/)?\/\/\?/ || t ~ /^\*\/$/) { next }
    p {
        if (s !~ /albedo\$[A-Za-z0-9_]*[ \t]*\(/) { print FILENAME ":" FNR ": error: a handler name must start with albedo$"; bad = 1 }
        p = 0
    }
    END { exit bad }' || fail=1

# Comments: one sentence each, and no history of the code.
py=python3
$py --version >/dev/null 2>&1 || py=python # Windows has a python3 placeholder that does not run
git ls-files -z -- '*.java' | xargs -0 "$py" tools/comments.py || fail=1

exit $fail
