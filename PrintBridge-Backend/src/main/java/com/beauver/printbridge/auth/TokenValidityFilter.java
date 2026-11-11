package com.beauver.printbridge.auth;

import com.beauver.printbridge.common.error.ApiError;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Rejects access tokens of users that were deleted or whose password was reset after the token
 * was issued. Without this, such a token would keep working until it expires (max 15 min).
 * <p>
 * Runs on the same (worker) thread as the endpoint, after SmallRye JWT has already checked the
 * signature, issuer and expiry.
 */
public class TokenValidityFilter {

    @Inject
    SecurityIdentity identity;

    @Inject
    TokenValidity validity;

    /** Returns null to continue, or a 401 response to stop the request. */
    @ServerRequestFilter
    public Response rejectStaleTokens() {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return null;
        }
        if (validity.isValid(Long.parseLong(jwt.getSubject()), jwt.getIssuedAtTime())) {
            return null;
        }
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(RefreshTokenService.INVALID))
                .build();
    }
}
