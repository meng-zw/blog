package com.blog.topic;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.PageResponse;
import com.blog.shared.web.TraceIdFilter;
import com.blog.topic.dto.PublicTopicDetailResponse;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicTopicController.class)
@ContextConfiguration(classes = {PublicTopicController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class PublicTopicControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean TopicService topicService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void listReturnsPagedPublicMetadataWithoutAdministrativeFields() throws Exception {
        when(topicService.listPublished(1, 2)).thenReturn(new PageResponse<>(List.of(
                new PublicTopicSummaryResponse(7L, "Java", "java", "JVM", "/api/media/java.png")),
                1, 2, 3, 2));

        mockMvc.perform(get("/api/public/topics").contextPath("/api").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("java"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].sortOrder").doesNotExist());
    }

    @Test
    void detailReturnsPublicMetadataAndOrderedArticlesWithoutAdministrativeFields() throws Exception {
        when(topicService.findPublishedDetailBySlug("java")).thenReturn(new PublicTopicDetailResponse(
                7L, "Java", "java", "JVM", null, List.of()));

        mockMvc.perform(get("/api/public/topics/java").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("java"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").doesNotExist())
                .andExpect(jsonPath("$.articles").isArray());
    }

    @Test
    void invalidPaginationReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/public/topics").contextPath("/api").param("page", "-1").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
