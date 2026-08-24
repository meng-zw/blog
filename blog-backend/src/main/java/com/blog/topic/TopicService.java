package com.blog.topic;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.article.ArticleService;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.media.MediaApplicationService;
import com.blog.media.MediaPurpose;
import com.blog.media.MediaStatus;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import com.blog.shared.web.PageResponse;
import com.blog.taxonomy.TaxonomyService;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.topic.dto.TopicResponse;
import com.blog.topic.dto.PublicTopicDetailResponse;
import com.blog.topic.dto.PublicTopicSummaryResponse;
import com.blog.topic.dto.TopicWriteRequest;
import com.blog.topic.dto.AdminTopicResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Predicate;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class TopicService {
    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final SlugAllocationLockRepository slugAllocationLockRepository;
    private final ArticleRepository articleRepository;
    private final TopicMembershipManager topicMembershipManager;

    public TopicService(TopicRepository topicRepository, TopicArticleRepository topicArticleRepository,
                        MediaAssetRepository mediaAssetRepository, SlugAllocationLockRepository slugAllocationLockRepository,
                        ArticleRepository articleRepository, TopicMembershipManager topicMembershipManager) {
        this.topicRepository = topicRepository;
        this.topicArticleRepository = topicArticleRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.slugAllocationLockRepository = slugAllocationLockRepository;
        this.articleRepository = articleRepository;
        this.topicMembershipManager = topicMembershipManager;
    }

    public PageResponse<AdminTopicResponse> listAdmin(int page, int size, TopicStatus status, String keyword) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("Topic page must be zero or greater and size between 1 and 50");
        var topics = topicRepository.findAdminPage(status, blankToNull(keyword), PageRequest.of(page, size));
        var ids = topics.stream().map(Topic::getId).toList();
        Map<Long, List<Long>> articleIds = topicArticleRepository.findByTopicIdInOrderByTopicIdAscSortOrderAsc(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(TopicArticle::getTopicId, LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(TopicArticle::getArticleId, java.util.stream.Collectors.toList())));
        return PageResponse.from(topics.map(topic -> adminResponse(topic, articleIds.getOrDefault(topic.getId(), List.of()))));
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public AdminTopicResponse findAdmin(long id) {
        Topic topic = requireTopic(id);
        return adminResponse(topic, topicArticleRepository.findByTopicIdOrderBySortOrderAsc(id).stream().map(TopicArticle::getArticleId).toList());
    }

    public PageResponse<PublicTopicSummaryResponse> listPublished(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Topic page must be zero or greater");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("Topic page size must be between 1 and 50");
        }
        return PageResponse.from(topicRepository.findPublishedPage(PageRequest.of(page, size))
                .map(TopicService::publicResponse));
    }

    public PublicTopicDetailResponse findPublishedDetailBySlug(String slug) {
        Topic topic = topicRepository.findBySlug(slug)
                .filter(found -> found.getStatus() == TopicStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published topic", slug));
        Instant now = Instant.now();
        List<com.blog.article.dto.ArticleSummaryResponse> articles = articleRepository
                .findVisibleForTopic(topic.getId(), now).stream()
                .filter(article -> visible(article, now))
                .map(ArticleService::summary)
                .toList();
        PublicTopicSummaryResponse metadata = publicResponse(topic);
        return new PublicTopicDetailResponse(metadata.id(), metadata.name(), metadata.slug(), metadata.description(),
                metadata.coverUrl(), articles);
    }

    @Transactional
    public TopicResponse create(TopicWriteRequest request) {
        slugAllocationLockRepository.lockSingleton();
        List<Article> articles = requireArticles(articleIds(request.articleIds()));
        Topic topic = new Topic();
        apply(topic, request, null);
        Topic saved = topicRepository.save(topic);
        topicMembershipManager.replaceTopic(saved, articles);
        return response(saved);
    }

    @Transactional
    public TopicResponse update(long id, TopicWriteRequest request) {
        slugAllocationLockRepository.lockSingleton();
        List<Article> articles = requireArticles(articleIds(request.articleIds()));
        Topic topic = requireTopic(id);
        apply(topic, request, id);
        Topic saved = topicRepository.save(topic);
        topicMembershipManager.replaceTopic(saved, articles);
        return response(saved);
    }

    @Transactional
    public void delete(long id) {
        Topic topic = requireTopic(id);
        topicRepository.delete(topic);
    }

    @Transactional
    public void reorderArticles(long topicId, List<Long> requestedArticleIds) {
        Topic topic = requireTopic(topicId);
        List<Long> requested = articleIds(requestedArticleIds);
        List<TopicArticle> existing = topicArticleRepository.findByTopicIdOrderBySortOrderAsc(topicId);
        Set<Long> existingIds = existing.stream().map(TopicArticle::getArticleId).collect(java.util.stream.Collectors.toSet());
        if (existingIds.size() != requested.size() || !existingIds.equals(new HashSet<>(requested))) {
            throw new IllegalArgumentException("The complete existing topic article list is required for reorder");
        }
        topicMembershipManager.replaceTopic(topic, requireArticles(requested));
    }

    private void apply(Topic topic, TopicWriteRequest request, Long currentId) {
        String name = TaxonomyService.normalizedName(request.name());
        TaxonomyService.validateNameBounds(name, 160);
        String normalizedName = TaxonomyService.normalizedKey(name);
        TaxonomyService.validateKeyBounds(normalizedName);
        topicRepository.findByNormalizedName(normalizedName).filter(found -> !found.getId().equals(currentId))
                .ifPresent(ignored -> { throw new ConflictException("A topic with this name already exists"); });
        topic.setName(name);
        if (!normalizedName.equals(topic.getNormalizedName())) topic.setSlug(nextSlug(TaxonomyService.slugBase(name, "topic"), topic.getSlug()));
        topic.setNormalizedName(normalizedName);
        topic.setDescription(request.description() == null ? null : Normalizer.normalize(request.description(), Normalizer.Form.NFKC).trim());
        topic.setCoverMedia(requireCover(request.coverMediaId(), topic.getCoverMedia()));
        topic.setStatus(request.status());
        topic.setSortOrder(request.sortOrder());
    }

    private String nextSlug(String base, String currentSlug) {
        Predicate<String> occupied = candidate -> topicRepository.existsBySlug(candidate)
                && !candidate.equals(currentSlug);
        for (int suffix = 1; ; suffix++) {
            String candidate = suffix == 1 ? base : suffix(base, suffix);
            if (!occupied.test(candidate)) {
                return candidate;
            }
        }
    }

    private static String suffix(String base, int suffix) {
        String suffixText = "-" + suffix;
        int maximumBaseLength = 180 - suffixText.length();
        return base.substring(0, Math.min(base.length(), maximumBaseLength)).replaceAll("-+$", "") + suffixText;
    }

    private List<Article> requireArticles(List<Long> articleIds) {
        if (articleIds.isEmpty()) return List.of();
        Map<Long, Article> found = new LinkedHashMap<>();
        articleRepository.findAllById(articleIds).forEach(article -> found.put(article.getId(), article));
        if (found.size() != articleIds.size()) {
            throw new ResourceNotFoundException("Article", "one or more requested IDs");
        }
        return articleIds.stream().map(found::get).toList();
    }

    private static boolean visible(Article article, Instant now) {
        return article.isVisibleAt(now);
    }

    private MediaAsset requireCover(Long id, MediaAsset existing) {
        if (id == null) return null;
        MediaAsset media = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cover media asset", id.toString()));
        if (media.getContentType() == null || !media.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Cover media must be an image");
        }
        if (media.getStatus() != MediaStatus.READY) throw new IllegalArgumentException("Cover media must be ready");
        if (media.getPurpose() != MediaPurpose.TOPIC_COVER && (existing == null || !media.getId().equals(existing.getId()))) {
            throw new IllegalArgumentException("Cover media purpose must be TOPIC_COVER");
        }
        return media;
    }

    private static List<Long> articleIds(List<Long> ids) {
        if (ids == null) throw new IllegalArgumentException("Article IDs are required");
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Article IDs must be positive");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Article IDs must not contain duplicates");
        }
        return List.copyOf(ids);
    }

    private Topic requireTopic(long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", Long.toString(id)));
    }

    private static TopicResponse response(Topic topic) {
        MediaAsset cover = topic.getCoverMedia();
        String coverUrl = MediaApplicationService.stableUrl(cover);
        return new TopicResponse(topic.getId(), topic.getName(), topic.getSlug(), topic.getDescription(), coverUrl,
                topic.getStatus(), topic.getSortOrder());
    }

    private AdminTopicResponse adminResponse(Topic topic, List<Long> articleIds) {
        TopicResponse metadata = response(topic);
        return new AdminTopicResponse(metadata.id(), metadata.name(), metadata.slug(), metadata.description(),
                metadata.coverUrl(), topic.getCoverMedia() == null ? null : topic.getCoverMedia().getId(),
                metadata.status(), metadata.sortOrder(), articleIds);
    }

    private static PublicTopicSummaryResponse publicResponse(Topic topic) {
        MediaAsset cover = topic.getCoverMedia();
        String coverUrl = MediaApplicationService.stableUrl(cover);
        return new PublicTopicSummaryResponse(topic.getId(), topic.getName(), topic.getSlug(),
                topic.getDescription(), coverUrl);
    }
}
