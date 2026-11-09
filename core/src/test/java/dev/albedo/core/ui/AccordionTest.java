package dev.albedo.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccordionTest {
    @Test
    void toggledOpensAClosedSectionAndClosesAnOpenOne() {
        Accordion none = Accordion.of();
        Accordion effects = none.toggled("effects");
        assertTrue(effects.isOpen("effects"));
        assertFalse(effects.isOpen("other"));
        assertFalse(effects.toggled("effects").isOpen("effects"));
    }

    @Test
    void aToggleGivesANewValueAndKeepsTheOld() {
        Accordion none = Accordion.of();
        none.toggled("effects");
        assertFalse(none.isOpen("effects"));
    }

    @Test
    void valuesWithTheSameOpenSectionsAreEqual() {
        assertEquals(Accordion.of("a", "b"), Accordion.of("b").toggled("a"));
        assertEquals(Accordion.of("a", "b").hashCode(), Accordion.of("b", "a").hashCode());
        assertEquals(Accordion.of(), Accordion.of("a").toggled("a"));
        assertNotEquals(Accordion.of("a"), Accordion.of("b"));
    }
}
