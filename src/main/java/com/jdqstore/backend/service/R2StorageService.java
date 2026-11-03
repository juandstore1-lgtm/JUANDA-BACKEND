package com.jdqstore.backend.service;

import com.jdqstore.backend.config.R2StorageProperties;
import com.jdqstore.backend.dto.PresignedUrlRequest;
import com.jdqstore.backend.dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    private static final String CACHE_CONTROL_IMMUTABLE = "public, max-age=31536000, immutable";
    private static final Pattern BASE64_PATTERN = Pattern.compile("^data:(image/[a-zA-Z0-9.+]+);base64,(.+)$", Pattern.DOTALL);

    private static final Set<String> ALLOWED_ENTITIES = Set.of(
            "products", "categories", "sedes", "raffles",
            "contests", "participants", "hero-slides", "collections",
            "outfits", "general"
    );

    private static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/svg+xml", "svg"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2StorageProperties properties;

    public PresignedUrlResponse generatePresignedUploadUrl(PresignedUrlRequest request) {
        String entity = sanitizeEntity(request.getEntity());
        String contentType = request.getContentType() != null ? request.getContentType().toLowerCase().trim() : "image/webp";

        if (!MIME_TO_EXTENSION.containsKey(contentType)) {
            throw new IllegalArgumentException("Tipo de contenido no permitido: " + contentType +
                    ". Tipos soportados: " + String.join(", ", MIME_TO_EXTENSION.keySet()));
        }

        String extension = MIME_TO_EXTENSION.get(contentType);
        String uniqueId = UUID.randomUUID().toString();
        String key = String.format("%s/%s.%s", entity, uniqueId, extension);

        Duration expiration = Duration.ofMinutes(Math.max(5, properties.getPresignedUrlExpirationMinutes()));

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL_IMMUTABLE)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presigned.url().toString();
        String publicUrl = buildPublicUrl(key);

        log.info("Generated presigned upload URL for key: {}, expires in: {}s", key, expiration.toSeconds());

        return PresignedUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .publicUrl(publicUrl)
                .key(key)
                .contentType(contentType)
                .expiresInSeconds(expiration.toSeconds())
                .build();
    }

    public String uploadDirect(byte[] bytes, String key, String contentType) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Cloudflare R2 no está configurado (faltan R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY).");
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL_IMMUTABLE)
                .contentLength((long) bytes.length)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
        String publicUrl = buildPublicUrl(key);
        log.info("Directly uploaded object to R2: {} ({} bytes)", publicUrl, bytes.length);
        return publicUrl;
    }

    public String uploadBase64(String base64Data, String entity, String optionalId) {
        if (base64Data == null || base64Data.trim().isEmpty()) {
            return null;
        }

        // If it's already an HTTP URL, no upload needed
        if (base64Data.startsWith("http://") || base64Data.startsWith("https://")) {
            return base64Data;
        }

        DecodedImage decoded = decodeBase64Image(base64Data);
        String sanitizedEntity = sanitizeEntity(entity);
        String extension = MIME_TO_EXTENSION.getOrDefault(decoded.contentType, "webp");
        String fileIdentifier = (optionalId != null && !optionalId.trim().isEmpty())
                ? optionalId.trim() + "_" + UUID.randomUUID().toString().substring(0, 8)
                : UUID.randomUUID().toString();

        String key = String.format("%s/%s.%s", sanitizedEntity, fileIdentifier, extension);
        return uploadDirect(decoded.bytes, key, decoded.contentType);
    }

    public void deleteObject(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.trim().isEmpty() || !properties.isConfigured()) {
            return;
        }

        String key = extractKeyFromUrl(keyOrUrl);
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted object from R2: {}", key);
        } catch (Exception e) {
            log.warn("Could not delete object from R2: {} - {}", key, e.getMessage());
        }
    }

    public boolean isBase64Image(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return value.startsWith("data:image/") || (!value.startsWith("http://") && !value.startsWith("https://") && value.length() > 500);
    }

    public DecodedImage decodeBase64Image(String rawInput) {
        if (rawInput == null) {
            throw new IllegalArgumentException("La cadena de imagen no puede ser nula");
        }

        String trimmed = rawInput.trim();
        Matcher matcher = BASE64_PATTERN.matcher(trimmed);

        String contentType = "image/webp";
        String payload = trimmed;

        if (matcher.find()) {
            contentType = matcher.group(1).toLowerCase();
            payload = matcher.group(2);
        } else if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",", 2);
            payload = parts[1];
        }

        // Clean any whitespace or newline characters from base64
        payload = payload.replaceAll("\\s+", "");

        byte[] bytes = Base64.getDecoder().decode(payload);
        return new DecodedImage(bytes, contentType);
    }

    public String buildPublicUrl(String key) {
        String base = properties.getPublicUrlBase();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        return base + "/" + cleanKey;
    }

    private String extractKeyFromUrl(String keyOrUrl) {
        if (!keyOrUrl.startsWith("http://") && !keyOrUrl.startsWith("https://")) {
            return keyOrUrl;
        }
        String base = properties.getPublicUrlBase();
        if (keyOrUrl.startsWith(base)) {
            String key = keyOrUrl.substring(base.length());
            return key.startsWith("/") ? key.substring(1) : key;
        }
        // Fallback: take last two path segments
        try {
            URI uri = URI.create(keyOrUrl);
            String path = uri.getPath();
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception e) {
            return keyOrUrl;
        }
    }

    private String sanitizeEntity(String entity) {
        if (entity == null || entity.trim().isEmpty()) {
            return "general";
        }
        String lower = entity.toLowerCase().trim().replaceAll("[^a-z0-9_-]", "");
        return ALLOWED_ENTITIES.contains(lower) ? lower : "general";
    }

    public record DecodedImage(byte[] bytes, String contentType) {}
}
