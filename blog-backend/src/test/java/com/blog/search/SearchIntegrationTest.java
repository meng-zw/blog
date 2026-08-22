package com.blog.search;

import com.blog.search.dto.SearchResultResponse;
import com.blog.search.dto.SearchResultType;
import com.blog.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock SearchRepository searchRepository;
    @Mock NamedParameterJdbcTemplate jdbc;

    @Test
    void normalizesTheQueryAfterNfkcAndClampsRequestedSize() {
        SearchResultResponse item = new SearchResultResponse(SearchResultType.ARTICLE, 4L, "cafe", "Cafe", "Summary",
                NOW.minusSeconds(1));
        when(searchRepository.search("Cafe", 2, 50, NOW))
                .thenReturn(new SearchRepository.SearchPage(List.of(item), 101));

        PageResponse<SearchResultResponse> result = service().search("  Ｃａｆｅ  ", 2, 500);

        assertThat(result.items()).containsExactly(item);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(50);
        assertThat(result.total()).isEqualTo(101);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidPageBlankAndPostNormalizationExpandedQueries() {
        assertThatThrownBy(() -> service().search("query", -1, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
        assertThatThrownBy(() -> service().search("　 ", 0, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 and 100");
        assertThatThrownBy(() -> service().search("\ufdfa".repeat(12), 0, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 and 100");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositoryEscapesWildcardsAndLimitsHighPageQueriesAtTheDatabase() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(10_001L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        SearchRepository repository = new SearchRepository(jdbc);

        SearchRepository.SearchPage result = repository.search("50%_!", 200, 50, NOW);

        assertThat(result.total()).isEqualTo(10_001);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("UNION ALL", "LIMIT :limit OFFSET :offset",
                "a.status = 'PUBLISHED'", "a.published_at <= :now", "t.status = 'PUBLISHED'",
                "tl.status = 'PUBLISHED'", "tl.published_at <= :now");
        assertThat(sql.getValue()).contains("ORDER BY relevance ASC, sort_time DESC, result_type ASC, entity_id DESC");
        MapSqlParameterSource values = (MapSqlParameterSource) parameters.getValue();
        assertThat(values.getValue("pattern")).isEqualTo("%50!%!_!!%");
        assertThat(values.getValue("prefix")).isEqualTo("50!%!_!!%");
        assertThat(values.getValue("limit")).isEqualTo(50);
        assertThat(values.getValue("offset")).isEqualTo(10_000L);
    }

    @Test
    void repositorySkipsTheContentQueryWhenAHighPageStartsPastTheTotal() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(3L);
        SearchRepository repository = new SearchRepository(jdbc);

        SearchRepository.SearchPage result = repository.search("query", 1_000_000, 50, NOW);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositorySearchesToolSummaryInBothCountAndContentQueries() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        new SearchRepository(jdbc).search("summary-only", 0, 20, NOW);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(countSql.capture(), any(SqlParameterSource.class), eq(Long.class));
        verify(jdbc).query(contentSql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        String summaryPredicate = "LOWER(COALESCE(tl.summary, '')) LIKE :pattern ESCAPE '!'";
        assertThat(countSql.getValue()).contains(summaryPredicate);
        assertThat(contentSql.getValue()).contains(summaryPredicate);
    }

    private SearchService service() {
        return new SearchService(searchRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
