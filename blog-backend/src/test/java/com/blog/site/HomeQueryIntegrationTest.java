package com.blog.site;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.article.ArticleStatus;
import com.blog.article.ContentType;
import com.blog.site.dto.HomeResponse;
import com.blog.site.dto.SiteProfileResponse;
import com.blog.tool.Tool;
import com.blog.tool.ToolRepository;
import com.blog.tool.ToolStatus;
import com.blog.topic.Topic;
import com.blog.topic.TopicRepository;
import com.blog.topic.TopicStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeQueryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Mock SiteProfileService siteProfileService;
    @Mock ArticleRepository articleRepository;
    @Mock ToolRepository toolRepository;
    @Mock TopicRepository topicRepository;

    @Test
    void returnsBoundedPublicSectionsWithoutRepeatingTheFeaturedArticle() {
        when(siteProfileService.getProfile()).thenReturn(new SiteProfileResponse(
                "小M的思与行", "中庸之道", "小M", "Bio", "/avatar.png", "https://github.com/meng-zw"));
        when(articleRepository.findNewestVisibleArticleIds(eq(NOW), any(Pageable.class))).thenReturn(List.of(10L));
        when(articleRepository.findLatestVisibleIds(eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(10L, 9L, 8L, 7L, 6L));
        when(articleRepository.findVisibleSummariesByIdIn(eq(List.of(10L, 9L, 8L, 7L, 6L)), eq(NOW)))
                .thenReturn(List.of(article(7, ContentType.NOTE), article(10, ContentType.ARTICLE),
                        article(6, ContentType.ARTICLE), article(9, ContentType.NOTE), article(8, ContentType.ARTICLE)));
        when(toolRepository.findVisibleFeaturedIds(eq(NOW), any(Pageable.class))).thenReturn(List.of(2L, 1L));
        when(toolRepository.findVisibleFeaturedSummariesByIdIn(List.of(2L, 1L), NOW))
                .thenReturn(List.of(tool(1), tool(2)));
        when(topicRepository.findPublishedForHome(eq(TopicStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(List.of(topic(3), topic(4)));

        HomeResponse result = service().getHome();

        assertThat(result.site().siteTitle()).isEqualTo("小M的思与行");
        assertThat(result.featuredArticle().id()).isEqualTo(10L);
        assertThat(result.latestArticles()).extracting(item -> item.id()).containsExactly(9L, 8L, 7L, 6L);
        assertThat(result.featuredTools()).extracting(item -> item.id()).containsExactly(2L, 1L);
        assertThat(result.topics()).extracting(item -> item.id()).containsExactly(3L, 4L);

        ArgumentCaptor<Pageable> articleLimits = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findNewestVisibleArticleIds(eq(NOW), articleLimits.capture());
        verify(articleRepository).findLatestVisibleIds(eq(NOW), articleLimits.capture());
        assertThat(articleLimits.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1, 5);
        ArgumentCaptor<Pageable> toolLimit = ArgumentCaptor.forClass(Pageable.class);
        verify(toolRepository).findVisibleFeaturedIds(eq(NOW), toolLimit.capture());
        assertThat(toolLimit.getValue().getPageSize()).isEqualTo(4);
        ArgumentCaptor<Pageable> topicLimit = ArgumentCaptor.forClass(Pageable.class);
        verify(topicRepository).findPublishedForHome(eq(TopicStatus.PUBLISHED), topicLimit.capture());
        assertThat(topicLimit.getValue().getPageSize()).isEqualTo(4);
    }

    @Test
    void keepsTheSiteAvailableWhenAllOptionalContentSectionsAreEmpty() {
        SiteProfileResponse site = new SiteProfileResponse("Site", "Subtitle", "Owner", "Bio", null, null);
        when(siteProfileService.getProfile()).thenReturn(site);
        when(articleRepository.findNewestVisibleArticleIds(eq(NOW), any(Pageable.class))).thenReturn(List.of());
        when(articleRepository.findLatestVisibleIds(eq(NOW), any(Pageable.class))).thenReturn(List.of());
        when(toolRepository.findVisibleFeaturedIds(eq(NOW), any(Pageable.class))).thenReturn(List.of());
        when(topicRepository.findPublishedForHome(eq(TopicStatus.PUBLISHED), any(Pageable.class))).thenReturn(List.of());

        HomeResponse result = service().getHome();

        assertThat(result.site()).isEqualTo(site);
        assertThat(result.featuredArticle()).isNull();
        assertThat(result.latestArticles()).isEmpty();
        assertThat(result.featuredTools()).isEmpty();
        assertThat(result.topics()).isEmpty();
    }

    @Test
    void homeRepositoryQueriesUseTheSamePublicVisibilityPredicatesAsDetails() throws Exception {
        Query featured = ArticleRepository.class
                .getMethod("findNewestVisibleArticleIds", Instant.class, Pageable.class).getAnnotation(Query.class);
        Query latest = ArticleRepository.class
                .getMethod("findLatestVisibleIds", Instant.class, Pageable.class).getAnnotation(Query.class);
        Query tools = ToolRepository.class
                .getMethod("findVisibleFeaturedIds", Instant.class, Pageable.class).getAnnotation(Query.class);

        assertThat(featured.value()).contains("ArticleStatus.PUBLISHED", "article.publishedAt <= :now",
                "article.contentType = com.blog.article.ContentType.ARTICLE");
        assertThat(latest.value()).contains("ArticleStatus.PUBLISHED", "article.publishedAt <= :now");
        assertThat(tools.value()).contains("ToolStatus.PUBLISHED", "tool.publishedAt <= :now", "tool.featured = true");
        EntityGraph site = SiteProfileRepository.class.getMethod("findFirstByOrderByIdAsc")
                .getAnnotation(EntityGraph.class);
        assertThat(site.attributePaths()).containsExactly("avatarMedia");
    }

    private HomeQueryService service() {
        return new HomeQueryService(siteProfileService, articleRepository, toolRepository, topicRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Article article(long id, ContentType type) {
        Article article = new Article();
        article.setId(id);
        article.setSlug("article-" + id);
        article.setTitle("Article " + id);
        article.setSummary("Summary " + id);
        article.setContentType(type);
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(NOW.minusSeconds(id));
        return article;
    }

    private static Tool tool(long id) {
        Tool tool = new Tool();
        tool.setId(id);
        tool.setSlug("tool-" + id);
        tool.setName("Tool " + id);
        tool.setSummary("Summary " + id);
        tool.setStatus(ToolStatus.PUBLISHED);
        tool.setFeatured(true);
        tool.setPublishedAt(NOW.minusSeconds(id));
        return tool;
    }

    private static Topic topic(long id) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setSlug("topic-" + id);
        topic.setName("Topic " + id);
        topic.setDescription("Description " + id);
        topic.setStatus(TopicStatus.PUBLISHED);
        topic.setSortOrder((int) id);
        return topic;
    }
}
