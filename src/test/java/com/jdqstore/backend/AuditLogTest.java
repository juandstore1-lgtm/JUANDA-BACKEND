package com.jdqstore.backend;

import com.jdqstore.backend.audit.AuditEvent;
import com.jdqstore.backend.audit.AuditEventListener;
import com.jdqstore.backend.dto.AuditLogDTO;
import com.jdqstore.backend.entity.AuditLog;
import com.jdqstore.backend.repository.AuditLogRepository;
import com.jdqstore.backend.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private AuditEventListener auditEventListener;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
        auditEventListener = new AuditEventListener(auditLogRepository);
    }

    @Test
    void testAuditEventListenerPersistsAuditLog() {
        AuditEvent event = AuditEvent.builder()
                .userId(10L)
                .userName("Carlos Store Admin")
                .userEmail("carlos@jdqstore.com")
                .userRole("STORE_ADMIN")
                .actionType("CREATE")
                .entityType("PRODUCT")
                .entityId("42")
                .previousData(null)
                .newData("{\"name\":\"Gorra Nike\"}")
                .ipAddress("190.24.50.12")
                .build();

        auditEventListener.handleAuditEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(10L, saved.getUserId());
        assertEquals("Carlos Store Admin", saved.getUserName());
        assertEquals("carlos@jdqstore.com", saved.getUserEmail());
        assertEquals("STORE_ADMIN", saved.getUserRole());
        assertEquals("CREATE", saved.getActionType());
        assertEquals("PRODUCT", saved.getEntityType());
        assertEquals("42", saved.getEntityId());
        assertEquals("{\"name\":\"Gorra Nike\"}", saved.getNewData());
        assertEquals("190.24.50.12", saved.getIpAddress());
    }

    @Test
    void testAuditLogServiceFiltersAndPagination() {
        AuditLog mockLog = AuditLog.builder()
                .id(1L)
                .userId(10L)
                .userName("Carlos Store Admin")
                .userEmail("carlos@jdqstore.com")
                .userRole("STORE_ADMIN")
                .actionType("UPDATE")
                .entityType("PRODUCT")
                .entityId("42")
                .previousData("{\"price\":50000}")
                .newData("{\"price\":55000}")
                .ipAddress("127.0.0.1")
                .createdAt(LocalDateTime.now())
                .build();

        Page<AuditLog> mockPage = new PageImpl<>(List.of(mockLog), PageRequest.of(0, 10), 1);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        Page<AuditLogDTO> result = auditLogService.getAuditLogs(
                10L, "UPDATE", "PRODUCT",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                PageRequest.of(0, 10)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        AuditLogDTO dto = result.getContent().get(0);
        assertEquals(1L, dto.getId());
        assertEquals(10L, dto.getUserId());
        assertEquals("UPDATE", dto.getActionType());
        assertEquals("PRODUCT", dto.getEntityType());
        assertEquals("{\"price\":50000}", dto.getPreviousData());
        assertEquals("{\"price\":55000}", dto.getNewData());
    }
}
