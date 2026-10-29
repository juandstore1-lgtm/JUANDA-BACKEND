package com.jdqstore.backend.service;

import com.jdqstore.backend.config.RafflePurchaseProperties;
import com.jdqstore.backend.dto.BuyerDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Valida y normaliza la solicitud de compra antes de abrir la transacción. Devuelve una copia normalizada. */
@Component
@RequiredArgsConstructor
public class PurchaseRequestValidator {

    // Letras (con tildes), espacios, puntos, apóstrofos y guiones; empieza por letra
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'-]*$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d+$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final RafflePurchaseProperties properties;

    public TicketPurchaseRequest normalize(TicketPurchaseRequest request) {
        if (request == null || request.getBuyer() == null) throw new IllegalArgumentException("Ingresa los datos del comprador");

        List<Long> ticketIds = request.getTicketIds();
        if (ticketIds == null || ticketIds.isEmpty()) throw new IllegalArgumentException("Selecciona al menos un boleto");
        int max = properties.getMaxTicketsPerRequest();
        if (ticketIds.size() > max) throw new IllegalArgumentException("Puedes reservar como máximo " + max + " boletos por solicitud");
        if (ticketIds.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("Boleto no válido");
        if (new HashSet<>(ticketIds).size() != ticketIds.size()) throw new IllegalArgumentException("Hay boletos repetidos en la solicitud");

        BuyerDTO buyer = new BuyerDTO();
        buyer.setName(normalizeName(request.getBuyer().getName()));
        buyer.setPhone(normalizePhone(request.getBuyer().getPhone()));
        buyer.setEmail(normalizeEmail(request.getBuyer().getEmail()));

        TicketPurchaseRequest normalized = new TicketPurchaseRequest();
        normalized.setTicketIds(List.copyOf(ticketIds));
        normalized.setBuyer(buyer);
        return normalized;
    }

    private static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 100 || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Ingresa un nombre válido (solo letras, entre 2 y 100 caracteres)");
        }
        return name;
    }

    // Solo dígitos; los números locales se guardan sin el prefijo del país para que "+57 300 123 4567" y
    // "3001234567" cuenten como el mismo teléfono en los límites
    private String normalizePhone(String raw) {
        String phone = raw == null ? "" : PHONE_SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (!PHONE.matcher(phone).matches()) throw new IllegalArgumentException("Ingresa un número de teléfono válido");
        String digits = phone.startsWith("+") ? phone.substring(1) : phone;
        String countryCode = properties.getDefaultCountryCode();
        if (countryCode != null && !countryCode.isEmpty() && digits.startsWith(countryCode) && digits.length() == countryCode.length() + 10) {
            digits = digits.substring(countryCode.length());
        }
        if (digits.length() < 7 || digits.length() > 15) throw new IllegalArgumentException("Ingresa un número de teléfono válido");
        return digits;
    }

    private static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Ingresa un correo electrónico válido");
        return email;
    }
}
