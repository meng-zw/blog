package com.blog.support;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DockerReleasePreflightTest {

    @Test
    void releaseBuildCannotPassWhenDockerIsRequiredButUnavailable() {
        assumeTrue(DockerTestRequirement.isRequired(),
                "Local run: enable the release gate with -Dblog.requireDockerTests=true");

        assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker-backed release tests were required, but no Docker daemon is available");
    }
}
