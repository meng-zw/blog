package com.blog.media;

import com.blog.shared.error.ResourceNotFoundException;
import com.blog.tool.Tool;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synchronizes stable Markdown image references owned by a tool. */
@Service
public class ToolMediaReferenceService {
    private final ToolMediaRepository toolMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final StableMediaReferenceParser stableMediaReferenceParser;
    private final Clock clock;

    public ToolMediaReferenceService(ToolMediaRepository toolMediaRepository,
                                     MediaAssetRepository mediaAssetRepository) {
        this(toolMediaRepository, mediaAssetRepository, new StableMediaReferenceParser(), Clock.systemUTC());
    }

    ToolMediaReferenceService(ToolMediaRepository toolMediaRepository,
                              MediaAssetRepository mediaAssetRepository,
                              StableMediaReferenceParser stableMediaReferenceParser, Clock clock) {
        this.toolMediaRepository = toolMediaRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.stableMediaReferenceParser = stableMediaReferenceParser;
        this.clock = clock;
    }

    public void synchronize(Tool tool, String markdown) {
        if (tool == null || tool.getId() == null) {
            throw new IllegalArgumentException("Tool must be saved before media references are synchronized");
        }
        List<Long> desiredMediaIds = stableMediaReferenceParser.parse(markdown);
        Map<Long, MediaAsset> readyMediaById = lockAndValidate(desiredMediaIds);

        Map<ToolMediaId, ToolMedia> existingById = new LinkedHashMap<>();
        for (ToolMedia existing : toolMediaRepository.findByTool_Id(tool.getId())) {
            existingById.put(existing.getId(), existing);
        }
        Set<ToolMediaId> desiredIds = desiredMediaIds.stream().map(mediaId -> new ToolMediaId(tool.getId(), mediaId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ToolMedia> obsolete = existingById.values().stream()
                .filter(reference -> !desiredIds.contains(reference.getId())).toList();
        if (!obsolete.isEmpty()) {
            toolMediaRepository.deleteAllInBatch(obsolete);
        }

        Instant createdAt = clock.instant();
        List<ToolMedia> additions = new java.util.ArrayList<>();
        for (int sortOrder = 0; sortOrder < desiredMediaIds.size(); sortOrder++) {
            Long mediaId = desiredMediaIds.get(sortOrder);
            ToolMediaId id = new ToolMediaId(tool.getId(), mediaId);
            ToolMedia retained = existingById.get(id);
            if (retained == null) {
                additions.add(new ToolMedia(tool, readyMediaById.get(mediaId), sortOrder, createdAt));
            } else {
                retained.updateSortOrder(sortOrder);
            }
        }
        if (!additions.isEmpty()) {
            toolMediaRepository.saveAll(additions);
        }
    }

    public void removeAll(Tool tool) {
        if (tool == null || tool.getId() == null) {
            throw new IllegalArgumentException("Tool must be saved before media references are removed");
        }
        toolMediaRepository.deleteByTool_Id(tool.getId());
    }

    private Map<Long, MediaAsset> lockAndValidate(List<Long> mediaIds) {
        Map<Long, MediaAsset> mediaById = new LinkedHashMap<>();
        for (Long mediaId : mediaIds.stream().sorted().toList()) {
            MediaAsset media = mediaAssetRepository.lockById(mediaId).or(() -> mediaAssetRepository.findById(mediaId))
                    .orElseThrow(() -> new ResourceNotFoundException("Media asset", Long.toString(mediaId)));
            if (media.getStatus() != MediaStatus.READY) {
                throw new IllegalArgumentException("Inline media must be READY");
            }
            if (media.getPurpose() != MediaPurpose.INLINE_IMAGE) {
                throw new IllegalArgumentException("Inline media has an incompatible purpose");
            }
            mediaById.put(mediaId, media);
        }
        return mediaById;
    }
}
