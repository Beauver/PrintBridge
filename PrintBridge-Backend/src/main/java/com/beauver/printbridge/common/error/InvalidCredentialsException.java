package com.beauver.printbridge.common.error;

/** Login or refresh failed. The message never reveals which part was wrong. Maps to 401. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
