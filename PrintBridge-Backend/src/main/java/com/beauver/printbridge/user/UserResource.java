package com.beauver.printbridge.user;

import com.beauver.printbridge.auth.CurrentUser;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import com.beauver.printbridge.user.dto.ResetPasswordRequest;
import com.beauver.printbridge.user.dto.UserDto;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/** Account management; admin only. */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Role.ADMIN_ROLE)
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<UserDto> list() {
        return userService.list();
    }

    @POST
    public Response create(@Valid @NotNull NewAccountRequest request) {
        return Response.status(Response.Status.CREATED).entity(userService.createUser(request)).build();
    }

    @PUT
    @Path("/{id}/password")
    public Response resetPassword(@PathParam("id") long id, @Valid @NotNull ResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") long id) {
        userService.delete(id, currentUser.id());
        return Response.noContent().build();
    }
}
