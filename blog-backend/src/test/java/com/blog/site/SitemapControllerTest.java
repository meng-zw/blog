package com.blog.site;

import com.blog.article.ArticleRepository;
import com.blog.tool.ToolRepository;
import com.blog.topic.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SitemapControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock ArticleRepository articleRepository;
    @Mock TopicRepository topicRepository;
    @Mock ToolRepository toolRepository;

    @Test
    void emitsCanonicalEncodedPublicPageUrlsFromTheTrustedBaseUrlOnly() throws Exception {
        List<ArticleRepository.SitemapRow> articles = List.of(article(1, "article&one"), article(2, "随笔"));
        List<TopicRepository.SitemapRow> topics = List.of(topic(3, "topic/three"));
        List<ToolRepository.SitemapRow> tools = List.of(tool(4, "tool four"));
        when(articleRepository.findVisibleSitemapBatch(eq(0L), eq(NOW), any(Pageable.class)))
                .thenReturn(articles);
        when(topicRepository.findPublishedSitemapBatch(eq(0L), any(Pageable.class)))
                .thenReturn(topics);
        when(toolRepository.findVisibleSitemapBatch(eq(0L), eq(NOW), any(Pageable.class)))
                .thenReturn(tools);

        String xml = service("https://example.com").generate();

        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(xml).contains(
                "<loc>https://example.com/</loc>",
                "<loc>https://example.com/articles</loc>",
                "<loc>https://example.com/notes</loc>",
                "<loc>https://example.com/topics</loc>",
                "<loc>https://example.com/tools</loc>",
                "<loc>https://example.com/articles/article%26one</loc>",
                "<loc>https://example.com/articles/%E9%9A%8F%E7%AC%94</loc>",
                "<loc>https://example.com/topics/topic%2Fthree</loc>",
                "<loc>https://example.com/tools/tool%20four</loc>");
        assertThat(xml).doesNotContain("/api/", "/admin", "evil.example");
        ArgumentCaptor<Pageable> bounded = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findVisibleSitemapBatch(eq(0L), eq(NOW), bounded.capture());
        assertThat(bounded.getValue().getPageSize()).isLessThanOrEqualTo(500);
    }

    @Test
    void rejectsNonHttpsOrNonOriginCanonicalBases() {
        assertThatThrownBy(() -> service("http://example.com"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> service("https://example.com/base?query=1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("origin");
    }

    @Test
    void sitemapQueriesReusePublicVisibilityPredicates() throws Exception {
        Query articles = ArticleRepository.class
                .getMethod("findVisibleSitemapBatch", long.class, Instant.class, Pageable.class)
                .getAnnotation(Query.class);
        Query tools = ToolRepository.class
                .getMethod("findVisibleSitemapBatch", long.class, Instant.class, Pageable.class)
                .getAnnotation(Query.class);
        Query topics = TopicRepository.class
                .getMethod("findPublishedSitemapBatch", long.class, Pageable.class)
                .getAnnotation(Query.class);

        assertThat(articles.value()).contains("ArticleStatus.PUBLISHED", "article.publishedAt <= :now");
        assertThat(articles.value()).startsWith("select article.id as id, article.slug as slug");
        assertThat(tools.value()).contains("ToolStatus.PUBLISHED", "tool.publishedAt <= :now");
        assertThat(tools.value()).startsWith("select tool.id as id, tool.slug as slug");
        assertThat(topics.value()).contains("TopicStatus.PUBLISHED");
        assertThat(topics.value()).startsWith("select topic.id as id, topic.slug as slug");
    }

    private SitemapService service(String baseUrl) {
        return new SitemapService(articleRepository, topicRepository, toolRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), baseUrl);
    }

    private static ArticleRepository.SitemapRow article(long id, String slug) {
        ArticleRepository.SitemapRow article = mock(ArticleRepository.SitemapRow.class);
        when(article.getId()).thenReturn(id);
        when(article.getSlug()).thenReturn(slug);
        return article;
    }

    private static TopicRepository.SitemapRow topic(long id, String slug) {
        TopicRepository.SitemapRow topic = mock(TopicRepository.SitemapRow.class);
        when(topic.getId()).thenReturn(id);
        when(topic.getSlug()).thenReturn(slug);
        return topic;
    }

    private static ToolRepository.SitemapRow tool(long id, String slug) {
        ToolRepository.SitemapRow tool = mock(ToolRepository.SitemapRow.class);
        when(tool.getId()).thenReturn(id);
        when(tool.getSlug()).thenReturn(slug);
        return tool;
    }
}
