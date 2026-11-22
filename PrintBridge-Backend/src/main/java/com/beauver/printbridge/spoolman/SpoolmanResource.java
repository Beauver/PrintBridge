package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.spoolman.dto.SpoolmanFilamentDto;
import com.beauver.printbridge.spoolman.dto.SpoolmanStatusDto;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Spoolman (#11): its status for the settings page, and the filaments requesters can choose. */
@Path("/spoolman")
@Produces(MediaType.APPLICATION_JSON)
public class SpoolmanResource {

    @Inject
    SpoolmanService service;

    @GET
    @Path("/status")
    @RolesAllowed(Role.ADMIN_ROLE)
    public SpoolmanStatusDto status() {
        return service.status();
    }

    /** 409 while colour selection is off, 503 when Spoolman can't be read. */
    @GET
    @Path("/filaments")
    @RolesAllowed({Role.USER_ROLE, Role.ADMIN_ROLE})
    public List<SpoolmanFilamentDto> filaments() {
        return service.filaments();
    }
}
