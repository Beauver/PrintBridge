package com.beauver.printbridge.modelfile.stl;

import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;
import com.beauver.printbridge.modelfile.geometry.MeshBuilder;
import com.beauver.printbridge.modelfile.mesh.MeshPlacement;
import com.beauver.printbridge.modelfile.mesh.PreviewBed;
import com.beauver.printbridge.modelfile.mesh.PreviewMesh;
import com.beauver.printbridge.modelfile.mesh.PreviewPart;
import com.beauver.printbridge.modelfile.mesh.PreviewPlate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** The preview mesh of an STL: one part on one plate, centred on the bed. */
public final class StlMeshReader {

    private StlMeshReader() {
    }

    public static PreviewMesh read(Path file, PreviewBed bed, long maxTriangles) throws IOException {
        MeshBuilder builder = new MeshBuilder(maxTriangles);
        StlAnalyzer.readTriangles(file, builder);
        if (builder.triangleCount() == 0) {
            throw new ModelAnalysisException("The model has no triangles");
        }
        List<PreviewPart> parts = List.of(new PreviewPart(1, null, builder.positions(), builder.indices()));
        MeshPlacement.centre(parts, bed);
        return new PreviewMesh(bed, List.of(new PreviewPlate(1, null)), parts);
    }
}
