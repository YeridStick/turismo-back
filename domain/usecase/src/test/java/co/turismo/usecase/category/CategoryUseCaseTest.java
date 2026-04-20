package co.turismo.usecase.category;

import co.turismo.model.category.Category;
import co.turismo.model.category.CreateCategoryRequest;
import co.turismo.model.category.UpdateCategoryRequest;
import co.turismo.model.category.gateways.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CategoryUseCase(categoryRepository);
    }

    @Test
    void findAllShouldDelegateToRepository() {
        when(categoryRepository.findAll()).thenReturn(Flux.just(Category.builder().id(1L).name("Parques").build()));

        StepVerifier.create(useCase.findAll())
                .expectNextCount(1)
                .verifyComplete();

        verify(categoryRepository).findAll();
    }
    // Mono<Category> findById(Long id) {
    //        return categoryRepository.findById(id)
    @Test
    void findByIdToRepository() {
        when(categoryRepository.findById(2L)).thenReturn(Mono.just(Category.builder().id(2L).name("Parques").build()));
        StepVerifier.create(useCase.findById(2L))
                .expectNextCount(1)
                .verifyComplete();

    }

    @Test
    void createShouldDelegateToRepository() {
        CreateCategoryRequest request = CreateCategoryRequest.builder().name("Aventura").build();
        Category created = Category.builder().id(2L).name("Aventura").build();
        when(categoryRepository.create(request)).thenReturn(Mono.just(created));

        StepVerifier.create(useCase.create(request))
                .expectNext(created)
                .verifyComplete();
    }

    @Test
    void updateShouldDelegateToRepository() {
        UpdateCategoryRequest request = UpdateCategoryRequest.builder().name("Nuevo nombre").build();
        Category updated = Category.builder().id(2L).name("Nuevo nombre").build();
        when(categoryRepository.update(2L, request)).thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.update(2L, request))
                .expectNext(updated)
                .verifyComplete();
    }
}

