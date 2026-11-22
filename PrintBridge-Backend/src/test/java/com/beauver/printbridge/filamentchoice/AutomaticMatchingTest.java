package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.modelfile.FilePlatesChanged;
import com.beauver.printbridge.printrequest.RequestStatus;
import com.beauver.printbridge.spoolman.SpoolmanCatalog;
import com.beauver.printbridge.spoolman.SpoolmanException;
import com.beauver.printbridge.support.FakeSpoolmanClient;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.support.ThreeMfFiles;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Automatic filament matching when a project's colours become known (#76). */
@QuarkusTest
class AutomaticMatchingTest {

    @Inject
    TestData data;

    @Inject
    FakeSpoolmanClient spoolman;

    @Inject
    SpoolmanCatalog catalog;

    @Inject
    Event<FilePlatesChanged> platesChanged;

    String bobToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        data.reset();
        spoolman.reset();
        catalog.clear();
        // The project's colours: plate 1 PLA #FFFFFF, plate 2 PETG #FF0000.
        spoolman.filaments.addAll(List.of(
                FakeSpoolmanClient.filament(1, "PLA", "#F0F0F0", 1000.0),
                FakeSpoolmanClient.filament(2, "PLA", "#FF0000", 1000.0),
                FakeSpoolmanClient.filament(3, "PETG", "#FFFFFF", 1000.0)));
        data.setSpoolman(true, true);
        data.setAutoMatch(true, 20);
        bobToken = data.accessToken(data.user("bob"));
        adminToken = data.accessToken(data.admin());
    }

    private record Upload(int requestId, int fileId) {
    }

    private Upload upload(String filename, byte[] content) {
        JsonPath created = given().auth().oauth2(bobToken)
                .multiPart("name", "Model")
                .multiPart("file", filename, content, "application/octet-stream")
                .post("/api/requests").then().statusCode(201).extract().jsonPath();
        return new Upload(created.getInt("id"), created.getInt("files[0].id"));
    }

    private Upload project() {
        return upload("project.3mf", ThreeMfFiles.bambuProject().build());
    }

    private JsonPath get(Upload upload) {
        return given().auth().oauth2(bobToken).get("/api/requests/" + upload.requestId()).then().statusCode(200)
                .extract().jsonPath();
    }

    private Map<String, Object> plate(int plate, String type, String color, long filamentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("wholeItem", false);
        body.put("plateIndex", plate);
        body.put("originalType", type);
        body.put("originalColor", color);
        body.put("spoolmanFilamentId", filamentId);
        return body;
    }

    private String filePath(Upload upload) {
        return "/api/requests/" + upload.requestId() + "/files/" + upload.fileId();
    }

    @Test
    void matchesTheClosestFilamentOfTheSameMaterialAfterUpload() {
        JsonPath request = get(project());

        // Plate 1: the light grey PLA (about ΔE 3 from white), not the white PETG.
        assertEquals(1, request.getList("files[0].filamentChoices").size());
        assertEquals(1, request.getInt("files[0].filamentChoices[0].plateIndex"));
        assertEquals(1, request.getInt("files[0].filamentChoices[0].spoolmanFilamentId"));
        assertEquals("AUTO", request.getString("files[0].filamentChoices[0].source"));
        // Plate 2: no red PETG in Spoolman.
        assertEquals(List.of(Map.of("plateIndex", 2, "originalType", "PETG", "originalColor", "#FF0000")),
                request.getList("files[0].unmatchedColors"));
        assertEquals("No close colour found, please select another colour for this print",
                request.getString("noMatchMessage"));
        // The admin sees the same.
        given().auth().oauth2(adminToken).get("/api/requests/" + request.getInt("id")).then()
                .body("files[0].unmatchedColors", hasSize(1));
    }

    @Test
    void choosingResolvesAndResettingSticks() {
        Upload project = project();
        spoolman.filaments.add(FakeSpoolmanClient.filament(4, "PETG", "#EE1111", 1000.0));
        catalog.clear();

        // Choosing a filament for the unmatched colour clears the warning; choosing on an auto match makes it manual.
        JsonPath chosen = given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .body(plate(2, "PETG", "#FF0000", 4))
                .put(filePath(project) + "/filaments").then().statusCode(200).extract().jsonPath();
        assertEquals(List.of(), chosen.getList("files[0].unmatchedColors"));
        long autoId = chosen.getLong("files[0].filamentChoices.find { it.plateIndex == 1 }.id");

        // Use file colour on the auto match: remembered, so matching again doesn't pick it.
        given().auth().oauth2(bobToken).delete(filePath(project) + "/filaments/" + autoId).then().statusCode(200);
        platesChanged.fire(new FilePlatesChanged(project.fileId()));
        JsonPath after = get(project);
        assertEquals(List.of(2), after.getList("files[0].filamentChoices.plateIndex"));
        assertEquals("MANUAL", after.getString("files[0].filamentChoices[0].source"));
        assertEquals(List.of(), after.getList("files[0].unmatchedColors"));
    }

    @Test
    void matchingAgainLeavesDecisionsAloneAndManualChoicesWin() {
        Upload project = project();
        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(plate(1, "PLA", "#FFFFFF", 2))
                .put(filePath(project) + "/filaments").then().statusCode(200)
                .body("files[0].filamentChoices[0].source", is("MANUAL"));

        platesChanged.fire(new FilePlatesChanged(project.fileId()));

        JsonPath after = get(project);
        assertEquals(2, after.getInt("files[0].filamentChoices[0].spoolmanFilamentId"));
        assertEquals(1, after.getList("files[0].filamentChoices").size());
        assertEquals(1, after.getList("files[0].unmatchedColors").size());
    }

    @Test
    void theToleranceDecides() {
        data.setAutoMatch(true, 1);
        JsonPath strict = get(project());
        assertEquals(List.of(), strict.getList("files[0].filamentChoices"));
        assertEquals(2, strict.getList("files[0].unmatchedColors").size());
    }

    @Test
    void leavesStlsFinalRequestsAndSwitchedOffMatchingAlone() {
        JsonPath stl = get(upload("cube.stl", ModelFiles.cubeStl(10)));
        assertEquals(List.of(), stl.getList("files[0].filamentChoices"));
        assertEquals(List.of(), stl.getList("files[0].unmatchedColors"));

        data.setAutoMatch(false, 20);
        JsonPath off = get(project());
        assertEquals(List.of(), off.getList("files[0].filamentChoices"));
        assertEquals(List.of(), off.getList("files[0].unmatchedColors"));

        // A final request isn't changed anymore: matching it later does nothing.
        Upload finalOne = project();
        data.setStatus(finalOne.requestId(), RequestStatus.COMPLETED);
        data.setAutoMatch(true, 20);
        platesChanged.fire(new FilePlatesChanged(finalOne.fileId()));
        assertEquals(List.of(), get(finalOne).getList("files[0].filamentChoices"));
        // Unknown files and links are ignored.
        platesChanged.fire(new FilePlatesChanged(999_999));
    }

    @Test
    void matchesLaterWhenSpoolmanWasDown() {
        spoolman.failure = new SpoolmanException("Spoolman can't be reached");
        Upload project = project();
        JsonPath down = get(project);
        assertEquals(List.of(), down.getList("files[0].filamentChoices"));
        assertEquals(List.of(), down.getList("files[0].unmatchedColors"));

        spoolman.failure = null;
        platesChanged.fire(new FilePlatesChanged(project.fileId()));
        assertEquals(1, get(project).getList("files[0].filamentChoices").size());
    }

    @Test
    void settingsNeedColourSelectionAndValidateTheTolerance() {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", true);
        body.put("colorSelection", false);
        body.put("prices", false);
        body.put("autoMatch", true);
        body.put("matchTolerance", 20);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .put("/api/settings/spoolman").then().statusCode(400)
                .body("message", is("Switch colour selection on before automatic matching"));

        body.put("colorSelection", true);
        body.put("matchTolerance", 35);
        body.put("noMatchMessage", "Pick another colour, please.");
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .put("/api/settings/spoolman").then().statusCode(200)
                .body("spoolman.autoMatch", is(true))
                .body("spoolman.matchTolerance", is(35))
                .body("spoolman.noMatchMessage", is("Pick another colour, please."));
        assertEquals("Pick another colour, please.", get(project()).getString("noMatchMessage"));

        body.put("matchTolerance", 0);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .put("/api/settings/spoolman").then().statusCode(400);
        body.put("matchTolerance", 101);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .put("/api/settings/spoolman").then().statusCode(400);
        body.put("matchTolerance", 20);
        body.put("noMatchMessage", "x".repeat(301));
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .put("/api/settings/spoolman").then().statusCode(400);
    }
}
