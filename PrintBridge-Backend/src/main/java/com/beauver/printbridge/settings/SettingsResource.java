package com.beauver.printbridge.settings;

import com.beauver.printbridge.settings.dto.SettingsDto;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** Application settings; admin only. */
@Path("/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Role.ADMIN_ROLE)
public class SettingsResource {

    @Inject
    SettingsService service;

    @GET
    public SettingsDto get() {
        return service.get();
    }

    @PUT
    public SettingsDto update(@Valid @NotNull SettingsDto settings) {
        return service.update(settings);
    }
}
