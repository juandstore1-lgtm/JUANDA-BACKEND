package com.jdqstore.backend.support;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Solo existe en el classpath de pruebas (registrado en src/test/resources/META-INF/spring.factories) y se
 * aplica a cualquier contexto Spring Boot que arranquen las pruebas. Exige un PostgreSQL local con "test" en
 * el nombre de la base de datos; si no puede comprobarlo, el contexto no arranca. Dos controles:
 * <ul>
 *   <li>Entorno: valida spring.datasource.url efectiva (TEST_DATABASE_URL, SPRING_DATASOURCE_URL,
 *       -Dspring.datasource.url...) antes de crear ningún bean.</li>
 *   <li>DataSource: valida el DataSource ya configurado, antes de que nadie abra una conexión. Cubre lo que
 *       se aplica después de la URL, como spring.datasource.hikari.jdbc-url (SPRING_DATASOURCE_HIKARI_JDBC_URL).</li>
 * </ul>
 */
public class TestDatabaseGuard implements EnvironmentPostProcessor,
        ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static final String PREFIX = "jdbc:postgresql://";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url;
        try {
            url = environment.getProperty("spring.datasource.url");
        } catch (IllegalArgumentException e) {
            throw refuse("no se pudo resolver spring.datasource.url (" + e.getMessage() + ")");
        }
        verify(url);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource) {
                    verify(dataSource);
                }
                return bean;
            }
        });
    }

    static void verify(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            throw refuse("no se puede comprobar el destino de un DataSource " + dataSource.getClass().getName());
        }
        if (hikari.getDataSource() != null || hikari.getDataSourceClassName() != null || hikari.getDataSourceJNDI() != null) {
            throw refuse("el pool no se conecta por jdbcUrl (dataSource, dataSourceClassName o JNDI configurados)");
        }
        verify(hikari.getJdbcUrl());
    }

    static void verify(String url) {
        if (url == null || url.isBlank()) {
            throw refuse("spring.datasource.url no está definida");
        }
        if (!url.startsWith(PREFIX)) {
            throw refuse("solo se admite jdbc:postgresql://<host local>/<base de pruebas>: " + withoutParameters(url));
        }
        URI uri;
        try {
            uri = new URI(url.substring("jdbc:".length()));
        } catch (URISyntaxException e) {
            throw refuse("URL no válida: " + withoutParameters(url));
        }
        String host = uri.getHost();
        if (host == null || !LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw refuse("el host no es local (" + host + "): " + withoutParameters(url));
        }
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (!database.toLowerCase(Locale.ROOT).contains("test")) {
            throw refuse("el nombre de la base de datos debe contener \"test\" (ddl-auto create-drop la borra): " + withoutParameters(url));
        }
    }

    // Los parámetros de la URL pueden llevar credenciales (?password=...): no se incluyen en el mensaje
    private static String withoutParameters(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?<parámetros omitidos>";
    }

    private static IllegalStateException refuse(String reason) {
        return new IllegalStateException("Pruebas abortadas para no tocar una base de datos real: " + reason);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
