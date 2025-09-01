package co.turismo.r2dbc.usersRepository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("roles")
public class RoleData {

    @Id
    private Long id;

    private String roleName; // 'ADMIN', 'OWNER', 'VISITOR'
}