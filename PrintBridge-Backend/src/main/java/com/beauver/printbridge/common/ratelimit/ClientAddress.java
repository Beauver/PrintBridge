package com.beauver.printbridge.common.ratelimit;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * The address the current request comes from. Behind the nginx proxy that is the address nginx
 * forwards ({@code quarkus.http.proxy.proxy-address-forwarding}), not nginx's own.
 */
@RequestScoped
public class ClientAddress {

    @Inject
    CurrentVertxRequest request;

    public String get() {
        return request.getCurrent().request().remoteAddress().host();
    }
}
