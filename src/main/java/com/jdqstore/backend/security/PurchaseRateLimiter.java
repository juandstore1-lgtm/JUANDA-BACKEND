package com.jdqstore.backend.security;

import com.jdqstore.backend.config.RafflePurchaseProperties;
import com.jdqstore.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de solicitudes de compra anónimas por IP y por teléfono (ventana fija). En memoria: vale para una sola
 * instancia y se reinicia al reiniciar la aplicación (ver docs/compra-publica-boletos.md). Las claves se guardan
 * como hash para no conservar teléfonos ni IPs en claro.
 */
@Component
@RequiredArgsConstructor
public class PurchaseRateLimiter {

    static final String TOO_MANY_REQUESTS = "Demasiadas solicitudes. Intenta de nuevo en unos minutos.";

    private record Window(Instant start, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final RafflePurchaseProperties properties;
    private final Clock clock;

    public void checkIp(String ip) {
        check("ip:" + hash(ip), properties.getMaxRequestsPerIp());
    }

    public void checkPhone(String phone) {
        check("phone:" + hash(phone), properties.getMaxRequestsPerPhone());
    }

    private void check(String key, int max) {
        Instant now = clock.instant();
        Window window = windows.compute(key, (k, current) -> current == null || !now.isBefore(current.start().plus(properties.getRateLimitWindow()))
                ? new Window(now, 1)
                : new Window(current.start(), current.count() + 1));
        if (window.count() > max) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS);
        }
    }

    /** Elimina las ventanas ya cerradas; lo llama la tarea programada para que el mapa no crezca sin límite. */
    public void removeExpiredWindows() {
        Instant cutoff = clock.instant().minus(properties.getRateLimitWindow());
        windows.values().removeIf(window -> !window.start().isAfter(cutoff));
    }

    /** Para pruebas. */
    public void reset() {
        windows.clear();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
