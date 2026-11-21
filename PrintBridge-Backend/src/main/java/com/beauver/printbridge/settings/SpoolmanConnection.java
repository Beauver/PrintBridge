package com.beauver.printbridge.settings;

/**
 * Whether Spoolman is set up ({@code SPOOLMAN_URL}). The Spoolman client implements this, so the
 * settings can check it without depending on the client.
 */
public interface SpoolmanConnection {

    boolean isConfigured();
}
