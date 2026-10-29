package com.jdqstore.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdqstore.backend.config.RafflePurchaseProperties;
import com.jdqstore.backend.dto.BuyerDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.entity.User;
import com.jdqstore.backend.repository.UserRepository;
import com.jdqstore.backend.security.ClientIpResolver;
import com.jdqstore.backend.security.JwtService;
import com.jdqstore.backend.security.PurchaseRateLimiter;
import com.jdqstore.backend.security.UserDetailsImpl;
import com.jdqstore.backend.service.ReservationExpiryJob;
import com.jdqstore.backend.service.TicketService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Compra pública (sin sesión) de boletos: validación, límites por IP y teléfono, reservas con vencimiento y
 * concurrencia. Sin CAPTCHA ni servicios externos.
 */
@SpringBootTest(properties = "app.raffle-purchase.public-enabled=true")
@AutoConfigureMockMvc
class PublicTicketPurchaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketService ticketService;
    @Autowired private ReservationExpiryJob expiryJob;
    @Autowired private PurchaseRateLimiter rateLimiter;
    @Autowired private RafflePurchaseProperties properties;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private int maxRequestsPerIp;
    private int maxRequestsPerPhone;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE tickets, buyers, raffles CASCADE");
        // Rifa 901 activa con 10 boletos libres; 902 aún no abierta; 903 finalizada
        jdbc.update("INSERT INTO raffles (id, name, prize, ticket_price, total_tickets, available_tickets, status, created_at) VALUES "
                + "(901, 'Activa', 'Premio', 5000.00, 10, 10, 'ACTIVE', '2026-01-01 00:00:00'),"
                + "(902, 'Proxima', 'Premio', 5000.00, 1, 1, 'UPCOMING', '2026-01-01 00:00:00'),"
                + "(903, 'Cerrada', 'Premio', 5000.00, 1, 1, 'FINISHED', '2026-01-01 00:00:00')");
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO tickets (id, raffle_id, ticket_number, status) VALUES (?, 901, ?, 'AVAILABLE')", 9001 + i, "%02d".formatted(i));
        }
        jdbc.update("INSERT INTO tickets (id, raffle_id, ticket_number, status) VALUES (9101, 902, '00', 'AVAILABLE'), (9201, 903, '00', 'AVAILABLE')");

        rateLimiter.reset();
        maxRequestsPerIp = properties.getMaxRequestsPerIp();
        maxRequestsPerPhone = properties.getMaxRequestsPerPhone();
    }

    @AfterEach
    void restoreLimits() {
        properties.setMaxRequestsPerIp(maxRequestsPerIp);
        properties.setMaxRequestsPerPhone(maxRequestsPerPhone);
        rateLimiter.reset();
    }

    // ---------- utilidades ----------

    private static String body(String ticketIds, String name, String phone, String email) {
        return "{\"ticketIds\":[" + ticketIds + "],\"buyer\":{\"name\":" + json(name) + ",\"phone\":" + json(phone)
                + ",\"email\":" + json(email) + "}}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String valid(String ticketIds) {
        return body(ticketIds, "Laura Gómez", "+57 300 123 4567", "Laura@Correo.com");
    }

    private MvcResult purchase(long raffleId, String body) throws Exception {
        return mockMvc.perform(purchaseRequest(raffleId, body)).andReturn();
    }

    private MockHttpServletRequestBuilder purchaseRequest(long raffleId, String body) {
        return post("/api/raffles/" + raffleId + "/tickets/purchase").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Error con el formato de la API: solo {"error": mensaje}, sin SQL, clases ni datos internos. */
    private void assertError(MvcResult result, int status, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode error = objectMapper.readTree(text(result));
        assertThat(error.size()).isEqualTo(1);
        assertThat(error.path("error").asText()).isEqualTo(message);
        assertThat(text(result)).doesNotContainIgnoringCase("sql").doesNotContain("Exception", "org.", "tickets", "buyer_id");
    }

    private int available(long raffleId) {
        return jdbc.queryForObject("SELECT available_tickets FROM raffles WHERE id = ?", Integer.class, raffleId);
    }

    private int buyers() {
        return jdbc.queryForObject("SELECT count(*) FROM buyers", Integer.class);
    }

    private String state(long ticketId) {
        return jdbc.queryForObject("SELECT t.status || ':' || coalesce(b.name, '-') || ':' || coalesce(b.payment_status, '-') "
                + "FROM tickets t LEFT JOIN buyers b ON b.id = t.buyer_id WHERE t.id = ?", String.class, ticketId);
    }

    /** Estado sin compradores ni cambios: ningún error deja una reserva parcial. */
    private void assertUntouched() {
        assertThat(buyers()).isZero();
        assertThat(available(901)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE status <> 'AVAILABLE'", Integer.class)).isZero();
    }

    private String bearer() {
        User admin = userRepository.findByEmail("admin@jdqstore.com").orElseThrow();
        return "Bearer " + jwtService.generateToken(new UserDetailsImpl(admin));
    }

    private static TicketPurchaseRequest request(String name, String phone, Long... ticketIds) {
        BuyerDTO buyer = new BuyerDTO();
        buyer.setName(name);
        buyer.setPhone(phone);
        TicketPurchaseRequest request = new TicketPurchaseRequest();
        request.setBuyer(buyer);
        request.setTicketIds(List.of(ticketIds));
        return request;
    }

    private void expire(long... ticketIds) {
        for (long id : ticketIds) {
            jdbc.update("UPDATE tickets SET reservation_expires_at = now() - interval '1 minute' WHERE id = ?", id);
        }
    }

    // ---------- compra anónima ----------

    @Test
    void anonymousVisitorReservesTicketsWithoutLoggingIn() throws Exception {
        Instant before = Instant.now();
        MvcResult result = purchase(901, valid("9003,9002"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode reservation = objectMapper.readTree(text(result));
        assertThat(reservation.path("status").asText()).isEqualTo("RESERVED");
        assertThat(reservation.path("ticketNumbers").toString()).isEqualTo("[\"01\",\"02\"]");
        assertThat(reservation.path("ticketIds").toString()).isEqualTo("[9002,9003]");
        Instant reservedUntil = Instant.parse(reservation.path("reservedUntil").asText());
        assertThat(reservedUntil).isBetween(before.plus(Duration.ofMinutes(15)), Instant.now().plus(Duration.ofMinutes(15)));
        // La respuesta no devuelve datos personales
        assertThat(text(result)).doesNotContain("Laura", "3001234567", "correo");

        // Reservado (no vendido), con vencimiento guardado y contador descontado
        assertThat(state(9002)).isEqualTo("RESERVED:Laura Gómez:PENDING");
        assertThat(state(9003)).isEqualTo("RESERVED:Laura Gómez:PENDING");
        // PostgreSQL guarda microsegundos: se compara con margen de 1 s
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE id IN (9002, 9003) "
                + "AND abs(extract(epoch FROM reservation_expires_at - ?::timestamptz)) < 1", Integer.class, reservedUntil.toString())).isEqualTo(2);
        assertThat(available(901)).isEqualTo(8);
        // Datos normalizados: teléfono sin prefijo ni separadores, correo en minúsculas
        assertThat(jdbc.queryForObject("SELECT phone || '|' || email FROM buyers", String.class)).isEqualTo("3001234567|laura@correo.com");

        // La vista pública muestra el vencimiento pero no al comprador
        String publicTickets = text(mockMvc.perform(get("/api/raffles/901/tickets")).andReturn());
        assertThat(publicTickets).contains("\"reservedUntil\"").doesNotContain("Laura", "3001234567");
    }

    @Test
    void invalidRequestsAreRejectedWithoutReservingAnything() throws Exception {
        properties.setMaxRequestsPerIp(100); // esta prueba hace más solicitudes que el límite por IP
        assertError(purchase(901, valid("")), 400, "Selecciona al menos un boleto");
        assertError(purchase(901, valid("9001,9001")), 400, "Hay boletos repetidos en la solicitud");
        assertError(purchase(901, valid("9001,9002,9003,9004,9005,9006")), 400, "Puedes reservar como máximo 5 boletos por solicitud");
        assertError(purchase(901, valid("-1")), 400, "Boleto no válido");
        assertError(purchase(901, valid("99999")), 400, "Ticket does not belong to this raffle");
        assertError(purchase(901, valid("9101")), 400, "Ticket does not belong to this raffle");
        assertError(purchase(901, "{\"ticketIds\":[9001]}"), 400, "Ingresa los datos del comprador");
        assertError(purchase(901, "{\"ticketIds\":[9001],"), 400, "Solicitud no válida");

        String nameError = "Ingresa un nombre válido (solo letras, entre 2 y 100 caracteres)";
        for (String name : new String[] {null, " ", "A", "Robert'); DROP TABLE buyers;--", "<script>x</script>", "a".repeat(101)}) {
            assertError(purchase(901, body("9001", name, "3001234567", null)), 400, nameError);
        }
        for (String phone : new String[] {null, "", "123", "300abc4567", "1234567890123456"}) {
            assertError(purchase(901, body("9001", "Laura", phone, null)), 400, "Ingresa un número de teléfono válido");
        }
        for (String email : new String[] {"sin-arroba", "a@b", "a b@c.com"}) {
            assertError(purchase(901, body("9001", "Laura", "3001234567", email)), 400, "Ingresa un correo electrónico válido");
        }
        assertUntouched();
    }

    @Test
    void unavailableTicketsRejectTheWholeRequest() throws Exception {
        assertThat(purchase(901, valid("9001")).getResponse().getStatus()).isEqualTo(200);
        ticketService.approveTicket(9001L);
        assertThat(purchase(901, body("9002", "Otra Persona", "3110000000", null)).getResponse().getStatus()).isEqualTo(200);

        // Vendido (aprobado) y reservado: la solicitud que los incluye no reserva tampoco el libre (9005)
        assertError(purchase(901, body("9005,9001", "Tercera", "3120000000", null)), 400, "Ticket 00 is no longer available");
        assertError(purchase(901, body("9005,9002", "Tercera", "3120000000", null)), 400, "Ticket 01 is no longer available");
        assertThat(state(9005)).isEqualTo("AVAILABLE:-:-");
        assertThat(available(901)).isEqualTo(8);
        assertThat(buyers()).isEqualTo(2);
    }

    @Test
    void closedOrUpcomingRafflesAreNotForSale() throws Exception {
        assertError(purchase(902, valid("9101")), 400, "La rifa no está disponible para la venta");
        assertError(purchase(903, valid("9201")), 400, "La rifa no está disponible para la venta");
        assertError(purchase(999, valid("9001")), 400, "No value present");
        assertThat(buyers()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE status <> 'AVAILABLE'", Integer.class)).isZero();
    }

    @Test
    void phoneCannotHoldMoreThanTheMaximumOfActiveReservations() throws Exception {
        assertThat(purchase(901, valid("9001,9002,9003")).getResponse().getStatus()).isEqualTo(200);
        assertThat(purchase(901, body("9004,9005", "Laura", "3001234567", null)).getResponse().getStatus()).isEqualTo(200);
        // Mismo teléfono escrito de otra forma: cuenta igual
        assertError(purchase(901, body("9006", "Laura", "(300) 123-4567", null)), 400,
                "Se alcanzó el máximo de boletos reservados permitidos para este teléfono en esta rifa");
        assertThat(state(9006)).isEqualTo("AVAILABLE:-:-");

        // Al aprobarse una reserva deja de contar como activa
        ticketService.approveTicket(9001L);
        assertThat(purchase(901, body("9006", "Laura", "3001234567", null)).getResponse().getStatus()).isEqualTo(200);
    }

    // ---------- sin CAPTCHA: los demás controles no se pueden saltar ----------

    @Test
    void purchaseNeedsNoCaptchaAndIgnoresAnOldCaptchaField() throws Exception {
        // Un frontend antiguo en caché podría seguir enviando captchaToken: se ignora
        String legacyBody = valid("9001").replace("}}", "},\"captchaToken\":\"x\"}");
        assertThat(purchase(901, legacyBody).getResponse().getStatus()).isEqualTo(200);
        assertThat(state(9001)).isEqualTo("RESERVED:Laura Gómez:PENDING");
    }

    @Test
    void invalidTokenOnPublicPurchaseIsTreatedAsAnonymousAndKeepsTheLimits() throws Exception {
        properties.setMaxRequestsPerIp(1);
        MvcResult first = mockMvc.perform(purchaseRequest(901, valid("9001")).header("Authorization", "Bearer token.invalido.x")).andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        // Un token inventado no cuenta como sesión: no se salta el límite por IP
        MvcResult second = mockMvc.perform(purchaseRequest(901, body("9002", "Otra Persona", "3110000000", null))
                .header("Authorization", "Bearer token.invalido.x")).andReturn();
        assertError(second, 429, "Demasiadas solicitudes. Intenta de nuevo en unos minutos.");
        assertThat(state(9002)).isEqualTo("AVAILABLE:-:-");
    }

    @Test
    void spoofedForwardedForDoesNotBypassTheIpLimit() throws Exception {
        // trusted-proxies = 0 (valor por defecto): X-Forwarded-For se ignora aunque cambie en cada solicitud
        properties.setMaxRequestsPerIp(2);
        for (int i = 0; i < 2; i++) {
            assertThat(mockMvc.perform(purchaseRequest(901, valid("")).header("X-Forwarded-For", "198.51.100." + i)).andReturn()
                    .getResponse().getStatus()).isEqualTo(400);
        }
        MvcResult third = mockMvc.perform(purchaseRequest(901, valid("9001")).header("X-Forwarded-For", "198.51.100.99")).andReturn();
        assertError(third, 429, "Demasiadas solicitudes. Intenta de nuevo en unos minutos.");
        assertUntouched();
    }

    // ---------- límites de solicitudes ----------

    @Test
    void requestsArelimitedPerIp() throws Exception {
        properties.setMaxRequestsPerIp(3);
        for (int i = 0; i < 3; i++) {
            // Las solicitudes rechazadas también cuentan
            assertThat(purchase(901, valid("")).getResponse().getStatus()).isEqualTo(400);
        }
        assertError(purchase(901, valid("9001")), 429, "Demasiadas solicitudes. Intenta de nuevo en unos minutos.");
        // Otra IP no se ve afectada
        MvcResult other = mockMvc.perform(purchaseRequest(901, valid("9001")).with(r -> { r.setRemoteAddr("10.0.0.2"); return r; })).andReturn();
        assertThat(other.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void requestsAreLimitedPerPhoneAcrossIps() throws Exception {
        properties.setMaxRequestsPerPhone(2);
        for (int i = 0; i < 2; i++) {
            int ip = i;
            assertThat(mockMvc.perform(purchaseRequest(901, valid(String.valueOf(9001 + i))).with(r -> { r.setRemoteAddr("10.0.1." + ip); return r; }))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);
        }
        MvcResult third = mockMvc.perform(purchaseRequest(901, body("9003", "Laura", "300-123-4567", null))
                .with(r -> { r.setRemoteAddr("10.0.1.9"); return r; })).andReturn();
        assertError(third, 429, "Demasiadas solicitudes. Intenta de nuevo en unos minutos.");
        assertThat(state(9003)).isEqualTo("AVAILABLE:-:-");
    }

    @Test
    void clientIpOnlyTrustsTheConfiguredProxies() {
        RafflePurchaseProperties props = new RafflePurchaseProperties();
        ClientIpResolver resolver = new ClientIpResolver(props);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.9.9.9");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.7");

        // Sin proxies de confianza se ignora la cabecera (el cliente la puede inventar)
        assertThat(resolver.resolve(request)).isEqualTo("10.9.9.9");
        // Un proxy: la última entrada es la que añadió el proxy; la primera ("1.1.1.1") la inventó el cliente
        props.setTrustedProxies(1);
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
        props.setTrustedProxies(2);
        assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
        // Cadena más corta de lo configurado: no se confía en nada que venga del cliente
        props.setTrustedProxies(3);
        assertThat(resolver.resolve(request)).isEqualTo("10.9.9.9");
    }

    // ---------- operaciones administrativas siguen protegidas ----------

    @Test
    void onlyThePurchaseIsPublic() throws Exception {
        assertThat(mockMvc.perform(put("/api/raffles/901/tickets/9001/approve")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(put("/api/raffles/901/tickets/9001/cancel")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/raffles/901/tickets/admin")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/raffles").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(put("/api/raffles/901/tickets/purchase").contentType(MediaType.APPLICATION_JSON).content(valid("9001")))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/raffles/901/tickets/9001/approve")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertUntouched();
    }

    // ---------- reservas y vencimiento ----------

    @Test
    void expiredReservationsAreReleasedOnceAndNeverAboveTheTotal() throws Exception {
        purchase(901, valid("9001,9002"));
        purchase(901, body("9003", "Otra Persona", "3110000000", null));
        assertThat(available(901)).isEqualTo(7);
        expire(9001, 9002);

        assertThat(expiryJob.releaseExpiredReservations()).isEqualTo(2);
        assertThat(state(9001)).isEqualTo("AVAILABLE:-:-");
        assertThat(state(9002)).isEqualTo("AVAILABLE:-:-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE id IN (9001, 9002) AND reservation_expires_at IS NULL", Integer.class)).isEqualTo(2);
        assertThat(state(9003)).isEqualTo("RESERVED:Otra Persona:PENDING");
        assertThat(available(901)).isEqualTo(9);

        // Repetir no vuelve a incrementar
        assertThat(expiryJob.releaseExpiredReservations()).isZero();
        assertThat(available(901)).isEqualTo(9);

        // Contador ya inconsistente (en el máximo): liberar no lo lleva por encima del total
        jdbc.update("UPDATE raffles SET available_tickets = 10 WHERE id = 901");
        expire(9003);
        assertThat(expiryJob.releaseExpiredReservations()).isEqualTo(1);
        assertThat(available(901)).isEqualTo(10);
    }

    @Test
    void approvedReservationsAreNeverReleased() throws Exception {
        purchase(901, valid("9001,9002"));
        Long buyerId = jdbc.queryForObject("SELECT id FROM buyers", Long.class);
        mockMvc.perform(put("/api/raffles/901/tickets/9001/approve").param("buyerId", buyerId.toString()).header("Authorization", bearer()));
        assertThat(state(9001)).isEqualTo("SOLD:Laura Gómez:CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT reservation_expires_at IS NULL FROM tickets WHERE id = 9001", Boolean.class)).isTrue();

        // Aunque un boleto vendido tuviera una fecha pasada, la tarea solo toca reservas
        expire(9001, 9002);
        assertThat(expiryJob.releaseExpiredReservations()).isEqualTo(1);
        assertThat(state(9001)).isEqualTo("SOLD:Laura Gómez:CONFIRMED");
        assertThat(state(9002)).isEqualTo("AVAILABLE:-:-");
        assertThat(available(901)).isEqualTo(9);
    }

    @Test
    void legacyReservationsWithoutExpiryAreKept() {
        jdbc.update("INSERT INTO buyers (id, name, phone, payment_status) VALUES (1, 'Antigua', '3000000000', 'PENDING')");
        jdbc.update("UPDATE tickets SET status = 'RESERVED', buyer_id = 1 WHERE id = 9001");
        assertThat(expiryJob.releaseExpiredReservations()).isZero();
        assertThat(state(9001)).isEqualTo("RESERVED:Antigua:PENDING");
    }

    @Test
    void purchaseReleasesAnExpiredReservationWithoutWaitingForTheJob() throws Exception {
        purchase(901, valid("9001"));
        expire(9001);
        assertThat(purchase(901, body("9001", "Nuevo Comprador", "3110000000", null)).getResponse().getStatus()).isEqualTo(200);
        assertThat(state(9001)).isEqualTo("RESERVED:Nuevo Comprador:PENDING");
        // Liberado (+1) y vuelto a reservar (-1)
        assertThat(available(901)).isEqualTo(9);
    }

    @Test
    void cancellingAReservationRestoresAvailabilityExactlyOnce() throws Exception {
        purchase(901, valid("9001"));
        Long buyerId = jdbc.queryForObject("SELECT id FROM buyers", Long.class);
        assertThat(available(901)).isEqualTo(9);
        String token = bearer();
        for (int i = 0; i < 2; i++) {
            MvcResult cancel = mockMvc.perform(put("/api/raffles/901/tickets/9001/cancel").param("buyerId", buyerId.toString())
                    .header("Authorization", token)).andReturn();
            // La segunda vez el boleto ya está libre: el buyerId ya no coincide, pero cancelar un boleto libre no hace nada
            assertThat(cancel.getResponse().getStatus()).isEqualTo(200);
            assertThat(available(901)).isEqualTo(10);
        }
        assertThat(state(9001)).isEqualTo("AVAILABLE:-:-");
    }

    @Test
    void adminActionsOnAStaleViewDoNotTouchAnotherBuyersReservation() throws Exception {
        purchase(901, valid("9001"));
        Long firstBuyer = jdbc.queryForObject("SELECT id FROM buyers", Long.class);
        expire(9001);
        purchase(901, body("9001", "Nuevo Comprador", "3110000000", null));
        String token = bearer();

        for (String action : List.of("approve", "cancel")) {
            MvcResult stale = mockMvc.perform(put("/api/raffles/901/tickets/9001/" + action).param("buyerId", firstBuyer.toString())
                    .header("Authorization", token)).andReturn();
            assertError(stale, 400, "La reserva del boleto 00 cambió. Recarga el panel e inténtalo de nuevo.");
        }
        assertThat(state(9001)).isEqualTo("RESERVED:Nuevo Comprador:PENDING");

        // Aprobar una reserva que ya venció y se liberó
        expire(9001);
        expiryJob.releaseExpiredReservations();
        assertError(mockMvc.perform(put("/api/raffles/901/tickets/9001/approve").header("Authorization", token)).andReturn(), 400,
                "La reserva del boleto 00 ya no está vigente");
        assertThat(state(9001)).isEqualTo("AVAILABLE:-:-");
    }

    // ---------- concurrencia ----------

    /** Lanza las tareas a la vez y devuelve cuántas terminaron sin error de negocio. */
    private int runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        List<Future<Boolean>> results = new ArrayList<>();
        for (Runnable task : tasks) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    task.run();
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) successes++;
        }
        pool.shutdown();
        return successes;
    }

    @Test
    void twoBuyersForTheSameTicketOnlyOneWins() throws Exception {
        int successes = runConcurrently(List.of(
                () -> ticketService.purchaseTickets(901L, request("Primera", "3000000001", 9001L)),
                () -> ticketService.purchaseTickets(901L, request("Segunda", "3000000002", 9001L))));
        assertThat(successes).isEqualTo(1);
        assertThat(state(9001)).matches("RESERVED:(Primera|Segunda):PENDING");
        assertThat(available(901)).isEqualTo(9);
        assertThat(buyers()).isEqualTo(1);
    }

    @Test
    void overlappingRequestsAreAllOrNothing() throws Exception {
        // 8 compradores piden pares que se solapan; cada solicitud reserva sus dos boletos o ninguno
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            long first = 9001 + (i % 5);
            tasks.add(() -> ticketService.purchaseTickets(901L, request("Comprador " + first, "30000000" + (10 + first % 100), first, first + 1)));
        }
        int successes = runConcurrently(tasks);

        int reserved = jdbc.queryForObject("SELECT count(*) FROM tickets WHERE raffle_id = 901 AND status = 'RESERVED'", Integer.class);
        assertThat(successes).isPositive();
        assertThat(reserved).isEqualTo(successes * 2);
        assertThat(available(901)).isEqualTo(10 - reserved);
        // Cada comprador tiene exactamente dos boletos: ninguna reserva parcial
        assertThat(jdbc.queryForList("SELECT count(*) FROM tickets WHERE buyer_id IS NOT NULL GROUP BY buyer_id", Integer.class))
                .hasSize(successes).allMatch(count -> count == 2);
        assertThat(buyers()).isEqualTo(successes);
    }

    /**
     * Ejecuta {@code first} y hace flush (bloqueos tomados) en una transacción abierta; lanza {@code second} en otro hilo
     * y confirma la primera cuando la segunda está esperando un bloqueo o ya terminó. Devuelve el error de {@code second}.
     */
    private Throwable interleave(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> concurrent = new AtomicReference<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            first.run();
            entityManager.flush();
            concurrent.set(pool.submit(second));
            long deadline = System.currentTimeMillis() + 10_000;
            while (!concurrent.get().isDone() && jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                    + "AND wait_event_type = 'Lock'", Integer.class) == 0) {
                if (System.currentTimeMillis() > deadline) throw new AssertionError("La operación concurrente ni terminó ni quedó bloqueada");
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        try {
            concurrent.get().get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } finally {
            pool.shutdown();
        }
    }

    private void reserveExpired(long ticketId, String name) {
        ticketService.purchaseTickets(901L, request(name, "3009999999", ticketId));
        expire(ticketId);
    }

    @Test
    void purchaseWaitsForTheExpiryJobAndThenGetsTheTicket() throws Exception {
        reserveExpired(9001, "Vencida");
        Throwable error = interleave(() -> ticketService.releaseExpiredReservations(901L, Instant.now()),
                () -> ticketService.purchaseTickets(901L, request("Nuevo", "3110000000", 9001L)));
        assertThat(error).isNull();
        assertThat(state(9001)).isEqualTo("RESERVED:Nuevo:PENDING");
        assertThat(available(901)).isEqualTo(9);
    }

    @Test
    void expiryJobAfterAPurchaseFindsNothingToRelease() throws Exception {
        reserveExpired(9001, "Vencida");
        AtomicReference<Integer> released = new AtomicReference<>();
        Throwable error = interleave(() -> ticketService.purchaseTickets(901L, request("Nuevo", "3110000000", 9001L)),
                () -> released.set(ticketService.releaseExpiredReservations(901L, Instant.now())));
        assertThat(error).isNull();
        // La compra liberó la vencida y reservó el boleto; la tarea ya no tiene nada que hacer
        assertThat(released.get()).isZero();
        assertThat(state(9001)).isEqualTo("RESERVED:Nuevo:PENDING");
        assertThat(available(901)).isEqualTo(9);
    }

    @Test
    void approvalBeforeTheExpiryJobWins() throws Exception {
        reserveExpired(9001, "Vencida");
        AtomicReference<Integer> released = new AtomicReference<>();
        Throwable error = interleave(() -> ticketService.approveTicket(9001L),
                () -> released.set(ticketService.releaseExpiredReservations(901L, Instant.now())));
        assertThat(error).isNull();
        assertThat(released.get()).isZero();
        assertThat(state(9001)).isEqualTo("SOLD:Vencida:CONFIRMED");
        assertThat(available(901)).isEqualTo(9);
    }

    @Test
    void approvalAfterTheExpiryJobIsRejected() throws Exception {
        reserveExpired(9001, "Vencida");
        Throwable error = interleave(() -> ticketService.releaseExpiredReservations(901L, Instant.now()),
                () -> ticketService.approveTicket(9001L));
        assertThat(error).isInstanceOf(IllegalStateException.class).hasMessage("La reserva del boleto 00 ya no está vigente");
        assertThat(state(9001)).isEqualTo("AVAILABLE:-:-");
        assertThat(available(901)).isEqualTo(10);
    }

    @Test
    void cancellationAndExpiryRestoreTheTicketOnlyOnce() throws Exception {
        reserveExpired(9001, "Vencida");
        AtomicReference<Integer> released = new AtomicReference<>();
        Throwable error = interleave(() -> ticketService.cancelTicket(9001L),
                () -> released.set(ticketService.releaseExpiredReservations(901L, Instant.now())));
        assertThat(error).isNull();
        assertThat(released.get()).isZero();
        assertThat(state(9001)).isEqualTo("AVAILABLE:-:-");
        assertThat(available(901)).isEqualTo(10);
    }

    @Test
    void cancellationDuringAnotherPurchaseKeepsTheCounter() throws Exception {
        // Sin bloquear la rifa al empezar, la cancelación leería el contador antes del commit de la compra y lo
        // sobrescribiría con el valor anterior (lost update)
        ticketService.purchaseTickets(901L, request("Primera", "3000000001", 9001L));
        Throwable error = interleave(() -> ticketService.purchaseTickets(901L, request("Segunda", "3000000002", 9002L)),
                () -> ticketService.cancelTicket(9001L));
        assertThat(error).isNull();
        assertThat(state(9001)).isEqualTo("AVAILABLE:-:-");
        assertThat(state(9002)).isEqualTo("RESERVED:Segunda:PENDING");
        assertThat(available(901)).isEqualTo(9);
    }

    @Test
    void expiryJobDuringRaffleDeletionDoesNotFail() throws Exception {
        reserveExpired(9001, "Vencida");
        AtomicReference<Integer> released = new AtomicReference<>();
        Throwable error = interleave(() -> {
                    jdbc.update("DELETE FROM tickets WHERE raffle_id = 901");
                    jdbc.update("DELETE FROM raffles WHERE id = 901");
                },
                () -> released.set(ticketService.releaseExpiredReservations(901L, Instant.now())));
        assertThat(error).isNull();
        assertThat(released.get()).isZero();
    }

    @Test
    void repeatedConcurrentExpiryRunsReleaseEachTicketOnce() throws Exception {
        // Se vencen después de reservar todas: una compra libera por sí misma las vencidas de su rifa
        ticketService.purchaseTickets(901L, request("A", "3009999999", 9001L));
        ticketService.purchaseTickets(901L, request("B", "3008888888", 9002L, 9003L));
        expire(9001, 9002, 9003);
        List<Integer> released = java.util.Collections.synchronizedList(new ArrayList<>());
        runConcurrently(List.of(
                () -> released.add(expiryJob.releaseExpiredReservations()),
                () -> released.add(expiryJob.releaseExpiredReservations()),
                () -> released.add(expiryJob.releaseExpiredReservations())));
        assertThat(released.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
        assertThat(available(901)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE status = 'AVAILABLE' AND raffle_id = 901", Integer.class)).isEqualTo(10);
    }

    @Test
    void noPersonalDataIsLeakedInErrorsAboutOtherBuyers() throws Exception {
        purchase(901, valid("9001"));
        MvcResult taken = purchase(901, body("9001", "Intruso", "3110000000", null));
        assertError(taken, 400, "Ticket 00 is no longer available");
        assertThat(text(taken)).doesNotContain("Laura", "3001234567");
        assertThat(jdbc.queryForList("SELECT name FROM buyers", String.class).stream().collect(Collectors.toList())).containsExactly("Laura Gómez");
    }
}
