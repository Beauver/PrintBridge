package com.beauver.printbridge.modelfile.threemf;

import com.beauver.printbridge.modelfile.ReadBudget;
import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Charges every byte read to a {@link ReadBudget}, and keeps one part within its own limit. */
final class LimitedInputStream extends FilterInputStream {

    private final ReadBudget budget;
    private final String name;
    private final long maxBytes;
    private long bytes;

    /** Only limited by the budget. */
    LimitedInputStream(InputStream in, ReadBudget budget) {
        this(in, budget, null, Long.MAX_VALUE);
    }

    /** Also fails once more than {@code maxBytes} are read from this part. */
    LimitedInputStream(InputStream in, ReadBudget budget, String name, long maxBytes) {
        super(in);
        this.budget = budget;
        this.name = name;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            charge(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = super.read(buffer, offset, length);
        if (count > 0) {
            charge(count);
        }
        return count;
    }

    @Override
    public long skip(long count) throws IOException {
        long skipped = super.skip(count);
        charge(skipped);
        return skipped;
    }

    private void charge(long count) throws ModelAnalysisException {
        bytes += count;
        if (bytes > maxBytes) {
            throw new ModelAnalysisException(name + " is larger than " + maxBytes + " bytes");
        }
        budget.addBytes(count);
    }
}
