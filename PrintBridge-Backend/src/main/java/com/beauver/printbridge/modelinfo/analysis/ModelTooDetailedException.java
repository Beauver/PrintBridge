package com.beauver.printbridge.modelinfo.analysis;

/** The file is readable, but has more geometry than PrintBridge handles (a limit of {@link ReadBudget}). */
public class ModelTooDetailedException extends ModelAnalysisException {

    public ModelTooDetailedException(String message) {
        super(message);
    }
}
