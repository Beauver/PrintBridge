package com.beauver.printbridge.modelinfo.analysis;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Charges every byte read to a {@link ReadBudget}. */
final class LimitedInputStream extends FilterInputStream {

    private final ReadBudget budget;

    LimitedInputStream(InputStream in, ReadBudget budget) {
        super(in);
        this.budget = budget;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            budget.addBytes(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = super.read(buffer, offset, length);
        if (count > 0) {
            budget.addBytes(count);
        }
        return count;
    }

    @Override
    public long skip(long count) throws IOException {
        long skipped = super.skip(count);
        budget.addBytes(skipped);
        return skipped;
    }
}
