package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.support.FakeSpoolmanClient;
import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.*;

/** The Spoolman status, the switches in the settings, and the filament list (#11). */
@QuarkusTest
class SpoolmanResourceTest {

    @Inject
    TestData data;

    @Inject
    FakeSpoolmanClient spoolman;

    @Inject
    SpoolmanCatalog catalog;

    String adminToken;
    String userToken;

    @BeforeEach
    void setUp() {
        data.reset();
        spoolman.reset();
        catalog.clear();
        adminToken = data.accessToken(data.admin());
        userToken = data.accessToken(data.user("bob"));
    }

    private ValidatableResponse saveSwitches(boolean enabled, boolean colorSelection) {
        return given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("enabled", enabled, "colorSelection", colorSelection))
                .put("/api/settings/spoolman").then();
    }

    @Test
    void reportsTheStatus() {
        given().auth().oauth2(adminToken).get("/api/spoolman/status").then().statusCode(200)
                .body("configured", is(true))
                .body("reachable", is(true))
                .body("url", is("http://spoolman.test:7912"))
                .body("version", is("0.22.1"))
                .body("message", nullValue());

        spoolman.failure = new SpoolmanException("Spoolman can't be reached");
        given().auth().oauth2(adminToken).get("/api/spoolman/status").then().statusCode(200)
                .body("configured", is(true))
                .body("reachable", is(false))
                .body("message", is("Spoolman can't be reached"));

        spoolman.configured = false;
        given().auth().oauth2(adminToken).get("/api/spoolman/status").then().statusCode(200)
                .body("configured", is(false))
                .body("url", nullValue())
                .body("message", is("Spoolman isn't set up (SPOOLMAN_URL is missing)"));

        given().auth().oauth2(userToken).get("/api/spoolman/status").then().statusCode(403);
    }

    @Test
    void switchesAreOffByDefaultAndSaved() {
        given().auth().oauth2(adminToken).get("/api/settings").then().statusCode(200)
                .body("spoolman.enabled", is(false))
                .body("spoolman.colorSelection", is(false));

        saveSwitches(true, false).statusCode(200)
                .body("spoolman.enabled", is(true))
                .body("spoolman.colorSelection", is(false));
        saveSwitches(true, true).statusCode(200).body("spoolman.colorSelection", is(true));
        given().auth().oauth2(adminToken).get("/api/settings").then()
                .body("spoolman.enabled", is(true))
                .body("spoolman.colorSelection", is(true));

        saveSwitches(false, false).statusCode(200)
                .body("spoolman.enabled", is(false))
                .body("spoolman.colorSelection", is(false));
    }

    @Test
    void colourSelectionNeedsTheIntegrationAndAnAddress() {
        saveSwitches(false, true).statusCode(400)
                .body("message", is("Switch the Spoolman integration on before colour selection"));

        spoolman.configured = false;
        saveSwitches(true, false).statusCode(400)
                .body("message", is("Set SPOOLMAN_URL on the server before switching Spoolman on"));
        // Switching off always works.
        saveSwitches(false, false).statusCode(200);

        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("enabled", true))
                .put("/api/settings/spoolman").then().statusCode(400);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(Map.of("enabled", false, "colorSelection", false))
                .put("/api/settings/spoolman").then().statusCode(403);
    }

    @Test
    void listsEveryFilamentWhileColourSelectionIsOn() {
        spoolman.filaments.addAll(List.of(
                FakeSpoolmanClient.filament(1, "PLA", "#FF0000", 0.0),
                FakeSpoolmanClient.filament(2, "ABS", "#000000", 300.0),
                new SpoolmanFilament(3, "Silk", null, "PLA", null, List.of("#FF0000", "#00FF00"), 50.0),
                new SpoolmanFilament(4, null, null, null, "#FFFFFF", List.of(), null)));

        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(409)
                .body("message", is("Choosing filaments from Spoolman is switched off"));
        data.setSpoolman(true, false);
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(409);

        data.setSpoolman(true, true);
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(200)
                // By material, vendor and name; a filament without material last. Used up ones too.
                .body("id", contains(2, 1, 3, 4))
                .body("[1].gramsLeft", is(0.0f))
                .body("[2].colors", contains("#FF0000", "#00FF00"))
                .body("[2].color", nullValue())
                .body("[3].gramsLeft", nullValue());
    }

    @Test
    void cachesTheListAndReportsAnUnreachableSpoolman() {
        data.setSpoolman(true, true);
        spoolman.filaments.add(FakeSpoolmanClient.filament(1, "PLA", "#FF0000", 10.0));
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(200);
        given().auth().oauth2(adminToken).get("/api/spoolman/filaments").then().statusCode(200);
        assertEquals(1, spoolman.calls.get());

        catalog.clear();
        spoolman.failure = new SpoolmanException("Spoolman can't be reached");
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(503)
                .body("message", is("Spoolman can't be reached"));
    }

    @Test
    void switchingTheIntegrationOnForgetsTheCachedList() {
        data.setSpoolman(true, true);
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(200);
        saveSwitches(false, false).statusCode(200);
        saveSwitches(true, true).statusCode(200);
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(200);
        assertEquals(2, spoolman.calls.get());

        // Saving again while it is on keeps the list.
        saveSwitches(true, true).statusCode(200);
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(200);
        assertEquals(2, spoolman.calls.get());
    }

    @Test
    void switchesCountAsOffWithoutAnAddress() {
        data.setSpoolman(true, true);
        spoolman.configured = false;
        given().auth().oauth2(userToken).get("/api/spoolman/filaments").then().statusCode(409);
    }
}
