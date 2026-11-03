package com.jdqstore.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cloudflare.r2")
@Data
public class R2StorageProperties {

    private String accountId = "";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String bucketName = "juandstore-media";
    private String publicUrlBase = "https://media.juandstore.com";
    private int presignedUrlExpirationMinutes = 15;

    public String getEndpoint() {
        if (accountId != null && !accountId.trim().isEmpty()) {
            return "https://" + accountId.trim() + ".r2.cloudflarestorage.com";
        }
        return "https://r2.cloudflarestorage.com";
    }

    public boolean isConfigured() {
        return accessKeyId != null && !accessKeyId.trim().isEmpty() &&
               secretAccessKey != null && !secretAccessKey.trim().isEmpty();
    }
}
