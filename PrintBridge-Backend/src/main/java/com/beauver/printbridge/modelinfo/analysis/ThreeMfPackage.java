package com.beauver.printbridge.modelinfo.analysis;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** The ZIP container of a 3MF. Every read goes through the analysis' {@link ReadBudget}. */
final class ThreeMfPackage implements Closeable {

    static final String DEFAULT_ROOT_MODEL = "3D/3dmodel.model";
    private static final String ROOT_RELS = "_rels/.rels";
    private static final String MODEL_RELATIONSHIP_SUFFIX = "/3dmodel";

    private final ZipFile zip;
    private final ReadBudget budget;
    /** Entries by lower-case name: part names in 3MF are case-insensitive. */
    private final Map<String, ZipEntry> entries = new HashMap<>();

    private ThreeMfPackage(ZipFile zip, ReadBudget budget) {
        this.zip = zip;
        this.budget = budget;
        zip.stream().forEach(entry -> entries.putIfAbsent(entry.getName().toLowerCase(Locale.ROOT), entry));
    }

    static ThreeMfPackage open(Path file, ReadBudget budget) throws IOException {
        try {
            return new ThreeMfPackage(new ZipFile(file.toFile()), budget);
        } catch (ZipException e) {
            throw Xml.failure("The file is not a valid 3MF package", e);
        }
    }

    ReadBudget budget() {
        return budget;
    }

    boolean has(String name) {
        return entries.containsKey(normalize(name));
    }

    /** Opens a part. Part names may start with a slash, as they do in relationships and {@code p:path}. */
    InputStream open(String name) throws IOException {
        ZipEntry entry = entries.get(normalize(name));
        if (entry == null) {
            throw new ModelAnalysisException("Missing part in 3MF: " + name);
        }
        return new LimitedInputStream(zip.getInputStream(entry), budget);
    }

    Optional<InputStream> openIfPresent(String name) throws IOException {
        if (!has(name)) {
            return Optional.empty();
        }
        return Optional.of(open(name));
    }

    /** The main model part, from the package relationships; the usual default if they don't say. */
    String rootModelPath() throws IOException {
        if (has(ROOT_RELS)) {
            try (InputStream in = open(ROOT_RELS)) {
                String target = findModelTarget(in);
                if (target != null && has(target)) {
                    return normalize(target);
                }
            }
        }
        if (has(DEFAULT_ROOT_MODEL)) {
            return normalize(DEFAULT_ROOT_MODEL);
        }
        return entries.keySet().stream()
                .filter(name -> name.startsWith("3d/") && name.endsWith(".model"))
                .sorted()
                .findFirst()
                .orElseThrow(() -> new ModelAnalysisException("The 3MF has no model part"));
    }

    private static String findModelTarget(InputStream in) throws ModelAnalysisException {
        try {
            XMLStreamReader reader = Xml.reader(in);
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT
                            && reader.getLocalName().equals("Relationship")) {
                        String type = Xml.attribute(reader, "Type");
                        if (type != null && type.endsWith(MODEL_RELATIONSHIP_SUFFIX)) {
                            return Xml.attribute(reader, "Target");
                        }
                    }
                }
                return null;
            } finally {
                Xml.close(reader);
            }
        } catch (XMLStreamException e) {
            throw Xml.failure("Invalid 3MF relationships", e);
        }
    }

    static String normalize(String name) {
        String normalized = name.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
