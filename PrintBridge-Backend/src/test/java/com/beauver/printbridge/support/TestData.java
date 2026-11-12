package com.beauver.printbridge.support;

import com.beauver.printbridge.auth.AuthService;
import com.beauver.printbridge.printrequest.PrintRequest;
import com.beauver.printbridge.printrequest.PrintRequestService;
import com.beauver.printbridge.printrequest.RequestStatus;
import com.beauver.printbridge.printrequest.dto.NewPrintRequest;
import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.printrequest.dto.UploadedModel;
import com.beauver.printbridge.storage.StorageConfig;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserService;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Database helpers for @QuarkusTest tests. */
@ApplicationScoped
public class TestData {

    public static final String PASSWORD = "password123";

    @Inject
    EntityManager em;

    @Inject
    UserService userService;

    @Inject
    AuthService authService;

    @Inject
    PrintRequestService printRequestService;

    @Inject
    StorageConfig storageConfig;

    @Transactional
    public void reset() {
        em.createNativeQuery("TRUNCATE users, refresh_tokens, print_requests, request_files RESTART IDENTITY CASCADE")
                .executeUpdate();
        deleteRecursively(storageRoot());
    }

    public Path storageRoot() {
        return Path.of(storageConfig.path()).toAbsolutePath().normalize();
    }

    /** Creates a request with a small binary STL through the service, then forces its status. */
    public PrintRequestDto printRequest(User owner, RequestStatus status) {
        return printRequest(owner, "Benchy", status);
    }

    public PrintRequestDto printRequest(User owner, String name, RequestStatus status) {
        try {
            Path upload = Files.createTempFile("upload", ".stl");
            Files.write(upload, ModelFiles.binaryStl(1));
            PrintRequestDto created = printRequestService.create(owner.id,
                    new NewPrintRequest(name, null, new UploadedModel("benchy.stl", upload, Files.size(upload))));
            Files.delete(upload);
            setStatus(created.id(), status);
            return created;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void setPrice(long requestId, java.math.BigDecimal price) {
        em.find(PrintRequest.class, requestId).finalPrice = price;
    }

    @Transactional
    public void setStatus(long requestId, RequestStatus status) {
        em.find(PrintRequest.class, requestId).status = status;
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public User admin() {
        return userService.create(new NewAccountRequest("admin", "admin@example.com", PASSWORD), Role.ADMIN);
    }

    public User user(String username) {
        return userService.create(new NewAccountRequest(username, username + "@example.com", PASSWORD), Role.USER);
    }

    public String accessToken(User user) {
        return authService.startSession(user).response().accessToken();
    }
}
