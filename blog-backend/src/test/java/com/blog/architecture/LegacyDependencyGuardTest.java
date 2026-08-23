package com.blog.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDependencyGuardTest {

    private static final Map<String, String> FORBIDDEN_SOURCE_MARKERS = Map.of(
            "legacy controller package", "com.blog.controller",
            "legacy entity package", "com.blog.entity",
            "legacy repository package", "com.blog.repository",
            "JJWT", "io.jsonwebtoken",
            "MyBatis-Plus", "com.baomidou.mybatisplus"
    );

    @Test
    void productionSourcesDoNotDependOnTheRemovedCommunityArchitecture() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                FORBIDDEN_SOURCE_MARKERS.forEach((label, marker) -> {
                    if (source.contains(marker)) {
                        violations.add(sourceRoot.relativize(path) + " -> " + label + " (" + marker + ")");
                    }
                });
            }
        }

        assertTrue(violations.isEmpty(), () -> "Legacy dependencies remain:\n" + String.join("\n", violations));
    }
}
