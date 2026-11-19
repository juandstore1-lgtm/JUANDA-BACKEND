package com.jdqstore.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdqstore.backend.dto.ProductDTO;
import com.jdqstore.backend.entity.Category;
import com.jdqstore.backend.entity.Product;
import com.jdqstore.backend.entity.Sede;
import com.jdqstore.backend.entity.User;
import com.jdqstore.backend.repository.CategoryRepository;
import com.jdqstore.backend.repository.ProductRepository;
import com.jdqstore.backend.repository.SedeRepository;
import com.jdqstore.backend.security.ClientIpResolver;
import com.jdqstore.backend.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final ClientIpResolver clientIpResolver;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SedeRepository sedeRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditable)")
    public Object auditAction(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        User currentUser = resolveCurrentUser();
        String ipAddress = resolveClientIp();

        String entityId = extractEntityId(joinPoint.getArgs());
        String previousDataJson = null;

        // Snapshot previous data for UPDATE and DELETE
        if (("UPDATE".equalsIgnoreCase(auditable.action()) || "DELETE".equalsIgnoreCase(auditable.action())) && entityId != null) {
            try {
                Long id = Long.parseLong(entityId);
                if ("PRODUCT".equalsIgnoreCase(auditable.entity())) {
                    Optional<Product> prod = productRepository.findById(id);
                    if (prod.isPresent()) {
                        previousDataJson = objectMapper.writeValueAsString(prod.get());
                    }
                } else if ("CATEGORY".equalsIgnoreCase(auditable.entity())) {
                    Optional<Category> cat = categoryRepository.findById(id);
                    if (cat.isPresent()) {
                        previousDataJson = objectMapper.writeValueAsString(cat.get());
                    }
                }
            } catch (Exception e) {
                log.warn("Could not capture previousData for audit: {}", e.getMessage());
            }
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (AccessDeniedException ex) {
            // Audit failed security attempt if user was authenticated
            if (currentUser != null) {
                publishEvent(currentUser, "ACCESS_DENIED", auditable.entity(), entityId,
                        previousDataJson, "{\"error\":\"" + ex.getMessage() + "\"}", ipAddress, null, null);
            }
            throw ex;
        }

        // On successful completion, determine new data and final entityId
        if (currentUser != null) {
            try {
                Object body = result instanceof ResponseEntity<?> re ? re.getBody() : result;
                String newDataJson = null;

                if (!"DELETE".equalsIgnoreCase(auditable.action()) && body != null) {
                    newDataJson = objectMapper.writeValueAsString(body);
                    if (entityId == null) {
                        entityId = tryExtractIdFromBody(body);
                    }
                }

                Long storeId = extractStoreId(joinPoint.getArgs(), currentUser, previousDataJson, newDataJson);
                String storeName = resolveStoreName(storeId);

                publishEvent(currentUser, auditable.action(), auditable.entity(), entityId,
                        previousDataJson, newDataJson, ipAddress, storeId, storeName);
            } catch (Exception e) {
                log.warn("Could not publish audit event after method execution: {}", e.getMessage());
            }
        }

        return result;
    }

    private Long extractStoreId(Object[] args, User user, String previousDataJson, String newDataJson) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof ProductDTO dto && dto.getStoreIds() != null && !dto.getStoreIds().isEmpty()) {
                    return dto.getStoreIds().get(0);
                }
            }
        }
        try {
            if (newDataJson != null) {
                var node = objectMapper.readTree(newDataJson);
                if (node.has("storeIds") && node.get("storeIds").isArray() && node.get("storeIds").size() > 0) {
                    return node.get("storeIds").get(0).asLong();
                }
                if (node.has("storeId")) {
                    return node.get("storeId").asLong();
                }
            }
            if (previousDataJson != null) {
                var node = objectMapper.readTree(previousDataJson);
                if (node.has("stores") && node.get("stores").isArray() && node.get("stores").size() > 0) {
                    return node.get("stores").get(0).get("id").asLong();
                }
                if (node.has("storeIds") && node.get("storeIds").isArray() && node.get("storeIds").size() > 0) {
                    return node.get("storeIds").get(0).asLong();
                }
            }
        } catch (Exception ignored) {}

        if (user != null && user.getStores() != null && !user.getStores().isEmpty()) {
            return user.getStores().get(0).getId();
        }
        return null;
    }

    private String resolveStoreName(Long storeId) {
        if (storeId == null) return null;
        try {
            Optional<Sede> sede = sedeRepository.findById(storeId);
            if (sede.isPresent()) {
                String name = sede.get().getName();
                String barrio = sede.get().getBarrio();
                return (barrio != null && !barrio.isBlank()) ? barrio : name;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void publishEvent(User user, String action, String entity, String entityId,
                              String previousData, String newData, String ip,
                              Long storeId, String storeName) {
        String role = user.getRole() != null ? user.getRole().getName() : "UNKNOWN";
        AuditEvent event = AuditEvent.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .userRole(role)
                .actionType(action)
                .entityType(entity)
                .entityId(entityId)
                .previousData(previousData)
                .newData(newData)
                .ipAddress(ip)
                .storeId(storeId)
                .storeName(storeName)
                .build();

        eventPublisher.publishEvent(event);
    }

    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl principal) {
            return principal.getUser();
        }
        return null;
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return clientIpResolver.resolve(request);
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private String extractEntityId(Object[] args) {
        if (args == null || args.length == 0) return null;
        for (Object arg : args) {
            if (arg instanceof Long l) {
                return String.valueOf(l);
            }
        }
        return null;
    }

    private String tryExtractIdFromBody(Object body) {
        if (body == null) return null;
        try {
            Method getIdMethod = body.getClass().getMethod("getId");
            Object idVal = getIdMethod.invoke(body);
            if (idVal != null) {
                return String.valueOf(idVal);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
