package com.blog.site.dto;

public record SiteProfileResponse(String siteTitle, String subtitle, String nickname, String bio,
                                  String avatarUrl, String githubUrl) {
}
