package com.blog.taxonomy;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TaxonomyController.class)
@ContextConfiguration(classes = {TaxonomyController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class TaxonomyControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TaxonomyService taxonomyService;
    @MockitoBean
    private AdminAccountRepository adminAccountRepository;

    @Test
    void publicCategoriesAreVisibleWithoutAnAdminSession() throws Exception {
        when(taxonomyService.listCategories(CategoryScope.ARTICLE)).thenReturn(List.of(
                new CategoryResponse(1L, "Java", "java", "JVM", 0, CategoryScope.ARTICLE)));

        mockMvc.perform(get("/api/public/taxonomy/categories").contextPath("/api").param("scope", "ARTICLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("java"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCategoriesUseTheBoundedPageContract() throws Exception {
        var item = new CategoryResponse(1L, "Java", "java", "JVM", 0, CategoryScope.ARTICLE);
        when(taxonomyService.pageCategories(CategoryScope.ARTICLE, 1, 20))
                .thenReturn(new PageResponse<>(List.of(item), 1, 20, 21, 2));

        mockMvc.perform(get("/api/admin/taxonomy/categories").contextPath("/api")
                        .param("scope", "ARTICLE").param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].slug").value("java"))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.total").value(21));
    }

    @Test
    void anonymousTaxonomyWriteReturnsRfc9457UnauthorizedProblem() throws Exception {
        mockMvc.perform(post("/api/admin/taxonomy/categories").contextPath("/api")
                        .with(csrf()).contentType("application/json")
                        .content("{\"name\":\"Java\",\"scope\":\"ARTICLE\",\"sort_order\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void taxonomyWriteWithoutCsrfReturnsRfc9457ForbiddenProblem() throws Exception {
        mockMvc.perform(post("/api/admin/taxonomy/categories").contextPath("/api")
                        .contentType("application/json")
                        .content("{\"name\":\"Java\",\"scope\":\"ARTICLE\",\"sort_order\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void invalidScopeQueryReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/public/taxonomy/categories").contextPath("/api").param("scope", "INVALID"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonNumericAdminPathReturnsProblemDetails() throws Exception {
        mockMvc.perform(put("/api/admin/taxonomy/categories/nope").contextPath("/api").with(csrf())
                        .contentType("application/json").content("{\"name\":\"Java\",\"scope\":\"ARTICLE\",\"sort_order\":0}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
