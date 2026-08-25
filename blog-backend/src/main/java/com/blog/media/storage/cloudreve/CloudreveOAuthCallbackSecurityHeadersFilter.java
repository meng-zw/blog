package com.blog.media.storage.cloudreve;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Applies callback-specific leak-prevention headers before authentication can reject the request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CloudreveOAuthCallbackSecurityHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isCallback(request)) {
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Cache-Control", "no-store");
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isCallback(HttpServletRequest request) {
        return (request.getContextPath() + CloudreveOAuthCallbackController.CALLBACK_PATH)
                .equals(request.getRequestURI());
    }
}
