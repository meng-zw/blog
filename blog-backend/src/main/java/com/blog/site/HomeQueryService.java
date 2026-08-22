package com.blog.site;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.article.ArticleService;
import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.media.MediaAsset;
import com.blog.site.dto.HomeResponse;
import com.blog.tool.Tool;
import com.blog.tool.ToolRepository;
import com.blog.tool.ToolService;
import com.blog.tool.dto.ToolSummaryResponse;
import com.blog.topic.Topic;
import com.blog.topic.TopicRepository;
import com.blog.topic.TopicStatus;
import com.blog.topic.dto.PublicTopicSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class HomeQueryService {
    private final SiteProfileService siteProfileService;
    private final ArticleRepository articleRepository;
    private final ToolRepository toolRepository;
    private final TopicRepository topicRepository;
    private final Clock clock;

    @Autowired
    public HomeQueryService(SiteProfileService siteProfileService, ArticleRepository articleRepository,
                            ToolRepository toolRepository, TopicRepository topicRepository) {
        this(siteProfileService, articleRepository, toolRepository, topicRepository, Clock.systemUTC());
    }

    HomeQueryService(SiteProfileService siteProfileService, ArticleRepository articleRepository,
                     ToolRepository toolRepository, TopicRepository topicRepository, Clock clock) {
        this.siteProfileService = siteProfileService;
        this.articleRepository = articleRepository;
        this.toolRepository = toolRepository;
        this.topicRepository = topicRepository;
        this.clock = clock;
    }

    public HomeResponse getHome() {
        Instant now = clock.instant();
        List<Long> featuredIds = articleRepository.findNewestVisibleArticleIds(now, PageRequest.of(0, 1));
        Long featuredId = featuredIds.isEmpty() ? null : featuredIds.getFirst();
        List<Long> latestCandidateIds = articleRepository.findLatestVisibleIds(now, PageRequest.of(0, 5));
        List<Long> articleIds = java.util.stream.Stream.concat(featuredIds.stream(), latestCandidateIds.stream())
                .distinct().toList();
        Map<Long, ArticleSummaryResponse> articles = articleSummaries(articleIds, now);
        ArticleSummaryResponse featured = featuredId == null ? null : articles.get(featuredId);
        List<ArticleSummaryResponse> latest = latestCandidateIds.stream()
                .filter(id -> !id.equals(featuredId)).limit(4).map(articles::get).filter(java.util.Objects::nonNull).toList();

        List<Long> toolIds = toolRepository.findVisibleFeaturedIds(now, PageRequest.of(0, 4));
        Map<Long, ToolSummaryResponse> tools = toolSummaries(toolIds, now);
        List<ToolSummaryResponse> featuredTools = toolIds.stream().map(tools::get)
                .filter(java.util.Objects::nonNull).toList();
        List<PublicTopicSummaryResponse> topics = topicRepository
                .findPublishedForHome(TopicStatus.PUBLISHED, PageRequest.of(0, 4)).stream()
                .map(HomeQueryService::topicSummary).toList();

        return new HomeResponse(siteProfileService.getProfile(), featured, latest, featuredTools, topics);
    }

    private Map<Long, ArticleSummaryResponse> articleSummaries(List<Long> ids, Instant now) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ArticleSummaryResponse> result = new LinkedHashMap<>();
        for (Article article : articleRepository.findVisibleSummariesByIdIn(ids, now)) {
            result.put(article.getId(), ArticleService.summary(article));
        }
        return result;
    }

    private Map<Long, ToolSummaryResponse> toolSummaries(List<Long> ids, Instant now) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ToolSummaryResponse> result = new LinkedHashMap<>();
        for (Tool tool : toolRepository.findVisibleFeaturedSummariesByIdIn(ids, now)) {
            result.put(tool.getId(), ToolService.summary(tool));
        }
        return result;
    }

    private static PublicTopicSummaryResponse topicSummary(Topic topic) {
        MediaAsset cover = topic.getCoverMedia();
        return new PublicTopicSummaryResponse(topic.getId(), topic.getName(), topic.getSlug(), topic.getDescription(),
                cover == null ? null : "/api/media/" + cover.getStorageKey());
    }
}
