package db.migration;

import com.blog.media.StableMediaReferenceParser;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backfills stable Markdown image references for tools that predate tool_media. */
public class V14_1__backfill_tool_media_references extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V14_1__backfill_tool_media_references.class);
    private static final int PAGE_SIZE = 10;

    @Override
    public void migrate(Context context) throws Exception {
        StableMediaReferenceParser parser = new StableMediaReferenceParser();
        Counters counters = new Counters();
        long lastToolId = 0;
        while (true) {
            List<ToolMarkdown> page = loadPage(context.getConnection(), lastToolId);
            if (page.isEmpty()) {
                break;
            }
            insertPage(context.getConnection(), page, parser, counters);
            lastToolId = page.getLast().id();
        }
        log.info("tool_media backfill inserted={} skippedMissing={} skippedNotReady={} skippedWrongPurpose={}",
                counters.inserted, counters.missing, counters.notReady, counters.wrongPurpose);
    }

    /**
     * Reads and closes one bounded page before issuing any media query. This avoids holding a
     * streaming LONGTEXT cursor while MySQL processes nested statements on the same connection.
     */
    private static List<ToolMarkdown> loadPage(Connection connection, long lastToolId) throws SQLException {
        List<ToolMarkdown> page = new ArrayList<>(PAGE_SIZE);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, description_markdown
                FROM tool
                WHERE id > ?
                ORDER BY id
                LIMIT ?
                """)) {
            statement.setLong(1, lastToolId);
            statement.setInt(2, PAGE_SIZE);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    page.add(new ToolMarkdown(result.getLong("id"), result.getString("description_markdown")));
                }
            }
        }
        return page;
    }

    /** Loads one bounded, distinct media-ID chunk while retaining skip diagnostics. */
    private static Map<Long, LegacyMediaState> loadMediaStates(Connection connection, List<Long> mediaIds)
            throws SQLException {
        Map<Long, LegacyMediaState> states = new LinkedHashMap<>();
        for (Long mediaId : mediaIds) {
            states.put(mediaId, LegacyMediaState.MISSING);
        }
        if (mediaIds.isEmpty()) {
            return states;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(mediaIds.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, status, purpose
                FROM media_asset
                WHERE id IN (""" + placeholders + ")")) {
            int parameter = 1;
            for (Long mediaId : mediaIds) {
                statement.setLong(parameter++, mediaId);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    states.put(result.getLong("id"), legacyMediaState(result));
                }
            }
        }
        return states;
    }

    /** Inserts in fixed-size INSERT IGNORE batches so pre-existing references remain unchanged. */
    private static void insertPage(Connection connection, List<ToolMarkdown> page, StableMediaReferenceParser parser,
                                   Counters counters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO tool_media (tool_id, media_id, sort_order) VALUES (?, ?, ?)")) {
            InsertBatch batch = new InsertBatch(statement, counters);
            for (ToolMarkdown tool : page) {
                addToolReferences(connection, parser.parse(tool.markdown()), tool.id(), batch, counters);
            }
            batch.flush();
        }
    }

    private static void addToolReferences(Connection connection, List<Long> mediaIds, long toolId, InsertBatch batch,
                                          Counters counters) throws SQLException {
        int[] sortOrder = {0};
        BackfillBatching.forEachChunk(mediaIds, BackfillBatching.MAX_MEDIA_IDS_PER_LOOKUP, mediaChunk -> {
            Map<Long, LegacyMediaState> states = loadMediaStates(connection, mediaChunk);
            for (Long mediaId : mediaChunk) {
                LegacyMediaState state = states.getOrDefault(mediaId, LegacyMediaState.MISSING);
                if (state == LegacyMediaState.VALID) {
                    batch.add(toolId, mediaId, sortOrder[0]++);
                } else {
                    counters.recordSkip(state);
                }
            }
        });
    }

    private static LegacyMediaState legacyMediaState(ResultSet result) throws SQLException {
        if (!"READY".equals(result.getString("status"))) return LegacyMediaState.NOT_READY;
        return "INLINE_IMAGE".equals(result.getString("purpose"))
                ? LegacyMediaState.VALID : LegacyMediaState.WRONG_PURPOSE;
    }

    private enum LegacyMediaState {
        VALID,
        MISSING,
        NOT_READY,
        WRONG_PURPOSE
    }

    private record ToolMarkdown(long id, String markdown) {
    }

    private static final class Counters {
        private int inserted;
        private int missing;
        private int notReady;
        private int wrongPurpose;

        private void recordSkip(LegacyMediaState state) {
            switch (state) {
                case MISSING -> missing++;
                case NOT_READY -> notReady++;
                case WRONG_PURPOSE -> wrongPurpose++;
                case VALID -> throw new IllegalArgumentException("Valid media must not be skipped");
            }
        }
    }

    private static final class InsertBatch {
        private final PreparedStatement statement;
        private final Counters counters;
        private int size;

        private InsertBatch(PreparedStatement statement, Counters counters) {
            this.statement = statement;
            this.counters = counters;
        }

        private void add(long toolId, long mediaId, int sortOrder) throws SQLException {
            statement.setLong(1, toolId);
            statement.setLong(2, mediaId);
            statement.setInt(3, sortOrder);
            statement.addBatch();
            size++;
            if (size == BackfillBatching.MAX_INSERT_ROWS) {
                flush();
            }
        }

        private void flush() throws SQLException {
            if (size == 0) {
                return;
            }
            for (int updated : statement.executeBatch()) {
                if (updated != 0 && updated != Statement.EXECUTE_FAILED) {
                    counters.inserted++;
                }
            }
            size = 0;
        }
    }

}
