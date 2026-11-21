package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.CurrentUser;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceRequest;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.printrequest.file.FileBundle;
import com.beauver.printbridge.printrequest.file.NewLink;
import com.beauver.printbridge.printrequest.file.RequestFile;
import com.beauver.printbridge.printrequest.file.StoredFile;
import com.beauver.printbridge.printrequest.file.UploadedModel;
import com.beauver.printbridge.slicing.dto.ResliceOptionsDto;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Print requests: users manage their own; the admin can open, review and delete any of them. */
@Path("/requests")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Role.USER_ROLE, Role.ADMIN_ROLE})
public class PrintRequestResource {

    @Inject
    PrintRequestService service;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<PrintRequestSummaryDto> listOwn() {
        return service.listOwn(currentUser.id());
    }

    /**
     * Admin overview. {@code status}: {@code OPEN} (default), {@code ALL} or a single status;
     * {@code q}: matches request name or username.
     */
    @GET
    @Path("/all")
    @RolesAllowed(Role.ADMIN_ROLE)
    public List<PrintRequestSummaryDto> listAll(@QueryParam("status") String status, @QueryParam("q") String search) {
        return service.listAll(StatusFilter.parse(status), search);
    }

    @GET
    @Path("/upload-config")
    public UploadConfigDto uploadConfig() {
        return service.uploadConfig();
    }

    /** Creates a request with its first entry: a model file, or a link ({@code url}, {@code linkName}; #59). */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response create(@Valid NewPrintRequestForm form) {
        boolean hasLink = form.url != null && !form.url.isBlank();
        if ((form.file != null) == hasLink) {
            throw new InvalidInputException("Add a model file or a link");
        }
        UploadedModel file = null;
        NewLink link = null;
        if (form.file != null) {
            file = upload(form.file);
        } else {
            link = new NewLink(form.url, form.linkName);
        }
        PrintRequestDto created = service.create(currentUser.id(),
                new NewPrintRequest(form.name, form.description, file, link, quantityOrOne(form.quantity)));
        return Response.created(UriBuilder.fromPath("/api/requests/{id}").build(created.id()))
                .entity(created)
                .build();
    }

    @GET
    @Path("/{id}")
    public PrintRequestDto get(@PathParam("id") long id) {
        return service.get(id, currentUser.caller());
    }

    /** Adds a model file to the request (#23); answers with the whole request. */
    @POST
    @Path("/{id}/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addFile(@PathParam("id") long id, @Valid NewFileForm form) {
        PrintRequestDto updated = service.addFile(id, upload(form.file), quantityOrOne(form.quantity),
                currentUser.caller());
        return Response.status(Response.Status.CREATED).entity(updated).build();
    }

    /** Adds a link to the request (#59); answers with the whole request. */
    @POST
    @Path("/{id}/links")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addLink(@PathParam("id") long id, @Valid @NotNull NewLinkRequest request) {
        PrintRequestDto updated = service.addLink(id, new NewLink(request.url(), request.name()),
                quantityOrOne(request.quantity()), currentUser.caller());
        return Response.status(Response.Status.CREATED).entity(updated).build();
    }

    /** Removes a file or a link from the request. */
    @DELETE
    @Path("/{id}/files/{fileId}")
    public PrintRequestDto removeFile(@PathParam("id") long id, @PathParam("fileId") long fileId) {
        return service.removeFile(id, fileId, currentUser.caller());
    }

    @PUT
    @Path("/{id}/files/{fileId}/quantity")
    @Consumes(MediaType.APPLICATION_JSON)
    public PrintRequestDto changeQuantity(@PathParam("id") long id, @PathParam("fileId") long fileId,
                                          @Valid @NotNull QuantityChangeRequest request) {
        return service.changeQuantity(id, fileId, request.quantity(), currentUser.caller());
    }

    /**
     * Chooses a Spoolman filament for one colour of a plate, or for the whole item (#11). 409 while
     * colour selection is off or the request may no longer change for the caller.
     */
    @PUT
    @Path("/{id}/files/{fileId}/filaments")
    @Consumes(MediaType.APPLICATION_JSON)
    public PrintRequestDto chooseFilament(@PathParam("id") long id, @PathParam("fileId") long fileId,
                                          @Valid @NotNull FilamentChoiceRequest choice) {
        return service.chooseFilament(id, fileId, choice, currentUser.caller());
    }

