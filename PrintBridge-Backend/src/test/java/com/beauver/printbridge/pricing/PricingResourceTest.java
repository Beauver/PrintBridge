package com.beauver.printbridge.pricing;

import com.beauver.printbridge.printrequest.RequestStatus;
import com.beauver.printbridge.spoolman.SpoolmanCatalog;
import com.beauver.printbridge.support.FakeSpoolmanClient;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** The price estimate settings and the estimate on requests and in the lists (#72). */
@QuarkusTest
class PricingResourceTest {

    @Inject
    TestData data;

    @Inject
    FakeSpoolmanClient spoolman;

    @Inject
    SpoolmanCatalog catalog;

    String adminToken;
    String bobToken;

    @BeforeEach
    void setUp() {
        data.reset();
        spoolman.reset();
        catalog.clear();
        adminToken = data.accessToken(data.admin());
        bobToken = data.accessToken(data.user("bob"));
    }

    private ValidatableResponse savePricing(String markup, String pricePer100Grams) {
        return given().auth().oauth2(adminToken).contentType(ContentType.JSON)
                .body(Map.of("markup", new BigDecimal(markup),
                        "pricePer100Grams", new BigDecimal(pricePer100Grams)))
                .put("/api/settings/pricing").then();
    }

    /** A request with a 50 mm cube (an STL, so its grams are an estimate), as bob. */
    private int cubeRequest() {
        return given().auth().oauth2(bobToken)
                .multiPart("name", "Cube")
                .multiPart("file", "cube.stl", ModelFiles.cubeStl(50), "application/octet-stream")
                .post("/api/requests").then().statusCode(201).extract().path("id");
    }

    private JsonPath request(String token, int id) {
        return given().auth().oauth2(token).get("/api/requests/" + id).then().statusCode(200).extract().jsonPath();
    }

    private static BigDecimal cents(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void settingsDefaultToNoAutomaticPrice() {
        given().auth().oauth2(adminToken).get("/api/settings").then().statusCode(200)
                .body("spoolman.prices", is(false))
                .body("pricing.markup", is(1.0f))
                .body("pricing.pricePer100Grams", is(0.0f));
        int id = cubeRequest();
        assertNull(request(bobToken, id).get("estimatedPrice"));
    }

    @Test
    void savesBothValuesAndValidatesThem() {
        savePricing("1.5", "2.5").statusCode(200)
                .body("pricing.markup", is(1.5f))
                .body("pricing.pricePer100Grams", is(2.5f));

        savePricing("0", "1").statusCode(400);
        savePricing("100.01", "1").statusCode(400);
        savePricing("1.555", "1").statusCode(400)
                .body("violations[0].message", is("Markup can have at most 2 decimals"));
        savePricing("1", "-1").statusCode(400);
        savePricing("1", "1000.01").statusCode(400);
        savePricing("1", "1.005").statusCode(400);
        given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .body(Map.of("markup", 1, "pricePer100Grams", 1))
                .put("/api/settings/pricing").then().statusCode(403);
    }

    @Test
    void spoolmanPricesWithoutADefaultOnlyPriceChosenFilaments() {
        data.setSpoolman(true, true, true);
        int id = cubeRequest();
        // Nothing chosen and no price per 100 g: nothing to price.
        assertNull(request(bobToken, id).get("estimatedPrice"));
    }

    @Test
    void estimatesFromThePricePer100Grams() {
        savePricing("1", "2.50").statusCode(200);
        int id = cubeRequest();
        int fileId = request(bobToken, id).getInt("files[0].id");
        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(Map.of("quantity", 2))
                .put("/api/requests/" + id + "/files/" + fileId + "/quantity").then().statusCode(200);

        JsonPath detail = request(bobToken, id);
        float grams = detail.getFloat("files[0].modelInfo.totalGrams");
        assertEquals(cents(grams * 2 / 100.0 * 2.5).doubleValue(), detail.getDouble("estimatedPrice.amount"), 0.001);
        assertFalse(detail.getBoolean("estimatedPrice.partial"));
        given().auth().oauth2(bobToken).get("/api/requests").then()
                .body("[0].estimatedPrice.amount", is(detail.getFloat("estimatedPrice.amount")));
        given().auth().oauth2(adminToken).get("/api/requests/all").then()
                .body("[0].estimatedPrice.partial", is(false));

        // A link has no grams: partial.
        given().auth().oauth2(bobToken).contentType(ContentType.JSON).body(Map.of("url", "https://example.com/m"))
                .post("/api/requests/" + id + "/links").then().statusCode(201)
                .body("estimatedPrice.partial", is(true));
    }

    @Test
    void theFinalPriceOrADeclineReplacesTheEstimate() {
        savePricing("1", "10").statusCode(200);
        int id = cubeRequest();
        assertNotNull(request(bobToken, id).get("estimatedPrice"));

        data.setPrice(id, new BigDecimal("12.00"));
        assertNull(request(bobToken, id).get("estimatedPrice"));
        given().auth().oauth2(bobToken).get("/api/requests").then().body("[0].estimatedPrice", nullValue());

        data.setPrice(id, null);
        data.setStatus(id, RequestStatus.DECLINED);
        assertNull(request(adminToken, id).get("estimatedPrice"));
    }

    @Test
    void estimatesFromSpoolmanPricesWithTheMarkupAndDensity() {
        data.setSpoolman(true, true, true);
        spoolman.filaments.add(FakeSpoolmanClient.priced(7, "PETG", 25, 1000, 2.48));
        savePricing("1.5", "99").statusCode(200);
        int id = cubeRequest();
        JsonPath before = request(bobToken, id);
        float plaGrams = before.getFloat("files[0].modelInfo.totalGrams");
        // No choice yet: the price per 100 g is the default, times the markup.
        assertEquals(cents(plaGrams * 0.99 * 1.5).doubleValue(), before.getDouble("estimatedPrice.amount"), 0.001);

        int fileId = before.getInt("files[0].id");
        JsonPath chosen = given().auth().oauth2(bobToken).contentType(ContentType.JSON)
                .body(Map.of("wholeItem", true, "spoolmanFilamentId", 7))
                .put("/api/requests/" + id + "/files/" + fileId + "/filaments")
                .then().statusCode(200).extract().jsonPath();

        // Twice PLA's density: about twice the grams (both rounded to whole grams).
        float grams = chosen.getFloat("files[0].modelInfo.totalGrams");
        assertEquals(plaGrams * 2, grams, 1.0);
        assertEquals(cents(grams * 0.025 * 1.5).doubleValue(), chosen.getDouble("estimatedPrice.amount"), 0.001);
        assertFalse(chosen.getBoolean("estimatedPrice.partial"));

        // Spoolman prices switched off: the price per 100 g (with the markup) applies to everything.
        data.setSpoolman(true, true, false);
        assertEquals(cents(grams / 100.0 * 99 * 1.5).doubleValue(),
                request(bobToken, id).getDouble("estimatedPrice.amount"), 0.001);

        // The integration switched off as well: the density is PLA's again.
        data.setSpoolman(false, false);
        JsonPath off = request(bobToken, id);
        assertEquals(plaGrams, off.getFloat("files[0].modelInfo.totalGrams"));
        assertEquals(cents(plaGrams / 100.0 * 99 * 1.5).doubleValue(), off.getDouble("estimatedPrice.amount"),
                0.001);
    }
}
