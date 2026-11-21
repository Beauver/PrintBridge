package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.common.error.ServiceUnavailableException;

/** Spoolman isn't configured, can't be reached or sent something unreadable. */
public class SpoolmanException extends ServiceUnavailableException {
    public SpoolmanException(String message) {
        super(message);
    }
}
