package com.jdqstore.backend.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
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
}
