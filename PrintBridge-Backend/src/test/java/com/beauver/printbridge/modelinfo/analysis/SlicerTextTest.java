package com.beauver.printbridge.modelinfo.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlicerTextTest {

    @Test
    void cleansText() {
        assertNull(SlicerText.clean(null));
        assertNull(SlicerText.clean(" \u0007 "));
        assertEquals("Plate A", SlicerText.clean(" Plate\u0000 A "));
        assertEquals(100, SlicerText.clean("x".repeat(150)).length());
    }

    @Test
    void acceptsOnlyHexColours() {
        assertEquals("#A1B2C3", SlicerText.color(" #a1b2c3 "));
        assertEquals("#A1B2C3", SlicerText.color("#a1b2c3ff"));
        assertNull(SlicerText.color("red"));
        assertNull(SlicerText.color("#12345"));
        assertNull(SlicerText.color(null));
    }

    @Test
    void readsExtruderNumbers() {
        assertEquals(3, SlicerText.extruder(" 3 "));
        assertEquals(1, SlicerText.extruder("0"));
        assertEquals(1, SlicerText.extruder("x"));
        assertEquals(1, SlicerText.extruder(null));
    }
}
