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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            Map<Long, LegacyMediaState> mediaStates = loadMediaStates(context.getConnection(), page, parser);
            insertPage(context.getConnection(), page, parser, mediaStates, counters);
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

    /** Loads all distinct media states for a page with one lookup, retaining skip diagnostics. */
    private static Map<Long, LegacyMediaState> loadMediaStates(Connection connection, List<ToolMarkdown> page,
                                                                StableMediaReferenceParser parser) throws SQLException {
        Set<Long> mediaIds = new LinkedHashSet<>();
        for (ToolMarkdown tool : page) {
            mediaIds.addAll(parser.parse(tool.markdown()));
        }
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

    /** Inserts the page in one INSERT IGNORE batch so pre-existing references remain unchanged. */
    private static void insertPage(Connection connection, List<ToolMarkdown> page, StableMediaReferenceParser parser,
                                   Map<Long, LegacyMediaState> mediaStates, Counters counters) throws SQLException {
        List<ToolMediaInsert> inserts = new ArrayList<>();
        for (ToolMarkdown tool : page) {
            int sortOrder = 0;
            for (Long mediaId : parser.parse(tool.markdown())) {
                LegacyMediaState state = mediaStates.getOrDefault(mediaId, LegacyMediaState.MISSING);
                if (state == LegacyMediaState.VALID) {
                    inserts.add(new ToolMediaInsert(tool.id(), mediaId, sortOrder++));
                } else {
                    counters.recordSkip(state);
                }
            }
        }
        if (inserts.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO tool_media (tool_id, media_id, sort_order) VALUES (?, ?, ?)")) {
            for (ToolMediaInsert insert : inserts) {
                statement.setLong(1, insert.toolId());
                statement.setLong(2, insert.mediaId());
                statement.setInt(3, insert.sortOrder());
                statement.addBatch();
            }
            for (int updated : statement.executeBatch()) {
                if (updated != 0 && updated != Statement.EXECUTE_FAILED) {
                    counters.inserted++;
                }
            }
        }
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

    private record ToolMediaInsert(long toolId, long mediaId, int sortOrder) {
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
}
