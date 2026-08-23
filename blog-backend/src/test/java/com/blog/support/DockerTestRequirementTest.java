package com.blog.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DockerTestRequirementTest {

    @Test
    void explicitSystemPropertyEnablesTheReleaseGate() {
        assertThat(DockerTestRequirement.isRequired(
                Map.of("blog.requireDockerTests", "true"), Map.of())).isTrue();
    }

    @Test
    void environmentCanEnableTheReleaseGateWhenThePropertyIsAbsent() {
        assertThat(DockerTestRequirement.isRequired(
                Map.of(), Map.of("BLOG_REQUIRE_DOCKER_TESTS", "TRUE"))).isTrue();
    }

    @Test
    void systemPropertyTakesPrecedenceOverTheEnvironment() {
        assertThat(DockerTestRequirement.isRequired(
                Map.of("blog.requireDockerTests", "false"),
                Map.of("BLOG_REQUIRE_DOCKER_TESTS", "true"))).isFalse();
    }

    @Test
    void rejectsTyposInsteadOfSilentlyDisablingTheReleaseGate() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                DockerTestRequirement.isRequired(
                        Map.of("blog.requireDockerTests", "yes"), Map.of()));
    }
}
