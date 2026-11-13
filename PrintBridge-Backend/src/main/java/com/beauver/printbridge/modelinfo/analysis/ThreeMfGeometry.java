package com.beauver.printbridge.modelinfo.analysis;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The geometry of a 3MF: objects (meshes or components pointing at other objects, possibly in
 * other model parts, as Bambu Studio does with {@code 3D/Objects/*.model}) and the build items
 * that place them. Parts are parsed lazily and cached. For model info meshes keep only their
 * vertices and volume; for a preview ({@link #loadWithTriangles}) the triangles are kept too.
 */
final class ThreeMfGeometry {

    /** Decides per component of a build item's object whether it counts (e.g. skip modifiers). */
    @FunctionalInterface
    interface ComponentFilter {
        ComponentFilter ALL = (objectId, componentObjectId) -> true;

        boolean includes(int objectId, int componentObjectId);
    }

    record BuildItem(int objectId, String path, Transform transform) {
    }

    /** Where a build item ends up: its bounding box and solid volume (mm³). */
    record Placement(Bounds bounds, double volume) {
    }

    /**
     * A mesh as a build item places it. {@code part} is the object id of the item's component the
     * mesh belongs to (the part in Bambu terms), or the item's own object id for a plain mesh.
     * The arrays are shared with the parsed part: read only.
     */
    record PlacedMesh(int part, float[] vertices, int vertexCount, int[] triangles, int triangleCount,
                      Transform transform) {
    }

    @FunctionalInterface
    private interface MeshVisitor {
        void visit(int part, Mesh mesh, Transform transform) throws ModelAnalysisException;
    }

    private record Component(int objectId, String path, Transform transform) {
    }

    private static final class Mesh {
        float[] vertices = new float[3 * 1024];
        int vertexCount;
        double signedVolume;
        /** Vertex indices, three per triangle; only kept for a preview. */
        int[] triangles = new int[0];
        int triangleCount;
    }

    private static final class ObjectResource {
        Mesh mesh;
        final List<Component> components = new ArrayList<>();
    }

    private record ModelPart(Map<Integer, ObjectResource> objects, List<BuildItem> build) {
    }

    private static final int MAX_DEPTH = 8;

    private final ThreeMfPackage pkg;
    private final String rootPath;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final boolean keepTriangles;

    private ThreeMfGeometry(ThreeMfPackage pkg, String rootPath, boolean keepTriangles) {
        this.pkg = pkg;
        this.rootPath = rootPath;
        this.keepTriangles = keepTriangles;
    }

    static ThreeMfGeometry load(ThreeMfPackage pkg) throws IOException {
        return load(pkg, false);
    }

    /** Also keeps the triangles, for {@link #meshes}. */
    static ThreeMfGeometry loadWithTriangles(ThreeMfPackage pkg) throws IOException {
        return load(pkg, true);
    }

    private static ThreeMfGeometry load(ThreeMfPackage pkg, boolean keepTriangles) throws IOException {
        ThreeMfGeometry geometry = new ThreeMfGeometry(pkg, pkg.rootModelPath(), keepTriangles);
        geometry.part(geometry.rootPath);
        return geometry;
    }

    List<BuildItem> buildItems() throws IOException {
        return part(rootPath).build();
    }

    Placement place(BuildItem item, ComponentFilter filter) throws IOException {
        Bounds bounds = new Bounds();
        double[] volume = new double[1];
        walk(item, filter, (part, mesh, transform) -> {
            float[] v = mesh.vertices;
            for (int i = 0; i < mesh.vertexCount * 3; i += 3) {
                bounds.add(transform.x(v[i], v[i + 1], v[i + 2]),
                        transform.y(v[i], v[i + 1], v[i + 2]),
                        transform.z(v[i], v[i + 1], v[i + 2]));
            }
            volume[0] += Math.abs(mesh.signedVolume * transform.determinant());
        });
        return new Placement(bounds, volume[0]);
    }

    /** The meshes a build item places, with their triangles; needs {@link #loadWithTriangles}. */
    List<PlacedMesh> meshes(BuildItem item, ComponentFilter filter) throws IOException {
        if (!keepTriangles) {
            throw new IllegalStateException("Triangles were not kept");
        }
        List<PlacedMesh> meshes = new ArrayList<>();
        walk(item, filter, (part, mesh, transform) -> meshes.add(new PlacedMesh(part, mesh.vertices,
                mesh.vertexCount, mesh.triangles, mesh.triangleCount, transform)));
        return meshes;
    }

    private void walk(BuildItem item, ComponentFilter filter, MeshVisitor visitor) throws IOException {
        String path = item.path() == null ? rootPath : item.path();
        walk(path, item.objectId(), item.objectId(), item.transform(), 0, filter, visitor);
    }

    private void walk(String path, int objectId, int part, Transform transform, int depth, ComponentFilter filter,
                      MeshVisitor visitor) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new ModelAnalysisException("Components are nested too deeply");
        }
        ObjectResource object = part(path).objects().get(objectId);
        if (object == null) {
            throw new ModelAnalysisException("The 3MF refers to a missing object " + objectId);
        }
        if (object.mesh != null) {
            visitor.visit(part, object.mesh, transform);
        }
        for (Component component : object.components) {
            if (depth == 0 && !filter.includes(objectId, component.objectId())) {
                continue;
            }
            String componentPath = path;
            if (component.path() != null) {
                componentPath = component.path();
            }
            int componentPart = part;
            if (depth == 0) {
                componentPart = component.objectId();
            }
            walk(componentPath, component.objectId(), componentPart, component.transform().then(transform),
                    depth + 1, filter, visitor);
        }
    }

    private ModelPart part(String path) throws IOException {
        String key = ThreeMfPackage.normalize(path);
        ModelPart part = parts.get(key);
        if (part == null) {
            try (InputStream in = pkg.open(path)) {
                part = parse(in);
            }
            parts.put(key, part);
        }
        return part;
    }

    private ModelPart parse(InputStream in) throws ModelAnalysisException {
        try {
            XMLStreamReader reader = Xml.reader(in);
            try {
                return parse(reader);
            } finally {
                Xml.close(reader);
            }
        } catch (XMLStreamException e) {
            throw Xml.failure("Invalid 3MF model XML", e);
        }
    }

    private ModelPart parse(XMLStreamReader reader) throws XMLStreamException, ModelAnalysisException {
        Map<Integer, ObjectResource> objects = new HashMap<>();
        List<BuildItem> build = new ArrayList<>();
        double scale = 1;
        ObjectResource object = null;
        int objectId = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("object") && object != null) {
                objects.put(objectId, object);
                object = null;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "model" -> scale = unitScale(Xml.attribute(reader, "unit"));
                case "object" -> {
                    object = new ObjectResource();
                    objectId = Xml.intAttribute(reader, "id");
                }
                case "mesh" -> {
                    if (object != null) {
                        object.mesh = new Mesh();
                    }
                }
                case "vertex" -> {
                    if (object != null && object.mesh != null) {
                        addVertex(object.mesh, reader, scale);
                    }
                }
                case "triangle" -> {
                    if (object != null && object.mesh != null) {
                        addTriangle(object.mesh, reader, keepTriangles);
                    }
                }
                case "component" -> {
                    if (object != null) {
                        object.components.add(new Component(Xml.intAttribute(reader, "objectid"),
                                Xml.attribute(reader, "path"), Transform.parse(Xml.attribute(reader, "transform"))));
                    }
                }
                case "item" -> build.add(new BuildItem(Xml.intAttribute(reader, "objectid"),
                        Xml.attribute(reader, "path"), Transform.parse(Xml.attribute(reader, "transform"))));
                default -> {
                    // Metadata, materials, extensions: not needed for size and volume.
                }
            }
        }
        return new ModelPart(objects, build);
    }

    private void addVertex(Mesh mesh, XMLStreamReader reader, double scale) throws ModelAnalysisException {
        pkg.budget().addVertex();
        if (mesh.vertexCount * 3 + 3 > mesh.vertices.length) {
            mesh.vertices = Arrays.copyOf(mesh.vertices, mesh.vertices.length * 2);
        }
        int i = mesh.vertexCount * 3;
        mesh.vertices[i] = (float) (Xml.doubleAttribute(reader, "x") * scale);
        mesh.vertices[i + 1] = (float) (Xml.doubleAttribute(reader, "y") * scale);
        mesh.vertices[i + 2] = (float) (Xml.doubleAttribute(reader, "z") * scale);
        mesh.vertexCount++;
    }

    private void addTriangle(Mesh mesh, XMLStreamReader reader, boolean keep) throws ModelAnalysisException {
        int a = vertexIndex(mesh, Xml.intAttribute(reader, "v1"));
        int b = vertexIndex(mesh, Xml.intAttribute(reader, "v2"));
        int c = vertexIndex(mesh, Xml.intAttribute(reader, "v3"));
        if (keep) {
            pkg.budget().addTriangle();
            if (mesh.triangleCount * 3 + 3 > mesh.triangles.length) {
                mesh.triangles = Arrays.copyOf(mesh.triangles, Math.max(3 * 1024, mesh.triangles.length * 2));
            }
            int i = mesh.triangleCount * 3;
            mesh.triangles[i] = a / 3;
            mesh.triangles[i + 1] = b / 3;
            mesh.triangles[i + 2] = c / 3;
            mesh.triangleCount++;
        }
        float[] v = mesh.vertices;
        mesh.signedVolume += MeshAccumulator.signedTetrahedronVolume(v[a], v[a + 1], v[a + 2],
                v[b], v[b + 1], v[b + 2], v[c], v[c + 1], v[c + 2]);
    }

    private static int vertexIndex(Mesh mesh, int index) throws ModelAnalysisException {
        if (index < 0 || index >= mesh.vertexCount) {
            throw new ModelAnalysisException("A triangle refers to a missing vertex");
        }
        return index * 3;
    }

    /** Factor to millimetres for the 3MF {@code unit} attribute (default millimetre). */
    static double unitScale(String unit) throws ModelAnalysisException {
        if (unit == null) {
            return 1;
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "micron" -> 0.001;
            case "millimeter", "" -> 1;
            case "centimeter" -> 10;
            case "inch" -> 25.4;
            case "foot" -> 304.8;
            case "meter" -> 1000;
            default -> throw new ModelAnalysisException("Unknown 3MF unit: " + unit);
        };
    }
}
