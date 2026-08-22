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

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ResourceNotFoundException("article", "missing");
        }

        @PostMapping("/test/validate")
        void validate(@jakarta.validation.Valid @RequestBody NameRequest request) {
        }
    }

    record NameRequest(@NotBlank String name) {
    }
}
