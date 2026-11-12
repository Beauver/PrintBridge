package com.beauver.printbridge.modelinfo.analysis;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Project metadata written by PrusaSlicer. Its project files hold no slicing results, so this
 * only gives the filaments: {@code Metadata/Slic3r_PE.config} has the filament slots as
 * {@code ; filament_type = PLA;PETG} lines, {@code Metadata/Slic3r_PE_model.config} the extruder
 * per object and volume. Built from the documented format; not checked against real files yet.
 */
final class PrusaProject {

    static final String CONFIG = "Metadata/Slic3r_PE.config";
    static final String MODEL_CONFIG = "Metadata/Slic3r_PE_model.config";
    private static final String PRINTED_VOLUME = "ModelPart";

    private final List<String> types = new ArrayList<>();
    private final List<String> colors = new ArrayList<>();
    private final SortedSet<Integer> usedSlots = new TreeSet<>();

    private PrusaProject() {
    }

    static boolean isPresent(ThreeMfPackage pkg) {
        return pkg.has(CONFIG) || pkg.has(MODEL_CONFIG);
    }

    static PrusaProject read(ThreeMfPackage pkg) throws IOException {
        PrusaProject project = new PrusaProject();
        Optional<InputStream> config = pkg.openIfPresent(CONFIG);
        if (config.isPresent()) {
            try (InputStream in = config.get()) {
                project.readConfig(in);
            }
        }
        Optional<InputStream> modelConfig = pkg.openIfPresent(MODEL_CONFIG);
        if (modelConfig.isPresent()) {
            try (InputStream in = modelConfig.get()) {
                project.readModelConfig(in);
            }
        }
        return project;
    }

    /** The filament slots used by the model (slot 1 if the project doesn't say). */
    List<FilamentUsage> filaments() {
        SortedSet<Integer> slots = usedSlots;
        if (slots.isEmpty()) {
            slots = new TreeSet<>(List.of(1));
        }
        Set<FilamentUsage> used = new LinkedHashSet<>();
        for (int slot : slots) {
            if (slot <= Math.max(types.size(), colors.size())) {
                used.add(new FilamentUsage(valueAt(types, slot), SlicerText.color(valueAt(colors, slot)), null));
            }
        }
        return List.copyOf(used);
    }

    private static String valueAt(List<String> values, int slot) {
        if (slot > values.size()) {
            return null;
        }
        return values.get(slot - 1);
    }

    private void readConfig(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith(";")) {
                trimmed = trimmed.substring(1).trim();
            }
            int equals = trimmed.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = trimmed.substring(0, equals).trim();
            String value = trimmed.substring(equals + 1).trim();
            if (key.equals("filament_type")) {
                split(value, types);
            } else if (key.equals("filament_colour")) {
                split(value, colors);
            }
        }
    }

    private static void split(String value, List<String> target) {
        target.clear();
        for (String part : value.split(";", -1)) {
            target.add(SlicerText.clean(part));
        }
    }

    /** A volume's extruder; 0 or missing means it uses the object's extruder. */
    private static Integer ownExtruder(String value) {
        if (value == null || value.trim().equals("0")) {
            return null;
        }
        return SlicerText.extruder(value);
    }

    private void readModelConfig(InputStream in) throws ModelAnalysisException {
        try {
            XMLStreamReader reader = Xml.reader(in);
            try {
                readModelConfig(reader);
            } finally {
                Xml.close(reader);
            }
        } catch (XMLStreamException e) {
            throw Xml.failure("Invalid " + MODEL_CONFIG, e);
        }
    }

    /**
     * Collects the extruder of every printed volume; a volume without its own extruder uses its
     * object's. Modifiers and negative volumes don't count.
     */
    private void readModelConfig(XMLStreamReader reader) throws XMLStreamException {
        int objectExtruder = 1;
        Integer volumeExtruder = null;
        String volumeType = null;
        boolean inVolume = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("volume")) {
                if (volumeType == null || volumeType.equals(PRINTED_VOLUME)) {
                    usedSlots.add(volumeExtruder == null ? objectExtruder : volumeExtruder);
                }
                inVolume = false;
                volumeExtruder = null;
                volumeType = null;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "object" -> objectExtruder = 1;
                case "volume" -> inVolume = true;
                case "metadata" -> {
                    String key = Xml.attribute(reader, "key");
                    String value = Xml.attribute(reader, "value");
                    if ("extruder".equals(key) && inVolume) {
                        volumeExtruder = ownExtruder(value);
                    } else if ("extruder".equals(key)) {
                        objectExtruder = SlicerText.extruder(value);
                    } else if ("volume_type".equals(key) && inVolume) {
                        volumeType = value;
                    }
                }
                default -> {
                    // Mesh statistics and the like: not needed.
                }
            }
        }
    }
}
