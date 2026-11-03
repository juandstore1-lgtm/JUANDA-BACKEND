package com.jdqstore.backend;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class R2ConnectivityTest {

    @Test
    public void testR2ConnectionAndPutObject() {
        String endpoint = "https://2946e9ab97bba1f6be5a7c182e8bede3.r2.cloudflarestorage.com";
        String accessKey = "67292ce9a873a6e83d92440a89f0933b";
        String secretKey = "ac719eb47823ff8b8a11697b4323499941be2c4500593bb04332148774c6ab14";
        String bucket = "juandstore";

        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();

        String testKey = "healthcheck/test-" + System.currentTimeMillis() + ".txt";
        byte[] payload = "R2 CONNECTIVITY OK - JUANDSTORE".getBytes(StandardCharsets.UTF_8);

        System.out.println("Enviando objeto de prueba a Cloudflare R2...");
        PutObjectResponse putResponse = s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(testKey)
                        .contentType("text/plain")
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(payload)
        );

        assertNotNull(putResponse.eTag());
        System.out.println("Subida exitosa! eTag: " + putResponse.eTag());

        // Limpiar objeto de prueba
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(testKey).build());
        System.out.println("Objeto de prueba eliminado correctamente de R2.");
    }
}
