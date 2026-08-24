package db.migration;

import java.util.List;

/** Bounded, ordered chunk traversal for Java data migrations. */
final class BackfillBatching {
    static final int MAX_MEDIA_IDS_PER_LOOKUP = 500;
    static final int MAX_INSERT_ROWS = 500;

    private BackfillBatching() {
    }

    static <T, E extends Exception> void forEachChunk(List<T> values, int maximumSize,
                                                       ChunkConsumer<T, E> consumer) throws E {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Maximum batch size must be positive");
        }
        for (int start = 0; start < values.size(); start += maximumSize) {
            consumer.accept(values.subList(start, Math.min(start + maximumSize, values.size())));
        }
    }

    @FunctionalInterface
    interface ChunkConsumer<T, E extends Exception> {
        void accept(List<T> chunk) throws E;
    }
}
