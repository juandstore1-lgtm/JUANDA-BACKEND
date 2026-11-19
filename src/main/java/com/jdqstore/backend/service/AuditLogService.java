package com.jdqstore.backend.service;

import com.jdqstore.backend.dto.AuditLogDTO;
import com.jdqstore.backend.entity.AuditLog;
import com.jdqstore.backend.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLogDTO> getAuditLogs(Long userId, String actionType, String entityType,
                                          LocalDateTime startDate, LocalDateTime endDate,
                                          Pageable pageable) {
        return getAuditLogs(userId, null, actionType, entityType, null, startDate, endDate, pageable);
    }

    public Page<AuditLogDTO> getAuditLogs(Long userId, Long storeId, String actionType, String entityType,
                                          String search, LocalDateTime startDate, LocalDateTime endDate,
                                          Pageable pageable) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (storeId != null) {
                predicates.add(cb.equal(root.get("storeId"), storeId));
            }
            if (actionType != null && !actionType.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("actionType")), actionType.trim().toUpperCase()));
            }
            if (entityType != null && !entityType.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase()));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (search != null && !search.trim().isEmpty()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("userName")), term),
                    cb.like(cb.lower(root.get("entityId")), term),
                    cb.like(cb.lower(root.get("previousData")), term),
                    cb.like(cb.lower(root.get("newData")), term)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable).map(this::mapToDTO);
    }

    private AuditLogDTO mapToDTO(AuditLog entity) {
        return AuditLogDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .userEmail(entity.getUserEmail())
                .userRole(entity.getUserRole())
                .actionType(entity.getActionType())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .previousData(entity.getPreviousData())
                .newData(entity.getNewData())
                .ipAddress(entity.getIpAddress())
                .storeId(entity.getStoreId())
                .storeName(entity.getStoreName())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
