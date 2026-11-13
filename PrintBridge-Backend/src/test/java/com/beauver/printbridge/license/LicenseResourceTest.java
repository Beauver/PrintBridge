package com.beauver.printbridge.license;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class LicenseResourceTest {

    @Test
    void anyoneCanSeeTheLicenseAndWhereTheSourceIs() {
        given().get("/api/license").then()
                .statusCode(200)
                .body("license", is("AGPL-3.0-or-later"))
                .body("sourceUrl", is("https://github.com/Beauver/PrintBridge"));
    }
}
