package com.beauver.printbridge.common.error;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Translates domain exceptions thrown by services into HTTP responses. */
public class ExceptionMappers {

    @ServerExceptionMapper
    public Response conflict(ConflictException e) {
        return error(Response.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    public Response notFound(ResourceNotFoundException e) {
        return error(Response.Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    public Response invalidCredentials(InvalidCredentialsException e) {
        return error(Response.Status.UNAUTHORIZED, e.getMessage());
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(new ApiError(message)).build();
    }
}
