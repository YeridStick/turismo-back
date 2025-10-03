package co.turismo.model.feedback.gateways;

import co.turismo.model.feedback.Feedback;
import reactor.core.publisher.Mono;

public interface FeedbackModelRepository {
    Mono<Feedback> create(Feedback feedback);
}