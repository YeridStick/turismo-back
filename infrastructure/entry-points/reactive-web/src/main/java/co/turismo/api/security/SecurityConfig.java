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
        // Tu proveedor de tokens + manager
        var tokenProvider = new JwtTokenProvider(jwtSecret);
        var authManager   = new JwtReactiveAuthenticationManager(tokenProvider);

        // Filtro de autenticación Bearer (sin oauth2ResourceServer)
        var bearerConverter = new ServerBearerTokenAuthenticationConverter();
        var jwtFilter = new AuthenticationWebFilter(authManager);
        jwtFilter.setServerAuthenticationConverter(bearerConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex
                        .pathMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/places/**").permitAll()
                        .pathMatchers(HttpMethod.POST,   "/api/places").hasRole("OWNER")
                        .pathMatchers(HttpMethod.PUT,    "/api/places/**").hasRole("OWNER")
                        .pathMatchers(HttpMethod.DELETE, "/api/places/**").hasRole("OWNER")
                        .pathMatchers("/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, AUTHENTICATION)
                .build();
    }
}
