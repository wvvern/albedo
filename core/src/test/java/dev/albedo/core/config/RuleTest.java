package dev.albedo.core.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static dev.albedo.core.config.ConfigFixtures.recolor;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleTest {

    @Test
    void groupsFollowTheCategoryInTheFieldsTheyDoNotSet() {
        Appearance category = recolor(0xFF000000, 80, 0, 0);
        Rule follows = new Rule("Follows", Collections.singletonList("a"), category.withColor(0xFF0000FF),
                EnumSet.of(Appearance.Field.COLOR));
        Rule own = new Rule("Own", Collections.singletonList("b"), category, EnumSet.noneOf(Appearance.Field.class))
                .withLook(category.withRecolor(60, 0, 0), category);
        CategorySettings next = new CategorySettings(category, false, Arrays.asList(follows, own))
                .withAppearance(recolor(0xFF000000, 40, 0, 0));
        assertEquals(40, next.lookOf(next.rules().get(0)).detail());
        assertEquals(0xFF0000FF, next.lookOf(next.rules().get(0)).color());
        assertEquals(60, next.lookOf(next.rules().get(1)).detail(), "a value the player set stays");
        assertEquals(EnumSet.of(Appearance.Field.DETAIL), next.rules().get(1).ownFields());
    }
}
