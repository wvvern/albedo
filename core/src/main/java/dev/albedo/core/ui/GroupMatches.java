package dev.albedo.core.ui;

import dev.albedo.core.Glob;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** What group patterns match in the loaded textures, kept until the patterns or the catalog change, because a scan is too slow for each frame. */
final class GroupMatches {
    private static final int MAX_SUGGESTIONS = 6;
    /** Texture-name endings that are faces or states of one block, so "grass_block*" can stand for all of them. */
    private static final List<String> FACE_SUFFIXES = Arrays.asList("top", "side", "bottom", "front", "back", "end",
            "overlay", "snow", "inner", "outer", "still", "flow", "base", "open", "on", "off", "lit", "particle");

    /** What one pattern of a group matches. */
    static final class Chip {
        final int matched;
        /** Of those, the textures the group really gets: no higher group took them. */
        final int effective;
        /** The average color of the first match; null when nothing matches. */
        final Integer color;

        Chip(int matched, int effective, Integer color) {
            this.matched = matched;
            this.effective = effective;
            this.color = color;
        }
    }

    /** A match for the text in the group input. */
    static final class Suggestion {
        final String pattern;
        final int count;
        final Integer color;
        /** A different group that already lists this pattern, or null. */
        final String owner;

        Suggestion(String pattern, int count, Integer color, String owner) {
            this.pattern = pattern;
            this.count = count;
            this.color = color;
            this.owner = owner;
        }
    }

    private List<List<String>> patterns;
    private Map<String, Integer> catalog;
    private TreeSet<String> shortIds;
    private final Map<String, Chip> chips = new HashMap<String, Chip>();
    private final Map<String, List<Suggestion>> suggestions = new HashMap<String, List<Suggestion>>();

    private void validate(CategorySettings cs, Map<String, Integer> now) {
        if (now != catalog) {
            catalog = now;
            shortIds = new TreeSet<String>();
            for (String id : now.keySet()) shortIds.add(shortId(id));
            patterns = null;
        }
        List<List<String>> current = new ArrayList<List<String>>();
        for (Rule r : cs.rules()) current.add(r.patterns());
        if (!current.equals(patterns)) {
            patterns = current;
            chips.clear();
            suggestions.clear();
        }
    }

    Chip chip(CategorySettings cs, int index, String pattern, Map<String, Integer> catalog) {
        validate(cs, catalog);
        String key = index + "|" + pattern;
        Chip chip = chips.get(key);
        if (chip != null) return chip;
        Glob g = Glob.compile(pattern);
        Rule rule = cs.rules().get(index);
        int matched = 0, effective = 0;
        Integer color = null;
        for (Map.Entry<String, Integer> e : catalog.entrySet()) {
            if (!g.matches(e.getKey())) continue;
            if (matched++ == 0) color = e.getValue();
            if (cs.ruleFor(e.getKey()) == rule) effective++;
        }
        chip = new Chip(matched, effective, color);
        chips.put(key, chip);
        return chip;
    }

    /** Matches for typed text: the exact name, then whole blocks such as "grass_block*", then single textures, with prefix matches first. */
    List<Suggestion> suggestions(String typed, Map<String, Integer> catalog, CategorySettings cs, int index) {
        String q = toPattern(typed);
        if (q.isEmpty()) return Collections.emptyList();
        validate(cs, catalog);
        String key = index + "|" + q;
        List<Suggestion> found = suggestions.get(key);
        if (found == null) {
            found = find(q, cs, cs.rules().get(index));
            suggestions.put(key, found);
        }
        return found;
    }

    private List<Suggestion> find(String q, CategorySettings cs, Rule self) {
        List<Suggestion> out = new ArrayList<Suggestion>();
        if (Glob.isRegex(q) || q.indexOf('*') >= 0 || q.indexOf('?') >= 0) {
            out.add(suggestionFor(q, cs, self));
            return out;
        }
        Map<String, List<String>> stems = new LinkedHashMap<String, List<String>>();
        List<String> singles = new ArrayList<String>();
        for (String id : catalog.keySet()) {
            String s = shortId(id);
            if (!s.contains(q)) continue;
            singles.add(s);
            String stem = stem(s);
            if (!stems.containsKey(stem)) stems.put(stem, new ArrayList<String>());
            stems.get(stem).add(s);
        }
        List<String> candidates = new ArrayList<String>();
        for (Map.Entry<String, List<String>> e : stems.entrySet()) {
            if (e.getValue().size() > 1 && wholeBlock(e.getKey())) candidates.add(e.getKey() + "*");
        }
        candidates.addAll(singles);
        // The exact name comes first, so that "dirt" + Enter adds dirt, not dirt_path*.
        List<String> starts = new ArrayList<String>(), contains = new ArrayList<String>();
        for (String c : candidates) {
            if (self.patterns().contains(c)) continue;
            if (c.equals(q) || c.equals(q + "*")) starts.add(0, c);
            else (c.startsWith(q) ? starts : contains).add(c);
        }
        for (String c : starts) if (out.size() < MAX_SUGGESTIONS) out.add(suggestionFor(c, cs, self));
        for (String c : contains) if (out.size() < MAX_SUGGESTIONS) out.add(suggestionFor(c, cs, self));
        return out;
    }

    private Suggestion suggestionFor(String pattern, CategorySettings cs, Rule self) {
        Glob g = Glob.compile(pattern);
        int count = 0;
        Integer color = null;
        String owner = null;
        for (Map.Entry<String, Integer> e : catalog.entrySet()) {
            if (!g.matches(e.getKey())) continue;
            count++;
            if (color == null) color = e.getValue();
            Rule r = cs.ruleFor(e.getKey());
            if (owner == null && r != null && r != self && r.patterns().contains(pattern)) owner = r.displayName();
        }
        return new Suggestion(pattern, count, color, owner);
    }

    /**
     * True when "stem*" matches only that block's own textures (not e.g. "stone*" matching stone_bricks): a prefix
     * range over the sorted names, no regex.
     */
    private boolean wholeBlock(String stem) {
        for (String s : shortIds.subSet(stem, true, stem + Character.MAX_VALUE, true)) {
            if (!stem(s).equals(stem)) return false;
        }
        return true;
    }

    /** The name without face and frame suffixes, so "grass_block_side_overlay" gives "grass_block" and "generic_7" gives "generic". */
    static String stem(String id) {
        for (int u = id.lastIndexOf('_'); u > 0 && isPart(id.substring(u + 1)); u = id.lastIndexOf('_')) {
            id = id.substring(0, u);
        }
        return id;
    }

    private static boolean isPart(String suffix) {
        return FACE_SUFFIXES.contains(suffix) || suffix.matches("[0-9]+");
    }

    /** The pattern that typed text stands for: a glob in lower case without the vanilla namespace, or a regex as typed. */
    static String toPattern(String text) {
        return Glob.isRegex(text) ? text.trim() : shortId(text.trim().toLowerCase(Locale.ROOT));
    }

    /** Vanilla ids without the namespace; modded ids keep it. */
    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
