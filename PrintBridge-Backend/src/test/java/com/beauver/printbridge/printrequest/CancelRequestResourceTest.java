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

/** The requester cancels a request before it is accepted (#85). */
@QuarkusTest
class CancelRequestResourceTest {

    @Inject
    TestData data;

    User bob;
    String adminToken;
    String bobToken;
    String eveToken;

    @BeforeEach
    void setUp() {
        data.reset();
        bob = data.user("bob");
        adminToken = data.accessToken(data.admin());
        bobToken = data.accessToken(bob);
        eveToken = data.accessToken(data.user("eve"));
    }

    private ValidatableResponse cancel(String token, long requestId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .post("/api/requests/" + requestId + "/cancel").then();
    }

    @Test
    void ownerCancelsASubmittedRequestWithAReason() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        cancel(bobToken, r.id(), "  Found one in a shop ").statusCode(200)
                .body("status", is("CANCELLED"))
                .body("cancelReason", is("Found one in a shop"))
                .body("declineReason", nullValue())
                .body("nextStatuses", empty())
                .body("files[0].status", is("PENDING"))
                .body("estimatedPrice", nullValue());

        given().auth().oauth2(adminToken).get("/api/requests/" + r.id()).then()
                .body("status", is("CANCELLED")).body("cancelReason", is("Found one in a shop"));
    }

    @Test
    void ownerCancelsAnInReviewRequestWithoutAReason() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.IN_REVIEW);
        cancel(bobToken, r.id(), "   ").statusCode(200)
                .body("status", is("CANCELLED")).body("cancelReason", nullValue());

        PrintRequestDto withoutBody = data.printRequest(bob, RequestStatus.SUBMITTED);
        given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .post("/api/requests/" + withoutBody.id() + "/cancel").then()
                .statusCode(200).body("cancelReason", nullValue());
    }

    @Test
    void onlyBeforeAcceptance() {
        for (RequestStatus status : new RequestStatus[]{RequestStatus.ACCEPTED, RequestStatus.PRINTING,
                RequestStatus.DECLINED, RequestStatus.COMPLETED, RequestStatus.CANCELLED}) {
            PrintRequestDto r = data.printRequest(bob, status);
            cancel(bobToken, r.id(), null).statusCode(409)
                    .body("message", is("Only a submitted or in-review request can be cancelled"));
        }
    }

    @Test
    void onlyTheOwnerCancels() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        cancel(eveToken, r.id(), null).statusCode(404);
        cancel(adminToken, r.id(), null).statusCode(403);
        cancel(bobToken, 999, null).statusCode(404);
        given().contentType(ContentType.JSON).post("/api/requests/" + r.id() + "/cancel").then().statusCode(401);
    }

    @Test
    void reasonIsAtMost2000Characters() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        cancel(bobToken, r.id(), "x".repeat(2001)).statusCode(400)
                .body("violations[0].message", is("Reason must be at most 2000 characters"));
        cancel(bobToken, r.id(), "x".repeat(2000)).statusCode(200);
    }

    @Test
    void aCancelledRequestNoLongerChanges() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        long fileId = r.files().getFirst().id();
        cancel(bobToken, r.id(), null).statusCode(200);

        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(Map.of("name", "New"))
                .put("/api/requests/" + r.id()).then().statusCode(409);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("name", "New"))
                .put("/api/requests/" + r.id()).then().statusCode(409);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("quantity", 2))
                .put("/api/requests/" + r.id() + "/files/" + fileId + "/quantity").then().statusCode(409);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("status", "ACCEPTED"))
                .put("/api/requests/" + r.id() + "/status").then().statusCode(409);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("finalPrice", BigDecimal.TEN))
                .put("/api/requests/" + r.id() + "/price").then().statusCode(409);
        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("status", "ACCEPTED"))
                .put("/api/requests/" + r.id() + "/files/" + fileId + "/status").then().statusCode(409);
    }

    @Test
    void cancelledIsNotOpenButHasItsOwnFilter() {
        PrintRequestDto r = data.printRequest(bob, RequestStatus.SUBMITTED);
        cancel(bobToken, r.id(), null).statusCode(200);

        given().auth().oauth2(adminToken).get("/api/requests/all").then().body("id", not(hasItem(r.id().intValue())));
        given().auth().oauth2(adminToken).queryParam("status", "ALL").get("/api/requests/all").then()
                .body("id", hasItem(r.id().intValue()));
        given().auth().oauth2(adminToken).queryParam("status", "cancelled").get("/api/requests/all").then()
                .body("status", everyItem(is("CANCELLED"))).body("id", hasItem(r.id().intValue()));
    }
}
