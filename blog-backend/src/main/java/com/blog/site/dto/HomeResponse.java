package com.blog.site.dto;

import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.tool.dto.ToolSummaryResponse;
import com.blog.topic.dto.PublicTopicSummaryResponse;

import java.util.List;

public record HomeResponse(SiteProfileResponse site, ArticleSummaryResponse featuredArticle,
                           List<ArticleSummaryResponse> latestArticles, List<ToolSummaryResponse> featuredTools,
                           List<PublicTopicSummaryResponse> topics) {
}
