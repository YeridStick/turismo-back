package co.turismo.r2dbc.usersRepository.repository;

import co.turismo.r2dbc.usersRepository.entity.RoleData;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface RoleAdapterRepository extends ReactiveCrudRepository<RoleData, Long> {}