package co.turismo.usecase.reviews;

import co.turismo.model.reviews.Review;
import co.turismo.model.reviews.gateways.ReviewModalRepository;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import co.turismo.model.userIdentityPort.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewsUseCaseTest {

    @Mock
    private ReviewModalRepository reviewRepository;
    @Mock
    private UserIdentityPort userIdentityPort;

    private ReviewsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReviewsUseCase(reviewRepository, userIdentityPort);
    }

    @Test
    void createShouldFailWhenRatingIsOutOfRange() {
        StepVerifier.create(useCase.create(1L, (short) 6, "ok", "ana@example.com"))
                .expectErrorMatches(error -> error.getMessage().contains("Rating"))
                .verify();
    }

    @Test
    void createShouldFailWhenUserDoesNotExist() {
        when(userIdentityPort.getUserIdForEmail("ana@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.create(1L, (short) 5, "Excelente", "ana@example.com"))
                .expectErrorMatches(error -> error.getMessage().contains("Usuario no encontrado"))
                .verify();
    }

    @Test
    void createShouldPersistReviewWithTrimmedComment() {
        when(userIdentityPort.getUserIdForEmail("ana@example.com"))
                .thenReturn(Mono.just(new UserSummary(7L, "ana@example.com")));
        when(reviewRepository.create(any(Review.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.create(10L, (short) 4, "  Buen lugar  ", "ana@example.com"))
                .assertNext(review -> {
                    assertEquals(10L, review.getPlaceId());
                    assertEquals(7L, review.getUserId());
                    assertEquals("Buen lugar", review.getComment());
                    assertEquals(4, review.getRating());
                })
                .verifyComplete();

        verify(reviewRepository).create(any(Review.class));
    }
}

