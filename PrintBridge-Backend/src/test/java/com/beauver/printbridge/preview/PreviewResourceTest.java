package com.beauver.printbridge.preview;

import com.beauver.printbridge.slicing.SliceResult;
import com.beauver.printbridge.slicing.SliceStatus;
import com.beauver.printbridge.support.MeshFiles;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.support.ThreeMfFiles;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Previews of uploaded files and of files before uploading (#13). */
@QuarkusTest
class PreviewResourceTest {

    @Inject
    TestData data;

    @Inject
    EntityManager em;

    String bobToken;
    String aliceToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        data.reset();
        bobToken = data.accessToken(data.user("bob"));
        aliceToken = data.accessToken(data.user("alice"));
        adminToken = data.accessToken(data.admin());
    }

    private record Upload(int requestId, int fileId) {
    }

    private Upload upload(String filename, byte[] content) {
        ExtractableResponse<Response> created = given().auth().oauth2(bobToken)
                .multiPart("name", "Model")
                .multiPart("file", filename, content, "application/octet-stream")
                .post("/api/requests").then().statusCode(201).extract();
        return new Upload(created.path("id"), created.path("files[0].id"));
    }

    private String filePath(Upload upload) {
        return "/api/requests/" + upload.requestId() + "/files/" + upload.fileId();
    }

    private JsonNode previewHeader(String token, Upload upload) {
        byte[] body = given().auth().oauth2(token).get(filePath(upload) + "/preview")
                .then().statusCode(200)
                .contentType(MeshEncoder.MEDIA_TYPE)
                .header("Cache-Control", "private, no-store")
                .extract().asByteArray();
        // RestAssured already unpacked the gzip.
        return MeshFiles.decode(body, false).header();
    }

    private void addPrinter(String model, int size) {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(TestData.printer(model, size, size, size))
                .post("/api/settings/printers").then().statusCode(201);
    }

    @Test
    void ownerAndAdminSeeTheMeshOfAnUploadedFile() {
        Upload cube = upload("cube.stl", ModelFiles.cubeStl(10));

        JsonNode header = previewHeader(bobToken, cube);
        assertTrue(header.path("bed").isNull());
        assertEquals(1, header.path("plates").size());
        assertEquals(8, header.path("parts").get(0).path("vertices").asInt());
        assertEquals(12, header.path("parts").get(0).path("triangles").asInt());
        previewHeader(adminToken, cube);

        given().auth().oauth2(aliceToken).get(filePath(cube) + "/preview").then().statusCode(404);
        given().auth().oauth2(bobToken).get("/api/requests/" + cube.requestId() + "/files/999/preview")
                .then().statusCode(404);
        given().get(filePath(cube) + "/preview").then().statusCode(401);
    }

    @Test
    void meshSitsOnTheFitCheckPrintersBed() {
        Upload cube = upload("cube.stl", ModelFiles.cubeStl(10));
        addPrinter("Big", 300);
        addPrinter("Small", 180);

        assertEquals(300, previewHeader(bobToken, cube).path("bed").path("x").asInt());

        sliced(cube.fileId(), "Small");
        assertEquals(180, previewHeader(bobToken, cube).path("bed").path("x").asInt());
    }

    @Test
    void unreadableAndTooDetailedFilesHaveNoPreview() {
        Upload broken = upload("empty.3mf", ModelFiles.threeMf());
        given().auth().oauth2(bobToken).get(filePath(broken) + "/preview").then()
                .statusCode(422).body("message", is("No preview available for this file"));

        // The test profile allows 1000 triangles.
        Upload detailed = upload("detailed.stl", ModelFiles.binaryStl(1001));
        given().auth().oauth2(bobToken).get(filePath(detailed) + "/preview").then()
                .statusCode(422).body("message", is("This model is too detailed for a preview"));
    }

    @Test
    void servesThePlateImagesOfBambuFiles() {
        Upload project = upload("project.3mf", ThreeMfFiles.bambuProject()
                .entry("Metadata/plate_1.png", "png bytes").build());
        Upload cube = upload("cube.stl", ModelFiles.cubeStl(10));

        byte[] image = given().auth().oauth2(bobToken).get(filePath(project) + "/plates/1/thumbnail")
                .then().statusCode(200).contentType("image/png")
                .header("Cache-Control", "private, max-age=3600")
                .extract().asByteArray();
        assertEquals("png bytes", new String(image));
        given().auth().oauth2(bobToken).get(filePath(project) + "/plates/2/thumbnail").then()
                .statusCode(404).body("message", is("This plate has no image"));
        given().auth().oauth2(bobToken).get(filePath(cube) + "/plates/1/thumbnail").then().statusCode(404);
        given().auth().oauth2(aliceToken).get(filePath(project) + "/plates/1/thumbnail").then().statusCode(404);
        JsonNode header = previewHeader(bobToken, project);
        assertEquals(List.of("First"), List.of(header.path("plates").get(0).path("name").asText()));
        assertEquals("#FFFFFF", header.path("parts").get(0).path("color").asText());
    }

    @Test
    void previewsAFileBeforeUploadingWithoutStoringIt() throws Exception {
        addPrinter("Default", 256);

        byte[] body = given().auth().oauth2(aliceToken)
                .multiPart("file", "cube.stl", ModelFiles.cubeStl(10), "application/octet-stream")
                .post("/api/previews").then().statusCode(200)
                .contentType(MeshEncoder.MEDIA_TYPE)
                .extract().asByteArray();

        assertEquals(256, MeshFiles.decode(body, false).header().path("bed").path("x").asInt());
        if (Files.exists(data.storageRoot())) {
            try (Stream<Path> files = Files.walk(data.storageRoot())) {
                assertEquals(0, files.filter(Files::isRegularFile).count());
            }
        }
    }

    @Test
    void previewBeforeUploadingChecksTheFileLikeAnUpload() {
        given().auth().oauth2(aliceToken)
                .multiPart("file", "model.obj", new byte[]{1, 2, 3}, "application/octet-stream")
                .post("/api/previews").then().statusCode(400).body("message", is("Only .stl, .3mf files are allowed"));
        given().auth().oauth2(aliceToken)
                .multiPart("file", "model.stl", new byte[]{1, 2, 3}, "application/octet-stream")
                .post("/api/previews").then().statusCode(400).body("message", is("The file is not a valid STL file"));
        given().auth().oauth2(aliceToken)
                .multiPart("file", "big.stl", ModelFiles.binaryStl(25_000), "application/octet-stream")
                .post("/api/previews").then().statusCode(400).body("message", startsWith("The file is larger than"));
        given().auth().oauth2(aliceToken)
                .multiPart("file", "empty.3mf", ModelFiles.threeMf(), "application/octet-stream")
                .post("/api/previews").then().statusCode(422);
        given().auth().oauth2(aliceToken).multiPart("other", "x").post("/api/previews").then().statusCode(400);
        given().multiPart("file", "cube.stl", ModelFiles.cubeStl(10), "application/octet-stream")
                .post("/api/previews").then().statusCode(401);
    }

    @Transactional
    void sliced(long fileId, String printer) {
        SliceResult result = new SliceResult();
        result.fileId = fileId;
        result.status = SliceStatus.DONE;
        result.printer = printer;
        result.plates = List.of();
        result.queuedAt = Instant.now();
        em.persist(result);
    }
}
