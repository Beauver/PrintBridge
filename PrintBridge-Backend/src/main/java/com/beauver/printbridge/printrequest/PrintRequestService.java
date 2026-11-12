package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.transaction.AfterTransaction;
import com.beauver.printbridge.modelinfo.ModelInfoService;
import com.beauver.printbridge.slicing.SlicingService;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.storage.FileStorage;
import com.beauver.printbridge.user.UserRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Inject
    ModelInfoService modelInfo;

    @Inject
    SlicingService slicing;

    public UploadConfigDto uploadConfig() {
        return new UploadConfigDto(maxSizeBytes(), allowedTypes().stream().map(ModelFileType::extension).toList());
    }

    /** The caller's own requests, newest first. */
    public List<PrintRequestSummaryDto> listOwn(long ownerId) {
        return requests.listOwnNewestFirst(ownerId).stream().map(mapper::toSummary).toList();
    }

    /** Admin overview: everyone's requests in the given statuses, optionally searched, newest first. */
    public List<PrintRequestSummaryDto> listAll(Set<RequestStatus> statuses, String search) {
        return requests.searchNewestFirst(statuses, search).stream().map(mapper::toSummary).toList();
    }

    public PrintRequestDto get(long id, Caller caller) {
        return mapper.toDto(findVisible(id, caller), caller.admin());
    }

    /**
     * Validates the upload, stores the file and creates the request in status SUBMITTED. If the
     * transaction rolls back, the stored file is removed again. The file is analysed right away
     * (an analysis that fails is recorded but doesn't stop the upload) and queued for server
     * slicing when that is on.
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
        modelInfo.analyze(file);
        slicing.enqueueIfEnabled(file);
        // A fresh upload has no slicing errors yet, so nothing admin-only to show.
        return mapper.toDto(request, false);
    }

    /**
     * Moves a request along the status flow (admin only). Final requests can't change, a move
     * outside {@link RequestStatus#nextStatuses()} is refused, and completing needs a price.
     */
    @Transactional
    public PrintRequestDto changeStatus(long id, RequestStatus target, String declineReason) {
        PrintRequest request = findForReview(id);
        if (!request.status.canMoveTo(target)) {
            throw new ConflictException("Cannot change status from " + request.status + " to " + target);
        }
        if (target == RequestStatus.COMPLETED && request.finalPrice == null) {
            throw new ConflictException("Set a final price before completing the request");
        }
        request.status = target;
        if (target == RequestStatus.DECLINED) {
            request.declineReason = blankToNull(declineReason);
        }
        requests.flush();
        return mapper.toDto(request, true);
    }

    /**
     * Sets or clears ({@code null}) the final price of a non-final request (admin only). A paid
     * request keeps its price until it is marked unpaid, so the paid amount stays correct.
     */
    @Transactional
    public PrintRequestDto changePrice(long id, BigDecimal finalPrice) {
        PrintRequest request = findForReview(id);
        if (request.paidAt != null) {
            throw new ConflictException("Mark the request as unpaid before changing the price");
        }
        request.finalPrice = finalPrice;
        requests.flush();
        return mapper.toDto(request, true);
    }

    /**
     * Marks an ACCEPTED or COMPLETED request with a price as paid, or undoes that (admin only).
     * Marking an already paid request again keeps the original moment.
     */
    @Transactional
    public PrintRequestDto changePaid(long id, boolean paid) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!request.status.allowsPayment()) {
            throw new ConflictException("Only accepted or completed requests can be marked as paid");
        }
        if (!paid) {
            request.paidAt = null;
        } else if (request.finalPrice == null) {
            throw new ConflictException("Set a final price before marking the request as paid");
        } else if (request.paidAt == null) {
            request.paidAt = Instant.now();
        }
        requests.flush();
        return mapper.toDto(request, true);
    }

    /**
     * Queues the request's files for server slicing again (admin), e.g. after changing the
     * profiles. Refused while slicing is off or a file is already queued or running.
     */
    @Transactional
    public PrintRequestDto reslice(long id) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        slicing.reslice(request.files);
        return mapper.toDto(request, true);
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

    /** A request the admin may still change: it must exist and not be final. */
    private PrintRequest findForReview(long id) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (request.status.isFinal()) {
            throw new ConflictException("This request is " + request.status + " and can no longer be changed");
        }
        return request;
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
