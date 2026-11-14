package dev.albedo.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTest {

    @Test
    void onlyBlocksAndFluidsGetBevelAndItemsAlsoGetOutline() {
        for (Category c : Category.values()) {
            assertEquals(c == Category.BLOCKS || c == Category.FLUIDS, c.hasBevel(), c.key());
            assertEquals(c == Category.BLOCKS || c == Category.FLUIDS || c == Category.ITEMS, c.hasOutline(), c.key());
        }
    }

    @Test
    void mobsAndPlayersHaveNoChroma() {
        assertFalse(Category.ENTITIES.hasChroma());
        assertFalse(Category.PLAYERS.hasChroma());
        assertTrue(Category.BLOCKS.hasChroma());
    }
}
