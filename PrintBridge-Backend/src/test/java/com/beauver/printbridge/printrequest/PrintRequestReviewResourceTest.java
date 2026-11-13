package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.user.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Admin overview and review: GET /requests/all, PUT /requests/{id}/status and /price. */
@QuarkusTest
class PrintRequestReviewResourceTest {

    @Inject
    TestData data;

    User bob;
    User carol;
    String adminToken;
    String bobToken;

    @BeforeEach
    void setUp() {
        data.reset();
        adminToken = data.accessToken(data.admin());
        bob = data.user("bob");
        carol = data.user("carol");
        bobToken = data.accessToken(bob);
    }

    private ValidatableResponse listAll(String name, String value) {
        var spec = given().auth().oauth2(adminToken);
        if (name != null) {
            spec = spec.queryParam(name, value);
        }
        return spec.get("/api/requests/all").then();
    }

    private ValidatableResponse status(String token, long id, String status, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("declineReason", reason);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .put("/api/requests/" + id + "/status").then();
    }

    private ValidatableResponse price(String token, long id, Object price) {
        Map<String, Object> body = new HashMap<>();
        body.put("finalPrice", price);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .put("/api/requests/" + id + "/price").then();
    }

    // --- overview ---

    @Test
    void listsOpenRequestsOfEveryoneByDefault() {
        data.printRequest(bob, "Benchy", RequestStatus.SUBMITTED);
        data.printRequest(carol, "Vase", RequestStatus.ACCEPTED);
        data.printRequest(carol, "Lamp", RequestStatus.PRINTING);
        data.printRequest(carol, "Old one", RequestStatus.COMPLETED);

        listAll(null, null).statusCode(200)
                .body("name", contains("Lamp", "Vase", "Benchy"))
                .body("ownerUsername", contains("carol", "carol", "bob"));
        listAll("status", "ALL").body("name", contains("Old one", "Lamp", "Vase", "Benchy"));
        listAll("status", "completed").body("name", contains("Old one"));
        listAll("status", "printing").body("name", contains("Lamp"));
    }

    @Test
    void searchesNameAndUsernameIgnoringCase() {
        data.printRequest(bob, "Benchy", RequestStatus.SUBMITTED);
        data.printRequest(carol, "Vase", RequestStatus.SUBMITTED);
        data.printRequest(carol, "100% infill", RequestStatus.SUBMITTED);

        listAll("q", "BENCH").body("name", contains("Benchy"));
        listAll("q", "car").body("name", contains("100% infill", "Vase"));
        listAll("q", "%").body("name", contains("100% infill"));
        listAll("q", "_").body("$", empty());
        listAll("q", "  ").body("$", hasSize(3));
    }

    @Test
    void unknownStatusFilterIsBadRequest() {
        listAll("status", "DONE").statusCode(400).body("message", is("Unknown status filter: DONE"));
    }

    // --- status ---

