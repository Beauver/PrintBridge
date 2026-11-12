package com.beauver.printbridge.auth;

/** Who is calling a service: used for ownership checks without passing HTTP or JWT types around. */
public record Caller(long id, boolean admin) {
}
