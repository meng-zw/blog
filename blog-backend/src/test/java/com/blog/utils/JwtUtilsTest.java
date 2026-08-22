package com.blog.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtUtilsTest {
    @Test
    void refusesToIssueTokensWithoutAnExplicitSigningSecret() {
        var jwtUtils = new JwtUtils();

        assertThatThrownBy(() -> jwtUtils.generateToken("admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT signing secret must be configured");
    }
}
