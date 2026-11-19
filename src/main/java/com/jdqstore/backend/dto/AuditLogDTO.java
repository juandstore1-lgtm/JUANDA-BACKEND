package com.jdqstore.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String actionType;
    private String entityType;
    private String entityId;
    private String previousData;
    private String newData;
    private String ipAddress;
    private Long storeId;
    private String storeName;
    private LocalDateTime createdAt;
}
