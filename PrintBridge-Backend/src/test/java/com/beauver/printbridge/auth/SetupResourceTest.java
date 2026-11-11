package com.beauver.printbridge.auth;

import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SetupResourceTest {

    @Inject
    TestData data;

    @BeforeEach
    void reset() {
        data.reset();
    }

    @Test
    void statusIsRequiredWithoutAdmin() {
        given().get("/api/setup").then().statusCode(200).body("setupRequired", is(true));
    }

    @Test
    void createsAdminAndLogsIn() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", "owner", "email", "owner@example.com", "password", "password123"))
                .post("/api/setup")
                .then().statusCode(201)
                .body("accessToken", not(emptyOrNullString()))
                .body("user.username", is("owner"))
                .body("user.role", is("ADMIN"))
                .cookie(RefreshCookie.NAME, not(emptyOrNullString()));

        given().get("/api/setup").then().body("setupRequired", is(false));
    }

    @Test
    void lockedOnceAdminExists() {
        data.admin();
        given().contentType(ContentType.JSON)
                .body(Map.of("username", "second", "email", "second@example.com", "password", "password123"))
                .post("/api/setup")
                .then().statusCode(409)
                .body("message", is(SetupService.ALREADY_DONE));
    }

    @Test
    void rejectsInvalidInput() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", "x", "email", "not-an-email", "password", "short"))
                .post("/api/setup")
                .then().statusCode(400);
        given().get("/api/setup").then().body("setupRequired", is(true));
    }
}
