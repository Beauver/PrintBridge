package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.support.ThreeMfFiles;

/** Lets tests in other packages use the Bambu project of {@link ThreeMfAnalyzerTest}. */
public final class ThreeMfAnalyzerTestAccess {

    private ThreeMfAnalyzerTestAccess() {
    }

    public static ThreeMfFiles bambuProject() {
        return ThreeMfAnalyzerTest.bambuProject();
    }
}
