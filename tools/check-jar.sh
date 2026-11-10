#!/usr/bin/env bash
# Examines one modern release jar with no game: the mixin list, the Java level and the version ranges of its node.
# Run from the repository root: bash tools/check-jar.sh <jar> <loader> <node>
set -u
jar=$1
loader=$2
node=$3
fail=0
error() { echo "$jar: error: $1"; fail=1; }
prop() { grep -m1 "^$2=" "$1" | cut -d= -f2- | tr -d '\r'; }
node_prop() { prop "modern/versions/$node/gradle.properties" "$1"; }

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
unzip -q "$jar" -d "$dir" || { error 'cannot read the jar'; exit 1; }

# The mixin config lists each mixin class of the jar and no other class.
listed=$(sed -n '/"client"/,/]/p' "$dir/albedo.mixins.json" | grep -o '"[A-Za-z0-9_]*"' | tr -d '"' | grep -vx client | sort)
present=$(cd "$dir/dev/albedo/modern/mixin" && ls | grep -v '\$' | sed 's/\.class$//' | sort)
[ "$listed" = "$present" ] || error 'the client list of albedo.mixins.json is not the list of the mixin classes in the jar'

# The mixin level is the Java version of the node, but not above 21, which is the level of the 26.x jars.
java=$(node_prop java_version)
level=$(( java < 21 ? java : 21 ))
grep -q "\"compatibilityLevel\": \"JAVA_$level\"" "$dir/albedo.mixins.json" || error "the mixin compatibilityLevel is not JAVA_$level"

# Byte 8 of a class file is its major version, which is the Java version plus 44.
while IFS= read -r -d '' class; do
    major=$(od -An -tu1 -j7 -N1 "$class" | tr -d ' ')
    [ "$major" = "$((java + 44))" ] || { error "${class#"$dir"/} has the class version $major, not $((java + 44))"; break; }
done < <(find "$dir/dev" -name '*.class' -print0)

has() { grep -qF -- "$2" "$dir/$1" || error "$1 does not contain $2"; }
case $loader in
    fabric)
        has fabric.mod.json "\"minecraft\": \"$(node_prop minecraft_range_fabric)\""
        has fabric.mod.json "\"fabricloader\": \">=$(prop modern/gradle.properties fabric_loader_min)\""
        ;;
    forge)
        has META-INF/mods.toml "versionRange = \"$(node_prop minecraft_range_forge)\""
        has META-INF/mods.toml "versionRange = \"$(node_prop forge_range)\""
        ;;
    neoforge)
        toml=META-INF/neoforge.mods.toml
        [ -f "$dir/$toml" ] || toml=META-INF/mods.toml
        has "$toml" "versionRange = \"$(node_prop minecraft_range_neoforge)\""
        has "$toml" "versionRange = \"$(node_prop neoforge_range)\""
        ;;
    *) error "unknown loader $loader" ;;
esac

[ $fail = 0 ] && echo "$jar: the static check passed"
exit $fail
