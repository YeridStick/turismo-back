package co.turismo.model.user;
 
public record EmailVerificationResult(VerificationStatus status) {
    public enum VerificationStatus {
        SENT,
        ALREADY_VERIFIED
    }
}
