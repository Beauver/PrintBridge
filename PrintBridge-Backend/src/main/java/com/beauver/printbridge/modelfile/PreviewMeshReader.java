package com.beauver.printbridge.modelfile;

import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;
import com.beauver.printbridge.modelfile.exception.ModelTooDetailedException;
import com.beauver.printbridge.modelfile.mesh.PreviewBed;
import com.beauver.printbridge.modelfile.mesh.PreviewMesh;
import com.beauver.printbridge.modelfile.stl.StlMeshReader;
import com.beauver.printbridge.modelfile.threemf.ThreeMfMeshReader;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads the meshes of a model file for the 3D preview (#13), with the same parsing and limits
 * as the model info: plates, modifiers left out, and the filament colour of every part.
 * <p>
 * Placement on the bed:
 * <ul>
 *   <li>Bambu Studio / OrcaSlicer projects keep every plate in one coordinate space, plate N
 *       shifted by 1.2 × the bed size in a grid of ⌈√plates⌉ columns (rows going towards −y).
 *       Each plate is moved back onto its own bed, and that bed is centred on {@code bed}.</li>
 *   <li>Everything else (STL, plain 3MF, a project without bed size) is centred on the bed and
 *       stood on it.</li>
 * </ul>
 */
@ApplicationScoped
public class PreviewMeshReader {

    /**
     * The image Bambu Studio / OrcaSlicer renders of a plate when saving a project
     * ({@code Metadata/plate_N.png}); empty for other files or when there is none.
     */
    public Optional<byte[]> thumbnail(Path file, ModelFileType type, int plate) throws ModelAnalysisException {
        if (type != ModelFileType.THREE_MF || plate < 1) {
            return Optional.empty();
        }
        try {
            return ThreeMfMeshReader.thumbnail(file, plate);
        } catch (ModelAnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelAnalysisException("Could not read the file", e);
        }
    }

    /**
     * @param bed          where to place the model; {@code null} centres it on 0, 0
     * @param maxTriangles more placed triangles than this give a {@link ModelTooDetailedException}
     */
    public PreviewMesh read(Path file, ModelFileType type, PreviewBed bed, long maxTriangles)
            throws ModelAnalysisException {
        try {
            return switch (type) {
                case STL -> StlMeshReader.read(file, bed, maxTriangles);
                case THREE_MF -> ThreeMfMeshReader.read(file, bed, maxTriangles);
            };
        } catch (ModelAnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelAnalysisException("Could not read the file", e);
        }
    }
}
