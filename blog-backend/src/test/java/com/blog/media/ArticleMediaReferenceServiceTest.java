package com.blog.media;

import com.blog.article.Article;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.jpa.repository.EntityGraph;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleMediaReferenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    @Mock ArticleMediaRepository articleMediaRepository;
    @Mock MediaAssetRepository mediaAssetRepository;

    @Test
    void extractsOnlyStableMediaImageUrlsAndIgnoresFencedCodeExamples() {
        ArticleMediaReferenceService service = service();

        assertThat(service.extractInlineMediaIds("""
                ![first](/api/media/assets/12)
                ![duplicate](/api/media/assets/12)
                [ordinary link](/api/media/assets/13)
                ```markdown
                ![example](/api/media/assets/14)
                ```
                ![zero](/api/media/assets/0)
                ![external](https://example.test/api/media/assets/15)
                ![second](/api/media/assets/16)
                """))
                .containsExactly(12L, 16L);
    }

    @Test
    void synchronizesReadyInlineAndAttachmentReferencesInRequestOrder() {
        MediaAsset inline = readyMedia(12L, MediaPurpose.INLINE_IMAGE, "diagram.png");
        MediaAsset firstAttachment = readyMedia(21L, MediaPurpose.ATTACHMENT, "guide.pdf");
        MediaAsset secondAttachment = readyMedia(22L, MediaPurpose.ATTACHMENT, "sheet.xlsx");
        when(mediaAssetRepository.findById(12L)).thenReturn(Optional.of(inline));
        when(mediaAssetRepository.findById(21L)).thenReturn(Optional.of(firstAttachment));
        when(mediaAssetRepository.findById(22L)).thenReturn(Optional.of(secondAttachment));

        service().synchronize(article(9L), "![diagram](/api/media/assets/12)", List.of(21L, 22L));

        verify(articleMediaRepository).deleteByArticle_Id(9L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArticleMedia>> references = ArgumentCaptor.forClass(List.class);
        verify(articleMediaRepository).saveAll(references.capture());
        assertThat(references.getValue()).extracting(reference -> reference.getId().getRole())
                .containsExactly(ArticleMediaRole.INLINE, ArticleMediaRole.ATTACHMENT, ArticleMediaRole.ATTACHMENT);
        assertThat(references.getValue()).extracting(ArticleMedia::getSortOrder).containsExactly(null, 0, 1);
        assertThat(references.getValue()).extracting(ArticleMedia::getDisplayName)
                .containsExactly(null, "guide.pdf", "sheet.xlsx");
    }

    @Test
    void rejectsNonReadyOrPurposeIncompatibleReferencesBeforeReplacingExistingRows() {
        MediaAsset pending = readyMedia(12L, MediaPurpose.INLINE_IMAGE, "diagram.png");
        pending.setStatus(MediaStatus.PENDING_UPLOAD);
        when(mediaAssetRepository.findById(12L)).thenReturn(Optional.of(pending));

        assertThatIllegalArgumentException().isThrownBy(() -> service()
                .synchronize(article(9L), "![diagram](/api/media/assets/12)", List.of()));

        verify(articleMediaRepository, org.mockito.Mockito.never()).deleteByArticle_Id(any());
    }

    @Test
    void usesAStableFallbackNameAndRejectsDuplicateAttachmentIds() {
        MediaAsset attachment = readyMedia(21L, MediaPurpose.ATTACHMENT, "  ");
        when(mediaAssetRepository.findById(21L)).thenReturn(Optional.of(attachment));

        service().synchronize(article(9L), "body", List.of(21L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ArticleMedia>> references = ArgumentCaptor.forClass(List.class);
        verify(articleMediaRepository).saveAll(references.capture());
        assertThat(references.getValue().getFirst().getDisplayName()).isEqualTo("attachment-21");

        assertThatIllegalArgumentException().isThrownBy(() -> service()
                .synchronize(article(9L), "body", List.of(21L, 21L)));
    }

    @Test
    void removesAllExistingRowsWhenARevisionNoLongerReferencesMedia() {
        service().synchronize(article(9L), "body", List.of());

        verify(articleMediaRepository).deleteByArticle_Id(9L);
        verify(articleMediaRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void attachmentLookupFetchesMediaWithTheReferenceRowsToAvoidDetailNPlusOneQueries() throws NoSuchMethodException {
        var query = ArticleMediaRepository.class.getMethod("findByArticle_IdAndId_RoleOrderBySortOrderAsc",
                Long.class, ArticleMediaRole.class);

        EntityGraph entityGraph = query.getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).containsExactly("media");
    }

    private ArticleMediaReferenceService service() {
        return new ArticleMediaReferenceService(articleMediaRepository, mediaAssetRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Article article(long id) {
        Article article = new Article();
        article.setId(id);
        return article;
    }

    private static MediaAsset readyMedia(long id, MediaPurpose purpose, String originalFilename) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setPurpose(purpose);
        asset.setStatus(MediaStatus.READY);
        asset.setOriginalFilename(originalFilename);
        asset.setContentType(purpose == MediaPurpose.ATTACHMENT ? "application/pdf" : "image/png");
        asset.setByteSize(42L);
        return asset;
    }
}
