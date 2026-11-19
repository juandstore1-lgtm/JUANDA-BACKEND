package com.jdqstore.backend.audit;

import com.jdqstore.backend.entity.AuditLog;
import com.jdqstore.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async("auditTaskExecutor")
    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(event.getUserId())
                    .userName(event.getUserName() != null ? event.getUserName() : "Sistema")
                    .userEmail(event.getUserEmail() != null ? event.getUserEmail() : "sistema@jdqstore.com")
                    .userRole(event.getUserRole() != null ? event.getUserRole() : "UNKNOWN")
                    .actionType(event.getActionType())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .previousData(event.getPreviousData())
                    .newData(event.getNewData())
                    .ipAddress(event.getIpAddress())
                    .storeId(event.getStoreId())
                    .storeName(event.getStoreName())
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("AuditLog saved successfully: action={}, entity={}, user={}",
                    event.getActionType(), event.getEntityType(), event.getUserEmail());
        } catch (Exception e) {
            log.error("Failed to persist AuditLog: {}", e.getMessage(), e);
        }
    }
}
