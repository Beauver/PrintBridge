package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.client.SliceResponse;
import com.beauver.printbridge.slicing.client.SlicerException;
import com.beauver.printbridge.support.FakeSlicerClient;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.beauver.printbridge.support.ThreeMfFiles.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Server slicing end to end, with {@link FakeSlicerClient} instead of the slicer service. */
@QuarkusTest
class SlicingResourceTest {

    @Inject
    TestData data;

    @Inject
    FakeSlicerClient slicer;

    @Inject
    SliceWorker worker;

    @Inject
    SlicingService service;

    @Inject
    EntityManager em;

    User bob;
    String bobToken;
    String adminToken;
    boolean printerAdded;

    @BeforeEach
    void setUp() {
        data.reset();
        slicer.reset();
        bob = data.user("bob");
        bobToken = data.accessToken(bob);
        adminToken = data.accessToken(data.admin());
        printerAdded = false;
    }

    private void addPrinter(Map<String, Object> printer) {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(printer)
                .post("/api/settings/printers").then().statusCode(201);
    }

    /** Switching on adds a P1S (owned 0.2 + 0.4) first when there is no printer yet. */
    private void slicing(boolean enabled) {
        if (enabled && !printerAdded) {
            addPrinter(TestData.printer("Bambu Lab P1S"));
            printerAdded = true;
        }
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(TestData.slicingSettings(enabled))
                .put("/api/settings/slicing").then().statusCode(200);
    }

    private int upload(String filename, byte[] content) {
        return given().auth().oauth2(bobToken)
                .multiPart("name", "Model")
                .multiPart("file", filename, content, "application/octet-stream")
                .post("/api/requests").then().statusCode(201).extract().path("id");
    }

    private ValidatableResponse get(String token, int id) {
        return given().auth().oauth2(token).get("/api/requests/" + id).then().statusCode(200);
    }

    private ValidatableResponse reslice(String token, int id) {
        return given().auth().oauth2(token).post("/api/requests/" + id + "/reslice").then();
    }

