package dev.albedo.core.ui;

import dev.albedo.core.config.Appearance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelsTest {
    @Test
    void everyStyleHasItsOwnLabel() {
        Set<String> labels = new HashSet<String>();
        for (Appearance.Mode m : Appearance.Mode.values()) assertTrue(labels.add(Labels.mode(m)), "two styles show " + Labels.mode(m));
    }
}
