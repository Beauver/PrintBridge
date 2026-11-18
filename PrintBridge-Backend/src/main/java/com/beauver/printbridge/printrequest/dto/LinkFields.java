package com.beauver.printbridge.printrequest.dto;

/** The limits on a link (#59), shared by the forms that take one. */
public final class LinkFields {

    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_NAME_LENGTH = 100;
    /** http(s) and no whitespace; the service also checks that it parses and has a host. */
    public static final String URL_PATTERN = "^(?i)https?://\\S+$";
    public static final String URL_MESSAGE = "The link must start with http:// or https://";

    private LinkFields() {
    }
}
