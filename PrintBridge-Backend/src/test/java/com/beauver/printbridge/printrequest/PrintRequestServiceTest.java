package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.Caller;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.common.tx.AfterTransaction;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.storage.FileStorage;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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

    @TempDir
    Path dir;

    PrintRequestService service;
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
        service.validator = validator;
        service.storage = storage;
        service.afterTransaction = afterTransaction;
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

    private static PrintRequest request(long id, User owner, RequestStatus status) {
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
        f.sizeBytes = 134;
        r.addFile(f);
        return r;
    }

    private NewPrintRequest input(String filename, long size) {
        return new NewPrintRequest(" Benchy ", "  ", new UploadedModel(filename, dir.resolve("upload"), size));
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
        assertEquals(List.of("stl"), config.allowedExtensions());
    }

    // --- create ---

    @Test
    void createStoresFileAndPersistsSubmittedRequest() {
        User bob = user(2, "bob");
        when(users.findByIdOptional(2L)).thenReturn(Optional.of(bob));
        when(validator.isValid(dir.resolve("upload"), ModelFileType.STL)).thenReturn(true);
        doAnswer(inv -> {
            ((PrintRequest) inv.getArgument(0)).id = 9L;
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

        captured(false).run();
        verify(storage).deleteDirectory("9");
    }

    @Test
    void createAcceptsThreeMfAndKeepsDescription() {
        when(users.findByIdOptional(2L)).thenReturn(Optional.of(user(2, "bob")));
        when(validator.isValid(any(), eq(ModelFileType.THREE_MF))).thenReturn(true);
        doAnswer(inv -> {
            ((PrintRequest) inv.getArgument(0)).id = 1L;
            return null;
        }).when(requests).persist(any(PrintRequest.class));
        when(storage.store(any(), any(), eq("3mf"))).thenReturn("1/uuid.3mf");

        PrintRequestDto dto = service.create(2,
                new NewPrintRequest("Box", " A box ", new UploadedModel("box.3mf", dir.resolve("upload"), 10)));

        assertEquals("A box", dto.description());
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
        when(requests.listNewestFirst(2L)).thenReturn(List.of(r));

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
    void deleteFilesOfOwnerSchedulesEveryRequestDirectory() {
        when(requests.idsOfOwner(2)).thenReturn(List.of(4L, 5L));

        service.deleteFilesOfOwner(2);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterTransaction, times(2)).onCommit(captor.capture());
        captor.getAllValues().forEach(Runnable::run);
        verify(storage).deleteDirectory("4");
        verify(storage).deleteDirectory("5");
    }
}
