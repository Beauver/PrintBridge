package com.beauver.printbridge.modelinfo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Project metadata written by Bambu Studio and OrcaSlicer (same format):
 * <ul>
 *   <li>{@code Metadata/model_settings.config}: plates with the object instances on them, and per
 *       object and part the extruder (filament slot) and part type (normal, modifier, ...).</li>
 *   <li>{@code Metadata/project_settings.config}: JSON with the project's filament slots.</li>
 *   <li>{@code Metadata/slice_info.config}: only filled in after slicing; per plate the print
 *       time, weight and filaments actually used.</li>
 * </ul>
 */
final class BambuProject {

    static final String MODEL_SETTINGS = "Metadata/model_settings.config";
    static final String PROJECT_SETTINGS = "Metadata/project_settings.config";
    static final String SLICE_INFO = "Metadata/slice_info.config";
    private static final String NORMAL_PART = "normal_part";
    private static final int MAX_PLATES = 500;

    record Instance(int objectId, int instanceId) {
    }

    record Plate(int index, String name, List<Instance> instances) {
    }

    record SlicedPlate(Double grams, Long printSeconds, List<FilamentUsage> filaments) {
    }

    record Filament(String type, String color) {
    }

    private final List<Plate> plates = new ArrayList<>();
    /** Extruders used per object: the object's own plus those of its normal parts. */
    private final Map<Integer, Set<Integer>> extruders = new HashMap<>();
    /** The extruder (filament slot) set on an object itself, and per object on its parts. */
    private final Map<Integer, Integer> objectExtruders = new HashMap<>();
    private final Map<Integer, Map<Integer, Integer>> partExtruders = new HashMap<>();
    /** Part types per object and part id; parts that aren't listed count as normal. */
    private final Map<Integer, Map<Integer, String>> partTypes = new HashMap<>();
    private final Map<Integer, SlicedPlate> sliced = new HashMap<>();
    private List<Filament> filaments = List.of();

    private BambuProject() {
    }

    static boolean isPresent(ThreeMfPackage pkg) {
        return pkg.has(MODEL_SETTINGS);
    }

    static BambuProject read(ThreeMfPackage pkg) throws IOException {
        BambuProject project = new BambuProject();
        try (InputStream in = pkg.open(MODEL_SETTINGS)) {
            project.readModelSettings(in);
        }
        Optional<InputStream> settings = pkg.openIfPresent(PROJECT_SETTINGS);
        if (settings.isPresent()) {
            try (InputStream in = settings.get()) {
                project.filaments = readFilaments(in);
            }
        }
        Optional<InputStream> sliceInfo = pkg.openIfPresent(SLICE_INFO);
        if (sliceInfo.isPresent()) {
            try (InputStream in = sliceInfo.get()) {
                project.readSliceInfo(in);
            }
        }
        return project;
    }

    List<Plate> plates() {
        return plates;
    }

    /** Sliced data of a plate, if the project was sliced (and saved) with that plate. */
    Optional<SlicedPlate> sliced(int plateIndex) {
        return Optional.ofNullable(sliced.get(plateIndex));
    }

    /** Only normal parts are printed as the model; modifiers, negative parts and support blockers aren't. */
    boolean isPrintedPart(int objectId, int partId) {
        String type = partTypes.getOrDefault(objectId, Map.of()).get(partId);
        return type == null || type.equals(NORMAL_PART);
    }

    /**
     * The project filaments used by these objects, in slot order, without slicing data. Slots
     * with the same type and colour are listed once.
     */
    List<FilamentUsage> filamentsOf(Collection<Integer> objectIds) {
        SortedSet<Integer> slots = new TreeSet<>();
        for (int objectId : objectIds) {
            Set<Integer> objectSlots = extruders.getOrDefault(objectId, Set.of());
            if (objectSlots.isEmpty()) {
                slots.add(1);
            } else {
                slots.addAll(objectSlots);
            }
        }
        Set<FilamentUsage> used = new LinkedHashSet<>();
        for (int slot : slots) {
            if (slot <= filaments.size()) {
                Filament filament = filaments.get(slot - 1);
                used.add(new FilamentUsage(filament.type(), filament.color(), null));
            }
        }
        return List.copyOf(used);
    }

    /**
     * The "#RRGGBB" colour of the filament a part is printed with: the part's own slot, else the
     * object's, else slot 1. {@code null} when the project doesn't say.
     */
    String colorOf(int objectId, int partId) {
        Integer slot = partExtruders.getOrDefault(objectId, Map.of()).get(partId);
        if (slot == null) {
            slot = objectExtruders.getOrDefault(objectId, 1);
        }
        if (slot < 1 || slot > filaments.size()) {
            return null;
        }
        return filaments.get(slot - 1).color();
    }

    private void readModelSettings(InputStream in) throws ModelAnalysisException {
        try {
            XMLStreamReader reader = Xml.reader(in);
            try {
                readModelSettings(reader);
            } finally {
                Xml.close(reader);
            }
        } catch (XMLStreamException e) {
            throw Xml.failure("Invalid " + MODEL_SETTINGS, e);
        }
    }

