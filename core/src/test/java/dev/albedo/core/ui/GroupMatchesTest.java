package dev.albedo.core.ui;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Rule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroupMatchesTest {
    private final GroupMatches matches = new GroupMatches();

    /** Vanilla textures, each with its own color. */
    private static Map<String, Integer> catalog(String... names) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < names.length; i++) out.put("minecraft:" + names[i], 0xFF000000 | i);
        return out;
    }

    private static CategorySettings groups(Rule... rules) {
        return new CategorySettings(Appearance.solid(0xFF808080), false, Arrays.asList(rules));
    }

    private static Rule group(String name, String... patterns) {
        return new Rule(name, Arrays.asList(patterns), Appearance.solid(0xFFFF0000), false);
    }

    private static List<String> patterns(List<GroupMatches.Suggestion> suggestions) {
        List<String> out = new ArrayList<String>();
        for (GroupMatches.Suggestion s : suggestions) out.add(s.pattern);
        return out;
    }

    @Test
    void theStemDropsFaceAndFrameSuffixes() {
        assertEquals("grass_block", GroupMatches.stem("grass_block_side_overlay"));
        assertEquals("generic", GroupMatches.stem("generic_7"));
        assertEquals("stone_bricks", GroupMatches.stem("stone_bricks"));
    }

    @Test
    void typedTextBecomesALowerCaseGlobWithoutTheVanillaNamespaceOrStaysARegex() {
        assertEquals("dirt", GroupMatches.toPattern(" Minecraft:Dirt "));
        assertEquals("mymod:dirt", GroupMatches.toPattern("MyMod:Dirt"));
        assertEquals("/^Fl/", GroupMatches.toPattern(" /^Fl/ "));
        assertEquals("dirt", GroupMatches.shortId("minecraft:dirt"));
        assertEquals("mymod:dirt", GroupMatches.shortId("mymod:dirt"));
    }

    @Test
    void theExactNameComesFirstThenPrefixesThenTheRest() {
        Map<String, Integer> catalog = catalog("coarse_dirt", "dirt_path_top", "dirt", "dirt_path_side");
        List<String> found = patterns(matches.suggestions("dirt", catalog, groups(group("Own")), 0));
        assertEquals(Arrays.asList("dirt", "dirt_path*", "dirt_path_top", "dirt_path_side", "coarse_dirt"), found);
    }

    @Test
    void aStemIsNoWholeBlockWhenItsGlobAlsoMatchesAnotherBlock() {
        Map<String, Integer> catalog = catalog("stone", "stone_top", "stone_bricks");
        List<String> found = patterns(matches.suggestions("stone", catalog, groups(group("Own")), 0));
        assertFalse(found.contains("stone*"), "stone* also matches stone_bricks: " + found);
        assertEquals("stone", found.get(0));

        found = patterns(matches.suggestions("grass", catalog("grass_block_top", "grass_block_side"), groups(group("Own")), 0));
        assertEquals("grass_block*", found.get(0));
    }

    @Test
    void atMostSixSuggestions() {
        String[] frames = new String[10];
        for (int i = 0; i < frames.length; i++) frames[i] = "generic_" + i;
        List<String> found = patterns(matches.suggestions("generic", catalog(frames), groups(group("Own")), 0));
        assertEquals(6, found.size());
        assertEquals("generic*", found.get(0));
    }

    @Test
    void aGlobOrRegexIsOfferedAsTyped() {
        Map<String, Integer> catalog = catalog("flame", "generic_0");
        assertEquals(Collections.singletonList("/^fl/"), patterns(matches.suggestions("/^fl/", catalog, groups(group("Own")), 0)));
        GroupMatches.Suggestion s = matches.suggestions("gen*", catalog, groups(group("Own")), 0).get(0);
        assertEquals("gen*", s.pattern);
        assertEquals(1, s.count);
        assertEquals(Integer.valueOf(0xFF000001), s.color);
    }

    @Test
    void aPatternOfTheGroupItselfIsNotOfferedAndOneOfAnotherGroupNamesIt() {
        Map<String, Integer> catalog = catalog("dirt", "gravel");
        CategorySettings cs = groups(group("Ground", "dirt"), group("Own", "gravel"));
        assertEquals(Collections.emptyList(), patterns(matches.suggestions("gravel", catalog, cs, 1)));
        GroupMatches.Suggestion dirt = matches.suggestions("dirt", catalog, cs, 1).get(0);
        assertEquals("Ground", dirt.owner);
    }

    @Test
    void aChipCountsTheTexturesThatAHigherGroupDidNotTake() {
        Map<String, Integer> catalog = catalog("dirt", "dirt_path_top");
        CategorySettings cs = groups(group("Ground", "dirt*"), group("Own", "dirt"));
        GroupMatches.Chip chip = matches.chip(cs, 1, "dirt", catalog);
        assertEquals(1, chip.matched);
        assertEquals(0, chip.effective);
        assertEquals(Integer.valueOf(0xFF000000), chip.color);
        assertNull(matches.chip(cs, 1, "sand", catalog).color, "nothing matches");
    }

    @Test
    void theCacheClearsWhenThePatternsOrTheCatalogChange() {
        Map<String, Integer> catalog = catalog("dirt", "gravel");
        CategorySettings cs = groups(group("Own"));
        List<GroupMatches.Suggestion> first = matches.suggestions("dirt", catalog, cs, 0);
        assertSame(first, matches.suggestions("dirt", catalog, cs, 0), "kept while nothing changes");
        assertNotSame(first, matches.suggestions("dirt", catalog("dirt", "gravel"), cs, 0), "a new catalog scans again");
        catalog = catalog("dirt");
        first = matches.suggestions("dirt", catalog, cs, 0);
        assertNotSame(first, matches.suggestions("dirt", catalog, groups(group("Own", "gravel")), 0), "new patterns scan again");
    }
}
