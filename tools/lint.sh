#!/usr/bin/env bash
# Repository checks that Checkstyle cannot do (see docs/CODING_STANDARDS.md). CI runs this file.
# Run from the repository root: bash tools/lint.sh
set -u
fail=0

# Text: all tracked text files (-I skips binary files), where the .bat files keep their CRLF line ends.
git grep -nIP '[^\x00-\x7F]' && { echo 'error: use only ASCII characters'; fail=1; }
git grep -nIP '\t' && { echo 'error: use spaces, not tabs'; fail=1; }
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
        t = s; sub(/^[ \t]*(\/[*^])?[ \t]*/, "", t)
        n = gsub(/\(/, "(", s) - gsub(/\)/, ")", s)
    }
    d > 0 { d += n; next }
    t ~ /^@(Inject|Redirect|ModifyArg|ModifyArgs|ModifyVariable|ModifyConstant|ModifyExpressionValue|ModifyReturnValue|WrapOperation|WrapWithCondition)\(/ { p = 1; d = n; next }
    p && t ~ /^@/ { d = n; next }
    p && (t ~ /^$/ || t ~ /^([*^]\/)?\/\/\?/ || t ~ /^\*\/$/) { next }
    p {
        if (s !~ /albedo\$[A-Za-z0-9_]*[ \t]*\(/) { print FILENAME ":" FNR ": error: a handler name must start with albedo$"; bad = 1 }
        p = 0
    }
    END { exit bad }' || fail=1

# The active Stonecutter version stays 26.3, because a different one changes the comment state of each version branch in src/.
grep -qx "stonecutter.active '26.3'" modern/stonecutter.gradle || { echo "modern/stonecutter.gradle: error: stonecutter.active must be '26.3'"; fail=1; }

# Each version in a Stonecutter condition is a boundary in the table of docs/ARCHITECTURE.md, so that no condition has a version that nobody examined.
for version in $(git grep -hE '//\?.*(if|elif) ' -- 'modern/*.java' | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | sort -u); do
    grep -qF "| \`$version\` |" docs/ARCHITECTURE.md \
        || { echo "docs/ARCHITECTURE.md: error: the version $version of a Stonecutter condition is not in the boundary table"; fail=1; }
done

# A file that all versions share must compile with Java 8 for the oldest node, so it gets no more Java 9 syntax than the 3 lines that it has, which are in branches for 1.21.4 and later.
# A file with a condition around the whole file is not examined, because its condition can leave out the oldest node.
java9='\bvar [A-Za-z_]+ [=:]|instanceof [A-Za-z_.<>?]+ [a-z][A-Za-z]* *[)&|]|Math\.clamp\(|\b(List|Set|Map)\.(of|copyOf)\(|"""|\brecord [A-Z]'
shared=$(git ls-files -- 'modern/*/src/*.java' | while IFS= read -r f; do
    awk 'NR > 1 && NF { exit !/^\/\/\? if/ }' "$f" || echo "$f"
done)
# shellcheck disable=SC2086
if [ "$(grep -hE "$java9" $shared | wc -l)" -gt 3 ]; then
    grep -nE "$java9" $shared
    echo 'error: do not add Java 9 syntax (var, instanceof with a name, Math.clamp, List.of) to a file that all versions share'
    fail=1
fi

# Each row of the CI matrix agrees with modern/settings.gradle and with the gradle.properties of its node.
ci=.github/workflows/ci.yml
node_list() { sed -n "s/^def $1 = \[\(.*\)\]$/\1/p" modern/settings.gradle | tr -d "' "; }
at_most() { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -n1)" = "$1" ]; }
rows=$(grep -E "^ +- \{ node: '" "$ci")
[ "$(sed -n "s/.*node: '\([^']*\)'.*/\1/p" <<<"$rows" | paste -sd, -)" = "$(node_list nodes)" ] \
    || { echo "$ci: error: the node rows are not the list nodes of modern/settings.gradle"; fail=1; }
while IFS= read -r row; do
    node=$(sed -n "s/.*node: '\([^']*\)'.*/\1/p" <<<"$row")
    props=modern/versions/$node/gradle.properties
    [ -f "$props" ] || continue
    prop() { grep -m1 "^$1=" "$props" | cut -d= -f2- | tr -d '\r'; }
    row_error() { echo "$ci: error: the row of node $node $1"; fail=1; }
    [ "$(sed -n "s/.*mc: '\([^']*\)'.*/\1/p" <<<"$row")" = "$(prop minecraft_version)" ] || row_error 'does not have the minecraft_version of the node'
    [ "$(sed -n 's/.*java: \([0-9]*\).*/\1/p' <<<"$row")" = "$(prop java_version)" ] || row_error 'does not have the java_version of the node'
    loaders=fabric
    for loader in forge neoforge; do
        case ",$(node_list "${loader}Nodes")," in *",$node,"*) loaders="$loaders,$loader" ;; esac
    done
    [ "$(sed -n 's/.*loaders: \[\([^]]*\)\].*/\1/p' <<<"$row" | tr -d ' ')" = "$loaders" ] || row_error "must have the loaders [$loaders]"
    read -r lower upper <<<"$(prop minecraft_range_fabric | tr -d '>=<')"
    for version in $(sed -n "s/.*game_versions: [\"']\(.*\)[\"'] }.*/\1/p" <<<"$row" | sed 's/\\n/ /g'); do
        { at_most "$lower" "$version" && at_most "$version" "$upper" && [ "$version" != "$upper" ]; } \
            || row_error "has the game version $version, which is not in minecraft_range_fabric"
    done
done <<<"$rows"

exit $fail
