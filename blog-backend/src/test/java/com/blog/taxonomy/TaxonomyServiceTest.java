package com.blog.taxonomy;

import com.blog.shared.error.ConflictException;
import com.blog.taxonomy.dto.CategoryRequest;
import com.blog.taxonomy.dto.TagRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TagRepository tagRepository;
    @InjectMocks
    private TaxonomyService taxonomyService;

    @Test
    void createCategoryRejectsNfkcNormalizedCaseInsensitiveDuplicate() {
        Category existing = category(41L, "café", CategoryScope.ARTICLE);
        when(categoryRepository.findByNormalizedName("café")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> taxonomyService.createCategory(
                new CategoryRequest("  Cafe\u0301  ", "notes", 3, CategoryScope.ARTICLE)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createTagRejectsNfkcNormalizedCaseInsensitiveDuplicate() {
        Tag existing = tag(42L, "kotlin");
        when(tagRepository.findByNormalizedName("kotlin")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> taxonomyService.createTag(new TagRequest("  Kotlin  ")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createCategoryAppendsDeterministicSlugSuffixOnlyForCollision() {
        when(categoryRepository.findByNormalizedName("c")).thenReturn(Optional.empty());
        when(categoryRepository.existsBySlug("c")).thenReturn(true);
        when(categoryRepository.existsBySlug("c-2")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taxonomyService.createCategory(new CategoryRequest("C", null, 0, CategoryScope.ARTICLE));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getSlug()).isEqualTo("c-2");
    }

    @Test
    void requireCategoryRejectsCategoryFromDifferentScope() {
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category(7L, "Tools", CategoryScope.TOOL)));

        assertThatIllegalArgumentException().isThrownBy(() -> taxonomyService.requireCategory(7L, CategoryScope.ARTICLE));
    }

    @Test
    void deleteCategoryRejectsReferencedContentWithoutCascading() {
        when(categoryRepository.existsById(7L)).thenReturn(true);
        when(categoryRepository.countArticleReferences(7L)).thenReturn(1L);

        assertThatThrownBy(() -> taxonomyService.deleteCategory(7L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteTagRejectsReferencedContentWithoutCascading() {
        when(tagRepository.existsById(9L)).thenReturn(true);
        when(tagRepository.countArticleReferences(9L)).thenReturn(1L);

        assertThatThrownBy(() -> taxonomyService.deleteTag(9L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateCategoryRejectsChangingArticleCategoryWithArticleReferencesToToolScope() {
        Category category = category(7L, "Java", CategoryScope.ARTICLE);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNormalizedName("java")).thenReturn(Optional.of(category));
        when(categoryRepository.countArticleReferences(7L)).thenReturn(1L);

        assertThatThrownBy(() -> taxonomyService.updateCategory(7L,
                new CategoryRequest("Java", null, 0, CategoryScope.TOOL))).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateCategoryRejectsChangingToolCategoryWithToolReferencesToArticleScope() {
        Category category = category(8L, "Editors", CategoryScope.TOOL);
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNormalizedName("editors")).thenReturn(Optional.of(category));
        when(categoryRepository.countToolReferences(8L)).thenReturn(1L);

        assertThatThrownBy(() -> taxonomyService.updateCategory(8L,
                new CategoryRequest("Editors", null, 0, CategoryScope.ARTICLE))).isInstanceOf(ConflictException.class);
    }

    private static Category category(Long id, String name, CategoryScope scope) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setScope(scope);
        category.setSlug(name.toLowerCase());
        return category;
    }

    private static Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSlug(name.toLowerCase());
        return tag;
    }
}
