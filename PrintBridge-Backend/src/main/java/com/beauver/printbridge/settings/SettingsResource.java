package com.beauver.printbridge.settings;

import com.beauver.printbridge.settings.dto.BuildPlateDto;
import com.beauver.printbridge.settings.dto.SettingsDto;
import com.beauver.printbridge.settings.dto.SlicingSettingsDto;
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
    @Path("/build-plate")
    public SettingsDto updateBuildPlate(@Valid @NotNull BuildPlateDto buildPlate) {
        return service.updateBuildPlate(buildPlate);
    }

    @PUT
    @Path("/slicing")
    public SettingsDto updateSlicing(@Valid @NotNull SlicingSettingsDto slicing) {
        return service.updateSlicing(slicing);
    }
}
