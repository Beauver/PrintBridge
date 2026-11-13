package com.beauver.printbridge.slicing.client;

import com.beauver.printbridge.common.error.ServiceUnavailableException;

/** The slicer service isn't configured, can't be reached, refused the request or timed out. */
public class SlicerException extends ServiceUnavailableException {
    public SlicerException(String message) {
        super(message);
    }
}
