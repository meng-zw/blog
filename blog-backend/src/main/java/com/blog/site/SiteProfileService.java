package com.blog.site;

import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.media.MediaApplicationService;
import com.blog.media.MediaPurpose;
import com.blog.media.MediaStatus;
import com.blog.shared.error.ResourceNotFoundException;
import com.blog.site.dto.SiteProfileResponse;
import com.blog.site.dto.AdminSiteProfileResponse;
import com.blog.site.dto.UpdateSiteProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SiteProfileService {
    private static final String DEFAULT_AVATAR_URL = "/images/xiao-m-mark.png";

    private final SiteProfileRepository siteProfileRepository;
    private final MediaAssetRepository mediaAssetRepository;

    public SiteProfileService(SiteProfileRepository siteProfileRepository, MediaAssetRepository mediaAssetRepository) {
        this.siteProfileRepository = siteProfileRepository;
        this.mediaAssetRepository = mediaAssetRepository;
    }

    public SiteProfileResponse getProfile() {
        return publicResponseFor(profile());
    }

    public AdminSiteProfileResponse getAdminProfile() {
        return adminResponseFor(profile());
    }

    @Transactional
    public AdminSiteProfileResponse update(UpdateSiteProfileRequest request) {
        SiteProfile profile = profile();
        profile.setSiteName(request.siteTitle());
        profile.setSubtitle(request.subtitle());
        profile.setOwnerName(request.nickname());
        profile.setSiteDescription(request.bio());
        profile.setGithubUrl(request.githubUrl());
        profile.setAvatarMedia(requireAvatar(request.avatarMediaId(), profile.getAvatarMedia()));
        return adminResponseFor(siteProfileRepository.save(profile));
    }

    private SiteProfile profile() {
        return siteProfileRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Site profile", "default"));
    }

    private MediaAsset requireAvatar(Long id, MediaAsset existing) {
        if (id == null) return null;
        MediaAsset media = mediaAssetRepository.lockById(id).or(() -> mediaAssetRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Avatar media asset", id.toString()));
        if (media.getContentType() == null || !media.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Avatar media must be an image");
        }
        if (media.getStatus() != MediaStatus.READY) throw new IllegalArgumentException("Avatar media must be ready");
        if (media.getPurpose() != MediaPurpose.AVATAR && (existing == null || !media.getId().equals(existing.getId()))) {
            throw new IllegalArgumentException("Avatar media purpose must be AVATAR");
        }
        return media;
    }

    private static SiteProfileResponse publicResponseFor(SiteProfile profile) {
        MediaAsset avatarMedia = profile.getAvatarMedia();
        String avatarUrl = avatarMedia == null ? DEFAULT_AVATAR_URL : MediaApplicationService.stableUrl(avatarMedia);
        return new SiteProfileResponse(profile.getSiteName(), profile.getSubtitle(), profile.getOwnerName(),
                profile.getSiteDescription(), avatarUrl, profile.getGithubUrl());
    }

    private static AdminSiteProfileResponse adminResponseFor(SiteProfile profile) {
        MediaAsset avatarMedia = profile.getAvatarMedia();
        String avatarUrl = avatarMedia == null ? DEFAULT_AVATAR_URL : MediaApplicationService.stableUrl(avatarMedia);
        return new AdminSiteProfileResponse(profile.getSiteName(), profile.getSubtitle(), profile.getOwnerName(),
                profile.getSiteDescription(), avatarMedia == null ? null : avatarMedia.getId(), avatarUrl,
                profile.getGithubUrl());
    }
}
