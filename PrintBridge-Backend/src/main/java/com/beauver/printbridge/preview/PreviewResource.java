package com.beauver.printbridge.preview;

import com.beauver.printbridge.printrequest.dto.UploadedModel;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Preview of a model file before it is uploaded, for the new request form. The file is checked
 * like an upload, read and thrown away. The previews of uploaded files are under
 * {@code /requests/{id}/files/{fileId}}.
 */
@Path("/previews")
@RolesAllowed({Role.USER_ROLE, Role.ADMIN_ROLE})
public class PreviewResource {

    @Inject
    PreviewService service;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({MeshEncoder.MEDIA_TYPE, MediaType.APPLICATION_JSON})
    public Response preview(@RestForm("file") @NotNull FileUpload file) {
        UploadedModel upload = new UploadedModel(file.fileName(), file.uploadedFile(), file.size());
        return mesh(service.ofUpload(upload));
    }

    /** Gzipped by {@link MeshEncoder}; browsers unpack it themselves. */
    public static Response mesh(byte[] gzipped) {
        return Response.ok(gzipped, MeshEncoder.MEDIA_TYPE)
                .header("Content-Encoding", "gzip")
                .header("Cache-Control", "private, no-store")
                .build();
    }
}
