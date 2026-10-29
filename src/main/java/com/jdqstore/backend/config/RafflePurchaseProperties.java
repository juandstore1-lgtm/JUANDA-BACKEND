package com.jdqstore.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Único lugar donde se definen los límites de la compra de boletos. Cada valor se puede sobrescribir con una
 * variable de entorno (p. ej. app.raffle-purchase.public-enabled -> APP_RAFFLEPURCHASE_PUBLICENABLED).
 * Ver docs/compra-publica-boletos.md.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.raffle-purchase")
public class RafflePurchaseProperties {

    /** Permite comprar sin sesión. Desactivado por defecto: sin él, la compra exige autenticación como antes. */
    private boolean publicEnabled = false;

    /** Tiempo que un boleto queda reservado esperando la confirmación del pago. */
    private Duration reservationDuration = Duration.ofMinutes(15);

    private int maxTicketsPerRequest = 5;

    /** Boletos en estado RESERVED que puede acumular un mismo teléfono en una rifa. */
    private int maxActiveTicketsPerPhone = 5;

    /** Ventana de los límites de solicitudes (compras anónimas). */
    private Duration rateLimitWindow = Duration.ofMinutes(60);
    private int maxRequestsPerIp = 20;
    private int maxRequestsPerPhone = 5;

    /**
     * Proxies de confianza delante de la aplicación. 0: se usa la IP de la conexión. N > 0: se toma la N-ésima IP
     * empezando por la derecha de X-Forwarded-For (las anteriores las puede inventar el cliente).
     */
    private int trustedProxies = 0;

    /** Prefijo internacional que se elimina para guardar los teléfonos locales en un único formato. */
    private String defaultCountryCode = "57";

    private boolean expiryJobEnabled = true;
    private Duration expiryCheckInterval = Duration.ofMinutes(1);
}
