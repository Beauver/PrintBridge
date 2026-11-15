package com.beauver.printbridge.preview;

import com.beauver.printbridge.auth.CurrentUser;
import com.beauver.printbridge.user.Role;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Previews of the files of a print request: the 3D mesh and the plate images. They sit under
 * {@code /requests} next to the other file endpoints, but live here so the print requests don't
 * depend on the preview code. Who may see a file is decided by {@link PreviewService}.
 */
@Path("/requests")
@RolesAllowed({Role.USER_ROLE, Role.ADMIN_ROLE})
public class FilePreviewResource {

    @Inject
    PreviewService previews;

    @Inject
    CurrentUser currentUser;

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
}
