package com.beauver.printbridge.settings;

import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void buildPlateIsEmptyByDefault() {
        given().auth().oauth2(adminToken).get("/api/settings")
                .then().statusCode(200)
                .body("buildPlateX", nullValue())
                .body("buildPlateY", nullValue())
                .body("buildPlateZ", nullValue());
        assertTrue(settings.buildPlate().isEmpty());
    }

    @Test
    void adminSavesBuildPlate() {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("buildPlateX", 256, "buildPlateY", 256, "buildPlateZ", 250))
                .put("/api/settings")
                .then().statusCode(200)
                .body("buildPlateX", is(256))
                .body("buildPlateZ", is(250));

        given().auth().oauth2(adminToken).get("/api/settings")
                .then().statusCode(200).body("buildPlateY", is(256));
        assertEquals(new BuildPlate(256, 256, 250), settings.buildPlate().orElseThrow());
    }

    @Test
    void rejectsInvalidSizes() {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("buildPlateX", 0, "buildPlateY", 2001, "buildPlateZ", 100))
                .put("/api/settings")
                .then().statusCode(400);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("buildPlateX", 100, "buildPlateY", 100))
                .put("/api/settings")
                .then().statusCode(400);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .put("/api/settings")
                .then().statusCode(400);

        assertTrue(settings.buildPlate().isEmpty());
    }

    @Test
    void usersCannotReadOrChangeSettings() {
        given().auth().oauth2(userToken).get("/api/settings").then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(Map.of("buildPlateX", 1, "buildPlateY", 1, "buildPlateZ", 1))
                .put("/api/settings")
                .then().statusCode(403);
        given().get("/api/settings").then().statusCode(401);
    }
}