    /** A one-plate Bambu project saved for this nozzle and printer, with 2 filament slots. */
    private static byte[] project(String nozzle, String printer) {
        String modelSettings = "<config><object id=\"1\"><metadata key=\"extruder\" value=\"1\"/></object>"
                + "<plate><metadata key=\"plater_id\" value=\"1\"/>"
                + "<model_instance><metadata key=\"object_id\" value=\"1\"/></model_instance></plate></config>";
        String projectSettings = "{\"nozzle_diameter\": [\"" + nozzle + "\"], \"printer_model\": \"" + printer + "\","
                + " \"wall_loops\": \"4\", \"filament_type\": [\"PLA\", \"PETG\"],"
                + " \"filament_colour\": [\"#FFFFFF\", \"#FF0000\"]}";
        return ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 20), item(1, null)))
                .entry("Metadata/model_settings.config", modelSettings)
                .entry("Metadata/project_settings.config", projectSettings)
                .build();
    }

    @Test
    void nothingIsQueuedWhileSlicingIsOff() {
        int id = upload("cube.stl", ModelFiles.cubeStl(10));

        get(bobToken, id).body("files[0].modelInfo.slicing.enabled", is(false))
                .body("files[0].modelInfo.slicing.status", nullValue());
        assertEquals(0, worker.drain());
        reslice(adminToken, id).statusCode(409).body("message", is("Server slicing is off"));
    }

    @Test
    void slicesAnStlWithTheAdminsProfiles() {
        slicing(true);
        int id = upload("cube.stl", ModelFiles.cubeStl(10));
        get(bobToken, id).body("files[0].modelInfo.slicing.status", is("QUEUED"))
                .body("files[0].modelInfo.weight", is("ESTIMATED"));

        assertEquals(1, worker.drain());

        get(bobToken, id)
                .body("files[0].modelInfo.slicing.status", is("DONE"))
                .body("files[0].modelInfo.slicing.printer", is("Bambu Lab P1S"))
                .body("files[0].modelInfo.slicing.nozzle", is(0.4f))
                .body("files[0].modelInfo.totalGrams", is(21.0f))
                .body("files[0].modelInfo.weight", is("EXACT"))
                .body("files[0].modelInfo.printSeconds", is(600))
                .body("files[0].modelInfo.plates[0].filaments[0].color", is("#00FF00"));
        SlicePlan plan = slicer.plans.getFirst();
        assertEquals("stl", plan.format());
        assertEquals("0.20mm Standard @BBL X1C", plan.process());
        assertEquals("Bambu PLA Basic @BBL X1C", plan.filament());
        assertTrue(plan.arrange());
    }

    /** In production the worker runs on its own thread, without a request context or transaction. */
    @Test
    void workerWorksOnABackgroundThread() throws Exception {
        slicing(true);
        int id = upload("cube.stl", ModelFiles.cubeStl(10));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertEquals(1, executor.submit(worker::drain).get());
        }

        get(bobToken, id).body("files[0].modelInfo.slicing.status", is("DONE"));
    }

    @Test
    void projectForAnOwnedNozzleOnTheSamePrinterKeepsItsSettings() {
        slicing(true);
        upload("project.3mf", project("0.2", "Bambu Lab P1S"));

        worker.drain();

        SlicePlan plan = slicer.plans.getFirst();
        assertEquals(new BigDecimal("0.2"), plan.nozzle());
        assertTrue(plan.projectPrinter());
        assertNull(plan.process());
        assertFalse(plan.arrange());
    }

    @Test
    void projectForAnotherOwnedPrinterIsSlicedOnIt() {
        Map<String, Object> mini = TestData.printer("Bambu Lab A1 mini", 180, 180, 180);
        mini.put("ownedNozzles", List.of(0.2));
        mini.put("defaultNozzle", 0.2);
        mini.put("process", "0.10mm Detail @A1M");
        mini.put("filament", "PLA @A1M");
        addPrinter(mini);
        slicing(true);
        int id = upload("project.3mf", project("0.2", "Bambu Lab A1 mini"));

        worker.drain();

        SlicePlan plan = slicer.plans.getFirst();
        assertEquals("Bambu Lab A1 mini", plan.printer());
        assertTrue(plan.projectPrinter());
        get(bobToken, id)
                .body("files[0].modelInfo.slicing.printer", is("Bambu Lab A1 mini"))
                .body("files[0].modelInfo.fitPrinter", is("Bambu Lab A1 mini"));
    }

    @Test
    void projectForAnotherPrinterUsesTheDefaultsAndShowsWhatItWasMadeFor() {
        slicing(true);
        int id = upload("project.3mf", project("0.8", "Bambu Lab X1 Carbon"));

        worker.drain();

        SlicePlan plan = slicer.plans.getFirst();
        assertEquals(new BigDecimal("0.4"), plan.nozzle());
        assertEquals("0.20mm Standard @BBL X1C", plan.process());
        assertEquals(2, plan.filamentSlots());
        assertEquals(Map.of("wall_loops", "4"), plan.overrides());
        assertTrue(plan.arrange());
        get(bobToken, id)
                .body("files[0].modelInfo.slicing.madeForNozzle", is(0.8f))
                .body("files[0].modelInfo.slicing.madeForPrinter", is("Bambu Lab X1 Carbon"))
                .body("files[0].modelInfo.slicing.printer", is("Bambu Lab P1S"))
                .body("files[0].modelInfo.plates[0].filaments[0].color", is("#FFFFFF"))
                .body("files[0].modelInfo.plates[0].filaments[0].grams", nullValue());
    }

    @Test
    void refusedPlateFallsBackToItsEstimateAndOnlyTheAdminSeesWhy() {
        slicing(true);
        slicer.answer = plan -> new SliceResponse(List.of(new SliceResponse.Plate(1, null, null, null,
                "Object conflicts were detected")));
        int id = upload("cube.stl", ModelFiles.cubeStl(10));

        worker.drain();

        get(bobToken, id)
                .body("files[0].modelInfo.slicing.status", is("DONE"))
                .body("files[0].modelInfo.weight", is("ESTIMATED"))
                .body("files[0].modelInfo.plates[0].estimated", is(true))
                .body("files[0].modelInfo.plates[0].sliceError", nullValue());
        get(adminToken, id).body("files[0].modelInfo.plates[0].sliceError", is("Object conflicts were detected"));
    }

    @Test
    void failedJobShowsTheFallbackAndOnlyTheAdminSeesWhy() {
        slicing(true);
        slicer.failure = FakeSlicerClient.unreachable();
        int id = upload("cube.stl", ModelFiles.cubeStl(10));

        worker.drain();

        get(bobToken, id)
                .body("files[0].modelInfo.slicing.status", is("FAILED"))
                .body("files[0].modelInfo.slicing.error", nullValue())
                .body("files[0].modelInfo.weight", is("ESTIMATED"));
        get(adminToken, id).body("files[0].modelInfo.slicing.error", is("The slicer can't be reached"));
    }

    @Test
    void unexpectedErrorsFailTheJobToo() {
        slicing(true);
        slicer.failure = new IllegalStateException("boom");
        int id = upload("cube.stl", ModelFiles.cubeStl(10));

        worker.drain();

        get(adminToken, id).body("files[0].modelInfo.slicing.error", is("Unexpected error while slicing"));
    }

    @Test
    void adminReslicesButNotWhileQueued() {
        slicing(true);
        int id = upload("cube.stl", ModelFiles.cubeStl(10));
        reslice(adminToken, id).statusCode(409).body("message", is("This file is already being sliced"));
        worker.drain();

        reslice(adminToken, id).statusCode(200).body("files[0].modelInfo.slicing.status", is("QUEUED"));
        assertEquals(1, worker.drain());
        assertEquals(2, slicer.plans.size());

        reslice(bobToken, id).statusCode(403);
        reslice(adminToken, 999).statusCode(404);
    }

    @Test
    void switchingSlicingOffDropsQueuedJobs() {
        slicing(true);
        int id = upload("cube.stl", ModelFiles.cubeStl(10));

        slicing(false);

        get(bobToken, id).body("files[0].modelInfo.slicing.status", nullValue());
        assertEquals(0, worker.drain());
        slicing(true);
        assertEquals(0, worker.drain());
    }

    @Test
    void interruptedJobsGoBackIntoTheQueue() {
        slicing(true);
        int id = upload("cube.stl", ModelFiles.cubeStl(10));
        setStatus(SliceStatus.RUNNING);

        service.requeueInterrupted();
        service.requeueInterrupted();

        get(bobToken, id).body("files[0].modelInfo.slicing.status", is("QUEUED"));
    }

    @Test
    void missingFilesAndMissingDetailsFailRightAway() throws IOException {
        slicing(true);
        int stl = upload("cube.stl", ModelFiles.cubeStl(10));
        int project = upload("project.3mf", project("0.4", "Bambu Lab P1S"));
        try (Stream<Path> files = Files.walk(data.storageRoot().resolve(String.valueOf(stl)))) {
            files.filter(Files::isRegularFile).forEach(path -> path.toFile().delete());
        }
        deleteModelInfo();

        assertEquals(0, worker.drain());

        get(adminToken, stl).body("files[0].modelInfo.slicing.error", is("The model file is missing on disk"));
        get(adminToken, project).body("files[0].modelInfo.slicing.error",
                startsWith("Model details aren't available yet"));
    }

    @Test
    void resultsForDeletedFilesAreIgnored() {
        service.complete(12345, slicer.plans.isEmpty() ? null : slicer.plans.getFirst(), new SliceResponse(List.of()));
        service.fail(12345, "gone");
        assertTrue(service.find(12345).isEmpty());
    }

    @Test
    void slicerEndpointsForTheSettingsPage() {
        given().auth().oauth2(adminToken).get("/api/slicer/status").then().statusCode(200)
                .body("configured", is(true)).body("reachable", is(true)).body("orcaVersion", is("2.5.0"));
        given().auth().oauth2(adminToken).get("/api/slicer/printers").then().statusCode(200)
                .body("[0].nozzles[0].bed.x", is(256));
        given().auth().oauth2(adminToken).queryParam("printer", "Bambu Lab P1S").queryParam("nozzle", "0.4")
                .get("/api/slicer/profiles").then().statusCode(200)
                .body("machine", is("Bambu Lab P1S 0.4 nozzle")).body("processes", contains("0.20mm Standard"));
        given().auth().oauth2(adminToken).queryParam("nozzle", "9").get("/api/slicer/profiles")
                .then().statusCode(400);

        slicer.failure = new SlicerException("The slicer can't be reached");
        given().auth().oauth2(adminToken).get("/api/slicer/status").then().statusCode(200)
                .body("reachable", is(false)).body("message", is("The slicer can't be reached"));
        given().auth().oauth2(adminToken).get("/api/slicer/printers").then().statusCode(503)
                .body("message", is("The slicer can't be reached"));

        slicer.configured = false;
        given().auth().oauth2(adminToken).get("/api/slicer/status").then().statusCode(200)
                .body("configured", is(false));

        given().auth().oauth2(bobToken).get("/api/slicer/status").then().statusCode(403);
    }

    @Transactional
    void setStatus(SliceStatus status) {
        em.createQuery("update SliceResult set status = :status").setParameter("status", status).executeUpdate();
    }

    @Transactional
    void deleteModelInfo() {
        em.createQuery("delete from ModelInfo").executeUpdate();
    }
}
