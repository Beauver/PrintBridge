package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.modelinfo.analysis.ThreeMfGeometry.BuildItem;
import com.beauver.printbridge.modelinfo.analysis.ThreeMfGeometry.ComponentFilter;
import com.beauver.printbridge.modelinfo.analysis.ThreeMfGeometry.PlacedMesh;
import com.beauver.printbridge.printrequest.ModelFileType;
import jakarta.enterprise.context.ApplicationScoped;

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

    /** Plate images are a few dozen kB; anything far bigger isn't one. */
    static final long MAX_THUMBNAIL_BYTES = 5L * 1024 * 1024;
    /** Bambu Studio leaves a fifth of the bed between plates. */
    private static final double PLATE_STRIDE = 1.2;

    /**
     * The image Bambu Studio / OrcaSlicer renders of a plate when saving a project
     * ({@code Metadata/plate_N.png}); empty for other files or when there is none.
     */
    public Optional<byte[]> thumbnail(Path file, ModelFileType type, int plate) throws ModelAnalysisException {
        if (type != ModelFileType.THREE_MF || plate < 1) {
            return Optional.empty();
        }
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget(MAX_THUMBNAIL_BYTES, 0))) {
            Optional<InputStream> image = pkg.openIfPresent("Metadata/plate_" + plate + ".png");
            if (image.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = image.get()) {
                return Optional.of(in.readAllBytes());
            }
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
                case STL -> stl(file, bed, maxTriangles);
                case THREE_MF -> threeMf(file, bed, maxTriangles);
            };
        } catch (ModelAnalysisException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelAnalysisException("Could not read the file", e);
        }
    }

    private static PreviewMesh stl(Path file, PreviewBed bed, long maxTriangles) throws IOException {
        MeshBuilder builder = new MeshBuilder(maxTriangles);
        StlAnalyzer.readTriangles(file, builder);
        if (builder.triangleCount() == 0) {
            throw new ModelAnalysisException("The model has no triangles");
        }
        List<PreviewPart> parts = List.of(new PreviewPart(1, null, builder.positions(), builder.indices()));
        centre(parts, bed);
        return new PreviewMesh(bed, List.of(new PreviewPlate(1, null)), parts);
    }

    private static PreviewMesh threeMf(Path file, PreviewBed bed, long maxTriangles) throws IOException {
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
        centre(parts, bed);
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
                move(plateParts, offsetX, offsetY, 0);
            } else {
                centre(plateParts, bed);
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
            for (int i = 0; i < positions.length; i += 3) {
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

    /** Centres the parts on the bed (or on 0, 0 without one) and stands them on it. */
    private static void centre(List<PreviewPart> parts, PreviewBed bed) {
        if (parts.isEmpty()) {
            return;
        }
        Bounds bounds = new Bounds();
        for (PreviewPart part : parts) {
            float[] p = part.positions();
            for (int i = 0; i < p.length; i += 3) {
                bounds.add(p[i], p[i + 1], p[i + 2]);
            }
        }
        double centreX = 0;
        double centreY = 0;
        if (bed != null) {
            centreX = bed.x() / 2.0;
            centreY = bed.y() / 2.0;
        }
        move(parts, centreX - (bounds.minX() + bounds.maxX()) / 2, centreY - (bounds.minY() + bounds.maxY()) / 2,
                -bounds.minZ());
    }

    private static void move(List<PreviewPart> parts, double x, double y, double z) {
        for (PreviewPart part : parts) {
            float[] p = part.positions();
            for (int i = 0; i < p.length; i += 3) {
                p[i] += (float) x;
                p[i + 1] += (float) y;
                p[i + 2] += (float) z;
            }
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
