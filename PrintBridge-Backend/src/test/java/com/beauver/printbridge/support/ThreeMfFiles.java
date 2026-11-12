package com.beauver.printbridge.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds small 3MF packages for tests, modelled on what Bambu Studio and PrusaSlicer write (see
 * the analysis package docs): cubes as meshes, components across model parts, plates and
 * slicer metadata.
 */
public final class ThreeMfFiles {

    public static final String CORE_NS = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02";
    public static final String PRODUCTION_NS = "http://schemas.microsoft.com/3dmanufacturing/production/2015/06";

    private final Map<String, String> entries = new LinkedHashMap<>();

    public static ThreeMfFiles builder() {
        return new ThreeMfFiles().entry("[Content_Types].xml", "<Types/>");
    }

    public ThreeMfFiles entry(String name, String content) {
        entries.put(name, content);
        return this;
    }

    public byte[] build() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A model part with the given unit, resources and build items. */
    public static String model(String unit, String resources, String build) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<model unit=\"" + unit + "\" xmlns=\"" + CORE_NS + "\" xmlns:p=\"" + PRODUCTION_NS + "\">"
                + "<resources>" + resources + "</resources>"
                + "<build>" + build + "</build></model>";
    }

    /** A closed cube mesh object from (0,0,0) to (size,size,size), outward facing. */
    public static String cube(int id, double size) {
        return box(id, size, size, size);
    }

    public static String box(int id, double x, double y, double z) {
        double[][] corners = {
                {0, 0, 0}, {x, 0, 0}, {x, y, 0}, {0, y, 0},
                {0, 0, z}, {x, 0, z}, {x, y, z}, {0, y, z}};
        int[][] triangles = {
                {0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7}, {0, 1, 5}, {0, 5, 4},
                {3, 7, 6}, {3, 6, 2}, {0, 4, 7}, {0, 7, 3}, {1, 2, 6}, {1, 6, 5}};
        StringBuilder xml = new StringBuilder("<object id=\"" + id + "\" type=\"model\"><mesh><vertices>");
        for (double[] c : corners) {
            xml.append(String.format(Locale.ROOT, "<vertex x=\"%s\" y=\"%s\" z=\"%s\"/>", c[0], c[1], c[2]));
        }
        xml.append("</vertices><triangles>");
        for (int[] t : triangles) {
            xml.append("<triangle v1=\"").append(t[0]).append("\" v2=\"").append(t[1])
                    .append("\" v3=\"").append(t[2]).append("\"/>");
        }
        return xml.append("</triangles></mesh></object>").toString();
    }

    /** An object made of components: {@code {objectId, path or null, transform or null}}. */
    public static String components(int id, String[]... components) {
        StringBuilder xml = new StringBuilder("<object id=\"" + id + "\" type=\"model\"><components>");
        for (String[] component : components) {
            xml.append("<component objectid=\"").append(component[0]).append('"');
            if (component[1] != null) {
                xml.append(" p:path=\"").append(component[1]).append('"');
            }
            if (component[2] != null) {
                xml.append(" transform=\"").append(component[2]).append('"');
            }
            xml.append("/>");
        }
        return xml.append("</components></object>").toString();
    }

    public static String item(int objectId, String transform) {
        if (transform == null) {
            return "<item objectid=\"" + objectId + "\"/>";
        }
        return "<item objectid=\"" + objectId + "\" transform=\"" + transform + "\"/>";
    }

    /** Translation-only transform. */
    public static String move(double x, double y, double z) {
        return String.format(Locale.ROOT, "1 0 0 0 1 0 0 0 1 %s %s %s", x, y, z);
    }
}
