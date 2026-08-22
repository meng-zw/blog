package com.blog.shared.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void runsAtHighestServletPrecedenceBeforeSecurityFilters() {
        Order order = TraceIdFilter.class.getAnnotation(Order.class);

        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
    }

    @Test
    void replacesTooShortTraceIdWithHyphenlessUuidAndClearsMdcAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "short");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                traceIdInChain.set(MDC.get("traceId")));

        String traceId = response.getHeader("X-Trace-Id");
        assertTrue(traceId.matches("[0-9a-fA-F]{32}"));
        assertEquals(traceId, traceIdInChain.get());
        assertNull(MDC.get("traceId"));
    }

    @Test
    void clearsMdcWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> {
                    assertTrue(MDC.get("traceId").matches("[0-9a-fA-F]{32}"));
                    throw new ServletException("boom");
                }));

        assertNull(MDC.get("traceId"));
    }
}
