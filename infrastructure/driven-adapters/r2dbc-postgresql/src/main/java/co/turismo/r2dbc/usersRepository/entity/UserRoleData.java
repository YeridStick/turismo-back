package co.turismo.r2dbc.usersRepository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("user_roles")
public class UserRoleData {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("role_id")
    private Long roleId;
}