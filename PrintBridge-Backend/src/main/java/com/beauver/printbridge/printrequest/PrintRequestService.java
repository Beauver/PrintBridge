package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.transaction.AfterTransaction;
import com.beauver.printbridge.common.transaction.Transactions;
import com.beauver.printbridge.filamentchoice.ChoiceItem;
import com.beauver.printbridge.filamentchoice.FilamentChoiceService;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceRequest;
import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelfile.ModelFileValidator;
import com.beauver.printbridge.modelinfo.ModelInfoService;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.printrequest.file.FileBundle;
import com.beauver.printbridge.printrequest.file.ItemStatus;
import com.beauver.printbridge.printrequest.file.NewLink;
import com.beauver.printbridge.printrequest.file.RequestFile;
import com.beauver.printbridge.printrequest.file.RequestFileKind;
import com.beauver.printbridge.printrequest.file.StoredFile;
import com.beauver.printbridge.printrequest.file.UploadedModel;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.settings.SpoolmanSettings;
import com.beauver.printbridge.settings.UploadConfig;
import com.beauver.printbridge.settings.UploadLimits;
import com.beauver.printbridge.slicing.CustomResliceService;
import com.beauver.printbridge.slicing.SlicingService;
import com.beauver.printbridge.slicing.dto.ResliceOptionsDto;
import com.beauver.printbridge.storage.FileStorage;
import com.beauver.printbridge.user.UserDeleted;
import com.beauver.printbridge.user.UserRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Inject
    Transactions transactions;

    @Inject
    CustomResliceService customReslice;

    @Inject
    FilamentChoiceService filamentChoices;

    public UploadConfigDto uploadConfig() {
        UploadLimits limits = settings.getUploads();
        return new UploadConfigDto(limits.maxFileSizeBytes(), limits.maxFiles(),
                allowedTypes().stream().map(ModelFileType::extension).toList());
    }

    /** The caller's own requests, newest first. */
    public List<PrintRequestSummaryDto> listOwn(long ownerId) {
        return summaries(requests.listOwnNewestFirst(ownerId));
    }

    /** Admin overview: everyone's requests in the given statuses, optionally searched, newest first. */
    public List<PrintRequestSummaryDto> listAll(Set<RequestStatus> statuses, String search) {
        return summaries(requests.searchNewestFirst(statuses, search));
    }

    /** List rows with the progress of their items (#61), loaded in one query. */
    private List<PrintRequestSummaryDto> summaries(List<PrintRequest> found) {
        Map<Long, ItemProgress> progress = requests.itemProgress(found.stream().map(r -> r.id).toList());
        return found.stream()
                .map(r -> mapper.toSummary(r, progress.getOrDefault(r.id, ItemProgress.NONE)))
                .toList();
    }

    public PrintRequestDto get(long id, Caller caller) {
        return describe(findVisible(id, caller), caller.admin());
    }

    /**
     * Validates the first entry, a model file or a link (#59), and creates the request with it in
     * status SUBMITTED. The other files and links of the request follow one by one through
     * {@link #addFile} and {@link #addLink}.
     * <p>
     * Not one transaction (#39): the upload is checked first, the request and its file are
     * inserted in a short transaction, and the model is analysed after that has committed. A
     * link has nothing to copy or analyse.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public PrintRequestDto create(long ownerId, NewPrintRequest input) {
        if (input.link() != null) {
            NewLink link = checkLink(input.link());
            PrintRequest request = transactions.call(() -> insertRequestWithLink(ownerId, input, link));
            return transactions.call(() -> describe(request, false));
        }
        ModelFileType type = checkUpload(input.file());
        PrintRequest request = transactions.call(() -> insertRequest(ownerId, input, type));
        analyseAndQueue(newestFile(request));
        // A fresh upload has no slicing errors yet, so nothing admin-only to show.
        return transactions.call(() -> describe(request, false));
    }

    /**
     * The request row and its first file. The file is copied inside this transaction, unlike in
     * {@link #addFile}: its directory is named after the request id, which only exists once the
     * row is inserted. Nobody else can see or lock the new request yet, so nothing waits on it.
     */
    private PrintRequest insertRequest(long ownerId, NewPrintRequest input, ModelFileType type) {
        PrintRequest request = insertRequestRow(ownerId, input);
        String storagePath = storage.store(input.file().path(), directoryOf(request.id), type.extension());
        afterTransaction.onRollback(() -> storage.deleteFile(storagePath));
        attach(request, input.file(), type, storagePath, input.quantity());
        return request;
    }

    /** The request row and its first entry, a link (#59). */
    private PrintRequest insertRequestWithLink(long ownerId, NewPrintRequest input, NewLink link) {
        PrintRequest request = insertRequestRow(ownerId, input);
        attachLink(request, link, input.quantity());
        return request;
    }

    private PrintRequest insertRequestRow(long ownerId, NewPrintRequest input) {
        PrintRequest request = new PrintRequest();
        request.owner = users.findByIdOptional(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        request.name = input.name().trim();
        request.description = blankToNull(input.description());
        request.status = RequestStatus.SUBMITTED;
        requests.persist(request);
        return request;
    }

    /**
     * Adds a model file to a request (#23). The owner may while it is SUBMITTED, the admin
     * always. The file counts against the file limit and goes through the same checks as the
     * first one.
     * <p>
     * Not one transaction (#39), so the request's row lock is only held for the insert:
     * <ol>
     *   <li>a first look at the request, so an upload that will be refused isn't copied;</li>
     *   <li>the upload is checked and copied into storage;</li>
     *   <li>a short transaction locks the request, checks again and inserts the file row (the
     *       copy is removed if this fails);</li>
     *   <li>the model is analysed and queued for slicing.</li>
     * </ol>
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public PrintRequestDto addFile(long requestId, UploadedModel upload, int quantity, Caller caller) {
        transactions.run(() -> checkMayAddFile(findVisible(requestId, caller), caller));
        ModelFileType type = checkUpload(upload);
        String storagePath = storage.store(upload.path(), directoryOf(requestId), type.extension());
        PrintRequest request;
        try {
            request = transactions.call(() -> insertFile(requestId, upload, type, storagePath, quantity, caller));
        } catch (RuntimeException e) {
            storage.deleteFile(storagePath);
            throw e;
        }
        analyseAndQueue(newestFile(request));
        return transactions.call(() -> describe(request, caller.admin()));
    }

    /** Locks the request, so two uploads at once can't both pass the file limit. */
    private PrintRequest insertFile(long requestId, UploadedModel upload, ModelFileType type, String storagePath,
                                    int quantity, Caller caller) {
        PrintRequest request = requests.findLockedWithDetails(requestId)
                .filter(r -> isVisible(r, caller))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        checkMayAddFile(request, caller);
        attach(request, upload, type, storagePath, quantity);
        return request;
    }

    /**
     * Adds a link to a request (#59), under the same rules as {@link #addFile}: the owner while it
     * is SUBMITTED, the admin always, and it counts against the file limit. The server never
     * fetches the link. The request is locked, so two additions at once can't both pass the limit.
     */
    @Transactional
    public PrintRequestDto addLink(long requestId, NewLink input, int quantity, Caller caller) {
        NewLink link = checkLink(input);
        PrintRequest request = requests.findLockedWithDetails(requestId)
                .filter(r -> isVisible(r, caller))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        checkMayAddFile(request, caller);
        attachLink(request, link, quantity);
        return describe(request, caller.admin());
    }

    private void checkMayAddFile(PrintRequest request, Caller caller) {
        checkMayChangeFiles(request, caller);
        int maxFiles = settings.getUploads().maxFiles();
        if (request.files.size() >= maxFiles) {
            throw new InvalidInputException("A request can hold at most " + maxFiles + " files and links");
        }
    }

    private static RequestFile newestFile(PrintRequest request) {
        return request.files.getLast();
    }

    /**
     * What happens to a file once its row is committed: the model is analysed and, when server
     * slicing is on, queued. Neither may fail the upload: the file is stored, and a file without
     * model details is picked up again by the startup backfill.
     */
    private void analyseAndQueue(RequestFile file) {
        long fileId = file.id;
        try {
            modelInfo.analyze(fileId);
            transactions.run(() -> slicing.enqueueIfEnabled(fileId));
        } catch (RuntimeException e) {
            // For example the file was removed again while it was being analysed.
            Log.warnf(e, "Could not analyse or queue file %d after its upload", fileId);
        }
    }

    /**
     * Removes one file or link of a request (#23, #59): the owner while it is SUBMITTED, the admin
     * always. The last one can't go; that is deleting the request. A file leaves the disk after
     * the commit; its model details and slicing result go with the row.
     */
    @Transactional
    public PrintRequestDto removeFile(long requestId, long fileId, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        checkMayChangeFiles(request, caller);
        RequestFile file = fileOf(request, fileId);
        if (request.files.size() == 1) {
            throw new ConflictException("A request needs at least one file or link; delete the request instead");
        }
        request.files.remove(file);
        requests.flush();
        String storagePath = file.storagePath;
        if (storagePath != null) {
            afterTransaction.onCommit(() -> storage.deleteFile(storagePath));
        }
        return describe(request, caller.admin());
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
        return describe(request, caller.admin());
    }

    /**
     * Chooses a Spoolman filament for one colour of a plate of a file, or for a whole file without
     * colours or a link (#11). Allowed while colour selection is on, like the quantity: the owner
     * while the request is SUBMITTED, the admin until it is final.
     */
    @Transactional
    public PrintRequestDto chooseFilament(long requestId, long fileId, FilamentChoiceRequest choice, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        checkMayChooseFilaments(request, caller);
        RequestFile file = fileOf(request, fileId);
        ModelInfoDto info = null;
        if (!file.isLink()) {
            info = modelInfo.describe(List.of(file.id), caller.admin()).get(file.id);
        }
        filamentChoices.choose(file.id, info, choice);
        return describe(request, caller.admin());
    }

    /** Removes a chosen filament (#11): the colour goes back to the file's own. Same rules as choosing. */
    @Transactional
    public PrintRequestDto removeFilamentChoice(long requestId, long fileId, long choiceId, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        checkMayChooseFilaments(request, caller);
        filamentChoices.remove(fileOf(request, fileId).id, choiceId);
        return describe(request, caller.admin());
    }

    private void checkMayChooseFilaments(PrintRequest request, Caller caller) {
        if (!settings.getSpoolman().colorSelection()) {
            throw new ConflictException("Choosing filaments from Spoolman is switched off");
        }
        if (!mayChooseFilaments(request, caller.admin())) {
            if (caller.admin()) {
                throw new ConflictException("This request is " + request.status + " and can no longer be changed");
            }
            throw new ConflictException("Filaments can only be chosen while the request is submitted");
        }
    }

    /** Like the quantity: the owner while SUBMITTED, the admin until the request is final. */
    private static boolean mayChooseFilaments(PrintRequest request, boolean admin) {
        if (admin) {
            return !request.status.isFinal();
        }
        return request.status == RequestStatus.SUBMITTED;
    }

    /**
     * Every file of a request for one ZIP download, under its original name. Duplicate names
     * get a number ({@code part (2).stl}). Links (#59) are listed in a text file.
     */
    public FileBundle files(long requestId, Caller caller) {
        PrintRequest request = findVisible(requestId, caller);
        List<RequestFile> stored = request.files.stream().filter(file -> !file.isLink()).toList();
        List<RequestFile> links = request.files.stream().filter(RequestFile::isLink).toList();
        List<String> names = uniqueNames(stored.stream().map(file -> file.originalFilename).toList());
        List<StoredFile> files = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            RequestFile file = stored.get(i);
            files.add(new StoredFile(pathOnDisk(file), names.get(i), file.fileType.contentType(), file.sizeBytes));
        }
        String linksText = null;
        if (!links.isEmpty()) {
            linksText = linksText(links);
        }
        // The name is only used for the download, but keep slashes out of it anyway.
        return new FileBundle(request.name.replaceAll("[\\\\/\\p{Cntrl}]", "_") + ".zip", files, linksText);
    }

    /** One block per link: its name (or host) with the quantity, then the URL. */
    static String linksText(List<RequestFile> links) {
        StringBuilder text = new StringBuilder("Links in this request; download these models yourself.\n");
        for (RequestFile link : links) {
            text.append('\n')
                    .append(linkLabel(link.linkName, link.url))
                    .append(" (quantity: ").append(link.quantity).append(")\n")
                    .append(link.url).append('\n');
        }
        return text.toString();
    }

    /** The link's name, or else the URL's host, as the frontend shows it. */
    static String linkLabel(String name, String url) {
        if (name != null) {
            return name;
        }
        String host = URI.create(url).getHost();
        if (host == null) {
            return url;
        }
        return host;
    }

    /**
     * The request as the API returns it: its files with what is known about each model, and the
     * totals over them.
     *
     * @param admin whether the caller is the admin (sees slicing errors)
     */
    private PrintRequestDto describe(PrintRequest request, boolean admin) {
        // Links have no model details (#59).
        List<Long> fileIds = request.files.stream().filter(file -> !file.isLink()).map(file -> file.id).toList();
        Map<Long, ModelInfoDto> infos = modelInfo.describe(fileIds, admin);
        List<ChoiceItem> items = request.files.stream()
                .map(file -> new ChoiceItem(file.id, file.quantity, file.isDeclined()))
                .toList();
        List<RequestFileDto> files = mapper.toFileDtos(request.files, infos, filamentChoices.describe(items, infos));
        SpoolmanSettings spoolman = settings.getSpoolman();
        boolean filamentSelection = spoolman.colorSelection() && mayChooseFilaments(request, admin);
        return mapper.toDto(request, files, RequestTotals.of(files), filamentSelection, spoolman.notEnoughMessage());
    }

    /** Adds the row of an upload that passed {@link #checkUpload} and is already in storage. */
    private void attach(PrintRequest request, UploadedModel upload, ModelFileType type, String storagePath,
                        int quantity) {
        RequestFile file = new RequestFile();
        file.originalFilename = sanitizeFilename(upload.filename());
        file.fileType = type;
        file.sizeBytes = upload.size();
        file.quantity = quantity;
        file.storagePath = storagePath;
        request.addFile(file);
        requests.flush();
    }

    /** Adds the row of a link that passed {@link #checkLink}. */
    private void attachLink(PrintRequest request, NewLink link, int quantity) {
        RequestFile file = new RequestFile();
        file.kind = RequestFileKind.LINK;
        file.url = link.url();
        file.linkName = link.name();
        file.quantity = quantity;
        request.addFile(file);
        requests.flush();
    }

    /**
     * The checks every link goes through (#59): an absolute http(s) URL with a host, at most 2048
     * characters, and a name of at most 100 characters. Returns it trimmed, with a blank name as
     * {@code null}. The form checks most of this too; this also covers callers without one.
     */
    static NewLink checkLink(NewLink link) {
        String url = "";
        if (link.url() != null) {
            url = link.url().trim();
        }
        if (url.isEmpty()) {
            throw new InvalidInputException("A link is required");
        }
        if (url.length() > LinkFields.MAX_URL_LENGTH) {
            throw new InvalidInputException("The link must be at most " + LinkFields.MAX_URL_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidInputException("The link is not a valid web address");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidInputException("The link must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidInputException("The link is not a valid web address");
        }
        String name = null;
        if (link.name() != null) {
            name = blankToNull(link.name().replaceAll("\\p{Cntrl}", ""));
        }
        if (name != null && name.length() > LinkFields.MAX_NAME_LENGTH) {
            throw new InvalidInputException("The link name must be at most " + LinkFields.MAX_NAME_LENGTH + " characters");
        }
        return new NewLink(url, name);
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
        RequestStatus from = request.status;
        request.status = target;
        if (target == RequestStatus.DECLINED) {
            request.declineReason = blankToNull(declineReason);
        }
        moveItemsAlong(request, from, target);
        requests.flush();
        return describe(request, true);
    }

    /**
     * The request moves its items along (#61): accepting it accepts the pending ones, declining
     * it declines all but the completed ones, completing it completes all but the declined ones.
     * Other moves (to PRINTING, back to ACCEPTED, IN_REVIEW) leave the items alone.
     */
    static void moveItemsAlong(PrintRequest request, RequestStatus from, RequestStatus to) {
        // Back from PRINTING to ACCEPTED is a failed print, not an acceptance.
        boolean accepting = to == RequestStatus.ACCEPTED && from != RequestStatus.PRINTING;
        for (RequestFile item : request.files) {
            if (accepting && item.status == ItemStatus.PENDING) {
                setItemStatus(item, ItemStatus.ACCEPTED, null);
            } else if (to == RequestStatus.DECLINED && item.status != ItemStatus.COMPLETED) {
                setItemStatus(item, ItemStatus.DECLINED, null);
            } else if (to == RequestStatus.COMPLETED && item.status != ItemStatus.DECLINED) {
                setItemStatus(item, ItemStatus.COMPLETED, null);
            }
        }
    }

    /**
     * Moves one file or link of a request along its own flow (#61; admin). Refused on a final
     * request and for a move outside {@link ItemStatus#nextStatuses()}. Declining keeps the
     * optional reason; any other status drops it.
     */
    @Transactional
    public PrintRequestDto changeItemStatus(long id, long fileId, ItemStatus target, String declineReason) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        RequestFile item = fileOf(request, fileId);
        if (request.status.isFinal()) {
            throw new ConflictException("This request is " + request.status + " and its items can no longer change");
        }
        if (!item.status.canMoveTo(target)) {
            throw new ConflictException("Cannot change the item's status from " + item.status + " to " + target);
        }
        String reason = null;
        if (target == ItemStatus.DECLINED) {
            reason = blankToNull(declineReason);
        }
        setItemStatus(item, target, reason);
        requests.flush();
        return describe(request, true);
    }

    private static void setItemStatus(RequestFile item, ItemStatus status, String declineReason) {
        item.status = status;
        item.declineReason = declineReason;
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
        return describe(request, true);
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
        return describe(request, true);
    }

    /**
     * Queues one file of a request for server slicing with the admin's choice of printer, nozzle
     * and profiles (#21); the other files are left alone. The choice is checked by
     * {@link CustomResliceService}.
     */
    @Transactional
    public PrintRequestDto resliceWith(long id, long fileId, CustomResliceRequest choice) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        RequestFile file = sliceableFile(request, fileId);
        customReslice.reslice(file.id, choice.printerId(), choice.nozzle(), choice.process(), choice.filament());
        return describe(request, true);
    }

    /** What the "Re-slice with…" dialog of one file offers (#21; admin). */
    @Transactional
    public ResliceOptionsDto resliceOptions(long id, long fileId) {
        PrintRequest request = requests.findWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return customReslice.options(sliceableFile(request, fileId).id);
    }

    private static RequestFile sliceableFile(PrintRequest request, long fileId) {
        RequestFile file = fileOf(request, fileId);
        if (file.isLink()) {
            throw new ConflictException("Links can't be sliced");
        }
        return file;
    }

    /** The file of a request, for download by the owner or the admin. A link (#59) has no file. */
    public StoredFile file(long requestId, long fileId, Caller caller) {
        RequestFile file = fileOf(findVisible(requestId, caller), fileId);
        if (file.isLink()) {
            throw new ResourceNotFoundException(FILE_NOT_FOUND);
        }
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
     * Schedules removal of all files of a user's requests when the user is deleted; the rows
     * themselves go via ON DELETE CASCADE. Runs inside the deleting transaction.
     */
    void onUserDeleted(@Observes UserDeleted deleted) {
        requests.idsOfOwner(deleted.userId()).forEach(this::deleteFilesAfterCommit);
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
        UploadLimits limits = settings.getUploads();
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
