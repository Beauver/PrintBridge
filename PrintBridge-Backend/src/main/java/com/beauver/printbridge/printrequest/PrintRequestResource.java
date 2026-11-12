package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.auth.CurrentUser;
import com.beauver.printbridge.printrequest.dto.*;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Print requests of the logged-in user; the admin can also open and delete anyone's. */
@Path("/requests")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Role.USER_ROLE, Role.ADMIN_ROLE})
public class PrintRequestResource {

    @Inject
    PrintRequestService service;

    @Inject
    CurrentUser currentUser;

    // TODO: #5 admin overview of all requests (with filters).
    @GET
    public List<PrintRequestSummaryDto> listOwn() {
        return service.listOwn(currentUser.id());
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
