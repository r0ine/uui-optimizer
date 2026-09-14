package dev.r0ine.uuiopt.filter;

import java.util.concurrent.atomic.LongAdder;

public final class Stats {

    private final LongAdder[] seen;
    private final LongAdder[] dropped;
    private final LongAdder[] saved;

    public Stats(int slots) {
        seen = adders(slots);
        dropped = adders(slots);
        saved = adders(slots);
    }

    private static LongAdder[] adders(int n) {
        LongAdder[] out = new LongAdder[n];
        for (int i = 0; i < n; i++) out[i] = new LongAdder();
        return out;
    }

    public void recordSeen(int slot) {
        seen[slot].increment();
    }

    public void recordDrop(int slot, int bytes) {
        dropped[slot].increment();
        saved[slot].add(bytes);
    }

    public long seenCount(int slot) {
        return seen[slot].sum();
    }

    public long droppedCount(int slot) {
        return dropped[slot].sum();
    }

    public long savedBytes(int slot) {
        return saved[slot].sum();
    }

    public void reset() {
        for (int i = 0; i < seen.length; i++) {
            seen[i].reset();
            dropped[i].reset();
            saved[i].reset();
        }
    }
}
