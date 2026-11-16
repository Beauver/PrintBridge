package com.beauver.printbridge.auth;

import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Login with rate limiting switched on (it is off for the other tests). Counters live for the
 * whole run, so every test uses its own client addresses, passed the way nginx passes them.
 */
@QuarkusTest
@TestProfile(LoginThrottlingTest.RateLimited.class)
class LoginThrottlingTest {

    public static class RateLimited implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("printbridge.rate-limit.enabled", "true");
        }
    }

    @Inject
    TestData data;

    @BeforeEach
    void reset() {
        data.reset();
        data.user("alice");
    }

    private Response login(String address, String login, String password) {
        return given().header("X-Forwarded-For", address).contentType(ContentType.JSON)
                .body(Map.of("login", login, "password", password))
                .post("/api/auth/login");
    }

    @Test
    void anAddressGetsTenLoginsAMinute() {
        for (int i = 0; i < 10; i++) {
            login("10.1.0.1", "alice", TestData.PASSWORD).then().statusCode(200);
        }

        Response refused = login("10.1.0.1", "alice", TestData.PASSWORD);
        refused.then().statusCode(429)
                .contentType(ContentType.JSON)
                .body("message", matchesPattern("Too many attempts, try again in \\d+ seconds"));
        String retryAfterHeader = refused.header("Retry-After");
        int retryAfter;
        try {
            retryAfter = Integer.parseInt(retryAfterHeader);
        } catch (NumberFormatException e) {
            fail("Retry-After header is not a valid integer: " + retryAfterHeader, e);
            return;
        }
        assertTrue(retryAfter >= 1 && retryAfter <= 60);

        // Counted by the forwarded address: another client is not affected.
        login("10.1.0.2", "alice", TestData.PASSWORD).then().statusCode(200);
    }

    @Test
    void invalidRequestsCountToo() {
        for (int i = 0; i < 10; i++) {
            login("10.2.0.1", "", "").then().statusCode(400);
        }
        login("10.2.0.1", "", "").then().statusCode(429);
    }

    @Test
    void fiveFailedLoginsPauseTheAccountForThatAddressOnly() {
        for (int i = 0; i < 5; i++) {
            login("10.3.0.1", "alice", "wrong-password").then().statusCode(401);
        }

        login("10.3.0.1", "alice", TestData.PASSWORD).then().statusCode(429)
                .header("Retry-After", notNullValue());
        // Also by email address: it is the same account.
        login("10.3.0.1", "alice@example.com", TestData.PASSWORD).then().statusCode(429);
        login("10.3.0.2", "alice", TestData.PASSWORD).then().statusCode(200);
    }

    @Test
    void aSuccessfulLoginClearsTheFailedAttempts() {
        for (int i = 0; i < 4; i++) {
            login("10.4.0.1", "alice", "wrong-password").then().statusCode(401);
        }
        login("10.4.0.1", "alice", TestData.PASSWORD).then().statusCode(200);

        for (int i = 0; i < 4; i++) {
            login("10.4.0.1", "alice", "wrong-password").then().statusCode(401);
        }
        login("10.4.0.1", "alice", TestData.PASSWORD).then().statusCode(200);
    }

    @Test
    void unknownNamesGetTheSameAnswersAsAccounts() {
        for (int i = 0; i < 5; i++) {
            login("10.5.0.1", "nobody", "wrong-password").then().statusCode(401)
                    .body("message", is("Invalid credentials"));
        }
        login("10.5.0.1", "nobody", "wrong-password").then().statusCode(429);
    }

    @Test
    void otherEndpointsAreNotLimited() {
        for (int i = 0; i < 15; i++) {
            given().header("X-Forwarded-For", "10.6.0.1").post("/api/auth/refresh").then().statusCode(401);
        }
    }
}
