package co.turismo.authenticate.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class JwtProvider {

    @Value("${security.jwt.secret}")       private String secret;
    @Value("${security.jwt.issuer:turismo-app}") private String issuer;
    @Value("${security.jwt.ttl-hours:4}") private long ttlHours;

    public String generate(String email, Set<String> roles, String jti, Instant expiration) {
        return Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .subject(email)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .signWith(signingKey())
                .compact();
    }

    public Claims parseStrict(String token) {
        return Jwts.parser().verifyWith(signingKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    /** Permite tokens expirados — útil sólo para refresh dentro de la ventana de gracia. */
    public Claims parseAllowExpired(String token) {
        try {
            return parseStrict(token);
        } catch (ExpiredJwtException ex) {
            return ex.getClaims();
        }
    }

    public Instant nextExpiration() {
        return Instant.now().plus(Duration.ofHours(ttlHours));
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}