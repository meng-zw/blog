package com.blog.support;

import java.util.Map;

final class DockerTestRequirement {

    static final String SYSTEM_PROPERTY = "blog.requireDockerTests";
    static final String ENVIRONMENT_VARIABLE = "BLOG_REQUIRE_DOCKER_TESTS";

    private DockerTestRequirement() {
    }

    static boolean isRequired() {
        String property = System.getProperty(SYSTEM_PROPERTY);
        String environment = System.getenv(ENVIRONMENT_VARIABLE);
        return parse(property != null ? property : environment);
    }

    static boolean isRequired(Map<String, String> properties, Map<String, String> environment) {
        String property = properties.get(SYSTEM_PROPERTY);
        String value = property != null ? property : environment.get(ENVIRONMENT_VARIABLE);
        return parse(value);
    }

    private static boolean parse(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("false")) {
            return false;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        throw new IllegalArgumentException(
                SYSTEM_PROPERTY + " / " + ENVIRONMENT_VARIABLE + " must be true or false, but was: " + value);
    }
}
