package com.beauver.printbridge.modelfile.geometry;

import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;

/** Receives a mesh one triangle at a time (corners in mm). */
@FunctionalInterface
public interface TriangleSink {

    void addTriangle(double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     double x3, double y3, double z3) throws ModelAnalysisException;
}
