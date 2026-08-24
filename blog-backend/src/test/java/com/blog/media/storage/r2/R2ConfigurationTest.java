package com.blog.media.storage.r2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class R2ConfigurationTest {

    @Test
    void keepsConfiguredR2ReadableWhenLocalIsTheDefaultUploadProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(R2Configuration.class)
                .withPropertyValues(
                        "blog.media.provider=local",
                        "blog.media.r2.account-id=account",
                        "blog.media.r2.access-key-id=access",
                        "blog.media.r2.secret-access-key=secret",
                        "blog.media.r2.bucket=blog-media",
                        "blog.media.r2.public-base-url=https://images.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(R2ObjectStorage.class);
                });
    }

    @Test
    void failsStartupWhenR2IsTheDefaultButCredentialsAreIncomplete() {
        new ApplicationContextRunner()
                .withUserConfiguration(R2Configuration.class)
                .withPropertyValues("blog.media.provider=r2")
                .run(context -> assertThat(context).hasFailed());
    }
}
