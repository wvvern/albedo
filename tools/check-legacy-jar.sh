#!/usr/bin/env bash
# Examines one legacy release jar with no game: the Forge metadata, the Mixin manifest, the mixin list and the Java level.
# Run from the repository root: bash tools/check-legacy-jar.sh <jar> <minecraft version>
set -u
jar=$1
mc=$2
fail=0
error() { echo "$jar: error: $1"; fail=1; }

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
unzip -q "$jar" -d "$dir" || { error 'cannot read the jar'; exit 1; }

has() { grep -qF -- "$2" "$dir/$1" || error "$1 does not contain $2"; }
has mcmod.info "\"mcversion\": \"$mc\""
has mcmod.info "+$mc\""
has META-INF/MANIFEST.MF 'TweakClass: org.spongepowered.asm.launch.MixinTweaker'
has META-INF/MANIFEST.MF 'MixinConfigs: mixins.albedo.json'
has mixins.albedo.json '"compatibilityLevel": "JAVA_8"'
[ -f "$dir/mixins.albedo.refmap.json" ] || error 'the jar has no mixins.albedo.refmap.json'

# The mixin config lists each mixin class of the jar and no other class.
listed=$(sed -n '/"client"/,/]/p' "$dir/mixins.albedo.json" | grep -o '"[A-Za-z0-9_]*"' | tr -d '"' | grep -vx client | sort)
present=$(cd "$dir/dev/albedo/legacy/mixin" && ls | grep -v '\$' | sed 's/\.class$//' | sort)
[ "$listed" = "$present" ] || error 'the client list of mixins.albedo.json is not the list of the mixin classes in the jar'

# Byte 8 of a class file is its major version, which is 52 for Java 8.
while IFS= read -r -d '' class; do
    major=$(od -An -tu1 -j7 -N1 "$class" | tr -d ' ')
    [ "$major" = 52 ] || { error "${class#"$dir"/} has the class version $major, not 52"; break; }
done < <(find "$dir/dev" -name '*.class' -print0)

[ $fail = 0 ] && echo "$jar: the static check passed"
exit $fail
