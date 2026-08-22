package com.blog.article;

import com.blog.article.dto.ArticleDetailResponse;
import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicArticleController.class, AdminArticleController.class})
@ContextConfiguration(classes = {PublicArticleController.class, AdminArticleController.class, SecurityConfig.class,
        AdminUserDetailsService.class, LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class ArticleSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ArticleService articleService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void publicPublishedDetailIsAnonymousAndReturnsOnlyTrustedRenderedHtml() throws Exception {
        when(articleService.findPublishedBySlug("safe-post")).thenReturn(new ArticleDetailResponse(
                1L, "safe-post", "Safe", "Summary", ContentType.ARTICLE, Instant.parse("2026-08-22T10:00:00Z"),
                null, null, List.of(), null, "<h2 id=\"safe\">Safe</h2>", "SEO", "Description", null, null));

        mockMvc.perform(get("/api/public/articles/safe-post").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendered_html").value("<h2 id=\"safe\">Safe</h2>"))
                .andExpect(jsonPath("$.markdown_content").doesNotExist());
    }

    @Test
    void anonymousAdminWriteReturnsProblemDetailsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/articles").contextPath("/api").with(csrf())
                        .contentType("application/json").content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWriteWithoutCsrfReturnsProblemDetailsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/articles").contextPath("/api")
                        .contentType("application/json").content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidArticleBoundsReturnProblemDetailsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/articles").contextPath("/api").with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"\",\"summary\":\"\",\"markdown_content\":\"\",\"content_type\":\"ARTICLE\",\"tag_ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blankOptionalSlugUsesServerAllocation() throws Exception {
        mockMvc.perform(post("/api/admin/articles").contextPath("/api").with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Title\",\"slug\":\"\",\"summary\":\"Summary\","
                                + "\"markdown_content\":\"Body\",\"content_type\":\"ARTICLE\",\"tag_ids\":[]}"))
                .andExpect(status().isCreated());
    }

    private static String validRequest() {
        return "{\"title\":\"Title\",\"summary\":\"Summary\",\"markdown_content\":\"Body\",\"content_type\":\"ARTICLE\",\"tag_ids\":[]}";
    }
}
