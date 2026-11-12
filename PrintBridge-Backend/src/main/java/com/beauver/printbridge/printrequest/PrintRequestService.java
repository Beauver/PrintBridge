package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.tx.AfterTransaction;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.storage.FileStorage;
import com.beauver.printbridge.user.UserRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Print requests and their model files. Users only ever see their own requests; the admin sees
 * all of them. Requests the caller may not see are reported as not found, so their existence
 * does not leak.
 */
@ApplicationScoped
public class PrintRequestService {

    static final String NOT_FOUND = "Print request not found";
    static final long BYTES_PER_MB = 1024L * 1024L;
    private static final int MAX_FILENAME_LENGTH = 255;

    @Inject
    PrintRequestRepository requests;

    @Inject
    UserRepository users;

    @Inject
    PrintRequestMapper mapper;

    @Inject
    ModelFileValidator validator;

    @Inject
    FileStorage storage;

    @Inject
    AfterTransaction afterTransaction;

    @Inject
    UploadConfig uploadConfig;

    public UploadConfigDto uploadConfig() {
        return new UploadConfigDto(maxSizeBytes(), allowedTypes().stream().map(ModelFileType::extension).toList());
    }

    /** The caller's own requests, newest first. */
    public List<PrintRequestSummaryDto> listOwn(long ownerId) {
        return requests.listNewestFirst(ownerId).stream().map(mapper::toSummary).toList();
    }

    // TODO: #5 listAll(filters) for the admin overview, via requests.listNewestFirst(null).

    public PrintRequestDto get(long id, Caller caller) {
        return mapper.toDto(findVisible(id, caller));
    }

    /**
     * Validates the upload, stores the file and creates the request in status SUBMITTED. If the
     * transaction rolls back, the stored file is removed again.
     */
    @Transactional
    public PrintRequestDto create(long ownerId, NewPrintRequest input) {
        UploadedModel upload = input.file();
        String filename = sanitizeFilename(upload.filename());
        ModelFileType type = typeOf(filename);
        if (upload.size() <= 0) {
            throw new InvalidInputException("The file is empty");
        }
        if (upload.size() > maxSizeBytes()) {
            throw new InvalidInputException("The file is larger than " + uploadConfig.maxSizeMb() + " MB");
        }
        if (!validator.isValid(upload.path(), type)) {
            throw new InvalidInputException("The file is not a valid " + type.extension().toUpperCase() + " file");
        }

        PrintRequest request = new PrintRequest();
        request.owner = users.findByIdOptional(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        request.name = input.name().trim();
        request.description = blankToNull(input.description());
        request.status = RequestStatus.SUBMITTED;
        requests.persist(request);

        String directory = directoryOf(request.id);
        RequestFile file = new RequestFile();
        file.originalFilename = filename;
        file.fileType = type;
        file.sizeBytes = upload.size();
        file.storagePath = storage.store(upload.path(), directory, type.extension());
        afterTransaction.onRollback(() -> storage.deleteDirectory(directory));
        request.addFile(file);
        requests.flush();
        return mapper.toDto(request);
    }

    /** The file of a request, for download by the owner or the admin. */
    public StoredFile file(long requestId, long fileId, Caller caller) {
        RequestFile file = findVisible(requestId, caller).files.stream()
                .filter(f -> f.id == fileId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        Path path = storage.resolve(file.storagePath);
        if (!Files.isRegularFile(path)) {
            Log.warnf("File %s of request %d is missing on disk", file.storagePath, requestId);
            throw new ResourceNotFoundException("File not found");
        }
        return new StoredFile(path, file.originalFilename, file.fileType.contentType(), file.sizeBytes);
    }

    /**
     * Deletes a request and its files. Owners may only delete while SUBMITTED; the admin may
     * delete in any status. Files on disk are removed after the transaction commits.
     */
    @Transactional
    public void delete(long id, Caller caller) {
        PrintRequest request = findVisible(id, caller);
        if (!caller.admin() && request.status != RequestStatus.SUBMITTED) {
            throw new ConflictException("Only submitted requests can be deleted");
        }
        requests.delete(request);
        deleteFilesAfterCommit(id);
    }

    /**
     * Schedules removal of all files of a user's requests. Called when the user is deleted; the
     * rows themselves go via ON DELETE CASCADE. Must run inside the deleting transaction.
     */
    public void deleteFilesOfOwner(long ownerId) {
        requests.idsOfOwner(ownerId).forEach(this::deleteFilesAfterCommit);
    }

    private void deleteFilesAfterCommit(long requestId) {
        String directory = directoryOf(requestId);
        afterTransaction.onCommit(() -> storage.deleteDirectory(directory));
    }

    private PrintRequest findVisible(long id, Caller caller) {
        return requests.findWithDetails(id)
                .filter(r -> caller.admin() || r.owner.id == caller.id())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private ModelFileType typeOf(String filename) {
        int dot = filename.lastIndexOf('.');
        Optional<ModelFileType> type = dot < 0 ? Optional.empty() : ModelFileType.fromExtension(filename.substring(dot + 1));
        return type.filter(allowedTypes()::contains)
                .orElseThrow(() -> new InvalidInputException("Only " + allowedList() + " files are allowed"));
    }

    private List<ModelFileType> allowedTypes() {
        return uploadConfig.allowedExtensions().stream()
                .map(ModelFileType::fromExtension)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private String allowedList() {
        return String.join(", ", allowedTypes().stream().map(t -> "." + t.extension()).toList());
    }

    private long maxSizeBytes() {
        return uploadConfig.maxSizeMb() * BYTES_PER_MB;
    }

    private static String directoryOf(long requestId) {
        return String.valueOf(requestId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Keeps only the last path segment of the client's filename and drops control characters.
     * The name is only shown and used for downloads, never as a path on disk.
     */
    static String sanitizeFilename(String filename) {
        String name = filename == null ? "" : filename;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            throw new InvalidInputException("The file has no name");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot);
            name = name.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
        }
        return name;
    }
}
