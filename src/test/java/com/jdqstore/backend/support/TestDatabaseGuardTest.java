package com.jdqstore.backend.support;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:54329/jdq_test",
            "jdbc:postgresql://127.0.0.1:5432/backend_test?sslmode=disable",
            "jdbc:postgresql://[::1]:5432/TEST_db",
    })
    void acceptsLocalTestDatabases(String url) {
        assertThatCode(() -> TestDatabaseGuard.verify(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "jdbc:postgresql://altaria.proxy.rlwy.net:20859/railway",
            "jdbc:postgresql://altaria.proxy.rlwy.net:20859/railway_test",
            "jdbc:postgresql://postgres.railway.internal:5432/test",
            "jdbc:postgresql://localhost:5432/railway",
            "jdbc:postgresql://localhost:5432/jdqstore",
            "jdbc:postgresql://localhost.evil.com:5432/test",
            "jdbc:postgresql://localhost:5432,altaria.proxy.rlwy.net:20859/test",
            "jdbc:postgresql:test",
            "jdbc:h2:mem:test",
            "postgresql://localhost:5432/test",
            "jdbc:postgresql://local host/test",
    })
    void refusesAnythingElse(String url) {
        assertThatThrownBy(() -> TestDatabaseGuard.verify(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Pruebas abortadas");
    }

    @Test
    void refusesUnresolvablePlaceholder() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "${NO_EXISTE_EN_NINGUN_SITIO}");
        assertThatThrownBy(() -> new TestDatabaseGuard().postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Pruebas abortadas");
    }

    @Test
    void doesNotEchoUrlParameters() {
        assertThatThrownBy(() -> TestDatabaseGuard.verify("jdbc:postgresql://db.example.com:5432/test?password=no-mostrar"))
                .isInstanceOf(IllegalStateException.class)
                .message().doesNotContain("no-mostrar");
    }

    // --- Control sobre el DataSource ya configurado (no abre conexiones: Hikari conecta de forma perezosa) ---

    private static HikariDataSource hikari(String jdbcUrl) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        return dataSource;
    }

    @Test
    void acceptsHikariPointingToLocalTestDatabase() {
        try (HikariDataSource dataSource = hikari("jdbc:postgresql://localhost:54329/jdq_test")) {
            assertThatCode(() -> TestDatabaseGuard.verify(dataSource)).doesNotThrowAnyException();
        }
    }

    @Test
    void refusesHikariJdbcUrlOverride() {
        // spring.datasource.hikari.jdbc-url se aplica sobre el pool después de spring.datasource.url
        try (HikariDataSource dataSource = hikari("jdbc:postgresql://altaria.proxy.rlwy.net:20859/railway")) {
            assertThatThrownBy(() -> TestDatabaseGuard.verify(dataSource)).hasMessageStartingWith("Pruebas abortadas");
        }
    }

    @Test
    void refusesHikariConfiguredWithoutJdbcUrl() {
        try (HikariDataSource dataSource = hikari("jdbc:postgresql://localhost:54329/jdq_test")) {
            dataSource.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
            assertThatThrownBy(() -> TestDatabaseGuard.verify(dataSource)).hasMessageStartingWith("Pruebas abortadas");
        }
    }

    @Test
    void refusesDataSourcesItCannotInspect() {
        DataSource other = new DriverManagerDataSource("jdbc:postgresql://localhost:54329/jdq_test");
        assertThatThrownBy(() -> TestDatabaseGuard.verify(other)).hasMessageStartingWith("Pruebas abortadas");
    }

    @Test
    void usesTheEffectiveValueAfterOverrides() {
        // Una variable SPRING_DATASOURCE_URL (u otra fuente de mayor prioridad) apuntando a Railway debe bloquearse
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://altaria.proxy.rlwy.net:20859/railway");
        assertThatThrownBy(() -> new TestDatabaseGuard().postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class);
    }
}
