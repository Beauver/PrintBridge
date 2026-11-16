package com.beauver.printbridge.common.transaction;

import java.util.function.Supplier;

/** For unit tests: runs the work directly and remembers whether it is "inside a transaction". */
public class RecordingTransactions extends Transactions {

    private int depth;
    private int count;

    public boolean active() {
        return depth > 0;
    }

    /** How many transactions were started so far. */
    public int count() {
        return count;
    }

    @Override
    public <T> T call(Supplier<T> work) {
        if (depth == 0) {
            count++;
        }
        depth++;
        try {
            return work.get();
        } finally {
            depth--;
        }
    }

    @Override
    public void run(Runnable work) {
        call(() -> {
            work.run();
            return null;
        });
    }
}
