package com.beauver.printbridge.slicing;

import com.beauver.printbridge.support.FakeSlicerClient;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.user.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Re-slicing one file with a chosen printer, nozzle and profiles (#21), end to end. */
@QuarkusTest
class CustomResliceResourceTest {

    /** What {@link FakeSlicerClient} offers for every printer and nozzle. */
    static final String PROCESS = "0.20mm Standard";
    static final String FILAMENT = "PLA Basic";

    @Inject
    TestData data;

    @Inject
    FakeSlicerClient slicer;

    @Inject
    SliceWorker worker;

    String bobToken;
    String adminToken;
    long p1sId;

    @BeforeEach
    void setUp() {
        data.reset();
        slicer.reset();
        User bob = data.user("bob");
        bobToken = data.accessToken(bob);
        adminToken = data.accessToken(data.admin());
        p1sId = addPrinter(TestData.printer("Bambu Lab P1S"));
        slicing(true);
    }

    private long addPrinter(Map<String, Object> printer) {
        List<Map<String, Object>> printers = given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(printer).post("/api/settings/printers").then().statusCode(201)
                .extract().path("printers");
        return ((Number) printers.getLast().get("id")).longValue();
    }

    private void slicing(boolean enabled) {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(TestData.slicingSettings(enabled))
                .put("/api/settings/slicing").then().statusCode(200);
    }

    /** Uploads a model, lets the automatic slicing run, and answers {requestId, fileId}. */
    private int[] uploaded(String filename, byte[] content) {
        int id = given().auth().oauth2(bobToken)
                .multiPart("name", "Model")
                .multiPart("file", filename, content, "application/octet-stream")
                .post("/api/requests").then().statusCode(201).extract().path("id");
        worker.drain();
        slicer.plans.clear();
        int fileId = given().auth().oauth2(adminToken).get("/api/requests/" + id).then()
                .extract().path("files[0].id");
        return new int[] {id, fileId};
    }

    private static Map<String, Object> choice(long printerId, String nozzle, String process, String filament) {
        Map<String, Object> body = new HashMap<>();
        body.put("printerId", printerId);
        body.put("nozzle", new BigDecimal(nozzle));
        body.put("process", process);
        body.put("filament", filament);
        return body;
    }

    private ValidatableResponse resliceWith(String token, int[] file, Map<String, Object> choice) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(choice)
                .post("/api/requests/" + file[0] + "/files/" + file[1] + "/reslice").then();
    }

    private ValidatableResponse options(String token, int[] file) {
        return given().auth().oauth2(token)
                .get("/api/requests/" + file[0] + "/files/" + file[1] + "/reslice").then();
    }

    private ValidatableResponse slicingOf(int[] file) {
        return given().auth().oauth2(bobToken).get("/api/requests/" + file[0]).then().statusCode(200);
    }

    @Test
    void slicesAnStlWithTheChosenSettingsAndShowsThem() {
        int[] file = uploaded("cube.stl", ModelFiles.cubeStl(10));
        options(adminToken, file).statusCode(200)
                .body("project", is(false))
                .body("projectPrinter", nullValue())
                .body("printers.model", contains("Bambu Lab P1S"))
                .body("start.printerId", is((int) p1sId))
                .body("start.nozzle", is(0.4f))
                .body("start.process", is("0.20mm Standard @BBL X1C"))
                .body("start.filament", is("Bambu PLA Basic @BBL X1C"))
                .body("start.custom", is(false));

        resliceWith(adminToken, file, choice(p1sId, "0.2", PROCESS, FILAMENT)).statusCode(200)
                .body("files[0].modelInfo.slicing.status", is("QUEUED"));
        assertEquals(1, worker.drain());

        SlicePlan plan = slicer.plans.getFirst();
        assertEquals("Bambu Lab P1S", plan.printer());
        assertEquals(0, new BigDecimal("0.2").compareTo(plan.nozzle()));
        assertEquals(PROCESS, plan.process());
        assertEquals(FILAMENT, plan.filament());
        assertTrue(plan.arrange());
        slicingOf(file)
                .body("files[0].modelInfo.slicing.status", is("DONE"))
                .body("files[0].modelInfo.slicing.custom", is(true))
                .body("files[0].modelInfo.slicing.nozzle", is(0.2f))
                .body("files[0].modelInfo.slicing.process", is(PROCESS))
                .body("files[0].modelInfo.slicing.filament", is(FILAMENT));
        options(adminToken, file)
                .body("start.custom", is(true))
                .body("start.nozzle", is(0.2f))
                .body("start.process", is(PROCESS));
    }

    /** The old plain re-slice (#15) is gone since #21; a POST without a choice is refused. */
    @Test
    void aResliceWithoutAChoiceIsRefused() {
        int[] file = uploaded("cube.stl", ModelFiles.cubeStl(10));
        String url = "/api/requests/" + file[0] + "/files/" + file[1] + "/reslice";

        given().auth().oauth2(adminToken).post(url).then().statusCode(415);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).post(url).then().statusCode(400);
        assertTrue(slicer.plans.isEmpty());
    }

    @Test
    void aProjectOnItsOwnPrinterAndNozzleCanKeepItsOwnSettings() {
        int[] file = uploaded("project.3mf", SlicingResourceTest.project("0.2", "Bambu Lab P1S"));
        options(adminToken, file)
                .body("project", is(true))
                .body("projectPrinter", is("Bambu Lab P1S"))
                .body("projectNozzle", is(0.2f))
                .body("start.nozzle", is(0.2f))
                .body("start.process", nullValue())
                .body("start.filament", nullValue());

        resliceWith(adminToken, file, choice(p1sId, "0.2", null, null)).statusCode(200);
        worker.drain();

        SlicePlan plan = slicer.plans.getFirst();
        assertTrue(plan.projectPrinter());
        assertNull(plan.process());
        assertNull(plan.filament());
        assertFalse(plan.arrange());
        slicingOf(file)
                .body("files[0].modelInfo.slicing.custom", is(true))
                .body("files[0].modelInfo.slicing.process", nullValue());
    }

    @Test
    void aProjectWithAChosenProcessKeepsTheDesignersSettingsAndCanKeepItsFilaments() {
        int[] file = uploaded("project.3mf", SlicingResourceTest.project("0.2", "Bambu Lab P1S"));

        resliceWith(adminToken, file, choice(p1sId, "0.4", null, null)).statusCode(400)
                .body("message", is("The project's own settings only work on the printer and nozzle it was made "
                        + "for; choose a process profile"));
        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, null)).statusCode(200);
        worker.drain();

        SlicePlan plan = slicer.plans.getFirst();
        assertFalse(plan.projectPrinter());
        assertEquals(PROCESS, plan.process());
        assertNull(plan.filament());
        assertEquals(2, plan.filamentSlots());
        assertEquals(Map.of("wall_loops", "4"), plan.overrides());
        assertFalse(plan.arrange());
        slicingOf(file).body("files[0].modelInfo.slicing.madeForNozzle", is(0.2f));
    }

    @Test
    void refusesWhatCantBeSliced() {
        int[] file = uploaded("cube.stl", ModelFiles.cubeStl(10));

        resliceWith(adminToken, file, choice(p1sId, "0.4", null, FILAMENT))
                .statusCode(400).body("message", is("Choose a process profile"));
        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, null))
                .statusCode(400).body("message", is("Choose a filament profile"));
        resliceWith(adminToken, file, choice(999, "0.4", PROCESS, FILAMENT))
                .statusCode(400).body("message", is("Choose one of your printers"));
        resliceWith(adminToken, file, choice(p1sId, "0.6", PROCESS, FILAMENT))
                .statusCode(400).body("message", is("You don't own a 0.6 mm nozzle for Bambu Lab P1S"));
        resliceWith(adminToken, file, choice(p1sId, "0.4", "0.08mm Extra Fine", FILAMENT))
                .statusCode(400).body("message", is("The process profile 0.08mm Extra Fine doesn't exist for "
                        + "Bambu Lab P1S with a 0.4 mm nozzle"));
        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, "PETG HF"))
                .statusCode(400).body("message", startsWith("The filament profile PETG HF doesn't exist"));
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .post("/api/requests/" + file[0] + "/files/" + file[1] + "/reslice").then().statusCode(400);
        Map<String, Object> noNozzle = choice(p1sId, "0.4", PROCESS, FILAMENT);
        noNozzle.remove("nozzle");
        resliceWith(adminToken, file, noNozzle).statusCode(400);
        resliceWith(bobToken, file, choice(p1sId, "0.4", PROCESS, FILAMENT)).statusCode(403);
        options(bobToken, file).statusCode(403);

        slicer.failure = FakeSlicerClient.unreachable();
        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, FILAMENT)).statusCode(503);
        slicer.failure = null;

        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, FILAMENT)).statusCode(200);
        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, FILAMENT))
                .statusCode(409).body("message", is("This file is already being sliced"));
        worker.drain();
        slicing(false);
        resliceWith(adminToken, file, choice(p1sId, "0.4", PROCESS, FILAMENT))
                .statusCode(409).body("message", is("Server slicing is off"));
    }

    @Test
    void linksCantBeSliced() {
        int id = given().auth().oauth2(bobToken).multiPart("name", "Dragon")
                .multiPart("url", "https://www.patreon.com/posts/1")
                .post("/api/requests").then().statusCode(201).extract().path("id");
        int linkId = given().auth().oauth2(adminToken).get("/api/requests/" + id).then()
                .extract().path("files[0].id");
        int[] link = {id, linkId};

        resliceWith(adminToken, link, choice(p1sId, "0.4", PROCESS, FILAMENT)).statusCode(409);
        options(adminToken, link).statusCode(409);
        options(adminToken, new int[] {id, 999}).statusCode(404);
        options(adminToken, new int[] {999, linkId}).statusCode(404);
    }

    @Test
    void onlyThatFileIsResliced() {
        int[] file = uploaded("cube.stl", ModelFiles.cubeStl(10));
        given().auth().oauth2(bobToken)
                .multiPart("file", "lid.stl", ModelFiles.cubeStl(5), "application/octet-stream")
                .post("/api/requests/" + file[0] + "/files").then().statusCode(201);
        worker.drain();

        resliceWith(adminToken, file, choice(p1sId, "0.2", PROCESS, FILAMENT)).statusCode(200)
                .body("files[0].modelInfo.slicing.status", is("QUEUED"))
                .body("files[1].modelInfo.slicing.status", is("DONE"));
    }

    @Test
    void aChosenPrinterThatIsRemovedBeforeTheJobRunsFailsIt() {
        Map<String, Object> a1 = TestData.printer("Bambu Lab A1");
        long a1Id = addPrinter(a1);
        int[] file = uploaded("cube.stl", ModelFiles.cubeStl(10));
        resliceWith(adminToken, file, choice(a1Id, "0.4", PROCESS, FILAMENT)).statusCode(200);

        given().auth().oauth2(adminToken).delete("/api/settings/printers/" + a1Id).then().statusCode(200);
        worker.drain();

        assertTrue(slicer.plans.isEmpty());
        given().auth().oauth2(adminToken).get("/api/requests/" + file[0]).then()
                .body("files[0].modelInfo.slicing.status", is("FAILED"))
                .body("files[0].modelInfo.slicing.error",
                        is("The chosen printer Bambu Lab A1 isn't one of your printers any more"));
        options(adminToken, file).body("start.printerId", is((int) p1sId)).body("start.custom", is(false));
    }
}
