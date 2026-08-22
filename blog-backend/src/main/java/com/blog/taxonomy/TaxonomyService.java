package com.blog.taxonomy;

import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import com.blog.taxonomy.dto.CategoryRequest;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagRequest;
import com.blog.taxonomy.dto.TagResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class TaxonomyService {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG_CHARACTER = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public TaxonomyService(CategoryRepository categoryRepository, TagRepository tagRepository) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    public Category requireCategory(long id, CategoryScope scope) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", Long.toString(id)));
        if (category.getScope() != scope) {
            throw new IllegalArgumentException("Category does not support " + scope + " content");
        }
        return category;
    }

    public Set<Tag> requireTags(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Tag IDs must be positive");
        }
        List<Tag> found = tagRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException("Tag", "one or more requested IDs");
        }
        return new LinkedHashSet<>(found);
    }

    public List<CategoryResponse> listCategories(CategoryScope scope) {
        List<Category> categories = scope == null ? categoryRepository.findAllByOrderByScopeAscSortOrderAscNameAsc()
                : categoryRepository.findAllByScopeOrderBySortOrderAscNameAsc(scope);
        return categories.stream().map(TaxonomyService::categoryResponse).toList();
    }

    public List<TagResponse> listTags() {
        return tagRepository.findAllByOrderByNameAsc().stream().map(TaxonomyService::tagResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        apply(category, request, null);
        return categoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", Long.toString(id)));
        apply(category, request, id);
        return categoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category", Long.toString(id));
        }
        if (categoryRepository.countArticleReferences(id) > 0 || categoryRepository.countToolReferences(id) > 0) {
            throw new ConflictException("Category is referenced by existing content and cannot be deleted");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    public TagResponse createTag(TagRequest request) {
        Tag tag = new Tag();
        apply(tag, request, null);
        return tagResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse updateTag(long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", Long.toString(id)));
        apply(tag, request, id);
        return tagResponse(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(long id) {
        if (!tagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tag", Long.toString(id));
        }
        if (tagRepository.countArticleReferences(id) > 0 || tagRepository.countToolReferences(id) > 0) {
            throw new ConflictException("Tag is referenced by existing content and cannot be deleted");
        }
        tagRepository.deleteById(id);
    }

    private void apply(Category category, CategoryRequest request, Long currentId) {
        String name = normalizedName(request.name());
        String key = normalizedKey(name);
        rejectCategoryNameConflict(key, currentId);
        if (category.getScope() != null && category.getScope() != request.scope()
                && ((category.getScope() == CategoryScope.ARTICLE && categoryRepository.countArticleReferences(category.getId()) > 0)
                || (category.getScope() == CategoryScope.TOOL && categoryRepository.countToolReferences(category.getId()) > 0))) {
            throw new ConflictException("Referenced category scope cannot be changed");
        }
        category.setName(name);
        if (!key.equals(category.getNormalizedName())) category.setSlug(nextCategorySlug(slugBase(name, "category"), category.getSlug()));
        category.setNormalizedName(key);
        category.setDescription(normalizedOptionalText(request.description()));
        category.setSortOrder(request.sortOrder());
        category.setScope(request.scope());
    }

    private void apply(Tag tag, TagRequest request, Long currentId) {
        String name = normalizedName(request.name());
        String key = normalizedKey(name);
        rejectTagNameConflict(key, currentId);
        tag.setName(name);
        if (!key.equals(tag.getNormalizedName())) tag.setSlug(nextTagSlug(slugBase(name, "tag"), tag.getSlug()));
        tag.setNormalizedName(key);
    }

    private void rejectCategoryNameConflict(String name, Long currentId) {
        categoryRepository.findByNormalizedName(name).filter(found -> !found.getId().equals(currentId))
                .ifPresent(ignored -> { throw new ConflictException("A category with this name already exists"); });
    }

    private void rejectTagNameConflict(String name, Long currentId) {
        tagRepository.findByNormalizedName(name).filter(found -> !found.getId().equals(currentId))
                .ifPresent(ignored -> { throw new ConflictException("A tag with this name already exists"); });
    }

    private String nextCategorySlug(String base, String currentSlug) {
        return nextSlug(base, candidate -> categoryRepository.existsBySlug(candidate)
                && !candidate.equals(currentSlug));
    }

    private String nextTagSlug(String base, String currentSlug) {
        return nextSlug(base, candidate -> tagRepository.existsBySlug(candidate)
                && !candidate.equals(currentSlug));
    }

    private static String nextSlug(String base, java.util.function.Predicate<String> occupied) {
        for (int suffix = 1; ; suffix++) {
            String candidate = suffix == 1 ? base : truncateForSuffix(base, suffix);
            if (!occupied.test(candidate)) {
                return candidate;
            }
        }
    }

    private static String truncateForSuffix(String base, int suffix) {
        String suffixText = "-" + suffix;
        int maximumBaseLength = 160 - suffixText.length();
        return base.substring(0, Math.min(base.length(), maximumBaseLength)).replaceAll("-+$", "") + suffixText;
    }

    public static String normalizedName(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Name is required");
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        return normalized;
    }

    public static String normalizedKey(String input) { return normalizedName(input).toLowerCase(Locale.ROOT); }

    public static String slugBase(String input, String fallback) {
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD);
        String ascii = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String slug = NON_SLUG_CHARACTER.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? fallback : slug.substring(0, Math.min(slug.length(), 160));
    }

    private static String normalizedOptionalText(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
    }

    private static CategoryResponse categoryResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getDescription(),
                category.getSortOrder(), category.getScope());
    }

    private static TagResponse tagResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getSlug());
    }
}
