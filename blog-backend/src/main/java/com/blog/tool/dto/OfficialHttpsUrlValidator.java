package com.blog.tool.dto;

import com.blog.tool.OfficialUrlPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OfficialHttpsUrlValidator implements ConstraintValidator<OfficialHttpsUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true;
        try {
            OfficialUrlPolicy.normalize(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
