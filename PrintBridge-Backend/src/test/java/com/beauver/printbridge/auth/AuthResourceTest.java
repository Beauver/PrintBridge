package com.beauver.printbridge.auth;

import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class AuthResourceTest {

    @Inject
    TestData data;

    @Inject
    UserService userService;

    @BeforeEach
    void reset() {
        data.reset();
    }

    private Response login(String login, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("login", login, "password", password))
                .post("/api/auth/login");
    }

    @Test
    void loginWithUsernameOrEmail() {
        data.user("alice");
        login("alice", TestData.PASSWORD).then().statusCode(200)
                .body("user.username", is("alice"))
                .body("user.role", is("USER"))
                .body("expiresIn", greaterThan(0))
                .cookie(RefreshCookie.NAME, not(emptyOrNullString()));
        login("ALICE@example.com", TestData.PASSWORD).then().statusCode(200);
    }

    @Test
    void wrongPasswordOrUnknownUserGivesSameError() {
        data.user("alice");
        login("alice", "wrong-password").then().statusCode(401).body("message", is("Invalid credentials"));
        login("nobody", "wrong-password").then().statusCode(401).body("message", is("Invalid credentials"));
    }

    @Test
    void loginValidatesInput() {
        login("", "").then().statusCode(400);
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() {
        data.user("alice");
        String first = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);

        Response refreshed = given().cookie(RefreshCookie.NAME, first).post("/api/auth/refresh");
        refreshed.then().statusCode(200).body("accessToken", not(emptyOrNullString()));
        String second = refreshed.cookie(RefreshCookie.NAME);
        assertNotEquals(first, second);

        // Reusing the rotated token fails and also kills the newer one.
        given().cookie(RefreshCookie.NAME, first).post("/api/auth/refresh").then().statusCode(401);
        given().cookie(RefreshCookie.NAME, second).post("/api/auth/refresh").then().statusCode(401);
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() {
        given().post("/api/auth/refresh").then().statusCode(401);
    }

    @Test
    void logoutRevokesRefreshToken() {
        data.user("alice");
        String token = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);
        given().cookie(RefreshCookie.NAME, token).post("/api/auth/logout").then().statusCode(204);
        given().cookie(RefreshCookie.NAME, token).post("/api/auth/refresh").then().statusCode(401);
    }

    @Test
    void meReturnsCurrentUser() {
        User alice = data.user("alice");
        given().auth().oauth2(data.accessToken(alice)).get("/api/auth/me")
                .then().statusCode(200).body("username", is("alice"));
    }

    @Test
    void meRequiresToken() {
        given().get("/api/auth/me").then().statusCode(401);
    }

    @Test
    void accessTokenStopsWorkingWhenUserIsDeleted() {
        User admin = data.admin();
        User alice = data.user("alice");
        String token = data.accessToken(alice);
        userService.delete(alice.id, admin.id);
        given().auth().oauth2(token).get("/api/auth/me").then().statusCode(401);
    }

    @Test
    void accessTokenStopsWorkingAfterPasswordReset() throws InterruptedException {
        User alice = data.user("alice");
        String token = data.accessToken(alice);
        // Token iat has second precision; make sure the reset happens in a later second.
        Thread.sleep(1100);
        userService.resetPassword(alice.id, "new-password1");
        given().auth().oauth2(token).get("/api/auth/me").then().statusCode(401);
        login("alice", "new-password1").then().statusCode(200);
    }
}
