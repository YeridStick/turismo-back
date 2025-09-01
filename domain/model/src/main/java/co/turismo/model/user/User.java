package co.turismo.model.user;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Set;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String fullName;
    private String email;
    private String urlAvatar;

    private OffsetDateTime lockedUntil;
    private OffsetDateTime createdAt;

    private Set<String> roles;
}