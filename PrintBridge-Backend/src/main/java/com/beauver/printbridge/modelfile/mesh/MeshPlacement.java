package com.beauver.printbridge.modelfile.mesh;

import com.beauver.printbridge.modelfile.geometry.Bounds;
import java.util.List;

/** Moves the parts of a preview into place on the bed. */
public final class MeshPlacement {

    private MeshPlacement() {
    }

    /** Centres the parts on the bed (or on 0, 0 without one) and stands them on it. */
    public static void centre(List<PreviewPart> parts, PreviewBed bed) {
        if (parts.isEmpty()) {
            return;
        }
        Bounds bounds = new Bounds();
        for (PreviewPart part : parts) {
            float[] p = part.positions();
            for (int i = 0; i + 2 < p.length; i += 3) {
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

    public static void move(List<PreviewPart> parts, double x, double y, double z) {
        for (PreviewPart part : parts) {
            float[] p = part.positions();
            for (int i = 0; i + 2 < p.length; i += 3) {
                p[i] += (float) x;
                p[i + 1] += (float) y;
                p[i + 2] += (float) z;
            }
        }
    }
}
