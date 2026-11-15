package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.dto.SlicerPrinterDto;
import com.beauver.printbridge.slicing.dto.SlicerProfilesDto;
import com.beauver.printbridge.slicing.dto.SlicerStatusDto;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.List;

/** What the slicer service offers, for the settings page; admin only. 503 when it can't be used. */
@Path("/slicer")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Role.ADMIN_ROLE)
public class SlicerResource {

    @Inject
    SlicerService service;

    @GET
    @Path("/status")
    public SlicerStatusDto status() {
        return service.status();
    }

    @GET
    @Path("/printers")
    public List<SlicerPrinterDto> printers() {
        return service.printers();
    }

    @GET
    @Path("/profiles")
    public SlicerProfilesDto profiles(@QueryParam("printer") @NotBlank String printer,
                                      @QueryParam("nozzle") @NotNull @DecimalMin("0.05") @DecimalMax("5") BigDecimal nozzle) {
        return service.profiles(printer, nozzle);
    }
}
