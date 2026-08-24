package com.blog.media;

import com.blog.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolMediaReferenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    @Mock ToolMediaRepository toolMediaRepository;
    @Mock MediaAssetRepository mediaAssetRepository;

    @Test
    void createsRowsInMarkdownOrderAfterLockingReadyImagesInAscendingIdOrder() {
        MediaAsset first = inlineImage(12L);
        MediaAsset second = inlineImage(7L);
        when(mediaAssetRepository.lockById(7L)).thenReturn(Optional.of(second));
        when(mediaAssetRepository.lockById(12L)).thenReturn(Optional.of(first));

        service().synchronize(tool(4L), "![first](/api/media/assets/12) ![second](/api/media/assets/7)");

        InOrder locks = inOrder(mediaAssetRepository);
        locks.verify(mediaAssetRepository).lockById(7L);
        locks.verify(mediaAssetRepository).lockById(12L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ToolMedia>> rows = ArgumentCaptor.forClass(List.class);
        verify(toolMediaRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).extracting(row -> row.getMedia().getId(), ToolMedia::getSortOrder)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(12L, 0),
                        org.assertj.core.groups.Tuple.tuple(7L, 1));
    }

    @Test
    void retainsCompositeIdsWhileReorderingAndRemovingOnlyObsoleteRows() {
        Tool tool = tool(4L);
        MediaAsset first = inlineImage(7L);
        MediaAsset second = inlineImage(12L);
        MediaAsset removed = inlineImage(13L);
        ToolMedia retainedFirst = new ToolMedia(tool, first, 0, NOW.minusSeconds(60));
        ToolMedia retainedSecond = new ToolMedia(tool, second, 1, NOW.minusSeconds(60));
        ToolMedia obsolete = new ToolMedia(tool, removed, 2, NOW.minusSeconds(60));
        when(mediaAssetRepository.lockById(7L)).thenReturn(Optional.of(first));
        when(mediaAssetRepository.lockById(12L)).thenReturn(Optional.of(second));
        when(toolMediaRepository.findByTool_Id(4L)).thenReturn(List.of(retainedFirst, retainedSecond, obsolete));

        service().synchronize(tool, "![second](/api/media/assets/12) ![first](/api/media/assets/7)");

        verify(toolMediaRepository).deleteAllInBatch(List.of(obsolete));
        verify(toolMediaRepository, never()).saveAll(any());
        assertThat(retainedSecond.getSortOrder()).isZero();
        assertThat(retainedFirst.getSortOrder()).isEqualTo(1);
        assertThat(retainedFirst.getCreatedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void failedValidationLeavesExistingRowsUntouchedBeforeTheTransactionCanCommit() {
        Tool tool = tool(4L);
        MediaAsset retainedMedia = inlineImage(8L);
        ToolMedia retained = new ToolMedia(tool, retainedMedia, 0, NOW.minusSeconds(60));
        MediaAsset invalid = inlineImage(7L);
        invalid.setStatus(MediaStatus.DELETING);
        when(mediaAssetRepository.lockById(7L)).thenReturn(Optional.of(invalid));

        assertThatIllegalArgumentException().isThrownBy(() -> service()
                .synchronize(tool, "![image](/api/media/assets/7)"));

        verify(toolMediaRepository, never()).deleteAllInBatch(any());
        verify(toolMediaRepository, never()).saveAll(any());
        assertThat(retained.getMedia().getId()).isEqualTo(8L);
        assertThat(retained.getSortOrder()).isZero();
    }

    @Test
    void rejectsNonInlineImagePurposeBeforeChangingExistingRows() {
        MediaAsset invalid = inlineImage(7L);
        invalid.setPurpose(MediaPurpose.ATTACHMENT);
        when(mediaAssetRepository.lockById(7L)).thenReturn(Optional.of(invalid));

        assertThatIllegalArgumentException().isThrownBy(() -> service()
                .synchronize(tool(4L), "![image](/api/media/assets/7)"));

        verify(toolMediaRepository, never()).deleteAllInBatch(any());
        verify(toolMediaRepository, never()).saveAll(any());
    }

    @Test
    void removesAllRowsBeforeDeletingTheTool() {
        Tool tool = tool(4L);

        service().removeAll(tool);

        verify(toolMediaRepository).deleteByTool_Id(4L);
    }

    private ToolMediaReferenceService service() {
        return new ToolMediaReferenceService(toolMediaRepository, mediaAssetRepository,
                new StableMediaReferenceParser(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Tool tool(long id) {
        Tool tool = new Tool();
        tool.setId(id);
        return tool;
    }

    private static MediaAsset inlineImage(long id) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setStatus(MediaStatus.READY);
        asset.setContentType("image/png");
        asset.setByteSize(42L);
        return asset;
    }
}
