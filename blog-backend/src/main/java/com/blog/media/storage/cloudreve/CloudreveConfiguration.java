package com.blog.media.storage.cloudreve;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Registers Cloudreve configuration without coupling its read capability to the default upload provider. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CloudreveProperties.class)
public class CloudreveConfiguration {

    @Bean
    @Conditional(CloudreveRequiredConfigurationCondition.class)
    CloudreveStartupValidator cloudreveStartupValidator(CloudreveProperties properties) {
        properties.validate();
        return new CloudreveStartupValidator();
    }

    static final class CloudreveStartupValidator {
    }

    static final class CloudreveRequiredConfigurationCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = context.getEnvironment().getProperty("blog.media.provider");
            return Boolean.parseBoolean(context.getEnvironment().getProperty("blog.media.cloudreve.enabled", "false"))
                    || "cloudreve".equalsIgnoreCase(provider == null ? "" : provider.trim());
        }
    }
}
