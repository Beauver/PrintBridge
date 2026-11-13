package com.beauver.printbridge.about;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Public facts about this installation (#16). The AGPL-3.0 asks that users of a network service
 * can get its source: whoever hosts a changed PrintBridge sets {@code SOURCE_URL} to their code.
 */
@Path("/about")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class AboutResource {

    static final String LICENCE = "AGPL-3.0-or-later";

    @ConfigProperty(name = "printbridge.source-url")
    String sourceUrl;

    @GET
    public AboutDto about() {
        return new AboutDto(sourceUrl, LICENCE);
    }
}
