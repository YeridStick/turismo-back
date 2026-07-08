package co.turismo.api.mapper;

import co.turismo.api.dto.feedback.CreateFeedbackBody;

public final class FeedbackMapper {

    private FeedbackMapper() {}

    public static String extractType(CreateFeedbackBody body) {
        return body.getType();
    }

    public static String extractMessage(CreateFeedbackBody body) {
        return body.getMessage();
    }

    public static String extractDeviceId(CreateFeedbackBody body) {
        return body.getDevice_id();
    }

    public static String extractContactEmail(CreateFeedbackBody body) {
        return body.getContact_email();
    }
}
