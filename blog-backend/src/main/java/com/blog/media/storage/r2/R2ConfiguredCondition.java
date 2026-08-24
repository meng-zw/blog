package com.blog.media.storage.r2;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Creates the readable R2 adapter whenever complete credentials are configured, independent of upload default. */
final class R2ConfiguredCondition implements Condition {
    private static final String[] REQUIRED = {
            "blog.media.r2.account-id", "blog.media.r2.access-key-id", "blog.media.r2.secret-access-key",
            "blog.media.r2.bucket", "blog.media.r2.public-base-url"
    };

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        for (String property : REQUIRED) {
            String value = context.getEnvironment().getProperty(property);
            if (value == null || value.isBlank()) return false;
        }
        return true;
    }
}
