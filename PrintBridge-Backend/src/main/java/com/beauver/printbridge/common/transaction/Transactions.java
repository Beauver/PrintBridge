package com.beauver.printbridge.common.transaction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.function.Supplier;

/**
 * Runs a piece of work in a transaction: the caller's if there is one, else a new one that
 * commits when the work returns and rolls back when it throws.
 * <p>
 * For flows that must not be one transaction from start to end, like an upload: the slow parts
 * (copying a file, parsing a model) run between short transactions instead of inside a long one
 * that holds a database connection and row locks.
 */
@ApplicationScoped
public class Transactions {

    @Transactional
    public <T> T call(Supplier<T> work) {
        return work.get();
    }

    @Transactional
    public void run(Runnable work) {
        work.run();
    }
}
