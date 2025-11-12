package co.turismo.api.security;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.security.jwt.JwtReactiveAuthenticationManager;
import co.turismo.api.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public ServerAuthenticationEntryPoint jsonAuthEntryPoint(ObjectMapper mapper) {
        return (exchange, ex) -> {
            var resp = exchange.getResponse();
            resp.setRawStatusCode(401);
            resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            var body = ApiResponse.error(401, "No autenticado");
            byte[] bytes;
            try {
                bytes = mapper.writeValueAsBytes(body);
            } catch (Exception e) {
                bytes = "{\"status\":401,\"message\":\"No autenticado\",\"data\":null}".getBytes();
            }
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
        };
    }

    @Bean
    public ServerAccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper mapper) {
        return (exchange, ex) -> {
            var resp = exchange.getResponse();
            resp.setRawStatusCode(403);
            resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            var body = ApiResponse.error(403, "No tienes permisos para realizar esta acción");
            byte[] bytes;
            try {
                bytes = mapper.writeValueAsBytes(body);
            } catch (Exception e) {
                bytes = "{\"status\":403,\"message\":\"No tienes permisos para realizar esta acción\",\"data\":null}".getBytes();
            }
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
        };
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint jsonAuthEntryPoint,
            ServerAccessDeniedHandler jsonAccessDeniedHandler
    ) {
        var tokenProvider = new JwtTokenProvider(jwtSecret);
        var authManager   = new JwtReactiveAuthenticationManager(tokenProvider);

        var bearerConverter = new ServerBearerTokenAuthenticationConverter();
        var jwtFilter = new AuthenticationWebFilter(authManager);
        jwtFilter.setServerAuthenticationConverter(bearerConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler)
                )
                .authorizeExchange(ex -> ex
                        // ---- Público: documentación ----
                        .pathMatchers("/scalar", "/scalar/**").permitAll()
                        .pathMatchers("/docs", "/docs/**").permitAll()
                        .pathMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll()

                        // ---- Público: actuator y recursos ----
                        .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .pathMatchers("/favicon.ico", "/").permitAll()

                        // ---- Público: endpoints de autenticación ----
                        .pathMatchers("/api/auth/**").permitAll()

                        // ---- Público: endpoints de places ----
                        .pathMatchers(HttpMethod.GET, "/api/places/nearby").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/places/all").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/places/search").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/places/{id}").permitAll()

                        // ---- Público: pruebas ----
                        .pathMatchers(HttpMethod.POST,  "/api/pruebas/places/*/checkin").permitAll()
                        .pathMatchers(HttpMethod.PATCH, "/api/pruebas/visits/*/confirm").authenticated()
                        .pathMatchers(HttpMethod.GET,   "/api/pruebas/analytics/places/top").permitAll()
                        .pathMatchers("/api/pruebas/**").permitAll()

                        // ---- Protegido: Places / Admin ----
                        .pathMatchers(HttpMethod.POST,  "/api/places").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.GET,   "/api/places/mine").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/places/*/active").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.POST,  "/api/places/*/owners").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE,"/api/places/*/owners/**").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers("/admin/**").hasRole("ADMIN")

                        // ---- Resto autenticado ----
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, AUTHENTICATION)
                .build();
    }
}