package com.beauver.printbridge.common.error;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Translates domain exceptions thrown by services into HTTP responses, so services stay free of
 * HTTP types and can be called from anywhere (other services, tests).
 */
public class ExceptionMappers {

    /** Not in {@link Response.Status}. */
    private static final int UNPROCESSABLE_CONTENT = 422;

    @ServerExceptionMapper
    public Response conflict(ConflictException e) {
        return error(Response.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    public Response notFound(ResourceNotFoundException e) {
        return error(Response.Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    public Response invalidInput(InvalidInputException e) {
        return error(Response.Status.BAD_REQUEST, e.getMessage());
    }

    @ServerExceptionMapper
    public Response invalidCredentials(InvalidCredentialsException e) {
        return error(Response.Status.UNAUTHORIZED, e.getMessage());
    }

    @ServerExceptionMapper
    public Response serviceUnavailable(ServiceUnavailableException e) {
        return error(Response.Status.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ServerExceptionMapper
    public Response unprocessable(UnprocessableContentException e) {
        return error(UNPROCESSABLE_CONTENT, e.getMessage());
    }

    private static Response error(Response.Status status, String message) {
        return error(status.getStatusCode(), message);
    }

    private static Response error(int status, String message) {
        // Explicit: on endpoints that produce files (download, preview) the first produced type would win.
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ApiError(message)).build();
    }
}
