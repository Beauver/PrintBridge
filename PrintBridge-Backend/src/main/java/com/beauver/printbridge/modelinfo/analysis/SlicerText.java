package com.beauver.printbridge.modelinfo.analysis;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cleans up text and colours read from slicer metadata before it is stored and shown. */
final class SlicerText {

    private static final int MAX_LENGTH = 100;
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");

    private SlicerText() {
    }

    /** Trimmed, control characters removed, at most 100 characters; {@code null} if blank. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_LENGTH) {
            return cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned;
    }

    /** {@code #RRGGBB} in upper case (an alpha part is dropped), or {@code null} if it isn't a colour. */
    static String color(String value) {
        if (value == null || !COLOR.matcher(value.trim()).matches()) {
            return null;
        }
        return value.trim().substring(0, 7).toUpperCase(Locale.ROOT);
    }

    /** 1-based extruder number; 0, blank or garbage mean the default (1), as slicers treat it. */
    static int extruder(String value) {
        try {
            int extruder = Integer.parseInt(value == null ? "" : value.trim());
            if (extruder < 1) {
                return 1;
            }
            return extruder;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
