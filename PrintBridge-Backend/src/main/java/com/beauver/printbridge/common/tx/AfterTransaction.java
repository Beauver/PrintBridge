package com.beauver.printbridge.common.tx;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Runs side effects outside the database (like deleting files on disk) only once the current
 * transaction has committed or rolled back, so disk and database never disagree because of a
 * rollback.
 */
@ApplicationScoped
public class AfterTransaction {

    @Inject
    TransactionSynchronizationRegistry registry;

    /** Runs {@code action} after the current transaction commits. Must be called inside a transaction. */
    public void onCommit(Runnable action) {
        register(action, true);
    }

    /** Runs {@code action} after the current transaction rolls back. Must be called inside a transaction. */
    public void onRollback(Runnable action) {
        register(action, false);
    }

    private void register(Runnable action, boolean onCommit) {
        registry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // nothing to do before completion
            }

            @Override
            public void afterCompletion(int status) {
                if ((status == Status.STATUS_COMMITTED) == onCommit) {
                    action.run();
                }
            }
        });
    }
}
