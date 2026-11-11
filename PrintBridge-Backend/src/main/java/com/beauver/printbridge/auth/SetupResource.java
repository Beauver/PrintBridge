package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.dto.SetupStatus;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** First-run setup. Public, but only usable while no admin exists. */
@Path("/setup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class SetupResource {

    @Inject
    SetupService setupService;

    @Inject
    RefreshCookie refreshCookie;

    @GET
    public SetupStatus status() {
        return new SetupStatus(setupService.isSetupRequired());
    }

    @POST
    public Response createAdmin(@Valid @NotNull NewAccountRequest request) {
        AuthSession session = setupService.createAdmin(request);
        return Response.status(Response.Status.CREATED)
                .entity(session.response())
                .cookie(refreshCookie.create(session.refreshToken()))
                .build();
    }
}
