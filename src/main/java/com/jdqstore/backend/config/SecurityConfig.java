package com.jdqstore.backend.config;

import com.jdqstore.backend.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String GLOBAL_ADMIN = "GLOBAL_ADMIN";
    private static final String STORE_ADMIN = "STORE_ADMIN";

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final RafflePurchaseProperties purchaseProperties;

    private AuthorizationManager<RequestAuthorizationContext> purchaseAccess() {
        return purchaseProperties.isPublicEnabled()
                ? (authentication, context) -> new AuthorizationDecision(true)
                : AuthenticatedAuthorizationManager.authenticated();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configure(http))
            .authorizeHttpRequests(auth -> auth
                // Matriz de permisos (el orden importa: gana la primera regla que coincide)
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/contests/public/**").permitAll()
                .requestMatchers("/api/contests/admin/**").hasRole(GLOBAL_ADMIN)
                // Rifas: lectura pública (boletos sin datos del comprador); detalle de compradores y gestión solo GLOBAL_ADMIN
                .requestMatchers(HttpMethod.GET, "/api/raffles/*/tickets/admin").hasRole(GLOBAL_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/raffles/**").permitAll()
                // Compra: pública solo con app.raffle-purchase.public-enabled=true (controles contra abuso en
                // RafflePurchaseService); si no, exige sesión como antes. Solo este método y esta ruta
                .requestMatchers(HttpMethod.POST, "/api/raffles/*/tickets/purchase").access(purchaseAccess())
                .requestMatchers("/api/raffles/**").hasRole(GLOBAL_ADMIN)
                // Catálogo y contenido del sitio: lectura pública
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/sedes/**", "/api/home/**",
                        "/api/gallerys/**", "/api/videos/**", "/api/contacts/**").permitAll()
                // Productos y categorías: también STORE_ADMIN, limitado a sus sedes en SedeAccessService
                .requestMatchers("/api/products/**", "/api/categories/**").hasAnyRole(GLOBAL_ADMIN, STORE_ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/promotions/**", "/api/roulettecoupons/**").authenticated()
                .requestMatchers("/api/sedes/**", "/api/home/**", "/api/gallerys/**", "/api/videos/**", "/api/contacts/**",
                        "/api/users/**", "/api/promotions/**", "/api/roulettecoupons/**").hasRole(GLOBAL_ADMIN)
                // Documentación de la API: ya no es pública
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole(GLOBAL_ADMIN)
                .anyRequest().authenticated()
            )
            // 401 sin sesión válida y 403 sin el rol necesario, con el mismo formato {"error": ...} que el resto de la API
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> writeError(response, HttpStatus.UNAUTHORIZED, "Autenticación requerida"))
                .accessDeniedHandler((request, response, ex) -> writeError(response, HttpStatus.FORBIDDEN, "Acceso denegado"))
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        // El frontend se autentica con la cabecera Authorization, no con cookies: no hace falta permitir credenciales
        // a cualquier origen
        config.setAllowCredentials(false);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
