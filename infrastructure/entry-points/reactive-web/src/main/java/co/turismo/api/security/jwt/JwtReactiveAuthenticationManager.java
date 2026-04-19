package co.turismo.api.security.jwt;

import co.turismo.model.authenticationsession.gateways.AuthenticationSessionRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider tokenProvider;
    private final AuthenticationSessionRepository authenticationSessionRepository;

    public JwtReactiveAuthenticationManager(
            JwtTokenProvider tokenProvider,
            AuthenticationSessionRepository authenticationSessionRepository
    ) {
        this.tokenProvider = tokenProvider;
        this.authenticationSessionRepository = authenticationSessionRepository;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof BearerTokenAuthenticationToken bearer)) {
            return Mono.empty();
        }
        final String token = bearer.getToken();

        return Mono.fromCallable(() -> tokenProvider.parseAndValidate(token))
                .onErrorMap(this::mapJwtException)
                .flatMap(jws -> authenticationSessionRepository.validateToken(token, null)
                        .flatMap(valid -> {
                            if (!valid) {
                                return Mono.error(new BadCredentialsException("Token revocado o sesion invalida"));
                            }
                            return Mono.just(jws);
                        }))
                .map(jws -> {
                    final String email = tokenProvider.getSubject(jws);
                    final List<String> roles = tokenProvider.getRoles(jws);

                    var authorities = roles == null ? List.<SimpleGrantedAuthority>of()
                            : roles.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(r -> r.toUpperCase(Locale.ROOT))
                            .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    return new UsernamePasswordAuthenticationToken(email, token, authorities);
                });
    }

    private Throwable mapJwtException(Throwable error) {
        if (error instanceof ExpiredJwtException) {
            return new BadCredentialsException("Token expirado", error);
        }
        if (error instanceof JwtException || error instanceof IllegalArgumentException) {
            return new BadCredentialsException("Token invalido", error);
        }
        return error;
    }
}
