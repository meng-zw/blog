package com.blog.article;

import com.blog.article.dto.ArticleDetailResponse;
import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.article.dto.ArticleWriteRequest;
import com.blog.article.dto.AdminArticleResponse;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import com.blog.shared.web.PageResponse;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.CategoryScope;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.taxonomy.Tag;
import com.blog.taxonomy.TaxonomyService;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagResponse;
import com.blog.topic.Topic;
import com.blog.topic.TopicRepository;
import com.blog.topic.dto.TopicResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ArticleService {
    private static final int PUBLISH_BATCH_SIZE = 100;

    private final ArticleRepository articleRepository;
    private final MarkdownRenderer markdownRenderer;
    private final TaxonomyService taxonomyService;
    private final MediaAssetRepository mediaAssetRepository;
    private final TopicRepository topicRepository;
    private final SlugAllocationLockRepository slugAllocationLockRepository;
    private final Clock clock;

    @Autowired
    public ArticleService(ArticleRepository articleRepository, MarkdownRenderer markdownRenderer,
                          TaxonomyService taxonomyService, MediaAssetRepository mediaAssetRepository,
                          TopicRepository topicRepository, SlugAllocationLockRepository slugAllocationLockRepository) {
        this(articleRepository, markdownRenderer, taxonomyService, mediaAssetRepository, topicRepository,
                slugAllocationLockRepository, Clock.systemUTC());
    }

    ArticleService(ArticleRepository articleRepository, MarkdownRenderer markdownRenderer,
                   TaxonomyService taxonomyService, MediaAssetRepository mediaAssetRepository,
                   TopicRepository topicRepository, SlugAllocationLockRepository slugAllocationLockRepository,
                   Clock clock) {
        this.articleRepository = articleRepository;
        this.markdownRenderer = markdownRenderer;
        this.taxonomyService = taxonomyService;
        this.mediaAssetRepository = mediaAssetRepository;
        this.topicRepository = topicRepository;
        this.slugAllocationLockRepository = slugAllocationLockRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminArticleResponse createDraft(ArticleWriteRequest request) {
        slugAllocationLockRepository.lockSingleton();
        Article article = new Article();
        article.setSlug(allocateCreateSlug(request));
        article.setStatus(ArticleStatus.DRAFT);
        apply(article, request, true);
        return adminDetail(articleRepository.save(article));
    }

    @Transactional
    public AdminArticleResponse update(long id, ArticleWriteRequest request) {
        slugAllocationLockRepository.lockSingleton();
        Article article = requireArticle(id);
        if (article.getStatus() == ArticleStatus.ARCHIVED) {
            throw new ConflictException("Archived content cannot be edited");
        }
        updateExplicitSlug(article, request.slug());
        apply(article, request, !request.markdownContent().equals(article.getMarkdownContent()));
        return adminDetail(articleRepository.save(article));
    }

    @Transactional
    public AdminArticleResponse publishNow(long id) {
        Article article = requireArticle(id);
        requireState(article, ArticleStatus.DRAFT, ArticleStatus.SCHEDULED);
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(clock.instant());
        article.setScheduledAt(null);
        return adminDetail(articleRepository.save(article));
    }

    @Transactional
    public AdminArticleResponse schedule(long id, Instant scheduledAt) {
        if (scheduledAt == null || !scheduledAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Scheduled time must be in the future");
        }
        Article article = requireArticle(id);
        requireState(article, ArticleStatus.DRAFT, ArticleStatus.SCHEDULED);
        article.setStatus(ArticleStatus.SCHEDULED);
        article.setScheduledAt(scheduledAt);
        article.setPublishedAt(null);
        return adminDetail(articleRepository.save(article));
    }

    @Transactional
    public AdminArticleResponse archive(long id) {
        Article article = requireArticle(id);
        requireState(article, ArticleStatus.PUBLISHED);
        article.setStatus(ArticleStatus.ARCHIVED);
        article.setScheduledAt(null);
        return adminDetail(articleRepository.save(article));
    }

    @Transactional
    public int publishDue(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Publication time is required");
        }
        return articleRepository.publishDue(now, PUBLISH_BATCH_SIZE);
    }

    public PageResponse<ArticleSummaryResponse> listPublic(int page, int size, ContentType contentType,
                                                           String categorySlug, String tagSlug, String topicSlug,
                                                           String keyword) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        Page<ArticleSummaryResponse> result = articleRepository.findPublicPage(contentType, blankToNull(categorySlug),
                blankToNull(tagSlug), blankToNull(topicSlug), blankToNull(keyword), clock.instant(), pageable)
                .map(ArticleService::summary);
        return PageResponse.from(result);
    }

    public PageResponse<ArticleSummaryResponse> listAdmin(int page, int size, ArticleStatus status,
                                                          ContentType contentType) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return PageResponse.from(articleRepository.findAdminPage(status, contentType, pageable)
                .map(ArticleService::summary));
    }

    public AdminArticleResponse findAdmin(long id) {
        return adminDetail(requireArticle(id));
    }

    public ArticleDetailResponse findPublishedBySlug(String slug) {
        Instant now = clock.instant();
        Article article = articleRepository.findPublishedBySlug(slug, now)
                .orElseThrow(() -> new ResourceNotFoundException("Published article", slug));
        ArticleSummaryResponse previous = first(articleRepository.findPreviousVisible(article.getContentType(),
                article.getPublishedAt(), article.getId(), now, PageRequest.of(0, 1)));
        ArticleSummaryResponse next = first(articleRepository.findNextVisible(article.getContentType(),
                article.getPublishedAt(), article.getId(), now, PageRequest.of(0, 1)));
        return detail(article, previous, next);
    }

    private void apply(Article article, ArticleWriteRequest request, boolean renderMarkdown) {
        article.setTitle(normalizeRequired(request.title(), "Title"));
        article.setSummary(normalizeRequired(request.summary(), "Summary"));
        if (renderMarkdown) {
            article.setRenderedHtml(markdownRenderer.render(request.markdownContent()));
        }
        article.setMarkdownContent(request.markdownContent());
        article.setContentType(request.contentType());
        article.setCoverMedia(requireImage(request.coverMediaId()));
        article.setCategory(request.categoryId() == null ? null
                : taxonomyService.requireCategory(request.categoryId(), CategoryScope.ARTICLE));
        article.setTopic(request.topicId() == null ? null : topicRepository.findById(request.topicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", request.topicId().toString())));
        article.setTags(taxonomyService.requireTags(request.tagIds()));
        article.setSeoTitle(normalizeOptional(request.seoTitle()));
        article.setSeoDescription(normalizeOptional(request.seoDescription()));
    }

    private MediaAsset requireImage(Long id) {
        if (id == null) {
            return null;
        }
        MediaAsset media = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cover media asset", id.toString()));
        if (media.getContentType() == null || !media.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Cover media must be an image");
        }
        return media;
    }

    private String allocateCreateSlug(ArticleWriteRequest request) {
        if (request.slug() != null && !request.slug().isBlank()) {
            if (articleRepository.existsBySlug(request.slug())) {
                throw new ConflictException("An article with this slug already exists");
            }
            return request.slug();
        }
        String base = TaxonomyService.slugBase(request.title(), "article");
        for (int suffix = 1; ; suffix++) {
            String candidate = suffix == 1 ? base : suffixed(base, suffix);
            if (!articleRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
    }

    private void updateExplicitSlug(Article article, String requestedSlug) {
        if (requestedSlug == null || requestedSlug.isBlank() || requestedSlug.equals(article.getSlug())) {
            return;
        }
        if (articleRepository.existsBySlug(requestedSlug)) {
            throw new ConflictException("An article with this slug already exists");
        }
        article.setSlug(requestedSlug);
    }

    private static String suffixed(String base, int suffix) {
        String suffixText = "-" + suffix;
        int maximumBaseLength = 160 - suffixText.length();
        String truncated = base.substring(0, Math.min(base.length(), maximumBaseLength)).replaceAll("-+$", "");
        return truncated + suffixText;
    }

    private Article requireArticle(long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", Long.toString(id)));
    }

    private static void requireState(Article article, ArticleStatus... allowed) {
        for (ArticleStatus state : allowed) {
            if (article.getStatus() == state) {
                return;
            }
        }
        throw new ConflictException("Illegal article state transition from " + article.getStatus());
    }

    private static ArticleSummaryResponse first(List<Article> articles) {
        return articles.isEmpty() ? null : summary(articles.getFirst());
    }

    public static ArticleSummaryResponse summary(Article article) {
        return new ArticleSummaryResponse(article.getId(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getContentType(), article.getStatus(), article.getPublishedAt(), article.getScheduledAt(),
                mediaUrl(article.getCoverMedia()),
                category(article.getCategory()), tags(article.getTags()));
    }

    private static ArticleDetailResponse detail(Article article, ArticleSummaryResponse previous,
                                                ArticleSummaryResponse next) {
        return new ArticleDetailResponse(article.getId(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getContentType(), article.getPublishedAt(), mediaUrl(article.getCoverMedia()),
                category(article.getCategory()), tags(article.getTags()), topic(article.getTopic()),
                article.getRenderedHtml(), article.getSeoTitle(), article.getSeoDescription(), previous, next);
    }

    private static AdminArticleResponse adminDetail(Article article) {
        return new AdminArticleResponse(article.getId(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getMarkdownContent(), article.getRenderedHtml(), article.getContentType(), article.getStatus(),
                article.getPublishedAt(), article.getScheduledAt(), mediaUrl(article.getCoverMedia()),
                category(article.getCategory()), tags(article.getTags()), topic(article.getTopic()),
                article.getSeoTitle(), article.getSeoDescription());
    }

    private static CategoryResponse category(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getSortOrder(), category.getScope());
    }

    private static List<TagResponse> tags(Set<Tag> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream().sorted(Comparator.comparing(Tag::getName).thenComparing(Tag::getId))
                .map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.getSlug())).toList();
    }

    private static TopicResponse topic(Topic topic) {
        return topic == null ? null : new TopicResponse(topic.getId(), topic.getName(), topic.getSlug(),
                topic.getDescription(), mediaUrl(topic.getCoverMedia()), topic.getStatus(), topic.getSortOrder());
    }

    private static String mediaUrl(MediaAsset media) {
        return media == null ? null : "/api/media/" + media.getStorageKey();
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
