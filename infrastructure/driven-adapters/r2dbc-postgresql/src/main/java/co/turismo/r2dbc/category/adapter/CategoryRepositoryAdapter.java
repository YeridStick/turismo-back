package co.turismo.r2dbc.category.adapter;

import co.turismo.model.category.Category;
import co.turismo.model.category.CreateCategoryRequest;
import co.turismo.model.category.UpdateCategoryRequest;
import co.turismo.model.category.gateways.CategoryRepository;
import co.turismo.r2dbc.category.entity.CategoryData;
import co.turismo.r2dbc.category.repository.CategoryAdapterRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class CategoryRepositoryAdapter
        extends ReactiveAdapterOperations<Category, CategoryData, Long, CategoryAdapterRepository>
        implements CategoryRepository {

    protected CategoryRepositoryAdapter(CategoryAdapterRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> mapper.map(data, Category.class));
    }

    @Override
    public Flux<Category> findAll() {
        return repository.findAllProjected()
                .map(this::toEntity);
    }

    @Override
    public Mono<Category> findById(Long id) {
        return repository.findByIdProjected(id)
                .map(this::toEntity);
    }

    @Override
    public Mono<Category> create(CreateCategoryRequest request) {
        return repository.insertCategory(
                        request.getSlug(),
                        request.getName()
                )
                .map(this::toEntity);
    }

    @Override
    public Mono<Category> update(Long id, UpdateCategoryRequest request) {
        return repository.updateCategory(
                        id,
                        request.getSlug(),
                        request.getName()
                )
                .map(this::toEntity);
    }
}
