package co.turismo.usecase.category;

import co.turismo.model.category.Category;
import co.turismo.model.category.CreateCategoryRequest;
import co.turismo.model.category.UpdateCategoryRequest;
import co.turismo.model.category.gateways.CategoryRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CategoryUseCase {

    private final CategoryRepository categoryRepository;

    public Flux<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Mono<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    public Mono<Category> create(CreateCategoryRequest request) {
        return categoryRepository.create(request);
    }

    public Mono<Category> update(Long id, UpdateCategoryRequest request) {
        return categoryRepository.update(id, request);
    }
}
