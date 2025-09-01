package co.turismo.model.authenticationsession;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AuthenticationSession {
    private String token;
    private String email;
    private Set<String> roles;
    private String ip;
    private LocalDateTime expirationTime;
    private boolean isValid;
}
