package com.beauver.printbridge.settings;

import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SettingsResourceTest {

    @Inject
    TestData data;

    @Inject
    SettingsService settings;

    String adminToken;
    String userToken;

    @BeforeEach
    void setUp() {
        data.reset();
        adminToken = data.accessToken(data.admin());
        userToken = data.accessToken(data.user("bob"));
    }

    private ValidatableResponse put(String path, Object body) {
        return given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .put("/api/settings/" + path).then();
    }

    private ValidatableResponse addPrinter(Object body) {
        return given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body)
                .post("/api/settings/printers").then();
    }

    private ValidatableResponse deletePrinter(long id) {
        return given().auth().oauth2(adminToken).delete("/api/settings/printers/" + id).then();
    }

    @Test
    void everythingIsEmptyOrOffByDefault() {
        given().auth().oauth2(adminToken).get("/api/settings")
                .then().statusCode(200)
                .body("printers", empty())
                .body("defaultPrinterId", nullValue())
                .body("slicing.enabled", is(false))
                .body("slicing.timeoutMinutes", is(10));
        SlicingSettings slicing = settings.slicing();
        assertFalse(slicing.enabled());
        assertTrue(slicing.printers().isEmpty());
        assertNull(slicing.defaultPrinter());
    }

    @Test
    void firstPrinterBecomesTheDefault() {
        addPrinter(TestData.printer("Bambu Lab P1S"))
                .statusCode(201)
                .body("defaultPrinterId", is(1))
                .body("printers[0].model", is("Bambu Lab P1S"))
                .body("printers[0].buildPlate.z", is(250))
                .body("printers[0].ownedNozzles", contains(0.2f, 0.4f))
                .body("printers[0].defaultNozzle", is(0.4f));
        addPrinter(TestData.printer(" Bambu Lab A1 mini ", 180, 180, 180))
                .statusCode(201)
                .body("defaultPrinterId", is(1))
                .body("printers.model", contains("Bambu Lab P1S", "Bambu Lab A1 mini"))
                .body("printers[1].ownedNozzles", empty())
                .body("printers[1].process", nullValue());

        SlicingSettings slicing = settings.slicing();
        PrinterSettings p1s = slicing.defaultPrinter();
        assertEquals("Bambu Lab P1S", p1s.model());
        assertEquals(new BuildPlate(256, 256, 250), p1s.buildPlate());
        assertEquals(List.of(new BigDecimal("0.2"), new BigDecimal("0.4")), p1s.ownedNozzles());
        assertTrue(p1s.owns(new BigDecimal("0.40")));
        assertFalse(p1s.owns(new BigDecimal("0.6")));
        assertFalse(p1s.owns(null));
        assertTrue(p1s.readyForSlicing());
        PrinterSettings mini = slicing.printer("bambu lab a1 MINI").orElseThrow();
        assertFalse(mini.readyForSlicing());
        assertTrue(slicing.printer(null).isEmpty());
        assertTrue(slicing.printer("Prusa MK4").isEmpty());
    }

    @Test
    void rejectsInvalidPrinters() {
        addPrinter(TestData.printer("Bambu Lab P1S"));

        addPrinter(TestData.printer("bambu lab p1s")).statusCode(409)
                .body("message", is("You already have a printer called Bambu Lab P1S"));
        Map<String, Object> wrongDefault = TestData.printer("Other");
        wrongDefault.put("defaultNozzle", 0.6);
        addPrinter(wrongDefault).statusCode(400)
                .body("message", is("The default nozzle must be one of the owned nozzles"));
        addPrinter(TestData.printer("Other", 0, 2001, 100)).statusCode(400);
        addPrinter(TestData.printer(" ", 100, 100, 100)).statusCode(400);
        addPrinter(Map.of("model", "No plate")).statusCode(400);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).post("/api/settings/printers")
                .then().statusCode(400);

        assertEquals(1, settings.slicing().printers().size());
    }

    @Test
    void adminChangesAPrinter() {
        addPrinter(TestData.printer("Bambu Lab P1S"));
        addPrinter(TestData.printer("Bambu Lab A1", 256, 256, 256));

        put("printers/1", TestData.printer("Bambu Lab P1S", 250, 250, 250)).statusCode(200)
                .body("printers[0].buildPlate.x", is(250))
                .body("printers[0].ownedNozzles", empty());
        put("printers/1", TestData.printer("Bambu Lab A1", 1, 1, 1)).statusCode(409);
        put("printers/99", TestData.printer("Nope", 1, 1, 1)).statusCode(404);
    }

    @Test
    void slicingNeedsPrintersThatAreReady() {
        put("slicing", TestData.slicingSettings(true)).statusCode(400)
                .body("message", is("Add a printer before switching slicing on"));
        addPrinter(TestData.printer("Bambu Lab P1S"));
        addPrinter(TestData.printer("Bambu Lab A1 mini", 180, 180, 180));
        put("slicing", TestData.slicingSettings(true)).statusCode(400)
                .body("message", is("Choose nozzles and profiles for Bambu Lab A1 mini before switching slicing on"));
        deletePrinter(2).statusCode(200);

        put("slicing", TestData.slicingSettings(true)).statusCode(200)
                .body("slicing.enabled", is(true))
                .body("slicing.timeoutMinutes", is(5));

        addPrinter(TestData.printer("Bambu Lab A1 mini", 180, 180, 180)).statusCode(400)
                .body("message", is("Choose nozzles and profiles for this printer while server slicing is on"));
        SlicingSettings saved = settings.slicing();
        assertTrue(saved.enabled());
        assertEquals(Duration.ofMinutes(5), saved.timeout());
        assertEquals(1, saved.printers().size());
    }

    @Test
    void rejectsInvalidSlicingSettings() {
        Map<String, Object> tooLong = TestData.slicingSettings(false);
        tooLong.put("timeoutMinutes", 61);
        put("slicing", tooLong).statusCode(400);

        Map<String, Object> missing = TestData.slicingSettings(false);
        missing.remove("enabled");
        put("slicing", missing).statusCode(400);

        put("slicing", Map.of("enabled", false, "timeoutMinutes", 60)).statusCode(200)
                .body("slicing.timeoutMinutes", is(60));
    }

    @Test
    void removingTheDefaultPrinterMovesTheDefault() {
        addPrinter(TestData.printer("Bambu Lab P1S"));
        addPrinter(TestData.printer("Bambu Lab A1"));
        addPrinter(TestData.printer("Bambu Lab X1 Carbon"));
        put("printers/3/default", "").statusCode(200).body("defaultPrinterId", is(3));
        put("printers/9/default", "").statusCode(404);

        deletePrinter(1).statusCode(200).body("defaultPrinterId", is(3)).body("printers", hasSize(2));
        deletePrinter(3).statusCode(200).body("defaultPrinterId", is(2));
        put("slicing", TestData.slicingSettings(true)).statusCode(200);

        deletePrinter(2).statusCode(409)
                .body("message", is("Switch server slicing off before removing your last printer"));
        put("slicing", TestData.slicingSettings(false)).statusCode(200);
        deletePrinter(2).statusCode(200).body("defaultPrinterId", nullValue()).body("printers", empty());
        deletePrinter(2).statusCode(404);
    }

    @Test
    void usersCannotReadOrChangeSettings() {
        given().auth().oauth2(userToken).get("/api/settings").then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(TestData.printer("P1S"))
                .post("/api/settings/printers")
                .then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(TestData.slicingSettings(false))
                .put("/api/settings/slicing")
                .then().statusCode(403);
        given().auth().oauth2(userToken).delete("/api/settings/printers/1").then().statusCode(403);
        given().get("/api/settings").then().statusCode(401);
    }
}
