package com.blog.site;

import com.blog.site.dto.SiteProfileResponse;
import com.blog.site.dto.UpdateSiteProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings")
public class AdminSiteController {
    private final SiteProfileService siteProfileService;

    public AdminSiteController(SiteProfileService siteProfileService) {
        this.siteProfileService = siteProfileService;
    }

    @GetMapping
    public SiteProfileResponse getProfile() {
        return siteProfileService.getProfile();
    }

    @PutMapping
    public SiteProfileResponse updateProfile(@Valid @RequestBody UpdateSiteProfileRequest request) {
        return siteProfileService.update(request);
    }
}
