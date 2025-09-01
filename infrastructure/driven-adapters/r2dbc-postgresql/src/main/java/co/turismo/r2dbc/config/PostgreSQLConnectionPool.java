package co.turismo.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PostgreSQLConnectionPool {

    @Value("${spring.r2dbc.postgresql.host}")
    private String host;

    @Value("${spring.r2dbc.postgresql.port}")
    private int port;

    @Value("${spring.r2dbc.postgresql.database}")
    private String database;

    @Value("${spring.r2dbc.postgresql.username}")
    private String username;

    @Value("${spring.r2dbc.postgresql.password}")
    private String password;

    @Value("${spring.r2dbc.postgresql.schema}")
    private String schema;

    @Value("${spring.connection-pool.initial-size}")
    private int initialSize;

    @Value("${spring.connection-pool.max-size}")
    private int maxSize;

    @Value("${spring.connection-pool.max-idle-time}")
    private int maxIdleTime;

    @Bean
    public ConnectionPool connectionPool() {
        PostgresqlConnectionConfiguration dbConfiguration = PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .database(database)
                .schema(schema)
                .username(username)
                .password(password)
                .build();

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder()
                .connectionFactory(new PostgresqlConnectionFactory(dbConfiguration))
                .name("api-postgres-connection-pool")
                .initialSize(initialSize)
                .maxSize(maxSize)
                .maxIdleTime(Duration.ofSeconds(maxIdleTime))
                .validationQuery("SELECT 1")
                .build();

        return new ConnectionPool(poolConfiguration);
    }
}