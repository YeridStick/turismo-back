package co.turismo.usecase.feedback;

import co.turismo.model.feedback.Feedback;
import co.turismo.model.feedback.gateways.FeedbackModelRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackUseCaseTest {

    @Mock
    private FeedbackModelRepository feedbackRepository;
    @Mock
    private UserIdentityPort userIdentityPortGateway;

    private FeedbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FeedbackUseCase(feedbackRepository, userIdentityPortGateway);
    }

    @Test
    void createShouldFailWhenTypeIsInvalid() {
        StepVerifier.create(useCase.create(1L, "other", "mensaje", "ana@example.com", null, null))
                .expectErrorMatches(error -> error.getMessage().contains("type"))
                .verify();
    }

    @Test
    void createShouldFailWhenMessageIsBlank() {
        StepVerifier.create(useCase.create(1L, "suggestion", "   ", "ana@example.com", null, null))
                .expectErrorMatches(error -> error.getMessage().contains("message"))
                .verify();
    }

    @Test
    void createShouldPersistFeedbackWhenUserExists() {
        when(userIdentityPortGateway.getUserIdForEmail("ana@example.com"))
                .thenReturn(Mono.just(new UserSummary(99L, "ana@example.com")));
        when(feedbackRepository.create(any(Feedback.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.create(12L, "suggestion", "  agregar mas horarios  ", "ana@example.com", null, "cont@a.com"))
                .assertNext(feedback -> {
                    assertEquals(12L, feedback.getPlaceId());
                    assertEquals(99L, feedback.getUserId());
                    assertEquals("agregar mas horarios", feedback.getMessage());
                    assertEquals("suggestion", feedback.getType());
                })
                .verifyComplete();
    }
}

