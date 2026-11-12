package com.beauver.printbridge.printrequest;

import io.smallrye.config.ConfigMapping;

import java.util.List;

@ConfigMapping(prefix = "printbridge.upload")
public interface UploadConfig {

    /** Maximum size of one model file in megabytes. */
    int maxSizeMb();

    /** Allowed extensions; only those PrintBridge can validate ({@link ModelFileType}) take effect. */
    List<String> allowedExtensions();
}
