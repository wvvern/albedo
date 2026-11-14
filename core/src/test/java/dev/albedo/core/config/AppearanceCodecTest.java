package dev.albedo.core.config;

import dev.albedo.core.Category;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static dev.albedo.core.config.ConfigFixtures.recolor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearanceCodecTest {

    @Test
    void theConfigFileHasTheFieldsOnlyWhereRecolorUsesThem() {
        Appearance category = recolor(0xFFC0A080, 80, 0, 50);
        Rule ore = new Rule("Ores", Collections.singletonList("*_ore"), category, true).withLook(recolor(0xFF3050C0, 60, 30, 0), category);
        Config withRecolor = ConfigFixtures.with(Category.BLOCKS, new CategorySettings(category, false, Collections.singletonList(ore)));
        String json = ConfigCodec.encode(withRecolor);
        assertTrue(json.contains("\"detail\": 80") && json.contains("\"keepLight\": 50"), json);
        assertEquals(withRecolor, ConfigCodec.decode(json));

        CategorySettings plain = new CategorySettings(recolor(0xFFC0A080, 80, 0, 50).withMode(Appearance.Mode.SOLID), false,
                Collections.<Rule>emptyList());
        String old = ConfigCodec.encode(ConfigFixtures.with(Category.BLOCKS, plain));
        assertFalse(old.contains("detail"), "a file without Recolor stays readable for older versions: " + old);
    }

    @Test
    void theConfigFileRejectsBadRecolorValues() {
        for (String bad : new String[]{"80.5", "101", "-1", "\"high\""}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                    "{ \"profiles\": { \"x\": { \"blocks\": { \"mode\": \"recolor\", \"detail\": " + bad + " } } } }"));
            assertTrue(e.getMessage().contains("x.blocks"), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(
                "{ \"profiles\": { \"x\": { \"sky\": { \"mode\": \"solid\", \"detail\": 50 } } } }"));
    }
}
