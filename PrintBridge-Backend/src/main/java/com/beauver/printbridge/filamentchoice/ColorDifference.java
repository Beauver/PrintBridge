package com.beauver.printbridge.filamentchoice;

/**
 * How different two colours look to people: CIEDE2000 ΔE between sRGB colours (#76). Around 1 is
 * barely visible, 10 a clearly different shade, 20 still the same colour family, 35+ another colour.
 * Formula after Sharma, Wu and Dalal (2005).
 */
public final class ColorDifference {

    private static final double POW_25_7 = Math.pow(25, 7);

    private ColorDifference() {
    }

    /** ΔE between two {@code #RRGGBB} colours. */
    public static double between(String firstColor, String secondColor) {
        return deltaE2000(lab(firstColor), lab(secondColor));
    }

    /** CIE L*a*b* (D65) of an {@code #RRGGBB} colour. */
    static double[] lab(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double red = linear((rgb >> 16) & 0xFF);
        double green = linear((rgb >> 8) & 0xFF);
        double blue = linear(rgb & 0xFF);
        double x = (0.4124564 * red + 0.3575761 * green + 0.1804375 * blue) / 0.95047;
        double y = 0.2126729 * red + 0.7151522 * green + 0.0721750 * blue;
        double z = (0.0193339 * red + 0.1191920 * green + 0.9503041 * blue) / 1.08883;
        double fx = labCurve(x);
        double fy = labCurve(y);
        double fz = labCurve(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    /** CIEDE2000 between two L*a*b* colours. */
    static double deltaE2000(double[] first, double[] second) {
        double lightness1 = first[0];
        double lightness2 = second[0];
        double chroma1 = Math.hypot(first[1], first[2]);
        double chroma2 = Math.hypot(second[1], second[2]);
        double meanChroma7 = Math.pow((chroma1 + chroma2) / 2, 7);
        double g = 0.5 * (1 - Math.sqrt(meanChroma7 / (meanChroma7 + POW_25_7)));
        double a1 = (1 + g) * first[1];
        double a2 = (1 + g) * second[1];
        double c1 = Math.hypot(a1, first[2]);
        double c2 = Math.hypot(a2, second[2]);
        double h1 = hue(a1, first[2]);
        double h2 = hue(a2, second[2]);

        double deltaLightness = lightness2 - lightness1;
        double deltaChroma = c2 - c1;
        double deltaHue = 0;
        if (c1 * c2 != 0) {
            deltaHue = h2 - h1;
            if (deltaHue > 180) {
                deltaHue -= 360;
            } else if (deltaHue < -180) {
                deltaHue += 360;
            }
        }
        double deltaHueTerm = 2 * Math.sqrt(c1 * c2) * Math.sin(Math.toRadians(deltaHue / 2));

        double meanLightness = (lightness1 + lightness2) / 2;
        double meanChroma = (c1 + c2) / 2;
        double meanHue = h1 + h2;
        if (c1 * c2 != 0) {
            if (Math.abs(h1 - h2) <= 180) {
                meanHue = (h1 + h2) / 2;
            } else if (h1 + h2 < 360) {
                meanHue = (h1 + h2 + 360) / 2;
            } else {
                meanHue = (h1 + h2 - 360) / 2;
            }
        }
        double t = 1 - 0.17 * Math.cos(Math.toRadians(meanHue - 30)) + 0.24 * Math.cos(Math.toRadians(2 * meanHue))
                + 0.32 * Math.cos(Math.toRadians(3 * meanHue + 6)) - 0.20 * Math.cos(Math.toRadians(4 * meanHue - 63));
        double rotation = 30 * Math.exp(-Math.pow((meanHue - 275) / 25, 2));
        double meanChromaTerm7 = Math.pow(meanChroma, 7);
        double rc = 2 * Math.sqrt(meanChromaTerm7 / (meanChromaTerm7 + POW_25_7));
        double lightnessSquare = Math.pow(meanLightness - 50, 2);
        double sl = 1 + 0.015 * lightnessSquare / Math.sqrt(20 + lightnessSquare);
        double sc = 1 + 0.045 * meanChroma;
        double sh = 1 + 0.015 * meanChroma * t;
        double rt = -Math.sin(Math.toRadians(2 * rotation)) * rc;

        double lightnessPart = deltaLightness / sl;
        double chromaPart = deltaChroma / sc;
        double huePart = deltaHueTerm / sh;
        return Math.sqrt(lightnessPart * lightnessPart + chromaPart * chromaPart + huePart * huePart
                + rt * chromaPart * huePart);
    }

    private static double hue(double a, double b) {
        if (a == 0 && b == 0) {
            return 0;
        }
        double degrees = Math.toDegrees(Math.atan2(b, a));
        if (degrees < 0) {
            degrees += 360;
        }
        return degrees;
    }

    /** sRGB gamma removed: 0–255 to linear 0–1. */
    private static double linear(int channel) {
        double value = channel / 255.0;
        if (value <= 0.04045) {
            return value / 12.92;
        }
        return Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double labCurve(double value) {
        if (value > 216.0 / 24389) {
            return Math.cbrt(value);
        }
        return (24389.0 / 27 * value + 16) / 116;
    }
}
