package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.client.SlicerClient;
import com.beauver.printbridge.slicing.client.SlicerException;
import com.beauver.printbridge.slicing.client.SlicerPrinter;
import com.beauver.printbridge.slicing.client.SlicerProfiles;
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
    SlicerClient client;

    @GET
    @Path("/status")
    public SlicerStatusDto status() {
        if (!client.isConfigured()) {
            return new SlicerStatusDto(false, false, null, "Server slicing isn't set up (SLICER_URL is missing)");
        }
        try {
            return new SlicerStatusDto(true, true, client.version(), null);
        } catch (SlicerException e) {
            return new SlicerStatusDto(true, false, null, e.getMessage());
        }
    }

    @GET
    @Path("/printers")
    public List<SlicerPrinter> printers() {
        return client.printers();
    }

    @GET
    @Path("/profiles")
    public SlicerProfiles profiles(@QueryParam("printer") @NotBlank String printer,
                                   @QueryParam("nozzle") @NotNull @DecimalMin("0.05") @DecimalMax("5") BigDecimal nozzle) {
        return client.profiles(printer, nozzle);
    }
}
