package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.user.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PrintRequestResourceTest {

    @Inject
    TestData data;

    User admin;
    User bob;
    String adminToken;
    String bobToken;
    String eveToken;

    @BeforeEach
    void setUp() {
        data.reset();
        admin = data.admin();
        bob = data.user("bob");
        adminToken = data.accessToken(admin);
        bobToken = data.accessToken(bob);
        eveToken = data.accessToken(data.user("eve"));
    }

    private ValidatableResponse upload(String token, String name, String filename, byte[] content) {
        var spec = given().auth().oauth2(token).multiPart("description", "Please print in red");
        if (name != null) {
            spec = spec.multiPart("name", name);
        }
        if (filename != null) {
            spec = spec.multiPart("file", filename, content, "application/octet-stream");
        }
        return spec.post("/api/requests").then();
    }

    private long storedFileCount() throws IOException {
        Path root = data.storageRoot();
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void uploadConfigIsAvailableToUsers() {
        given().auth().oauth2(bobToken).get("/api/requests/upload-config")
                .then().statusCode(200)
                .body("maxSizeBytes", is(1024 * 1024))
                .body("maxFiles", is(10))
                .body("allowedExtensions", contains("stl", "3mf"));
    }

    @Test
    void createsRequestWithBinaryStl() throws IOException {
        upload(bobToken, "Benchy", "benchy.stl", ModelFiles.binaryStl(2))
                .statusCode(201)
                .header("Location", endsWith("/api/requests/1"))
                .body("name", is("Benchy"))
                .body("description", is("Please print in red"))
                .body("status", is("SUBMITTED"))
                .body("finalPrice", nullValue())
                .body("ownerUsername", is("bob"))
                .body("files[0].originalFilename", is("benchy.stl"))
                .body("files[0].type", is("STL"))
                .body("files[0].sizeBytes", is(184))
                .body("files[0].quantity", is(1))
                .body("totals.files", is(1))
                .body("totals.copies", is(1));
        assertEquals(1, storedFileCount());

        given().auth().oauth2(bobToken).get("/api/requests")
                .then().statusCode(200)
                .body("name", contains("Benchy"))
                .body("[0].status", is("SUBMITTED"))
                .body("[0].finalPrice", nullValue());
    }

    @Test
    void createsRequestWithAsciiStlAndThreeMf() {
        upload(bobToken, "Cube", "cube.stl", ModelFiles.asciiStl()).statusCode(201);
        upload(bobToken, "Box", "box.3MF", ModelFiles.threeMf()).statusCode(201).body("files[0].type", is("THREE_MF"));
    }

    @Test
    void adminCanSubmitToo() {
        upload(adminToken, "Mine", "a.stl", ModelFiles.binaryStl(1)).statusCode(201);
    }

    @Test
    void rejectsInvalidUploadsAndCreatesNothing() throws IOException {
        upload(bobToken, null, "a.stl", ModelFiles.binaryStl(1)).statusCode(400);
        upload(bobToken, "  ", "a.stl", ModelFiles.binaryStl(1)).statusCode(400);
        upload(bobToken, "x".repeat(101), "a.stl", ModelFiles.binaryStl(1)).statusCode(400);
        upload(bobToken, "No file", null, null).statusCode(400);
        upload(bobToken, "Obj", "a.obj", ModelFiles.binaryStl(1))
                .statusCode(400).body("message", is("Only .stl, .3mf files are allowed"));
        upload(bobToken, "Fake", "a.stl", "not a model at all".getBytes())
                .statusCode(400).body("message", is("The file is not a valid STL file"));
        upload(bobToken, "Fake 3mf", "a.3mf", ModelFiles.zip("other.txt"))
                .statusCode(400).body("message", is("The file is not a valid 3MF file"));
        upload(bobToken, "Huge", "a.stl", ModelFiles.binaryStl(21_000))
                .statusCode(400).body("message", is("The file is larger than 1 MB"));

        given().auth().oauth2(bobToken).get("/api/requests").then().body("$", empty());
        assertEquals(0, storedFileCount());
    }

    @Test
    void usersOnlySeeTheirOwnRequests() {
        PrintRequestDto bobs = data.printRequest(bob, RequestStatus.SUBMITTED);

        given().auth().oauth2(eveToken).get("/api/requests").then().statusCode(200).body("$", empty());
        given().auth().oauth2(eveToken).get("/api/requests/" + bobs.id()).then().statusCode(404);
        given().auth().oauth2(bobToken).get("/api/requests/" + bobs.id()).then().statusCode(200).body("name", is("Benchy"));
        given().auth().oauth2(adminToken).get("/api/requests/" + bobs.id())
                .then().statusCode(200).body("ownerUsername", is("bob"));
        given().auth().oauth2(adminToken).get("/api/requests").then().body("$", empty());
    }

    @Test
    void unknownRequestIsNotFound() {
        given().auth().oauth2(adminToken).get("/api/requests/999").then().statusCode(404);
    }

    @Test
    void ownerAndAdminCanDownloadOthersCannot() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        String url = "/api/requests/" + r.id() + "/files/" + r.files().getFirst().id();

        byte[] body = given().auth().oauth2(bobToken).get(url)
                .then().statusCode(200)
                .contentType("model/stl")
                .header("Content-Disposition", is("attachment; filename=\"benchy.stl\"; filename*=UTF-8''benchy.stl"))
                .extract().asByteArray();
        assertArrayEquals(ModelFiles.binaryStl(1), body);

        given().auth().oauth2(adminToken).get(url).then().statusCode(200);
        given().auth().oauth2(eveToken).get(url).then().statusCode(404);
        given().get(url).then().statusCode(401);
        given().auth().oauth2(bobToken).get("/api/requests/" + r.id() + "/files/999").then().statusCode(404)
                .contentType(ContentType.JSON).body("message", is("File not found"));
    }

    @Test
    void ownerDeletesSubmittedRequest() throws IOException {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        assertEquals(1, storedFileCount());

        given().auth().oauth2(eveToken).delete("/api/requests/" + r.id()).then().statusCode(404);
        given().auth().oauth2(bobToken).delete("/api/requests/" + r.id()).then().statusCode(204);

        given().auth().oauth2(bobToken).get("/api/requests/" + r.id()).then().statusCode(404);
        assertEquals(0, storedFileCount());
    }

    @Test
    void ownerCannotDeleteAfterSubmittedButAdminCan() throws IOException {
        PrintRequestDto printing = data.printRequest(bob, RequestStatus.PRINTING);
        given().auth().oauth2(bobToken).delete("/api/requests/" + printing.id()).then().statusCode(409);
        given().auth().oauth2(adminToken).delete("/api/requests/" + printing.id()).then().statusCode(204);

        PrintRequestDto r = data.printRequest(bob, RequestStatus.ACCEPTED);

        given().auth().oauth2(bobToken).delete("/api/requests/" + r.id())
                .then().statusCode(409).body("message", is("Only submitted requests can be deleted"));
        given().auth().oauth2(adminToken).delete("/api/requests/" + r.id()).then().statusCode(204);
        assertEquals(0, storedFileCount());
    }

    @Test
    void deletingUserRemovesTheirRequestsAndFiles() throws IOException {
        data.printRequest(bob, RequestStatus.SUBMITTED);
        data.printRequest(bob, RequestStatus.COMPLETED);
        assertEquals(2, storedFileCount());

        given().auth().oauth2(adminToken).delete("/api/users/" + bob.id).then().statusCode(204);

        assertEquals(0, storedFileCount());
    }

    @Test
    void anonymousIsUnauthorized() {
        given().get("/api/requests").then().statusCode(401);
        given().get("/api/requests/upload-config").then().statusCode(401);
    }

    @Test
    void contentDispositionEscapesUnsafeCharacters() {
        assertEquals("attachment; filename=\"_a_ b_.stl\"; filename*=UTF-8''%22a%5C%20b%C3%A9.stl",
                PrintRequestResource.contentDisposition("\"a\\ bé.stl"));
    }

    // --- several files (#23) ---

    private ValidatableResponse addFile(String token, long requestId, String filename, byte[] content,
                                        Integer quantity) {
        var spec = given().auth().oauth2(token).multiPart("file", filename, content, "application/octet-stream");
        if (quantity != null) {
            spec = spec.multiPart("quantity", String.valueOf(quantity));
        }
        return spec.post("/api/requests/" + requestId + "/files").then();
    }

    private ValidatableResponse changeQuantity(String token, long requestId, long fileId, Object quantity) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("quantity", quantity))
                .put("/api/requests/" + requestId + "/files/" + fileId + "/quantity").then();
    }

    @Test
    void createsRequestWithQuantityThenAddsFiles() throws IOException {
        long id = given().auth().oauth2(bobToken)
                .multiPart("name", "Box set")
                .multiPart("quantity", "2")
                .multiPart("file", "box.stl", ModelFiles.binaryStl(1), "application/octet-stream")
                .post("/api/requests")
                .then().statusCode(201)
                .body("files[0].quantity", is(2))
                .extract().jsonPath().getLong("id");

        addFile(bobToken, id, "lid.stl", ModelFiles.binaryStl(2), 3)
                .statusCode(201)
                .body("files.originalFilename", contains("box.stl", "lid.stl"))
                .body("files.quantity", contains(2, 3))
                .body("totals.files", is(2))
                .body("totals.copies", is(5))
                .body("totals.grams", notNullValue())
                .body("totals.weight", is("ESTIMATED"));
        addFile(bobToken, id, "insert.3mf", ModelFiles.threeMf(), null)
                .statusCode(201)
                .body("files[2].quantity", is(1))
                .body("files[2].modelInfo.status", not("PENDING"));
        assertEquals(3, storedFileCount());
    }

    @Test
    void refusesInvalidExtraFiles() throws IOException {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);

        addFile(bobToken, r.id(), "a.obj", ModelFiles.binaryStl(1), null).statusCode(400);
        addFile(bobToken, r.id(), "huge.stl", ModelFiles.binaryStl(21_000), null)
                .statusCode(400).body("message", is("The file is larger than 1 MB"));
        addFile(bobToken, r.id(), "a.stl", ModelFiles.binaryStl(1), 0).statusCode(400);
        addFile(bobToken, r.id(), "a.stl", ModelFiles.binaryStl(1), 101).statusCode(400);
        given().auth().oauth2(bobToken).multiPart("quantity", "1").post("/api/requests/" + r.id() + "/files")
                .then().statusCode(400);
        addFile(eveToken, r.id(), "a.stl", ModelFiles.binaryStl(1), null).statusCode(404);
        assertEquals(1, storedFileCount());
    }

    @Test
    void fileLimitFromTheSettingsApplies() {
        data.setUploadLimits(2, null);
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        given().auth().oauth2(bobToken).get("/api/requests/upload-config").then().body("maxFiles", is(2));

        addFile(bobToken, r.id(), "a.stl", ModelFiles.binaryStl(1), null).statusCode(201);
        addFile(bobToken, r.id(), "b.stl", ModelFiles.binaryStl(1), null)
                .statusCode(400).body("message", is("A request can hold at most 2 files and links"));
    }

    @Test
    void ownerChangesFilesOnlyWhileSubmittedAdminAlways() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        long first = r.files().getFirst().id();
        long second = addFile(bobToken, r.id(), "lid.stl", ModelFiles.binaryStl(1), null)
                .extract().jsonPath().getLong("files[1].id");
        changeQuantity(bobToken, r.id(), first, 4).statusCode(200).body("files[0].quantity", is(4));

        data.setStatus(r.id(), RequestStatus.IN_REVIEW);
        addFile(bobToken, r.id(), "c.stl", ModelFiles.binaryStl(1), null).statusCode(409);
        given().auth().oauth2(bobToken).delete("/api/requests/" + r.id() + "/files/" + second).then().statusCode(409);
        changeQuantity(bobToken, r.id(), first, 5).statusCode(409);

        changeQuantity(adminToken, r.id(), first, 6).statusCode(200).body("files[0].quantity", is(6));
        data.setStatus(r.id(), RequestStatus.COMPLETED);
        changeQuantity(adminToken, r.id(), first, 7).statusCode(409);
        addFile(adminToken, r.id(), "c.stl", ModelFiles.binaryStl(1), null).statusCode(201);
        given().auth().oauth2(adminToken).delete("/api/requests/" + r.id() + "/files/" + second)
                .then().statusCode(200).body("files.originalFilename", contains("benchy.stl", "c.stl"));
    }

    @Test
    void quantityMustBeBetweenOneAndHundred() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        long file = r.files().getFirst().id();
        changeQuantity(bobToken, r.id(), file, 0).statusCode(400);
        changeQuantity(bobToken, r.id(), file, 101).statusCode(400);
        changeQuantity(bobToken, r.id(), file, 100).statusCode(200);
        changeQuantity(eveToken, r.id(), file, 2).statusCode(404);
        changeQuantity(bobToken, r.id(), 999, 2).statusCode(404);
    }

    private ValidatableResponse changeDetails(String token, long requestId, String name, String description) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .put("/api/requests/" + requestId).then();
    }

    @Test
    void ownerChangesNameAndDescriptionWhileSubmitted() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        changeDetails(bobToken, r.id(), "  Big benchy ", "In blue, please")
                .statusCode(200)
                .body("name", is("Big benchy"))
                .body("description", is("In blue, please"))
                .body("updatedAt", not(is(r.updatedAt().toString())));
        given().auth().oauth2(bobToken).get("/api/requests").then().body("[0].name", is("Big benchy"));

        // A blank description clears it.
        changeDetails(bobToken, r.id(), "Big benchy", "   ").statusCode(200).body("description", nullValue());
        changeDetails(bobToken, r.id(), "Big benchy", null).statusCode(200).body("description", nullValue());

        data.setStatus(r.id(), RequestStatus.IN_REVIEW);
        changeDetails(bobToken, r.id(), "Too late", null).statusCode(409)
                .body("message", is("The name and description can only be changed while the request is submitted"));
        changeDetails(eveToken, r.id(), "Not mine", null).statusCode(404);
    }

    @Test
    void adminChangesDetailsUntilTheRequestIsFinal() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.PRINTING);
        changeDetails(adminToken, r.id(), "Renamed", "By the owner").statusCode(200).body("name", is("Renamed"));

        data.setStatus(r.id(), RequestStatus.COMPLETED);
        changeDetails(adminToken, r.id(), "Again", null).statusCode(409);
        data.setStatus(r.id(), RequestStatus.DECLINED);
        changeDetails(adminToken, r.id(), "Again", null).statusCode(409);
        changeDetails(adminToken, 999, "Missing", null).statusCode(404);
    }

    @Test
    void detailsFollowTheRulesOfANewRequest() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        changeDetails(bobToken, r.id(), "  ", null).statusCode(400)
                .body("violations[0].message", is("Name is required"));
        changeDetails(bobToken, r.id(), null, null).statusCode(400);
        changeDetails(bobToken, r.id(), "x".repeat(101), null).statusCode(400)
                .body("violations[0].message", is("Name must be at most 100 characters"));
        changeDetails(bobToken, r.id(), "Ok", "x".repeat(2001)).statusCode(400)
                .body("violations[0].message", is("Description must be at most 2000 characters"));
        changeDetails(bobToken, r.id(), "x".repeat(100), "x".repeat(2000)).statusCode(200);
        given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .put("/api/requests/" + r.id()).then().statusCode(400);
    }

    @Test
    void removedFileLeavesTheDiskButTheLastOneStays() throws IOException {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        long first = r.files().getFirst().id();
        long second = addFile(bobToken, r.id(), "lid.stl", ModelFiles.binaryStl(1), null)
                .extract().jsonPath().getLong("files[1].id");
        assertEquals(2, storedFileCount());

        given().auth().oauth2(eveToken).delete("/api/requests/" + r.id() + "/files/" + first).then().statusCode(404);
        given().auth().oauth2(bobToken).delete("/api/requests/" + r.id() + "/files/" + first)
                .then().statusCode(200).body("files.id", contains((int) second));
        assertEquals(1, storedFileCount());

        given().auth().oauth2(bobToken).delete("/api/requests/" + r.id() + "/files/" + second)
                .then().statusCode(409)
                .body("message", is("A request needs at least one file or link; delete the request instead"));
        assertEquals(1, storedFileCount());
    }

    @Test
    void downloadsAllFilesAsZip() throws IOException {
        PrintRequestDto r = data.printRequest(bob, "Box set", RequestStatus.SUBMITTED);
        addFile(bobToken, r.id(), "benchy.stl", ModelFiles.binaryStl(2), null).statusCode(201);
        String url = "/api/requests/" + r.id() + "/files.zip";

        byte[] zip = given().auth().oauth2(bobToken).get(url)
                .then().statusCode(200)
                .contentType("application/zip")
                .header("Content-Disposition", startsWith("attachment; filename=\"Box set.zip\""))
                .extract().asByteArray();

        List<String> names = new ArrayList<>();
        List<byte[]> contents = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                names.add(entry.getName());
                contents.add(in.readAllBytes());
                entry = in.getNextEntry();
            }
        }
        assertEquals(List.of("benchy.stl", "benchy (2).stl"), names);
        assertArrayEquals(ModelFiles.binaryStl(1), contents.get(0));
        assertArrayEquals(ModelFiles.binaryStl(2), contents.get(1));

        given().auth().oauth2(adminToken).get(url).then().statusCode(200);
        given().auth().oauth2(eveToken).get(url).then().statusCode(404).contentType(ContentType.JSON);
    }

    // --- links (#59) ---

    private ValidatableResponse createWithLink(String token, String url, String linkName) {
        var spec = given().auth().oauth2(token).multiPart("name", "Dragon").multiPart("url", url);
        if (linkName != null) {
            spec = spec.multiPart("linkName", linkName);
        }
        return spec.post("/api/requests").then();
    }

    private ValidatableResponse addLink(String token, long requestId, Map<String, Object> body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .post("/api/requests/" + requestId + "/links").then();
    }

    @Test
    void createsRequestWithOnlyALink() throws IOException {
        var created = createWithLink(bobToken, "https://www.patreon.com/posts/dragon-123", "Dragon (tier 2)")
                .statusCode(201)
                .body("files[0].kind", is("LINK"))
                .body("files[0].url", is("https://www.patreon.com/posts/dragon-123"))
                .body("files[0].linkName", is("Dragon (tier 2)"))
                .body("files[0].originalFilename", nullValue())
                .body("files[0].type", nullValue())
                .body("files[0].sizeBytes", nullValue())
                .body("files[0].modelInfo", nullValue())
                .body("totals.files", is(0))
                .body("totals.links", is(1))
                .body("totals.copies", is(1))
                .body("totals.grams", nullValue())
                .extract().jsonPath();
        assertEquals(0, storedFileCount());

        String file = "/api/requests/" + created.getLong("id") + "/files/" + created.getLong("files[0].id");
        given().auth().oauth2(bobToken).get(file).then().statusCode(404);
        given().auth().oauth2(bobToken).get(file + "/preview").then().statusCode(404);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("printerId", 1, "nozzle", 0.4, "process", "0.20mm Standard", "filament", "PLA Basic"))
                .post(file + "/reslice").then().statusCode(409);
    }

    @Test
    void createNeedsExactlyAFileOrALink() throws IOException {
        upload(bobToken, "Nothing", null, null).statusCode(400).body("message", is("Add a model file or a link"));
        given().auth().oauth2(bobToken).multiPart("name", "Both")
                .multiPart("url", "https://example.com/a")
                .multiPart("file", "a.stl", ModelFiles.binaryStl(1), "application/octet-stream")
                .post("/api/requests").then().statusCode(400).body("message", is("Add a model file or a link"));
        createWithLink(bobToken, "ftp://example.com/a.stl", null).statusCode(400);
        createWithLink(bobToken, "javascript:alert(1)", null).statusCode(400);
        createWithLink(bobToken, "https://example.com/" + "a".repeat(2048), null).statusCode(400);
        createWithLink(bobToken, "https://example.com/a", "n".repeat(101)).statusCode(400);
        createWithLink(bobToken, "https:///nohost", null)
                .statusCode(400).body("message", is("The link is not a valid web address"));

        given().auth().oauth2(bobToken).get("/api/requests").then().body("$", empty());
        assertEquals(0, storedFileCount());
    }

    @Test
    void addsLinksNextToFilesAndTheyShareTheLimit() {
        data.setUploadLimits(3, null);
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);

        addLink(bobToken, r.id(), Map.of("url", "https://www.printables.com/model/1", "quantity", 2))
                .statusCode(201)
                .body("files.kind", contains("FILE", "LINK"))
                .body("files[1].linkName", nullValue())
                .body("totals.files", is(1))
                .body("totals.links", is(1))
                .body("totals.copies", is(3))
                .body("totals.weight", is("ESTIMATED"));
        addFile(bobToken, r.id(), "lid.stl", ModelFiles.binaryStl(1), null).statusCode(201);
        addLink(bobToken, r.id(), Map.of("url", "https://example.com/b"))
                .statusCode(400).body("message", is("A request can hold at most 3 files and links"));
    }

    @Test
    void refusesInvalidLinks() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        addLink(bobToken, r.id(), Map.of("url", "")).statusCode(400);
        addLink(bobToken, r.id(), Map.of("name", "No url")).statusCode(400);
        addLink(bobToken, r.id(), Map.of("url", "mailto:bob@example.com")).statusCode(400);
        addLink(bobToken, r.id(), Map.of("url", "https://example.com", "name", "n".repeat(101))).statusCode(400);
        addLink(bobToken, r.id(), Map.of("url", "https://example.com", "quantity", 0)).statusCode(400);
        addLink(bobToken, r.id(), Map.of("url", "https://example.com", "quantity", 101)).statusCode(400);
        addLink(eveToken, r.id(), Map.of("url", "https://example.com")).statusCode(404);
        given().auth().oauth2(bobToken).get("/api/requests/" + r.id()).then().body("files", hasSize(1));
    }

    @Test
    void ownerChangesLinksOnlyWhileSubmittedAdminAlways() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        long link = addLink(bobToken, r.id(), Map.of("url", "https://example.com/a"))
                .extract().jsonPath().getLong("files[1].id");
        changeQuantity(bobToken, r.id(), link, 4).statusCode(200).body("files[1].quantity", is(4));

        data.setStatus(r.id(), RequestStatus.IN_REVIEW);
        addLink(bobToken, r.id(), Map.of("url", "https://example.com/b")).statusCode(409);
        given().auth().oauth2(bobToken).delete("/api/requests/" + r.id() + "/files/" + link).then().statusCode(409);

        addLink(adminToken, r.id(), Map.of("url", "https://example.com/b")).statusCode(201);
        given().auth().oauth2(adminToken).delete("/api/requests/" + r.id() + "/files/" + link)
                .then().statusCode(200).body("files.kind", contains("FILE", "LINK"));
    }

    @Test
    void theLastLinkStaysWhenTheFileIsRemoved() throws IOException {
        var created = createWithLink(bobToken, "https://example.com/a", null).extract().jsonPath();
        long id = created.getLong("id");
        long link = created.getLong("files[0].id");
        long file = addFile(bobToken, id, "lid.stl", ModelFiles.binaryStl(1), null)
                .extract().jsonPath().getLong("files[1].id");

        given().auth().oauth2(bobToken).delete("/api/requests/" + id + "/files/" + file).then().statusCode(200);
        assertEquals(0, storedFileCount());
        given().auth().oauth2(bobToken).delete("/api/requests/" + id + "/files/" + link).then().statusCode(409);
    }

    @Test
    void zipHoldsTheLinksInATextFile() throws IOException {
        PrintRequestDto r = data.printRequest(bob, "Box set", RequestStatus.SUBMITTED);
        addLink(bobToken, r.id(), Map.of("url", "https://www.patreon.com/posts/dragon-123", "name", "Dragon",
                "quantity", 2)).statusCode(201);

        byte[] zip = given().auth().oauth2(bobToken).get("/api/requests/" + r.id() + "/files.zip")
                .then().statusCode(200).extract().asByteArray();

        Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                entry = in.getNextEntry();
            }
        }
        assertEquals(List.of("benchy.stl", "links.txt"), List.copyOf(entries.keySet()));
        assertTrue(entries.get("links.txt").contains("Dragon (quantity: 2)\nhttps://www.patreon.com/posts/dragon-123\n"));
    }
}
