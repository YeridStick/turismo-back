package co.turismo.r2dbc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@Profile("!local")
public class SecretsManagerPostgresqlConnectionConfiguration {

    private final ObjectMapper objectMapper;

    public SecretsManagerPostgresqlConnectionConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    PostgresqlConnectionProperties secretPostgresqlConnectionProperties(
            @Value("${turismo.database.secret-id}") String secretId,
            @Value("${turismo.database.secret-json-b64:}") String secretJsonB64,
            @Value("${turismo.database.region:${AWS_REGION:us-east-1}}") String region) {
        try {
            String secret = resolveSecret(secretId, secretJsonB64, region);

            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("El secreto de base de datos no contiene SecretString");
            }

            JsonNode json = objectMapper.readTree(secret);
            return new PostgresqlConnectionProperties(
                    required(json, "host"),
                    requiredPort(json, "port"),
                    required(json, "database"),
                    json.path("schema").asText("public"),
                    required(json, "username"),
                    required(json, "password"));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "No se pudo cargar la conexión PostgreSQL desde AWS Secrets Manager: " + secretId,
                    exception);
        }
    }

    private String resolveSecret(String secretId, String secretJsonB64, String region) {
        if (secretJsonB64 != null && !secretJsonB64.isBlank()) {
            try {
                return new String(Base64.getDecoder().decode(secretJsonB64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("TURISMO_DATABASE_SECRET_JSON_B64 no es válido", exception);
            }
        }

        if (secretId == null || secretId.isBlank()) {
            throw new IllegalStateException(
                    "Debe configurarse TURISMO_DATABASE_SECRET_ID o TURISMO_DATABASE_SECRET_JSON_B64");
        }

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .build()) {
            return client.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .build())
                    .secretString();
        }
    }

    private static String required(JsonNode json, String key) {
        String value = json.path(key).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta la propiedad '" + key + "' en el secreto de base de datos");
        }
        return value;
    }

    private static int requiredPort(JsonNode json, String key) {
        String value = required(json, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("La propiedad '" + key + "' del secreto debe ser numérica", exception);
        }
    }
}
