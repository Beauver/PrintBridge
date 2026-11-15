package com.beauver.printbridge.modelfile;

import com.beauver.printbridge.modelfile.analysis.ModelAnalysis;
import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;
import com.beauver.printbridge.modelfile.stl.StlAnalyzer;
import com.beauver.printbridge.modelfile.threemf.ThreeMfAnalyzer;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads size, volume, plates and slicer data from a model file. It never trusts the file: reads
 * are bounded (see {@link ReadBudget}) and anything unexpected ends in a
 * {@link ModelAnalysisException}.
 */
@ApplicationScoped
public class ModelAnalyzer {

    public ModelAnalysis analyze(Path file, ModelFileType type) throws ModelAnalysisException {
        return analyze(file, type, new ReadBudget());
    }

    public ModelAnalysis analyze(Path file, ModelFileType type, ReadBudget budget) throws ModelAnalysisException {
        try {
            return switch (type) {
                case STL -> new StlAnalyzer().analyze(file);
                case THREE_MF -> new ThreeMfAnalyzer().analyze(file, budget);
            };
        } catch (ModelAnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelAnalysisException("Could not read the file", e);
        }
    }
}