    @Test
    void adminMovesRequestThroughTheFlow() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);

        status(adminToken, r.id(), "IN_REVIEW", null).statusCode(200)
                .body("status", is("IN_REVIEW"))
                .body("nextStatuses", contains("ACCEPTED", "DECLINED"));
        status(adminToken, r.id(), "ACCEPTED", null).statusCode(200)
                .body("nextStatuses", contains("PRINTING", "COMPLETED"));
        status(adminToken, r.id(), "PRINTING", null).statusCode(200)
                .body("status", is("PRINTING"))
                .body("nextStatuses", contains("COMPLETED", "ACCEPTED", "DECLINED"));
        status(adminToken, r.id(), "COMPLETED", null)
                .statusCode(409).body("message", is("Set a final price before completing the request"));
        price(adminToken, r.id(), 12.5).statusCode(200);
        status(adminToken, r.id(), "COMPLETED", null).statusCode(200)
                .body("status", is("COMPLETED"))
                .body("nextStatuses", empty());

        given().auth().oauth2(bobToken).get("/api/requests/" + r.id())
                .then().body("status", is("COMPLETED")).body("finalPrice", is(12.5f));
    }

    @Test
    void printingCanGoBackToAcceptedOrBeDeclined() {
        PrintRequestDto failed = data.printRequest(bob, RequestStatus.PRINTING);
        status(adminToken, failed.id(), "ACCEPTED", null).statusCode(200).body("status", is("ACCEPTED"));

        PrintRequestDto impossible = data.printRequest(bob, RequestStatus.PRINTING);
        status(adminToken, impossible.id(), "DECLINED", "Warps on every try").statusCode(200)
                .body("status", is("DECLINED"))
                .body("declineReason", is("Warps on every try"));

        PrintRequestDto submitted = data.printRequest(bob, RequestStatus.SUBMITTED);
        status(adminToken, submitted.id(), "PRINTING", null)
                .statusCode(409).body("message", is("Cannot change status from SUBMITTED to PRINTING"));
    }

    @Test
    void priceAndPaidStillChangeWhilePrinting() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.PRINTING);

        price(adminToken, r.id(), 20).statusCode(200).body("finalPrice", is(20));
        paid(adminToken, r.id(), true).statusCode(200).body("paidAt", notNullValue());
        paid(adminToken, r.id(), false).statusCode(200).body("paidAt", nullValue());
    }

    @Test
    void declineReasonIsShownToTheOwner() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        status(adminToken, r.id(), "DECLINED", " Too big ").statusCode(200);

        given().auth().oauth2(bobToken).get("/api/requests/" + r.id())
                .then().body("status", is("DECLINED")).body("declineReason", is("Too big"));
    }

    @Test
    void invalidTransitionsAndFinalRequestsAreConflicts() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.ACCEPTED);
        status(adminToken, r.id(), "SUBMITTED", null)
                .statusCode(409).body("message", is("Cannot change status from ACCEPTED to SUBMITTED"));

        PrintRequestDto declined = data.printRequest(bob, RequestStatus.DECLINED);
        status(adminToken, declined.id(), "IN_REVIEW", null)
                .statusCode(409).body("message", is("This request is DECLINED and can no longer be changed"));
        price(adminToken, declined.id(), 5).statusCode(409);
    }

    @Test
    void invalidStatusBodyIsBadRequest() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        status(adminToken, r.id(), null, null).statusCode(400);
        status(adminToken, r.id(), "DECLINED", "x".repeat(2001)).statusCode(400);
        status(adminToken, r.id(), "NOPE", null).statusCode(400);
    }

    @Test
    void unknownRequestIsNotFound() {
        status(adminToken, 999, "IN_REVIEW", null).statusCode(404);
        price(adminToken, 999, 1).statusCode(404);
    }

    // --- price ---

    @Test
    void adminSetsChangesAndClearsPrice() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.IN_REVIEW);

        price(adminToken, r.id(), 0).statusCode(200).body("finalPrice", is(0));
        price(adminToken, r.id(), new BigDecimal("99999999.99")).statusCode(200);
        price(adminToken, r.id(), null).statusCode(200).body("finalPrice", nullValue());

        given().auth().oauth2(bobToken).get("/api/requests").then().body("[0].finalPrice", nullValue());
    }

    @Test
    void invalidPricesAreBadRequest() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        price(adminToken, r.id(), -1).statusCode(400);
        price(adminToken, r.id(), new BigDecimal("1.234")).statusCode(400);
        price(adminToken, r.id(), new BigDecimal("100000000")).statusCode(400);
    }

    // --- paid ---

    private ValidatableResponse paid(String token, long id, Object paid) {
        Map<String, Object> body = new HashMap<>();
        body.put("paid", paid);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .put("/api/requests/" + id + "/paid").then();
    }

    @Test
    void adminMarksCompletedRequestPaidAndBack() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.ACCEPTED);
        paid(adminToken, r.id(), true)
                .statusCode(409).body("message", is("Set a final price before marking the request as paid"));
        price(adminToken, r.id(), 20).statusCode(200);
        status(adminToken, r.id(), "COMPLETED", null).statusCode(200);

        paid(adminToken, r.id(), true).statusCode(200).body("paidAt", notNullValue());
        given().auth().oauth2(bobToken).get("/api/requests").then().body("[0].paidAt", notNullValue());

        paid(adminToken, r.id(), false).statusCode(200).body("paidAt", nullValue());
    }

    @Test
    void paidRequestPriceIsLocked() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.ACCEPTED);
        price(adminToken, r.id(), 20).statusCode(200);
        paid(adminToken, r.id(), true).statusCode(200);

        price(adminToken, r.id(), 25)
                .statusCode(409).body("message", is("Mark the request as unpaid before changing the price"));
        price(adminToken, r.id(), null).statusCode(409);
    }

    @Test
    void paidIsRefusedOutsideAcceptedAndCompleted() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.IN_REVIEW);
        price(adminToken, r.id(), 20).statusCode(200);
        paid(adminToken, r.id(), true)
                .statusCode(409).body("message", is("Only accepted, printing or completed requests can be marked as paid"));
        paid(adminToken, r.id(), null).statusCode(400);
        paid(adminToken, 999, true).statusCode(404);
    }

    // --- access ---

    @Test
    void usersCannotReview() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        given().auth().oauth2(bobToken).get("/api/requests/all").then().statusCode(403);
        status(bobToken, r.id(), "IN_REVIEW", null).statusCode(403);
        price(bobToken, r.id(), 1).statusCode(403);
        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(Map.of("paid", true))
                .put("/api/requests/" + r.id() + "/paid").then().statusCode(403);
        given().get("/api/requests/all").then().statusCode(401);
    }
}
