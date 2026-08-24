package com.blog.shared.error;

import com.blog.shared.web.TraceIdFilter;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void returnsProblemDetailsAndTraceIdForMissingResource() throws Exception {
        String traceId = "valid_Trace-Id1";

        mockMvc.perform(get("/test/not-found").header("X-Trace-Id", traceId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.detail").value("article not found: missing"))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    void returnsFieldErrorsForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void returnsServiceUnavailableForRetryableStorageFailures() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("媒体存储暂时不可用，请稍后重试"));
    }

    @Test
    void returnsNotFoundForMissingProviderObjectsOutsideCompletionWorkflow() throws Exception {
        mockMvc.perform(get("/test/missing-object"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("媒体文件不存在"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private/object-key"))));
    }

    @Test
    void returnsSanitizedServiceUnavailableForTransientProviderFailures() throws Exception {
        mockMvc.perform(get("/test/storage-transient"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("媒体存储暂时不可用，请稍后重试"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private/object-key"))));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ResourceNotFoundException("article", "missing");
        }

        @GetMapping("/test/unavailable")
        void unavailable() {
            throw new ServiceUnavailableException("媒体存储暂时不可用，请稍后重试");
        }

        @GetMapping("/test/missing-object")
        void missingObject() {
            throw com.blog.media.storage.ObjectStorageException.notFound(
                    "Media object not found: private/object-key.png", null);
        }

        @GetMapping("/test/storage-transient")
        void transientStorageFailure() {
            throw com.blog.media.storage.ObjectStorageException.transientFailure(
                    "Unable to access R2 object: private/object-key.png", null);
        }

        @PostMapping("/test/validate")
        void validate(@jakarta.validation.Valid @RequestBody NameRequest request) {
        }
    }

    record NameRequest(@NotBlank String name) {
    }
}
