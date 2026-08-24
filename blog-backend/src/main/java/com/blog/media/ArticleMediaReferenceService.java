package com.blog.media;

import com.blog.article.Article;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synchronizes stable Markdown image references and explicit article attachments. */
@Service
public class ArticleMediaReferenceService {
    private final ArticleMediaRepository articleMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final Clock clock;
    private final StableMediaReferenceParser stableMediaReferenceParser;

    public ArticleMediaReferenceService(ArticleMediaRepository articleMediaRepository,
                                        MediaAssetRepository mediaAssetRepository) {
        this(articleMediaRepository, mediaAssetRepository, Clock.systemUTC());
    }

    ArticleMediaReferenceService(ArticleMediaRepository articleMediaRepository,
                                 MediaAssetRepository mediaAssetRepository, Clock clock) {
        this.articleMediaRepository = articleMediaRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.clock = clock;
        this.stableMediaReferenceParser = new StableMediaReferenceParser();
    }

    public List<Long> extractInlineMediaIds(String markdown) {
        return stableMediaReferenceParser.parse(markdown);
    }

    public void synchronize(Article article, String markdown, List<Long> attachmentMediaIds) {
        if (article == null || article.getId() == null) {
            throw new IllegalArgumentException("Article must be saved before media references are synchronized");
        }
        List<Long> inlineMediaIds = extractInlineMediaIds(markdown);
        List<Long> attachments = normalizedAttachmentIds(attachmentMediaIds);
        lockForAssignment(null, markdown, attachments);
        List<MediaAsset> inline = inlineMediaIds.stream()
                .map(id -> requireReady(id, MediaPurpose.INLINE_IMAGE, "Inline media"))
                .toList();
        List<MediaAsset> attachmentAssets = attachments.stream()
                .map(id -> requireReady(id, MediaPurpose.ATTACHMENT, "Attachment media"))
                .toList();

        Instant createdAt = clock.instant();
        List<ArticleMedia> desired = new java.util.ArrayList<>(inline.size() + attachmentAssets.size());
        for (MediaAsset media : inline) {
            desired.add(new ArticleMedia(article, media, ArticleMediaRole.INLINE, null, null, createdAt));
        }
        for (int index = 0; index < attachmentAssets.size(); index++) {
            MediaAsset media = attachmentAssets.get(index);
            desired.add(new ArticleMedia(article, media, ArticleMediaRole.ATTACHMENT, displayName(media), index, createdAt));
        }

        Map<ArticleMediaId, ArticleMedia> existingById = new LinkedHashMap<>();
        for (ArticleMedia existing : articleMediaRepository.findByArticle_Id(article.getId())) {
            existingById.put(existing.getId(), existing);
        }
        Set<ArticleMediaId> desiredIds = desired.stream().map(ArticleMedia::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ArticleMedia> obsolete = existingById.values().stream()
                .filter(reference -> !desiredIds.contains(reference.getId())).toList();
        if (!obsolete.isEmpty()) articleMediaRepository.deleteAllInBatch(obsolete);

        List<ArticleMedia> additions = new java.util.ArrayList<>();
        for (ArticleMedia candidate : desired) {
            ArticleMedia retained = existingById.get(candidate.getId());
            if (retained == null) {
                additions.add(candidate);
            } else if (candidate.getId().getRole() == ArticleMediaRole.ATTACHMENT) {
                retained.updateAttachment(candidate.getDisplayName(), candidate.getSortOrder());
            }
        }
        if (!additions.isEmpty()) {
            articleMediaRepository.saveAll(additions);
        }
    }

    /** Locks all media selected by an article in ascending ID order before any reference is written. */
    public void lockForAssignment(Long coverMediaId, String markdown, List<Long> attachmentMediaIds) {
        List<Long> attachments = normalizedAttachmentIds(attachmentMediaIds);
        java.util.stream.Stream.concat(java.util.stream.Stream.concat(
                        coverMediaId == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(coverMediaId),
                        extractInlineMediaIds(markdown).stream()), attachments.stream())
                .distinct().sorted().forEach(id -> mediaAssetRepository.lockById(id).or(() -> mediaAssetRepository.findById(id))
                        .orElseThrow(() -> new ResourceNotFoundException("Media asset", Long.toString(id))));
    }

    public List<ArticleMedia> attachmentsFor(long articleId) {
        return articleMediaRepository.findByArticle_IdAndId_RoleOrderBySortOrderAsc(articleId, ArticleMediaRole.ATTACHMENT);
    }

    private MediaAsset requireReady(Long id, MediaPurpose expectedPurpose, String label) {
        MediaAsset asset = mediaAssetRepository.lockById(id).or(() -> mediaAssetRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", Long.toString(id)));
        if (asset.getStatus() != MediaStatus.READY) {
            throw new IllegalArgumentException(label + " must be READY");
        }
        if (asset.getPurpose() != expectedPurpose) {
            throw new IllegalArgumentException(label + " has an incompatible purpose");
        }
        return asset;
    }

    private static List<Long> normalizedAttachmentIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Attachment media IDs must be positive");
            }
            if (!unique.add(id)) {
                throw new IllegalArgumentException("Attachment media IDs must not contain duplicates");
            }
        }
        return List.copyOf(unique);
    }

    private static String displayName(MediaAsset media) {
        String originalFilename = media.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank()
                ? "attachment-" + media.getId()
                : originalFilename.strip();
    }
}
