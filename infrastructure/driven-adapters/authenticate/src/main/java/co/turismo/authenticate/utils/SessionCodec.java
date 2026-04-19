package co.turismo.authenticate.utils;

import co.turismo.authenticate.dto.SessionSnapshot;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SessionCodec {

    public String encode(SessionSnapshot session) {
        String rolesJoined = String.join(",", session.roles() == null ? Set.of() : session.roles());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((session.email() + "|" + rolesJoined + "|" + nullSafe(session.ip()))
                        .getBytes(StandardCharsets.UTF_8));
    }

    public SessionSnapshot decode(String raw) {
        String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 3) throw new IllegalStateException("Formato de sesión inválido");
        Set<String> roles = Arrays.stream(parts[1].split(","))
                .filter(r -> !r.isBlank())
                .collect(Collectors.toSet());
        return new SessionSnapshot(parts[0], roles, parts[2].isBlank() ? null : parts[2]);
    }

    private String nullSafe(String v) { return v == null ? "" : v; }
}