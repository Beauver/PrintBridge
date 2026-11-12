package com.beauver.printbridge.common.transaction;

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
            /*
             * Required by the Synchronization interface (it has no default). It runs before the
             * commit, while the transaction can still fail, so it's the wrong moment for disk
             * side effects; afterCompletion is the one that matters here.
             */
            @Override
            public void beforeCompletion() {
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
