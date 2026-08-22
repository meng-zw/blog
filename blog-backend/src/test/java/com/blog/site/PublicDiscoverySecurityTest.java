package com.blog.site;

import com.blog.article.ContentType;
import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.search.PublicSearchController;
import com.blog.search.SearchRepository;
import com.blog.search.SearchService;
import com.blog.search.dto.SearchResultResponse;
import com.blog.search.dto.SearchResultType;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import com.blog.site.dto.HomeResponse;
import com.blog.site.dto.SiteProfileResponse;
import com.blog.tool.dto.ToolSummaryResponse;
import com.blog.topic.dto.PublicTopicSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicHomeController.class, PublicSearchController.class, SitemapController.class})
@ContextConfiguration(classes = {PublicHomeController.class, PublicSearchController.class, SitemapController.class,
        SearchService.class, SecurityConfig.class, AdminUserDetailsService.class, LoginAttemptService.class,
        TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class PublicDiscoverySecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean HomeQueryService homeQueryService;
    @MockitoBean SearchRepository searchRepository;
    @MockitoBean SitemapService sitemapService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void homeIsAnonymousAndContainsOnlyPublicSummaryFields() throws Exception {
        Instant published = Instant.parse("2026-08-22T10:00:00Z");
        ArticleSummaryResponse article = new ArticleSummaryResponse(1L, "article", "Article", "Summary",
                ContentType.ARTICLE, published, null, null, List.of());
        ToolSummaryResponse tool = new ToolSummaryResponse(2L, "tool", "Tool", "Summary", "https://example.com",
                null, null, List.of(), true, published);
        when(homeQueryService.getHome()).thenReturn(new HomeResponse(
                new SiteProfileResponse("Site", "Subtitle", "Owner", "Bio", null, null), article,
                List.of(), List.of(tool), List.of(new PublicTopicSummaryResponse(3L, "Topic", "topic", "Description", null))));

        mockMvc.perform(get("/api/public/home").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured_article.slug").value("article"))
                .andExpect(jsonPath("$.featured_article.status").doesNotExist())
                .andExpect(jsonPath("$.featured_article.markdown_content").doesNotExist())
                .andExpect(jsonPath("$.featured_tools[0].sort_order").doesNotExist())
                .andExpect(jsonPath("$.featured_tools[0].description_markdown").doesNotExist())
                .andExpect(jsonPath("$.topics[0].status").doesNotExist())
                .andExpect(jsonPath("$.topics[0].sort_order").doesNotExist());
    }

    @Test
    void searchIsAnonymousClampsSizeAndReturnsAStableDiscriminator() throws Exception {
        Instant now = Instant.now();
        when(searchRepository.search(eq("query"), eq(0), eq(50), any(Instant.class)))
                .thenReturn(new SearchRepository.SearchPage(List.of(new SearchResultResponse(
                        SearchResultType.NOTE, 9L, "note", "Note", "Summary", now)), 1));

        mockMvc.perform(get("/api/public/search").contextPath("/api")
                        .param("q", "query").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NOTE"))
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].rendered_html").doesNotExist());
    }

    @Test
    void invalidSearchInputsReturnTraceableProblemDetails() throws Exception {
        mockMvc.perform(get("/api/public/search").contextPath("/api"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mockMvc.perform(get("/api/public/search").contextPath("/api").param("q", "　 "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mockMvc.perform(get("/api/public/search").contextPath("/api")
                        .param("q", "query").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void sitemapIsAnonymousXmlUtf8AndCannotBeInfluencedByTheHostHeader() throws Exception {
        when(sitemapService.generate()).thenReturn("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<urlset><url><loc>https://example.com/articles/safe</loc></url></urlset>");

        mockMvc.perform(get("/api/sitemap.xml").contextPath("/api").header("Host", "evil.example"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com/articles/safe")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("evil.example"))));
    }
}
