package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.transaction.AfterTransaction;
import com.beauver.printbridge.common.transaction.RecordingTransactions;
import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelfile.ModelFileValidator;
import com.beauver.printbridge.modelinfo.ModelInfoService;
import com.beauver.printbridge.modelinfo.ModelInfoStatus;
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
import com.beauver.printbridge.settings.UploadConfig;
import com.beauver.printbridge.settings.UploadLimits;
import com.beauver.printbridge.slicing.CustomResliceService;
import com.beauver.printbridge.slicing.SlicingService;
import com.beauver.printbridge.storage.FileStorage;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserDeleted;
import com.beauver.printbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintRequestServiceTest {

    @Mock
    PrintRequestRepository requests;
    @Mock
    UserRepository users;
    @Mock
    ModelFileValidator validator;
    @Mock
    FileStorage storage;
    @Mock
    AfterTransaction afterTransaction;
    @Mock
    ModelInfoService modelInfo;
    @Mock
    SlicingService slicing;
    @Mock
    SettingsService settings;
    @Mock
    CustomResliceService customReslice;

    @TempDir
    Path dir;

    final RecordingTransactions transactions = new RecordingTransactions();
    PrintRequestService service;
    /** Requests of the test; the mocked flush gives their new files an id, as the database would. */
    final List<PrintRequest> tracked = new ArrayList<>();
    List<String> allowed = List.of("stl", "3mf");

    static final Caller BOB = new Caller(2, false);
    static final Caller EVE = new Caller(3, false);
    static final Caller ADMIN = new Caller(1, true);

    @BeforeEach
    void setUp() {
        service = new PrintRequestService();
        service.requests = requests;
        service.users = users;
        service.mapper = new PrintRequestMapper();
        service.modelInfo = modelInfo;
        service.slicing = slicing;
        service.validator = validator;
        service.storage = storage;
        service.afterTransaction = afterTransaction;
        service.transactions = transactions;
        service.settings = settings;
        service.customReslice = customReslice;
        lenient().when(settings.uploads()).thenReturn(new UploadLimits(3, 2, 5));
        lenient().doAnswer(inv -> {
            assignFileIds();
            return null;
        }).when(requests).flush();
        lenient().when(modelInfo.describe(anyCollection(), anyBoolean())).thenAnswer(inv -> {
            Collection<Long> fileIds = inv.getArgument(0);
            Map<Long, ModelInfoDto> infos = new HashMap<>();
            fileIds.forEach(id -> infos.put(id, ModelInfoDto.withStatus(ModelInfoStatus.PENDING, null)));
            return infos;
        });
        service.uploadConfig = new UploadConfig() {
            @Override
            public int maxSizeMb() {
                return 2;
            }

            @Override
            public List<String> allowedExtensions() {
                return allowed;
            }
        };
    }

    private static User user(long id, String username) {
        User u = new User();
        u.id = id;
        u.username = username;
        u.role = Role.USER;
        return u;
    }

    private void assignFileIds() {
        long next = 900;
        for (PrintRequest request : tracked) {
            for (RequestFile file : request.files) {
                if (file.id == null) {
                    file.id = next++;
                }
            }
        }
    }

    private PrintRequest request(long id, User owner, RequestStatus status) {
        PrintRequest r = new PrintRequest();
        r.id = id;
        r.owner = owner;
        r.name = "Request " + id;
        r.status = status;
        r.createdAt = Instant.EPOCH;
        r.updatedAt = Instant.EPOCH;
        RequestFile f = new RequestFile();
        f.id = 100 + id;
        f.originalFilename = "model.stl";
        f.storagePath = id + "/abc.stl";
        f.fileType = ModelFileType.STL;
        f.sizeBytes = 134L;
        r.addFile(f);
        tracked.add(r);
        return r;
    }

    private NewPrintRequest input(String filename, long size) {
        return new NewPrintRequest(" Benchy ", "  ", new UploadedModel(filename, dir.resolve("upload"), size), null, 1);
    }

    private Runnable captured(boolean commit) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        if (commit) {
            verify(afterTransaction).onCommit(captor.capture());
        } else {
            verify(afterTransaction).onRollback(captor.capture());
        }
        return captor.getValue();
    }

    // --- upload config ---

    @Test
    void uploadConfigReportsLimitAndOnlySupportedExtensions() {
        allowed = List.of("STL", "obj", "stl");
        UploadConfigDto config = service.uploadConfig();
        assertEquals(2L * 1024 * 1024, config.maxSizeBytes());
        assertEquals(3, config.maxFiles());
        assertEquals(List.of("stl"), config.allowedExtensions());
    }

    // --- create ---

    @Test
    void createStoresFileAndPersistsSubmittedRequest() {
        User bob = user(2, "bob");
        when(users.findByIdOptional(2L)).thenReturn(Optional.of(bob));
        when(validator.isValid(dir.resolve("upload"), ModelFileType.STL)).thenReturn(true);
        doAnswer(inv -> {
            PrintRequest created = inv.getArgument(0);
            created.id = 9L;
            tracked.add(created);
            return null;
        }).when(requests).persist(any(PrintRequest.class));
        when(storage.store(dir.resolve("upload"), "9", "stl")).thenReturn("9/uuid.stl");

        PrintRequestDto dto = service.create(2, input("C:\\models\\Benchy.STL", 500));

        assertEquals("Benchy", dto.name());
        assertNull(dto.description());
        assertEquals(RequestStatus.SUBMITTED, dto.status());
        assertNull(dto.finalPrice());
        assertEquals("bob", dto.ownerUsername());
        RequestFileDto file = dto.files().getFirst();
        assertEquals("Benchy.STL", file.originalFilename());
        assertEquals(ModelFileType.STL, file.type());
        assertEquals(500, file.sizeBytes());
        verify(requests).flush();
        // Analysed and queued once the request and its file are committed.
        verify(modelInfo).analyze(900L);
        verify(slicing).enqueueIfEnabled(900L);

        captured(false).run();
        verify(storage).deleteFile("9/uuid.stl");
    }

    @Test
    void createAcceptsThreeMfAndKeepsDescription() {
        when(users.findByIdOptional(2L)).thenReturn(Optional.of(user(2, "bob")));
        when(validator.isValid(any(), eq(ModelFileType.THREE_MF))).thenReturn(true);
        doAnswer(inv -> {
            PrintRequest created = inv.getArgument(0);
            created.id = 1L;
            tracked.add(created);
            return null;
        }).when(requests).persist(any(PrintRequest.class));
        when(storage.store(any(), any(), eq("3mf"))).thenReturn("1/uuid.3mf");

        PrintRequestDto dto = service.create(2,
                new NewPrintRequest("Box", " A box ", new UploadedModel("box.3mf", dir.resolve("upload"), 10), null, 4));

        assertEquals("A box", dto.description());
        assertEquals(4, dto.files().getFirst().quantity());
        assertEquals(ModelFileType.THREE_MF, dto.files().getFirst().type());
    }

    @Test
    void createRejectsOtherExtension() {
        var e = assertThrows(InvalidInputException.class, () -> service.create(2, input("model.obj", 10)));
        assertEquals("Only .stl, .3mf files are allowed", e.getMessage());
        verifyNoInteractions(requests, storage);
    }

    @Test
    void createRejectsFileWithoutExtension() {
        assertThrows(InvalidInputException.class, () -> service.create(2, input("model", 10)));
    }

    @Test
    void createRejectsExtensionNotEnabledInConfig() {
        allowed = List.of("stl");
        assertThrows(InvalidInputException.class, () -> service.create(2, input("box.3mf", 10)));
    }

    @Test
    void createRejectsEmptyFile() {
        var e = assertThrows(InvalidInputException.class, () -> service.create(2, input("a.stl", 0)));
        assertEquals("The file is empty", e.getMessage());
    }

    @Test
    void createRejectsTooLargeFile() {
        var e = assertThrows(InvalidInputException.class,
                () -> service.create(2, input("a.stl", 2L * 1024 * 1024 + 1)));
        assertEquals("The file is larger than 2 MB", e.getMessage());
    }

    @Test
    void createRejectsInvalidContent() {
        when(validator.isValid(any(), eq(ModelFileType.STL))).thenReturn(false);
        var e = assertThrows(InvalidInputException.class, () -> service.create(2, input("a.stl", 10)));
        assertEquals("The file is not a valid STL file", e.getMessage());
        verifyNoInteractions(requests, storage);
    }

    @Test
    void createForUnknownOwnerThrows() {
        when(validator.isValid(any(), any())).thenReturn(true);
        when(users.findByIdOptional(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(2, input("a.stl", 10)));
        verifyNoInteractions(storage);
    }

    @Test
    void sanitizeFilenameStripsPathsAndControlCharacters() {
        assertEquals("cube.stl", PrintRequestService.sanitizeFilename("../../etc/cube.stl"));
        assertEquals("cube.stl", PrintRequestService.sanitizeFilename("cu\u0000be.stl\n"));
        assertThrows(InvalidInputException.class, () -> PrintRequestService.sanitizeFilename("dir/"));
        assertThrows(InvalidInputException.class, () -> PrintRequestService.sanitizeFilename(null));
    }

    @Test
    void sanitizeFilenameShortensLongNamesKeepingExtension() {
        String name = PrintRequestService.sanitizeFilename("a".repeat(300) + ".stl");
        assertEquals(255, name.length());
        assertTrue(name.endsWith("a.stl"));
        assertEquals(255, PrintRequestService.sanitizeFilename("b".repeat(300)).length());
    }

    // --- list / get ---

    @Test
    void listOwnMapsSummaries() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        r.finalPrice = new BigDecimal("12.50");
        when(requests.listOwnNewestFirst(2L)).thenReturn(List.of(r));

        PrintRequestSummaryDto summary = service.listOwn(2).getFirst();

        assertEquals(4L, summary.id());
        assertEquals(new BigDecimal("12.50"), summary.finalPrice());
        assertEquals("bob", summary.ownerUsername());
    }

    @Test
    void ownerAndAdminCanGetRequest() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertEquals(4L, service.get(4, BOB).id());
        assertEquals(4L, service.get(4, ADMIN).id());
    }

    @Test
    void otherUserGetsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        var e = assertThrows(ResourceNotFoundException.class, () -> service.get(4, EVE));
        assertEquals("Print request not found", e.getMessage());
    }

    @Test
    void unknownRequestIsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(4, ADMIN));
    }

    @Test
    void listAllPassesFilterAndSearch() {
        when(requests.searchNewestFirst(RequestStatus.OPEN, "ben"))
                .thenReturn(List.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertEquals("bob", service.listAll(RequestStatus.OPEN, "ben").getFirst().ownerUsername());
    }

    @Test
    void detailListsNextStatuses() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.IN_REVIEW)));
        assertEquals(List.of(RequestStatus.ACCEPTED, RequestStatus.DECLINED), service.get(4, ADMIN).nextStatuses());
    }

    // --- review: status ---

    private PrintRequest reviewed(RequestStatus status) {
        PrintRequest r = request(4, user(2, "bob"), status);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void changeStatusMovesAlongTheFlow() {
        PrintRequest r = reviewed(RequestStatus.SUBMITTED);
        PrintRequestDto dto = service.changeStatus(4, RequestStatus.IN_REVIEW, "ignored");
        assertEquals(RequestStatus.IN_REVIEW, r.status);
        assertNull(r.declineReason, "reason is only kept when declining");
        assertEquals(RequestStatus.IN_REVIEW, dto.status());
        verify(requests).flush();
    }

    @Test
    void declineStoresTrimmedReason() {
        PrintRequest r = reviewed(RequestStatus.IN_REVIEW);
        PrintRequestDto dto = service.changeStatus(4, RequestStatus.DECLINED, "  Too big for my printer ");
        assertEquals("Too big for my printer", r.declineReason);
        assertEquals("Too big for my printer", dto.declineReason());
        assertTrue(dto.nextStatuses().isEmpty());
    }

    @Test
    void declineWithoutReasonKeepsItEmpty() {
        PrintRequest r = reviewed(RequestStatus.SUBMITTED);
        service.changeStatus(4, RequestStatus.DECLINED, " ");
        assertNull(r.declineReason);
    }

    @Test
    void invalidTransitionIsConflict() {
        PrintRequest r = reviewed(RequestStatus.SUBMITTED);
        var e = assertThrows(ConflictException.class, () -> service.changeStatus(4, RequestStatus.COMPLETED, null));
        assertEquals("Cannot change status from SUBMITTED to COMPLETED", e.getMessage());
        assertEquals(RequestStatus.SUBMITTED, r.status);
    }

    @Test
    void completingNeedsAPrice() {
        PrintRequest r = reviewed(RequestStatus.ACCEPTED);
        var e = assertThrows(ConflictException.class, () -> service.changeStatus(4, RequestStatus.COMPLETED, null));
        assertEquals("Set a final price before completing the request", e.getMessage());

        r.finalPrice = BigDecimal.TEN;
        service.changeStatus(4, RequestStatus.COMPLETED, null);
        assertEquals(RequestStatus.COMPLETED, r.status);
    }

    @Test
    void acceptingDoesNotNeedAPrice() {
        PrintRequest r = reviewed(RequestStatus.SUBMITTED);
        service.changeStatus(4, RequestStatus.ACCEPTED, null);
        assertEquals(RequestStatus.ACCEPTED, r.status);
    }

    @Test
    void finalRequestCannotChangeStatus() {
        reviewed(RequestStatus.DECLINED);
        var e = assertThrows(ConflictException.class, () -> service.changeStatus(4, RequestStatus.IN_REVIEW, null));
        assertEquals("This request is DECLINED and can no longer be changed", e.getMessage());
    }

    @Test
    void reviewingUnknownRequestIsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.changeStatus(4, RequestStatus.IN_REVIEW, null));
        assertThrows(ResourceNotFoundException.class, () -> service.changePrice(4, BigDecimal.ONE));
    }

    // --- review: price ---

    @Test
    void changePriceSetsAndClears() {
        PrintRequest r = reviewed(RequestStatus.ACCEPTED);
        assertEquals(new BigDecimal("12.50"), service.changePrice(4, new BigDecimal("12.50")).finalPrice());
        assertEquals(new BigDecimal("12.50"), r.finalPrice);

        assertNull(service.changePrice(4, null).finalPrice());
        assertNull(r.finalPrice);
    }

    @Test
    void paidRequestKeepsItsPrice() {
        PrintRequest r = reviewed(RequestStatus.ACCEPTED);
        r.finalPrice = BigDecimal.ONE;
        r.paidAt = Instant.EPOCH;
        var e = assertThrows(ConflictException.class, () -> service.changePrice(4, BigDecimal.TEN));
        assertEquals("Mark the request as unpaid before changing the price", e.getMessage());
        assertEquals(BigDecimal.ONE, r.finalPrice);
    }

    // --- review: paid ---

    @Test
    void marksAcceptedAndCompletedRequestsPaid() {
        PrintRequest r = reviewed(RequestStatus.ACCEPTED);
        r.finalPrice = BigDecimal.TEN;
        assertNotNull(service.changePaid(4, true).paidAt());
        assertNotNull(r.paidAt);

        r.status = RequestStatus.COMPLETED;
        assertNull(service.changePaid(4, false).paidAt());
        assertNull(r.paidAt);
    }

    @Test
    void markingPaidAgainKeepsTheOriginalMoment() {
        PrintRequest r = reviewed(RequestStatus.COMPLETED);
        r.finalPrice = BigDecimal.TEN;
        r.paidAt = Instant.EPOCH;
        service.changePaid(4, true);
        assertEquals(Instant.EPOCH, r.paidAt);
    }

    @Test
    void paidNeedsAPrice() {
        PrintRequest r = reviewed(RequestStatus.ACCEPTED);
        var e = assertThrows(ConflictException.class, () -> service.changePaid(4, true));
        assertEquals("Set a final price before marking the request as paid", e.getMessage());
        assertNull(r.paidAt);
    }

    @Test
    void paidOnlyForAcceptedOrCompleted() {
        PrintRequest r = reviewed(RequestStatus.IN_REVIEW);
        r.finalPrice = BigDecimal.TEN;
        var e = assertThrows(ConflictException.class, () -> service.changePaid(4, true));
        assertEquals("Only accepted, printing or completed requests can be marked as paid", e.getMessage());
        r.status = RequestStatus.DECLINED;
        assertThrows(ConflictException.class, () -> service.changePaid(4, false));
    }

    @Test
    void paidOfUnknownRequestIsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.changePaid(4, true));
    }

    @Test
    void finalRequestCannotChangePrice() {
        PrintRequest r = reviewed(RequestStatus.COMPLETED);
        r.finalPrice = BigDecimal.ONE;
        assertThrows(ConflictException.class, () -> service.changePrice(4, BigDecimal.TEN));
        assertEquals(BigDecimal.ONE, r.finalPrice);
    }

    // --- file download ---

    @Test
    void fileReturnsStoredFile() throws IOException {
        Path stored = Files.writeString(dir.resolve("abc.stl"), "x");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.ACCEPTED)));
        when(storage.resolve("4/abc.stl")).thenReturn(stored);

        StoredFile file = service.file(4, 104, BOB);

        assertEquals(stored, file.path());
        assertEquals("model.stl", file.filename());
        assertEquals("model/stl", file.contentType());
        assertEquals(134, file.size());
    }

    @Test
    void fileOfOtherRequestIsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertThrows(ResourceNotFoundException.class, () -> service.file(4, 999, BOB));
    }

    @Test
    void fileMissingOnDiskIsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        when(storage.resolve("4/abc.stl")).thenReturn(dir.resolve("gone.stl"));
        assertThrows(ResourceNotFoundException.class, () -> service.file(4, 104, ADMIN));
    }

    @Test
    void otherUserCannotDownload() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertThrows(ResourceNotFoundException.class, () -> service.file(4, 104, EVE));
        verifyNoInteractions(storage);
    }

    // --- delete ---

    @Test
    void ownerDeletesSubmittedRequestAndFilesGoAfterCommit() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        service.delete(4, BOB);

        verify(requests).delete(r);
        verify(storage, never()).deleteDirectory(any());
        captured(true).run();
        verify(storage).deleteDirectory("4");
    }

    @Test
    void ownerCannotDeleteOnceInReview() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.IN_REVIEW)));
        var e = assertThrows(ConflictException.class, () -> service.delete(4, BOB));
        assertEquals("Only submitted requests can be deleted", e.getMessage());
        verify(requests, never()).delete(any(PrintRequest.class));
        verifyNoInteractions(afterTransaction);
    }

    @Test
    void adminDeletesInAnyStatus() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.COMPLETED);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        service.delete(4, ADMIN);
        verify(requests).delete(r);
    }

    @Test
    void otherUserCannotDelete() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(4, EVE));
        verify(requests, never()).delete(any(PrintRequest.class));
    }

    @Test
    void deletingAUserSchedulesEveryRequestDirectory() {
        when(requests.idsOfOwner(2)).thenReturn(List.of(4L, 5L));

        service.onUserDeleted(new UserDeleted(2));

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterTransaction, times(2)).onCommit(captor.capture());
        captor.getAllValues().forEach(Runnable::run);
        verify(storage).deleteDirectory("4");
        verify(storage).deleteDirectory("5");
    }

    // --- files of a request (#23) ---

    private PrintRequest locked(RequestStatus status) {
        PrintRequest r = request(4, user(2, "bob"), status);
        // The first look at the request, and the locked one inside the insert.
        lenient().when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        lenient().when(requests.findLockedWithDetails(4)).thenReturn(Optional.of(r));
        return r;
    }

    private UploadedModel upload(String filename) {
        return new UploadedModel(filename, dir.resolve("upload"), 100);
    }

    private static RequestFile extraFile(PrintRequest r, long id, String name) {
        RequestFile f = new RequestFile();
        f.id = id;
        f.originalFilename = name;
        f.storagePath = r.id + "/" + id + ".stl";
        f.fileType = ModelFileType.STL;
        f.sizeBytes = 10L;
        r.addFile(f);
        return f;
    }

    @Test
    void ownerAddsFileWhileSubmitted() {
        PrintRequest r = locked(RequestStatus.SUBMITTED);
        when(validator.isValid(dir.resolve("upload"), ModelFileType.STL)).thenReturn(true);
        when(storage.store(dir.resolve("upload"), "4", "stl")).thenReturn("4/new.stl");

        PrintRequestDto dto = service.addFile(4, upload("lid.stl"), 3, BOB);

        assertEquals(2, dto.files().size());
        RequestFileDto added = dto.files().get(1);
        assertEquals("lid.stl", added.originalFilename());
        assertEquals(3, added.quantity());
        assertEquals(4, dto.totals().copies());
        RequestFile file = r.files.get(1);
        // Analysed first: slicing a 3MF needs the model details.
        InOrder order = inOrder(requests, modelInfo, slicing);
        order.verify(requests).flush();
        order.verify(modelInfo).analyze(file.id);
        order.verify(slicing).enqueueIfEnabled(file.id);
        verify(storage, never()).deleteFile(any());
    }

    @Test
    void onlyTheInsertOfAnAddedFileHoldsATransaction() {
        locked(RequestStatus.SUBMITTED);
        when(validator.isValid(any(), any())).thenAnswer(inv -> {
            assertFalse(transactions.active(), "the upload is checked outside a transaction");
            return true;
        });
        when(storage.store(any(), any(), any())).thenAnswer(inv -> {
            assertFalse(transactions.active(), "the file is copied outside a transaction");
            return "4/new.stl";
        });
        when(requests.findLockedWithDetails(4)).thenAnswer(inv -> {
            assertTrue(transactions.active(), "the request is locked inside a transaction");
            return requests.findWithDetails(4);
        });
        doAnswer(inv -> {
            assertFalse(transactions.active(), "the model is analysed outside a transaction");
            return null;
        }).when(modelInfo).analyze(anyLong());
        doAnswer(inv -> {
            assertTrue(transactions.active(), "queueing for slicing needs a transaction");
            return null;
        }).when(slicing).enqueueIfEnabled(anyLong());

        service.addFile(4, upload("lid.stl"), 1, BOB);

        verify(modelInfo).analyze(anyLong());
        verify(slicing).enqueueIfEnabled(anyLong());
    }

    @Test
    void theModelOfANewRequestIsAnalysedOutsideTheTransaction() {
        when(users.findByIdOptional(2L)).thenReturn(Optional.of(user(2, "bob")));
        when(validator.isValid(any(), any())).thenReturn(true);
        doAnswer(inv -> {
            PrintRequest created = inv.getArgument(0);
            created.id = 9L;
            tracked.add(created);
            return null;
        }).when(requests).persist(any(PrintRequest.class));
        when(storage.store(any(), any(), any())).thenReturn("9/uuid.stl");
        doAnswer(inv -> {
            assertFalse(transactions.active(), "the model is analysed outside a transaction");
            return null;
        }).when(modelInfo).analyze(anyLong());

        service.create(2, input("benchy.stl", 500));

        verify(modelInfo).analyze(900L);
    }

    @Test
    void theCopyIsRemovedWhenTheRequestChangedWhileItWasMade() {
        locked(RequestStatus.SUBMITTED);
        // Between the first look and the insert another upload filled the request.
        PrintRequest full = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraFile(full, 201, "a.stl");
        extraFile(full, 202, "b.stl");
        when(requests.findLockedWithDetails(4)).thenReturn(Optional.of(full));
        when(validator.isValid(any(), any())).thenReturn(true);
        when(storage.store(any(), any(), any())).thenReturn("4/new.stl");

        assertThrows(InvalidInputException.class, () -> service.addFile(4, upload("lid.stl"), 1, BOB));

        verify(storage).deleteFile("4/new.stl");
        verifyNoInteractions(modelInfo, slicing);
    }

    @Test
    void theCopyIsRemovedWhenTheRequestWasDeletedWhileItWasMade() {
        locked(RequestStatus.SUBMITTED);
        when(requests.findLockedWithDetails(4)).thenReturn(Optional.empty());
        when(validator.isValid(any(), any())).thenReturn(true);
        when(storage.store(any(), any(), any())).thenReturn("4/new.stl");

        assertThrows(ResourceNotFoundException.class, () -> service.addFile(4, upload("lid.stl"), 1, BOB));

        verify(storage).deleteFile("4/new.stl");
    }

    @Test
    void aFailingAnalysisOrQueueDoesNotFailTheUpload() {
        locked(RequestStatus.SUBMITTED);
        when(validator.isValid(any(), any())).thenReturn(true);
        when(storage.store(any(), any(), any())).thenReturn("4/new.stl");
        doThrow(new IllegalStateException("gone")).when(modelInfo).analyze(anyLong());

        assertEquals(2, service.addFile(4, upload("lid.stl"), 1, BOB).files().size());

        verify(storage, never()).deleteFile(any());
    }

    @Test
    void ownerCannotAddFileOnceInReview() {
        locked(RequestStatus.IN_REVIEW);
        var e = assertThrows(ConflictException.class, () -> service.addFile(4, upload("lid.stl"), 1, BOB));
        assertEquals("Files can only be changed while the request is submitted", e.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void adminAddsFileInAnyStatus() {
        locked(RequestStatus.COMPLETED);
        when(validator.isValid(any(), any())).thenReturn(true);
        when(storage.store(any(), any(), any())).thenReturn("4/new.stl");
        assertEquals(2, service.addFile(4, upload("lid.stl"), 1, ADMIN).files().size());
    }

    @Test
    void addFileRefusedAtTheFileLimit() {
        PrintRequest r = locked(RequestStatus.SUBMITTED);
        extraFile(r, 201, "a.stl");
        extraFile(r, 202, "b.stl");
        var e = assertThrows(InvalidInputException.class, () -> service.addFile(4, upload("lid.stl"), 1, BOB));
        assertEquals("A request can hold at most 3 files and links", e.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void addFileChecksTheUpload() {
        locked(RequestStatus.SUBMITTED);
        assertThrows(InvalidInputException.class, () -> service.addFile(4, upload("lid.obj"), 1, BOB));
        verifyNoInteractions(storage);
    }

    @Test
    void otherUserCannotAddFile() {
        locked(RequestStatus.SUBMITTED);
        assertThrows(ResourceNotFoundException.class, () -> service.addFile(4, upload("lid.stl"), 1, EVE));
        when(requests.findWithDetails(5)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addFile(5, upload("lid.stl"), 1, ADMIN));
        verifyNoInteractions(storage);
    }

    @Test
    void ownerRemovesFileAndItLeavesTheDiskAfterCommit() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraFile(r, 201, "lid.stl");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        PrintRequestDto dto = service.removeFile(4, 104, BOB);

        assertEquals(List.of(201L), dto.files().stream().map(RequestFileDto::id).toList());
        verify(requests).flush();
        verify(storage, never()).deleteFile(any());
        captured(true).run();
        verify(storage).deleteFile("4/abc.stl");
    }

    @Test
    void lastFileCannotBeRemoved() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        var e = assertThrows(ConflictException.class, () -> service.removeFile(4, 104, ADMIN));
        assertEquals("A request needs at least one file or link; delete the request instead", e.getMessage());
        verifyNoInteractions(afterTransaction);
    }

    @Test
    void ownerCannotRemoveFileOnceAccepted() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.ACCEPTED);
        extraFile(r, 201, "lid.stl");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        assertThrows(ConflictException.class, () -> service.removeFile(4, 201, BOB));
        assertEquals(2, r.files.size());
    }

    @Test
    void adminRemovesFileInAnyStatus() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.DECLINED);
        extraFile(r, 201, "lid.stl");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        assertEquals(1, service.removeFile(4, 201, ADMIN).files().size());
    }

    @Test
    void removingUnknownFileIsNotFound() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraFile(r, 201, "lid.stl");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        assertThrows(ResourceNotFoundException.class, () -> service.removeFile(4, 999, BOB));
    }

    @Test
    void ownerChangesQuantityWhileSubmitted() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        PrintRequestDto dto = service.changeQuantity(4, 104, 7, BOB);

        assertEquals(7, dto.files().getFirst().quantity());
        assertEquals(7, r.files.getFirst().quantity);
        verify(requests).flush();
    }

    @Test
    void ownerCannotChangeQuantityOnceInReview() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.IN_REVIEW)));
        var e = assertThrows(ConflictException.class, () -> service.changeQuantity(4, 104, 2, BOB));
        assertEquals("Quantities can only be changed while the request is submitted", e.getMessage());
    }

    @Test
    void adminChangesQuantityUntilFinal() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.PRINTING);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        assertEquals(2, service.changeQuantity(4, 104, 2, ADMIN).files().getFirst().quantity());

        r.status = RequestStatus.COMPLETED;
        assertThrows(ConflictException.class, () -> service.changeQuantity(4, 104, 3, ADMIN));
        assertEquals(2, r.files.getFirst().quantity);
    }

    @Test
    void otherUserCannotChangeQuantity() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertThrows(ResourceNotFoundException.class, () -> service.changeQuantity(4, 104, 2, EVE));
    }

    @Test
    void filesBundleGivesEveryFileAUniqueName() throws IOException {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        r.name = "Box/lid set";
        extraFile(r, 201, "MODEL.stl");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        when(storage.resolve(any())).thenReturn(Files.writeString(dir.resolve("on-disk.stl"), "x"));

        FileBundle bundle = service.files(4, BOB);

        assertEquals("Box_lid set.zip", bundle.name());
        assertEquals(List.of("model.stl", "MODEL (2).stl"), bundle.files().stream().map(StoredFile::filename).toList());
    }

    @Test
    void filesBundleFailsWhenAFileIsMissing() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        when(storage.resolve("4/abc.stl")).thenReturn(dir.resolve("gone.stl"));
        assertThrows(ResourceNotFoundException.class, () -> service.files(4, BOB));
    }

    @Test
    void otherUserCannotDownloadAllFiles() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        assertThrows(ResourceNotFoundException.class, () -> service.files(4, EVE));
    }

    @Test
    void uniqueNamesNumbersDuplicates() {
        assertEquals(
                List.of("a.stl", "b.stl", "a (2).stl", "A (3).stl", "noext", "noext (2)", ".hidden", ".hidden (2)"),
                PrintRequestService.uniqueNames(
                        List.of("a.stl", "b.stl", "a.stl", "A.stl", "noext", "noext", ".hidden", ".hidden")));
    }

    @Test
    void uniqueNamesSkipsANumberThatIsTaken() {
        assertEquals(List.of("a (2).stl", "a.stl", "a (3).stl"),
                PrintRequestService.uniqueNames(List.of("a (2).stl", "a.stl", "a.stl")));
    }

    static final CustomResliceRequest CHOICE = new CustomResliceRequest(1L, new BigDecimal("0.4"), "0.20mm Standard", "PLA Basic");

    @Test
    void resliceQueuesOnlyThatFileWithTheChoice() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.ACCEPTED);
        RequestFile lid = extraFile(r, 201, "lid.stl");
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        service.resliceWith(4, 201, CHOICE);

        verify(customReslice).reslice(lid.id, 1L, new BigDecimal("0.4"), "0.20mm Standard", "PLA Basic");
    }

    @Test
    void resliceOfUnknownFileOrRequestIsNotFound() {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.ACCEPTED)));
        assertThrows(ResourceNotFoundException.class, () -> service.resliceWith(4, 999, CHOICE));
        when(requests.findWithDetails(5)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.resliceWith(5, 105, CHOICE));
        verifyNoInteractions(customReslice);
    }

    // --- links (#59) ---

    private static RequestFile extraLink(PrintRequest r, long id, String url, String name, int quantity) {
        RequestFile link = new RequestFile();
        link.id = id;
        link.kind = RequestFileKind.LINK;
        link.url = url;
        link.linkName = name;
        link.quantity = quantity;
        r.addFile(link);
        return link;
    }

    @Test
    void createWithALinkStoresNothingAndAnalysesNothing() {
        when(users.findByIdOptional(2L)).thenReturn(Optional.of(user(2, "bob")));
        doAnswer(inv -> {
            PrintRequest created = inv.getArgument(0);
            created.id = 9L;
            tracked.add(created);
            return null;
        }).when(requests).persist(any(PrintRequest.class));

        PrintRequestDto dto = service.create(2, new NewPrintRequest("Dragon", null, null,
                new NewLink("  https://www.patreon.com/posts/dragon-123  ", "  Dragon (tier 2) "), 3));

        RequestFileDto link = dto.files().getFirst();
        assertEquals(RequestFileKind.LINK, link.kind());
        assertEquals("https://www.patreon.com/posts/dragon-123", link.url());
        assertEquals("Dragon (tier 2)", link.linkName());
        assertEquals(3, link.quantity());
        assertNull(link.originalFilename());
        assertNull(link.type());
        assertNull(link.sizeBytes());
        assertNull(link.modelInfo());
        assertEquals(1, dto.totals().links());
        assertEquals(0, dto.totals().files());
        verifyNoInteractions(storage, validator, slicing, afterTransaction);
        verify(modelInfo, never()).analyze(anyLong());
        verify(modelInfo).describe(List.of(), false);
    }

    @Test
    void createWithAnInvalidLinkCreatesNothing() {
        var e = assertThrows(InvalidInputException.class, () -> service.create(2,
                new NewPrintRequest("Dragon", null, null, new NewLink("ftp://example.com/a.stl", null), 1)));
        assertEquals("The link must start with http:// or https://", e.getMessage());
        verifyNoInteractions(requests, users);
    }

    @Test
    void checkLinkAcceptsHttpAndHttpsAndCleansTheName() {
        assertEquals(new NewLink("HTTP://example.com/a", null),
                PrintRequestService.checkLink(new NewLink(" HTTP://example.com/a ", "   ")));
        assertEquals(new NewLink("https://example.com/a?b=c#d", "Lid"),
                PrintRequestService.checkLink(new NewLink("https://example.com/a?b=c#d", "Li\u0000d")));
    }

    @Test
    void checkLinkRefusesWhatIsNotAWebAddress() {
        assertLinkRefused(null, "A link is required");
        assertLinkRefused("   ", "A link is required");
        assertLinkRefused("javascript:alert(1)", "The link must start with http:// or https://");
        assertLinkRefused("www.example.com", "The link must start with http:// or https://");
        assertLinkRefused("https://exa mple.com", "The link is not a valid web address");
        assertLinkRefused("https:///path-only", "The link is not a valid web address");
        assertLinkRefused("https://example.com/" + "a".repeat(2048), "The link must be at most 2048 characters");
        var e = assertThrows(InvalidInputException.class,
                () -> PrintRequestService.checkLink(new NewLink("https://example.com", "n".repeat(101))));
        assertEquals("The link name must be at most 100 characters", e.getMessage());
    }

    private static void assertLinkRefused(String url, String message) {
        var e = assertThrows(InvalidInputException.class, () -> PrintRequestService.checkLink(new NewLink(url, null)));
        assertEquals(message, e.getMessage());
    }

    @Test
    void ownerAddsLinkWhileSubmitted() {
        locked(RequestStatus.SUBMITTED);

        PrintRequestDto dto = service.addLink(4, new NewLink("https://www.printables.com/model/1", null), 2, BOB);

        assertEquals(2, dto.files().size());
        RequestFileDto link = dto.files().getLast();
        assertEquals(RequestFileKind.LINK, link.kind());
        assertEquals(2, link.quantity());
        assertNull(link.linkName());
        assertEquals(1, dto.totals().links());
        assertEquals(3, dto.totals().copies());
        verify(requests).findLockedWithDetails(4);
        verifyNoInteractions(storage, slicing);
        verify(modelInfo, never()).analyze(anyLong());
        verify(modelInfo).describe(List.of(104L), false);
    }

    @Test
    void addLinkRefusedAtTheFileLimit() {
        PrintRequest r = locked(RequestStatus.SUBMITTED);
        extraFile(r, 201, "a.stl");
        extraLink(r, 202, "https://example.com/b", null, 1);
        var e = assertThrows(InvalidInputException.class,
                () -> service.addLink(4, new NewLink("https://example.com/c", null), 1, BOB));
        assertEquals("A request can hold at most 3 files and links", e.getMessage());
        assertEquals(3, r.files.size());
    }

    @Test
    void ownerCannotAddLinkOnceInReviewButTheAdminCan() {
        locked(RequestStatus.IN_REVIEW);
        NewLink link = new NewLink("https://example.com/c", null);
        assertThrows(ConflictException.class, () -> service.addLink(4, link, 1, BOB));
        assertEquals(2, service.addLink(4, link, 1, ADMIN).files().size());
    }

    @Test
    void otherUserCannotAddLink() {
        locked(RequestStatus.SUBMITTED);
        NewLink link = new NewLink("https://example.com/c", null);
        assertThrows(ResourceNotFoundException.class, () -> service.addLink(4, link, 1, EVE));
        when(requests.findLockedWithDetails(5)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addLink(5, link, 1, ADMIN));
    }

    @Test
    void addLinkChecksTheLinkBeforeLookingAtTheRequest() {
        assertThrows(InvalidInputException.class,
                () -> service.addLink(4, new NewLink("mailto:bob@example.com", null), 1, BOB));
        verifyNoInteractions(requests);
    }

    @Test
    void removingALinkTouchesNoDisk() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraLink(r, 201, "https://example.com/a", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        PrintRequestDto dto = service.removeFile(4, 201, BOB);

        assertEquals(List.of(104L), dto.files().stream().map(RequestFileDto::id).toList());
        verifyNoInteractions(afterTransaction, storage);
    }

    @Test
    void theLastLinkCannotBeRemoved() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        r.files.clear();
        extraLink(r, 201, "https://example.com/a", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        assertThrows(ConflictException.class, () -> service.removeFile(4, 201, BOB));
    }

    @Test
    void ownerChangesTheQuantityOfALink() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraLink(r, 201, "https://example.com/a", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        PrintRequestDto dto = service.changeQuantity(4, 201, 5, BOB);

        assertEquals(5, dto.files().getLast().quantity());
    }

    @Test
    void filesBundleListsTheLinksInATextFile() throws IOException {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraLink(r, 201, "https://www.patreon.com/posts/dragon-123", "Dragon", 2);
        extraLink(r, 202, "https://www.printables.com/model/1", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        when(storage.resolve(any())).thenReturn(Files.writeString(dir.resolve("on-disk.stl"), "x"));

        FileBundle bundle = service.files(4, BOB);

        assertEquals(List.of("model.stl"), bundle.files().stream().map(StoredFile::filename).toList());
        assertEquals("""
                Links in this request; download these models yourself.

                Dragon (quantity: 2)
                https://www.patreon.com/posts/dragon-123

                www.printables.com (quantity: 1)
                https://www.printables.com/model/1
                """, bundle.linksText());
    }

    @Test
    void filesBundleOfOnlyLinksHasNoFiles() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        r.files.clear();
        extraLink(r, 201, "https://example.com/a", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));

        FileBundle bundle = service.files(4, BOB);

        assertEquals(List.of(), bundle.files());
        assertTrue(bundle.linksText().contains("https://example.com/a"));
        verifyNoInteractions(storage);
    }

    @Test
    void filesBundleWithoutLinksHasNoText() throws IOException {
        when(requests.findWithDetails(4)).thenReturn(Optional.of(request(4, user(2, "bob"), RequestStatus.SUBMITTED)));
        when(storage.resolve(any())).thenReturn(Files.writeString(dir.resolve("on-disk.stl"), "x"));
        assertNull(service.files(4, BOB).linksText());
    }

    @Test
    void linkLabelIsTheNameOrTheHost() {
        assertEquals("Lid", PrintRequestService.linkLabel("Lid", "https://example.com/a"));
        assertEquals("example.com", PrintRequestService.linkLabel(null, "https://example.com/a"));
        assertEquals("https:/a", PrintRequestService.linkLabel(null, "https:/a"));
    }

    @Test
    void aLinkCannotBeDownloadedAsAFile() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.SUBMITTED);
        extraLink(r, 201, "https://example.com/a", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        var e = assertThrows(ResourceNotFoundException.class, () -> service.file(4, 201, BOB));
        assertEquals("File not found", e.getMessage());
        verifyNoInteractions(storage);
    }

    @Test
    void aLinkCannotBeResliced() {
        PrintRequest r = request(4, user(2, "bob"), RequestStatus.ACCEPTED);
        extraLink(r, 201, "https://example.com/a", null, 1);
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        var e = assertThrows(ConflictException.class, () -> service.resliceWith(4, 201, CHOICE));
        assertEquals("Links can't be sliced", e.getMessage());
        verifyNoInteractions(customReslice);
    }

    // --- item statuses (#61) ---

    private PrintRequest withItems(RequestStatus status, ItemStatus... items) {
        PrintRequest r = request(4, user(2, "bob"), status);
        r.files.getFirst().status = items[0];
        for (int i = 1; i < items.length; i++) {
            extraFile(r, 200 + i, "part" + i + ".stl").status = items[i];
        }
        when(requests.findWithDetails(4)).thenReturn(Optional.of(r));
        return r;
    }

    private static List<ItemStatus> statusesOf(PrintRequest r) {
        return r.files.stream().map(file -> file.status).toList();
    }

    @Test
    void adminMovesAnItemAlongItsFlow() {
        PrintRequest r = withItems(RequestStatus.ACCEPTED, ItemStatus.PENDING, ItemStatus.PENDING);

        PrintRequestDto dto = service.changeItemStatus(4, 104, ItemStatus.ACCEPTED, "ignored");

        assertEquals(ItemStatus.ACCEPTED, dto.files().getFirst().status());
        assertNull(dto.files().getFirst().declineReason());
        assertEquals(List.of(ItemStatus.PRINTING, ItemStatus.COMPLETED, ItemStatus.DECLINED),
                dto.files().getFirst().nextStatuses());
        assertEquals(ItemStatus.PENDING, r.files.getLast().status);
        verify(requests).flush();
    }

    @Test
    void decliningAnItemKeepsTheTrimmedReasonAndReopeningDropsIt() {
        PrintRequest r = withItems(RequestStatus.IN_REVIEW, ItemStatus.PENDING, ItemStatus.PENDING);

        PrintRequestDto dto = service.changeItemStatus(4, 104, ItemStatus.DECLINED, "  Too big for my printers ");

        assertEquals("Too big for my printers", dto.files().getFirst().declineReason());
        assertEquals(1, dto.totals().declined());
        assertEquals(1, dto.totals().copies());

        service.changeItemStatus(4, 104, ItemStatus.ACCEPTED, null);
        assertEquals(ItemStatus.ACCEPTED, r.files.getFirst().status);
        assertNull(r.files.getFirst().declineReason);

        service.changeItemStatus(4, 104, ItemStatus.DECLINED, "   ");
        assertNull(r.files.getFirst().declineReason);
    }

    @Test
    void itemMovesOutsideTheFlowOrOnAFinalRequestAreRefused() {
        withItems(RequestStatus.ACCEPTED, ItemStatus.PENDING);
        var e = assertThrows(ConflictException.class, () -> service.changeItemStatus(4, 104, ItemStatus.PRINTING, null));
        assertEquals("Cannot change the item's status from PENDING to PRINTING", e.getMessage());

        withItems(RequestStatus.COMPLETED, ItemStatus.COMPLETED);
        e = assertThrows(ConflictException.class, () -> service.changeItemStatus(4, 104, ItemStatus.ACCEPTED, null));
        assertEquals("This request is COMPLETED and its items can no longer change", e.getMessage());

        assertThrows(ResourceNotFoundException.class, () -> service.changeItemStatus(4, 999, ItemStatus.ACCEPTED, null));
        when(requests.findWithDetails(5)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.changeItemStatus(5, 104, ItemStatus.ACCEPTED, null));
    }

    @Test
    void aFinalRequestOffersNoItemStatuses() {
        withItems(RequestStatus.DECLINED, ItemStatus.DECLINED);
        assertEquals(List.of(), service.get(4, ADMIN).files().getFirst().nextStatuses());
    }

    @Test
    void acceptingTheRequestAcceptsItsPendingItems() {
        PrintRequest r = withItems(RequestStatus.IN_REVIEW, ItemStatus.PENDING, ItemStatus.DECLINED);
        service.changeStatus(4, RequestStatus.ACCEPTED, null);
        assertEquals(List.of(ItemStatus.ACCEPTED, ItemStatus.DECLINED), statusesOf(r));
    }

    @Test
    void decliningTheRequestDeclinesAllButCompletedItems() {
        PrintRequest r = withItems(RequestStatus.PRINTING, ItemStatus.PRINTING, ItemStatus.COMPLETED,
                ItemStatus.PENDING);
        r.files.getFirst().declineReason = "old";
        service.changeStatus(4, RequestStatus.DECLINED, "No time");
        assertEquals(List.of(ItemStatus.DECLINED, ItemStatus.COMPLETED, ItemStatus.DECLINED), statusesOf(r));
        assertNull(r.files.getFirst().declineReason);
    }

    @Test
    void completingTheRequestCompletesAllButDeclinedItems() {
        PrintRequest r = withItems(RequestStatus.PRINTING, ItemStatus.PRINTING, ItemStatus.DECLINED,
                ItemStatus.PENDING);
        r.finalPrice = BigDecimal.TEN;
        r.files.get(1).declineReason = "Too big";
        service.changeStatus(4, RequestStatus.COMPLETED, null);
        assertEquals(List.of(ItemStatus.COMPLETED, ItemStatus.DECLINED, ItemStatus.COMPLETED), statusesOf(r));
        assertEquals("Too big", r.files.get(1).declineReason);
    }

    @Test
    void otherRequestMovesLeaveTheItemsAlone() {
        PrintRequest r = withItems(RequestStatus.PRINTING, ItemStatus.PENDING, ItemStatus.PRINTING);
        service.changeStatus(4, RequestStatus.ACCEPTED, null);
        assertEquals(List.of(ItemStatus.PENDING, ItemStatus.PRINTING), statusesOf(r));
        service.changeStatus(4, RequestStatus.PRINTING, null);
        assertEquals(List.of(ItemStatus.PENDING, ItemStatus.PRINTING), statusesOf(r));
        r.status = RequestStatus.SUBMITTED;
        service.changeStatus(4, RequestStatus.IN_REVIEW, null);
        assertEquals(List.of(ItemStatus.PENDING, ItemStatus.PRINTING), statusesOf(r));
    }

    @Test
    void listsCarryTheItemProgress() {
        PrintRequest first = request(4, user(2, "bob"), RequestStatus.ACCEPTED);
        PrintRequest second = request(5, user(2, "bob"), RequestStatus.SUBMITTED);
        when(requests.listOwnNewestFirst(2)).thenReturn(List.of(first, second));
        when(requests.itemProgress(List.of(4L, 5L))).thenReturn(Map.of(4L, new ItemProgress(2, 3)));

        List<PrintRequestSummaryDto> rows = service.listOwn(2);

        assertEquals(2, rows.getFirst().itemsCompleted());
        assertEquals(3, rows.getFirst().itemsActive());
        assertEquals(0, rows.getLast().itemsCompleted());
        assertEquals(0, rows.getLast().itemsActive());
    }
}
