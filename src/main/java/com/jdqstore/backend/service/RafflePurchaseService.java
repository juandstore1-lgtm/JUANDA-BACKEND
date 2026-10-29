package com.jdqstore.backend.service;

import com.jdqstore.backend.config.RafflePurchaseProperties;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.dto.TicketReservationDTO;
import com.jdqstore.backend.security.ClientIpResolver;
import com.jdqstore.backend.security.PurchaseRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Compra de boletos desde el endpoint. Todas las solicitudes pasan la validación estricta; las anónimas además
 * pasan los límites contra abuso (por IP y por teléfono). Los límites de boletos por solicitud y de reservas
 * activas por teléfono se aplican siempre (validador y TicketService). La reserva se hace en TicketService.
 */
@Service
@RequiredArgsConstructor
public class RafflePurchaseService {

    private static final Logger log = LoggerFactory.getLogger(RafflePurchaseService.class);

    private final RafflePurchaseProperties properties;
    private final PurchaseRequestValidator validator;
    private final PurchaseRateLimiter rateLimiter;
    private final ClientIpResolver ipResolver;
    private final TicketService ticketService;

    public TicketReservationDTO purchase(Long raffleId, TicketPurchaseRequest request, HttpServletRequest httpRequest) {
        if (!isAnonymous()) {
            return ticketService.purchaseTickets(raffleId, validator.normalize(request));
        }

        // El límite por IP va antes de validar: también cuentan las solicitudes rechazadas
        rateLimiter.checkIp(ipResolver.resolve(httpRequest));
        TicketPurchaseRequest normalized = validator.normalize(request);
        rateLimiter.checkPhone(normalized.getBuyer().getPhone());
        return ticketService.purchaseTickets(raffleId, normalized);
    }

    // Un token inválido o caducado no llega aquí como autenticado (JwtAuthenticationFilter lo trata como anónimo),
    // así que no sirve para saltarse los límites
    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutConfiguration() {
        if (!properties.isPublicEnabled()) return;
        log.info("Compra pública de boletos habilitada: máximo {} solicitudes por IP y {} por teléfono cada {}",
                properties.getMaxRequestsPerIp(), properties.getMaxRequestsPerPhone(), properties.getRateLimitWindow());
        if (properties.getTrustedProxies() <= 0) {
            log.warn("Compra pública con trusted-proxies=0: el límite por IP usa la IP de la conexión; detrás de un proxy "
                    + "todos los visitantes comparten ese límite (ver docs/compra-publica-boletos.md)");
        }
    }
}
