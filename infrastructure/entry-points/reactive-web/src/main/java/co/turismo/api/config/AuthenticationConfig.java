package co.turismo.api.config;

import co.turismo.model.common.AppUrlConfig;
import co.turismo.model.security.gateways.PasswordHasher;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.function.Supplier;

@Configuration
public class AuthenticationConfig {

    @Bean
    public Supplier<String> totpSecretSupplier() {
        return () -> {
            byte[] buf = new byte[20];
            new SecureRandom().nextBytes(buf);
            return new Base32().encodeToString(buf).replace("=", "");
        };
    }

    @Bean
    public PasswordHasher passwordHasher() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return encoder.encode(rawPassword);
            }

            @Override
            public boolean matches(String rawPassword, String hash) {
                return encoder.matches(rawPassword, hash);
            }
        };
    }

    @Bean
    public AppUrlConfig appUrlConfig(
            @Value("${app.public-url:http://localhost:8082}") String publicUrl,
            @Value("${app.frontend-url:}") String frontendUrl) {
        return new AppUrlConfig(publicUrl, frontendUrl);
    }
}
