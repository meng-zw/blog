package com.blog.topic;

import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import com.blog.taxonomy.TaxonomyService;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.topic.dto.TopicResponse;
import com.blog.topic.dto.TopicWriteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;

@Service
@Transactional(readOnly = true)
public class TopicService {
    private final TopicRepository topicRepository;
    private final TopicArticleRepository topicArticleRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final SlugAllocationLockRepository slugAllocationLockRepository;

    public TopicService(TopicRepository topicRepository, TopicArticleRepository topicArticleRepository,
                        MediaAssetRepository mediaAssetRepository, SlugAllocationLockRepository slugAllocationLockRepository) {
        this.topicRepository = topicRepository;
        this.topicArticleRepository = topicArticleRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.slugAllocationLockRepository = slugAllocationLockRepository;
    }

    public List<TopicResponse> listAdmin() {
        return topicRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(TopicService::response).toList();
    }

    public TopicResponse findAdmin(long id) {
        return response(requireTopic(id));
    }

    public List<TopicResponse> listPublished() {
        return topicRepository.findAllByStatusOrderBySortOrderAscIdAsc(TopicStatus.PUBLISHED).stream()
                .map(TopicService::response).toList();
    }

    public TopicResponse findPublishedBySlug(String slug) {
        Topic topic = topicRepository.findBySlug(slug)
                .filter(found -> found.getStatus() == TopicStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published topic", slug));
        return response(topic);
    }

    @Transactional
    public TopicResponse create(TopicWriteRequest request) {
        List<Long> articles = articleIds(request.articleIds());
        Topic topic = new Topic();
        apply(topic, request, null);
        Topic saved = topicRepository.save(topic);
        replaceArticles(saved.getId(), articles);
        return response(saved);
    }

    @Transactional
    public TopicResponse update(long id, TopicWriteRequest request) {
        List<Long> articles = articleIds(request.articleIds());
        Topic topic = requireTopic(id);
        apply(topic, request, id);
        Topic saved = topicRepository.save(topic);
        replaceArticles(id, articles);
        return response(saved);
    }

    @Transactional
    public void delete(long id) {
        Topic topic = requireTopic(id);
        topicRepository.delete(topic);
    }

    @Transactional
    public void reorderArticles(long topicId, List<Long> requestedArticleIds) {
        requireTopic(topicId);
        List<Long> requested = articleIds(requestedArticleIds);
        List<TopicArticle> existing = topicArticleRepository.findByTopicIdOrderBySortOrderAsc(topicId);
        Set<Long> existingIds = existing.stream().map(TopicArticle::getArticleId).collect(java.util.stream.Collectors.toSet());
        if (existingIds.size() != requested.size() || !existingIds.equals(new HashSet<>(requested))) {
            throw new IllegalArgumentException("The complete existing topic article list is required for reorder");
        }
        validateExistingArticleIds(requested);
        topicArticleRepository.saveAll(topicArticles(topicId, requested));
    }

    private void apply(Topic topic, TopicWriteRequest request, Long currentId) {
        slugAllocationLockRepository.acquire();
        String name = TaxonomyService.normalizedName(request.name());
        String normalizedName = TaxonomyService.normalizedKey(name);
        topicRepository.findByNormalizedName(normalizedName).filter(found -> !found.getId().equals(currentId))
                .ifPresent(ignored -> { throw new ConflictException("A topic with this name already exists"); });
        topic.setName(name);
        if (!normalizedName.equals(topic.getNormalizedName())) topic.setSlug(nextSlug(TaxonomyService.slugBase(name, "topic"), topic.getSlug()));
        topic.setNormalizedName(normalizedName);
        topic.setDescription(request.description() == null ? null : Normalizer.normalize(request.description(), Normalizer.Form.NFKC).trim());
        topic.setCoverMedia(request.coverMediaId() == null ? null : mediaAssetRepository.findById(request.coverMediaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cover media asset", request.coverMediaId().toString())));
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

    private void replaceArticles(Long topicId, List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            topicArticleRepository.deleteByTopicId(topicId);
            return;
        }
        validateExistingArticleIds(articleIds);
        topicArticleRepository.deleteByTopicId(topicId);
        topicArticleRepository.saveAll(topicArticles(topicId, articleIds));
    }

    private void validateExistingArticleIds(List<Long> articleIds) {
        if (!articleIds.isEmpty() && topicArticleRepository.countExistingArticlesByIds(articleIds) != articleIds.size()) {
            throw new ResourceNotFoundException("Article", "one or more requested IDs");
        }
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

    private static TopicArticle topicArticle(Long topicId, Long articleId, int sortOrder) {
        TopicArticle topicArticle = new TopicArticle();
        topicArticle.setTopicId(topicId);
        topicArticle.setArticleId(articleId);
        topicArticle.setSortOrder(sortOrder);
        return topicArticle;
    }

    private static List<TopicArticle> topicArticles(Long topicId, List<Long> articleIds) {
        List<TopicArticle> topicArticles = new ArrayList<>(articleIds.size());
        for (int position = 0; position < articleIds.size(); position++) {
            topicArticles.add(topicArticle(topicId, articleIds.get(position), position));
        }
        return topicArticles;
    }

    private static TopicResponse response(Topic topic) {
        MediaAsset cover = topic.getCoverMedia();
        String coverUrl = cover == null ? null : "/api/media/" + cover.getStorageKey();
        return new TopicResponse(topic.getId(), topic.getName(), topic.getSlug(), topic.getDescription(), coverUrl,
                topic.getStatus(), topic.getSortOrder());
    }
}
