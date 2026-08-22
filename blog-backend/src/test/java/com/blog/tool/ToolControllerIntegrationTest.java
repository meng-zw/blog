package com.blog.tool;

import com.blog.article.dto.PublicCategoryResponse;
import com.blog.article.dto.PublicTagResponse;
import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.PageResponse;
import com.blog.shared.web.TraceIdFilter;
import com.blog.tool.dto.ToolDetailResponse;
import com.blog.tool.dto.ToolSummaryResponse;
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

@WebMvcTest(controllers = {PublicToolController.class, AdminToolController.class})
@ContextConfiguration(classes = {PublicToolController.class, AdminToolController.class, SecurityConfig.class,
        AdminUserDetailsService.class, LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class ToolControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ToolService toolService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void publicToolsAreAnonymousAndDoNotExposeWorkflowOrOrderingInternals() throws Exception {
        ToolSummaryResponse summary = new ToolSummaryResponse(1L, "safe-tool", "Safe", "Summary", "https://openai.com",
                "/api/media/tool.png", new PublicCategoryResponse(2L, "Tools", "tools"),
                List.of(new PublicTagResponse(3L, "Java", "java")), true, Instant.parse("2026-08-22T10:00:00Z"));
        when(toolService.listPublic(0, 20, null, null, null))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));
        when(toolService.findPublishedBySlug("safe-tool")).thenReturn(new ToolDetailResponse(1L, "safe-tool", "Safe",
                "Summary", "https://openai.com", "/api/media/tool.png", new PublicCategoryResponse(2L, "Tools", "tools"),
                List.of(new PublicTagResponse(3L, "Java", "java")), true, Instant.parse("2026-08-22T10:00:00Z"),
                "<h2 id=\"safe\">Safe</h2>"));

        mockMvc.perform(get("/api/public/tools").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].sort_order").doesNotExist())
                .andExpect(jsonPath("$.items[0].description_markdown").doesNotExist())
                .andExpect(jsonPath("$.items[0].category.scope").doesNotExist());
        mockMvc.perform(get("/api/public/tools/safe-tool").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendered_html").value("<h2 id=\"safe\">Safe</h2>"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.description_markdown").doesNotExist())
                .andExpect(jsonPath("$.sort_order").doesNotExist());
    }

    @Test
    void anonymousAdminWritesReturnProblemDetailsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/tools").contextPath("/api").with(csrf())
                        .contentType("application/json").content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWriteWithoutCsrfReturnsProblemDetailsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/tools").contextPath("/api")
                        .contentType("application/json").content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidOfficialUrlReturnsProblemDetailsBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/admin/tools").contextPath("/api").with(csrf())
                        .contentType("application/json")
                        .content(validRequest().replace("https://example.com", "http://example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.officialUrl").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedReorderPayloadReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/admin/tools/reorder").contextPath("/api").with(csrf())
                        .contentType("application/json").content("{\"tool_ids\":[1,null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private static String validRequest() {
        return "{\"name\":\"Tool\",\"summary\":\"Summary\",\"description_markdown\":\"# Safe\","
                + "\"official_url\":\"https://example.com\",\"tag_ids\":[],\"featured\":false}";
    }
}
