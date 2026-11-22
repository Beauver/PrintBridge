package com.beauver.printbridge.user;

import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class UserResourceTest {

    @Inject
    TestData data;

    User admin;
    String adminToken;

    @BeforeEach
    void setUp() {
        data.reset();
        admin = data.admin();
        adminToken = data.accessToken(admin);
    }

    private Map<String, String> account(String username) {
        return Map.of("username", username, "email", username + "@example.com", "password", "password123");
    }

    @Test
    void listsUsers() {
        data.user("bob");
        given().auth().oauth2(adminToken).get("/api/users")
                .then().statusCode(200)
                .body("username", contains("admin", "bob"))
                .body("[0]", not(hasKey("passwordHash")));
    }

    @Test
    void createsUser() {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(account("carol"))
                .post("/api/users")
                .then().statusCode(201)
                .body("username", is("carol"))
                .body("role", is("USER"));

        given().contentType(ContentType.JSON).body(Map.of("login", "carol", "password", "password123"))
                .post("/api/auth/login").then().statusCode(200);
    }

    @Test
    void rejectsDuplicateUsernameAndEmail() {
        data.user("bob");
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(account("BOB"))
                .post("/api/users").then().statusCode(409).body("message", is("Username is already taken"));
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("username", "other", "email", "bob@example.com", "password", "password123"))
                .post("/api/users").then().statusCode(409).body("message", is("Email is already in use"));
    }

    @Test
    void rejectsInvalidUser() {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("username", "a b", "email", "nope", "password", "123"))
                .post("/api/users").then().statusCode(400);
    }

    private static Map<String, Object> update(String username, String email, boolean changePassword, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("changePassword", changePassword);
        body.put("newPassword", password);
        return body;
    }

    private Response putUser(long id, Map<String, Object> body) {
        return given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(body).put("/api/users/" + id);
    }

    @Test
    void updatesDetailsWithoutEndingSessions() {
        User bob = data.user("bob");
        String bobToken = data.accessToken(bob);

        putUser(bob.id, update("robert", "robert@example.com", false, null))
                .then().statusCode(200)
                .body("username", is("robert"))
                .body("email", is("robert@example.com"));

        given().auth().oauth2(bobToken).get("/api/auth/me").then().statusCode(200).body("username", is("robert"));
        given().contentType(ContentType.JSON).body(Map.of("login", "robert", "password", TestData.PASSWORD))
                .post("/api/auth/login").then().statusCode(200);
    }

    @Test
    void updatesPassword() {
        User bob = data.user("bob");
        putUser(bob.id, update("bob", "bob@example.com", true, "brand-new-pw")).then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("login", "bob", "password", "brand-new-pw"))
                .post("/api/auth/login").then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("login", "bob", "password", TestData.PASSWORD))
                .post("/api/auth/login").then().statusCode(401);
    }

    @Test
    void changingPasswordRequiresANewPassword() {
        User bob = data.user("bob");
        putUser(bob.id, update("bob", "bob@example.com", true, null))
                .then().statusCode(400).body(containsString("Password is required"));
        putUser(bob.id, update("bob", "bob@example.com", true, "short"))
                .then().statusCode(400).body(containsString("Password must be at least 8 characters"));
    }

    @Test
    void rejectsUsernameOrEmailOfAnotherAccount() {
        User bob = data.user("bob");
        data.user("carol");
        putUser(bob.id, update("CAROL", "bob@example.com", false, null))
                .then().statusCode(409).body("message", is("Username is already taken"));
        putUser(bob.id, update("bob", "carol@example.com", false, null))
                .then().statusCode(409).body("message", is("Email is already in use"));
        putUser(bob.id, update("BOB", "BOB@example.com", false, null)).then().statusCode(200);
    }

    @Test
    void rejectsInvalidUpdate() {
        User bob = data.user("bob");
        putUser(bob.id, update("a b", "nope", false, null)).then().statusCode(400);
    }

    @Test
    void adminUpdatesThemselvesOnMyAccount() {
        putUser(admin.id, update("boss", "admin@example.com", false, null))
                .then().statusCode(409).body("message", is("Update your own account on the My account page"));
    }

    @Test
    void passwordsLongerThanBcryptHashesAreRefused() {
        User bob = data.user("bob");
        // 18 emoji of 4 bytes each plus one letter: 37 characters but 73 bytes.
        String tooLong = "\uD83D\uDE00".repeat(18) + "a";
        String justFits = "\uD83D\uDE00".repeat(18);
        String asciiLimit = "a".repeat(72);

        putUser(bob.id, update("bob", "bob@example.com", true, tooLong))
                .then().statusCode(400).body(containsString("Password is too long"));
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("username", "dave", "email", "dave@example.com", "password", "a".repeat(73)))
                .post("/api/users").then().statusCode(400).body(containsString("Password is too long"));

        putUser(bob.id, update("bob", "bob@example.com", true, justFits)).then().statusCode(200);
        putUser(bob.id, update("bob", "bob@example.com", true, asciiLimit)).then().statusCode(200);
    }

    @Test
    void updateOfUnknownUserIsNotFound() {
        putUser(9999, update("ghost", "ghost@example.com", false, null)).then().statusCode(404);
    }

    @Test
    void deletesUser() {
        User bob = data.user("bob");
        given().auth().oauth2(adminToken).delete("/api/users/" + bob.id).then().statusCode(204);
        given().auth().oauth2(adminToken).get("/api/users").then().body("username", contains("admin"));
    }

    @Test
    void adminCannotDeleteSelf() {
        given().auth().oauth2(adminToken).delete("/api/users/" + admin.id)
                .then().statusCode(409).body("message", is("You cannot delete your own account"));
    }

    @Test
    void usersCannotManageAccounts() {
        String userToken = data.accessToken(data.user("bob"));
        given().auth().oauth2(userToken).get("/api/users").then().statusCode(403);
        given().auth().oauth2(userToken).delete("/api/users/" + admin.id).then().statusCode(403);
        given().auth().oauth2(userToken).contentType(ContentType.JSON)
                .body(update("admin", "admin@example.com", false, null))
                .put("/api/users/" + admin.id).then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorized() {
        given().get("/api/users").then().statusCode(401);
    }
}
