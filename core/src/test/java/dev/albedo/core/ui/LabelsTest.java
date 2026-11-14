package dev.albedo.core.ui;

import dev.albedo.core.config.Appearance;
import dev.albedo.core.sky.SkyPresets;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelsTest {
    @Test
    void everyStyleHasItsOwnLabel() {
        Set<String> labels = new HashSet<String>();
        for (Appearance.Mode m : Appearance.Mode.values()) assertTrue(labels.add(Labels.mode(m)), "two styles show " + Labels.mode(m));
    }

    @Test
    void eachPresetHasTheTextsOfItsCard() {
        for (SkyPresets.Preset p : SkyPresets.all()) {
            assertFalse(Labels.presetName(p.key()).isEmpty(), p.key());
            String description = Labels.presetDescription(p.key());
            assertFalse(description.isEmpty() || description.endsWith("."), p.key() + ": one sentence with no period");
        }
    }
}