    private void readModelSettings(XMLStreamReader reader) throws XMLStreamException, ModelAnalysisException {
        Integer objectId = null;
        Integer partId = null;
        Integer plateIndex = null;
        String plateName = null;
        List<Instance> instances = null;
        Map<String, String> instance = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "object" -> objectId = null;
                    case "part" -> partId = null;
                    case "model_instance" -> {
                        addInstance(instances, instance);
                        instance = null;
                    }
                    case "plate" -> {
                        if (instances != null && plateIndex != null) {
                            addPlate(new Plate(plateIndex, SlicerText.clean(plateName), instances));
                        }
                        instances = null;
                        plateIndex = null;
                        plateName = null;
                    }
                    default -> {
                        // Other elements carry nothing we need.
                    }
                }
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "object" -> {
                    objectId = Xml.intAttribute(reader, "id");
                    extruders.computeIfAbsent(objectId, id -> new TreeSet<>());
                }
                case "part" -> {
                    if (objectId != null) {
                        partId = Xml.intAttribute(reader, "id");
                        String subtype = Xml.attribute(reader, "subtype");
                        partTypes.computeIfAbsent(objectId, id -> new HashMap<>())
                                .put(partId, subtype == null ? NORMAL_PART : subtype);
                    }
                }
                case "plate" -> instances = new ArrayList<>();
                case "model_instance" -> instance = new HashMap<>();
                case "metadata" -> {
                    String key = Xml.attribute(reader, "key");
                    String value = Xml.attribute(reader, "value");
                    if (key == null) {
                        continue;
                    }
                    if (instance != null) {
                        instance.put(key, value);
                    } else if (instances != null) {
                        if (key.equals("plater_id")) {
                            plateIndex = parseInt(value);
                        } else if (key.equals("plater_name")) {
                            plateName = value;
                        }
                    } else if (objectId != null && key.equals("extruder")
                            && (partId == null || isPrintedPart(objectId, partId))) {
                        int extruder = SlicerText.extruder(value);
                        extruders.get(objectId).add(extruder);
                        if (partId == null) {
                            objectExtruders.put(objectId, extruder);
                        } else {
                            partExtruders.computeIfAbsent(objectId, id -> new HashMap<>()).put(partId, extruder);
                        }
                    }
                }
                default -> {
                    // Mesh statistics, assembly info, ...: not needed.
                }
            }
        }
        plates.sort(Comparator.comparingInt(Plate::index));
    }

    private static void addInstance(List<Instance> instances, Map<String, String> instance) {
        if (instances == null || instance == null) {
            return;
        }
        Integer objectId = parseInt(instance.get("object_id"));
        Integer instanceId = parseInt(instance.get("instance_id"));
        if (objectId == null) {
            return;
        }
        if (instanceId == null) {
            instanceId = 0;
        }
        instances.add(new Instance(objectId, instanceId));
    }

    private void addPlate(Plate plate) throws ModelAnalysisException {
        if (plates.size() >= MAX_PLATES) {
            throw new ModelAnalysisException("The project has more than " + MAX_PLATES + " plates");
        }
        plates.add(plate);
    }

    private static List<Filament> readFilaments(InputStream in) throws IOException {
        JsonNode settings;
        try {
            settings = new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw Xml.failure("Invalid " + PROJECT_SETTINGS, e);
        }
        if (settings == null) {
            return List.of();
        }
        JsonNode types = settings.path("filament_type");
        JsonNode colors = settings.path("filament_colour");
        List<Filament> filaments = new ArrayList<>();
        for (int i = 0; i < Math.max(types.size(), colors.size()); i++) {
            filaments.add(new Filament(SlicerText.clean(types.path(i).asText(null)),
                    SlicerText.color(colors.path(i).asText(null))));
        }
        return filaments;
    }

    private void readSliceInfo(InputStream in) throws ModelAnalysisException {
        try {
            XMLStreamReader reader = Xml.reader(in);
            try {
                readSliceInfo(reader);
            } finally {
                Xml.close(reader);
            }
        } catch (XMLStreamException e) {
            throw Xml.failure("Invalid " + SLICE_INFO, e);
        }
    }

    /** A {@code <plate>} of slice_info.config while it is being read. */
    private static final class SliceInfoPlate {
        final Map<String, String> metadata = new HashMap<>();
        final List<FilamentUsage> filaments = new ArrayList<>();

        void read(XMLStreamReader reader) {
            switch (reader.getLocalName()) {
                case "metadata" -> metadata.put(Xml.attribute(reader, "key"), Xml.attribute(reader, "value"));
                case "filament" -> filaments.add(new FilamentUsage(SlicerText.clean(Xml.attribute(reader, "type")),
                        SlicerText.color(Xml.attribute(reader, "color")),
                        parseDouble(Xml.attribute(reader, "used_g"))));
                default -> {
                    // Objects, warnings: not needed.
                }
            }
        }
    }

    /** Elements outside a {@code <plate>} (the header) are skipped. */
    private void readSliceInfo(XMLStreamReader reader) throws XMLStreamException {
        SliceInfoPlate plate = null;
        while (reader.hasNext()) {
            int event = reader.next();
            boolean isPlate = (event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.END_ELEMENT)
                    && reader.getLocalName().equals("plate");
            if (event == XMLStreamConstants.START_ELEMENT && isPlate) {
                plate = new SliceInfoPlate();
            } else if (event == XMLStreamConstants.END_ELEMENT && isPlate && plate != null) {
                addSlicedPlate(plate);
                plate = null;
            } else if (event == XMLStreamConstants.START_ELEMENT && plate != null) {
                plate.read(reader);
            }
        }
    }

    private void addSlicedPlate(SliceInfoPlate plate) {
        Integer index = parseInt(plate.metadata.get("index"));
        if (index != null) {
            sliced.put(index, new SlicedPlate(parseDouble(plate.metadata.get("weight")),
                    parseLong(plate.metadata.get("prediction")), plate.filaments));
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            double number = Double.parseDouble(value == null ? "" : value.trim());
            if (Double.isFinite(number) && number >= 0) {
                return number;
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
