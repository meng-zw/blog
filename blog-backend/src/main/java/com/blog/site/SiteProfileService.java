package com.blog.site;

import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
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
        profile.setAvatarMedia(request.avatarMediaId() == null ? null : mediaAssetRepository.findById(request.avatarMediaId())
                .orElseThrow(() -> new ResourceNotFoundException("Avatar media asset", request.avatarMediaId().toString())));
        return adminResponseFor(siteProfileRepository.save(profile));
    }

    private SiteProfile profile() {
        return siteProfileRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Site profile", "default"));
    }

    private static SiteProfileResponse publicResponseFor(SiteProfile profile) {
        MediaAsset avatarMedia = profile.getAvatarMedia();
        String avatarUrl = avatarMedia == null ? DEFAULT_AVATAR_URL : "/api/media/" + avatarMedia.getStorageKey();
        return new SiteProfileResponse(profile.getSiteName(), profile.getSubtitle(), profile.getOwnerName(),
                profile.getSiteDescription(), avatarUrl, profile.getGithubUrl());
    }

    private static AdminSiteProfileResponse adminResponseFor(SiteProfile profile) {
        MediaAsset avatarMedia = profile.getAvatarMedia();
        String avatarUrl = avatarMedia == null ? DEFAULT_AVATAR_URL : "/api/media/" + avatarMedia.getStorageKey();
        return new AdminSiteProfileResponse(profile.getSiteName(), profile.getSubtitle(), profile.getOwnerName(),
                profile.getSiteDescription(), avatarMedia == null ? null : avatarMedia.getId(), avatarUrl,
                profile.getGithubUrl());
    }
}
