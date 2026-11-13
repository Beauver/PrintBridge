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
import java.util.stream.Stream;

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
                .body("files[0].sizeBytes", is(184));
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
}
