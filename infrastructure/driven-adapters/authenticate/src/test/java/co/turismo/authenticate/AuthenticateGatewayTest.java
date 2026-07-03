package co.turismo.authenticate;

import co.turismo.authenticate.dto.SessionSnapshot;
import co.turismo.authenticate.utils.AuthSessionStore;
import co.turismo.authenticate.utils.JwtProvider;
import co.turismo.model.user.User;
import co.turismo.model.user.gateways.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticateGatewayTest {

    private JwtProvider jwt;
    private AuthSessionStore sessions;
    private UserRepository userRepository;
    private AuthenticateGateway gateway;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtProvider.class);
        sessions = mock(AuthSessionStore.class);
        userRepository = mock(UserRepository.class);
        gateway = new AuthenticateGateway(jwt, sessions, userRepository);
        ReflectionTestUtils.setField(gateway, "graceMins", 15L);
        ReflectionTestUtils.setField(gateway, "bindIp", false);
    }

    @Test
    void generateTokenShouldStoreSessionInPostgresSessionStore() {
        Instant expiration = Instant.now().plus(Duration.ofHours(4));
        when(jwt.nextExpiration()).thenReturn(expiration);
        when(jwt.generate(eq("ana@example.com"), eq(Set.of("visitor")), anyString(), eq(expiration)))
                .thenReturn("jwt-token");
        when(userRepository.findByEmail("ana@example.com"))
                .thenReturn(Mono.just(User.builder().id(10L).email("ana@example.com").build()));
        when(sessions.save(eq("jwt-token"), eq(10L), any(SessionSnapshot.class), anyString(),
                eq(expiration), eq(Duration.ofMinutes(15)))).thenReturn(Mono.empty());

        StepVerifier.create(gateway.generateToken(" ANA@EXAMPLE.COM ", Set.of("VISITOR"), "10.0.0.1"))
                .expectNext("jwt-token")
                .verifyComplete();

        ArgumentCaptor<SessionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(SessionSnapshot.class);
        verify(sessions).save(eq("jwt-token"), eq(10L), snapshotCaptor.capture(), anyString(),
                eq(expiration), eq(Duration.ofMinutes(15)));
        assertEquals("ana@example.com", snapshotCaptor.getValue().email());
        assertEquals(Set.of("visitor"), snapshotCaptor.getValue().roles());
        assertNull(snapshotCaptor.getValue().ip());
    }

    @Test
    void validateTokenShouldRequireNonRevokedStoredSession() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(jwt.parseStrict("jwt-token")).thenReturn(claims);
        when(sessions.isRevoked("jti-1")).thenReturn(Mono.just(false));
        when(sessions.load("jwt-token"))
                .thenReturn(Mono.just(new SessionSnapshot("ana@example.com", Set.of("visitor"), null)));

        StepVerifier.create(gateway.validateToken("jwt-token", "10.0.0.1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void refreshTokenShouldCreateNewSessionAndRevokeOldToken() {
        Instant oldExpiration = Instant.now().plus(Duration.ofMinutes(5));
        Instant newExpiration = Instant.now().plus(Duration.ofHours(4));
        Claims oldClaims = mock(Claims.class);
        when(oldClaims.getId()).thenReturn("old-jti");
        when(oldClaims.getExpiration()).thenReturn(Date.from(oldExpiration));
        when(jwt.parseAllowExpired("old-token")).thenReturn(oldClaims);
        when(jwt.nextExpiration()).thenReturn(newExpiration);
        when(jwt.generate(eq("ana@example.com"), eq(Set.of("visitor")), anyString(), eq(newExpiration)))
                .thenReturn("new-token");
        when(sessions.isRevoked("old-jti")).thenReturn(Mono.just(false));
        when(sessions.load("old-token"))
                .thenReturn(Mono.just(new SessionSnapshot("ana@example.com", Set.of("visitor"), null)));
        when(userRepository.findByEmail("ana@example.com"))
                .thenReturn(Mono.just(User.builder().id(10L).email("ana@example.com").build()));
        when(sessions.save(eq("new-token"), eq(10L), any(SessionSnapshot.class), anyString(),
                eq(newExpiration), eq(Duration.ofMinutes(15)))).thenReturn(Mono.empty());
        when(sessions.revokeByJti(eq("old-jti"), any(Duration.class))).thenReturn(Mono.empty());
        when(sessions.revokeByToken("old-token")).thenReturn(Mono.empty());

        StepVerifier.create(gateway.refreshToken("old-token", "10.0.0.1"))
                .expectNext("new-token")
                .verifyComplete();

        verify(sessions).revokeByJti(eq("old-jti"), any(Duration.class));
        verify(sessions).revokeByToken("old-token");
    }

    @Test
    void revokeTokenShouldRevokeByJtiAndTokenHash() {
        Instant expiration = Instant.now().plus(Duration.ofHours(1));
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getExpiration()).thenReturn(Date.from(expiration));
        when(jwt.parseAllowExpired("jwt-token")).thenReturn(claims);
        when(sessions.revokeByJti(eq("jti-1"), any(Duration.class))).thenReturn(Mono.empty());
        when(sessions.revokeByToken("jwt-token")).thenReturn(Mono.empty());

        StepVerifier.create(gateway.revokeToken("jwt-token"))
                .verifyComplete();

        verify(sessions).revokeByJti(eq("jti-1"), any(Duration.class));
        verify(sessions).revokeByToken("jwt-token");
    }
}
