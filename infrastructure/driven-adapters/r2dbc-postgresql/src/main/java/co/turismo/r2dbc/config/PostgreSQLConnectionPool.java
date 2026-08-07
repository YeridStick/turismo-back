package co.turismo.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PostgreSQLConnectionPool {

    private final PostgresqlConnectionProperties databaseProperties;

    @Value("${spring.r2dbc.pool.initial-size}")
    private int initialSize;

    @Value("${spring.r2dbc.pool.max-size}")
    private int maxSize;

    @Value("${spring.r2dbc.pool.max-idle-time}")
    private Duration maxIdleTime;

    public PostgreSQLConnectionPool(PostgresqlConnectionProperties databaseProperties) {
        this.databaseProperties = databaseProperties;
    }

    @Bean
    public ConnectionPool connectionPool() {
        PostgresqlConnectionConfiguration dbConfiguration = PostgresqlConnectionConfiguration.builder()
                .host(databaseProperties.host())
                .port(databaseProperties.port())
                .database(databaseProperties.database())
                .username(databaseProperties.username())
                .password(databaseProperties.password())
                .sslMode(SSLMode.REQUIRE)
                .build();

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder()
                .connectionFactory(new PostgresqlConnectionFactory(dbConfiguration))
                .name("api-postgres-connection-pool")
                .initialSize(initialSize)
                .maxSize(maxSize)
                .maxIdleTime(maxIdleTime)
                .validationQuery("SELECT 1")
                .build();

        return new ConnectionPool(poolConfiguration);
    }
}
