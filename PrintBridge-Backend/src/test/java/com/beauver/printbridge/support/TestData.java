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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        em.createNativeQuery("TRUNCATE users, refresh_tokens, print_requests, request_files, model_info "
                + "RESTART IDENTITY CASCADE").executeUpdate();
        em.createNativeQuery("UPDATE app_settings SET slicing_enabled = FALSE, slicing_timeout_minutes = 10, "
                + "default_printer_id = NULL, upload_max_files = NULL, upload_max_file_size_mb = NULL").executeUpdate();
        // Not TRUNCATE: app_settings references printers, and its row must stay.
        em.createNativeQuery("DELETE FROM printers").executeUpdate();
        em.createNativeQuery("ALTER SEQUENCE printers_id_seq RESTART").executeUpdate();
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
                    new NewPrintRequest(name, null, new UploadedModel("benchy.stl", upload, Files.size(upload)), 1));
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

    /** Writes the upload limits straight to the table, past the checks of the settings endpoint. */
    @Transactional
    public void setUploadLimits(Integer maxFiles, Integer maxFileSizeMb) {
        em.createNativeQuery("UPDATE app_settings SET upload_max_files = ?1, upload_max_file_size_mb = ?2")
                .setParameter(1, maxFiles)
                .setParameter(2, maxFileSizeMb)
                .executeUpdate();
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

    /** A server slicing settings body with a 5 minute timeout. */
    public static Map<String, Object> slicingSettings(boolean enabled) {
        Map<String, Object> slicing = new HashMap<>();
        slicing.put("enabled", enabled);
        slicing.put("timeoutMinutes", 5);
        return slicing;
    }

    /** A printer body ready for slicing: 256 × 256 × 250 mm, owned 0.2 + 0.4, default 0.4. */
    public static Map<String, Object> printer(String model) {
        Map<String, Object> printer = new HashMap<>();
        printer.put("model", model);
        printer.put("buildPlate", Map.of("x", 256, "y", 256, "z", 250));
        printer.put("ownedNozzles", List.of(0.4, 0.2, 0.40));
        printer.put("defaultNozzle", 0.4);
        printer.put("process", "0.20mm Standard @BBL X1C");
        printer.put("filament", "Bambu PLA Basic @BBL X1C");
        return printer;
    }

    /** A printer body with only a name and build plate (enough while slicing is off). */
    public static Map<String, Object> printer(String model, int x, int y, int z) {
        Map<String, Object> printer = new HashMap<>();
        printer.put("model", model);
        printer.put("buildPlate", Map.of("x", x, "y", y, "z", z));
        return printer;
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
