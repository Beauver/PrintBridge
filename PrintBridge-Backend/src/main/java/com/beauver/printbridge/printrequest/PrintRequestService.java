package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.transaction.AfterTransaction;
import com.beauver.printbridge.modelinfo.ModelInfoService;
import com.beauver.printbridge.slicing.SlicingService;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.settings.UploadLimits;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    static final String FILE_NOT_FOUND = "File not found";
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

    @Inject
    SettingsService settings;

    public UploadConfigDto uploadConfig() {
        UploadLimits limits = settings.uploads();
        return new UploadConfigDto(limits.maxFileSizeBytes(), limits.maxFiles(),
                allowedTypes().stream().map(ModelFileType::extension).toList());
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
     * Validates the upload and creates the request with it in status SUBMITTED. The other files
     * of a multi-file request follow one by one through {@link #addFile}.
     */
    @Transactional
    public PrintRequestDto create(long ownerId, NewPrintRequest input) {
        checkUpload(input.file());

        PrintRequest request = new PrintRequest();
        request.owner = users.findByIdOptional(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        request.name = input.name().trim();
        request.description = blankToNull(input.description());
        request.status = RequestStatus.SUBMITTED;
        requests.persist(request);
        attach(request, input.file(), input.quantity());
        // A fresh upload has no slicing errors yet, so nothing admin-only to show.
        return mapper.toDto(request, false);
    }

    /**
     * Adds a model file to a request (#23). The owner may while it is SUBMITTED, the admin
     * always. The file counts against the file limit and goes through the same checks as the
     * first one.
     */
    @Transactional
    public PrintRequestDto addFile(long requestId, UploadedModel upload, int quantity, Caller caller) {
        PrintRequest request = requests.findLockedWithDetails(requestId)
                .filter(r -> isVisible(r, caller))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        checkMayChangeFiles(request, caller);
        int maxFiles = settings.uploads().maxFiles();
        if (request.files.size() >= maxFiles) {
            throw new InvalidInputException("A request can hold at most " + maxFiles + " files");
        }
        checkUpload(upload);
        attach(request, upload, quantity);
        return mapper.toDto(request, caller.admin());
    }

    /**
     * Removes one file of a request (#23): the owner while it is SUBMITTED, the admin always. The
     * last file can't go; that is deleting the request. The file leaves the disk after the commit;
     * its model details and slicing result go with the row.
     */
    @Transactional
    public PrintRequestDto removeFile(long requestId, long fileId, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        checkMayChangeFiles(request, caller);
        RequestFile file = fileOf(request, fileId);
        if (request.files.size() == 1) {
            throw new ConflictException("A request needs at least one file; delete the request instead");
        }
        request.files.remove(file);
        requests.flush();
        String storagePath = file.storagePath;
        afterTransaction.onCommit(() -> storage.deleteFile(storagePath));
        return mapper.toDto(request, caller.admin());
    }

    /**
     * Changes how many copies of a file to print (#23): the owner while the request is SUBMITTED,
     * the admin until it is final.
     */
    @Transactional
    public PrintRequestDto changeQuantity(long requestId, long fileId, int quantity, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        if (caller.admin()) {
            if (request.status.isFinal()) {
                throw new ConflictException("This request is " + request.status + " and can no longer be changed");
            }
        } else if (request.status != RequestStatus.SUBMITTED) {
            throw new ConflictException("Quantities can only be changed while the request is submitted");
        }
        fileOf(request, fileId).quantity = quantity;
        requests.flush();
        return mapper.toDto(request, caller.admin());
    }

    /**
     * Every file of a request for one ZIP download, under its original name. Duplicate names
     * get a number ({@code part (2).stl}).
     */
    public FileBundle files(long requestId, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        List<String> names = uniqueNames(request.files.stream().map(file -> file.originalFilename).toList());
        List<StoredFile> files = new ArrayList<>();
        for (int i = 0; i < request.files.size(); i++) {
            RequestFile file = request.files.get(i);
            files.add(new StoredFile(pathOnDisk(file), names.get(i), file.fileType.contentType(), file.sizeBytes));
        }
        // The name is only used for the download, but keep slashes out of it anyway.
        return new FileBundle(request.name.replaceAll("[\\\\/\\p{Cntrl}]", "_") + ".zip", files);
    }

    /**
     * Stores an upload that passed {@link #checkUpload} and adds it to the request. If the
     * transaction rolls back, the stored file is removed again. The file is analysed right away
     * (an analysis that fails is recorded but doesn't stop the upload) and queued for server
     * slicing when that is on.
     */
    private void attach(PrintRequest request, UploadedModel upload, int quantity) {
        ModelFileType type = typeOf(sanitizeFilename(upload.filename()));
        RequestFile file = new RequestFile();
        file.originalFilename = sanitizeFilename(upload.filename());
        file.fileType = type;
        file.sizeBytes = upload.size();
        file.quantity = quantity;
        file.storagePath = storage.store(upload.path(), directoryOf(request.id), type.extension());
        String storagePath = file.storagePath;
        afterTransaction.onRollback(() -> storage.deleteFile(storagePath));
        request.addFile(file);
        requests.flush();
        modelInfo.analyze(file);
        slicing.enqueueIfEnabled(file);
    }

    /** Owners change files only while the request is SUBMITTED; the admin always may. */
    private static void checkMayChangeFiles(PrintRequest request, Caller caller) {
        if (!caller.admin() && request.status != RequestStatus.SUBMITTED) {
            throw new ConflictException("Files can only be changed while the request is submitted");
        }
    }

    private static RequestFile fileOf(PrintRequest request, long fileId) {
        return request.files.stream()
                .filter(f -> f.id == fileId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(FILE_NOT_FOUND));
    }

    /**
     * Makes names unique, ignoring case (ZIP tools on Windows and macOS would overwrite one with
     * the other): the second {@code part.stl} becomes {@code part (2).stl}.
     */
    static List<String> uniqueNames(List<String> names) {
        Set<String> taken = new HashSet<>();
        List<String> unique = new ArrayList<>();
        for (String name : names) {
            String candidate = name;
            int dot = name.lastIndexOf('.');
            String base = name;
            String extension = "";
            if (dot > 0) {
                base = name.substring(0, dot);
                extension = name.substring(dot);
            }
            int number = 2;
            while (!taken.add(candidate.toLowerCase(Locale.ROOT))) {
                candidate = base + " (" + number + ")" + extension;
                number++;
            }
            unique.add(candidate);
        }
        return unique;
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
     * Marks an ACCEPTED, PRINTING or COMPLETED request with a price as paid, or undoes that (admin only).
     * Marking an already paid request again keeps the original moment.
     */
    @Transactional
    public PrintRequestDto changePaid(long id, boolean paid) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!request.status.allowsPayment()) {
            throw new ConflictException("Only accepted, printing or completed requests can be marked as paid");
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
     * Queues one file of a request for server slicing again (admin), e.g. after changing the
     * profiles. The other files are left alone. Refused while slicing is off or the file is
     * already queued or running.
     */
    @Transactional
    public PrintRequestDto reslice(long id, long fileId) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        slicing.reslice(List.of(fileOf(request, fileId)));
        return mapper.toDto(request, true);
    }

    /** The file of a request, for download by the owner or the admin. */
    public StoredFile file(long requestId, long fileId, Caller caller) {
        RequestFile file = fileOf(findVisible(requestId, caller), fileId);
        return new StoredFile(pathOnDisk(file), file.originalFilename, file.fileType.contentType(), file.sizeBytes);
    }

    private Path pathOnDisk(RequestFile file) {
        Path path = storage.resolve(file.storagePath);
        if (!Files.isRegularFile(path)) {
            Log.warnf("File %s of request %d is missing on disk", file.storagePath, file.request.id);
            throw new ResourceNotFoundException(FILE_NOT_FOUND);
        }
        return path;
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
                .filter(r -> isVisible(r, caller))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private static boolean isVisible(PrintRequest request, Caller caller) {
        return caller.admin() || request.owner.id == caller.id();
    }

    /**
     * The checks every model upload goes through (also the preview before uploading): an allowed
     * extension, not empty, within the size limit, and really an STL / 3MF. Returns the type.
     */
    public ModelFileType checkUpload(UploadedModel upload) {
        ModelFileType type = typeOf(sanitizeFilename(upload.filename()));
        if (upload.size() <= 0) {
            throw new InvalidInputException("The file is empty");
        }
        UploadLimits limits = settings.uploads();
        if (upload.size() > limits.maxFileSizeBytes()) {
            throw new InvalidInputException("The file is larger than " + limits.maxFileSizeMb() + " MB");
        }
        if (!validator.isValid(upload.path(), type)) {
            throw new InvalidInputException("The file is not a valid " + type.extension().toUpperCase() + " file");
        }
        return type;
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
