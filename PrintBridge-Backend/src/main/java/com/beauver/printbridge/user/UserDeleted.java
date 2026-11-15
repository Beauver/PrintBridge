package com.beauver.printbridge.user;

/**
 * Fired inside the deleting transaction, before the user's row goes: what belongs to the user
 * outside the database (like request files on disk) can still be found and cleaned up.
 */
public record UserDeleted(long userId) {
}
