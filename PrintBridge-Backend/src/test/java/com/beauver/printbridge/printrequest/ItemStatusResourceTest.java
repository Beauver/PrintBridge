package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.dto.PrintRequestDto;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import com.beauver.printbridge.user.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Statuses of the files and links of a request (#61): PUT /requests/{id}/files/{fileId}/status. */
@QuarkusTest
class ItemStatusResourceTest {

    @Inject
    TestData data;

    @Inject
    EntityManager em;

    User bob;
    String adminToken;
    String bobToken;

    @BeforeEach
    void setUp() {
        data.reset();
        adminToken = data.accessToken(data.admin());
        bob = data.user("bob");
        bobToken = data.accessToken(bob);
    }

    private ValidatableResponse itemStatus(String token, long id, long fileId, String status, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("declineReason", reason);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .put("/api/requests/" + id + "/files/" + fileId + "/status").then();
    }

    private ValidatableResponse requestStatus(long id, String status) {
        return given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("status", status))
                .put("/api/requests/" + id + "/status").then();
    }

    /** A submitted request of bob with a file and a link. */
    private long[] requestWithFileAndLink() {
        PrintRequestDto r = data.printRequest(bob, "Box", RequestStatus.SUBMITTED);
        long link = given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .body(Map.of("url", "https://example.com/lid", "quantity", 2))
                .post("/api/requests/" + r.id() + "/links").then().statusCode(201)
                .extract().jsonPath().getLong("files[1].id");
        return new long[] {r.id(), r.files().getFirst().id(), link};
    }

    @Test
    void newItemsArePendingAndTheAdminMovesThemAlong() {
        long[] r = requestWithFileAndLink();
        given().auth().oauth2(bobToken).get("/api/requests/" + r[0]).then()
                .body("files.status", contains("PENDING", "PENDING"))
                .body("files[0].nextStatuses", contains("ACCEPTED", "DECLINED"))
                .body("totals.declined", is(0))
                .body("totals.completed", is(0));

        itemStatus(adminToken, r[0], r[1], "ACCEPTED", null).statusCode(200)
                .body("files[0].status", is("ACCEPTED"))
                .body("files[0].nextStatuses", contains("PRINTING", "COMPLETED", "DECLINED"))
                .body("files[1].status", is("PENDING"))
                .body("status", is("SUBMITTED"));
        itemStatus(adminToken, r[0], r[1], "PRINTING", null).statusCode(200);
        itemStatus(adminToken, r[0], r[1], "COMPLETED", null).statusCode(200)
                .body("totals.completed", is(1));
    }

    @Test
    void aDeclinedItemShowsItsReasonAndLeavesTheTotals() {
        long[] r = requestWithFileAndLink();

        itemStatus(adminToken, r[0], r[2], "DECLINED", " Not on Patreon any more ").statusCode(200)
                .body("files[1].status", is("DECLINED"))
                .body("files[1].declineReason", is("Not on Patreon any more"))
                .body("totals.links", is(1))
                .body("totals.declined", is(1))
                .body("totals.copies", is(1));
        given().auth().oauth2(bobToken).get("/api/requests/" + r[0]).then()
                .body("files[1].declineReason", is("Not on Patreon any more"));

        itemStatus(adminToken, r[0], r[2], "ACCEPTED", null).statusCode(200)
                .body("files[1].declineReason", nullValue())
                .body("totals.copies", is(3));
    }

    @Test
    void refusesWhatTheFlowDoesNotAllow() {
        long[] r = requestWithFileAndLink();

        itemStatus(adminToken, r[0], r[1], "PRINTING", null).statusCode(409)
                .body("message", is("Cannot change the item's status from PENDING to PRINTING"));
        itemStatus(adminToken, r[0], r[1], null, null).statusCode(400);
        itemStatus(adminToken, r[0], r[1], "NOT_A_STATUS", null).statusCode(400);
        itemStatus(adminToken, r[0], r[1], "DECLINED", "x".repeat(501)).statusCode(400);
        itemStatus(bobToken, r[0], r[1], "ACCEPTED", null).statusCode(403);
        itemStatus(adminToken, r[0], 999, "ACCEPTED", null).statusCode(404);
        itemStatus(adminToken, 999, r[1], "ACCEPTED", null).statusCode(404);

        requestStatus(r[0], "DECLINED").statusCode(200);
        itemStatus(adminToken, r[0], r[1], "ACCEPTED", null).statusCode(409)
                .body("message", is("This request is DECLINED and its items can no longer change"));
        given().auth().oauth2(adminToken).get("/api/requests/" + r[0]).then()
                .body("files[0].nextStatuses", empty());
    }

    @Test
    void theRequestMovesItsItemsAlong() {
        long[] r = requestWithFileAndLink();
        itemStatus(adminToken, r[0], r[2], "DECLINED", "Gone").statusCode(200);

        requestStatus(r[0], "ACCEPTED").statusCode(200)
                .body("files.status", contains("ACCEPTED", "DECLINED"));
        itemStatus(adminToken, r[0], r[1], "PRINTING", null).statusCode(200);
        requestStatus(r[0], "PRINTING").statusCode(200)
                .body("files.status", contains("PRINTING", "DECLINED"));

        given().auth().oauth2(adminToken).contentType(ContentType.JSON).body(Map.of("finalPrice", new BigDecimal("5")))
                .put("/api/requests/" + r[0] + "/price").then().statusCode(200);
        requestStatus(r[0], "COMPLETED").statusCode(200)
                .body("files.status", contains("COMPLETED", "DECLINED"))
                .body("files[1].declineReason", is("Gone"));
    }

    @Test
    void itemsAddedLaterStartPendingAndOwnerRulesStillApply() {
        long[] r = requestWithFileAndLink();
        itemStatus(adminToken, r[0], r[1], "DECLINED", null).statusCode(200);

        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(Map.of("quantity", 4))
                .put("/api/requests/" + r[0] + "/files/" + r[1] + "/quantity").then().statusCode(200);
        given().auth().oauth2(bobToken).get("/api/requests/" + r[0] + "/files/" + r[1]).then().statusCode(200);
        requestStatus(r[0], "ACCEPTED").statusCode(200);
        given().auth().oauth2(adminToken)
                .multiPart("file", "extra.stl", ModelFiles.binaryStl(1), "application/octet-stream")
                .post("/api/requests/" + r[0] + "/files").then().statusCode(201)
                .body("files[2].status", is("PENDING"));
    }

    @Test
    void theListsShowTheItemProgress() {
        long[] r = requestWithFileAndLink();
        data.printRequest(bob, "Other", RequestStatus.SUBMITTED);
        requestStatus(r[0], "ACCEPTED").statusCode(200);
        itemStatus(adminToken, r[0], r[1], "COMPLETED", null).statusCode(200);

        given().auth().oauth2(bobToken).get("/api/requests").then().statusCode(200)
                .body("find { it.name == 'Box' }.itemsCompleted", is(1))
                .body("find { it.name == 'Box' }.itemsActive", is(2))
                .body("find { it.name == 'Other' }.itemsCompleted", is(0))
                .body("find { it.name == 'Other' }.itemsActive", is(1));
        itemStatus(adminToken, r[0], r[2], "DECLINED", null).statusCode(200);
        given().auth().oauth2(adminToken).queryParam("status", "ALL").get("/api/requests/all").then()
                .body("find { it.name == 'Box' }.itemsActive", is(1));
    }

    /** The V12 migration's UPDATE gives existing items the status that matches their request. */
    @Test
    void theMigrationMapsExistingItemsFromTheirRequest() throws IOException {
        Map<RequestStatus, String> expected = Map.of(
                RequestStatus.SUBMITTED, "PENDING",
                RequestStatus.IN_REVIEW, "PENDING",
                RequestStatus.ACCEPTED, "ACCEPTED",
                RequestStatus.PRINTING, "ACCEPTED",
                RequestStatus.COMPLETED, "COMPLETED",
                RequestStatus.DECLINED, "DECLINED");
        Map<RequestStatus, Long> fileIds = new HashMap<>();
        for (RequestStatus status : expected.keySet()) {
            fileIds.put(status, data.printRequest(bob, status.name(), status).files().getFirst().id());
        }

        runMigrationUpdate();

        for (Map.Entry<RequestStatus, String> entry : expected.entrySet()) {
            org.junit.jupiter.api.Assertions.assertEquals(entry.getValue(), statusOf(fileIds.get(entry.getKey())),
                    "item of a " + entry.getKey() + " request");
        }
    }

    @Transactional
    void runMigrationUpdate() throws IOException {
        String migration;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V12__item_status.sql")) {
            migration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String update = migration.substring(migration.indexOf("UPDATE request_files"));
        em.createNativeQuery(update.substring(0, update.indexOf(';'))).executeUpdate();
    }

    @Transactional
    String statusOf(long fileId) {
        return (String) em.createNativeQuery("SELECT status FROM request_files WHERE id = ?1")
                .setParameter(1, fileId).getSingleResult();
    }
}
