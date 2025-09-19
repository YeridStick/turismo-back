package co.turismo.api.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import co.turismo.model.authenticationsession.AuthenticationSession;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Supplier;

@Configuration
public class AuthenticationConfig {

    // ========= Beans requeridos por AuthenticateGateway =========
    @Bean
    public Cache<String, AuthenticationSession> sessionCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(4))
                .maximumSize(10_000)
                .build();
    }

    // Si tu AuthenticateGateway necesita el secreto JWT como String:
    @Bean
    public String jwtSecret(@Value("${security.jwt.secret}") String secret) {
        return secret;
    }

    // ========= Beans para el UseCase (TOTP) =========
    /** Proveedor del secreto Base32 para TOTP (el use case no conoce la clase concreta). */
    @Bean
    public Supplier<String> totpSecretSupplier() {
        return () -> {
            byte[] buf = new byte[20]; // 160 bits recomendado
            new SecureRandom().nextBytes(buf);
            return new Base32().encodeToString(buf).replace("=", "");
        };
    }
}
