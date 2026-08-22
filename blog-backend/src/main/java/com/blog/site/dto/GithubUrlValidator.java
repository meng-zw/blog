package com.blog.site.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class GithubUrlValidator implements ConstraintValidator<GithubUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                return false;
            }
            String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
            return host.equals("github.com") || host.endsWith(".github.com");
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return false;
        }
    }
}
