package dev.albedo.core.dev;

import dev.albedo.core.Category;
import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Profile;
import dev.albedo.core.config.Rule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarnessTest {
    @Test
    void theGroupCommandSetsOrReplacesAGroupByName() {
        Profile p = Config.defaults().activeProfile();
        p = Harness.withGroup(p, "particles Fire #FF0000 flame lava".split(" "));
        p = Harness.withGroup(p, "particles Love hidden heart".split(" "));
        p = Harness.withGroup(p, "particles Love red heart".split(" "));
        CategorySettings love = p.get(Category.PARTICLES);
        assertEquals(Appearance.solid(0xFFFF0000), love.lookOf(love.rules().get(1)), "a color name");
        p = Harness.withGroup(p, "particles Love hidden heart".split(" "));
        p = Harness.withGroup(p, "particles Fire #00FF00 flame".split(" "));
        CategorySettings particles = p.get(Category.PARTICLES);
        List<Rule> rules = particles.rules();
        assertEquals(2, rules.size());
        assertEquals("Fire", rules.get(0).name());
        assertEquals(Arrays.asList("flame"), rules.get(0).patterns());
        assertEquals(Appearance.solid(0xFF00FF00), particles.lookOf(rules.get(0)));
        assertEquals(Appearance.Mode.HIDDEN, particles.lookOf(rules.get(1)).mode());
    }
}
