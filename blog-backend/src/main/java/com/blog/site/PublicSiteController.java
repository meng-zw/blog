package com.blog.site;

import com.blog.site.dto.SiteProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/site-profile")
public class PublicSiteController {
    private final SiteProfileService siteProfileService;

    public PublicSiteController(SiteProfileService siteProfileService) {
        this.siteProfileService = siteProfileService;
    }

    @GetMapping
    public SiteProfileResponse getProfile() {
        return siteProfileService.getProfile();
    }
}
