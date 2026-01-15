package co.turismo.model.category.gateways;

import co.turismo.model.category.Category;
import co.turismo.model.category.CreateCategoryRequest;
import co.turismo.model.category.UpdateCategoryRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryRepository {
    Flux<Category> findAll();

    Mono<Category> findById(Long id);

    Mono<Category> create(CreateCategoryRequest request);

    Mono<Category> update(Long id, UpdateCategoryRequest request);
}
