package com.beauver.printbridge.modelinfo.analysis;

import java.io.IOException;

/** The file can't be analysed: it is malformed, uses something unsupported, or exceeds a limit. */
public class ModelAnalysisException extends IOException {

    public ModelAnalysisException(String message) {
        super(message);
    }

    public ModelAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
