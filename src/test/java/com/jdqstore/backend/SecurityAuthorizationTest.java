package com.jdqstore.backend;

import com.jdqstore.backend.entity.User;
import com.jdqstore.backend.repository.UserRepository;
import com.jdqstore.backend.security.JwtService;
import com.jdqstore.backend.security.UserDetailsImpl;
import com.jdqstore.backend.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * SEC-B (autorización por rol, método y sede), SEC-C (datos personales en respuestas públicas) y SEC-D (formato de
 * errores), contra el PostgreSQL local de pruebas. Usuarios: GLOBAL_ADMIN (admin@jdqstore.com, creado por
 * DataInitializer), STORE_ADMIN de la sede 11 y STORE_ADMIN de la sede 12.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Value("${application.security.jwt.secret-key}") private String jwtSecretKey;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE product_sizes, product_colors, product_images, product_tags, product_stores, products, categories, "
                + "tickets, buyers, raffles, contest_winners, contest_participants, contests, user_sedes, sedes, hero_slides, "
                + "home_category_collections, roulette_settings, mystery_box_settings, promotions, roulette_coupons CASCADE");
        jdbc.update("DELETE FROM users WHERE email <> 'admin@jdqstore.com'");

        jdbc.update("INSERT INTO sedes (id, name, created_at) VALUES (11, 'Sede Norte', '2026-01-01 00:00:00'), (12, 'Sede Sur', '2026-01-01 00:00:00')");
        Long storeAdminRole = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'STORE_ADMIN'", Long.class);
        jdbc.update("INSERT INTO users (id, name, email, password, role_id, is_active, created_at) VALUES "
                + "(201, 'Tienda Norte', 'norte@x.com', 'x', ?, true, '2026-01-01 00:00:00'),"
                + "(202, 'Tienda Sur', 'sur@x.com', 'x', ?, true, '2026-01-01 00:00:00')", storeAdminRole, storeAdminRole);
        jdbc.update("INSERT INTO user_sedes (user_id, sede_id) VALUES (201, 11), (202, 12)");

        jdbc.update("INSERT INTO products (id, name, price, status, created_at) VALUES "
                + "(101, 'Norte', 1.00, 'AVAILABLE', '2026-01-01'), (102, 'Sur', 1.00, 'AVAILABLE', '2026-01-01'),"
                + "(103, 'Ambas', 1.00, 'AVAILABLE', '2026-01-01'), (104, 'Sin sede', 1.00, 'AVAILABLE', '2026-01-01')");
        jdbc.update("INSERT INTO product_stores (product_id, sede_id) VALUES (101, 11), (102, 12), (103, 11), (103, 12)");
        jdbc.update("INSERT INTO categories (id, name, sede_id) VALUES (21, 'Cat Norte', 11), (22, 'Cat Sur', 12), (23, 'Cat Global', NULL)");

        jdbc.update("INSERT INTO raffles (id, name, prize, ticket_price, total_tickets, available_tickets, status, created_at) "
                + "VALUES (301, 'Rifa', 'Premio', 5000.00, 2, 1, 'ACTIVE', '2026-01-01 00:00:00')");
        jdbc.update("INSERT INTO buyers (id, name, phone, email, payment_status, payment_method, notes, created_at) VALUES "
                + "(401, 'Ana Compradora', '3001234567', 'ana@x.com', 'PENDING', 'NEQUI', 'nota privada', '2026-02-01 10:00:00')");
        jdbc.update("INSERT INTO tickets (id, raffle_id, ticket_number, status, buyer_id, updated_at) VALUES "
                + "(501, 301, '00', 'RESERVED', 401, '2026-02-01 10:00:00'), (502, 301, '01', 'AVAILABLE', NULL, NULL)");

        jdbc.update("INSERT INTO contests (id, title, status, form_enabled) VALUES (601, 'Concurso', 'FINISHED', false)");
        jdbc.update("INSERT INTO contest_participants (id, contest_id, full_name, identification_number, phone, email, city, social_media, "
                + "outfit_image_url, accepted_terms, agrees_to_public_display, status) VALUES "
                + "(701, 601, 'Gana Dora', '1234567890', '3109876543', 'gana@x.com', 'Cali', '@gana', 'img-1', true, true, 'WINNER'),"
                + "(702, 601, 'Privada', '999', '311', 'priv@x.com', 'Bogota', '@priv', 'img-2', true, false, 'PARTICIPANT')");
        jdbc.update("INSERT INTO contest_winners (id, contest_id, participant_id, selected_at) VALUES (801, 601, 701, '2026-03-01 00:00:00')");
    }

    // --- Utilidades --------------------------------------------------------------------------------

    private String token(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return "Bearer " + jwtService.generateToken(new UserDetailsImpl(user));
    }

    private String globalAdmin() { return token("admin@jdqstore.com"); }
    private String norte() { return token("norte@x.com"); }

    private record Call(HttpMethod method, String url, String body) {
        @Override public String toString() { return method + " " + url; }
    }

    private static Call call(String method, String url) { return new Call(HttpMethod.valueOf(method), url, "{}"); }
    private static Call call(String method, String url, String body) { return new Call(HttpMethod.valueOf(method), url, body); }

    private MvcResult perform(Call call, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(call.method(), call.url())
                .contentType(MediaType.APPLICATION_JSON).content(call.body());
        if (token != null) builder.header(HttpHeaders.AUTHORIZATION, token);
        return mockMvc.perform(builder).andReturn();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Huella del estado de todas las tablas que tocan las llamadas: debe quedar igual tras los intentos rechazados. */
    private String snapshot() {
        return jdbc.queryForObject("SELECT concat_ws('|', "
                + "(SELECT string_agg(id || ':' || name, ',' ORDER BY id) FROM sedes),"
                + "(SELECT string_agg(id || ':' || name || ':' || role_id || ':' || is_active, ',' ORDER BY id) FROM users),"
                + "(SELECT string_agg(user_id || '-' || sede_id, ',' ORDER BY user_id, sede_id) FROM user_sedes),"
                + "(SELECT string_agg(id || ':' || name, ',' ORDER BY id) FROM products),"
                + "(SELECT string_agg(product_id || '-' || sede_id, ',' ORDER BY product_id, sede_id) FROM product_stores),"
                + "(SELECT string_agg(id || ':' || name || ':' || coalesce(sede_id::text, '-'), ',' ORDER BY id) FROM categories),"
                + "(SELECT string_agg(id || ':' || name || ':' || available_tickets, ',' ORDER BY id) FROM raffles),"
                + "(SELECT string_agg(id || ':' || status || ':' || coalesce(buyer_id::text, '-'), ',' ORDER BY id) FROM tickets),"
                + "(SELECT string_agg(id || ':' || payment_status, ',' ORDER BY id) FROM buyers),"
                + "(SELECT string_agg(id || ':' || status || ':' || form_enabled, ',' ORDER BY id) FROM contests),"
                + "(SELECT string_agg(id || ':' || status, ',' ORDER BY id) FROM contest_participants),"
                + "(SELECT count(*) FROM contest_winners), (SELECT count(*) FROM hero_slides), (SELECT count(*) FROM home_category_collections),"
                + "(SELECT count(*) FROM roulette_settings), (SELECT count(*) FROM mystery_box_settings),"
                + "(SELECT count(*) FROM promotions), (SELECT count(*) FROM roulette_coupons))", String.class);
    }

    /** Operaciones reservadas a GLOBAL_ADMIN. */
    private static List<Call> globalAdminOnly() {
        return List.of(
                call("POST", "/api/sedes", "{\"name\":\"Nueva\"}"), call("PUT", "/api/sedes/11", "{\"name\":\"Cambiada\"}"),
                call("DELETE", "/api/sedes/12"),
                call("PUT", "/api/home/roulette-setting", "{\"activeDays\":\"MONDAY\",\"values\":\"100\",\"probabilities\":\"100\"}"),
                call("PUT", "/api/home/mystery-box", "{\"title\":\"Gratis\",\"price\":0}"),
                call("POST", "/api/home/hero/slides", "{\"imageUrl\":\"x\"}"), call("PUT", "/api/home/hero/slides/1", "{\"imageUrl\":\"x\"}"),
                call("DELETE", "/api/home/hero/slides/1"),
                call("POST", "/api/home/collections", "{\"name\":\"x\"}"), call("PUT", "/api/home/collections/1", "{\"name\":\"x\"}"),
                call("DELETE", "/api/home/collections/1"),
                call("GET", "/api/users"), call("GET", "/api/users/202"),
                call("POST", "/api/users", "{\"name\":\"Nuevo\",\"email\":\"nuevo@x.com\",\"password\":\"x\",\"roleName\":\"GLOBAL_ADMIN\",\"isActive\":true}"),
                call("PUT", "/api/users/201", "{\"name\":\"Tienda Norte\",\"email\":\"norte@x.com\",\"roleName\":\"GLOBAL_ADMIN\",\"storeIds\":[11,12],\"isActive\":true}"),
                call("DELETE", "/api/users/202"),
                call("GET", "/api/contests/admin/all"), call("POST", "/api/contests/admin", "{\"title\":\"x\"}"),
                call("PUT", "/api/contests/admin/601", "{\"title\":\"x\",\"status\":\"ACTIVE\"}"),
                call("GET", "/api/contests/admin/601/participants"), call("GET", "/api/contests/admin/601/winner"),
                call("POST", "/api/contests/admin/601/select-winner"), call("DELETE", "/api/contests/admin/601/reset"),
                call("POST", "/api/raffles", "{\"name\":\"x\",\"prize\":\"x\",\"ticketPrice\":1,\"totalTickets\":10}"),
                call("PUT", "/api/raffles/301", "{\"name\":\"x\",\"prize\":\"x\",\"ticketPrice\":1}"), call("DELETE", "/api/raffles/301"),
                call("PUT", "/api/raffles/301/tickets/501/approve"), call("PUT", "/api/raffles/301/tickets/501/cancel"),
                call("GET", "/api/raffles/301/tickets/admin"),
                call("POST", "/api/promotions"), call("PUT", "/api/promotions/1"), call("DELETE", "/api/promotions/1"),
                call("POST", "/api/roulettecoupons"), call("PUT", "/api/roulettecoupons/1"), call("DELETE", "/api/roulettecoupons/1"),
                call("GET", "/v3/api-docs"));
    }

    /** Escrituras de productos y categorías: GLOBAL_ADMIN o STORE_ADMIN de la sede. */
    private static List<Call> catalogWrites() {
        return List.of(
                call("POST", "/api/products", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"),
                call("PUT", "/api/products/101", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"),
                call("DELETE", "/api/products/101"),
                call("POST", "/api/categories", "{\"name\":\"x\",\"storeId\":11}"),
                call("PUT", "/api/categories/21", "{\"name\":\"x\",\"storeId\":11}"), call("DELETE", "/api/categories/21"));
    }

    // --- SEC-B: lecturas públicas ---------------------------------------------------------------------

    @Test
    void anonymousCanReadPublicEndpoints() throws Exception {
        for (String url : List.of("/api/products", "/api/products/101", "/api/categories", "/api/categories?storeId=11",
                "/api/categories/21", "/api/sedes", "/api/sedes/11", "/api/home/hero", "/api/home/collections",
                "/api/home/roulette-setting", "/api/home/mystery-box", "/api/raffles", "/api/raffles/301",
                "/api/raffles/301/tickets", "/api/contests/public/active", "/api/contests/public/participants",
                "/api/contests/public/winner?contestId=601")) {
            assertThat(mockMvc.perform(get(url)).andReturn().getResponse().getStatus()).as(url).isEqualTo(200);
        }
    }

    @Test
    void categoriesWithoutImagesKeepTheSameListAndOnlyDropTheImage() throws Exception {
        jdbc.update("UPDATE categories SET image = 'data:image/jpeg;base64,' || repeat('A', 100000) WHERE id IN (21, 23)");

        for (String filter : List.of("", "?storeId=11", "?storeId=12", "?storeId=99")) {
            String sep = filter.isEmpty() ? "?" : "&";
            String full = body(mockMvc.perform(get("/api/categories" + filter)).andReturn());
            String sameAsDefault = body(mockMvc.perform(get("/api/categories" + filter + sep + "includeImage=true")).andReturn());
            String light = body(mockMvc.perform(get("/api/categories" + filter + sep + "includeImage=false")).andReturn());

            JSONAssert.assertEquals(full, sameAsDefault, JSONCompareMode.STRICT);
            JSONAssert.assertEquals(full.replaceAll("\"data:image/jpeg;base64,A+\"", "null"), light, JSONCompareMode.STRICT);
            assertThat(light).as(filter).doesNotContain("base64");
        }
        JSONAssert.assertEquals("[{\"id\":21,\"name\":\"Cat Norte\",\"image\":null,\"storeId\":11}]",
                body(mockMvc.perform(get("/api/categories?storeId=11&includeImage=false")).andReturn()), JSONCompareMode.STRICT);
        assertThat(body(mockMvc.perform(get("/api/categories?includeImage=false")).andReturn()))
                .contains("\"id\":23,\"name\":\"Cat Global\",\"image\":null,\"storeId\":null");
    }

    // --- SEC-B: anónimo ----------------------------------------------------------------------------------

    @Test
    void anonymousCannotWriteOrUseAdminEndpoints() throws Exception {
        String before = snapshot();
        List<Call> calls = new ArrayList<>(globalAdminOnly());
        calls.addAll(catalogWrites());
        calls.add(call("GET", "/api/users/me"));
        calls.add(call("GET", "/api/promotions"));
        calls.add(call("GET", "/api/roulettecoupons"));
        calls.add(call("POST", "/api/raffles/301/tickets/purchase", "{\"ticketIds\":[502],\"buyer\":{\"name\":\"x\",\"phone\":\"1\"}}"));
        for (Call call : calls) {
            MvcResult result = perform(call, null);
            assertThat(result.getResponse().getStatus()).as(call.toString()).isEqualTo(401);
            JSONAssert.assertEquals("{\"error\":\"Autenticación requerida\"}", body(result), JSONCompareMode.STRICT);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    // --- SEC-B: autenticado sin el rol necesario ------------------------------------------------------

    @Test
    void storeAdminCannotUseGlobalAdminEndpoints() throws Exception {
        String before = snapshot();
        String token = norte();
        for (Call call : globalAdminOnly()) {
            MvcResult result = perform(call, token);
            assertThat(result.getResponse().getStatus()).as(call.toString()).isEqualTo(403);
            JSONAssert.assertEquals("{\"error\":\"Acceso denegado\"}", body(result), JSONCompareMode.STRICT);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void storeAdminKeepsItsOwnReadAccess() throws Exception {
        String token = norte();
        MvcResult me = perform(call("GET", "/api/users/me"), token);
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("{\"id\":201,\"email\":\"norte@x.com\",\"roleName\":\"STORE_ADMIN\",\"storeIds\":[11]}", body(me), JSONCompareMode.LENIENT);
        assertThat(perform(call("GET", "/api/promotions"), token).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(call("GET", "/api/roulettecoupons"), token).getResponse().getStatus()).isEqualTo(200);
    }

    // --- SEC-B: rol correcto -----------------------------------------------------------------------------

    @Test
    void globalAdminCanUseAdminEndpoints() throws Exception {
        String token = globalAdmin();
        for (Call call : List.of(call("GET", "/api/users"), call("GET", "/api/users/201"), call("GET", "/api/contests/admin/all"),
                call("GET", "/api/contests/admin/601/participants"), call("GET", "/api/contests/admin/601/winner"),
                call("GET", "/api/raffles/301/tickets/admin"), call("GET", "/v3/api-docs"),
                call("POST", "/api/sedes", "{\"name\":\"Nueva\"}"),
                call("PUT", "/api/home/roulette-setting", "{\"activeDays\":\"MONDAY\",\"values\":\"5\",\"probabilities\":\"100\"}"),
                call("POST", "/api/home/hero/slides", "{\"imageUrl\":\"x\"}"),
                call("PUT", "/api/raffles/301/tickets/501/approve"),
                call("PUT", "/api/products/102", "{\"name\":\"Sur editado\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[12]}"),
                call("PUT", "/api/categories/23", "{\"name\":\"Global editada\"}"))) {
            assertThat(perform(call, token).getResponse().getStatus()).as(call.toString()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = 501", String.class)).isEqualTo("SOLD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sedes", Integer.class)).isEqualTo(3);
    }

    // --- SEC-B: acceso entre sedes ------------------------------------------------------------------------

    @Test
    void storeAdminManagesOnlyProductsOfItsSede() throws Exception {
        String token = norte();
        String before = snapshot();
        for (Call denied : List.of(
                call("POST", "/api/products", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[12]}"),
                call("POST", "/api/products", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11,12]}"),
                call("POST", "/api/products", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[]}"),
                call("POST", "/api/products", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\"}"),
                call("PUT", "/api/products/102", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[12]}"),
                call("PUT", "/api/products/103", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"),
                call("PUT", "/api/products/104", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"),
                call("PUT", "/api/products/101", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11,12]}"),
                call("PUT", "/api/products/101", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[]}"),
                call("DELETE", "/api/products/102"), call("DELETE", "/api/products/103"), call("DELETE", "/api/products/104"))) {
            MvcResult result = perform(denied, token);
            assertThat(result.getResponse().getStatus()).as(denied + " " + denied.body()).isEqualTo(403);
            JSONAssert.assertEquals("{\"error\":\"Acceso denegado\"}", body(result), JSONCompareMode.STRICT);
        }
        assertThat(snapshot()).isEqualTo(before);

        MvcResult created = perform(call("POST", "/api/products", "{\"name\":\"Nuevo norte\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"), token);
        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("{\"name\":\"Nuevo norte\",\"storeIds\":[11]}", body(created), JSONCompareMode.LENIENT);
        assertThat(perform(call("PUT", "/api/products/101", "{\"name\":\"Norte editado\",\"price\":2,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"), token)
                .getResponse().getStatus()).isEqualTo(200);
        // storeIds ausente: el servicio conserva las sedes actuales (comportamiento previo)
        assertThat(perform(call("PUT", "/api/products/101", "{\"name\":\"Norte editado 2\",\"price\":2,\"status\":\"AVAILABLE\"}"), token)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT sede_id FROM product_stores WHERE product_id = 101", Long.class)).containsExactly(11L);
        assertThat(perform(call("DELETE", "/api/products/101"), token).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id IN (101, 102, 103, 104)", Integer.class)).isEqualTo(3);

        // Un producto inexistente responde como antes (sin comprobación de sede)
        MvcResult missing = perform(call("PUT", "/api/products/999", "{\"name\":\"x\",\"price\":1,\"status\":\"AVAILABLE\",\"storeIds\":[11]}"), token);
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"No value present\"}", body(missing), JSONCompareMode.STRICT);
    }

    @Test
    void storeAdminManagesOnlyCategoriesOfItsSede() throws Exception {
        String token = norte();
        String before = snapshot();
        for (Call denied : List.of(
                call("POST", "/api/categories", "{\"name\":\"x\",\"storeId\":12}"),
                call("POST", "/api/categories", "{\"name\":\"x\"}"),
                call("PUT", "/api/categories/22", "{\"name\":\"x\",\"storeId\":12}"),
                call("PUT", "/api/categories/21", "{\"name\":\"x\",\"storeId\":12}"),
                call("PUT", "/api/categories/21", "{\"name\":\"x\"}"),
                call("PUT", "/api/categories/23", "{\"name\":\"x\",\"storeId\":11}"),
                call("DELETE", "/api/categories/22"), call("DELETE", "/api/categories/23"))) {
            assertThat(perform(denied, token).getResponse().getStatus()).as(denied + " " + denied.body()).isEqualTo(403);
        }
        assertThat(snapshot()).isEqualTo(before);

        assertThat(perform(call("POST", "/api/categories", "{\"name\":\"Nueva norte\",\"storeId\":11}"), token).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(call("PUT", "/api/categories/21", "{\"name\":\"Norte editada\",\"storeId\":11}"), token).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(call("DELETE", "/api/categories/21"), token).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT name FROM categories", String.class)).containsExactlyInAnyOrder("Cat Sur", "Cat Global", "Nueva norte");
    }

    // --- SEC-B: escalada de privilegios ------------------------------------------------------------------

    @Test
    void storeAdminCannotGrantItselfMorePrivileges() throws Exception {
        String token = norte();
        String before = snapshot();
        for (Call escalation : List.of(
                call("PUT", "/api/users/201", "{\"name\":\"Tienda Norte\",\"email\":\"norte@x.com\",\"roleName\":\"GLOBAL_ADMIN\",\"isActive\":true}"),
                call("PUT", "/api/users/201", "{\"name\":\"Tienda Norte\",\"email\":\"norte@x.com\",\"roleName\":\"STORE_ADMIN\",\"storeIds\":[11,12],\"isActive\":true}"),
                call("PUT", "/api/users/202", "{\"name\":\"Tienda Sur\",\"email\":\"sur@x.com\",\"password\":\"nueva\",\"roleName\":\"STORE_ADMIN\",\"isActive\":true}"),
                call("POST", "/api/users", "{\"name\":\"Otro\",\"email\":\"otro@x.com\",\"password\":\"x\",\"roleName\":\"GLOBAL_ADMIN\",\"isActive\":true}"))) {
            assertThat(perform(escalation, token).getResponse().getStatus()).as(escalation + " " + escalation.body()).isEqualTo(403);
        }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id = 202", String.class)).isEqualTo("x");
        // El token sigue reflejando el rol real
        assertThat(body(perform(call("GET", "/api/users/me"), token))).contains("\"roleName\":\"STORE_ADMIN\"");
    }

    // --- Tokens no válidos ------------------------------------------------------------------------------

    @Test
    void invalidTokensAreTreatedAsAnonymous() throws Exception {
        String forged = "Bearer " + Jwts.builder().subject("admin@jdqstore.com").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(new SecureRandom().generateSeed(32))).compact();
        // Firmado con la clave real pero caducado: es lo que verá cada usuario tras rotar la clave o al expirar la sesión
        String expired = "Bearer " + Jwts.builder().subject("admin@jdqstore.com").issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretKey))).compact();
        String unknownUser = "Bearer " + jwtService.generateToken(new UserDetailsImpl(User.builder().email("borrado@x.com").build()));
        for (String token : List.of(forged, expired, "Bearer no-es-un-jwt", unknownUser)) {
            // Una lectura pública sigue funcionando (el frontend envía el token guardado en todas las peticiones)
            assertThat(perform(call("GET", "/api/products"), token).getResponse().getStatus()).isEqualTo(200);
            MvcResult me = perform(call("GET", "/api/users/me"), token);
            assertThat(me.getResponse().getStatus()).isEqualTo(401);
            assertThat(perform(call("GET", "/api/users"), token).getResponse().getStatus()).isEqualTo(401);
        }
    }

    // --- CORS -------------------------------------------------------------------------------------------------

    @Test
    void corsDoesNotAllowCredentials() throws Exception {
        MvcResult preflight = mockMvc.perform(options("/api/products").header(HttpHeaders.ORIGIN, "https://otro-sitio.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")).andReturn();
        assertThat(preflight.getResponse().getStatus()).isEqualTo(200);
        assertThat(preflight.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNotNull();
        assertThat(preflight.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    // --- SEC-C: datos personales --------------------------------------------------------------------------

    private static final List<String> BUYER_DATA = List.of("Ana Compradora", "3001234567", "ana@x.com", "NEQUI", "nota privada");

    @Test
    void publicTicketsDoNotExposeBuyers() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/raffles/301/tickets")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("[{\"id\":501,\"raffleId\":301,\"ticketNumber\":\"00\",\"status\":\"RESERVED\",\"buyer\":null,\"updatedAt\":\"2026-02-01T10:00:00\"},"
                + "{\"id\":502,\"raffleId\":301,\"ticketNumber\":\"01\",\"status\":\"AVAILABLE\",\"buyer\":null,\"updatedAt\":null}]",
                body(result), JSONCompareMode.STRICT);
        for (String value : BUYER_DATA) {
            assertThat(body(result)).doesNotContain(value);
        }
    }

    @Test
    void adminTicketsIncludeBuyers() throws Exception {
        MvcResult result = perform(call("GET", "/api/raffles/301/tickets/admin"), globalAdmin());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("[{\"id\":501,\"raffleId\":301,\"ticketNumber\":\"00\",\"status\":\"RESERVED\",\"updatedAt\":\"2026-02-01T10:00:00\","
                + "\"buyer\":{\"id\":401,\"name\":\"Ana Compradora\",\"phone\":\"3001234567\",\"email\":\"ana@x.com\",\"paymentStatus\":\"PENDING\","
                + "\"paymentMethod\":\"NEQUI\",\"notes\":\"nota privada\",\"createdAt\":\"2026-02-01T10:00:00\"}},"
                + "{\"id\":502,\"raffleId\":301,\"ticketNumber\":\"01\",\"status\":\"AVAILABLE\",\"buyer\":null,\"updatedAt\":null}]",
                body(result), JSONCompareMode.STRICT);
        assertThat(perform(call("GET", "/api/raffles/301/tickets/admin"), norte()).getResponse().getStatus()).isEqualTo(403);
        assertThat(perform(call("GET", "/api/raffles/301/tickets/admin"), null).getResponse().getStatus()).isEqualTo(401);
    }

    private static final List<String> PARTICIPANT_PRIVATE_DATA = List.of("1234567890", "3109876543", "gana@x.com", "identificationNumber",
            "\"phone\"", "\"email\"", "acceptedTerms", "agreesToPublicDisplay");

    @Test
    void publicContestEndpointsDoNotExposeContactData() throws Exception {
        MvcResult winner = mockMvc.perform(get("/api/contests/public/winner?contestId=601")).andReturn();
        assertThat(winner.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("{\"id\":801,\"selectedAt\":\"2026-03-01T00:00:00\",\"contest\":{\"id\":601,\"title\":\"Concurso\"},"
                + "\"participant\":{\"id\":701,\"fullName\":\"Gana Dora\",\"city\":\"Cali\",\"socialMedia\":\"@gana\",\"outfitImageUrl\":\"img-1\"}}",
                body(winner), JSONCompareMode.LENIENT);
        MvcResult participants = mockMvc.perform(get("/api/contests/public/participants?contestId=601")).andReturn();
        // Solo quien aceptó la publicación, y sin datos de contacto
        JSONAssert.assertEquals("[{\"id\":701,\"fullName\":\"Gana Dora\",\"city\":\"Cali\",\"socialMedia\":\"@gana\",\"outfitImageUrl\":\"img-1\"}]",
                body(participants), JSONCompareMode.STRICT);
        for (String value : PARTICIPANT_PRIVATE_DATA) {
            assertThat(body(winner)).doesNotContain(value);
            assertThat(body(participants)).doesNotContain(value);
        }
        assertThat(body(participants)).doesNotContain("Privada");
        // Sin ganador: sigue respondiendo vacío
        jdbc.update("DELETE FROM contest_winners");
        assertThat(body(mockMvc.perform(get("/api/contests/public/winner?contestId=601")).andReturn())).isEmpty();
    }

    @Test
    void adminWinnerIncludesContactData() throws Exception {
        MvcResult winner = perform(call("GET", "/api/contests/admin/601/winner"), globalAdmin());
        assertThat(winner.getResponse().getStatus()).isEqualTo(200);
        JSONAssert.assertEquals("{\"id\":801,\"participant\":{\"id\":701,\"fullName\":\"Gana Dora\",\"email\":\"gana@x.com\",\"phone\":\"3109876543\","
                + "\"identificationNumber\":\"1234567890\",\"city\":\"Cali\"}}", body(winner), JSONCompareMode.LENIENT);
    }

    // --- SEC-D: formato de errores ------------------------------------------------------------------------

    @Test
    void technicalErrorsAreGeneric() throws Exception {
        String token = globalAdmin();
        MvcResult malformed = perform(call("POST", "/api/sedes", "{\"name\": "), token);
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Solicitud no válida\"}", body(malformed), JSONCompareMode.STRICT);

        MvcResult badId = perform(call("GET", "/api/users/abc"), token);
        assertThat(badId.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Solicitud no válida\"}", body(badId), JSONCompareMode.STRICT);

        // Restricción NOT NULL de la base de datos: sin SQL, nombres de columnas ni valores
        MvcResult dbError = perform(call("POST", "/api/sedes", "{\"address\":\"Calle secreta 123\"}"), token);
        assertThat(dbError.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"No se pudo guardar la información enviada\"}", body(dbError), JSONCompareMode.STRICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sedes", Integer.class)).isEqualTo(2);
    }

    @Test
    void unexpectedErrorsDoNotReturnDetails() {
        var response = new GlobalExceptionHandler().handleException(new Exception("detalle interno: ana@x.com"),
                new MockHttpServletRequest("GET", "/api/x"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsExactly(java.util.Map.entry("error", "An unexpected error occurred"));
    }

    @Test
    void businessErrorsKeepTheirMessages() throws Exception {
        MvcResult login = mockMvc.perform(request(HttpMethod.POST, "/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@jdqstore.com\",\"password\":\"incorrecta\"}")).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Bad credentials\"}", body(login), JSONCompareMode.STRICT);

        // Datos del comprador válidos: la compra ahora valida nombre y teléfono antes de comprobar la disponibilidad
        MvcResult taken = perform(call("POST", "/api/raffles/301/tickets/purchase", "{\"ticketIds\":[501],\"buyer\":{\"name\":\"Ana Prueba\",\"phone\":\"3001112233\"}}"), norte());
        assertThat(taken.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"Ticket 00 is no longer available\"}", body(taken), JSONCompareMode.STRICT);

        MvcResult missing = perform(call("GET", "/api/products/999"), null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        JSONAssert.assertEquals("{\"error\":\"No value present\"}", body(missing), JSONCompareMode.STRICT);
    }
}
