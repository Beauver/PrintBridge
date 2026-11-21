package com.beauver.printbridge.filamentchoice;

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
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Choosing Spoolman filaments for the colours of a request's files (#11). */
@QuarkusTest
class FilamentChoiceResourceTest {

    static final String WHITE = "#FFFFFF";
    static final String RED = "#FF0000";

    @Inject
    TestData data;

    @Inject
    FakeSpoolmanClient spoolman;

    @Inject
    SpoolmanCatalog catalog;

    @Inject
    EntityManager em;

    String bobToken;
    String aliceToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        data.reset();
        spoolman.reset();
        catalog.clear();
        spoolman.filaments.addAll(List.of(
                FakeSpoolmanClient.filament(1, "PLA", "#00FF00", 1000.0),
                FakeSpoolmanClient.filament(2, "PLA", "#0000FF", 0.0),
                FakeSpoolmanClient.filament(3, "PETG", "#123456", 1000.0)));
        data.setSpoolman(true, true);
        bobToken = data.accessToken(data.user("bob"));
        aliceToken = data.accessToken(data.user("alice"));
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

    /** Plate 1: PLA white; plate 2: PETG red; plate 3: empty. */
    private Upload project() {
        return upload("project.3mf", ThreeMfFiles.bambuProject().build());
    }

    private static Map<String, Object> slot(Integer plate, String type, String color, long filamentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("plateIndex", plate);
        body.put("originalType", type);
        body.put("originalColor", color);
        body.put("spoolmanFilamentId", filamentId);
        return body;
    }

    private ValidatableResponse choose(String token, Upload upload, Map<String, Object> body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .put("/api/requests/" + upload.requestId() + "/files/" + upload.fileId() + "/filaments").then();
    }

    private ValidatableResponse remove(String token, Upload upload, Object choiceId) {
        return given().auth().oauth2(token)
                .delete("/api/requests/" + upload.requestId() + "/files/" + upload.fileId() + "/filaments/" + choiceId)
                .then();
    }

    private JsonPath get(String token, Upload upload) {
        return given().auth().oauth2(token).get("/api/requests/" + upload.requestId()).then().statusCode(200)
                .extract().jsonPath();
    }

    @Test
    void choosesAFilamentPerColourOfAPlate() {
        Upload project = project();
        assertTrue(get(bobToken, project).getBoolean("filamentSelection"));
        assertEquals(List.of(), get(bobToken, project).getList("files[0].filamentChoices"));

        choose(bobToken, project, slot(1, "PLA", "#ffffff", 1)).statusCode(200)
                .body("files[0].filamentChoices", hasSize(1))
                .body("files[0].filamentChoices[0].plateIndex", is(1))
                .body("files[0].filamentChoices[0].originalType", is("PLA"))
                .body("files[0].filamentChoices[0].originalColor", is(WHITE))
                .body("files[0].filamentChoices[0].spoolmanFilamentId", is(1))
                .body("files[0].filamentChoices[0].name", is("Basic #00FF00"))
                .body("files[0].filamentChoices[0].vendor", is("Acme"))
                .body("files[0].filamentChoices[0].material", is("PLA"))
                .body("files[0].filamentChoices[0].color", is("#00FF00"))
                .body("files[0].filamentChoices[0].orphaned", is(false))
                .body("files[0].filamentChoices[0].inSpoolman", is(true))
                .body("files[0].filamentChoices[0].gramsLeft", is(1000.0f))
                .body("files[0].filamentChoices[0].notEnough", is(false));

        // Another plate's colour is its own choice; choosing again replaces.
        choose(bobToken, project, slot(2, "PETG", RED, 3)).statusCode(200).body("files[0].filamentChoices", hasSize(2));
        choose(bobToken, project, slot(1, "PLA", WHITE, 3)).statusCode(200)
                .body("files[0].filamentChoices", hasSize(2))
                .body("files[0].filamentChoices.find { it.plateIndex == 1 }.spoolmanFilamentId", is(3))
                .body("files[0].filamentChoices.find { it.plateIndex == 2 }.spoolmanFilamentId", is(3));

        // The admin sees it too.
        assertEquals(2, get(adminToken, project).getList("files[0].filamentChoices").size());
    }

