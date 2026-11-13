package com.beauver.printbridge.settings;

import com.beauver.printbridge.modelinfo.analysis.Dimensions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildPlateTest {

    final BuildPlate plate = new BuildPlate(250, 210, 220);

    @Test
    void fitsAsPlacedOrTurned() {
        assertTrue(plate.fits(new Dimensions(250, 210, 220)));
        assertTrue(plate.fits(new Dimensions(210, 250, 100)));
        assertTrue(plate.fits(new Dimensions(250.005, 210, 220)));
    }

    @Test
    void tooBigInAnyDirectionDoesNotFit() {
        assertFalse(plate.fits(new Dimensions(251, 100, 100)));
        assertFalse(plate.fits(new Dimensions(240, 240, 100)));
        assertFalse(plate.fits(new Dimensions(100, 100, 220.5)));
    }
}
