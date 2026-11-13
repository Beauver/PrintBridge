package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.CurrentUser;
import com.beauver.printbridge.preview.MeshEncoder;
import com.beauver.printbridge.preview.PreviewResource;
import com.beauver.printbridge.preview.PreviewService;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Print requests: users manage their own; the admin can open, review and delete any of them. */
@Path("/requests")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Role.USER_ROLE, Role.ADMIN_ROLE})
public class PrintRequestResource {

    @Inject
    PrintRequestService service;

    @Inject
    CurrentUser currentUser;

    @Inject
    PreviewService previews;

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

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response create(@Valid NewPrintRequestForm form) {
        UploadedModel upload = new UploadedModel(form.file.fileName(), form.file.uploadedFile(), form.file.size());
        PrintRequestDto created = service.create(currentUser.id(),
                new NewPrintRequest(form.name, form.description, upload));
        return Response.created(UriBuilder.fromPath("/api/requests/{id}").build(created.id()))
                .entity(created)
                .build();
    }

    @GET
    @Path("/{id}")
    public PrintRequestDto get(@PathParam("id") long id) {
        return service.get(id, currentUser.caller());
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

    /** The file's 3D preview mesh (#13), see {@link MeshEncoder}. 422 when there is none. */
    @GET
    @Path("/{id}/files/{fileId}/preview")
    @Produces({MeshEncoder.MEDIA_TYPE, MediaType.APPLICATION_JSON})
    public Response preview(@PathParam("id") long id, @PathParam("fileId") long fileId) {
        return PreviewResource.mesh(previews.ofFile(id, fileId, currentUser.caller()));
    }

    /** The image a Bambu Studio / OrcaSlicer project carries of a plate; 404 when there is none. */
    @GET
    @Path("/{id}/files/{fileId}/plates/{plate}/thumbnail")
    @Produces({"image/png", MediaType.APPLICATION_JSON})
    public Response thumbnail(@PathParam("id") long id, @PathParam("fileId") long fileId,
                              @PathParam("plate") int plate) {
        return Response.ok(previews.thumbnail(id, fileId, plate, currentUser.caller()), "image/png")
                .header("Cache-Control", "private, max-age=3600")
                .build();
    }

    /** Slices the request's files again on the server (admin). */
    @POST
    @Path("/{id}/reslice")
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto reslice(@PathParam("id") long id) {
        return service.reslice(id);
    }

    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Role.ADMIN_ROLE)
    public PrintRequestDto changeStatus(@PathParam("id") long id, @Valid @NotNull StatusChangeRequest request) {
        return service.changeStatus(id, request.status(), request.declineReason());
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

    /** RFC 6266: an ASCII fallback plus the exact UTF-8 name for browsers that support it. */
    static String contentDisposition(String filename) {
        String ascii = filename.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
