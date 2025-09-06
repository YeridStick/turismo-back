package co.turismo.r2dbc.usersRepository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("users")
public class UserData {

    @Id
    private Long id;

    @Column("full_name")
    private String fullName;

    private String email;

    @Column("url_avatar")
    private String urlAvatar;

    @Column("identification_type")
    private String identificationType;

    @Column("identification_number")
    private String identificationNumber;

    @Column("otp_hash")
    private String otpHash;

    @Column("otp_expires_at")
    private OffsetDateTime otpExpiresAt;

    @Column("otp_attempts")
    private Integer otpAttempts;

    @Column("otp_max_attempts")
    private Integer otpMaxAttempts;

    @Column("locked_until")
    private OffsetDateTime lockedUntil;

    @Column("last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column("created_at")
    private OffsetDateTime createdAt;
}