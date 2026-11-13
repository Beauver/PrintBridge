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

    /**
     * Like Bambu Studio: root objects 2 and 4 are components pointing into 3D/Objects/object_1.model.
     * Object 2 has two instances and a modifier part (object 5, a big box that must not count).
     * Plate 1 holds both instances of object 2, plate 2 object 4, plate 3 is empty.
     */
    public static ThreeMfFiles bambuProject() {
        String root = model("millimeter",
                components(2, new String[]{"1", "/3D/Objects/object_1.model", null},
                        new String[]{"5", "/3D/Objects/object_1.model", null})
                        + components(4, new String[]{"3", "/3D/Objects/object_1.model", null}),
                item(2, move(0, 0, 0)) + item(2, move(30, 0, 0)) + item(4, move(300, 0, 0)));
        String objects = model("millimeter", cube(1, 10) + cube(3, 20) + box(5, 50, 50, 50), "");
        String modelSettings = """
                <?xml version="1.0" encoding="UTF-8"?>
                <config>
                  <object id="2">
                    <metadata key="name" value="Body"/>
                    <metadata key="extruder" value="1"/>
                    <part id="1" subtype="normal_part"><metadata key="name" value="Body"/></part>
                    <part id="5" subtype="modifier_part"><metadata key="extruder" value="3"/></part>
                  </object>
                  <object id="4">
                    <metadata key="extruder" value="2"/>
                    <part id="3" subtype="normal_part"/>
                  </object>
                  <plate>
                    <metadata key="plater_id" value="1"/>
                    <metadata key="plater_name" value="  First  "/>
                    <model_instance>
                      <metadata key="object_id" value="2"/>
                      <metadata key="instance_id" value="0"/>
                    </model_instance>
                    <model_instance>
                      <metadata key="object_id" value="2"/>
                      <metadata key="instance_id" value="1"/>
                    </model_instance>
                  </plate>
                  <plate>
                    <metadata key="plater_id" value="2"/>
                    <metadata key="plater_name" value=""/>
                    <model_instance>
                      <metadata key="object_id" value="4"/>
                      <metadata key="instance_id" value="9"/>
                    </model_instance>
                    <model_instance>
                      <metadata key="object_id" value="99"/>
                    </model_instance>
                    <model_instance>
                      <metadata key="instance_id" value="0"/>
                    </model_instance>
                  </plate>
                  <plate>
                    <metadata key="plater_id" value="3"/>
                  </plate>
                </config>
                """;
        String projectSettings = """
                {"filament_type": ["PLA", "PETG", "PLA"], "filament_colour": ["#FFFFFF", "#ff0000aa", "nope"],
                 "printable_area": ["0x0", "256x0", "256x256", "0x256"]}
                """;
        return ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", root)
                .entry("3D/Objects/object_1.model", objects)
                .entry("Metadata/model_settings.config", modelSettings)
                .entry("Metadata/project_settings.config", projectSettings)
                .entry("Metadata/slice_info.config", "<config><header/></config>");
    }
}
