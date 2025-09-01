package co.turismo.model.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Builder(toBuilder = true)
public class VerificationCode {
    private String email;
    private String code;
    private LocalDateTime expirationTime;
    private boolean used;
}