    /** Removes a chosen filament; the colour goes back to the file's own (#11). */
    @DELETE
    @Path("/{id}/files/{fileId}/filaments/{choiceId}")
    public PrintRequestDto removeFilamentChoice(@PathParam("id") long id, @PathParam("fileId") long fileId,
                                                @PathParam("choiceId") long choiceId) {
        return service.removeFilamentChoice(id, fileId, choiceId, currentUser.caller());
    }

    /** Every file of the request in one ZIP, under its original name, plus its links in a text file (#59). */
    @GET
    @Path("/{id}/files.zip")
    @Produces({"application/zip", MediaType.APPLICATION_JSON})
    public Response downloadAll(@PathParam("id") long id) {
        FileBundle bundle = service.files(id, currentUser.caller());
        // Streamed, so a large request isn't held in memory. Every file was checked to be on disk.
        StreamingOutput zip = output -> {
            try (ZipOutputStream out = new ZipOutputStream(output)) {
                for (StoredFile file : bundle.files()) {
                    out.putNextEntry(new ZipEntry(file.filename()));
                    Files.copy(file.path(), out);
                    out.closeEntry();
                }
                if (bundle.linksText() != null) {
                    out.putNextEntry(new ZipEntry(FileBundle.LINKS_FILENAME));
                    out.write(bundle.linksText().getBytes(StandardCharsets.UTF_8));
                    out.closeEntry();
                }
            }
        };
        return Response.ok(zip, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(bundle.name()))
                .build();
    }

    @GET
    @Path("/{id}/files/{fileId}")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
    public Response download(@PathParam("id") long id, @PathParam("fileId") long fileId) {
        StoredFile file = service.file(id, fileId, currentUser.caller());
        return Response.ok(file.path().toFile(), file.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.filename()))
                .header(HttpHeaders.CONTENT_LENGTH, file.size())
                .build();
    }

    /** Slices one file of the request again with a chosen printer, nozzle and profiles (admin; #21). */
    @POST
    @Path("/{id}/files/{fileId}/reslice")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto resliceWith(@PathParam("id") long id, @PathParam("fileId") long fileId,
                                       @Valid @NotNull CustomResliceRequest choice) {
        return service.resliceWith(id, fileId, choice);
    }

    /** What the "Re-slice with…" dialog of one file offers (admin; #21). */
    @GET
    @Path("/{id}/files/{fileId}/reslice")
    @RolesAllowed(Role.ADMIN_ROLE)
    public ResliceOptionsDto resliceOptions(@PathParam("id") long id, @PathParam("fileId") long fileId) {
        return service.resliceOptions(id, fileId);
    }

    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto changeStatus(@PathParam("id") long id, @Valid @NotNull StatusChangeRequest request) {
        return service.changeStatus(id, request.status(), request.declineReason());
    }

    /** Moves one file or link of the request to another status (#61; admin). */
    @PUT
    @Path("/{id}/files/{fileId}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto changeItemStatus(@PathParam("id") long id, @PathParam("fileId") long fileId,
                                            @Valid @NotNull ItemStatusChangeRequest request) {
        return service.changeItemStatus(id, fileId, request.status(), request.declineReason());
    }

    @PUT
    @Path("/{id}/price")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto changePrice(@PathParam("id") long id, @Valid @NotNull PriceChangeRequest request) {
        return service.changePrice(id, request.finalPrice());
    }

    @PUT
    @Path("/{id}/paid")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto changePaid(@PathParam("id") long id, @Valid @NotNull PaidChangeRequest request) {
        return service.changePaid(id, request.paid());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") long id) {
        service.delete(id, currentUser.caller());
        return Response.noContent().build();
    }

    private static UploadedModel upload(FileUpload file) {
        return new UploadedModel(file.fileName(), file.uploadedFile(), file.size());
    }

    private static int quantityOrOne(Integer quantity) {
        if (quantity == null) {
            return RequestFile.MIN_QUANTITY;
        }
        return quantity;
    }

    /** RFC 6266: an ASCII fallback plus the exact UTF-8 name for browsers that support it. */
    static String contentDisposition(String filename) {
        String ascii = filename.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
