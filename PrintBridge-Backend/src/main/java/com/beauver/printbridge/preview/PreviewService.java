package com.beauver.printbridge.preview;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.error.UnprocessableContentException;
import com.beauver.printbridge.modelinfo.analysis.ModelAnalysisException;
import com.beauver.printbridge.modelinfo.analysis.ModelTooDetailedException;
import com.beauver.printbridge.modelinfo.analysis.PreviewBed;
import com.beauver.printbridge.modelinfo.analysis.PreviewMeshReader;
import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.printrequest.PrintRequestService;
import com.beauver.printbridge.printrequest.dto.StoredFile;
import com.beauver.printbridge.printrequest.dto.UploadedModel;
import com.beauver.printbridge.settings.BuildPlate;
import com.beauver.printbridge.settings.PrinterSettings;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.slicing.SliceResult;
import com.beauver.printbridge.slicing.SliceStatus;
import com.beauver.printbridge.slicing.SlicingService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 3D preview of model files (#13): meshes of uploaded files and of files that are about to be
 * uploaded, and the plate images Bambu files carry. Who may see a file is decided by
 * {@link PrintRequestService#file}, like for the download.
 */
@ApplicationScoped
public class PreviewService {

    static final String TOO_DETAILED = "This model is too detailed for a preview";
    static final String NOT_AVAILABLE = "No preview available for this file";

    @Inject
    PrintRequestService requests;

    @Inject
    SettingsService settings;

    @Inject
    SlicingService slicing;

    @Inject
    PreviewMeshReader reader;

    @Inject
    MeshEncoder encoder;

    /** About 36 MB of mesh data at the default; more makes browsers struggle. */
    @ConfigProperty(name = "printbridge.preview.max-triangles", defaultValue = "2000000")
    long maxTriangles;

    /** The encoded mesh of a request file, on the bed of the printer its fit check uses. */
    public byte[] ofFile(long requestId, long fileId, Caller caller) {
        StoredFile file = requests.file(requestId, fileId, caller);
        String slicedFor = slicing.find(fileId)
                .filter(result -> result.status == SliceStatus.DONE)
                .map(result -> result.printer)
                .orElse(null);
        PreviewBed bed = settings.slicing().fitPrinter(slicedFor).map(PreviewService::bed).orElse(null);
        return encode(file.path(), typeOf(file), bed);
    }

    /** The encoded mesh of a file that isn't uploaded yet, on the default printer's bed. Nothing is stored. */
    public byte[] ofUpload(UploadedModel upload) {
        ModelFileType type = requests.checkUpload(upload);
        PreviewBed bed = Optional.ofNullable(settings.slicing().defaultPrinter()).map(PreviewService::bed).orElse(null);
        return encode(upload.path(), type, bed);
    }

    /** The PNG Bambu Studio / OrcaSlicer saved of a plate. */
    public byte[] thumbnail(long requestId, long fileId, int plate, Caller caller) {
        StoredFile file = requests.file(requestId, fileId, caller);
        try {
            return reader.thumbnail(file.path(), typeOf(file), plate)
                    .orElseThrow(() -> new ResourceNotFoundException("This plate has no image"));
        } catch (ModelAnalysisException e) {
            throw new ResourceNotFoundException("This plate has no image");
        }
    }

    private byte[] encode(Path path, ModelFileType type, PreviewBed bed) {
        try {
            return encoder.encode(reader.read(path, type, bed, maxTriangles));
        } catch (ModelTooDetailedException e) {
            throw new UnprocessableContentException(TOO_DETAILED);
        } catch (ModelAnalysisException e) {
            Log.infof("No preview for %s: %s", path.getFileName(), e.getMessage());
            throw new UnprocessableContentException(NOT_AVAILABLE);
        }
    }

    /** Stored files keep the type's extension (see {@code PrintRequestService#create}). */
    private static ModelFileType typeOf(StoredFile file) {
        String name = file.path().getFileName().toString();
        return ModelFileType.fromExtension(name.substring(name.lastIndexOf('.') + 1))
                .orElseThrow(() -> new UnprocessableContentException(NOT_AVAILABLE));
    }

    private static PreviewBed bed(PrinterSettings printer) {
        BuildPlate plate = printer.buildPlate();
        return new PreviewBed(plate.x(), plate.y(), plate.z());
    }
}
