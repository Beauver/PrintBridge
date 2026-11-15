package com.beauver.printbridge.modelfile.threemf;

import com.beauver.printbridge.modelfile.exception.ModelTooDetailedException;
import com.beauver.printbridge.modelfile.ReadBudget;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.ThreeMfFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.beauver.printbridge.support.ThreeMfFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class ThreeMfGeometryTest {

    @TempDir
    Path dir;

    @Test
    void meshesNeedTheTriangles() throws Exception {
        Path file = ModelFiles.write(dir, "plain.3mf", ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null))).build());
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget())) {
            ThreeMfGeometry geometry = ThreeMfGeometry.load(pkg);
            ThreeMfGeometry.BuildItem item = geometry.buildItems().getFirst();
            assertThrows(IllegalStateException.class,
                    () -> geometry.meshes(item, ThreeMfGeometry.ComponentFilter.ALL));
        }
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget(ReadBudget.MAX_BYTES, 100, 5))) {
            ModelTooDetailedException e = assertThrows(ModelTooDetailedException.class,
                    () -> ThreeMfGeometry.loadWithTriangles(pkg));
            assertEquals("The model has more than 5 triangles", e.getMessage());
        }
    }
}
