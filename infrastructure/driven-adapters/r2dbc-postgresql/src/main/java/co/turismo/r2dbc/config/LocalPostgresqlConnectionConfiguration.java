package co.turismo.r2dbc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalPostgresqlConnectionConfiguration {

    @Bean
    PostgresqlConnectionProperties localPostgresqlConnectionProperties(
            @Value("${spring.r2dbc.postgresql.host}") String host,
            @Value("${spring.r2dbc.postgresql.port}") Integer port,
            @Value("${spring.r2dbc.postgresql.database}") String database,
            @Value("${spring.r2dbc.postgresql.schema}") String schema,
            @Value("${spring.r2dbc.postgresql.username}") String username,
            @Value("${spring.r2dbc.postgresql.password}") String password) {
        return new PostgresqlConnectionProperties(host, port, database, schema, username, password);
    }
}
