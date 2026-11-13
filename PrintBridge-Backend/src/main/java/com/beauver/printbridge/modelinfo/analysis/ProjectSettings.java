package com.beauver.printbridge.modelinfo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The print settings a Bambu Studio / OrcaSlicer project was saved with
 * ({@code Metadata/project_settings.config}). Server slicing uses them to pick the right nozzle
 * and to keep the designer's choices when slicing for another nozzle.
 *
 * @param nozzle        nozzle size the project was made for, in mm; {@code null} when unknown
 * @param printerModel  e.g. "Bambu Lab P1S"; {@code null} when unknown
 * @param bedWidth      printable width in mm; {@code null} when unknown
 * @param bedDepth      printable depth in mm; {@code null} when unknown
 * @param keptSettings  the designer's {@link #KEPT_SETTINGS} that are present, as OrcaSlicer writes them
 * @param filamentSlots number of filament slots in the project (at least 1)
 */
public record ProjectSettings(
        BigDecimal nozzle,
        String printerModel,
        Integer bedWidth,
        Integer bedDepth,
        Map<String, String> keptSettings,
        int filamentSlots) {

    /** Settings of the designer that stay when the project is sliced with another process profile. */
    public static final List<String> KEPT_SETTINGS = List.of("sparse_infill_density", "wall_loops", "enable_support");

    /** The settings of a 3MF project, if it has them; never throws for a broken file. */
    public static Optional<ProjectSettings> read(Path file) {
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget())) {
            Optional<InputStream> settings = pkg.openIfPresent(BambuProject.PROJECT_SETTINGS);
            if (settings.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = settings.get()) {
                return Optional.ofNullable(new ObjectMapper().readTree(in))
                        .filter(JsonNode::isObject)
                        .map(ProjectSettings::of);
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    static ProjectSettings of(JsonNode json) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (String key : KEPT_SETTINGS) {
            String value = first(json.path(key));
            if (value != null) {
                kept.put(key, value);
            }
        }
        int[] bed = bed(json.path("printable_area"));
        Integer bedWidth = null;
        Integer bedDepth = null;
        if (bed != null) {
            bedWidth = bed[0];
            bedDepth = bed[1];
        }
        return new ProjectSettings(nozzle(first(json.path("nozzle_diameter"))),
                SlicerText.clean(first(json.path("printer_model"))), bedWidth, bedDepth, kept,
                Math.max(1, json.path("filament_type").size()));
    }

    /** OrcaSlicer writes most settings as strings, some as one-element arrays. */
    private static String first(JsonNode node) {
        JsonNode value = node;
        if (node.isArray()) {
            value = node.path(0);
        }
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            return null;
        }
        return SlicerText.clean(value.asText());
    }

    private static BigDecimal nozzle(String value) {
        try {
            BigDecimal nozzle = new BigDecimal(value == null ? "" : value).stripTrailingZeros();
            if (nozzle.signum() <= 0) {
                return null;
            }
            return nozzle;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Width and depth from corners like {@code ["0x0", "256x0", "256x256", "0x256"]}. */
    private static int[] bed(JsonNode area) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (JsonNode corner : area) {
            String[] parts = corner.asText().split("x");
            if (parts.length != 2) {
                return null;
            }
            try {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (minX > maxX) {
            return null;
        }
        return new int[]{(int) Math.round(maxX - minX), (int) Math.round(maxY - minY)};
    }
}
