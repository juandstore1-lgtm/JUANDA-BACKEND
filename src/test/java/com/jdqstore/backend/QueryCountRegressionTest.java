package com.jdqstore.backend;

import com.jdqstore.backend.dto.BuyerDTO;
import com.jdqstore.backend.dto.RaffleDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.entity.User;
import com.jdqstore.backend.repository.TicketRepository;
import com.jdqstore.backend.repository.UserRepository;
import com.jdqstore.backend.security.JwtService;
import com.jdqstore.backend.security.UserDetailsImpl;
import com.jdqstore.backend.service.RaffleService;
import com.jdqstore.backend.service.TicketService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Pruebas de caracterización: fijan el JSON exacto (orden de arrays incluido) que devuelven hoy
 * los endpoints optimizados y el número de sentencias SQL que ejecutan, contra un PostgreSQL local
 * (ver src/test/resources/config/application.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
class QueryCountRegressionTest {

    private static final Logger log = LoggerFactory.getLogger(QueryCountRegressionTest.class);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private TicketService ticketService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;
    @Autowired private RaffleService raffleService;
    // Espía que delega en el repositorio real; solo se intercepta en las pruebas de borrado de rifas
    @SpyBean private TicketRepository ticketRepository;

    private Statistics stats;

    @BeforeEach
    void seed() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        jdbc.execute("TRUNCATE product_sizes, product_colors, product_images, product_tags, product_stores, products, "
                + "tickets, buyers, raffles, contest_winners, contest_participants, contests, user_sedes, sedes CASCADE");
        jdbc.update("DELETE FROM users WHERE email <> 'admin@jdqstore.com'");

        jdbc.update("INSERT INTO sedes (id, name, created_at) VALUES (11, 'Sede Norte', '2026-01-01 00:00:00'), (12, 'Sede Sur', '2026-01-01 00:00:00')");

        // Productos: elementos insertados deliberadamente en orden no alfabético para fijar el orden actual.
        jdbc.update("INSERT INTO products (id, name, price, old_price, discount_percentage, description, category, status, display_order, created_at) VALUES "
                + "(101, 'Camisa', 50000.00, 60000.00, 10, 'Desc 1', 'Camisas', 'AVAILABLE', 2, '2026-01-01 00:00:00'),"
                + "(102, 'Pantalon', 80000.00, NULL, NULL, NULL, 'Pantalones', 'OUT_OF_STOCK', 1, '2026-01-01 00:00:00'),"
                + "(103, 'Gorra', 20000.00, NULL, NULL, 'Sin colecciones', NULL, 'AVAILABLE', NULL, '2026-01-01 00:00:00')");
        jdbc.update("INSERT INTO product_sizes (product_id, size) VALUES (101, 'M'), (102, 'XL'), (101, 'S'), (101, 'L'), (102, '32')");
        jdbc.update("INSERT INTO product_colors (product_id, color) VALUES (102, 'Azul'), (101, 'Rojo'), (101, 'Negro')");
        jdbc.update("INSERT INTO product_images (product_id, image_url) VALUES (101, 'img-b'), (102, 'img-p1'), (101, 'img-a'), (101, 'img-b')");
        jdbc.update("INSERT INTO product_tags (product_id, tag) VALUES (101, 'nuevo'), (101, 'oferta')");
        jdbc.update("INSERT INTO product_stores (product_id, sede_id) VALUES (101, 12), (101, 11), (102, 11)");

        // Rifa con boletos: sin comprador, comprador repetido y varios compradores.
        jdbc.update("INSERT INTO raffles (id, name, prize, ticket_price, total_tickets, available_tickets, status, created_at) "
                + "VALUES (301, 'Rifa', 'Premio', 5000.00, 6, 6, 'ACTIVE', '2026-01-01 00:00:00')");
        jdbc.update("INSERT INTO buyers (id, name, phone, email, payment_status, payment_method, notes, created_at) VALUES "
                + "(401, 'Ana', '300', 'ana@x.com', 'PENDING', NULL, NULL, '2026-02-01 10:00:00'),"
                + "(402, 'Luis', '301', NULL, 'CONFIRMED', 'NEQUI', 'nota', '2026-02-02 10:00:00')");
        jdbc.update("INSERT INTO tickets (id, raffle_id, ticket_number, status, buyer_id, updated_at) VALUES "
                + "(506, 301, '05', 'AVAILABLE', NULL, NULL),"
                + "(501, 301, '00', 'RESERVED', 401, '2026-02-01 10:00:00'),"
                + "(503, 301, '02', 'SOLD', 402, '2026-02-02 10:00:00'),"
                + "(502, 301, '01', 'RESERVED', 401, '2026-02-01 10:00:00'),"
                + "(504, 301, '03', 'AVAILABLE', NULL, NULL),"
                + "(505, 301, '04', 'AVAILABLE', NULL, NULL)");

        Long storeAdminRole = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'STORE_ADMIN'", Long.class);
        jdbc.update("INSERT INTO users (id, name, email, password, role_id, is_active, created_at) VALUES "
                + "(201, 'Uno', 'uno@x.com', 'x', ?, true, '2026-01-01 00:00:00'),"
                + "(202, 'Dos', 'dos@x.com', 'x', ?, false, '2026-01-01 00:00:00')", storeAdminRole, storeAdminRole);
        jdbc.update("INSERT INTO user_sedes (user_id, sede_id) VALUES (201, 12), (201, 11), (202, 11)");

        jdbc.update("INSERT INTO contests (id, title, status, form_enabled) VALUES (601, 'Concurso', 'FINISHED', false)");
        jdbc.update("INSERT INTO contest_participants (id, contest_id, full_name, email, status) VALUES "
                + "(701, 601, 'P1', 'p1@x.com', 'PARTICIPANT'), (702, 601, 'P2', 'p2@x.com', 'WINNER'), (703, 601, 'P3', 'p3@x.com', 'PARTICIPANT')");
        jdbc.update("INSERT INTO contest_winners (id, contest_id, participant_id, selected_at) VALUES (801, 601, 702, '2026-03-01 00:00:00')");
    }

    private long count(String label, RequestBuilder request, String expectedJson) throws Exception {
        stats.clear();
        MvcResult result = mockMvc.perform(request).andReturn();
        long statements = stats.getPrepareStatementCount();
        log.info("[query-count] {} -> {} sentencias, HTTP {}", label, statements, result.getResponse().getStatus());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        if (expectedJson != null) {
            JSONAssert.assertEquals(expectedJson, result.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
        }
        return statements;
    }

    private String bearer() {
        User admin = userRepository.findByEmail("admin@jdqstore.com").orElseThrow();
        return "Bearer " + jwtService.generateToken(new UserDetailsImpl(admin));
    }

    private static final String PRODUCT_101 = "{\"id\":101,\"name\":\"Camisa\",\"price\":50000.00,\"oldPrice\":60000.00,\"discountPercentage\":10,"
            + "\"description\":\"Desc 1\",\"category\":\"Camisas\",\"sizes\":[\"M\",\"S\",\"L\"],\"colors\":[\"Rojo\",\"Negro\"],"
            + "\"images\":[\"img-b\",\"img-a\",\"img-b\"],\"storeIds\":[12,11],\"status\":\"AVAILABLE\",\"tags\":[\"nuevo\",\"oferta\"],\"order\":2}";
    private static final String PRODUCT_102 = "{\"id\":102,\"name\":\"Pantalon\",\"price\":80000.00,\"oldPrice\":null,\"discountPercentage\":null,"
            + "\"description\":null,\"category\":\"Pantalones\",\"sizes\":[\"XL\",\"32\"],\"colors\":[\"Azul\"],"
            + "\"images\":[\"img-p1\"],\"storeIds\":[11],\"status\":\"OUT_OF_STOCK\",\"tags\":[],\"order\":1}";
    private static final String PRODUCT_103 = "{\"id\":103,\"name\":\"Gorra\",\"price\":20000.00,\"oldPrice\":null,\"discountPercentage\":null,"
            + "\"description\":\"Sin colecciones\",\"category\":null,\"sizes\":[],\"colors\":[],"
            + "\"images\":[],\"storeIds\":[],\"status\":\"AVAILABLE\",\"tags\":[],\"order\":null}";

    @Test
    void perf01_products() throws Exception {
        long all = count("GET /api/products (3 productos)", get("/api/products"),
                "[" + PRODUCT_101 + "," + PRODUCT_102 + "," + PRODUCT_103 + "]");
        long one = count("GET /api/products/101", get("/api/products/101"), PRODUCT_101);
        assertThat(all).isEqualTo(6);
        assertThat(one).isEqualTo(6);
    }

    @Test
    void perf01_productWritesKeepResponseContract() throws Exception {
        String token = bearer(); // SEC-B: las escrituras requieren GLOBAL_ADMIN o STORE_ADMIN
        MvcResult created = mockMvc.perform(post("/api/products").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nuevo\",\"price\":10,\"status\":\"AVAILABLE\"}")).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("{\"id\":0,\"name\":\"Nuevo\",\"price\":10,\"oldPrice\":null,\"discountPercentage\":null,\"description\":null,"
                        + "\"category\":null,\"sizes\":null,\"colors\":null,\"images\":null,\"storeIds\":[],\"status\":\"AVAILABLE\",\"tags\":null,\"order\":null}",
                created.getResponse().getContentAsString(StandardCharsets.UTF_8),
                new CustomComparator(JSONCompareMode.STRICT, new Customization("id", (a, b) -> true)));

        MvcResult updated = mockMvc.perform(put("/api/products/102").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pantalon 2\",\"price\":1,\"status\":\"AVAILABLE\",\"sizes\":[\"L\",\"S\"],\"colors\":[],"
                        + "\"images\":[\"z\",\"a\"],\"tags\":[\"t\"],\"storeIds\":[12,11]}")).andReturn();
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("{\"id\":102,\"name\":\"Pantalon 2\",\"price\":1,\"oldPrice\":null,\"discountPercentage\":null,\"description\":null,"
                        + "\"category\":null,\"sizes\":[\"L\",\"S\"],\"colors\":[],\"images\":[\"z\",\"a\"],\"storeIds\":[11,12],\"status\":\"AVAILABLE\",\"tags\":[\"t\"],\"order\":null}",
                updated.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);

        // La lectura posterior devuelve lo persistido, con [] para colecciones vacías
        count("GET /api/products/102 tras update", get("/api/products/102"),
                "{\"id\":102,\"name\":\"Pantalon 2\",\"price\":1.00,\"oldPrice\":null,\"discountPercentage\":null,\"description\":null,"
                        + "\"category\":null,\"sizes\":[\"L\",\"S\"],\"colors\":[],\"images\":[\"z\",\"a\"],\"storeIds\":[11,12],\"status\":\"AVAILABLE\",\"tags\":[\"t\"],\"order\":null}");

        MvcResult missing = mockMvc.perform(get("/api/products/999")).andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"No value present\"}", missing.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
    }

    @Test
    void perf08_failedLoginKeepsResponse() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@jdqstore.com\",\"password\":\"incorrecta\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Bad credentials\"}", result.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
    }

    @Test
    void perf05_tickets() throws Exception {
        String ana = "{\"id\":401,\"name\":\"Ana\",\"phone\":\"300\",\"email\":\"ana@x.com\",\"paymentStatus\":\"PENDING\",\"paymentMethod\":null,\"notes\":null,\"createdAt\":\"2026-02-01T10:00:00\"}";
        String luis = "{\"id\":402,\"name\":\"Luis\",\"phone\":\"301\",\"email\":null,\"paymentStatus\":\"CONFIRMED\",\"paymentMethod\":\"NEQUI\",\"notes\":\"nota\",\"createdAt\":\"2026-02-02T10:00:00\"}";
        String withBuyers = "["
                + "{\"id\":501,\"raffleId\":301,\"ticketNumber\":\"00\",\"status\":\"RESERVED\",\"buyer\":" + ana + ",\"updatedAt\":\"2026-02-01T10:00:00\"},"
                + "{\"id\":502,\"raffleId\":301,\"ticketNumber\":\"01\",\"status\":\"RESERVED\",\"buyer\":" + ana + ",\"updatedAt\":\"2026-02-01T10:00:00\"},"
                + "{\"id\":503,\"raffleId\":301,\"ticketNumber\":\"02\",\"status\":\"SOLD\",\"buyer\":" + luis + ",\"updatedAt\":\"2026-02-02T10:00:00\"},"
                + "{\"id\":504,\"raffleId\":301,\"ticketNumber\":\"03\",\"status\":\"AVAILABLE\",\"buyer\":null,\"updatedAt\":null},"
                + "{\"id\":505,\"raffleId\":301,\"ticketNumber\":\"04\",\"status\":\"AVAILABLE\",\"buyer\":null,\"updatedAt\":null},"
                + "{\"id\":506,\"raffleId\":301,\"ticketNumber\":\"05\",\"status\":\"AVAILABLE\",\"buyer\":null,\"updatedAt\":null}]";
        // SEC-C: la vista pública conserva todos los campos salvo el comprador, que pasa a null
        String publicView = withBuyers.replace(ana, "null").replace(luis, "null");
        long n = count("GET /api/raffles/301/tickets (6 boletos, vista pública)", get("/api/raffles/301/tickets"), publicView);
        assertThat(n).isEqualTo(1);
        // Vista de administración: el JSON anterior completo, en una consulta (+1 del filtro JWT)
        long admin = count("GET /api/raffles/301/tickets/admin (6 boletos, 2 compradores)",
                get("/api/raffles/301/tickets/admin").header("Authorization", bearer()), withBuyers);
        assertThat(admin).isEqualTo(2);
        count("GET /api/raffles/999/tickets (rifa inexistente)", get("/api/raffles/999/tickets"), "[]");
    }

    @Test
    void perf08_authenticatedRequests() throws Exception {
        String token = bearer();
        String me = "{\"id\":1,\"name\":\"Administrador\",\"email\":\"admin@jdqstore.com\",\"password\":null,"
                + "\"roleName\":\"GLOBAL_ADMIN\",\"storeIds\":[],\"isActive\":true}";
        long filterOnly = count("GET /api/roulettecoupons con JWT", get("/api/roulettecoupons").header("Authorization", token), "[]");
        long meCount = count("GET /api/users/me con JWT", get("/api/users/me").header("Authorization", token), me);
        long login = count("POST /api/auth/login", post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@jdqstore.com\",\"password\":\"admin123\"}"), null);
        // 1 consulta del filtro JWT + 1 del findAll de cupones
        assertThat(filterOnly).isEqualTo(2);
        assertThat(meCount).isEqualTo(3);
        assertThat(login).isEqualTo(3);
    }

    @Test
    void perf09_users() throws Exception {
        String token = bearer();
        String expected = "["
                + "{\"id\":1,\"name\":\"Administrador\",\"email\":\"admin@jdqstore.com\",\"password\":null,\"roleName\":\"GLOBAL_ADMIN\",\"storeIds\":[],\"isActive\":true},"
                + "{\"id\":201,\"name\":\"Uno\",\"email\":\"uno@x.com\",\"password\":null,\"roleName\":\"STORE_ADMIN\",\"storeIds\":[12,11],\"isActive\":true},"
                + "{\"id\":202,\"name\":\"Dos\",\"email\":\"dos@x.com\",\"password\":null,\"roleName\":\"STORE_ADMIN\",\"storeIds\":[11],\"isActive\":false}]";
        long n = count("GET /api/users (3 usuarios)", get("/api/users").header("Authorization", token), expected);
        // filtro JWT (1) + findAll (1) + roles secundarios (2) + sedes en lote (1)
        assertThat(n).isEqualTo(5);
    }

    @Test
    void perf10_resetContest() throws Exception {
        String token = bearer();
        // Otro concurso con participante y ganador: el reset no debe tocarlo
        jdbc.update("INSERT INTO contests (id, title, status, form_enabled) VALUES (602, 'Otro', 'FINISHED', false)");
        jdbc.update("INSERT INTO contest_participants (id, contest_id, full_name, email, status) VALUES (704, 602, 'Q1', 'q1@x.com', 'WINNER')");
        jdbc.update("INSERT INTO contest_winners (id, contest_id, participant_id, selected_at) VALUES (802, 602, 704, '2026-03-01 00:00:00')");
        long n = count("DELETE /api/contests/admin/601/reset (3 participantes)",
                delete("/api/contests/admin/601/reset").header("Authorization", token),
                "{\"message\":\"Concurso reseteado con éxito. Se eliminaron todos los participantes y registros asociados.\"}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contest_participants WHERE contest_id = 601", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contest_winners WHERE contest_id = 601", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM contests WHERE id = 601", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT form_enabled FROM contests WHERE id = 601", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contest_participants WHERE contest_id = 602", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contest_winners WHERE contest_id = 602", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM contests WHERE id = 602", String.class)).isEqualTo("FINISHED");
        // Reset de un concurso inexistente: 404 sin cuerpo, como antes
        MvcResult missing = mockMvc.perform(delete("/api/contests/admin/999/reset").header("Authorization", token)).andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        // filtro JWT (1) + findById (1) + delete ganador (1) + delete participantes (1) + update concurso (1)
        assertThat(n).isEqualTo(5);
    }

    // Teléfono válido: la compra ahora valida y normaliza los datos del comprador
    private static final String PURCHASE_BODY = "{\"ticketIds\":[%s],\"buyer\":{\"name\":\"Nuevo\",\"phone\":\"3009990000\",\"email\":\"n@x.com\"}}";

    private MvcResult purchase(String token, long raffleId, String ticketIds) throws Exception {
        return mockMvc.perform(post("/api/raffles/" + raffleId + "/tickets/purchase").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(PURCHASE_BODY.formatted(ticketIds))).andReturn();
    }

    private int availableTickets() {
        return jdbc.queryForObject("SELECT available_tickets FROM raffles WHERE id = 301", Integer.class);
    }

    private int buyers() {
        return jdbc.queryForObject("SELECT count(*) FROM buyers", Integer.class);
    }

    @Test
    void perf11_purchaseHappyPath() throws Exception {
        String token = bearer();
        stats.clear();
        MvcResult result = purchase(token, 301, "504,505");
        log.info("[query-count] POST /api/raffles/301/tickets/purchase (2 boletos) -> {} sentencias, HTTP {}",
                stats.getPrepareStatementCount(), result.getResponse().getStatus());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // Antes respondía sin cuerpo; ahora confirma los boletos reservados y su vencimiento (sin datos del comprador)
        JSONAssert.assertEquals("{\"raffleId\":301,\"ticketIds\":[504,505],\"ticketNumbers\":[\"03\",\"04\"],\"status\":\"RESERVED\"}",
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.LENIENT);
        assertThat(result.getResponse().getContentAsString()).contains("\"reservedUntil\"").doesNotContain("Nuevo", "3009990000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE id IN (504, 505) AND reservation_expires_at > now()", Integer.class)).isEqualTo(2);
        assertThat(availableTickets()).isEqualTo(4);
        Long buyerId = jdbc.queryForObject("SELECT id FROM buyers WHERE name = 'Nuevo'", Long.class);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM buyers WHERE id = ?", String.class, buyerId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForList("SELECT status || ':' || buyer_id FROM tickets WHERE id IN (504, 505) ORDER BY id", String.class))
                .containsExactly("RESERVED:" + buyerId, "RESERVED:" + buyerId);
    }

    @Test
    void perf11_purchaseErrorsRollBack() throws Exception {
        String token = bearer();

        MvcResult taken = purchase(token, 301, "504,501");
        assertThat(taken.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Ticket 00 is no longer available\"}",
                taken.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);

        jdbc.update("INSERT INTO raffles (id, name, prize, ticket_price, total_tickets, available_tickets, status) VALUES (302, 'Otra', 'P', 1.00, 1, 1, 'ACTIVE')");
        jdbc.update("INSERT INTO tickets (id, raffle_id, ticket_number, status) VALUES (601, 302, '00', 'AVAILABLE')");
        MvcResult foreign = purchase(token, 301, "601");
        assertThat(foreign.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Ticket does not belong to this raffle\"}",
                foreign.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);

        MvcResult missingRaffle = purchase(token, 999, "504");
        assertThat(missingRaffle.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"No value present\"}",
                missingRaffle.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);

        // Rollback completo: ni comprador nuevo, ni boletos tocados, ni contador modificado
        assertThat(buyers()).isEqualTo(2);
        assertThat(availableTickets()).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = 504", String.class)).isEqualTo("AVAILABLE");
    }

    @Test
    void perf11_concurrentPurchasesOfSameTicket() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                TicketPurchaseRequest request = new TicketPurchaseRequest();
                BuyerDTO buyer = new BuyerDTO();
                buyer.setName("Concurrente " + n);
                buyer.setPhone("3" + n);
                request.setBuyer(buyer);
                request.setTicketIds(List.of(504L));
                start.await();
                try {
                    ticketService.purchaseTickets(301L, request);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }
        int successes = 0;
        for (Future<Boolean> r : results) {
            if (r.get(60, TimeUnit.SECONDS)) successes++;
        }
        pool.shutdown();
        log.info("[concurrency] compras simultáneas del boleto 504: {} exitosas de {}; available_tickets = {}",
                successes, threads, availableTickets());

        assertThat(successes).isEqualTo(1);
        assertThat(availableTickets()).isEqualTo(5);
        assertThat(buyers()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT b.name FROM tickets t JOIN buyers b ON b.id = t.buyer_id WHERE t.id = 504", String.class))
                .startsWith("Concurrente ");
    }

    @Test
    void perf11_concurrentPurchasesOfDifferentTickets() throws Exception {
        List<Long> ticketIds = List.of(504L, 505L, 506L);
        ExecutorService pool = Executors.newFixedThreadPool(ticketIds.size());
        CyclicBarrier start = new CyclicBarrier(ticketIds.size());
        List<Future<?>> results = new ArrayList<>();
        for (Long ticketId : ticketIds) {
            results.add(pool.submit(() -> {
                TicketPurchaseRequest request = new TicketPurchaseRequest();
                BuyerDTO buyer = new BuyerDTO();
                buyer.setName("B" + ticketId);
                buyer.setPhone("3");
                request.setBuyer(buyer);
                request.setTicketIds(List.of(ticketId));
                start.await();
                ticketService.purchaseTickets(301L, request);
                return null;
            }));
        }
        for (Future<?> r : results) {
            r.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        log.info("[concurrency] compras simultáneas de 3 boletos distintos: available_tickets = {}", availableTickets());

        // Todas deben completarse y el contador debe reflejar las tres compras (sin lost update)
        assertThat(availableTickets()).isEqualTo(3);
    }

    // --- Compra contra aprobación / cancelación -------------------------------------------------
    // Intercalado forzado: la compra se ejecuta y se hace flush (bloqueos tomados) en una transacción
    // abierta; la operación administrativa arranca en otro hilo y, cuando está esperando un bloqueo,
    // se confirma la compra. El estado final debe equivaler a una ejecución en serie.

    private TicketPurchaseRequest purchaseRequest(long ticketId, String buyerName) {
        TicketPurchaseRequest request = new TicketPurchaseRequest();
        BuyerDTO buyer = new BuyerDTO();
        buyer.setName(buyerName);
        buyer.setPhone("3");
        request.setBuyer(buyer);
        request.setTicketIds(List.of(ticketId));
        return request;
    }

    /** Espera a que la operación concurrente quede bloqueada esperando un lock o termine sin bloquearse. */
    private void awaitBlockedOrDone(Future<?> operation) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!operation.isDone() && jdbc.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class) == 0) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("La operación concurrente ni terminó ni quedó esperando un bloqueo");
            }
            Thread.sleep(20);
        }
    }

    /**
     * Ejecuta {@code first} y hace flush (sus bloqueos quedan tomados) en una transacción abierta; lanza {@code second}
     * en otro hilo y confirma la primera cuando la segunda está bloqueada o ya terminó.
     * Devuelve la excepción de negocio lanzada por {@code second}, o null si terminó bien.
     */
    private Throwable interleave(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> concurrent = new AtomicReference<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            first.run();
            entityManager.flush();
            concurrent.set(pool.submit(second));
            try {
                awaitBlockedOrDone(concurrent.get());
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            concurrent.get().get(30, TimeUnit.SECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } finally {
            pool.shutdown();
        }
    }

    private String ticketState(long ticketId) {
        return jdbc.queryForObject("SELECT t.status || ':' || coalesce(b.name, '-') || ':' || coalesce(b.payment_status, '-') "
                + "FROM tickets t LEFT JOIN buyers b ON b.id = t.buyer_id WHERE t.id = ?", String.class, ticketId);
    }

    @Test
    void perf11_approveDuringPurchaseDoesNotOverwriteIt() throws Exception {
        Throwable error = interleave(() -> ticketService.purchaseTickets(301L, purchaseRequest(504, "Comprador")),
                () -> ticketService.approveTicket(504L));
        assertThat(error).isNull();
        // Única serie posible (la aprobación empezó después de la compra): compra y luego aprobación
        assertThat(ticketState(504)).isEqualTo("SOLD:Comprador:CONFIRMED");
        assertThat(availableTickets()).isEqualTo(5);
        assertThat(buyers()).isEqualTo(3);
    }

    @Test
    void perf11_cancelDuringPurchaseIsSerializable() throws Exception {
        Throwable error = interleave(() -> ticketService.purchaseTickets(301L, purchaseRequest(504, "Comprador")),
                () -> ticketService.cancelTicket(504L));
        assertThat(error).isNull();
        // La cancelación ahora bloquea la rifa como la compra, así que espera a su commit: única serie posible
        // compra -> cancelación. El boleto queda libre y el contador se repone una vez (antes no se reponía)
        assertThat(ticketState(504)).isEqualTo("AVAILABLE:-:-");
        assertThat(availableTickets()).isEqualTo(6);
        assertThat(buyers()).isEqualTo(3);
    }

    @Test
    void perf11_purchaseDuringCancelIsSerializable() throws Exception {
        // Boleto 501 reservado por Ana; el admin lo cancela y, antes del commit, otro comprador intenta comprarlo
        Throwable error = interleave(() -> ticketService.cancelTicket(501L),
                () -> ticketService.purchaseTickets(301L, purchaseRequest(501, "Tardio")));
        if (error == null) {
            // Cancelación -> compra
            assertThat(ticketState(501)).isEqualTo("RESERVED:Tardio:PENDING");
            assertThat(availableTickets()).isEqualTo(5);
            assertThat(buyers()).isEqualTo(3);
        } else {
            // Compra -> cancelación: la compra falla igual que en serie y se revierte por completo
            assertThat(error).isInstanceOf(IllegalStateException.class).hasMessage("Ticket 00 is no longer available");
            assertThat(ticketState(501)).isEqualTo("AVAILABLE:-:-");
            assertThat(availableTickets()).isEqualTo(6);
            assertThat(buyers()).isEqualTo(2);
        }
    }

    @Test
    void perf11_approveAndCancelSequentialBehaviour() throws Exception {
        String token = bearer();
        assertThat(mockMvc.perform(put("/api/raffles/301/tickets/501/approve").header("Authorization", token)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(ticketState(501)).isEqualTo("SOLD:Ana:CONFIRMED");
        // Ana tiene también el 502: comparte comprador, así que aparece CONFIRMED aunque el boleto siga RESERVED
        assertThat(ticketState(502)).isEqualTo("RESERVED:Ana:CONFIRMED");

        // Aprobar un boleto libre ya no lo deja SOLD sin comprador (y sin descontar el contador): solo se aprueban reservas
        MvcResult notReserved = mockMvc.perform(put("/api/raffles/301/tickets/504/approve").header("Authorization", token)).andReturn();
        assertThat(notReserved.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"La reserva del boleto 03 ya no está vigente\"}",
                notReserved.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
        assertThat(ticketState(504)).isEqualTo("AVAILABLE:-:-");

        assertThat(mockMvc.perform(put("/api/raffles/301/tickets/503/cancel").header("Authorization", token)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(ticketState(503)).isEqualTo("AVAILABLE:-:-");
        assertThat(availableTickets()).isEqualTo(6);

        for (String action : List.of("approve", "cancel")) {
            MvcResult missing = mockMvc.perform(put("/api/raffles/301/tickets/999/" + action).header("Authorization", token)).andReturn();
            assertThat(missing.getResponse().getStatus()).isEqualTo(400);
            JSONAssert.assertEquals("{\"error\":\"No value present\"}", missing.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
        }
    }

    // --- Borrado de rifa contra compra / aprobación ---------------------------------------------------
    // Orden de bloqueos común a las cuatro operaciones: rifa (FOR UPDATE) -> boletos -> compradores.
    // El borrado se detiene justo después de borrar los boletos (con sus bloqueos tomados) y la otra
    // operación arranca en ese punto; antes, la compra/aprobación bloqueaba la rifa y esperaba los
    // boletos mientras el borrado esperaba la rifa (deadlock).

    /** Ejecuta la llamada interceptada en el repositorio real (la respuesta por defecto del espía delega en él). */
    private void callReal(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        Mockito.mockingDetails(ticketRepository).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private Throwable duringRaffleDelete(Runnable concurrent) throws Exception {
        CountDownLatch ticketsDeleted = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            callReal(invocation);
            ticketsDeleted.countDown();
            resume.await(30, TimeUnit.SECONDS);
            return null;
        }).when(ticketRepository).deleteByRaffleId(301L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> delete = pool.submit(() -> raffleService.delete(301L));
            assertThat(ticketsDeleted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> other = pool.submit(concurrent);
            awaitBlockedOrDone(other);
            resume.countDown();
            delete.get(30, TimeUnit.SECONDS); // falla con CannotAcquireLockException si hay deadlock
            try {
                other.get(30, TimeUnit.SECONDS);
                return null;
            } catch (ExecutionException e) {
                return e.getCause();
            }
        } finally {
            resume.countDown();
            pool.shutdown();
        }
    }

    private int rowsOfRaffle301() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM raffles WHERE id = 301) + (SELECT count(*) FROM tickets WHERE raffle_id = 301)", Integer.class);
    }

    @Test
    void perf11_purchaseDuringRaffleDelete() throws Exception {
        Throwable error = duringRaffleDelete(() -> ticketService.purchaseTickets(301L, purchaseRequest(504, "Tardio")));
        // Única serie posible: borrado y luego compra, que falla como en serie (rifa inexistente) sin dejar comprador
        assertThat(error).isInstanceOf(NoSuchElementException.class).hasMessage("No value present");
        assertThat(rowsOfRaffle301()).isZero();
        assertThat(buyers()).isEqualTo(2);
    }

    @Test
    void perf11_approveDuringRaffleDelete() throws Exception {
        Throwable error = duringRaffleDelete(() -> ticketService.approveTicket(501L));
        assertThat(error).isInstanceOf(NoSuchElementException.class).hasMessage("No value present");
        assertThat(rowsOfRaffle301()).isZero();
        assertThat(jdbc.queryForObject("SELECT payment_status FROM buyers WHERE id = 401", String.class)).isEqualTo("PENDING");
    }

    @Test
    void perf11_raffleDeleteDuringPurchase() throws Exception {
        Throwable error = interleave(() -> ticketService.purchaseTickets(301L, purchaseRequest(504, "Comprador")),
                () -> raffleService.delete(301L));
        // Compra y luego borrado: el borrado espera, no falla, y como en serie no borra compradores
        assertThat(error).isNull();
        assertThat(rowsOfRaffle301()).isZero();
        assertThat(buyers()).isEqualTo(3);
    }

    // --- Edición de rifa contra compra -------------------------------------------------------------
    // update() guarda la fila completa de la rifa, available_tickets incluido: si leyó la rifa antes de
    // que una compra hiciera commit, sobrescribía el contador con el valor anterior (lost update).

    private RaffleDTO renamed(String name) {
        RaffleDTO dto = raffleService.findById(301L);
        dto.setName(name);
        dto.setAvailableTickets(999); // el cliente no puede cambiar el contador: update() lo ignora
        return dto;
    }

    @Test
    void perf11_raffleUpdateDuringPurchaseKeepsCounter() throws Exception {
        RaffleDTO dto = renamed("Rifa editada");
        Throwable error = interleave(() -> ticketService.purchaseTickets(301L, purchaseRequest(504, "Comprador")),
                () -> raffleService.update(301L, dto));
        assertThat(error).isNull();
        // Compra y luego edición: el nombre cambia y el contador conserva el descuento de la compra
        assertThat(jdbc.queryForObject("SELECT name FROM raffles WHERE id = 301", String.class)).isEqualTo("Rifa editada");
        assertThat(availableTickets()).isEqualTo(5);
        assertThat(ticketState(504)).isEqualTo("RESERVED:Comprador:PENDING");
    }

    @Test
    void perf11_purchaseDuringRaffleUpdateKeepsCounter() throws Exception {
        RaffleDTO dto = renamed("Rifa editada");
        Throwable error = interleave(() -> raffleService.update(301L, dto),
                () -> ticketService.purchaseTickets(301L, purchaseRequest(504, "Comprador")));
        assertThat(error).isNull();
        assertThat(jdbc.queryForObject("SELECT name FROM raffles WHERE id = 301", String.class)).isEqualTo("Rifa editada");
        assertThat(availableTickets()).isEqualTo(5);
        assertThat(ticketState(504)).isEqualTo("RESERVED:Comprador:PENDING");
    }

    @Test
    void raffleUpdateKeepsResponseContract() throws Exception {
        String token = bearer();
        String body = "{\"name\":\"Rifa editada\",\"prize\":\"Premio\",\"ticketPrice\":5000.00,\"totalTickets\":50,"
                + "\"availableTickets\":40,\"status\":\"ACTIVE\"}";
        stats.clear();
        MvcResult updated = mockMvc.perform(put("/api/raffles/301").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        log.info("[query-count] PUT /api/raffles/301 -> {} sentencias, HTTP {}", stats.getPrepareStatementCount(), updated.getResponse().getStatus());
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        // totalTickets y availableTickets del cuerpo se ignoran en una edición (regla actual)
        JSONAssert.assertEquals("{\"id\":301,\"name\":\"Rifa editada\",\"description\":null,\"imageUrl\":null,\"prize\":\"Premio\","
                        + "\"ticketPrice\":5000.00,\"totalTickets\":6,\"availableTickets\":6,\"startDate\":null,\"endDate\":null,"
                        + "\"status\":\"ACTIVE\",\"createdAt\":\"2026-01-01T00:00:00\"}",
                updated.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);

        MvcResult missing = mockMvc.perform(put("/api/raffles/999").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"No value present\"}", missing.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
    }

    @Test
    void raffleDeleteKeepsResponseAndRollsBack() throws Exception {
        String token = bearer();
        doAnswer(invocation -> {
            callReal(invocation);
            throw new IllegalStateException("fallo simulado");
        }).when(ticketRepository).deleteByRaffleId(301L);
        MvcResult failed = mockMvc.perform(delete("/api/raffles/301").header("Authorization", token)).andReturn();
        assertThat(failed.getResponse().getStatus()).isEqualTo(400);
        // El fallo ocurre dentro del repositorio (Spring lo traduce a DataAccessException): mensaje genérico (SEC-D)
        JSONAssert.assertEquals("{\"error\":\"No se pudo guardar la información enviada\"}", failed.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
        // Rollback: los boletos borrados vuelven y la rifa sigue igual
        assertThat(rowsOfRaffle301()).isEqualTo(7);
        assertThat(availableTickets()).isEqualTo(6);

        Mockito.reset(ticketRepository);
        stats.clear();
        MvcResult deleted = mockMvc.perform(delete("/api/raffles/301").header("Authorization", token)).andReturn();
        log.info("[query-count] DELETE /api/raffles/301 -> {} sentencias, HTTP {}", stats.getPrepareStatementCount(), deleted.getResponse().getStatus());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        assertThat(rowsOfRaffle301()).isZero();
        assertThat(buyers()).isEqualTo(2);

        // Rifa inexistente: igual que antes, 204 sin error
        assertThat(mockMvc.perform(delete("/api/raffles/999").header("Authorization", token)).andReturn()
                .getResponse().getStatus()).isEqualTo(204);
    }

    // --- PERF-01: orden de los elementos de las colecciones ------------------------------------------

    @Test
    void perf01_collectionOrderDoesNotDependOnQueryPlan() throws Exception {
        // Muchos productos y pocas filas por colección: PostgreSQL resuelve la consulta SUBSELECT con
        // Merge Join + Sort por product_id, que no es estable y reordenaba los elementos de un producto
        jdbc.update("INSERT INTO products (id, name, price, status, created_at) "
                + "SELECT g, 'P' || g, 1.00, 'AVAILABLE', '2026-01-01 00:00:00' FROM generate_series(1000, 5999) g");
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO product_images (product_id, image_url) SELECT g, 'img-' || g || '-' || ? FROM generate_series(1000, 1039) g", i);
            jdbc.update("INSERT INTO product_sizes (product_id, size) SELECT g, 'T' || ? FROM generate_series(1000, 1039) g", 6 - i);
        }
        jdbc.update("INSERT INTO product_stores (product_id, sede_id) SELECT g, 12 FROM generate_series(1000, 1039) g");
        jdbc.update("INSERT INTO product_stores (product_id, sede_id) SELECT g, 11 FROM generate_series(1000, 1039) g");
        jdbc.execute("ANALYZE");

        MvcResult result = mockMvc.perform(get("/api/products")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        int checked = 0;
        for (com.fasterxml.jackson.databind.JsonNode p : json) {
            long id = p.get("id").asLong();
            if (id >= 1000 && id <= 1039) {
                // Orden de inserción, el mismo que devuelve la consulta original por producto
                assertThat(p.get("images").toString()).as("images de %d", id)
                        .isEqualTo("[\"img-%d-1\",\"img-%d-2\",\"img-%d-3\",\"img-%d-4\",\"img-%d-5\"]".formatted(id, id, id, id, id));
                assertThat(p.get("sizes").toString()).as("sizes de %d", id).isEqualTo("[\"T5\",\"T4\",\"T3\",\"T2\",\"T1\"]");
                assertThat(p.get("storeIds").toString()).as("storeIds de %d", id).isEqualTo("[12,11]");
                checked++;
            }
        }
        assertThat(checked).isEqualTo(40);
    }

    // --- PERF-01 con volumen ---------------------------------------------------------------------

    @Test
    void perf01_productsAtVolume() throws Exception {
        int products = 500;
        jdbc.update("INSERT INTO products (id, name, price, status, display_order, created_at) "
                + "SELECT 1000 + g, 'P' || g, g, 'AVAILABLE', g % 7, '2026-01-01 00:00:00' FROM generate_series(1, ?) g", products);
        // Elementos intercalados entre productos y en orden descendente para que el orden físico no sea trivial
        for (int k = 4; k >= 1; k--) {
            jdbc.update("INSERT INTO product_sizes (product_id, size) SELECT 1000 + g, 'S' || ? || '-' || g FROM generate_series(1, ?) g", k, products);
            jdbc.update("INSERT INTO product_images (product_id, image_url) SELECT 1000 + g, 'https://cdn/img/' || g || '/' || ? FROM generate_series(1, ?) g", k, products);
        }
        for (int k = 1; k <= 3; k++) {
            jdbc.update("INSERT INTO product_colors (product_id, color) SELECT 1000 + g, 'C' || ? FROM generate_series(1, ?) g WHERE g % 3 <> 0", k, products);
        }
        jdbc.update("INSERT INTO product_tags (product_id, tag) SELECT 1000 + g, 'tag' || g FROM generate_series(1, ?) g WHERE g % 2 = 0", products);
        jdbc.update("INSERT INTO product_stores (product_id, sede_id) SELECT 1000 + g, 12 FROM generate_series(1, ?) g", products);
        jdbc.update("INSERT INTO product_stores (product_id, sede_id) SELECT 1000 + g, 11 FROM generate_series(1, ?) g WHERE g % 5 = 0", products);
        int totalProducts = products + 3;
        int collectionRows = jdbc.queryForObject("SELECT (SELECT count(*) FROM product_sizes) + (SELECT count(*) FROM product_colors) "
                + "+ (SELECT count(*) FROM product_images) + (SELECT count(*) FROM product_tags) + (SELECT count(*) FROM product_stores)", Integer.class);

        mockMvc.perform(get("/api/products")).andReturn(); // calentamiento
        boolean pgss = resetPgStatStatements();
        stats.clear();
        MvcResult result = mockMvc.perform(get("/api/products")).andReturn();
        long statements = stats.getPrepareStatementCount();
        long collectionsLoaded = stats.getCollectionLoadCount();
        Long rowsRead = pgss ? jdbc.queryForObject("SELECT coalesce(sum(rows), 0) FROM pg_stat_statements "
                + "WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database()) AND query ILIKE 'select%' "
                + "AND query NOT ILIKE '%pg_stat%' AND query NOT ILIKE '%pg_database%'", Long.class) : null;

        long[] timings = new long[7];
        for (int i = 0; i < timings.length; i++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/products")).andReturn();
            timings[i] = (System.nanoTime() - start) / 1_000_000;
        }
        java.util.Arrays.sort(timings);

        byte[] body = result.getResponse().getContentAsByteArray();
        String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));
        log.info("[volume] GET /api/products: {} productos, {} filas en colecciones -> {} sentencias, {} colecciones cargadas, "
                        + "filas leidas (pg_stat_statements) = {}, respuesta {} bytes, mediana {} ms (min {} / max {}), sha256 {}",
                totalProducts, collectionRows, statements, collectionsLoaded, rowsRead == null ? "n/d" : rowsRead,
                body.length, timings[timings.length / 2], timings[0], timings[timings.length - 1], sha);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(statements).isEqualTo(6);
        com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertThat(json.size()).isEqualTo(totalProducts);
        int elements = 0;
        for (com.fasterxml.jackson.databind.JsonNode p : json) {
            for (String field : List.of("sizes", "colors", "images", "tags", "storeIds")) {
                elements += p.get(field).size();
            }
        }
        assertThat(elements).isEqualTo(collectionRows);
        if (rowsRead != null) {
            // Solo se leen los productos y las filas de colecciones que se devuelven (sin productos cartesianos)
            assertThat(rowsRead).isEqualTo((long) totalProducts + collectionRows);
        }
        com.fasterxml.jackson.databind.JsonNode p1 = json.findParents("id").stream().filter(n -> n.get("id").asLong() == 1001).findFirst().orElseThrow();
        assertThat(p1.get("sizes").toString()).isEqualTo("[\"S4-1\",\"S3-1\",\"S2-1\",\"S1-1\"]");
        assertThat(p1.get("images").toString()).isEqualTo("[\"https://cdn/img/1/4\",\"https://cdn/img/1/3\",\"https://cdn/img/1/2\",\"https://cdn/img/1/1\"]");
    }

    private boolean resetPgStatStatements() {
        try {
            jdbc.queryForList("SELECT pg_stat_statements_reset()");
            return true;
        } catch (Exception e) {
            log.info("[volume] pg_stat_statements no disponible: no se miden filas leidas");
            return false;
        }
    }

    // --- Batching: errores y rollback -------------------------------------------------------------

    @Test
    void failedProductUpdateRollsBackAndKeepsErrorFormat() throws Exception {
        String tooLong = "x".repeat(300); // product_sizes.size es varchar(255)
        MvcResult result = mockMvc.perform(put("/api/products/101").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cambiado\",\"price\":1,\"status\":\"AVAILABLE\",\"sizes\":[\"A\",\"B\",\"" + tooLong + "\"],"
                        + "\"colors\":[\"Rojo\"],\"images\":[],\"tags\":[],\"storeIds\":[11]}")).andReturn();
        log.info("[error] PUT con talla demasiado larga -> HTTP {} cuerpo {}", result.getResponse().getStatus(),
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        // SEC-D: mensaje genérico, sin SQL, nombres de columnas ni valores enviados (antes "could not execute statement [...]")
        JSONAssert.assertEquals("{\"error\":\"No se pudo guardar la información enviada\"}",
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(tooLong);
        // Rollback completo: el producto y sus colecciones siguen como estaban
        count("GET /api/products/101 tras update fallido", get("/api/products/101"), PRODUCT_101);
    }

    // --- Configuración de pruebas -------------------------------------------------------------------

    @Test
    void testsUseTheLocalTestDatabase() {
        String url = ((com.zaxxer.hikari.HikariDataSource) dataSource).getJdbcUrl();
        log.info("[config] datasource de pruebas: {}", url);
        assertThat(url).startsWith("jdbc:postgresql://localhost:");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).contains("test");
    }
}
