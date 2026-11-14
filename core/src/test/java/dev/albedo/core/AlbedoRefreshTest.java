package dev.albedo.core;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.config.CategorySettings;
import dev.albedo.core.config.Config;
import dev.albedo.core.config.Rule;
import dev.albedo.core.config.Toggle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What {@link Albedo#apply} does to the loaded textures for each kind of config change. */
class AlbedoRefreshTest {
    private final Albedo albedo = CoreFixture.albedo();
    private final FakePlatform p = CoreFixture.platform();

    @BeforeEach
    void defaults() {
        CoreFixture.reset();
    }

    @Test
    void settingsChangesRepaintInsteadOfReloading() {
        Config c = albedo.config();

        // Fullbright and the sky color are read each frame: nothing to repaint.
        RefreshResult r = albedo.apply(c.withProfile(c.activeProfile().with(Toggle.FULLBRIGHT, false)));
        assertEquals(RefreshResult.Outcome.UNCHANGED, r.outcome);
        assertEquals(0, p.uploads + p.reloads + p.rebuilds);

        // A new group repaints its blocks only.
        c = albedo.config();
        Rule ground = new Rule("Ground", Collections.singletonList("dirt"), Appearance.solid(0xFFFF0000), false);
        CategorySettings blocks = c.activeProfile().get(Category.BLOCKS);
        List<Rule> groups = new ArrayList<Rule>(blocks.rules());
        groups.add(0, ground);
        r = albedo.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, blocks.withRules(groups))));
        assertEquals(RefreshResult.Outcome.REPAINTED, r.outcome);
        assertEquals(1, r.repainted);
        assertEquals(1, p.uploads);

        // Shading and tints are in the chunk meshes: rebuild them, never reload.
        c = albedo.config();
        r = albedo.apply(c.withProfile(c.activeProfile().with(Toggle.SHADING, false)));
        assertEquals(RefreshResult.Outcome.REBUILT_CHUNKS, r.outcome);
        c = albedo.config();
        albedo.apply(c.withProfile(c.activeProfile().with(Category.BLOCKS, c.activeProfile().get(Category.BLOCKS).withRemoveTint(false))));
        assertEquals(2, p.rebuilds);

        // Off and on again: all textures repaint, except the stick, which the default profile does not change.
        p.resetCounters();
        albedo.apply(albedo.config().withEnabled(false));
        assertEquals(11, p.uploads);
        assertEquals(0, p.reloads);
    }

    @Test
    void aRepaintThatCannotReachTheGpuReloads() {
        p.flushFails = true;
        RefreshResult r = albedo.apply(albedo.config().withEnabled(false));
        assertEquals(RefreshResult.Outcome.RELOADED, r.outcome);
        assertNotNull(r.reason);
        assertEquals(1, p.reloads, "the core reloads, the adapter does not");
    }
}
