package com.beauver.printbridge.modelinfo.analysis;

/** Size of a bounding box in mm, rounded to 0.01 mm. */
public record Dimensions(double width, double depth, double height) {

    static Dimensions of(double width, double depth, double height) {
        return new Dimensions(round(width), round(depth), round(height));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
