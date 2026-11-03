package com.jdqstore.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class R2StorageConfig {

    private final R2StorageProperties properties;

    @Bean
    public S3Client s3Client() {
        String accessKey = properties.isConfigured() ? properties.getAccessKeyId() : "mock-access-key";
        String secretKey = properties.isConfigured() ? properties.getSecretAccessKey() : "mock-secret-key";

        log.info("Initializing S3/R2 client with endpoint: {}, bucket: {}", 
                 properties.getEndpoint(), properties.getBucketName());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        String accessKey = properties.isConfigured() ? properties.getAccessKeyId() : "mock-access-key";
        String secretKey = properties.isConfigured() ? properties.getSecretAccessKey() : "mock-secret-key";

        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
