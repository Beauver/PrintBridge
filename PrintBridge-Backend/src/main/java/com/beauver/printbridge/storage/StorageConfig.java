package com.beauver.printbridge.storage;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "printbridge.storage")
public interface StorageConfig {

    /** Root directory for uploaded files; must not be served by a web server. */
    String path();
}
