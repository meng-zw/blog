package db.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillBatchingTest {
    @Test
    void sendsOverChunkSizeDistinctReferencesToSeparateLookupBatchesInOrder() {
        List<Long> references = LongStream.rangeClosed(1, BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP + 1)
                .boxed()
                .toList();
        List<List<Long>> lookupBatches = new ArrayList<>();

        BackfillBatching.forEachChunk(references, BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP, lookupBatches::add);

        assertThat(lookupBatches).hasSize(2);
        assertThat(lookupBatches.getFirst()).hasSize(BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP);
        assertThat(lookupBatches.get(1)).containsExactly(BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP + 1L);
        assertThat(lookupBatches).flatExtracting(batch -> batch).containsExactlyElementsOf(references);
    }

    @Test
    void migrationInsertBatchExecutesFiveHundredRowsThenFlushesTheRemainderInOrder() throws Exception {
        RecordingPreparedStatement recording = new RecordingPreparedStatement();
        V14_1__backfill_tool_media_references.Counters counters =
                new V14_1__backfill_tool_media_references.Counters();
        V14_1__backfill_tool_media_references.InsertBatch batch =
                new V14_1__backfill_tool_media_references.InsertBatch(recording.statement(), counters);

        for (int index = 0; index < 501; index++) {
            batch.add(17L, 1_000L + index, index);
        }

        assertThat(recording.executionSizes()).containsExactly(500);
        assertThat(recording.pendingRows()).hasSize(1);
        assertThat(recording.maximumPendingRows()).isEqualTo(500);

        batch.flush();

        assertThat(recording.executionSizes()).containsExactly(500, 1);
        assertThat(recording.executedRows())
                .containsExactlyElementsOf(LongStream.range(1_000L, 1_501L)
                        .mapToObj(mediaId -> new InsertRow(17L, mediaId, (int) (mediaId - 1_000L)))
                        .toList());
        assertThat(recording.pendingRows()).isEmpty();
    }

    private record InsertRow(long toolId, long mediaId, int sortOrder) {
    }

    private static final class RecordingPreparedStatement implements InvocationHandler {
        private final List<InsertRow> pendingRows = new ArrayList<>();
        private final List<InsertRow> executedRows = new ArrayList<>();
        private final List<Integer> executionSizes = new ArrayList<>();
        private final PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                this);
        private long toolId;
        private long mediaId;
        private int sortOrder;
        private int maximumPendingRows;

        PreparedStatement statement() {
            return statement;
        }

        List<InsertRow> pendingRows() {
            return pendingRows;
        }

        List<InsertRow> executedRows() {
            return executedRows;
        }

        List<Integer> executionSizes() {
            return executionSizes;
        }

        int maximumPendingRows() {
            return maximumPendingRows;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "setLong" -> {
                    if ((int) args[0] == 1) {
                        toolId = (long) args[1];
                    } else {
                        mediaId = (long) args[1];
                    }
                    yield null;
                }
                case "setInt" -> {
                    sortOrder = (int) args[1];
                    yield null;
                }
                case "addBatch" -> {
                    pendingRows.add(new InsertRow(toolId, mediaId, sortOrder));
                    maximumPendingRows = Math.max(maximumPendingRows, pendingRows.size());
                    yield null;
                }
                case "executeBatch" -> {
                    executionSizes.add(pendingRows.size());
                    executedRows.addAll(pendingRows);
                    int[] updates = new int[pendingRows.size()];
                    java.util.Arrays.fill(updates, 1);
                    pendingRows.clear();
                    yield updates;
                }
                case "toString" -> "RecordingPreparedStatement";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected PreparedStatement call: " + method.getName());
            };
        }
    }
}
