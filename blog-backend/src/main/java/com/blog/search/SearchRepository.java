package com.blog.search;

import com.blog.search.dto.SearchResultResponse;
import com.blog.search.dto.SearchResultType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class SearchRepository {
    private static final String COUNT_SQL = """
            SELECT (
              (SELECT COUNT(*) FROM article a
                 WHERE a.status = 'PUBLISHED' AND a.published_at <= :now
                   AND (LOWER(a.title) LIKE :pattern ESCAPE '!' OR LOWER(a.summary) LIKE :pattern ESCAPE '!'))
              +
              (SELECT COUNT(*) FROM topic t
                 WHERE t.status = 'PUBLISHED'
                   AND (LOWER(t.name) LIKE :pattern ESCAPE '!' OR LOWER(COALESCE(t.description, '')) LIKE :pattern ESCAPE '!'))
              +
              (SELECT COUNT(*) FROM tool tl
                 WHERE tl.status = 'PUBLISHED' AND tl.published_at <= :now
                   AND (LOWER(tl.name) LIKE :pattern ESCAPE '!'
                     OR LOWER(COALESCE(tl.summary, '')) LIKE :pattern ESCAPE '!'
                     OR LOWER(COALESCE(tl.description_markdown, '')) LIKE :pattern ESCAPE '!'))
            )
            """;

    private static final String SEARCH_SQL = """
            SELECT entity_id, slug, title, summary, result_type, sort_time, relevance
            FROM (
              SELECT a.id AS entity_id, a.slug, a.title, a.summary,
                     a.content_type AS result_type, a.published_at AS sort_time,
                     CASE WHEN LOWER(a.title) = :term THEN 0
                          WHEN LOWER(a.title) LIKE :prefix ESCAPE '!' THEN 1 ELSE 2 END AS relevance
                FROM article a
               WHERE a.status = 'PUBLISHED' AND a.published_at <= :now
                 AND (LOWER(a.title) LIKE :pattern ESCAPE '!' OR LOWER(a.summary) LIKE :pattern ESCAPE '!')
              UNION ALL
              SELECT t.id AS entity_id, t.slug, t.name AS title, t.description AS summary,
                     'TOPIC' AS result_type, NULL AS sort_time,
                     CASE WHEN LOWER(t.name) = :term THEN 0
                          WHEN LOWER(t.name) LIKE :prefix ESCAPE '!' THEN 1 ELSE 2 END AS relevance
                FROM topic t
               WHERE t.status = 'PUBLISHED'
                 AND (LOWER(t.name) LIKE :pattern ESCAPE '!' OR LOWER(COALESCE(t.description, '')) LIKE :pattern ESCAPE '!')
              UNION ALL
              SELECT tl.id AS entity_id, tl.slug, tl.name AS title, tl.summary,
                     'TOOL' AS result_type, tl.published_at AS sort_time,
                     CASE WHEN LOWER(tl.name) = :term THEN 0
                          WHEN LOWER(tl.name) LIKE :prefix ESCAPE '!' THEN 1 ELSE 2 END AS relevance
                FROM tool tl
               WHERE tl.status = 'PUBLISHED' AND tl.published_at <= :now
                 AND (LOWER(tl.name) LIKE :pattern ESCAPE '!'
                   OR LOWER(COALESCE(tl.summary, '')) LIKE :pattern ESCAPE '!'
                   OR LOWER(COALESCE(tl.description_markdown, '')) LIKE :pattern ESCAPE '!')
            ) combined
            ORDER BY relevance ASC, sort_time DESC, result_type ASC, entity_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchPage search(String normalizedQuery, int page, int size, Instant now) {
        String term = normalizedQuery.toLowerCase(java.util.Locale.ROOT);
        String literal = escapeLike(term);
        long offset = (long) page * size;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("term", term)
                .addValue("pattern", "%" + literal + "%")
                .addValue("prefix", literal + "%")
                .addValue("now", Timestamp.from(now))
                .addValue("limit", size)
                .addValue("offset", offset);
        Long count = jdbc.queryForObject(COUNT_SQL, parameters, Long.class);
        long total = count == null ? 0 : count;
        if (offset >= total) {
            return new SearchPage(List.of(), total);
        }
        List<SearchResultResponse> items = jdbc.query(SEARCH_SQL, parameters, (resultSet, rowNumber) -> {
            Timestamp publishedAt = resultSet.getTimestamp("sort_time");
            return new SearchResultResponse(SearchResultType.valueOf(resultSet.getString("result_type")),
                    resultSet.getLong("entity_id"), resultSet.getString("slug"), resultSet.getString("title"),
                    resultSet.getString("summary"), publishedAt == null ? null : publishedAt.toInstant());
        });
        return new SearchPage(List.copyOf(items), total);
    }

    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public record SearchPage(List<SearchResultResponse> items, long total) {
    }
}
