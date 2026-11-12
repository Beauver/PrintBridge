package com.beauver.printbridge.printrequest;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** 3D model formats PrintBridge accepts. */
public enum ModelFileType {
    STL("stl", "model/stl"),
    THREE_MF("3mf", "model/3mf");

    private final String extension;
    private final String contentType;

    ModelFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ModelFileType> fromExtension(String extension) {
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extension.equals(normalized)).findFirst();
    }
}
