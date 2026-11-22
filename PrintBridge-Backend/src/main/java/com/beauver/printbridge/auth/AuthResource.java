package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.dto.AuthResponse;
import com.beauver.printbridge.auth.dto.LoginRequest;
import com.beauver.printbridge.auth.token.RefreshCookie;
import com.beauver.printbridge.common.ratelimit.ClientAddress;
import com.beauver.printbridge.common.ratelimit.RateLimit;
import com.beauver.printbridge.common.ratelimit.RateLimitKey;
import com.beauver.printbridge.user.UserService;
import com.beauver.printbridge.user.dto.UpdateAccountRequest;
import com.beauver.printbridge.user.dto.UserDto;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    UserService userService;

    @Inject
    RefreshCookie refreshCookie;

    @Inject
    CurrentUser currentUser;

    @Inject
    ClientAddress clientAddress;

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @RateLimit(limit = 10, windowSeconds = 60)
    public Response login(@Valid @NotNull LoginRequest request) {
        return sessionResponse(authService.login(request.login(), request.password(), clientAddress.get()));
    }

    @POST
    @Path("/refresh")
    @PermitAll
    public Response refresh(@CookieParam(RefreshCookie.NAME) String token) {
        return sessionResponse(authService.refresh(token));
    }

    @POST
    @Path("/logout")
    @PermitAll
    public Response logout(@CookieParam(RefreshCookie.NAME) String token) {
        authService.logout(token);
        return Response.noContent().cookie(refreshCookie.clear()).build();
    }

    @GET
    @Path("/me")
    @Authenticated
    public UserDto me() {
        return userService.get(currentUser.id());
    }

    /**
     * Updates the caller's own account. Answers with a new session, like a login, so this browser
     * stays logged in after a password change. Rate limited per user because it checks a password.
     */
    @PUT
    @Path("/me")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @RateLimit(limit = 10, windowSeconds = 60, per = RateLimitKey.USER)
    public Response updateMe(@Valid @NotNull UpdateAccountRequest request) {
        return sessionResponse(authService.updateOwnAccount(currentUser.id(), request));
    }

    private Response sessionResponse(AuthSession session) {
        AuthResponse body = session.response();
        return Response.ok(body).cookie(refreshCookie.create(session.refreshToken())).build();
    }
}
