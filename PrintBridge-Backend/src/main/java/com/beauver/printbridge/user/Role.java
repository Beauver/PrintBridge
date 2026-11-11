package com.beauver.printbridge.user;

public enum Role {
    USER,
    ADMIN;

    /** Role names as used in {@code @RolesAllowed}. */
    public static final String USER_ROLE = "USER";
    public static final String ADMIN_ROLE = "ADMIN";
}
