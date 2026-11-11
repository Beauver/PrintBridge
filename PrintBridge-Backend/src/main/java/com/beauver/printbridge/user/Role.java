package com.beauver.printbridge.user;

public enum Role {
    USER,
    ADMIN;

    /*
     * The same names as plain String constants, for @RolesAllowed: annotation values must be
     * compile-time constants, and neither ADMIN nor ADMIN.name() counts as one.
     */
    public static final String USER_ROLE = "USER";
    public static final String ADMIN_ROLE = "ADMIN";
}
