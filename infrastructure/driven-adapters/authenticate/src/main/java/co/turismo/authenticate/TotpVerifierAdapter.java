package co.turismo.authenticate;

import co.turismo.model.authenticationsession.gateways.TotpVerifier;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.Key;
import java.time.Instant;

@Component
public class TotpVerifierAdapter implements TotpVerifier {

    private static final int PERIOD_SECONDS = 30; // TOTP estándar 30s
    private static final int DIGITS = 6;          // 6 dígitos

    @Override
    public Mono<Boolean> verify(String base32Secret, int code, int windowSteps) {
        return Mono.fromSupplier(() -> {
            try {
                byte[] key = new Base32().decode(base32Secret.trim());
                long timestep = Instant.now().getEpochSecond() / PERIOD_SECONDS;
                for (int i = -windowSteps; i <= windowSteps; i++) {
                    int expected = generateTotpCode(key, timestep + i);
                    if (expected == code) return true;
                }
                return false;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static int generateTotpCode(byte[] keyBytes, long counter) throws Exception {
        Key key = new SecretKeySpec(keyBytes, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(key);

        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array(); // big-endian
        byte[] hash = mac.doFinal(counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary =
                ((hash[offset] & 0x7F) << 24) |
                        ((hash[offset + 1] & 0xFF) << 16) |
                        ((hash[offset + 2] & 0xFF) << 8) |
                        (hash[offset + 3] & 0xFF);

        int mod = (int) Math.pow(10, DIGITS); // 1_000_000
        return binary % mod;
    }
}
