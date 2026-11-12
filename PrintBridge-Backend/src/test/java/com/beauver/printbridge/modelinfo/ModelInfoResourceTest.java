package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.printrequest.RequestStatus;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.support.ThreeMfFiles;
import com.beauver.printbridge.user.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

import static com.beauver.printbridge.support.ThreeMfFiles.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Model info as it reaches the detail page: analysis on upload, fit check and the startup backfill. */
@QuarkusTest
class ModelInfoResourceTest {

    @Inject
    TestData data;

    @Inject
    ModelInfoBackfill backfill;

    @Inject
    EntityManager em;

    User bob;
    String bobToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        data.reset();
        bob = data.user("bob");
        bobToken = data.accessToken(bob);
        adminToken = data.accessToken(data.admin());
    }

    private ValidatableResponse upload(String filename, byte[] content) {
        return given().auth().oauth2(bobToken)
                .multiPart("name", "Model")
                .multiPart("file", filename, content, "application/octet-stream")
                .post("/api/requests").then().statusCode(201);
    }

    private void setBuildPlate(int x, int y, int z) {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("x", x, "y", y, "z", z))
                .put("/api/settings/build-plate").then().statusCode(200);
    }

    @Test
    void analysesStlOnUpload() {
        upload("cube.stl", ModelFiles.cubeStl(10))
                .body("files[0].modelInfo.status", is("ANALYZED"))
                .body("files[0].modelInfo.source", is("MESH"))
                .body("files[0].modelInfo.volumeCm3", is(1.0f))
                .body("files[0].modelInfo.totalGrams", is(0.0f))
                .body("files[0].modelInfo.weight", is("ESTIMATED"))
                .body("files[0].modelInfo.slicing.enabled", is(false))
                .body("files[0].modelInfo.fits", nullValue())
                .body("files[0].modelInfo.plates", hasSize(1))
                .body("files[0].modelInfo.plates[0].width", is(10.0f))
                .body("files[0].modelInfo.plates[0].height", is(10.0f));
    }

    @Test
    void storesBambuPlatesAndReadsThemBack() {
        String modelSettings = "<config><object id=\"1\"><metadata key=\"extruder\" value=\"1\"/></object>"
                + "<plate><metadata key=\"plater_id\" value=\"1\"/><metadata key=\"plater_name\" value=\"Lid\"/>"
                + "<model_instance><metadata key=\"object_id\" value=\"1\"/></model_instance></plate></config>";
        String sliceInfo = "<config><plate><metadata key=\"index\" value=\"1\"/>"
                + "<metadata key=\"prediction\" value=\"600\"/><metadata key=\"weight\" value=\"12.5\"/>"
                + "<filament type=\"PLA\" color=\"#FF0000\" used_g=\"12.5\"/></plate></config>";
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 100), item(1, null)))
                .entry("Metadata/model_settings.config", modelSettings)
                .entry("Metadata/slice_info.config", sliceInfo)
                .build();

        int id = upload("lid.3mf", file).extract().path("id");

        given().auth().oauth2(bobToken).get("/api/requests/" + id).then().statusCode(200)
                .body("files[0].modelInfo.source", is("BAMBU"))
                .body("files[0].modelInfo.totalGrams", is(12.5f))
                .body("files[0].modelInfo.weight", is("EXACT"))
                .body("files[0].modelInfo.printSeconds", is(600))
                .body("files[0].modelInfo.plates[0].name", is("Lid"))
                .body("files[0].modelInfo.plates[0].filaments[0].color", is("#FF0000"))
                .body("files[0].modelInfo.plates[0].filaments[0].grams", is(12.5f));
    }

    @Test
    void unreadableModelDoesNotBlockTheUpload() {
        upload("empty.3mf", ModelFiles.threeMf())
                .body("status", is("SUBMITTED"))
                .body("files[0].modelInfo.status", is("FAILED"))
                .body("files[0].modelInfo.plates", empty());
    }

    @Test
    void fitCheckFollowsTheCurrentBuildPlate() {
        int id = upload("cube.stl", ModelFiles.cubeStl(200)).extract().path("id");

        setBuildPlate(256, 256, 250);
        given().auth().oauth2(bobToken).get("/api/requests/" + id).then()
                .body("files[0].modelInfo.fits", is(true))
                .body("files[0].modelInfo.plates[0].fits", is(true));

        setBuildPlate(180, 180, 180);
        given().auth().oauth2(bobToken).get("/api/requests/" + id).then()
                .body("files[0].modelInfo.fits", is(false))
                .body("files[0].modelInfo.plates[0].fits", is(false));
    }

    @Test
    void backfillAnalysesOldFilesOnceAndSkipsFailedOnes() throws Exception {
        long okId = data.printRequest(bob, RequestStatus.SUBMITTED).id();
        long missingId = data.printRequest(bob, "Gone", RequestStatus.SUBMITTED).id();
        deleteAllModelInfo();
        try (var files = Files.walk(data.storageRoot().resolve(String.valueOf(missingId)))) {
            files.filter(Files::isRegularFile).forEach(path -> path.toFile().delete());
        }

        assertEquals(2, backfill.run());

        given().auth().oauth2(bobToken).get("/api/requests/" + okId).then()
                .body("files[0].modelInfo.status", is("ANALYZED"));
        given().auth().oauth2(bobToken).get("/api/requests/" + missingId).then()
                .body("files[0].modelInfo.status", is("FAILED"));
        assertEquals(0, backfill.run());
    }

    /** In production the backfill runs on its own thread, without a request context or transaction. */
    @Test
    void backfillWorksOnABackgroundThread() throws Exception {
        long id = data.printRequest(bob, RequestStatus.SUBMITTED).id();
        deleteAllModelInfo();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertEquals(1, executor.submit(backfill::run).get());
        }

        given().auth().oauth2(bobToken).get("/api/requests/" + id).then()
                .body("files[0].modelInfo.status", is("ANALYZED"));
    }

    @Test
    void notYetAnalysedFileIsPending() {
        long id = data.printRequest(bob, RequestStatus.SUBMITTED).id();
        deleteAllModelInfo();

        given().auth().oauth2(bobToken).get("/api/requests/" + id).then()
                .body("files[0].modelInfo.status", is("PENDING"));
    }

    @Transactional
    void deleteAllModelInfo() {
        em.createQuery("delete from ModelInfo").executeUpdate();
    }
}
