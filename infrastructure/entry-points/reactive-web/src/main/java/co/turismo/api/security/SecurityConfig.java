package co.turismo.api.security;

import co.turismo.api.security.jwt.JwtReactiveAuthenticationManager;
import co.turismo.api.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION;

@Configuration
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        var tokenProvider = new JwtTokenProvider(jwtSecret);
        var authManager   = new JwtReactiveAuthenticationManager(tokenProvider);

        var bearerConverter = new ServerBearerTokenAuthenticationConverter();
        var jwtFilter = new AuthenticationWebFilter(authManager);
        jwtFilter.setServerAuthenticationConverter(bearerConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex
                        // Público
                        .pathMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/places/nearby").permitAll()

                        // Places: autenticado
                        // Crear: OWNER o ADMIN
                        .pathMatchers(HttpMethod.POST, "/api/places").hasAnyRole("OWNER", "ADMIN")
                        // Listar "mis lugares"
                        .pathMatchers(HttpMethod.GET, "/api/places/mine").authenticated()
                        // Activar/desactivar: OWNER o ADMIN (adapter valida pertenencia del OWNER)
                        .pathMatchers(HttpMethod.PATCH, "/api/places/*/active").hasAnyRole("OWNER", "ADMIN")
                        // Gestionar co-owners: OWNER o ADMIN
                        .pathMatchers(HttpMethod.POST,   "/api/places/*/owners").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/places/*/owners/**").hasAnyRole("OWNER", "ADMIN")

                        // Admin
                        .pathMatchers("/admin/**").hasRole("ADMIN")

                        // Resto: autenticado
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, AUTHENTICATION)
                .build();
    }
}
