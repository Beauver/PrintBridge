package com.beauver.printbridge.about;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class AboutResourceTest {

    @Test
    void anyoneCanSeeWhereTheSourceIs() {
        given().get("/api/about").then()
                .statusCode(200)
                .body("sourceUrl", is("https://github.com/Beauver/PrintBridge"))
                .body("licence", is("AGPL-3.0-or-later"));
    }
}
