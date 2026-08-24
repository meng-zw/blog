package com.blog.media.storage.r2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Creates R2 clients only when R2 is the active media provider. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "blog.media", name = "provider", havingValue = "r2")
@EnableConfigurationProperties(R2Properties.class)
public class R2Configuration {

    @Bean(destroyMethod = "close")
    S3Client r2S3Client(R2Properties properties) {
        properties.validate();
        return S3Client.builder()
                .endpointOverride(properties.endpointUri())
                .credentialsProvider(credentials(properties))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner r2S3Presigner(R2Properties properties) {
        properties.validate();
        return S3Presigner.builder()
                .endpointOverride(properties.endpointUri())
                .credentialsProvider(credentials(properties))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    R2ObjectStorage r2ObjectStorage(S3Client r2S3Client, S3Presigner r2S3Presigner, R2Properties properties) {
        return new R2ObjectStorage(r2S3Client, r2S3Presigner, properties);
    }

    private static StaticCredentialsProvider credentials(R2Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey()));
    }
}
