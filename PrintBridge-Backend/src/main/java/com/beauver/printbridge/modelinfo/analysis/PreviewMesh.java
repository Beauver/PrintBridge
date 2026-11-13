package com.beauver.printbridge.modelinfo.analysis;

import java.util.List;

/**
 * What the 3D preview shows of a model file: its plates and the meshes on them.
 *
 * @param bed the bed everything is placed on; {@code null} when unknown (then centred on 0, 0)
 */
public record PreviewMesh(PreviewBed bed, List<PreviewPlate> plates, List<PreviewPart> parts) {

    public long triangleCount() {
        return parts.stream().mapToLong(PreviewPart::triangleCount).sum();
    }
}
