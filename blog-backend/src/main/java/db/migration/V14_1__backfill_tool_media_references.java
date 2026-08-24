package db.migration;

import com.blog.media.StableMediaReferenceParser;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Backfills stable Markdown image references for tools that predate tool_media. */
public class V14_1__backfill_tool_media_references extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V14_1__backfill_tool_media_references.class);

    @Override
    public void migrate(Context context) throws Exception {
        StableMediaReferenceParser parser = new StableMediaReferenceParser();
        Counters counters = new Counters();
        try (Statement tools = context.getConnection().createStatement();
             ResultSet result = tools.executeQuery("SELECT id, description_markdown FROM tool ORDER BY id");
             PreparedStatement media = context.getConnection().prepareStatement(
                     "SELECT status, purpose FROM media_asset WHERE id = ?");
             PreparedStatement insert = context.getConnection().prepareStatement(
                     "INSERT IGNORE INTO tool_media (tool_id, media_id, sort_order) VALUES (?, ?, ?)")) {
            while (result.next()) {
                long toolId = result.getLong("id");
                int sortOrder = 0;
                for (Long mediaId : parser.parse(result.getString("description_markdown"))) {
                    LegacyMediaState state = legacyMediaState(media, mediaId);
                    if (state == LegacyMediaState.MISSING) {
                        counters.missing++;
                    } else if (state == LegacyMediaState.NOT_READY) {
                        counters.notReady++;
                    } else if (state == LegacyMediaState.WRONG_PURPOSE) {
                        counters.wrongPurpose++;
                    } else {
                        insert.setLong(1, toolId);
                        insert.setLong(2, mediaId);
                        insert.setInt(3, sortOrder++);
                        if (insert.executeUpdate() > 0) {
                            counters.inserted++;
                        }
                    }
                }
            }
        }
        log.info("tool_media backfill inserted={} skippedMissing={} skippedNotReady={} skippedWrongPurpose={}",
                counters.inserted, counters.missing, counters.notReady, counters.wrongPurpose);
    }

    private static LegacyMediaState legacyMediaState(PreparedStatement statement, long mediaId) throws SQLException {
        statement.setLong(1, mediaId);
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) return LegacyMediaState.MISSING;
            if (!"READY".equals(result.getString("status"))) return LegacyMediaState.NOT_READY;
            return "INLINE_IMAGE".equals(result.getString("purpose"))
                    ? LegacyMediaState.VALID : LegacyMediaState.WRONG_PURPOSE;
        }
    }

    private enum LegacyMediaState {
        VALID,
        MISSING,
        NOT_READY,
        WRONG_PURPOSE
    }

    private static final class Counters {
        private int inserted;
        private int missing;
        private int notReady;
        private int wrongPurpose;
    }
}
