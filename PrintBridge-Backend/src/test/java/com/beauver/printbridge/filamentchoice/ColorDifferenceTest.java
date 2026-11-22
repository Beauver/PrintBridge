package com.beauver.printbridge.filamentchoice;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorDifferenceTest {

    /** Reference pairs from Sharma, Wu and Dalal (2005), "The CIEDE2000 color-difference formula". */
    @ParameterizedTest
    @CsvSource({
            "50.0000, 2.6772, -79.7751, 50.0000, 0.0000, -82.7485, 2.0425",
            "50.0000, 3.1571, -77.2803, 50.0000, 0.0000, -82.7485, 2.8615",
            "50.0000, 2.5000, 0.0000, 50.0000, 0.0000, -2.5000, 4.3065",
            "50.0000, 2.5000, 0.0000, 73.0000, 25.0000, -18.0000, 27.1492",
            "50.0000, 2.5000, 0.0000, 50.0000, 3.1736, 0.5854, 1.0000",
            "60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644",
            "63.0109, -31.0961, -5.8663, 62.8187, -29.7946, -4.0864, 1.2630",
            "22.7233, 20.0904, -46.6940, 23.0331, 14.9730, -42.5619, 2.0373",
            "50.0000, 0.0000, 0.0000, 50.0000, -1.0000, 2.0000, 2.3669"
    })
    void matchesThePublishedReferenceValues(double l1, double a1, double b1, double l2, double a2, double b2,
                                            double expected) {
        double[] first = {l1, a1, b1};
        double[] second = {l2, a2, b2};
        assertEquals(expected, ColorDifference.deltaE2000(first, second), 0.0001);
        assertEquals(expected, ColorDifference.deltaE2000(second, first), 0.0001);
    }

    @Test
    void comparesSrgbColours() {
        double[] white = ColorDifference.lab("#FFFFFF");
        assertEquals(100, white[0], 0.01);
        assertEquals(0, white[1], 0.01);
        assertEquals(0, white[2], 0.01);
        assertEquals(0, ColorDifference.between("#FF0000", "#ff0000"), 0.0001);
        assertTrue(ColorDifference.between("#FFFFFF", "#FAFAFA") < 3);
        assertTrue(ColorDifference.between("#FFFFFF", "#000000") > 90);
        assertTrue(ColorDifference.between("#FF0000", "#00FF00") > 50);
        // Dark colours (below the L* curve's linear part) work too.
        assertTrue(ColorDifference.between("#010101", "#020202") < 1);
    }

    @Test
    void refusesAnythingButRrggbb() {
        assertTrue(ColorDifference.isColor("#a0B1c2"));
        assertFalse(ColorDifference.isColor("#12345"));
        assertFalse(ColorDifference.isColor("123456"));
        assertFalse(ColorDifference.isColor("#GGGGGG"));
        assertFalse(ColorDifference.isColor(null));
        assertThrows(IllegalArgumentException.class, () -> ColorDifference.between("#FFFFFF", "white"));
    }
}
