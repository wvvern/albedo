#!/usr/bin/env bash
# Prints the shape of each game member that a mixin or an adapter names, on each modern node, as javap reads it from
# the Mojang-named jars that Loom keeps after a build of the node. Nodes with the same shape are one line.
# Run from the repository root: bash tools/boundaries.sh [list file]
# A line of the list file is "<class>[|<class>...][#<member>[><text>]]":
#   <class>                   whether the first class that exists is there
#   <class>#<member>          the declarations of the member ("<init>" is the constructor)
#   <class>#<member>><text>   how many instructions in the member name <text>, for example a call or a field read
# For other nodes, for example a minor version, set NODES="1.21.9 26.1.1".
set -u
list="${1:-tools/boundaries.txt}"
maven="${LOOM_MINECRAFT:-$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft}"
nodes="${NODES:-1.16.5 1.17.1 1.18.2 1.19.2 1.19.4 1.20.1 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2 26.3}"

# The jar of a node: no remap from 26.1, else the newest layered Mojang mappings.
jar_of() {
    local found
    found=$(ls "$maven"/minecraft-merged-deobf/"$1"/minecraft-merged-deobf-"$1".jar \
        "$maven"/minecraft-merged/"$1"-loom.mappings.*/*.jar 2>/dev/null | sort | tail -1)
    echo "$found"
}

classes=$(grep -v '^#' "$list" | grep -v '^$' | sed 's/#.*//' | tr '|' '\n' | sort -u)

# One line for each list line and node: "<line number> TAB <node> TAB <shape>".
shapes=$(for node in $nodes; do
    jar=$(jar_of "$node")
    if [ -z "$jar" ]; then
        awk -v node="$node" '!/^#/ && NF { print NR "\t" node "\tno jar in the Loom cache" }' "$list"
        continue
    fi
    # shellcheck disable=SC2086
    javap -p -c -cp "$jar" $classes 2>/dev/null | awk -v node="$node" -v list="$list" '
        function short(s) {
            gsub(/net\.minecraft\.([a-z0-9_]+\.)*/, "", s)
            gsub(/java\.(lang|util|io|nio)\.([a-z]+\.)*/, "", s)
            gsub(/com\.mojang\.blaze3d\.([a-z0-9_]+\.)*/, "b3d.", s)
            gsub(/com\.mojang\.renderpearl\.api\.([a-z0-9_]+\.)*/, "rp.", s)
            gsub(/com\.mojang\.math\./, "mojmath.", s)
            gsub(/org\.joml\./, "joml.", s)
            return s
        }
        # A class header of javap.
        /^[a-z].*(class|interface|enum) / && /\{$/ {
            line = $0; sub(/<.*/, "", line); n = split(line, w, " ")
            for (i = 1; i < n; i++) if (w[i] == "class" || w[i] == "interface" || w[i] == "enum") { cls = w[i + 1]; break }
            sub(/<.*/, "", cls); known[cls] = 1; member = ""; next
        }
        # A member declaration.
        /^  [^ ].*;$/ {
            decl = $0; sub(/^  /, "", decl); sub(/;$/, "", decl); sub(/ throws .*/, "", decl)
            name = decl
            if (name ~ /\(/) { sub(/\(.*/, "", name) }
            sub(/.* /, "", name)
            if (name == cls) name = "<init>"
            member = name
            key = cls "#" name
            decls[key] = (key in decls) ? decls[key] " / " short(decl) : short(decl)
            next
        }
        # An instruction that names a method or a field.
        /\/\/ (Method|InterfaceMethod|Field) / {
            ref = $0; sub(/.*\/\/ (Method|InterfaceMethod|Field) /, "", ref); gsub(/\//, ".", ref)
            refs[cls "#" member] = refs[cls "#" member] "\n" ref
        }
        END {
            while ((getline row < list) > 0) {
                nr++
                if (row ~ /^#/ || row == "") continue
                text = ""; name = ""; owner = row
                if (index(row, "#")) { owner = substr(row, 1, index(row, "#") - 1); name = substr(row, index(row, "#") + 1) }
                rest = name; sub(/^<init>/, "", rest); cut = index(rest, ">")
                if (cut) { text = substr(rest, cut + 1); name = substr(name, 1, length(name) - length(rest) + cut - 1) }
                count = split(owner, owners, "|"); cls = ""
                for (i = 1; i <= count; i++) if (owners[i] in known) { cls = owners[i]; break }
                where = cls; sub(/^(net\.minecraft|com\.mojang)\./, "", where)
                where = count > 1 && cls != "" ? where ": " : ""
                if (cls == "") shape = "no class"
                else if (name == "") shape = "class"
                else if (!((cls "#" name) in decls)) shape = "no member"
                else if (text == "") shape = decls[cls "#" name]
                else {
                    calls = 0; m = split(refs[cls "#" name], r, "\n")
                    for (i = 1; i <= m; i++) if (index(r[i], text)) calls++
                    shape = calls
                }
                print nr "\t" node "\t" where shape
            }
        }'
done)

# Each list line with its nodes, where nodes that follow each other with the same shape are one range.
printf '%s\n' "$shapes" | awk -F '\t' -v list="$list" '
    { count[$1]++; node[$1, count[$1]] = $2; shape[$1, count[$1]] = $3 }
    END {
        while ((getline row < list) > 0) {
            nr++
            print row
            first = 1
            for (i = 1; i <= count[nr]; i++) {
                if (i < count[nr] && shape[nr, i + 1] == shape[nr, i]) continue
                range = first == i ? node[nr, i] : node[nr, first] " to " node[nr, i]
                print "    " range ": " shape[nr, i]
                first = i + 1
            }
        }
    }'
