package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.token.RefreshCookie;
import com.beauver.printbridge.auth.token.RefreshTokenRepository;
import com.beauver.printbridge.auth.token.RefreshTokenService;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserService;
import com.beauver.printbridge.user.dto.UpdateUserRequest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class AuthResourceTest {

    @Inject
    TestData data;

    @Inject
    UserService userService;

    @Inject
    RefreshTokenRepository refreshTokens;

    @BeforeEach
    void reset() {
        data.reset();
    }

    private Response login(String login, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("login", login, "password", password))
                .post("/api/auth/login");
    }

    /** An admin setting a new password for the user, which logs them out everywhere. */
    private void resetPassword(User user) {
        User admin = data.admin();
        userService.updateUser(
                user.id, new UpdateUserRequest(user.username, user.email, true, "new-password1"), admin.id);
    }

    private static Map<String, Object> ownUpdate(
            String username,
            boolean changePassword,
            String newPassword,
            String currentPassword) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@example.com");
        body.put("changePassword", changePassword);
        body.put("newPassword", newPassword);
        body.put("currentPassword", currentPassword);
        return body;
    }

    private Response putMe(String accessToken, Map<String, Object> body) {
        return given().auth().oauth2(accessToken).contentType(ContentType.JSON).body(body).put("/api/auth/me");
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

    private Response refresh(String token) {
        return given().cookie(RefreshCookie.NAME, token).post("/api/auth/refresh");
    }

    /** Makes every replaced token look like it was replaced a minute ago: past the grace period. */
    private void endGracePeriod() {
        Instant earlier = Instant.now().minusSeconds(60);
        QuarkusTransaction.requiringNew()
                .run(() -> refreshTokens.update("rotatedAt = ?1 where rotatedAt is not null", earlier));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() {
        data.user("alice");
        String first = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);

        Response refreshed = refresh(first);
        refreshed.then().statusCode(200).body("accessToken", not(emptyOrNullString()));
        String second = refreshed.cookie(RefreshCookie.NAME);
        assertNotEquals(first, second);
        endGracePeriod();

        // Reusing the rotated token fails and also kills the newer one.
        refresh(first).then().statusCode(401);
        refresh(second).then().statusCode(401);
    }

    @Test
    void aTokenThatWasJustReplacedStillWorksForAnotherTab() {
        data.user("alice");
        String first = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);
        String second = refresh(first).cookie(RefreshCookie.NAME);

        Response otherTab = refresh(first);
        otherTab.then().statusCode(200).body("user.username", is("alice"));
        String third = otherTab.cookie(RefreshCookie.NAME);
        assertNotEquals(second, third);

        // Nothing was revoked: both new tokens work.
        refresh(second).then().statusCode(200);
        refresh(third).then().statusCode(200);
    }

    @Test
    void parallelRefreshesWithTheSameTokenAllSucceed() throws Exception {
        data.user("alice");
        String token = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);

        ExecutorService tabs = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int tab = 0; tab < 4; tab++) {
                statuses.add(tabs.submit(() -> refresh(token).statusCode()));
            }
            for (Future<Integer> status : statuses) {
                assertEquals(200, status.get());
            }
        } finally {
            tabs.shutdownNow();
        }
        // The token was replaced exactly once; the other tabs used the grace period.
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> refreshTokens.count("rotatedAt is not null")));
    }

    @Test
    void logoutAndPasswordResetEndTheGracePeriod() {
        User alice = data.user("alice");
        String first = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);
        String second = refresh(first).cookie(RefreshCookie.NAME);
        given().cookie(RefreshCookie.NAME, second).post("/api/auth/logout").then().statusCode(204);
        refresh(first).then().statusCode(401);

        String third = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);
        refresh(third).then().statusCode(200);
        resetPassword(alice);
        refresh(third).then().statusCode(401);
    }

    @Test
    void gettingANewTokenDeletesTheExpiredOnesOfThatUser() {
        data.user("alice");
        data.user("bob");
        String alice = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);
        login("bob", TestData.PASSWORD);
        refresh(alice);
        // alice: one replaced token and one active; bob: one active. Now they all expire.
        Instant past = Instant.now().minusSeconds(1);
        QuarkusTransaction.requiringNew().run(() -> refreshTokens.update("expiresAt = ?1", past));

        login("alice", TestData.PASSWORD).then().statusCode(200);

        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> refreshTokens.count("user.username", "alice")));
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> refreshTokens.count("user.username", "bob")));
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
        given().auth().oauth2(token).get("/api/auth/me").then().statusCode(401)
                .body("message", is(RefreshTokenService.INVALID));
    }

    @Test
    void accessTokenStopsWorkingAfterPasswordReset() throws InterruptedException {
        User alice = data.user("alice");
        String token = data.accessToken(alice);
        // Token iat has second precision; make sure the reset happens in a later second.
        Thread.sleep(1100);
        resetPassword(alice);
        given().auth().oauth2(token).get("/api/auth/me").then().statusCode(401);
        login("alice", "new-password1").then().statusCode(200);
    }

    @Test
    void updateMeChangesDetailsAndAnswersWithANewSession() {
        User alice = data.user("alice");
        String token = data.accessToken(alice);

        Response response = putMe(token, ownUpdate("alicia", false, null, TestData.PASSWORD));

        response.then().statusCode(200)
                .body("user.username", is("alicia"))
                .body("user.email", is("alicia@example.com"))
                .body("accessToken", not(emptyOrNullString()))
                .cookie(RefreshCookie.NAME, not(emptyOrNullString()));
        given().auth().oauth2(response.path("accessToken")).get("/api/auth/me")
                .then().statusCode(200).body("username", is("alicia"));
        // Only details changed, so the old token still works.
        given().auth().oauth2(token).get("/api/auth/me").then().statusCode(200);
        login("alicia", TestData.PASSWORD).then().statusCode(200);
    }

    @Test
    void updateMeWithNewPasswordEndsOtherSessionsButKeepsThisOne() throws InterruptedException {
        data.user("alice");
        String otherBrowser = login("alice", TestData.PASSWORD).cookie(RefreshCookie.NAME);
        String token = login("alice", TestData.PASSWORD).path("accessToken");
        // Token iat has second precision; make sure the change happens in a later second.
        Thread.sleep(1100);

        Response response = putMe(token, ownUpdate("alice", true, "new-password1", TestData.PASSWORD));

        response.then().statusCode(200);
        given().auth().oauth2(token).get("/api/auth/me").then().statusCode(401);
        refresh(otherBrowser).then().statusCode(401);
        given().auth().oauth2(response.path("accessToken")).get("/api/auth/me").then().statusCode(200);
        refresh(response.cookie(RefreshCookie.NAME)).then().statusCode(200);
        login("alice", TestData.PASSWORD).then().statusCode(401);
        login("alice", "new-password1").then().statusCode(200);
    }

    @Test
    void updateMeRequiresTheCurrentPassword() {
        User alice = data.user("alice");
        String token = data.accessToken(alice);
        putMe(token, ownUpdate("alicia", false, null, "wrong-password"))
                .then().statusCode(400).body("message", is("Current password is incorrect"));
        putMe(token, ownUpdate("alicia", false, null, ""))
                .then().statusCode(400).body(containsString("Current password is required"));
        given().auth().oauth2(token).get("/api/auth/me").then().body("username", is("alice"));
    }

    @Test
    void updateMeRejectsUsernameOfAnotherAccount() {
        User alice = data.user("alice");
        data.user("bob");
        putMe(data.accessToken(alice), ownUpdate("bob", false, null, TestData.PASSWORD))
                .then().statusCode(409).body("message", is("Username is already taken"));
    }

    @Test
    void updateMeRequiresToken() {
        given().contentType(ContentType.JSON).body(ownUpdate("alice", false, null, TestData.PASSWORD))
                .put("/api/auth/me").then().statusCode(401);
    }
}
