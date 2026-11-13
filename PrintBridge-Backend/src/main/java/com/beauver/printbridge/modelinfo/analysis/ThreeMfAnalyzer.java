package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.modelinfo.analysis.ThreeMfGeometry.BuildItem;
import com.beauver.printbridge.modelinfo.analysis.ThreeMfGeometry.ComponentFilter;
import com.beauver.printbridge.modelinfo.analysis.ThreeMfGeometry.Placement;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a 3MF: Bambu Studio / OrcaSlicer projects plate by plate, anything else (plain 3MF,
 * other slicers' projects) as a single plate.
 */
final class ThreeMfAnalyzer {

    ModelAnalysis analyze(Path file, ReadBudget budget) throws IOException {
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, budget)) {
            ThreeMfGeometry geometry = ThreeMfGeometry.load(pkg);
            if (BambuProject.isPresent(pkg)) {
                return bambu(geometry, BambuProject.read(pkg));
            }
            return singlePlate(geometry, ModelSource.MESH, List.of());
        }
    }

    /** Everything in the build as one plate. */
    private static ModelAnalysis singlePlate(ThreeMfGeometry geometry, ModelSource source,
                                             List<FilamentUsage> filaments) throws IOException {
        Bounds bounds = new Bounds();
        double volume = 0;
        for (BuildItem item : geometry.buildItems()) {
            Placement placement = geometry.place(item, ComponentFilter.ALL);
            bounds.add(placement.bounds());
            volume += placement.volume();
        }
        if (bounds.isEmpty()) {
            throw new ModelAnalysisException("The 3MF contains no geometry");
        }
        PlateAnalysis plate = new PlateAnalysis(1, null, bounds.dimensions(), volume, null, null, filaments);
        return new ModelAnalysis(source, volume, List.of(plate));
    }

    /**
     * Per plate: the bounding box of its object instances, and the sliced numbers when present.
     * Objects that aren't on any plate are not printed, so they don't count. A project without
     * plate data is treated as one plate.
     */
    private static ModelAnalysis bambu(ThreeMfGeometry geometry, BambuProject project) throws IOException {
        ComponentFilter printedParts = project::isPrintedPart;
        Map<Integer, List<BuildItem>> itemsByObject = new HashMap<>();
        for (BuildItem item : geometry.buildItems()) {
            itemsByObject.computeIfAbsent(item.objectId(), id -> new ArrayList<>()).add(item);
        }
        if (project.plates().isEmpty()) {
            return singlePlate(geometry, ModelSource.BAMBU, project.filamentsOf(itemsByObject.keySet()));
        }

        List<PlateAnalysis> plates = new ArrayList<>();
        Bounds everything = new Bounds();
        double volume = 0;
        for (BambuProject.Plate plate : project.plates()) {
            Bounds bounds = new Bounds();
            double plateVolume = 0;
            Set<Integer> objectIds = new LinkedHashSet<>();
            for (BambuProject.Instance instance : plate.instances()) {
                Optional<BuildItem> item = itemFor(itemsByObject, instance);
                if (item.isEmpty()) {
                    continue;
                }
                Placement placement = geometry.place(item.get(), printedParts);
                bounds.add(placement.bounds());
                plateVolume += placement.volume();
                objectIds.add(instance.objectId());
            }
            everything.add(bounds);
            volume += plateVolume;
            plates.add(plateAnalysis(plate, bounds, plateVolume, project, objectIds));
        }
        if (everything.isEmpty()) {
            throw new ModelAnalysisException("The project has no objects on its plates");
        }
        return new ModelAnalysis(ModelSource.BAMBU, volume, plates);
    }

    private static PlateAnalysis plateAnalysis(BambuProject.Plate plate, Bounds bounds, double volume,
                                               BambuProject project, Set<Integer> objectIds) {
        Optional<BambuProject.SlicedPlate> sliced = project.sliced(plate.index());
        if (sliced.isPresent()) {
            BambuProject.SlicedPlate result = sliced.get();
            return new PlateAnalysis(plate.index(), plate.name(), bounds.dimensions(), volume, result.grams(),
                    result.printSeconds(), result.filaments());
        }
        List<FilamentUsage> filaments = List.of();
        if (!objectIds.isEmpty()) {
            filaments = project.filamentsOf(objectIds);
        }
        return new PlateAnalysis(plate.index(), plate.name(), bounds.dimensions(), volume, null, null,
                filaments);
    }

    /** Instances are the build items of an object in document order; fall back to the first. */
    private static Optional<BuildItem> itemFor(Map<Integer, List<BuildItem>> itemsByObject,
                                               BambuProject.Instance instance) {
        List<BuildItem> items = itemsByObject.getOrDefault(instance.objectId(), List.of());
        if (items.isEmpty()) {
            return Optional.empty();
        }
        if (instance.instanceId() >= 0 && instance.instanceId() < items.size()) {
            return Optional.of(items.get(instance.instanceId()));
        }
        return Optional.of(items.getFirst());
    }
}
