package co.turismo.api.security.jwt;

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

    public JwtReactiveAuthenticationManager(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof BearerTokenAuthenticationToken bearer)) {
            return Mono.empty();
        }
        final String token = bearer.getToken();

        return Mono.fromCallable(() -> tokenProvider.parseAndValidate(token))
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
}
