package co.turismo.api.security;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.security.jwt.JwtReactiveAuthenticationManager;
import co.turismo.api.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    /**
     * Configuración de CORS desde propiedades del YAML
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse de los orígenes permitidos desde el YAML (separados por comas)
        List<String> origins = new ArrayList<>();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            String[] originArray = allowedOrigins.split(",");
            for (String origin : originArray) {
                origins.add(origin.trim());
            }
        } else {
            // Fallback si no está configurado
            origins = Arrays.asList(
                    "http://localhost:3000",
                    "http://localhost:8080",
                    "http://localhost:5173"
            );
        }

        configuration.setAllowedOrigins(origins);

        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"
        ));

        // Headers permitidos
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-CSRF-Token",
                "Cache-Control"
        ));

        // Headers que puede exponer el navegador
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Total-Count"
        ));

        // Permitir credenciales (cookies, auth headers)
        configuration.setAllowCredentials(true);

        // Máximo tiempo de cache de la preflight request (en segundos)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

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
            ServerAccessDeniedHandler jsonAccessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource
    ) {
        var tokenProvider = new JwtTokenProvider(jwtSecret);
        var authManager   = new JwtReactiveAuthenticationManager(tokenProvider);

        var bearerConverter = new ServerBearerTokenAuthenticationConverter();
        var jwtFilter = new AuthenticationWebFilter(authManager);
        jwtFilter.setServerAuthenticationConverter(bearerConverter);
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler)
                )
                .authorizeExchange(ex -> ex
                        // ---- Público: documentación (IMPORTANTE: antes que todo) ----
                        .pathMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .pathMatchers("/scalar", "/docs").permitAll()

                        // ---- Público: actuator y recursos ----
                        .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .pathMatchers("/favicon.ico", "/").permitAll()

                        // ---- Público: endpoints de autenticación ----
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/admin/auth/**").permitAll()

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