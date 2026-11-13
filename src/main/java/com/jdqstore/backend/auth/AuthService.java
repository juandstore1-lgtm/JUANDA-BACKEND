package com.jdqstore.backend.auth;

import com.jdqstore.backend.audit.AuditEvent;
import com.jdqstore.backend.dto.AuthRequest;
import com.jdqstore.backend.dto.AuthResponse;
import com.jdqstore.backend.repository.UserRepository;
import com.jdqstore.backend.security.ClientIpResolver;
import com.jdqstore.backend.security.JwtService;
import com.jdqstore.backend.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ClientIpResolver clientIpResolver;

    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow();
        var userDetails = new UserDetailsImpl(user);
        var jwtToken = jwtService.generateToken(new HashMap<>(), userDetails);

        // Publish LOGIN audit event
        try {
            String ip = resolveClientIp();
            String roleName = user.getRole() != null ? user.getRole().getName() : "UNKNOWN";
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(user.getId())
                    .userName(user.getName())
                    .userEmail(user.getEmail())
                    .userRole(roleName)
                    .actionType("LOGIN")
                    .entityType("AUTH")
                    .entityId(String.valueOf(user.getId()))
                    .previousData(null)
                    .newData("{\"email\":\"" + user.getEmail() + "\",\"role\":\"" + roleName + "\"}")
                    .ipAddress(ip)
                    .build());
        } catch (Exception e) {
            log.warn("Could not publish LOGIN audit event: {}", e.getMessage());
        }

        return AuthResponse.builder()
                .token(jwtToken)
                .role(user.getRole().getName())
                .storeIds(user.getStores() != null ? user.getStores().stream().map(s -> s.getId()).toList() : new java.util.ArrayList<>())
                .build();
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
}