    @Test
    void refusesColoursTheFileDoesNotHave() {
        Upload project = project();
        choose(bobToken, project, slot(1, "PLA", RED, 1)).statusCode(400)
                .body("message", is("This file has no such colour on that plate"));
        choose(bobToken, project, slot(3, "PLA", WHITE, 1)).statusCode(400);
        choose(bobToken, project, slot(null, null, null, 1)).statusCode(400)
                .body("message", is("This file has colours: choose a filament per colour"));
        choose(bobToken, project, slot(null, "PLA", WHITE, 1)).statusCode(400)
                .body("message", is("Choose a plate for this colour"));
        choose(bobToken, project, slot(1, "PLA", WHITE, 99)).statusCode(400)
                .body("message", is("This filament isn't in Spoolman"));
        choose(bobToken, project, slot(1, "PLA", "white", 1)).statusCode(400);
        choose(bobToken, project, slot(-1, "PLA", WHITE, 1)).statusCode(400);
        choose(bobToken, project, slot(1, "PLA", WHITE, 1).entrySet().stream()
                .filter(entry -> !entry.getKey().equals("spoolmanFilamentId"))
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll))
                .statusCode(400);
    }

    @Test
    void wholeItemForFilesWithoutColoursAndLinks() {
        Upload stl = upload("cube.stl", ModelFiles.cubeStl(50));
        JsonPath chosen = choose(bobToken, stl, slot(null, null, null, 1)).statusCode(200)
                .body("files[0].filamentChoices[0].plateIndex", nullValue())
                .body("files[0].filamentChoices[0].orphaned", is(false))
                .extract().jsonPath();
        // The file's total grams (an estimate for an STL that wasn't sliced).
        float total = chosen.getFloat("files[0].modelInfo.totalGrams");
        assertTrue(total > 0);
        assertEquals(total, chosen.getFloat("files[0].filamentChoices[0].gramsNeeded"), 0.01);
        choose(bobToken, stl, slot(1, "PLA", WHITE, 1)).statusCode(400);

        int linkId = given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .body(Map.of("url", "https://example.com/model"))
                .post("/api/requests/" + stl.requestId() + "/links").then().statusCode(201)
                .extract().path("files[1].id");
        Upload link = new Upload(stl.requestId(), linkId);
        choose(bobToken, link, slot(null, null, null, 3)).statusCode(200)
                .body("files[1].filamentChoices[0].spoolmanFilamentId", is(3))
                .body("files[1].filamentChoices[0].gramsNeeded", nullValue())
                .body("files[1].filamentChoices[0].notEnough", is(false));
    }

    @Test
    void warnsWhenNotEnoughIsLeftForTheWholeRequest() {
        Upload project = project();
        JsonPath before = get(bobToken, project);
        float plate1 = before.getFloat("files[0].modelInfo.plates.find { it.index == 1 }.grams");
        float plate2 = before.getFloat("files[0].modelInfo.plates.find { it.index == 2 }.grams");
        // Exactly enough for plate 1 times two copies.
        spoolman.filaments.set(0, FakeSpoolmanClient.filament(1, "PLA", "#00FF00", plate1 * 2.0));
        catalog.clear();
        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(Map.of("quantity", 2))
                .put("/api/requests/" + project.requestId() + "/files/" + project.fileId() + "/quantity")
                .then().statusCode(200);

        choose(bobToken, project, slot(1, "PLA", WHITE, 1)).statusCode(200)
                .body("files[0].filamentChoices[0].notEnough", is(false));

        // The same filament for plate 2 too: now both colours need more than there is.
        JsonPath both = choose(bobToken, project, slot(2, "PETG", RED, 1)).statusCode(200).extract().jsonPath();
        assertEquals(List.of(true, true), both.getList("files[0].filamentChoices.notEnough"));
        assertEquals((plate1 + plate2) * 2, both.getFloat("files[0].filamentChoices[0].gramsNeeded"), 0.01);

        // Plate 2 to another filament: plate 1 has enough again.
        choose(bobToken, project, slot(2, "PETG", RED, 3)).statusCode(200)
                .body("files[0].filamentChoices.notEnough", contains(false, false));

        // A used up filament may still be chosen, with the warning.
        choose(bobToken, project, slot(2, "PETG", RED, 2)).statusCode(200)
                .body("files[0].filamentChoices.find { it.plateIndex == 2 }.notEnough", is(true))
                .body("files[0].filamentChoices.find { it.plateIndex == 2 }.gramsLeft", is(0.0f));
    }

    @Test
    void stockIsUnknownWhileSpoolmanCantBeReadAndShowsDeletedFilaments() {
        Upload project = project();
        choose(bobToken, project, slot(2, "PETG", RED, 2)).statusCode(200);

        catalog.clear();
        spoolman.failure = new SpoolmanException("Spoolman can't be reached");
        assertNull(get(bobToken, project).get("files[0].filamentChoices[0].inSpoolman"));
        assertFalse(get(bobToken, project).getBoolean("files[0].filamentChoices[0].notEnough"));
        // Choosing needs Spoolman.
        choose(bobToken, project, slot(1, "PLA", WHITE, 1)).statusCode(503);

        spoolman.failure = null;
        spoolman.filaments.remove(1);
        JsonPath deleted = get(bobToken, project);
        assertFalse(deleted.getBoolean("files[0].filamentChoices[0].inSpoolman"));
        assertEquals("Basic #0000FF", deleted.getString("files[0].filamentChoices[0].name"));
        assertNull(deleted.get("files[0].filamentChoices[0].gramsLeft"));
    }

    @Test
    void keepsChoicesTheFileNoLongerHasAndLetsThemBeRemoved() {
        Upload project = project();
        long choiceId = choose(bobToken, project, slot(1, "PLA", WHITE, 1)).statusCode(200)
                .extract().jsonPath().getLong("files[0].filamentChoices[0].id");

        recolour(project.fileId(), WHITE, "#000000");

        get(bobToken, project);
        assertTrue(get(bobToken, project).getBoolean("files[0].filamentChoices[0].orphaned"));
        assertFalse(get(bobToken, project).getBoolean("files[0].filamentChoices[0].notEnough"));

        remove(aliceToken, project, choiceId).statusCode(404);
        remove(bobToken, project, 9999).statusCode(404).body("message", is("Filament choice not found"));
        remove(bobToken, project, choiceId).statusCode(200).body("files[0].filamentChoices", empty());
    }

    @Transactional
    void recolour(long fileId, String from, String to) {
        em.createNativeQuery("UPDATE model_info SET plates = replace(plates::text, ?1, ?2)::jsonb WHERE file_id = ?3")
                .setParameter(1, from)
                .setParameter(2, to)
                .setParameter(3, fileId)
                .executeUpdate();
    }

    @Test
    void followsTheQuantityRules() {
        Upload project = project();
        choose(aliceToken, project, slot(1, "PLA", WHITE, 1)).statusCode(404);

        data.setStatus(project.requestId(), RequestStatus.IN_REVIEW);
        assertFalse(get(bobToken, project).getBoolean("filamentSelection"));
        choose(bobToken, project, slot(1, "PLA", WHITE, 1)).statusCode(409)
                .body("message", is("Filaments can only be chosen while the request is submitted"));
        assertTrue(get(adminToken, project).getBoolean("filamentSelection"));
        long choiceId = choose(adminToken, project, slot(1, "PLA", WHITE, 1)).statusCode(200)
                .extract().jsonPath().getLong("files[0].filamentChoices[0].id");
        remove(bobToken, project, choiceId).statusCode(409);

        data.setStatus(project.requestId(), RequestStatus.COMPLETED);
        assertFalse(get(adminToken, project).getBoolean("filamentSelection"));
        choose(adminToken, project, slot(1, "PLA", WHITE, 1)).statusCode(409);
        remove(adminToken, project, choiceId).statusCode(409);
    }

    @Test
    void choicesStayVisibleButCantChangeWhileSwitchedOff() {
        Upload project = project();
        long choiceId = choose(bobToken, project, slot(1, "PLA", WHITE, 2)).statusCode(200)
                .extract().jsonPath().getLong("files[0].filamentChoices[0].id");

        data.setSpoolman(true, false);
        JsonPath noSelection = get(bobToken, project);
        assertFalse(noSelection.getBoolean("filamentSelection"));
        // The integration is still on: the stock is still checked.
        assertTrue(noSelection.getBoolean("files[0].filamentChoices[0].notEnough"));
        choose(bobToken, project, slot(1, "PLA", WHITE, 1)).statusCode(409)
                .body("message", is("Choosing filaments from Spoolman is switched off"));
        remove(bobToken, project, choiceId).statusCode(409);

        data.setSpoolman(false, false);
        JsonPath off = get(adminToken, project);
        assertEquals("Basic #0000FF", off.getString("files[0].filamentChoices[0].name"));
        assertNull(off.get("files[0].filamentChoices[0].inSpoolman"));
        assertFalse(off.getBoolean("files[0].filamentChoices[0].notEnough"));
    }

    @Test
    void choicesGoWithTheirFileAndRequest() {
        Upload project = project();
        choose(bobToken, project, slot(1, "PLA", WHITE, 1)).statusCode(200);
        int stlId = given().auth().oauth2(bobToken)
                .multiPart("file", "cube.stl", ModelFiles.cubeStl(10), "application/octet-stream")
                .post("/api/requests/" + project.requestId() + "/files").then().statusCode(201)
                .extract().path("files[1].id");
        choose(bobToken, new Upload(project.requestId(), stlId), slot(null, null, null, 1)).statusCode(200);
        assertEquals(2L, countChoices());

        given().auth().oauth2(bobToken).delete("/api/requests/" + project.requestId() + "/files/" + stlId)
                .then().statusCode(200);
        assertEquals(1L, countChoices());
        given().auth().oauth2(bobToken).delete("/api/requests/" + project.requestId()).then().statusCode(204);
        assertEquals(0L, countChoices());
    }

    @Transactional
    long countChoices() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM filament_choices").getSingleResult()).longValue();
    }
}
