package co.turismo.antispam;

import co.turismo.model.common.AntiSpamService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Primary
@Component
public class AntiSpamNoopService implements AntiSpamService {
    @Override
    public Mono<Void> checkDeviceQuota(String deviceId) {
        return Mono.empty();
    }
}