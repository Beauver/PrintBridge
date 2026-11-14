package com.beauver.printbridge.settings;

import com.beauver.printbridge.settings.dto.PrinterInputDto;
import com.beauver.printbridge.settings.dto.SettingsDto;
import com.beauver.printbridge.settings.dto.SlicingSettingsDto;
import com.beauver.printbridge.settings.dto.UploadSettingsDto;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Application settings; admin only. Every change answers with all settings. */
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
    @Path("/slicing")
    public SettingsDto updateSlicing(@Valid @NotNull SlicingSettingsDto slicing) {
        return service.updateSlicing(slicing);
    }

    @PUT
    @Path("/uploads")
    public SettingsDto updateUploads(@Valid @NotNull UploadSettingsDto uploads) {
        return service.updateUploads(uploads);
    }

    @POST
    @Path("/printers")
    public Response addPrinter(@Valid @NotNull PrinterInputDto printer) {
        return Response.status(Response.Status.CREATED).entity(service.addPrinter(printer)).build();
    }

    @PUT
    @Path("/printers/{id}")
    public SettingsDto updatePrinter(@PathParam("id") long id, @Valid @NotNull PrinterInputDto printer) {
        return service.updatePrinter(id, printer);
    }

    @DELETE
    @Path("/printers/{id}")
    public SettingsDto deletePrinter(@PathParam("id") long id) {
        return service.deletePrinter(id);
    }

    @PUT
    @Path("/printers/{id}/default")
    public SettingsDto makeDefault(@PathParam("id") long id) {
        return service.makeDefault(id);
    }
}
