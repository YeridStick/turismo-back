// SecurityConfig.java
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
@EnableReactiveMethodSecurity // <- si quieres usar @PreAuthorize en services/use cases
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public ServerAuthenticationEntryPoint jsonAuthEntryPoint(ObjectMapper mapper) {
        // 401 - no autenticado
        return (exchange, ex) -> {
            var resp = exchange.getResponse();
            resp.setRawStatusCode(401);
            resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            var body = ApiResponse.error(401, "No autenticado");
            byte[] bytes;
            try { bytes = mapper.writeValueAsBytes(body); } catch (Exception e) {
                bytes = "{\"status\":401,\"message\":\"No autenticado\",\"data\":null}".getBytes();
            }
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
        };
    }

    @Bean
    public ServerAccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper mapper) {
        // 403 - sin permisos
        return (exchange, ex) -> {
            var resp = exchange.getResponse();
            resp.setRawStatusCode(403);
            resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            var body = ApiResponse.error(403, "No tienes permisos para realizar esta acción");
            byte[] bytes;
            try { bytes = mapper.writeValueAsBytes(body); } catch (Exception e) {
                bytes = "{\"status\":403,\"message\":\"No tienes permisos para realizar esta acción\",\"data\":null}".getBytes();
            }
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
        };
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint jsonAuthEntryPoint,
            ServerAccessDeniedHandler   jsonAccessDeniedHandler
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

                // <- EXCEPCIONES CENTRALES JSON
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthEntryPoint) // 401
                        .accessDeniedHandler(jsonAccessDeniedHandler)  // 403
                )

                .authorizeExchange(ex -> ex
                        // Público
                        .pathMatchers("/docs", "/docs/", "/docs/**").permitAll()
                        .pathMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET,  "/api/places/nearby").permitAll()
                        .pathMatchers(HttpMethod.GET,  "/api/places/all").permitAll()
                        .pathMatchers(HttpMethod.GET,  "/api/places/search").permitAll()
                        .pathMatchers(HttpMethod.GET,   "/api/places/{id}").permitAll()

                        // PRUEBAS (abrimos explícitamente los endpoints de visitas)
                        .pathMatchers(HttpMethod.POST,  "/api/pruebas/places/*/checkin").permitAll()         // <- público
                        .pathMatchers(HttpMethod.PATCH, "/api/pruebas/visits/*/confirm").authenticated()     // si quieres público, cambia a .permitAll()
                        .pathMatchers(HttpMethod.GET,   "/api/pruebas/analytics/places/top").permitAll()     // <- **público** (lo que pediste)

                        // Si quieres abrir TODO lo de /api/pruebas/ a público, puedes añadir:
                        .pathMatchers("/api/pruebas/**").permitAll()

                        // Places
                        .pathMatchers(HttpMethod.POST,  "/api/places").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.GET,   "/api/places/mine").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/places/*/active").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.POST,  "/api/places/*/owners").hasAnyRole("OWNER", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE,"/api/places/*/owners/**").hasAnyRole("OWNER", "ADMIN")

                        // Admin
                        .pathMatchers("/admin/**").hasRole("ADMIN")

                        // Resto
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, AUTHENTICATION)
                .build();
    }
}
