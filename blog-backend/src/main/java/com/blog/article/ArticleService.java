package com.blog.article;

import com.blog.article.dto.ArticleDetailResponse;
import com.blog.article.dto.ArticleAttachmentResponse;
import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.article.dto.ArticleWriteRequest;
import com.blog.article.dto.AdminArticleResponse;
import com.blog.article.dto.AdminArticleSummaryResponse;
import com.blog.article.dto.PublicCategoryResponse;
import com.blog.article.dto.PublicTagResponse;
import com.blog.article.dto.PublicTopicResponse;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.media.ArticleMedia;
import com.blog.media.ArticleMediaReferenceService;
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
import com.blog.topic.TopicMembershipManager;
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
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ArticleService {
    private static final int PUBLISH_BATCH_SIZE = 100;
    private static final Pattern EXPLICIT_SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final ArticleRepository articleRepository;
    private final MarkdownRenderer markdownRenderer;
    private final TaxonomyService taxonomyService;
    private final MediaAssetRepository mediaAssetRepository;
    private final TopicRepository topicRepository;
    private final SlugAllocationLockRepository slugAllocationLockRepository;
    private final TopicMembershipManager topicMembershipManager;
    private final ArticleMediaReferenceService articleMediaReferenceService;
    private final Clock clock;

    @Autowired
    public ArticleService(ArticleRepository articleRepository, MarkdownRenderer markdownRenderer,
                          TaxonomyService taxonomyService, MediaAssetRepository mediaAssetRepository,
                          TopicRepository topicRepository, SlugAllocationLockRepository slugAllocationLockRepository,
                          TopicMembershipManager topicMembershipManager,
                          ArticleMediaReferenceService articleMediaReferenceService) {
        this(articleRepository, markdownRenderer, taxonomyService, mediaAssetRepository, topicRepository,
                slugAllocationLockRepository, topicMembershipManager, articleMediaReferenceService, Clock.systemUTC());
    }

    ArticleService(ArticleRepository articleRepository, MarkdownRenderer markdownRenderer,
                   TaxonomyService taxonomyService, MediaAssetRepository mediaAssetRepository,
                   TopicRepository topicRepository, SlugAllocationLockRepository slugAllocationLockRepository,
                   TopicMembershipManager topicMembershipManager,
                   ArticleMediaReferenceService articleMediaReferenceService, Clock clock) {
        this.articleRepository = articleRepository;
        this.markdownRenderer = markdownRenderer;
        this.taxonomyService = taxonomyService;
        this.mediaAssetRepository = mediaAssetRepository;
        this.topicRepository = topicRepository;
        this.slugAllocationLockRepository = slugAllocationLockRepository;
        this.topicMembershipManager = topicMembershipManager;
        this.articleMediaReferenceService = articleMediaReferenceService;
        this.clock = clock;
    }

    @Transactional
    public AdminArticleResponse createDraft(ArticleWriteRequest request) {
        NormalizedInput input = normalizeInput(request);
        slugAllocationLockRepository.lockSingleton();
        Article article = new Article();
        article.setSlug(allocateCreateSlug(input));
        article.setStatus(ArticleStatus.DRAFT);
        apply(article, request, input, true);
        Article saved = articleRepository.save(article);
        topicMembershipManager.synchronizeArticle(saved);
        articleMediaReferenceService.synchronize(saved, request.markdownContent(), request.attachmentMediaIds());
        return adminDetail(saved);
    }

    @Transactional
    public AdminArticleResponse update(long id, ArticleWriteRequest request) {
        NormalizedInput input = normalizeInput(request);
        slugAllocationLockRepository.lockSingleton();
        Article article = requireArticle(id);
        if (article.getStatus() == ArticleStatus.ARCHIVED) {
            throw new ConflictException("Archived content cannot be edited");
        }
        updateExplicitSlug(article, input.slug());
        apply(article, request, input, !request.markdownContent().equals(article.getMarkdownContent()));
        Article saved = articleRepository.save(article);
        topicMembershipManager.synchronizeArticle(saved);
        articleMediaReferenceService.synchronize(saved, request.markdownContent(), request.attachmentMediaIds());
        return adminDetail(saved);
    }

    @Transactional
    public AdminArticleResponse publishNow(long id) {
        Article article = requireArticle(id);
        requireState(article, ArticleStatus.DRAFT, ArticleStatus.SCHEDULED);
        requirePublishableTopic(article);
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
        requirePublishableTopic(article);
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
        List<Article> due = articleRepository.findDueForPublishing(now, PageRequest.of(0, PUBLISH_BATCH_SIZE));
        List<Article> publishable = due.stream().filter(article -> article.getStatus() == ArticleStatus.SCHEDULED)
                .filter(article -> article.getScheduledAt() != null && !article.getScheduledAt().isAfter(now))
                .filter(ArticleService::hasPublishableTopic)
                .toList();
        for (Article article : publishable) {
            article.setStatus(ArticleStatus.PUBLISHED);
            article.setPublishedAt(article.getScheduledAt());
            article.setScheduledAt(null);
        }
        if (!publishable.isEmpty()) {
            articleRepository.saveAll(publishable);
        }
        return publishable.size();
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

    public PageResponse<AdminArticleSummaryResponse> listAdmin(int page, int size, ArticleStatus status,
                                                               ContentType contentType, String keyword) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return PageResponse.from(articleRepository.findAdminPage(status, contentType, blankToNull(keyword), pageable)
                .map(ArticleService::adminSummary));
    }

    public List<AdminArticleSummaryResponse> lookupAdmin(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new java.util.HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Between 1 and 50 unique positive article IDs are required");
        }
        var found = articleRepository.findAdminSummariesByIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Article::getId, java.util.function.Function.identity()));
        if (found.size() != ids.size()) throw new ResourceNotFoundException("Article", "one or more requested IDs");
        return ids.stream().map(found::get).map(ArticleService::adminSummary).toList();
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

    private void apply(Article article, ArticleWriteRequest request, NormalizedInput input, boolean renderMarkdown) {
        article.setTitle(input.title());
        article.setSummary(input.summary());
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
        article.setSeoTitle(input.seoTitle());
        article.setSeoDescription(input.seoDescription());
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

    private String allocateCreateSlug(NormalizedInput input) {
        if (input.slug() != null) {
            if (articleRepository.existsBySlug(input.slug())) {
                throw new ConflictException("An article with this slug already exists");
            }
            return input.slug();
        }
        String base = TaxonomyService.slugBase(input.title(), "article");
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

    private static void requirePublishableTopic(Article article) {
        if (!hasPublishableTopic(article)) {
            throw new ConflictException("Article topic must be published before publication can be scheduled");
        }
    }

    private static boolean hasPublishableTopic(Article article) {
        return article.getTopic() == null || article.getTopic().getStatus() == com.blog.topic.TopicStatus.PUBLISHED;
    }

    private static ArticleSummaryResponse first(List<Article> articles) {
        return articles.isEmpty() ? null : summary(articles.getFirst());
    }

    public static ArticleSummaryResponse summary(Article article) {
        return new ArticleSummaryResponse(article.getId(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getContentType(), article.getPublishedAt(), mediaUrl(article.getCoverMedia()),
                publicCategory(article.getCategory()), publicTags(article.getTags()));
    }

    private ArticleDetailResponse detail(Article article, ArticleSummaryResponse previous,
                                         ArticleSummaryResponse next) {
        return new ArticleDetailResponse(article.getId(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getContentType(), article.getPublishedAt(), mediaUrl(article.getCoverMedia()),
                publicCategory(article.getCategory()), publicTags(article.getTags()), publicTopic(article.getTopic()),
                article.getRenderedHtml(), article.getSeoTitle(), article.getSeoDescription(), previous, next,
                attachments(article));
    }

    private AdminArticleResponse adminDetail(Article article) {
        return new AdminArticleResponse(article.getId(), article.getSlug(), article.getTitle(), article.getSummary(),
                article.getMarkdownContent(), article.getRenderedHtml(), article.getContentType(), article.getStatus(),
                article.getPublishedAt(), article.getScheduledAt(), mediaUrl(article.getCoverMedia()), mediaId(article.getCoverMedia()),
                category(article.getCategory()), tags(article.getTags()), topic(article.getTopic()),
                article.getSeoTitle(), article.getSeoDescription(), attachments(article));
    }

    private List<ArticleAttachmentResponse> attachments(Article article) {
        if (article.getId() == null) {
            return List.of();
        }
        return articleMediaReferenceService.attachmentsFor(article.getId()).stream()
                .map(ArticleService::attachment)
                .toList();
    }

    private static ArticleAttachmentResponse attachment(ArticleMedia reference) {
        MediaAsset media = reference.getMedia();
        String displayName = reference.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = media.getOriginalFilename();
        }
        return new ArticleAttachmentResponse(media.getId(), displayName, media.getContentType(), media.getByteSize(),
                "/api/media/assets/" + media.getId() + "/download");
    }

    private static AdminArticleSummaryResponse adminSummary(Article article) {
        return new AdminArticleSummaryResponse(article.getId(), article.getSlug(), article.getTitle(),
                article.getSummary(), article.getContentType(), article.getStatus(), article.getPublishedAt(),
                article.getScheduledAt(), mediaUrl(article.getCoverMedia()), category(article.getCategory()),
                tags(article.getTags()));
    }

    private static CategoryResponse category(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getSortOrder(), category.getScope());
    }

    private static Long mediaId(MediaAsset media) { return media == null ? null : media.getId(); }

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

    private static PublicCategoryResponse publicCategory(Category category) {
        return category == null ? null : new PublicCategoryResponse(category.getId(), category.getName(), category.getSlug());
    }

    private static List<PublicTagResponse> publicTags(Set<Tag> tags) {
        if (tags == null) return List.of();
        return tags.stream().sorted(Comparator.comparing(Tag::getName).thenComparing(Tag::getId))
                .map(tag -> new PublicTagResponse(tag.getId(), tag.getName(), tag.getSlug())).toList();
    }

    private static PublicTopicResponse publicTopic(Topic topic) {
        return topic == null || topic.getStatus() != com.blog.topic.TopicStatus.PUBLISHED ? null
                : new PublicTopicResponse(topic.getId(), topic.getName(), topic.getSlug());
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

    private static NormalizedInput normalizeInput(ArticleWriteRequest request) {
        if (request == null) throw new IllegalArgumentException("Article request is required");
        String title = bounded(normalizeRequired(request.title(), "Title"), 200, "Title");
        String summary = bounded(normalizeRequired(request.summary(), "Summary"), 500, "Summary");
        String slug = normalizeOptional(request.slug());
        if (slug != null && slug.isBlank()) slug = null;
        if (slug != null) {
            bounded(slug, 160, "Slug");
            if (!EXPLICIT_SLUG.matcher(slug).matches()) {
                throw new IllegalArgumentException("Slug must be lowercase URL-safe text");
            }
        }
        String seoTitle = bounded(normalizeOptional(request.seoTitle()), 70, "SEO title");
        String seoDescription = bounded(normalizeOptional(request.seoDescription()), 160, "SEO description");
        return new NormalizedInput(title, slug, summary, seoTitle, seoDescription);
    }

    private static String bounded(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(field + " is too long after normalization");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NormalizedInput(String title, String slug, String summary,
                                   String seoTitle, String seoDescription) {
    }
}
