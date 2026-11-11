package com.beauver.printbridge.user;

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

    @Test
    void resetsPassword() {
        User bob = data.user("bob");
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("password", "brand-new-pw"))
                .put("/api/users/" + bob.id + "/password").then().statusCode(204);
        given().contentType(ContentType.JSON).body(Map.of("login", "bob", "password", "brand-new-pw"))
                .post("/api/auth/login").then().statusCode(200);
    }

    @Test
    void resetPasswordOfUnknownUserIsNotFound() {
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("password", "brand-new-pw"))
                .put("/api/users/9999/password").then().statusCode(404);
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
    }

    @Test
    void anonymousIsUnauthorized() {
        given().get("/api/users").then().statusCode(401);
    }
}
