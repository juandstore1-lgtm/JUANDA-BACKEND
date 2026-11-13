package com.jdqstore.backend.controller;

import com.jdqstore.backend.audit.Auditable;
import com.jdqstore.backend.config.R2StorageProperties;
import com.jdqstore.backend.dto.PresignedUrlRequest;
import com.jdqstore.backend.dto.PresignedUrlResponse;
import com.jdqstore.backend.service.R2StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StorageController {

    private final R2StorageService storageService;
    private final R2StorageProperties properties;

    @PostMapping("/presigned-url")
    @Auditable(action = "CREATE", entity = "STORAGE")
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
        return ResponseEntity.ok(storageService.generatePresignedUploadUrl(request));
    }

    @PostMapping("/public/presigned-url")
    public ResponseEntity<PresignedUrlResponse> generatePublicPresignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
        // Only allow participant or contest uploads via public endpoint
        String entity = request.getEntity() != null ? request.getEntity().toLowerCase().trim() : "";
        if (!"participants".equals(entity) && !"contests".equals(entity)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(storageService.generatePresignedUploadUrl(request));
    }

    @PostMapping("/upload-base64")
    @Auditable(action = "CREATE", entity = "STORAGE")
    public ResponseEntity<Map<String, String>> uploadBase64Direct(@RequestBody Map<String, String> payload) {
        String base64Data = payload.get("data");
        String entity = payload.getOrDefault("entity", "general");
        String optionalId = payload.get("id");

        if (base64Data == null || base64Data.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "data (base64) es requerido"));
        }

        String publicUrl = storageService.uploadBase64(base64Data, entity, optionalId);
        return ResponseEntity.ok(Map.of("publicUrl", publicUrl));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStorageStatus() {
        return ResponseEntity.ok(Map.of(
                "configured", properties.isConfigured(),
                "endpoint", properties.getEndpoint(),
                "bucket", properties.getBucketName(),
                "publicUrlBase", properties.getPublicUrlBase(),
                "presignedExpirationMinutes", properties.getPresignedUrlExpirationMinutes()
        ));
    }
}
