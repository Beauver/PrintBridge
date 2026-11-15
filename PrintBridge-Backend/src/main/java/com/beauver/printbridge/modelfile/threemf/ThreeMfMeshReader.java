package com.beauver.printbridge.modelfile.threemf;

import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;
import com.beauver.printbridge.modelfile.exception.ModelTooDetailedException;
import com.beauver.printbridge.modelfile.ReadBudget;
import com.beauver.printbridge.modelfile.mesh.MeshPlacement;
import com.beauver.printbridge.modelfile.mesh.PreviewBed;
import com.beauver.printbridge.modelfile.mesh.PreviewMesh;
import com.beauver.printbridge.modelfile.mesh.PreviewPart;
import com.beauver.printbridge.modelfile.mesh.PreviewPlate;
import com.beauver.printbridge.modelfile.threemf.ThreeMfGeometry.BuildItem;
import com.beauver.printbridge.modelfile.threemf.ThreeMfGeometry.ComponentFilter;
import com.beauver.printbridge.modelfile.threemf.ThreeMfGeometry.PlacedMesh;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The preview mesh and plate images of a 3MF, read with the same parsing and limits as the model
 * info (see {@code PreviewMeshReader} for how plates are placed on the bed).
 */
public final class ThreeMfMeshReader {

    /** Plate images are a few dozen kB; anything far bigger isn't one. */
    static final long MAX_THUMBNAIL_BYTES = 5L * 1024 * 1024;
    /** Bambu Studio leaves a fifth of the bed between plates. */
    private static final double PLATE_STRIDE = 1.2;

    private ThreeMfMeshReader() {
    }

    /** The image saved of a plate ({@code Metadata/plate_N.png}); empty when there is none. */
    public static Optional<byte[]> thumbnail(Path file, int plate) throws IOException {
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget(MAX_THUMBNAIL_BYTES, 0))) {
            Optional<InputStream> image = pkg.openIfPresent("Metadata/plate_" + plate + ".png");
            if (image.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = image.get()) {
                return Optional.of(in.readAllBytes());
            }
        }
    }

    /** The parts of a 3MF, plate by plate for a Bambu Studio / OrcaSlicer project. */
    public static PreviewMesh read(Path file, PreviewBed bed, long maxTriangles) throws IOException {
        Optional<ProjectSettings> settings = ProjectSettings.read(file);
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget())) {
            ThreeMfGeometry geometry = ThreeMfGeometry.loadWithTriangles(pkg);
            Counter counter = new Counter(maxTriangles);
            if (!BambuProject.isPresent(pkg)) {
                return singlePlate(geometry, ComponentFilter.ALL, null, bed, counter);
            }
            BambuProject project = BambuProject.read(pkg);
            if (project.plates().isEmpty()) {
                return singlePlate(geometry, project::isPrintedPart, project, bed, counter);
            }
            return plates(geometry, project, settings, bed, counter);
        }
    }

    /** Everything in the build as one plate, centred. */
    private static PreviewMesh singlePlate(ThreeMfGeometry geometry, ComponentFilter filter, BambuProject project,
                                           PreviewBed bed, Counter counter) throws IOException {
        List<PreviewPart> parts = new ArrayList<>();
        for (BuildItem item : geometry.buildItems()) {
            addParts(parts, 1, item, geometry, filter, project, counter);
        }
        if (parts.isEmpty()) {
            throw new ModelAnalysisException("The 3MF contains no geometry");
        }
        MeshPlacement.centre(parts, bed);
        return new PreviewMesh(bed, List.of(new PreviewPlate(1, null)), parts);
    }

    private static PreviewMesh plates(ThreeMfGeometry geometry, BambuProject project,
                                      Optional<ProjectSettings> settings, PreviewBed bed, Counter counter)
            throws IOException {
        Map<Integer, List<BuildItem>> itemsByObject = new HashMap<>();
        for (BuildItem item : geometry.buildItems()) {
            itemsByObject.computeIfAbsent(item.objectId(), id -> new ArrayList<>()).add(item);
        }
        Integer projectBedWidth = settings.map(ProjectSettings::bedWidth).orElse(null);
        Integer projectBedDepth = settings.map(ProjectSettings::bedDepth).orElse(null);
        int columns = (int) Math.ceil(Math.sqrt(project.plates().size()));
        List<PreviewPlate> plates = new ArrayList<>();
        List<PreviewPart> parts = new ArrayList<>();
        for (BambuProject.Plate plate : project.plates()) {
            plates.add(new PreviewPlate(plate.index(), plate.name()));
            List<PreviewPart> plateParts = new ArrayList<>();
            for (BambuProject.Instance instance : plate.instances()) {
                Optional<BuildItem> item = ThreeMfAnalyzer.itemFor(itemsByObject, instance);
                if (item.isPresent()) {
                    addParts(plateParts, plate.index(), item.get(), geometry, project::isPrintedPart, project,
                            counter);
                }
            }
            if (projectBedWidth != null && projectBedDepth != null) {
                int position = plate.index() - 1;
                double originX = (position % columns) * projectBedWidth * PLATE_STRIDE;
                double originY = -(position / columns) * projectBedDepth * PLATE_STRIDE;
                double offsetX = -originX;
                double offsetY = -originY;
                if (bed != null) {
                    offsetX += (bed.x() - projectBedWidth) / 2.0;
                    offsetY += (bed.y() - projectBedDepth) / 2.0;
                }
                MeshPlacement.move(plateParts, offsetX, offsetY, 0);
            } else {
                MeshPlacement.centre(plateParts, bed);
            }
            parts.addAll(plateParts);
        }
        if (parts.isEmpty()) {
            throw new ModelAnalysisException("The project has no objects on its plates");
        }
        return new PreviewMesh(bed, plates, parts);
    }

    /** One part per placed mesh, with its vertices transformed into place. */
    private static void addParts(List<PreviewPart> parts, int plate, BuildItem item, ThreeMfGeometry geometry,
                                 ComponentFilter filter, BambuProject project, Counter counter) throws IOException {
        for (PlacedMesh mesh : geometry.meshes(item, filter)) {
            if (mesh.triangleCount() == 0) {
                continue;
            }
            counter.add(mesh.triangleCount());
            float[] source = mesh.vertices();
            float[] positions = new float[mesh.vertexCount() * 3];
            for (int i = 0; i + 2 < positions.length; i += 3) {
                double x = source[i];
                double y = source[i + 1];
                double z = source[i + 2];
                positions[i] = (float) mesh.transform().x(x, y, z);
                positions[i + 1] = (float) mesh.transform().y(x, y, z);
                positions[i + 2] = (float) mesh.transform().z(x, y, z);
            }
            int[] indices = Arrays.copyOf(mesh.triangles(), mesh.triangleCount() * 3);
            String color = null;
            if (project != null) {
                color = project.colorOf(item.objectId(), mesh.part());
            }
            parts.add(new PreviewPart(plate, color, positions, indices));
        }
    }

    /** Triangles placed so far; an object placed twice counts twice. */
    private static final class Counter {
        private final long max;
        private long triangles;

        Counter(long max) {
            this.max = max;
        }

        void add(int count) throws ModelTooDetailedException {
            triangles += count;
            if (triangles > max) {
                throw new ModelTooDetailedException("The model has more than " + max + " triangles");
            }
        }
    }
}
