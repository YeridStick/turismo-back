package co.turismo.r2dbc.usersRepository.repository;

import co.turismo.r2dbc.usersRepository.entity.UserRoleData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserRoleAdapterRepository extends ReactiveCrudRepository<UserRoleData, Long> {}
