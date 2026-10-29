package com.jdqstore.backend.security;

import com.jdqstore.backend.config.RafflePurchaseProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IP del cliente para los límites de solicitudes. Por defecto, la de la conexión (detrás de un proxy es la del
 * proxy). Con trusted-proxies = N solo se confía en las N últimas entradas de X-Forwarded-For, que añaden
 * nuestros proxies: las primeras las puede escribir el cliente.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final RafflePurchaseProperties properties;

    public String resolve(HttpServletRequest request) {
        int trustedProxies = properties.getTrustedProxies();
        if (trustedProxies <= 0) return request.getRemoteAddr();

        List<String> chain = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders("X-Forwarded-For"))) {
            for (String part : header.split(",")) {
                if (!part.isBlank()) chain.add(part.trim());
            }
        }
        int clientIndex = chain.size() - trustedProxies;
        // Cadena más corta de lo configurado: la infraestructura no es la esperada; no se usa nada del cliente
        return clientIndex >= 0 ? chain.get(clientIndex) : request.getRemoteAddr();
    }
}
