package db.migration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillBatchingTest {
    @Test
    void sendsOverChunkSizeDistinctReferencesToSeparateLookupAndInsertBatchesInOrder() {
        List<Long> references = LongStream.rangeClosed(1, BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP + 1)
                .boxed()
                .toList();
        List<List<Long>> lookupBatches = new ArrayList<>();
        List<List<Long>> insertBatches = new ArrayList<>();

        BackfillBatching.forEachChunk(references, BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP, lookupBatches::add);
        BackfillBatching.forEachChunk(references, BackfillBatching.MAX_INSERT_ROWS, insertBatches::add);

        assertThat(lookupBatches).hasSize(2);
        assertThat(lookupBatches.getFirst()).hasSize(BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP);
        assertThat(lookupBatches.get(1)).containsExactly(BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP + 1L);
        assertThat(lookupBatches).flatExtracting(batch -> batch).containsExactlyElementsOf(references);
        assertThat(insertBatches).hasSize(2);
        assertThat(insertBatches.getFirst()).hasSize(BackfillBatching.MAX_INSERT_ROWS);
        assertThat(insertBatches).flatExtracting(batch -> batch).containsExactlyElementsOf(references);
    }
}
