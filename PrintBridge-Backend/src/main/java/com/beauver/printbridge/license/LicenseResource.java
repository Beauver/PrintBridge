package com.beauver.printbridge.license;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Public licence information (#16). The AGPL-3.0 asks that users of a network service can get
 * its source: whoever hosts a changed PrintBridge sets {@code SOURCE_URL} to their code.
 */
@Path("/license")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class LicenseResource {

    static final String LICENSE = "AGPL-3.0-or-later";

    @ConfigProperty(name = "printbridge.source-url")
    String sourceUrl;

    @GET
    public LicenseDto license() {
        return new LicenseDto(LICENSE, sourceUrl);
    }
}
