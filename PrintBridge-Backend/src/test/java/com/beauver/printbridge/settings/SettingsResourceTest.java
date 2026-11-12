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
import java.util.HashMap;
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

    @Test
    void everythingIsEmptyOrOffByDefault() {
        given().auth().oauth2(adminToken).get("/api/settings")
                .then().statusCode(200)
                .body("buildPlate.x", nullValue())
                .body("buildPlate.z", nullValue())
                .body("slicing.enabled", is(false))
                .body("slicing.timeoutMinutes", is(10))
                .body("slicing.ownedNozzles", empty());
        assertTrue(settings.buildPlate().isEmpty());
        assertFalse(settings.slicing().enabled());
    }

    @Test
    void adminSavesBuildPlate() {
        put("build-plate", Map.of("x", 256, "y", 256, "z", 250))
                .statusCode(200)
                .body("buildPlate.x", is(256))
                .body("buildPlate.z", is(250));

        given().auth().oauth2(adminToken).get("/api/settings")
                .then().statusCode(200).body("buildPlate.y", is(256));
        assertEquals(new BuildPlate(256, 256, 250), settings.buildPlate().orElseThrow());
    }

    @Test
    void rejectsInvalidBuildPlates() {
        put("build-plate", Map.of("x", 0, "y", 2001, "z", 100)).statusCode(400);
        put("build-plate", Map.of("x", 100, "y", 100)).statusCode(400);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).put("/api/settings/build-plate")
                .then().statusCode(400);

        assertTrue(settings.buildPlate().isEmpty());
    }

    @Test
    void adminSavesSlicingSettings() {
        put("slicing", TestData.slicingSettings(true))
                .statusCode(200)
                .body("slicing.enabled", is(true))
                .body("slicing.ownedNozzles", contains(0.2f, 0.4f))
                .body("slicing.defaultNozzle", is(0.4f));

        SlicingSettings saved = settings.slicing();
        assertTrue(saved.enabled());
        assertEquals(Duration.ofMinutes(5), saved.timeout());
        assertEquals(List.of(new BigDecimal("0.2"), new BigDecimal("0.4")), saved.ownedNozzles());
        assertTrue(saved.owns(new BigDecimal("0.40")));
        assertFalse(saved.owns(new BigDecimal("0.6")));
        assertFalse(saved.owns(null));
        assertEquals("Bambu PLA Basic @BBL X1C", saved.filament());
    }

    @Test
    void slicingCanStayOffWithoutProfiles() {
        Map<String, Object> off = new HashMap<>();
        off.put("enabled", false);
        off.put("timeoutMinutes", 60);
        off.put("printer", " ");

        put("slicing", off).statusCode(200).body("slicing.printer", nullValue()).body("slicing.timeoutMinutes", is(60));
    }

    @Test
    void rejectsIncompleteOrInvalidSlicingSettings() {
        Map<String, Object> noProfile = TestData.slicingSettings(true);
        noProfile.put("process", "");
        put("slicing", noProfile).statusCode(400)
                .body("message", is("Choose a printer, nozzles and profiles before switching slicing on"));

        Map<String, Object> wrongDefault = TestData.slicingSettings(false);
        wrongDefault.put("defaultNozzle", 0.6);
        put("slicing", wrongDefault).statusCode(400)
                .body("message", is("The default nozzle must be one of the owned nozzles"));

        Map<String, Object> tooLong = TestData.slicingSettings(false);
        tooLong.put("timeoutMinutes", 61);
        put("slicing", tooLong).statusCode(400);

        Map<String, Object> missing = TestData.slicingSettings(false);
        missing.remove("enabled");
        put("slicing", missing).statusCode(400);

        assertFalse(settings.slicing().enabled());
    }

    @Test
    void usersCannotReadOrChangeSettings() {
        given().auth().oauth2(userToken).get("/api/settings").then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(Map.of("x", 1, "y", 1, "z", 1))
                .put("/api/settings/build-plate")
                .then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(TestData.slicingSettings(false))
                .put("/api/settings/slicing")
                .then().statusCode(403);
        given().get("/api/settings").then().statusCode(401);
    }
}
