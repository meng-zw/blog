package com.blog.site.dto;

public record AdminSiteProfileResponse(String siteTitle, String subtitle, String nickname, String bio,
                                       Long avatarMediaId, String avatarUrl, String githubUrl) {
}
