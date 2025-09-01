package co.turismo.api.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class JwtTokenProvider {
    private final SecretKey key;

    public JwtTokenProvider(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Jws<Claims> parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public String getSubject(Jws<Claims> jws) {
        return jws.getPayload().getSubject(); // email
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Jws<Claims> jws) {
        Object claim = jws.getBody().get("roles");
        if (claim instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
