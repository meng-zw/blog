package com.blog.tool;

import com.blog.article.MarkdownRenderer;
import com.blog.article.dto.PublicCategoryResponse;
import com.blog.article.dto.PublicTagResponse;
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
import com.blog.tool.dto.AdminToolResponse;
import com.blog.tool.dto.AdminToolSummaryResponse;
import com.blog.tool.dto.ToolDetailResponse;
import com.blog.tool.dto.ToolSummaryResponse;
import com.blog.tool.dto.ToolWriteRequest;
import com.ibm.icu.lang.UCharacter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class ToolService {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final ToolRepository toolRepository;
    private final MarkdownRenderer markdownRenderer;
    private final TaxonomyService taxonomyService;
    private final MediaAssetRepository mediaAssetRepository;
    private final SlugAllocationLockRepository slugAllocationLockRepository;
    private final Clock clock;

    @Autowired
    public ToolService(ToolRepository toolRepository, MarkdownRenderer markdownRenderer, TaxonomyService taxonomyService,
                       MediaAssetRepository mediaAssetRepository, SlugAllocationLockRepository slugAllocationLockRepository) {
        this(toolRepository, markdownRenderer, taxonomyService, mediaAssetRepository, slugAllocationLockRepository,
                Clock.systemUTC());
    }

    ToolService(ToolRepository toolRepository, MarkdownRenderer markdownRenderer, TaxonomyService taxonomyService,
                MediaAssetRepository mediaAssetRepository, SlugAllocationLockRepository slugAllocationLockRepository,
                Clock clock) {
        this.toolRepository = toolRepository;
        this.markdownRenderer = markdownRenderer;
        this.taxonomyService = taxonomyService;
        this.mediaAssetRepository = mediaAssetRepository;
        this.slugAllocationLockRepository = slugAllocationLockRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminToolResponse createDraft(ToolWriteRequest request) {
        NormalizedInput input = normalizeInput(request);
        slugAllocationLockRepository.lockSingleton();
        Tool tool = new Tool();
        tool.setSlug(allocateCreateSlug(input.slug(), input.name()));
        tool.setStatus(ToolStatus.DRAFT);
        tool.setSortOrder(nextGlobalSortOrder());
        apply(tool, request, input, true);
        return adminDetail(toolRepository.save(tool));
    }

    @Transactional
    public AdminToolResponse update(long id, ToolWriteRequest request) {
        NormalizedInput input = normalizeInput(request);
        slugAllocationLockRepository.lockSingleton();
        Tool tool = requireTool(id);
        if (tool.getStatus() == ToolStatus.ARCHIVED) {
            throw new ConflictException("Archived tools cannot be edited");
        }
        updateExplicitSlug(tool, input.slug());
        apply(tool, request, input, !request.descriptionMarkdown().equals(tool.getDescriptionMarkdown()));
        return adminDetail(toolRepository.save(tool));
    }

    @Transactional
    public AdminToolResponse publish(long id) {
        slugAllocationLockRepository.lockSingleton();
        Tool tool = requireTool(id);
        requireState(tool, ToolStatus.DRAFT);
        tool.setStatus(ToolStatus.PUBLISHED);
        tool.setPublishedAt(clock.instant());
        return adminDetail(toolRepository.save(tool));
    }

    @Transactional
    public AdminToolResponse archive(long id) {
        slugAllocationLockRepository.lockSingleton();
        Tool tool = requireTool(id);
        requireState(tool, ToolStatus.PUBLISHED);
        tool.setStatus(ToolStatus.ARCHIVED);
        return adminDetail(toolRepository.save(tool));
    }

    @Transactional
    public void delete(long id) {
        slugAllocationLockRepository.lockSingleton();
        Tool tool = requireTool(id);
        List<Tool> remaining = toolRepository.findAllForReorder().stream().filter(candidate -> !candidate.getId().equals(id)).toList();
        toolRepository.delete(tool);
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setSortOrder(index);
        if (!remaining.isEmpty()) toolRepository.saveAll(remaining);
    }

    public PageResponse<ToolSummaryResponse> listPublic(int page, int size, String categorySlug, String tagSlug,
                                                         String keyword) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("featured"),
                Sort.Order.asc("sortOrder"), Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        Page<ToolSummaryResponse> result = toolRepository.findPublicPage(blankToNull(categorySlug), blankToNull(tagSlug),
                blankToNull(keyword), clock.instant(), pageable).map(ToolService::summary);
        return PageResponse.from(result);
    }

    public ToolDetailResponse findPublishedBySlug(String slug) {
        Tool tool = toolRepository.findPublishedBySlug(slug, clock.instant())
                .orElseThrow(() -> new ResourceNotFoundException("Published tool", slug));
        return detail(tool);
    }

    public PageResponse<AdminToolSummaryResponse> listAdmin(int page, int size, ToolStatus status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return PageResponse.from(toolRepository.findAdminPage(status, pageable).map(ToolService::adminSummary));
    }

    public AdminToolResponse findAdmin(long id) {
        return adminDetail(requireTool(id));
    }

    /**
     * The request must contain every and only current tool ID, regardless of lifecycle status. The list position becomes the persisted
     * zero-based sort order; validation completes before any managed entity is changed, then one transaction saves it.
     */
    @Transactional
    public void reorder(List<Long> orderedIds) {
        slugAllocationLockRepository.lockSingleton();
        if (orderedIds == null || orderedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Tool IDs are required and must be positive");
        }
        if (new HashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new IllegalArgumentException("Tool IDs must not contain duplicates");
        }
        List<Tool> tools = toolRepository.findAllForReorder();
        Map<Long, Tool> byId = tools.stream().collect(Collectors.toMap(Tool::getId, Function.identity()));
        if (byId.size() != orderedIds.size() || !byId.keySet().equals(Set.copyOf(orderedIds))) {
            throw new ConflictException("Tool IDs no longer match the complete tool list");
        }
        List<Tool> reordered = orderedIds.stream().map(byId::get).toList();
        for (int index = 0; index < reordered.size(); index++) {
            reordered.get(index).setSortOrder(index);
        }
        if (!reordered.isEmpty()) {
            toolRepository.saveAll(reordered);
        }
    }

    private void apply(Tool tool, ToolWriteRequest request, NormalizedInput input, boolean renderMarkdown) {
        tool.setName(input.name());
        tool.setSummary(input.summary());
        if (renderMarkdown) {
            tool.setRenderedHtml(markdownRenderer.render(request.descriptionMarkdown()));
        }
        tool.setDescriptionMarkdown(request.descriptionMarkdown());
        tool.setOfficialUrl(input.officialUrl());
        tool.setCoverMedia(requireImage(request.coverMediaId()));
        tool.setCategory(request.categoryId() == null ? null
                : taxonomyService.requireCategory(request.categoryId(), CategoryScope.TOOL));
        tool.setTags(taxonomyService.requireTags(request.tagIds()));
        tool.setFeatured(request.featured());
    }

    private MediaAsset requireImage(Long id) {
        if (id == null) return null;
        MediaAsset media = mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cover media asset", id.toString()));
        if (media.getContentType() == null || !media.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Cover media must be an image");
        }
        return media;
    }

    private String allocateCreateSlug(String explicitSlug, String name) {
        if (explicitSlug != null) {
            if (toolRepository.existsBySlug(explicitSlug)) {
                throw new ConflictException("A tool with this slug already exists");
            }
            return explicitSlug;
        }
        String base = TaxonomyService.slugBase(name, "tool");
        for (int suffix = 1; ; suffix++) {
            String candidate = suffix == 1 ? base : suffixed(base, suffix);
            if (!toolRepository.existsBySlug(candidate)) return candidate;
        }
    }

    private int nextGlobalSortOrder() {
        int currentMaximum = toolRepository.findMaxSortOrder();
        if (currentMaximum == Integer.MAX_VALUE) {
            throw new ConflictException("Tool ordering capacity is exhausted");
        }
        return currentMaximum + 1;
    }

    private void updateExplicitSlug(Tool tool, String requestedSlug) {
        if (requestedSlug == null || requestedSlug.equals(tool.getSlug())) return;
        if (toolRepository.existsBySlug(requestedSlug)) {
            throw new ConflictException("A tool with this slug already exists");
        }
        tool.setSlug(requestedSlug);
    }

    private static String suffixed(String base, int suffix) {
        String suffixText = "-" + suffix;
        int maximumBaseLength = 160 - suffixText.length();
        return base.substring(0, Math.min(base.length(), maximumBaseLength)).replaceAll("-+$", "") + suffixText;
    }

    private Tool requireTool(long id) {
        return toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool", Long.toString(id)));
    }

    private static void requireState(Tool tool, ToolStatus allowed) {
        if (tool.getStatus() != allowed) {
            throw new ConflictException("Illegal tool state transition from " + tool.getStatus());
        }
    }

    public static ToolSummaryResponse summary(Tool tool) {
        return new ToolSummaryResponse(tool.getId(), tool.getSlug(), tool.getName(), tool.getSummary(), tool.getOfficialUrl(),
                mediaUrl(tool.getCoverMedia()), publicCategory(tool.getCategory()), publicTags(tool.getTags()), tool.isFeatured(),
                tool.getPublishedAt());
    }

    private static ToolDetailResponse detail(Tool tool) {
        return new ToolDetailResponse(tool.getId(), tool.getSlug(), tool.getName(), tool.getSummary(), tool.getOfficialUrl(),
                mediaUrl(tool.getCoverMedia()), publicCategory(tool.getCategory()), publicTags(tool.getTags()), tool.isFeatured(),
                tool.getPublishedAt(), tool.getRenderedHtml());
    }

    private static AdminToolResponse adminDetail(Tool tool) {
        return new AdminToolResponse(tool.getId(), tool.getSlug(), tool.getName(), tool.getSummary(),
                tool.getDescriptionMarkdown(), tool.getRenderedHtml(), tool.getOfficialUrl(), mediaUrl(tool.getCoverMedia()), mediaId(tool.getCoverMedia()),
                category(tool.getCategory()), tags(tool.getTags()), tool.getStatus(), tool.isFeatured(), tool.getSortOrder(),
                tool.getPublishedAt(), tool.getCreatedAt(), tool.getUpdatedAt());
    }

    private static AdminToolSummaryResponse adminSummary(Tool tool) {
        return new AdminToolSummaryResponse(tool.getId(), tool.getSlug(), tool.getName(), tool.getSummary(), tool.getOfficialUrl(),
                mediaUrl(tool.getCoverMedia()), category(tool.getCategory()), tags(tool.getTags()), tool.getStatus(),
                tool.isFeatured(), tool.getSortOrder(), tool.getPublishedAt(), tool.getCreatedAt(), tool.getUpdatedAt());
    }

    private static String mediaUrl(MediaAsset media) {
        return media == null ? null : "/api/media/" + media.getStorageKey();
    }

    private static Long mediaId(MediaAsset media) { return media == null ? null : media.getId(); }

    private static CategoryResponse category(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getSortOrder(), category.getScope());
    }

    private static List<TagResponse> tags(Set<Tag> tags) {
        if (tags == null) return List.of();
        return tags.stream().sorted(Comparator.comparing(Tag::getName).thenComparing(Tag::getId))
                .map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.getSlug())).toList();
    }

    private static PublicCategoryResponse publicCategory(Category category) {
        return category == null ? null : new PublicCategoryResponse(category.getId(), category.getName(), category.getSlug());
    }

    private static List<PublicTagResponse> publicTags(Set<Tag> tags) {
        if (tags == null) return List.of();
        return tags.stream().sorted(Comparator.comparing(Tag::getName).thenComparing(Tag::getId))
                .map(tag -> new PublicTagResponse(tag.getId(), tag.getName(), tag.getSlug())).toList();
    }

    private static NormalizedInput normalizeInput(ToolWriteRequest request) {
        if (request == null) throw new IllegalArgumentException("Tool request is required");
        String name = bounded(required(request.name(), "Name"), 100, "Name");
        String summary = bounded(required(request.summary(), "Summary"), 500, "Summary");
        String slug = normalizeSlug(request.slug());
        String officialUrl = officialUrl(request.officialUrl());
        if (request.descriptionMarkdown() == null || request.descriptionMarkdown().isBlank()) {
            throw new IllegalArgumentException("Description Markdown is required");
        }
        if (request.descriptionMarkdown().length() > 100000) {
            throw new IllegalArgumentException("Description Markdown is too long");
        }
        return new NormalizedInput(name, summary, slug, officialUrl);
    }

    private static String normalizeSlug(String raw) {
        String normalized = optional(raw);
        if (normalized == null) return null;
        normalized = UCharacter.foldCase(normalized, true);
        bounded(normalized, 160, "Slug");
        if (!SLUG.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Slug must be lowercase URL-safe text");
        }
        return normalized;
    }

    static String officialUrl(String raw) {
        return OfficialUrlPolicy.normalize(raw);
    }

    private static String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String bounded(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(field + " is too long after normalization");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record NormalizedInput(String name, String summary, String slug, String officialUrl) {
    }
}
