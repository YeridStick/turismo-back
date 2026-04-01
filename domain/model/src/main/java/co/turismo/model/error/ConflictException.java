package co.turismo.model.error;

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(message);
    }
}
